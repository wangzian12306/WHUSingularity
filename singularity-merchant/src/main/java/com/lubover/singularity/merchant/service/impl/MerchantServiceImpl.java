package com.lubover.singularity.merchant.service.impl;

import com.lubover.singularity.merchant.auth.AuthRequestContext;
import com.lubover.singularity.merchant.auth.JwtProvider;
import com.lubover.singularity.merchant.auth.TokenBlacklistService;
import com.lubover.singularity.merchant.dto.LoginResponse;
import com.lubover.singularity.merchant.dto.MerchantView;
import com.lubover.singularity.merchant.entity.Merchant;
import com.lubover.singularity.merchant.exception.BusinessException;
import com.lubover.singularity.merchant.exception.ErrorCode;
import com.lubover.singularity.merchant.mapper.MerchantMapper;
import com.lubover.singularity.merchant.service.MerchantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantServiceImpl implements MerchantService {

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public Merchant register(String username, String password, String shopName,
                           String contactName, String contactPhone, String address, String description) {
        if (merchantMapper.selectByUsername(username) != null) {
            throw new BusinessException(ErrorCode.MERCHANT_USERNAME_EXISTS);
        }

        Merchant merchant = new Merchant();
        merchant.setUsername(username);
        merchant.setPassword(passwordEncoder.encode(password));
        merchant.setShopName(shopName);
        merchant.setContactName(contactName);
        merchant.setContactPhone(contactPhone);
        merchant.setAddress(address);
        merchant.setDescription(description);
        merchant.setStatus(1);

        merchantMapper.insert(merchant);
        return merchant;
    }

    @Override
    public LoginResponse login(String username, String password) {
        Merchant merchant = merchantMapper.selectByUsername(username);
        if (merchant == null) {
            throw new BusinessException(ErrorCode.AUTH_BAD_CREDENTIALS);
        }

        if (!passwordEncoder.matches(password, merchant.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_BAD_CREDENTIALS);
        }

        if (merchant.getStatus() != 1) {
            throw new BusinessException(ErrorCode.MERCHANT_STATUS_INVALID);
        }

        try {
            String token = jwtProvider.generateToken(merchant.getId(), merchant.getUsername());
            MerchantView view = convertToView(merchant);

            LoginResponse response = new LoginResponse();
            response.setTokenType("Bearer");
            response.setAccessToken(token);
            response.setExpiresIn(jwtProvider.getExpireSeconds());
            response.setMerchant(view);
            return response;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to generate token");
        }
    }

    @Override
    public void logout(String token) {
        tokenBlacklistService.blacklistToken(token);
    }

    @Override
    @Cacheable(value = "merchant", key = "#a0")
    public Merchant getMerchantById(Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

    @Override
    public Merchant getCurrentMerchant() {
        Long merchantId = AuthRequestContext.getMerchantId();
        if (merchantId == null) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
        return getMerchantById(merchantId);
    }

    @Override
    public MerchantView getMerchantViewById(Long id) {
        Merchant merchant = getMerchantById(id);
        return convertToView(merchant);
    }

    @Override
    public MerchantView getCurrentMerchantView() {
        Merchant merchant = getCurrentMerchant();
        return convertToView(merchant);
    }

    @Override
    @Transactional
    @CachePut(value = "merchant", key = "#a0.id")
    public Merchant updateMerchant(Merchant merchant) {
        Merchant existing = getCurrentMerchant();
        
        if (!existing.getId().equals(merchant.getId())) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }

        merchantMapper.update(merchant);
        return getMerchantById(merchant.getId());
    }

    @Override
    @Transactional
    @CacheEvict(value = "merchant", key = "#a0")
    public void updateMerchantStatus(Long id, Integer status) {
        if (status < 0 || status > 2) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM);
        }
        merchantMapper.updateStatus(id, status);
    }

    private MerchantView convertToView(Merchant merchant) {
        MerchantView view = new MerchantView();
        view.setId(merchant.getId());
        view.setUsername(merchant.getUsername());
        view.setShopName(merchant.getShopName());
        view.setContactName(merchant.getContactName());
        view.setContactPhone(merchant.getContactPhone());
        view.setAddress(merchant.getAddress());
        view.setDescription(merchant.getDescription());
        view.setStatus(merchant.getStatus());
        view.setAvatar(merchant.getAvatar());
        view.setCreateTime(merchant.getCreateTime());
        return view;
    }
}
