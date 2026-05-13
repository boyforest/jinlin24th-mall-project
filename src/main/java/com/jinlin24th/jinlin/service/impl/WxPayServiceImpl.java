package com.jinlin24th.jinlin.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinlin24th.jinlin.common.config.WxPayConfig;
import com.jinlin24th.jinlin.common.util.WxPayUtil;
import com.jinlin24th.jinlin.common.config.WxPayClient;
import com.jinlin24th.jinlin.mapper.PaymentRecordMapper;
import com.jinlin24th.jinlin.mapper.OrderMasterMapper;
import com.jinlin24th.jinlin.pojo.entity.PaymentRecord;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.pojo.dto.WxPayPrepayDTO;
import com.jinlin24th.jinlin.service.DistributionService;
import com.jinlin24th.jinlin.service.WxPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信支付服务实现类
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "wx.pay", name = "enabled", havingValue = "true")
public class WxPayServiceImpl implements WxPayService {

    @Autowired
    private WxPayConfig wxPayConfig;

    @Autowired
    private WxPayClient wxPayClient;

    @Autowired
    private PaymentRecordMapper paymentRecordMapper;

    @Autowired
    private OrderMasterMapper orderMasterMapper;

    @Autowired
    private DistributionService distributionService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(WxPayPrepayDTO prepayDTO, Long userId) throws Exception {
        log.info("创建微信支付订单，订单号：{}，金额：{}", prepayDTO.getOutTradeNo(), prepayDTO.getTotalAmount());

        // 1. 构建请求参数
        Map<String, Object> params = new HashMap<>();
        params.put("mchid", wxPayConfig.getMchId());
        params.put("out_trade_no", prepayDTO.getOutTradeNo());
        params.put("appid", prepayDTO.getAppid());
        params.put("description", prepayDTO.getDescription());
        params.put("notify_url", wxPayConfig.getNotifyUrl());

        // 订单金额信息
        Map<String, Object> amount = new HashMap<>();
        amount.put("total", prepayDTO.getTotalAmount().multiply(new BigDecimal("100")).intValue()); // 单位：分
        amount.put("currency", "CNY");
        params.put("amount", amount);

        // 场景信息（小程序支付）
        Map<String, Object> payer = new HashMap<>();
        payer.put("openid", prepayDTO.getOpenid());
        params.put("payer", payer);

        // 附加信息
        Map<String, Object> settle_info = new HashMap<>();
        settle_info.put("profit_sharing", false); // 是否分账
        params.put("settle_info", settle_info);

        // 附加数据（JSON字符串）
        Map<String, Object> attach = new HashMap<>();
        attach.put("userId", userId);
        attach.put("orderType", prepayDTO.getOrderType());
        params.put("attach", objectMapper.writeValueAsString(attach));

        // 2. 发送请求到微信支付
        String url = "/v3/pay/transactions/jsapi";
        String response = wxPayClient.sendPost(url, objectMapper.writeValueAsString(params));

        JsonNode jsonNode = objectMapper.readTree(response);
        if (!jsonNode.has("prepay_id")) {
            throw new Exception("微信支付下单失败：" + response);
        }

        String prepayId = jsonNode.get("prepay_id").asText();
        log.info("微信支付下单成功，prepayId：{}", prepayId);

        // 4. 保存支付记录
        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setPaymentNo(generatePaymentNo());
        paymentRecord.setOrderNo(prepayDTO.getOutTradeNo());
        paymentRecord.setUserId(userId);
        paymentRecord.setTotalAmount(prepayDTO.getTotalAmount());
        paymentRecord.setPayAmount(prepayDTO.getTotalAmount());
        paymentRecord.setPayType(1); // 微信支付
        paymentRecord.setTradeType("JSAPI");
        paymentRecord.setPrepayId(prepayId);
        paymentRecord.setStatus(0); // 待支付
        paymentRecord.setRefundStatus(0); // 未退款
        paymentRecord.setCreateTime(LocalDateTime.now());
        paymentRecord.setUpdateTime(LocalDateTime.now());

        paymentRecordMapper.insert(paymentRecord);

        return prepayId;
    }

    @Override
    public String queryOrder(String outTradeNo) throws Exception {
        log.info("查询微信支付订单，订单号：{}", outTradeNo);

        String url = "/v3/pay/transactions/out-trade-no/" + outTradeNo + "?mchid=" + wxPayConfig.getMchId();
        return wxPayClient.sendGet(url);
    }

