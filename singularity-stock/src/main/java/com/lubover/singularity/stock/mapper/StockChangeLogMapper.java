package com.lubover.singularity.stock.mapper;

import com.lubover.singularity.stock.entity.StockChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存变更日志Mapper
 */
@Mapper
public interface StockChangeLogMapper {

    /**
     * 根据messageId查询是否已处理过此消息
     */
    StockChangeLog selectByMessageId(@Param("messageId") String messageId);

    /**
     * 插入库存变更日志
     */
    int insert(StockChangeLog log);

    /**
     * 更新库存变更日志状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("remark") String remark);

    /**
     * 按条件查询库存变更日志
     */
    List<StockChangeLog> selectList(@Param("productId") String productId, @Param("status") Integer status);
}
