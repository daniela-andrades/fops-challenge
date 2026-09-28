package com.fops.domain.model;

import com.fops.domain.exception.BusinessRuleException;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Entity
@Table(name = "items")
@Check(constraints = "stock_on_hand >= 0")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private Integer stockOnHand;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Item() {
    }

    /**
     * Creates the item with no stock. Initial stock must come in as an IN movement to keep traceability.
     */
    public Item(String name, String sku) {
        this.name = name;
        this.sku = sku;
        this.stockOnHand = 0;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void increaseStock(int quantity) {
        requirePositive(quantity);
        this.stockOnHand += quantity;
    }

    public void decreaseStock(int quantity) {
        requirePositive(quantity);
        if (quantity > stockOnHand) {
            throw new BusinessRuleException(
                    "Insufficient stock for item " + sku + ": available " + stockOnHand + ", requested " + quantity);
        }
        this.stockOnHand -= quantity;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleException("Movement quantity must be greater than 0");
        }
    }
}
