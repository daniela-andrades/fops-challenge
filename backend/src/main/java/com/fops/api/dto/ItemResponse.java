package com.fops.api.dto;

import com.fops.domain.model.Item;

import java.time.LocalDateTime;

public class ItemResponse {
    private Long id;
    private String name;
    private String sku;
    private Integer stockOnHand;
    private LocalDateTime createdAt;

    public static ItemResponse from(Item item) {
        ItemResponse response = new ItemResponse();
        response.id = item.getId();
        response.name = item.getName();
        response.sku = item.getSku();
        response.stockOnHand = item.getStockOnHand();
        response.createdAt = item.getCreatedAt();
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public Integer getStockOnHand() {
        return stockOnHand;
    }

    public void setStockOnHand(Integer stockOnHand) {
        this.stockOnHand = stockOnHand;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
