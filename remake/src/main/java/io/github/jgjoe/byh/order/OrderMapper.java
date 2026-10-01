package io.github.jgjoe.byh.order;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL lives in {@code mapper/OrderMapper.xml}. */
@Mapper
public interface OrderMapper {

    List<ProductPrice> findPricesByProductIds(@Param("productIds") List<String> productIds);

    int insertOrder(OrderInsert order);

    int insertOrderItem(@Param("orderId") long orderId, @Param("productId") String productId,
                        @Param("quantity") int quantity, @Param("price") int price);

    long countOrdersByUserId(@Param("userId") String userId);

    List<OrderHistoryRow> findOrdersByUserId(@Param("userId") String userId,
                                             @Param("offset") long offset, @Param("size") int size);

    List<OrderHistoryRow> findOrderByIdAndUserId(@Param("orderId") long orderId, @Param("userId") String userId);
}
