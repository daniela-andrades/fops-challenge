package com.fops.domain.model;

import com.fops.domain.enums.MovementType;
import com.fops.domain.exception.BusinessRuleException;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType movementType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    /**
     * IN movement whose stock fed this OUT movement. Null when it used stock already on hand at order creation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_movement_id")
    private InventoryMovement sourceMovement;

    /**
     * true if this OUT movement brought the order to 100%.
     */
    @Column(name = "completes_order")
    private Boolean completesOrder;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private String reason;

    /** Client Idempotency-Key of an incoming delivery; unique, so a retried registration cannot add stock twice. */
    @Column(name = "request_id", length = 64, unique = true)
    private String requestId;

    protected InventoryMovement() {
    }

    private InventoryMovement(Item item, int quantity, MovementType movementType, Order order,
                              InventoryMovement sourceMovement, boolean completesOrder, String reason) {
        if (quantity <= 0) {
            throw new BusinessRuleException("Movement quantity must be greater than 0");
        }
        this.item = item;
        this.quantity = quantity;
        this.movementType = movementType;
        this.order = order;
        this.sourceMovement = sourceMovement;
        this.completesOrder = completesOrder;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public static InventoryMovement incoming(Item item, int quantity, String reason) {
        return new InventoryMovement(item, quantity, MovementType.IN, null, null, false, reason);
    }

    /**
     * Every OUT movement consumes stock for an order: the link is mandatory.
     */
    public static InventoryMovement allocation(Item item, int quantity, Order order,
                                               InventoryMovement sourceMovement, boolean completesOrder, String reason) {
        if (order == null) {
            throw new BusinessRuleException("An OUT movement must be linked to an order");
        }
        return new InventoryMovement(item, quantity, MovementType.OUT, order, sourceMovement, completesOrder, reason);
    }

    public Long getId() {
        return id;
    }

    public Item getItem() {
        return item;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public Order getOrder() {
        return order;
    }

    public InventoryMovement getSourceMovement() {
        return sourceMovement;
    }

    public boolean isCompletesOrder() {
        return Boolean.TRUE.equals(completesOrder);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getReason() {
        return reason;
    }

    public String getRequestId() {
        return requestId;
    }

    public void assignRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
     * The reason is descriptive metadata; quantity, item and links are immutable to keep the ledger consistent.
     */
    public void changeReason(String reason) {
        this.reason = reason;
    }
}
