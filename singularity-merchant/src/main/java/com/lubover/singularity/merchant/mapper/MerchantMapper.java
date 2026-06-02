package com.lubover.singularity.merchant.mapper;

import com.lubover.singularity.merchant.entity.Merchant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantMapper {

    int insert(Merchant merchant);

    int update(Merchant merchant);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    Merchant selectById(@Param("id") Long id);

    Merchant selectByUsername(@Param("username") String username);
}
