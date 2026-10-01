package com.fops.api.dto;

import com.fops.domain.model.Item;

/**
 * Item as listed by GET /api/items: every ItemResponse field plus the units still owed to its open orders.
 * A separate type so the other item endpoints, which share ItemResponse, keep their exact payload.
 */
public class ItemWithDemandResponse extends ItemResponse {

    private long outstandingDemand;

    public static ItemWithDemandResponse from(Item item, long outstandingDemand) {
        ItemWithDemandResponse response = new ItemWithDemandResponse();
        ItemResponse base = ItemResponse.from(item);
        response.setId(base.getId());
        response.setName(base.getName());
        response.setSku(base.getSku());
        response.setStockOnHand(base.getStockOnHand());
        response.setCreatedAt(base.getCreatedAt());
        response.outstandingDemand = outstandingDemand;
        return response;
    }

    public long getOutstandingDemand() {
        return outstandingDemand;
    }

    public void setOutstandingDemand(long outstandingDemand) {
        this.outstandingDemand = outstandingDemand;
    }
}
