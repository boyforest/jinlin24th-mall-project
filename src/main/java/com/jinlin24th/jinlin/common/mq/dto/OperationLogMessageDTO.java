package com.jinlin24th.jinlin.common.mq.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台操作日志消息 DTO。
 * <p>
 * Controller 完成增删改请求后异步发送该消息，由消费者写入 sys_operation_log 表。
 */
@Data
public class OperationLogMessageDTO implements Serializable {

    /**
     * 消息唯一键，用于生产排查和消费幂等。
     */
    private String messageKey;

    /**
     * 管理员 ID，目前项目 token 中没有 adminId 时可为空。
     */
    private Long adminId;

    /**
     * 管理员账号快照。
     */
    private String username;

    /**
     * 业务模块，例如 product、order、warehouse。
     */
    private String module;

    /**
     * 操作类型，例如 CREATE、UPDATE、DELETE。
     */
    private String operation;

    /**
     * HTTP 请求方法。
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
     * 失败原因，成功时为空。
     */
    private String errorMessage;

    /**
     * 请求耗时毫秒。
     */
    private Long costMs;

    /**
     * 日志创建时间。
     */
    private LocalDateTime createTime;
}
