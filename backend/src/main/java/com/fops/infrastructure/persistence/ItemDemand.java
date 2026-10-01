package com.fops.infrastructure.persistence;

/**
 * Outstanding demand of one item: the units still owed to its open orders.
 */
public interface ItemDemand {

    Long getItemId();

    Long getDemand();
}
