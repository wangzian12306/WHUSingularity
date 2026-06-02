package com.lubover.singularity.user.mapper;

import com.lubover.singularity.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface UserMapper {

    int insert(User user);

    int updateById(User user);

    int deleteById(@Param("id") Long id);

    User selectByUsername(@Param("username") String username);

    User selectById(@Param("id") Long id);

    List<User> selectAll();

    int updateBalance(@Param("id") Long id, @Param("balance") BigDecimal balance);
}
