package com.lubover.singularity.order.service;

import com.lubover.singularity.api.Actor;
import com.lubover.singularity.api.Result;

import java.util.concurrent.CompletableFuture;

public interface OrderService {

    /**
     * Allocate one available slot for an actor and create an order.
     * 异步执行：主线程立即返回 CompletableFuture，三步 I/O 在 worker 线程顺序执行。
     *
     * @param actor requester
     * @return future allocation result
     */
    CompletableFuture<Result> snagOrder(Actor actor);

    CompletableFuture<Result> snagOrderByProduct(Actor actor, String productId);

    /**
     * Pay an existing created order.
     *
     * @param orderId order id
     * @param userId requester user id
     * @return payment result
     */
    Result payOrder(String orderId, String userId);
}
