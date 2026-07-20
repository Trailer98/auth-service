package com.selflearning.authservice.auth.response;

import com.selflearning.authservice.domain.AuthUser;

public record UserProfile(
        /**
         * 用户ID
         */
        Long id,

        /**
         * 用户名
         */
        String username,

        /**
         * 用户昵称
         */
        String nickname,

        /**
         * 邮箱地址
         */
        String email,

        /**
         * 手机号码
         */
        String phone,

        /**
         * 用户状态，1 表示启用
         */
        Integer status
) {

    public static UserProfile from(AuthUser user) {
        return new UserProfile(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus());
    }
}
