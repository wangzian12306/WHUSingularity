package com.lubover.singularity.orderbaseline.mapper;

import com.lubover.singularity.orderbaseline.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderMapper {

    int insert(Order order);

    Order selectByOrderId(@Param("orderId") String orderId);

    int updateStatus(@Param("orderId") String orderId, @Param("status") String status);

    List<Order> selectList(@Param("userId") String userId, @Param("status") String status,
            @Param("offset") int offset, @Param("limit") int limit);

    long countList(@Param("userId") String userId, @Param("status") String status);
}
