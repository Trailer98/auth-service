package com.selflearning.authservice.application.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("auth_application")
public class AuthApplication {

    /**
     * 应用ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 应用编码
     */
    private String applicationCode;

    /**
     * 应用名称
     */
    private String applicationName;

    /**
     * 应用描述
     */
    private String description;

    /**
     * 应用状态，1 表示启用，0 表示停用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记
     */
    private Boolean deleted;
}
