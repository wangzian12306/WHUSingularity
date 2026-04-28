package com.lubover.singularity.user.mapper;

import com.lubover.singularity.user.entity.ProductInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductInventoryMapper {

    int insert(ProductInventory inventory);

    int update(ProductInventory inventory);

    int incrementAvailable(@Param("productId") Long productId, @Param("quantity") Long quantity, @Param("version") Long version);

    int lockInventory(@Param("productId") Long productId, @Param("quantity") Long quantity, @Param("version") Long version);

    int unlockInventory(@Param("productId") Long productId, @Param("quantity") Long quantity);

    int confirmSale(@Param("productId") Long productId, @Param("quantity") Long quantity);

    ProductInventory selectById(@Param("id") Long id);

    ProductInventory selectByProductId(@Param("productId") Long productId);
}
