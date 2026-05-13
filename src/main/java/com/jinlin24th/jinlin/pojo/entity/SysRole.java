package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色实体。
 * <p>
 * 对应 sys_role 表，用于后台 RBAC 权限模型。
 */
@Data
@TableName("sys_role")
public class SysRole {

    /**
     * 角色 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色编码，例如 SUPER_ADMIN。
     */
    private String roleCode;

    /**
     * 角色名称。
     */
    private String roleName;

    /**
     * 角色说明。
     */
    private String description;

    /**
     * 状态：1-启用，0-禁用。
     */
    private Integer status;

    /**
     * 排序值。
     */
    private Integer sort;

    /**
     * 创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
