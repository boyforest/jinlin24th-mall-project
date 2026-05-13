package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员角色关联实体。
 * <p>
 * 对应 sys_admin_role 表，一个管理员可以拥有多个角色。
 */
@Data
@TableName("sys_admin_role")
public class SysAdminRole {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 管理员 ID。
     */
    private Long adminId;

    /**
     * 角色 ID。
     */
    private Long roleId;

    /**
     * 创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
