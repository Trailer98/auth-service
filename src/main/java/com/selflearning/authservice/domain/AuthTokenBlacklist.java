package com.selflearning.authservice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("auth_token_blacklist")
public class AuthTokenBlacklist {

    /**
     * Token 黑名单记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * JWT 唯一标识
     */
    private String tokenId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * Token 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
