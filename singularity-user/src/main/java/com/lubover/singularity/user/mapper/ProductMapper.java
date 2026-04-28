package com.lubover.singularity.user.mapper;

import com.lubover.singularity.user.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    int insert(Product product);

    int update(Product product);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int incrementSalesCount(@Param("id") Long id, @Param("count") Long count);

    int incrementViewCount(@Param("id") Long id, @Param("count") Long count);

    int deleteById(@Param("id") Long id);

    Product selectById(@Param("id") Long id);

    List<Product> selectByMerchantId(@Param("merchantId") Long merchantId);

    List<Product> selectByMerchantIdWithStatus(@Param("merchantId") Long merchantId, @Param("status") Integer status);
}
