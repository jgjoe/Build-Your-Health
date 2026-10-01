package io.github.jgjoe.byh.product;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL lives in {@code mapper/ProductMapper.xml}; paging is done in SQL (RQ-F-002). */
@Mapper
public interface ProductMapper {

    long countProducts(@Param("field") String field, @Param("keyword") String keyword);

    List<ProductRow> findProducts(@Param("field") String field, @Param("keyword") String keyword,
                                  @Param("offset") long offset, @Param("size") int size);
}
