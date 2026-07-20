package com.selflearning.authservice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("auth_login_log")
public class AuthLoginLog {

    /**
     * 登录日志ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 登录IP
     */
    private String loginIp;

    /**
     * 用户代理信息
     */
    private String userAgent;

    /**
     * 登录状态，1 表示成功，0 表示失败
     */
    private Integer loginStatus;

    /**
     * 登录失败原因
     */
    private String failReason;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
