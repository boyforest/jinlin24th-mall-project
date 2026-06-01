package com.jinlin24th.jinlin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.service.WxPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信支付控制器
 */
@Slf4j
@RestController
@ConditionalOnProperty(prefix = "wx.pay", name = "enabled", havingValue = "true")
public class WxPayController {

    @Autowired
    private WxPayService wxPayService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ═══════════════════════════════════════════════════════════
    // 公开接口（微信支付服务器回调，无需鉴权）
    // ═══════════════════════════════════════════════════════════

    /**
     * 支付结果通知回调（微信支付服务器调用）
     */
    @PostMapping("/api/payment/notify")
    public String paymentNotify(HttpServletRequest request,
                                 @RequestBody String notifyData) {
        try {
            log.info("收到支付回调通知");

            boolean success = wxPayService.handlePaymentNotify(
                request.getHeader("Wechatpay-Timestamp"),
                request.getHeader("Wechatpay-Nonce"),
                request.getHeader("Wechatpay-Signature"),
                request.getHeader("Wechatpay-Serial"),
                notifyData
            );

            if (success) {
                Map<String, Object> response = new HashMap<>();
                response.put("code", "SUCCESS");
                response.put("message", "成功");
                return objectMapper.writeValueAsString(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("code", "FAIL");
                response.put("message", "处理失败");
                return objectMapper.writeValueAsString(response);
            }
        } catch (Exception e) {
            log.error("处理支付回调失败", e);
            try {
                Map<String, Object> response = new HashMap<>();
                response.put("code", "FAIL");
                response.put("message", "处理失败");
                return objectMapper.writeValueAsString(response);
            } catch (Exception ex) {
                return "{\"code\":\"FAIL\",\"message\":\"处理失败\"}";
            }
        }
    }

    /**
     * 退款结果通知回调（微信支付服务器调用）
     */
    @PostMapping("/api/payment/refund/notify")
    public String refundNotify(HttpServletRequest request,
                                @RequestBody String notifyData) {
        try {
            log.info("收到退款回调通知");

            boolean success = wxPayService.handleRefundNotify(
                request.getHeader("Wechatpay-Timestamp"),
                request.getHeader("Wechatpay-Nonce"),
                request.getHeader("Wechatpay-Signature"),
                request.getHeader("Wechatpay-Serial"),
                notifyData
            );

            if (success) {
                Map<String, Object> response = new HashMap<>();
                response.put("code", "SUCCESS");
                response.put("message", "成功");
                return objectMapper.writeValueAsString(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("code", "FAIL");
                response.put("message", "处理失败");
                return objectMapper.writeValueAsString(response);
            }
        } catch (Exception e) {
            log.error("处理退款回调失败", e);
            try {
                Map<String, Object> response = new HashMap<>();
                response.put("code", "FAIL");
                response.put("message", "处理失败");
                return objectMapper.writeValueAsString(response);
            } catch (Exception ex) {
                return "{\"code\":\"FAIL\",\"message\":\"处理失败\"}";
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 管理端接口（需 AdminJwtInterceptor 鉴权，/admin/** 路径）
    // ═══════════════════════════════════════════════════════════

    /**
     * 查询订单
     */
    @GetMapping("/admin/payment/query/{orderNo}")
    public Result<String> queryOrder(@PathVariable String orderNo) {
        try {
            String result = wxPayService.queryOrder(orderNo);
            return Result.success(result);
        } catch (Exception e) {
            log.error("查询订单失败", e);
            throw BizException.of(BizCode.WECHAT_PAY_ERROR, "查询订单失败：" + e.getMessage());
        }
    }

    /**
     * 关闭订单
     */
    @PostMapping("/admin/payment/close/{orderNo}")
    public Result<String> closeOrder(@PathVariable String orderNo) {
        try {
            wxPayService.closeOrder(orderNo);
            return Result.success("关闭订单成功");
        } catch (Exception e) {
            log.error("关闭订单失败", e);
            throw BizException.of(BizCode.WECHAT_PAY_ERROR, "关闭订单失败：" + e.getMessage());
        }
    }

    /**
     * 申请退款
     */
    @PostMapping("/admin/payment/refund")
    public Result<String> refund(
            @RequestParam String orderNo,
            @RequestParam String refundNo,
            @RequestParam BigDecimal totalAmount,
            @RequestParam BigDecimal refundAmount) {
        try {
            String refundId = wxPayService.refund(orderNo, refundNo, totalAmount, refundAmount);
            return Result.success(refundId);
        } catch (Exception e) {
            log.error("申请退款失败", e);
            throw BizException.of(BizCode.WECHAT_PAY_ERROR, "申请退款失败：" + e.getMessage());
        }
    }

    /**
     * 查询退款
     */
    @GetMapping("/admin/payment/refund/query/{refundNo}")
    public Result<String> queryRefund(@PathVariable String refundNo) {
        try {
            String result = wxPayService.queryRefund(refundNo);
            return Result.success(result);
        } catch (Exception e) {
            log.error("查询退款失败", e);
            throw BizException.of(BizCode.WECHAT_PAY_ERROR, "查询退款失败：" + e.getMessage());
        }
    }

    /**
     * 下载对账单
     */
    @GetMapping("/admin/payment/bill")
    public Result<String> downloadBill(
            @RequestParam String billDate,
            @RequestParam(defaultValue = "ALL") String billType) {
        try {
            String result = wxPayService.downloadBill(billDate, billType);
            return Result.success(result);
        } catch (Exception e) {
            log.error("下载对账单失败", e);
            throw BizException.of(BizCode.WECHAT_PAY_ERROR, "下载对账单失败：" + e.getMessage());
        }
    }
}
