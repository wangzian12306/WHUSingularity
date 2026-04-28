package com.lubover.singularity.user.mapper;

import com.lubover.singularity.user.entity.InventoryChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryChangeLogMapper {

    int insert(InventoryChangeLog log);

    List<InventoryChangeLog> selectByProductId(@Param("productId") Long productId);

    List<InventoryChangeLog> selectByMerchantId(@Param("merchantId") Long merchantId);
}
