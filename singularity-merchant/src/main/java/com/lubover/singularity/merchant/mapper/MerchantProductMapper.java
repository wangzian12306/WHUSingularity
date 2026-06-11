package com.lubover.singularity.merchant.mapper;

import com.lubover.singularity.merchant.entity.MerchantProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MerchantProductMapper {

    int insert(MerchantProduct merchantProduct);

    int update(MerchantProduct merchantProduct);

    int delete(@Param("merchantId") Long merchantId, @Param("productId") String productId);

    MerchantProduct selectByMerchantIdAndProductId(@Param("merchantId") Long merchantId,
                                                    @Param("productId") String productId);

    List<MerchantProduct> selectByMerchantId(@Param("merchantId") Long merchantId);

    List<String> selectProductIdsByMerchantId(@Param("merchantId") Long merchantId);

    int updateStatus(@Param("merchantId") Long merchantId,
                     @Param("productId") String productId,
                     @Param("status") Integer status);

    Long selectMerchantIdByProductId(@Param("productId") String productId);
}
