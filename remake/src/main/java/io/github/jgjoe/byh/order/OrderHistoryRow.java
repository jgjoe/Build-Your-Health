package io.github.jgjoe.byh.order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** One order with its lines, mapped from the history/detail query (nested result map). */
public class OrderHistoryRow {

    private Long orderId;
    private LocalDateTime orderDate;
    private LocalDate deliveryDate;
    private Integer totalPrice;
    private String recipientName;
    private String address;
    private List<OrderLineRow> lines;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public Integer getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(Integer totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public List<OrderLineRow> getLines() {
        return lines;
    }

    public void setLines(List<OrderLineRow> lines) {
        this.lines = lines;
    }
}
