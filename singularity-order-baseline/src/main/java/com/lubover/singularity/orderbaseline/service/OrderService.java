package com.lubover.singularity.orderbaseline.service;

import com.lubover.singularity.orderbaseline.dto.Result;

public interface OrderService {

    Result snagOrder(String userId, String productId);

    Result payOrder(String orderId, String userId);
}
