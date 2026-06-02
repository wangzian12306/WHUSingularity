package com.lubover.singularity.order.mapper;

import com.lubover.singularity.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单 Mapper 接口
 */
@Mapper
public interface OrderMapper {

    /**
     * 插入订单记录
     * 
     * @param order 订单实体
     * @return 影响的行数
     */
    int insert(Order order);

    /**
     * 根据订单ID查询订单
     * 
     * @param orderId 订单ID
     * @return 订单实体，不存在则返回 null
     */
    Order selectByOrderId(@Param("orderId") String orderId);

    /**
     * 更新订单状态
     *
     * @param orderId 订单ID
     * @param status 新状态
     * @return 影响的行数
     */
    int updateStatus(@Param("orderId") String orderId, @Param("status") String status);

    /**
     * 条件查询订单列表（分页）
     *
     * @param userId 用户ID
     * @param status 订单状态
     * @param offset 偏移量
     * @param limit  条数
     * @return 订单列表
     */
    List<Order> selectList(@Param("userId") String userId, @Param("status") String status,
                           @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 条件统计订单总数
     *
     * @param userId 用户ID
     * @param status 订单状态
     * @return 总数
     */
    long countList(@Param("userId") String userId, @Param("status") String status);
}
