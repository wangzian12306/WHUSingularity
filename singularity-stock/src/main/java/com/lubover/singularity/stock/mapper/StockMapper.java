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
     * 更新库存（扣库存-乐观锁）
     * 只更新当版本号匹配时的记录
     */
    int updateAvailableQuantity(@Param("productId") String productId,
                                @Param("changeQuantity") Long changeQuantity,
                                @Param("version") Long version);

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
