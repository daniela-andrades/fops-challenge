package com.fops.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class ItemRequest {

    @NotBlank(message = "Item name is required")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "SKU is required")
    @Size(max = 64)
    private String sku;

    @PositiveOrZero(message = "Initial stock cannot be negative")
    private Integer stockOnHand;

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
}
