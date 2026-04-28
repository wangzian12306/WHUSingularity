package com.lubover.singularity.user.service;

import com.lubover.singularity.user.entity.User;

import java.math.BigDecimal;
import java.util.List;

public interface UserService {

    User register(String username, String password, String nickname);

    User login(String username, String password);

    User getUserById(Long id);

    User getUserByUsername(String username);

    List<User> getAllUsers();

    User updateUser(Long id, String password, String nickname, String role, BigDecimal balance);

    boolean deleteUser(Long id);

    boolean recharge(Long id, BigDecimal amount);

    boolean deduct(Long id, BigDecimal amount);

    User registerMerchant(String username, String password, String shopName, String contactName, String contactPhone, String address, String description);

    List<User> getMerchants();

    User updateMerchantInfo(Long id, String shopName, String contactName, String contactPhone, String address, String description, String avatar);

    boolean updateMerchantStatus(Long id, Integer status);
}
