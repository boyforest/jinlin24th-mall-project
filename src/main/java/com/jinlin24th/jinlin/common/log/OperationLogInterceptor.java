package com.jinlin24th.jinlin.common.log;

import com.alibaba.fastjson2.JSON;
import com.jinlin24th.jinlin.common.mq.dto.OperationLogMessageDTO;
import com.jinlin24th.jinlin.common.mq.producer.OperationLogProducer;
import com.jinlin24th.jinlin.common.util.ClientIpUtil;
import com.jinlin24th.jinlin.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 后台操作日志拦截器。
 * <p>
 * 自动捕获 /admin/** 下的 POST、PUT、DELETE 请求，并通过 RocketMQ 异步写入 sys_operation_log 表。
 */
@Slf4j
@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "operationLogStartTime";

    private final JwtUtil jwtUtil;
    private final ObjectProvider<OperationLogProducer> operationLogProducerProvider;

    public OperationLogInterceptor(JwtUtil jwtUtil, ObjectProvider<OperationLogProducer> operationLogProducerProvider) {
        this.jwtUtil = jwtUtil;
        this.operationLogProducerProvider = operationLogProducerProvider;
    }

    /**
     * 请求开始时记录时间，后续计算接口耗时。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    /**
     * 请求结束后异步发送操作日志消息。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!shouldRecord(request)) {
            return;
        }

        OperationLogProducer producer = operationLogProducerProvider.getIfAvailable();
        if (producer == null) {
            log.debug("RocketMQ 未开启，跳过异步操作日志: uri={}", request.getRequestURI());
            return;
        }

        try {
            OperationLogMessageDTO message = buildMessage(request, response, ex);
            producer.send(message);
        } catch (Exception e) {
            log.error("发送操作日志 MQ 消息失败: uri={}", request.getRequestURI(), e);
        }
    }

    /**
     * 只记录后台增删改请求，查询请求不进入日志表。
     */
    private boolean shouldRecord(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/admin/")
            && Arrays.asList("POST", "PUT", "DELETE").contains(request.getMethod().toUpperCase())
            && !request.getRequestURI().equals("/admin/login")
            // 账号操作在业务事务内同步审计，不依赖 MQ，避免重复写入。
            && !request.getRequestURI().startsWith("/admin/accounts")
            && !request.getRequestURI().startsWith("/admin/password");
    }

    /**
     * 组装操作日志消息体。
     */
    private OperationLogMessageDTO buildMessage(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        OperationLogMessageDTO message = new OperationLogMessageDTO();
        message.setMessageKey("oplog:" + UUID.randomUUID());
        message.setUsername(parseAdminUsername(request));
        message.setModule(parseModule(request.getRequestURI()));
        message.setOperation(parseOperation(request.getMethod()));
        message.setRequestMethod(request.getMethod());
        message.setRequestUri(request.getRequestURI());
        message.setRequestParams(JSON.toJSONString(request.getParameterMap().entrySet().stream()
            .filter(entry -> !entry.getKey().toLowerCase(java.util.Locale.ROOT).matches(".*(password|token|secret|authorization|hash).*"))
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> Arrays.asList(entry.getValue())))));
        message.setIp(clientIp(request));
        message.setStatus(ex == null && response.getStatus() < 400 ? 1 : 0);
        message.setErrorMessage(ex == null ? null : ex.getMessage());
        message.setCostMs(costMs(request));
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    /**
     * 从 Bearer Token 中解析管理员账号。
     */
    private String parseAdminUsername(HttpServletRequest request) {
        try {
            String authorization = request.getHeader("Authorization");
            if (authorization == null || !authorization.regionMatches(true, 0, "Bearer", 0, 6)) {
                return null;
            }
            return jwtUtil.getSubjectFromToken(authorization.substring(6).trim());
        } catch (Exception e) {
            log.warn("解析操作日志管理员账号失败", e);
            return null;
        }
    }

    /**
     * 从 URI 中提取模块名，例如 /admin/product/1 -> product。
     */
    private String parseModule(String uri) {
        String[] parts = uri.split("/");
        return parts.length > 2 ? parts[2] : "admin";
    }

    /**
     * 将 HTTP 方法转换为业务操作。
     */
    private String parseOperation(String method) {
        return switch (method.toUpperCase()) {
            case "POST" -> "CREATE";
            case "PUT" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> method.toUpperCase();
        };
    }

    /**
     * 获取客户端真实 IP，用于操作日志审计。
     * <p>
     * 统一走 {@link ClientIpUtil} 取 X-Forwarded-For 最右侧值：
     * 此前取首值（split(",")[0]），会把审计日志的 IP 写成攻击者任意伪造的内容，
     * 出事后无法追溯真实来源。
     */
    private String clientIp(HttpServletRequest request) {
        return ClientIpUtil.resolve(request);
    }

    /**
     * 计算接口耗时。
     */
    private Long costMs(HttpServletRequest request) {
        Object start = request.getAttribute(START_TIME_ATTR);
        return start instanceof Long startTime ? System.currentTimeMillis() - startTime : null;
    }
}
