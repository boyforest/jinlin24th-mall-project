package com.jinlin24th.jinlin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinlin24th.jinlin.common.config.WxPayConfig;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.WxPayPrepayDTO;
import com.jinlin24th.jinlin.service.WxPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信支付控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@ConditionalOnProperty(prefix = "wx.pay", name = "enabled", havingValue = "true")
public class WxPayController {

    @Autowired
    private WxPayService wxPayService;

    @Autowired
    private WxPayConfig wxPayConfig;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 统一下单（小程序支付）
     */
    @PostMapping("/create")
    public Result<Map<String, String>> createOrder(@RequestBody WxPayPrepayDTO prepayDTO) {
        try {
            // TODO: 从token中获取userId
            Long userId = 1L;

            // 调用服务创建订单
            String prepayId = wxPayService.createOrder(prepayDTO, userId);

            // 生成小程序支付参数
            String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
            String nonceStr = UUID.randomUUID().toString().replace("-", "");
            String packageValue = "prepay_id=" + prepayId;
            String signType = "RSA";

            // 构建签名参数
            String signStr = prepayDTO.getAppid() + "\n" +
                           timeStamp + "\n" +
                           nonceStr + "\n" +
                           packageValue + "\n";

            // 签名
            PrivateKey privateKey = com.jinlin24th.jinlin.common.util.WxPayUtil.loadPrivateKeyFromPath(wxPayConfig.getPrivateKeyPath());
            String paySign = com.jinlin24th.jinlin.common.util.WxPayUtil.sign(signStr, "SHA256withRSA", privateKey);

            Map<String, String> result = new HashMap<>();
            result.put("timeStamp", timeStamp);
            result.put("nonceStr", nonceStr);
            result.put("package", packageValue);
            result.put("signType", signType);
            result.put("paySign", paySign);
            result.put("prepayId", prepayId);

            return Result.success(result);
        } catch (Exception e) {
            log.error("创建支付订单失败", e);
            return Result.error("创建支付订单失败：" + e.getMessage());
        }
    }

    /**
     * 查询订单
     */
    @GetMapping("/query/{orderNo}")
    public Result<String> queryOrder(@PathVariable String orderNo) {
        try {
            String result = wxPayService.queryOrder(orderNo);
            return Result.success(result);
        } catch (Exception e) {
            log.error("查询订单失败", e);
            return Result.error("查询订单失败：" + e.getMessage());
        }
    }

    /**
     * 关闭订单
     */
    @PostMapping("/close/{orderNo}")
    public Result<String> closeOrder(@PathVariable String orderNo) {
        try {
            wxPayService.closeOrder(orderNo);
            return Result.success("关闭订单成功");
        } catch (Exception e) {
            log.error("关闭订单失败", e);
            return Result.error("关闭订单失败：" + e.getMessage());
        }
    }

    /**
     * 申请退款
     */
    @PostMapping("/refund")
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
            return Result.error("申请退款失败：" + e.getMessage());
        }
    }

    /**
     * 查询退款
     */
    @GetMapping("/refund/query/{refundNo}")
    public Result<String> queryRefund(@PathVariable String refundNo) {
        try {
            String result = wxPayService.queryRefund(refundNo);
            return Result.success(result);
        } catch (Exception e) {
            log.error("查询退款失败", e);
            return Result.error("查询退款失败：" + e.getMessage());
        }
    }

    /**
     * 下载对账单
     */
    @GetMapping("/bill")
    public Result<String> downloadBill(
            @RequestParam String billDate,
            @RequestParam(defaultValue = "ALL") String billType) {
        try {
            String result = wxPayService.downloadBill(billDate, billType);
            return Result.success(result);
        } catch (Exception e) {
            log.error("下载对账单失败", e);
            return Result.error("下载对账单失败：" + e.getMessage());
        }
    }

    /**
     * 支付结果通知回调
     */
    @PostMapping("/notify")
    public String paymentNotify(@RequestBody String notifyData) {
        try {
            log.info("收到支付回调通知");

            // 解析请求头中的时间戳和随机串
            // 实际应该从HttpServletRequest中获取，这里简化处理

            // 处理回调
            boolean success = wxPayService.handlePaymentNotify(notifyData);

            if (success) {
                // 返回成功响应
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
     * 退款结果通知回调
     */
    @PostMapping("/refund/notify")
    public String refundNotify(@RequestBody String notifyData) {
        try {
            log.info("收到退款回调通知");

            // 处理回调
            boolean success = wxPayService.handleRefundNotify(notifyData);

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
}
