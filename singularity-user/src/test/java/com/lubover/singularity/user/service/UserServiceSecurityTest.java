package com.lubover.singularity.user.service;

import com.lubover.singularity.user.entity.User;
import com.lubover.singularity.user.exception.BusinessException;
import com.lubover.singularity.user.exception.ErrorCode;
import com.lubover.singularity.user.mapper.UserMapper;
import com.lubover.singularity.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceSecurityTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "userMapper", userMapper);
        ReflectionTestUtils.setField(userService, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(userService, "passwordEncoder", new BCryptPasswordEncoder());
    }

    @Test
    void registerShouldStorePasswordAsHash() {
        when(userMapper.selectByUsername(eq("alice01"))).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        userService.register("alice01", "P@ssw0rd!", "Alice");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        String storedPassword = userCaptor.getValue().getPassword();

        assertThat(storedPassword).isNotEqualTo("P@ssw0rd!");
        assertThat(storedPassword).startsWith("$2");
    }

    @Test
    void loginShouldMatchBcryptPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = new User();
        user.setId(1001L);
        user.setUsername("alice01");
        user.setRole("normal");
        user.setPassword(encoder.encode("P@ssw0rd!"));

        when(userMapper.selectByUsername(eq("alice01"))).thenReturn(user);

        User result = userService.login("alice01", "P@ssw0rd!");
        assertThat(result.getId()).isEqualTo(1001L);
    }

    @Test
    void loginWrongPasswordShouldReturnBadCredentials() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = new User();
        user.setUsername("alice01");
        user.setPassword(encoder.encode("P@ssw0rd!"));

        when(userMapper.selectByUsername(eq("alice01"))).thenReturn(user);

        assertThatThrownBy(() -> userService.login("alice01", "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_BAD_CREDENTIALS);
    }

    @Test
    void registerDuplicateAtInsertShouldMapTo409Code() {
        when(userMapper.selectByUsername(eq("alice01"))).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> userService.register("alice01", "P@ssw0rd!", "Alice"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_USERNAME_EXISTS);
    }

    @Test
    void registerInvalidUsernameShouldFailFast() {
        assertThatThrownBy(() -> userService.register("a!", "P@ssw0rd!", "Alice"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REQ_INVALID_PARAM);
    }

    @Test
    void loginMissingPasswordShouldFailFast() {
        assertThatThrownBy(() -> userService.login("alice01", ""))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REQ_INVALID_PARAM);
    }
}
