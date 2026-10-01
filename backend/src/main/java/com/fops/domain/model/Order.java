package com.fops.domain.model;

import com.fops.domain.enums.OrderStatus;
import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.InvalidOrderStateException;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders", indexes = {
        // FIFO allocation: item_id = ? and status in (open) order by created_at, id — runs under the item lock
        @Index(name = "idx_orders_item_status_created", columnList = "item_id, status, created_at, id"),
        // Foreign key checked when a user is deleted (and existsByUserId)
        @Index(name = "idx_orders_user", columnList = "user_id"),
        // Per-item outstanding demand: status in (open) group by item_id summing remaining_quantity — index-only scan
        @Index(name = "idx_orders_status_item_remaining", columnList = "status, item_id, remaining_quantity")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private Integer requestedQuantity;

    @Column(nullable = false)
    private Integer fulfilledQuantity;

    @Column(nullable = false)
    private Integer remainingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private LocalDateTime cancelledAt;

    /** Client Idempotency-Key; the unique constraint makes a retried creation fail instead of duplicating the order. */
    @Column(name = "request_id", length = 64, unique = true)
    private String requestId;

    protected Order() {
    }

    public Order(User user, Item item, Integer requestedQuantity) {
        if (requestedQuantity == null || requestedQuantity <= 0) {
            throw new BusinessRuleException("Order quantity must be greater than 0");
        }
        this.user = user;
        this.item = item;
        this.requestedQuantity = requestedQuantity;
        this.fulfilledQuantity = 0;
        this.remainingQuantity = requestedQuantity;
        this.status = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Item getItem() {
        return item;
    }

    public Integer getRequestedQuantity() {
        return requestedQuantity;
    }

    public Integer getFulfilledQuantity() {
        return fulfilledQuantity;
    }

    public Integer getRemainingQuantity() {
        return remainingQuantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getRequestId() {
        return requestId;
    }

    public void assignRequestId(String requestId) {
        this.requestId = requestId;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public boolean isOpen() {
        return status == OrderStatus.PENDING || status == OrderStatus.PARTIALLY_FULFILLED;
    }

    /**
     * Withdraws an open order. Its allocations are returned to stock by the caller through compensating
     * movements; fulfilledQuantity keeps recording what had been allocated before the cancellation.
     */
    public void cancel(LocalDateTime now) {
        if (status == OrderStatus.COMPLETED) {
            throw new InvalidOrderStateException("Order " + id + " is already completed and its notification was sent; it cannot be cancelled");
        }
        if (status == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException("Order " + id + " is already cancelled");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = now;
    }

    /**
     * completionPercent = fulfilledQuantity / requestedQuantity * 100, rounded to 2 decimals.
     */
    public double getCompletionPercent() {
        return Math.round(fulfilledQuantity * 10000.0 / requestedQuantity) / 100.0;
    }

    /**
     * The only way to advance fulfillment: completion derives from stock allocation and is never set by hand.
     */
    public void allocate(int quantity) {
        if (status == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException("Order " + id + " is cancelled and cannot receive stock");
        }
        if (quantity <= 0) {
            throw new BusinessRuleException("Allocated quantity must be greater than 0");
        }
        if (quantity > remainingQuantity) {
            throw new BusinessRuleException(
                    "Cannot allocate " + quantity + " units to order " + id + ": only " + remainingQuantity + " remaining");
        }
        this.fulfilledQuantity += quantity;
        this.remainingQuantity = requestedQuantity - fulfilledQuantity;
        updateStatus();
    }

    private void updateStatus() {
        if (remainingQuantity == 0) {
            this.status = OrderStatus.COMPLETED;
            if (this.completedAt == null) {
                this.completedAt = LocalDateTime.now();
            }
            return;
        }

        this.status = fulfilledQuantity > 0 ? OrderStatus.PARTIALLY_FULFILLED : OrderStatus.PENDING;
    }
}
