package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色权限关联实体。
 * <p>
 * 对应 sys_role_permission 表，一个角色可以绑定多个权限。
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色 ID。
     */
    private Long roleId;

    /**
     * 权限 ID。
     */
    private Long permissionId;

    /**
     * 创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
