package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统权限实体。
 * <p>
 * 对应 sys_permission 表，保存菜单和 API 权限编码。
 */
@Data
@TableName("sys_permission")
public class SysPermission {

    /**
     * 权限 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 权限编码，例如 product:manage。
     */
    private String permissionCode;

    /**
     * 权限名称。
     */
    private String permissionName;

    /**
     * 所属模块。
     */
    private String module;

    /**
     * 类型：1-菜单，2-按钮/API。
     */
    private Integer type;

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
