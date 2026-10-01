package io.github.jgjoe.byh.order;

/** Current DB price of one product, loaded before the order is written. */
public class ProductPrice {

    private String productId;
    private Integer discountPrice;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Integer getDiscountPrice() {
        return discountPrice;
    }

    public void setDiscountPrice(Integer discountPrice) {
        this.discountPrice = discountPrice;
    }
}
