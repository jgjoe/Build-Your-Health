package io.github.jgjoe.byh;

import com.jayway.jsonpath.ReadContext;
import io.github.jgjoe.byh.support.IntegrationTestBase;
import io.github.jgjoe.byh.support.Responses;
import io.github.jgjoe.byh.support.TestFixtures;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** TC-203 (login, RQ-F-004). */
class LoginApiTest extends IntegrationTestBase {

    @Test
    void tc203_loginSucceedsAndStoresHashNotPlaintext() throws Exception {
        insertMember("alice", "pw1234", "앨리스");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(TestFixtures.loginBody("alice", "pw1234").getBytes(StandardCharsets.UTF_8)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        ReadContext json = Responses.json(result);
        assertThat(Responses.string(json, "$.id")).isEqualTo("alice");
        assertThat(Responses.string(json, "$.name")).isEqualTo("앨리스");
        assertThat(result.getRequest().getSession(false)).as("login must create an HTTP session").isNotNull();

        String storedHash = jdbc.queryForObject("SELECT PASSWORD_HASH FROM MEMBER WHERE ID = ?", String.class, "alice");
        assertThat(storedHash).isNotEqualTo("pw1234");
        assertThat(storedHash).startsWith("$2");
    }

    @Test
    void tc203_loginWithWrongPasswordIs401() throws Exception {
        insertMember("alice", "pw1234", "앨리스");

        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(TestFixtures.loginBody("alice", "wrong-password").getBytes(StandardCharsets.UTF_8)))
                .andReturn();
        assertThat(wrongPassword.getResponse().getStatus()).as("wrong password").isEqualTo(401);
        assertThat(Responses.code(wrongPassword)).as("wrong password").isEqualTo("UNAUTHORIZED");

        MvcResult unknownId = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(TestFixtures.loginBody("nobody", "pw1234").getBytes(StandardCharsets.UTF_8)))
                .andReturn();
        assertThat(unknownId.getResponse().getStatus()).as("unknown id").isEqualTo(401);
        assertThat(Responses.code(unknownId)).as("unknown id").isEqualTo("UNAUTHORIZED");
    }
}
