package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统管理员实体。
 * <p>
 * 对应 sys_admin 表，负责 B 端后台登录账号的基础档案。
 */
@Data
@TableName("sys_admin")
public class SysAdmin {

    /**
     * 管理员 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 登录账号。
     */
    private String username;

    /**
     * BCrypt 密码哈希，禁止存储明文密码。
     */
    private String passwordHash;

    /**
     * 真实姓名。
     */
    private String realName;

    /**
     * 手机号。
     */
    private String phone;

    /**
     * 邮箱。
     */
    private String email;

    /**
     * 头像地址。
     */
    private String avatar;

    /**
     * 状态：1-启用，0-禁用。
     */
    private Integer status;

    /**
     * 最后登录时间。
     */
    private LocalDateTime lastLoginTime;

    /**
     * 最后登录 IP。
     */
    private String lastLoginIp;

    /**
     * 逻辑删除：0-未删除，1-已删除。
     */
    @TableLogic
    private Integer deleted;

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
