package com.lubover.singularity.order.service;

import com.lubover.singularity.api.Actor;
import com.lubover.singularity.api.Result;

public interface OrderService {

    /**
     * snagOrder 为一个 actor 抢占一个 slot 中的资源（下单）
     * @param actor 发起抢占的用户
     * @return 抢占结果
     */
    Result snagOrder(Actor actor);

    /**
     * payOrder 对一笔已创建的订单发起支付，扣除用户余额并将订单状态更新为 PAID
     * @param orderId 订单ID
     * @param userId  发起支付的用户ID
     * @return 支付结果
     */
    Result payOrder(String orderId, String userId);
}
