package com.lubover.singularity.product.mapper;

import com.lubover.singularity.product.entity.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductMapper {

    int insert(Product product);

    Product selectByProductId(@Param("productId") String productId);

    List<Product> selectList(@Param("status") Integer status,
	    @Param("category") String category,
	    @Param("keyword") String keyword,
	    @Param("offset") int offset,
	    @Param("limit") int limit);

    long countList(@Param("status") Integer status,
	    @Param("category") String category,
	    @Param("keyword") String keyword);

    int updateByProductId(Product product);

    int updateStatusByProductId(@Param("productId") String productId, @Param("status") Integer status);

    int markDeleted(@Param("productId") String productId);
}
