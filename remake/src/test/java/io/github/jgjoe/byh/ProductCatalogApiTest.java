package io.github.jgjoe.byh;

import com.jayway.jsonpath.ReadContext;
import io.github.jgjoe.byh.support.IntegrationTestBase;
import io.github.jgjoe.byh.support.Responses;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** TC-201 (paging, RQ-F-002) and TC-202 (search, RQ-F-003 / RQ-N-003). */
class ProductCatalogApiTest extends IntegrationTestBase {

    @Test
    void tc201_productPagingReturnsSecondPageAndTotal() throws Exception {
        for (int i = 1; i <= 25; i++) {
            insertProduct(String.format("T%03d", i), "상품 " + i, 10000 + i, 9000 + i, "제조사" + (i % 3));
        }

        MvcResult result = mockMvc.perform(get("/api/products").param("page", "2").param("size", "10")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        ReadContext json = Responses.json(result);
        assertThat(Responses.strings(json, "$.items[*].productId"))
                .containsExactly("T011", "T012", "T013", "T014", "T015", "T016", "T017", "T018", "T019", "T020");
        assertThat(Responses.longValue(json, "$.total")).isEqualTo(25);
        assertThat(Responses.longValue(json, "$.page")).isEqualTo(2);
        assertThat(Responses.longValue(json, "$.size")).isEqualTo(10);
    }

    @Test
    void tc201_productPagingRejectsOutOfRange() throws Exception {
        MvcResult pageZero = mockMvc.perform(get("/api/products").param("page", "0")).andReturn();
        assertThat(pageZero.getResponse().getStatus()).as("page=0").isEqualTo(400);
        assertThat(Responses.code(pageZero)).as("page=0").isEqualTo("INVALID_PARAMETER");

        MvcResult sizeTooLarge = mockMvc.perform(get("/api/products").param("size", "101")).andReturn();
        assertThat(sizeTooLarge.getResponse().getStatus()).as("size=101").isEqualTo(400);
        assertThat(Responses.code(sizeTooLarge)).as("size=101").isEqualTo("INVALID_PARAMETER");
    }

    @Test
    void tc202_searchRejectsUnknownField() throws Exception {
        insertProduct("T001", "생수 2L", 1000, 900, "제조사A");

        MvcResult byColumnName = mockMvc.perform(get("/api/products")
                .param("field", "product_name").param("keyword", "생수")).andReturn();
        assertThat(byColumnName.getResponse().getStatus()).as("field=product_name").isEqualTo(400);
        assertThat(Responses.code(byColumnName)).as("field=product_name").isEqualTo("INVALID_PARAMETER");

        MvcResult byUnknownAlias = mockMvc.perform(get("/api/products")
                .param("field", "subject").param("keyword", "생수")).andReturn();
        assertThat(byUnknownAlias.getResponse().getStatus()).as("field=subject").isEqualTo(400);
        assertThat(Responses.code(byUnknownAlias)).as("field=subject").isEqualTo("INVALID_PARAMETER");
    }

    @Test
    void tc202_searchTreatsInjectionTextLiterally() throws Exception {
        insertProduct("T001", "생수 2L", 1000, 900, "제조사A");
        insertProduct("T002", "현미차", 2000, 1800, "제조사B");

        MvcResult injection = mockMvc.perform(get("/api/products")
                .param("field", "name").param("keyword", "zzz%' OR '1%'='1")).andReturn();
        assertThat(injection.getResponse().getStatus()).isEqualTo(200);
        ReadContext injectionJson = Responses.json(injection);
        assertThat(Responses.longValue(injectionJson, "$.total")).isEqualTo(0);
        assertThat(Responses.strings(injectionJson, "$.items[*].productId")).isEmpty();

        MvcResult plain = mockMvc.perform(get("/api/products")
                .param("field", "name").param("keyword", "생수")).andReturn();
        assertThat(plain.getResponse().getStatus()).isEqualTo(200);
        ReadContext plainJson = Responses.json(plain);
        assertThat(Responses.longValue(plainJson, "$.total")).isEqualTo(1);
        assertThat(Responses.strings(plainJson, "$.items[*].productId")).containsExactly("T001");
    }

    @Test
    void tc202_searchByManufacturer() throws Exception {
        insertProduct("M001", "상품A", 1000, 900, "동원샘물");
        insertProduct("M002", "상품B", 2000, 1800, "제주삼다수");

        MvcResult result = mockMvc.perform(get("/api/products")
                .param("field", "manufacturer").param("keyword", "제주삼다수")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        ReadContext json = Responses.json(result);
        assertThat(Responses.longValue(json, "$.total")).isEqualTo(1);
        assertThat(Responses.strings(json, "$.items[*].productId")).containsExactly("M002");
        List<String> manufacturers = Responses.strings(json, "$.items[*].manufacturer");
        assertThat(manufacturers).containsExactly("제주삼다수");
    }
}
