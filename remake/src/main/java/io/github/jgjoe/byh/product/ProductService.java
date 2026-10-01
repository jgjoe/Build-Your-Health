package io.github.jgjoe.byh.product;

import io.github.jgjoe.byh.common.InvalidParameterException;
import io.github.jgjoe.byh.common.PageResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/** Product catalog paging and keyword search (RQ-F-002, RQ-F-003). */
@Service
public class ProductService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductMapper productMapper;

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    public PageResponse<ProductRow> search(int page, int size, String field, String keyword) {
        if (page < 1) {
            throw new InvalidParameterException("page는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidParameterException("size는 1 이상 100 이하여야 합니다.");
        }

        String searchField = normalize(field);
        if (searchField != null && !"name".equals(searchField) && !"manufacturer".equals(searchField)) {
            throw new InvalidParameterException("field는 name 또는 manufacturer만 사용할 수 있습니다.");
        }
        String searchKeyword = normalize(keyword);
        if (searchKeyword != null && searchField == null) {
            // A keyword without a field searches the product name.
            searchField = "name";
        }

        long total = productMapper.countProducts(searchField, searchKeyword);
        List<ProductRow> items = productMapper.findProducts(searchField, searchKeyword,
                ((long) page - 1) * size, size);
        return new PageResponse<>(items, page, size, total);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
