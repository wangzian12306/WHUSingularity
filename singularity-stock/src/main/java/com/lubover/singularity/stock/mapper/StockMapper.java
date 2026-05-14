package com.lubover.singularity.stock.mapper;

import com.lubover.singularity.stock.entity.Stock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存Mapper
 */
@Mapper
public interface StockMapper {

    /**
     * 根据productId查询库存
     */
    Stock selectByProductId(@Param("productId") String productId);

    /**
     * 插入库存记录
     */
    int insert(Stock stock);

    /**
     * 单条 SQL 条件扣减：仅当 {@code available_quantity >= quantity} 时，
     * 原子扣减可用、增加预占并递增 version（避免读 version 再写的乐观锁冲突）。
     *
     * @return 影响行数：1 表示成功；0 表示无此行或可用不足
     */
    int deductAvailableAndReserve(@Param("productId") String productId,
                                  @Param("quantity") Long quantity);

    /**
     * 增加已占用库存
     */
    int increaseReservedQuantity(@Param("productId") String productId,
                                  @Param("quantity") Long quantity);

    /**
     * 减少已占用库存
     */
    int decreaseReservedQuantity(@Param("productId") String productId,
                                  @Param("quantity") Long quantity);

    /**
     * 还库存：原子地减少已占用并增加可用库存
     */
    int returnStockQuantity(@Param("productId") String productId,
                            @Param("quantity") Long quantity);

    /**
     * 查询全部库存记录
     */
    List<Stock> selectAll();
}
