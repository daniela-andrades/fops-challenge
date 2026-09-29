package com.fops.api.controller;

import com.fops.api.dto.OrderResponse;
import com.fops.application.order.OrderCancellationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order cancellation. There is deliberately no DELETE for orders: an order that consumed stock is cancelled
 * and its stock returned through compensating movements, so the movement history always explains the stock.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderCancellationController {

    private final OrderCancellationService cancellationService;

    public OrderCancellationController(OrderCancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id) {
        return OrderResponse.from(cancellationService.cancel(id));
    }
}
