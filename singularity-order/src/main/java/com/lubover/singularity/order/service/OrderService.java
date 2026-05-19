package com.lubover.singularity.order.service;

import com.lubover.singularity.api.Actor;
import com.lubover.singularity.api.Result;

public interface OrderService {

    /**
     * Allocate one available slot for an actor and create an order.
     *
     * @param actor requester
     * @return allocation result
     */
    Result snagOrder(Actor actor);

    Result snagOrderByProduct(Actor actor, String productId);

    /**
     * Pay an existing created order.
     *
     * @param orderId order id
     * @param userId requester user id
     * @return payment result
     */
    Result payOrder(String orderId, String userId);
}