    @Override
    public boolean closeOrder(String outTradeNo) throws Exception {
        log.info("关闭微信支付订单，订单号：{}", outTradeNo);

        String url = "/v3/pay/transactions/out-trade-no/" + outTradeNo + "/close";

        Map<String, Object> params = new HashMap<>();
        params.put("mchid", wxPayConfig.getMchId());

        String response = wxPayClient.sendPost(url, objectMapper.writeValueAsString(params));
        log.info("关闭订单响应：{}", response);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String refund(String outTradeNo, String outRefundNo, BigDecimal totalAmount, BigDecimal refundAmount) throws Exception {
        log.info("申请退款，订单号：{}，退款单号：{}，退款金额：{}", outTradeNo, outRefundNo, refundAmount);

        // 1. 构建请求参数
        Map<String, Object> params = new HashMap<>();
        params.put("out_trade_no", outTradeNo);
        params.put("out_refund_no", outRefundNo);
        params.put("notify_url", wxPayConfig.getRefundNotifyUrl());

        Map<String, Object> amount = new HashMap<>();
        amount.put("refund", refundAmount.multiply(new BigDecimal("100")).intValue());
        amount.put("total", totalAmount.multiply(new BigDecimal("100")).intValue());
        amount.put("currency", "CNY");
        params.put("amount", amount);

        // 2. 发送请求到微信支付
        String url = wxPayConfig.getApiBaseUrl() + "/v3/refund/domestic/refunds";
        String response = wxPayClient.sendPost(url, objectMapper.writeValueAsString(params));

        // 3. 解析响应
        JsonNode jsonNode = objectMapper.readTree(response);
        if (!jsonNode.has("refund_id")) {
            throw new Exception("微信退款申请失败：" + response);
        }

        String refundId = jsonNode.get("refund_id").asText();
        log.info("微信退款申请成功，退款ID：{}", refundId);

        // 4. 更新支付记录
        PaymentRecord paymentRecord = paymentRecordMapper.selectByOrderNo(outTradeNo);
        if (paymentRecord != null) {
            paymentRecord.setRefundAmount(refundAmount);
            paymentRecord.setRefundStatus(1); // 退款中
            paymentRecord.setRefundId(refundId);
            paymentRecord.setUpdateTime(LocalDateTime.now());
            paymentRecordMapper.updateById(paymentRecord);
        }

        return refundId;
    }

    @Override
    public String queryRefund(String outRefundNo) throws Exception {
        log.info("查询退款，退款单号：{}", outRefundNo);

        String url = "/v3/refund/domestic/refunds/" + outRefundNo;
        return wxPayClient.sendGet(url);
    }

    @Override
    public String downloadBill(String billDate, String billType) throws Exception {
        log.info("下载对账单，日期：{}，类型：{}", billDate, billType);

        String url = "/v3/billdownload/bill?type=" + billType + "&bill_date=" + billDate;
        return wxPayClient.sendGet(url);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handlePaymentNotify(String notifyData) throws Exception {
        log.info("处理支付结果通知");

        try {
            // 1. 解析JSON通知
            JsonNode notifyNode = objectMapper.readTree(notifyData);

            // 2. 解密回调数据
            String cipherText = notifyNode.get("resource").get("ciphertext").asText();
            String associatedData = notifyNode.get("resource").get("associated_data").asText();
            String nonce = notifyNode.get("resource").get("nonce").asText();

            String decryptedData = WxPayUtil.aesAeadDecrypt(
                wxPayConfig.getApiV3Key().getBytes(),
                associatedData.getBytes(),
                nonce.getBytes(),
                Base64.getDecoder().decode(cipherText)
            );

            // 3. 解析JSON
            JsonNode jsonNode = objectMapper.readTree(decryptedData);
            String outTradeNo = jsonNode.get("out_trade_no").asText();
            String transactionId = jsonNode.get("transaction_id").asText();
            String tradeState = jsonNode.get("trade_state").asText();
            BigDecimal amount = new BigDecimal(jsonNode.get("amount").get("total").asText()).divide(new BigDecimal("100"));

            log.info("支付回调，订单号：{}，交易号：{}，状态：{}，金额：{}", outTradeNo, transactionId, tradeState, amount);

            // 4. 更新支付记录
            PaymentRecord paymentRecord = paymentRecordMapper.selectByOrderNo(outTradeNo);
            if (paymentRecord == null) {
                log.error("支付记录不存在，订单号：{}", outTradeNo);
                return false;
            }

            // 幂等性检查：如果已处理则跳过
            if (paymentRecord.getStatus() == 1) {
                log.info("订单已处理，订单号：{}", outTradeNo);
                return true;
            }

            // 只处理支付成功状态
            if (!"SUCCESS".equals(tradeState)) {
                log.warn("支付状态非成功，订单号：{}，状态：{}", outTradeNo, tradeState);
                paymentRecord.setStatus(2); // 支付失败
                paymentRecord.setNotifyTime(LocalDateTime.now());
                paymentRecord.setUpdateTime(LocalDateTime.now());
                paymentRecordMapper.updateById(paymentRecord);
                return false;
            }

            // 更新支付记录
            paymentRecord.setTransactionId(transactionId);
            paymentRecord.setStatus(1); // 支付成功
            paymentRecord.setPayTime(LocalDateTime.now());
            paymentRecord.setNotifyTime(LocalDateTime.now());
            paymentRecord.setUpdateTime(LocalDateTime.now());
            paymentRecordMapper.updateById(paymentRecord);

            // 5. 更新订单状态
            OrderMaster orderMaster = orderMasterMapper.selectByOrderNo(outTradeNo);
            if (orderMaster != null) {
                orderMaster.setStatus(10); // 待发货
                orderMaster.setPayType(1); // 微信支付
                orderMaster.setPayTime(LocalDateTime.now());
                orderMaster.setUpdateTime(LocalDateTime.now());
                orderMasterMapper.updateById(orderMaster);

                // 支付成功后生成分销佣金记录：仅具备分销资格的上级才会产生佣金，distribution.uk_order_id 保证幂等。
                distributionService.createForPaidOrder(orderMaster);
            }

            log.info("支付处理完成，订单号：{}", outTradeNo);
            return true;
        } catch (Exception e) {
            log.error("处理支付回调失败", e);
            throw new Exception("处理支付回调失败：" + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleRefundNotify(String notifyData) throws Exception {
        log.info("处理退款结果通知");

        try {
            // 1. 解析JSON通知
            JsonNode notifyNode = objectMapper.readTree(notifyData);

            // 2. 解密回调数据
            String cipherText = notifyNode.get("resource").get("ciphertext").asText();
            String associatedData = notifyNode.get("resource").get("associated_data").asText();
            String nonce = notifyNode.get("resource").get("nonce").asText();

            String decryptedData = WxPayUtil.aesAeadDecrypt(
                wxPayConfig.getApiV3Key().getBytes(),
                associatedData.getBytes(),
                nonce.getBytes(),
                Base64.getDecoder().decode(cipherText)
            );

            // 3. 解析JSON
            JsonNode jsonNode = objectMapper.readTree(decryptedData);
            String outRefundNo = jsonNode.get("out_refund_no").asText();
            String refundId = jsonNode.get("refund_id").asText();
            String refundStatus = jsonNode.get("refund_status").asText();
            BigDecimal refundAmount = new BigDecimal(jsonNode.get("amount").get("refund").asText()).divide(new BigDecimal("100"));

            log.info("退款回调，退款单号：{}，退款ID：{}，状态：{}，金额：{}", outRefundNo, refundId, refundStatus, refundAmount);

            // 4. 更新支付记录
            PaymentRecord paymentRecord = paymentRecordMapper.selectByRefundNo(outRefundNo);
            if (paymentRecord == null) {
                log.error("退款记录不存在，退款单号：{}", outRefundNo);
                return false;
            }

            // 幂等性检查
            if (paymentRecord.getRefundStatus() == 2) {
                log.info("退款已处理，退款单号：{}", outRefundNo);
                return true;
            }

            // 更新退款状态
            if ("SUCCESS".equals(refundStatus)) {
                paymentRecord.setRefundStatus(2); // 退款成功
            } else if ("ABNORMAL".equals(refundStatus)) {
                paymentRecord.setRefundStatus(3); // 退款失败
            } else {
                paymentRecord.setRefundStatus(1); // 退款中
            }

            paymentRecord.setRefundTime(LocalDateTime.now());
            paymentRecord.setUpdateTime(LocalDateTime.now());
            paymentRecordMapper.updateById(paymentRecord);

            // 5. 如果退款成功，更新订单状态
            if ("SUCCESS".equals(refundStatus)) {
                OrderMaster orderMaster = orderMasterMapper.selectByOrderNo(paymentRecord.getOrderNo());
                if (orderMaster != null) {
                    orderMaster.setStatus(60); // 已退款
                    orderMaster.setUpdateTime(LocalDateTime.now());
                    orderMasterMapper.updateById(orderMaster);
                }

                // 退款成功后作废该订单对应佣金，避免财务继续结算。
                distributionService.markRefundedByOrderNo(paymentRecord.getOrderNo());
            }

            log.info("退款处理完成，退款单号：{}", outRefundNo);
            return true;
        } catch (Exception e) {
            log.error("处理退款回调失败", e);
            throw new Exception("处理退款回调失败：" + e.getMessage(), e);
        }
    }

    /**
     * 生成支付流水号
     */
    private String generatePaymentNo() {
        return "PAY" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) +
               UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
