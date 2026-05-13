package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统操作日志实体。
 * <p>
 * 对应 sys_operation_log 表，用于记录后台管理员的增删改操作。
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    /**
     * 日志 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 管理员 ID。
     */
    private Long adminId;

    /**
     * 管理员账号快照。
     */
    private String username;

    /**
     * 业务模块。
     */
    private String module;

    /**
     * 操作类型。
     */
    private String operation;

    /**
     * 请求方法。
     */
    private String requestMethod;

    /**
     * 请求路径。
     */
    private String requestUri;

    /**
     * 请求参数 JSON。
     */
    private String requestParams;

    /**
     * 客户端 IP。
     */
    private String ip;

    /**
     * 1-成功，0-失败。
     */
    private Integer status;

    /**
     * 错误信息。
     */
    private String errorMessage;

    /**
     * 耗时毫秒。
     */
    private Long costMs;

    /**
     * 创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
