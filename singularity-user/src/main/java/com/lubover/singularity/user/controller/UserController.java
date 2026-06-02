package com.lubover.singularity.user.controller;

import com.lubover.singularity.user.auth.JwtProvider;
import com.lubover.singularity.user.auth.TokenBlacklistService;
import com.lubover.singularity.user.auth.AuthFilter;
import com.lubover.singularity.user.auth.AuthRequestContext;
import com.lubover.singularity.user.dto.ApiResponse;
import com.lubover.singularity.user.dto.LoginResponse;
import com.lubover.singularity.user.dto.UserView;
import com.lubover.singularity.user.entity.User;
import com.lubover.singularity.user.exception.BusinessException;
import com.lubover.singularity.user.exception.ErrorCode;
import com.lubover.singularity.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserView>> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String nickname = request.get("nickname");

        User user = userService.register(username, password, nickname);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(UserView.from(user)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        User user = userService.login(username, password);
        String accessToken = jwtProvider.generateToken(user.getId(), user.getRole());
        LoginResponse loginResponse = new LoginResponse("Bearer", accessToken, jwtProvider.getExpireSeconds(), UserView.from(user));
        return ApiResponse.success(loginResponse);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        AuthRequestContext authContext = requireAuthContext(request);
        String jti = authContext.getJti();
        long exp = authContext.getExp();

        if (!tokenBlacklistService.isBlacklisted(jti)) {
            tokenBlacklistService.blacklist(jti, exp);
        }
        return ApiResponse.successMessage("logged out");
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me(HttpServletRequest request) {
        AuthRequestContext authContext = requireAuthContext(request);
        Long userId = authContext.getUserId();

        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "User not found");
        }
        return ApiResponse.success(UserView.from(user));
    }

    @GetMapping("/admin/ping")
    public ApiResponse<Map<String, String>> adminPing() {
        return ApiResponse.success(Collections.singletonMap("message", "admin ok"));
    }

    private AuthRequestContext requireAuthContext(HttpServletRequest request) {
        Object contextObj = request.getAttribute(AuthFilter.ATTR_AUTH_CONTEXT);
        if (contextObj instanceof AuthRequestContext context) {
            return context;
        }

        Object userIdObj = request.getAttribute(AuthFilter.ATTR_USER_ID);
        Object roleObj = request.getAttribute(AuthFilter.ATTR_ROLE);
        Object jtiObj = request.getAttribute(AuthFilter.ATTR_JTI);
        Object expObj = request.getAttribute(AuthFilter.ATTR_EXP);
        if (!(userIdObj instanceof Long userId)
                || !(roleObj instanceof String role)
                || !(jtiObj instanceof String jti)
                || expObj == null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "token invalid");
        }

        long exp;
        try {
            exp = Long.parseLong(String.valueOf(expObj));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "token invalid");
        }
        return new AuthRequestContext(userId, role, jti, exp);
    }

    @GetMapping("/{id}")
    public Map<String, Object> getUserById(@PathVariable("id") Long id) {
        User user = userService.getUserById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", user != null);
        result.put("data", user);
        return result;
    }

    @GetMapping("/list")
    public Map<String, Object> getAllUsers() {
        List<User> users = userService.getAllUsers();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", users);
        return result;
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateUser(@PathVariable("id") Long id, @RequestBody Map<String, Object> request) {
        String password = (String) request.get("password");
        String nickname = (String) request.get("nickname");
        String role = (String) request.get("role");
        BigDecimal balance = request.get("balance") != null ? new BigDecimal(request.get("balance").toString()) : null;

        User user = userService.updateUser(id, password, nickname, role, balance);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", user);
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteUser(@PathVariable("id") Long id) {
        boolean deleted = userService.deleteUser(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", deleted);
        result.put("message", deleted ? "User deleted successfully" : "User not found");
        return result;
    }

    @PostMapping("/{id}/recharge")
    public Map<String, Object> recharge(@PathVariable("id") Long id, @RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        boolean success = userService.recharge(id, amount);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "Recharge successful" : "Recharge failed");
        return result;
    }

    @PostMapping("/{id}/deduct")
    public Map<String, Object> deduct(@PathVariable("id") Long id, @RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        boolean success = userService.deduct(id, amount);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "Deduct successful" : "Deduct failed");
        return result;
    }
}
