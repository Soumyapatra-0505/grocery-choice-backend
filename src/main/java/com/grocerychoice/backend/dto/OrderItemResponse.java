package com.grocerychoice.backend.dto;

import com.grocerychoice.backend.entity.OrderItem;
import com.grocerychoice.backend.entity.ProductUnit;
import java.math.BigDecimal;

public class OrderItemResponse {

    private Long id;
    private Long productId;
    private String productName;
    private ProductUnit unit;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
    private String imageUrl;

    public OrderItemResponse() {
    }

    public OrderItemResponse(Long id, Long productId, String productName, ProductUnit unit,
                             BigDecimal price, Integer quantity, BigDecimal subtotal, String imageUrl) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.unit = unit;
        this.price = price;
        this.quantity = quantity;
        this.subtotal = subtotal;
        this.imageUrl = imageUrl;
    }

    public static OrderItemResponse fromEntity(OrderItem item) {
        if (item == null) {
            return null;
        }
        String img = item.getProduct() != null ? item.getProduct().getImageUrl() : null;
        Long pId = item.getProduct() != null ? item.getProduct().getId() : null;

        return new OrderItemResponse(
                item.getId(),
                pId,
                item.getProductName(),
                item.getUnit(),
                item.getPrice(),
                item.getQuantity(),
                item.getSubtotal(),
                img
        );
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public ProductUnit getUnit() {
        return unit;
    }

    public void setUnit(ProductUnit unit) {
        this.unit = unit;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
