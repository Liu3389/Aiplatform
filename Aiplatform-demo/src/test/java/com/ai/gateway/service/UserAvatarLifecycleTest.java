package com.ai.gateway.service;

import com.ai.gateway.entity.User;
import com.ai.gateway.mapper.UserContactVerificationMapper;
import com.ai.gateway.mapper.UserMapper;
import com.ai.gateway.mapper.UserOAuthIdentityMapper;
import com.ai.gateway.vo.UserInfoVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAvatarLifecycleTest {
    @Mock private UserMapper userMapper;
    @Mock private UserContactVerificationMapper contactVerificationMapper;
    @Mock private UserOAuthIdentityMapper userOAuthIdentityMapper;
    @Mock private OssService ossService;
    @InjectMocks private UserService userService;

    @Test
    void savesSupportedAvatarUnderUserScopedStorageAndReturnsBrowserUrl() {
        long userId = 42L;
        User user = new User();
        user.setId(userId);
        user.setUsername("alice");
        when(userMapper.selectById(userId)).thenReturn(user);
        when(ossService.uploadBytes(any(byte[].class), eq("avatar.png"), eq("avatars/user_42/"), eq("image/png")))
                .thenReturn("local://avatars/user_42/generated_avatar.png");
        when(ossService.toAccessibleUrl("local://avatars/user_42/generated_avatar.png"))
                .thenReturn("/api/uploads/avatars/user_42/generated_avatar.png");
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        when(contactVerificationMapper.selectById(userId)).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile("file", "profile.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a});
        UserInfoVO result = userService.uploadAvatar(userId, file);

        assertEquals("/api/uploads/avatars/user_42/generated_avatar.png", result.getAvatarUrl());
        assertEquals(true, result.isCustomAvatar());
        assertEquals("local://avatars/user_42/generated_avatar.png", user.getAvatarUrl());
        verify(ossService).uploadBytes(any(byte[].class), eq("avatar.png"), eq("avatars/user_42/"), eq("image/png"));
    }

    @Test
    void rejectsUnsupportedOrMismatchedFileBeforeStorage() {
        MockMultipartFile file = new MockMultipartFile("file", "profile.svg", "image/svg+xml",
                "<svg/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> userService.uploadAvatar(42L, file));
        verify(ossService, never()).uploadBytes(any(byte[].class), anyString(), anyString(), anyString());
    }
}
