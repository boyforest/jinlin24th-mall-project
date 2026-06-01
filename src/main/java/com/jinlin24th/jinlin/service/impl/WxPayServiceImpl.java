package com.jinlin24th.jinlin.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinlin24th.jinlin.common.config.WxPayConfig;
import com.jinlin24th.jinlin.common.util.WxPayUtil;
import com.jinlin24th.jinlin.common.config.WxPayClient;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.mapper.AppUserMapper;
import com.jinlin24th.jinlin.mapper.PaymentRecordMapper;
import com.jinlin24th.jinlin.mapper.OrderMasterMapper;
import com.jinlin24th.jinlin.pojo.entity.AppUser;
import com.jinlin24th.jinlin.pojo.entity.PaymentRecord;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.pojo.dto.WxPayPrepayDTO;
import com.jinlin24th.jinlin.pojo.vo.WxPayParamsVO;
import com.jinlin24th.jinlin.service.DistributionService;
import com.jinlin24th.jinlin.service.WxPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.PublicKey;
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
    private AppUserMapper appUserMapper;

    @Autowired
    private OrderMasterMapper orderMasterMapper;

    @Autowired
    private DistributionService distributionService;

    @Value("${wx.miniapp.appid:}")
    private String miniappAppid;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WxPayParamsVO createMiniAppPayParams(Long userId, Long orderId) throws Exception {
        if (userId == null || orderId == null) {
            throw BizException.badRequest("订单参数错误");
        }
        if (isBlank(miniappAppid) || miniappAppid.contains("your-")) {
            throw BizException.badRequest("微信小程序 appid 未配置，无法发起支付");
        }

        OrderMaster order = orderMasterMapper.selectById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            throw BizException.badRequest("订单不存在");
        }
        if (Integer.valueOf(10).equals(order.getStatus())
                || Integer.valueOf(20).equals(order.getStatus())
                || Integer.valueOf(30).equals(order.getStatus())) {
            throw BizException.badRequest("订单已支付，无需重复支付");
        }
        if (!Integer.valueOf(0).equals(order.getStatus())) {
            throw BizException.badRequest("当前订单状态不可支付");
        }
        if (order.getPayAmount() == null || order.getPayAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badRequest("订单金额异常");
        }

        AppUser user = appUserMapper.selectById(userId);
        if (user == null || isBlank(user.getOpenid())) {
            throw BizException.badRequest("用户 openid 缺失，请重新登录");
        }
        if (user.getOpenid().startsWith("mock_")) {
            throw BizException.badRequest("当前为开发模拟 openid，请配置真实小程序 appid/secret 后重新登录再支付");
        }

        WxPayPrepayDTO dto = new WxPayPrepayDTO();
        dto.setAppid(miniappAppid);
        dto.setOutTradeNo(order.getOrderNo());
        dto.setDescription("金霖二十四养订单");
        dto.setTotalAmount(order.getPayAmount());
        dto.setOpenid(user.getOpenid());
        dto.setOrderType("MINIAPP");

        String prepayId = createOrder(dto, userId);
        return buildMiniAppPayParams(miniappAppid, prepayId);
    }

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

        // 4. 保存或刷新支付记录：payment_record.order_no 是唯一键，重复拉起支付时更新 prepayId。
        PaymentRecord paymentRecord = paymentRecordMapper.selectByOrderNo(prepayDTO.getOutTradeNo());
        if (paymentRecord != null && Integer.valueOf(1).equals(paymentRecord.getStatus())) {
            throw BizException.badRequest("订单已支付，无需重复支付");
        }
        if (paymentRecord == null) {
            paymentRecord = new PaymentRecord();
            paymentRecord.setPaymentNo(generatePaymentNo());
            paymentRecord.setOrderNo(prepayDTO.getOutTradeNo());
            paymentRecord.setCreateTime(LocalDateTime.now());
        }
        paymentRecord.setUserId(userId);
        paymentRecord.setTotalAmount(prepayDTO.getTotalAmount());
        paymentRecord.setPayAmount(prepayDTO.getTotalAmount());
        paymentRecord.setPayType(1); // 微信支付
        paymentRecord.setTradeType("JSAPI");
        paymentRecord.setPrepayId(prepayId);
        paymentRecord.setStatus(0); // 待支付
        paymentRecord.setRefundStatus(0); // 未退款
        paymentRecord.setUpdateTime(LocalDateTime.now());

        if (paymentRecord.getId() == null) {
            paymentRecordMapper.insert(paymentRecord);
        } else {
            paymentRecordMapper.updateById(paymentRecord);
        }

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
        //String url = wxPayConfig.getApiBaseUrl() + "/v3/refund/domestic/refunds";
        //这里只能使用相对路径，
        String url = "/v3/refund/domestic/refunds";
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
    public boolean handlePaymentNotify(String wechatpayTimestamp, String wechatpayNonce,
                                        String wechatpaySignature, String wechatpaySerial,
                                        String notifyData) throws Exception {
        log.info("处理支付结果通知");

        // 0. 验签：确认回调确实来自微信支付
        if (!verifyCallbackSignature(wechatpayTimestamp, wechatpayNonce,
                wechatpaySignature, wechatpaySerial, notifyData)) {
            log.error("支付回调验签失败，拒绝处理");
            return false;
        }

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

            // 5. 更新订单状态（CAS：只有待支付状态才能改为已支付，防止与超时取消并发冲突）
            LambdaUpdateWrapper<OrderMaster> orderUpdateWrapper = new LambdaUpdateWrapper<>();
            orderUpdateWrapper
                .set(OrderMaster::getStatus, 10) // 待发货
                .set(OrderMaster::getPayType, 1) // 微信支付
                .set(OrderMaster::getPayTime, LocalDateTime.now())
                .set(OrderMaster::getUpdateTime, LocalDateTime.now())
                .eq(OrderMaster::getOrderNo, outTradeNo)
                .eq(OrderMaster::getStatus, 0);  // CAS：只有待支付(0)才能更新
            boolean orderUpdated = orderMasterMapper.update(null, orderUpdateWrapper) > 0;
            if (orderUpdated) {
                log.info("支付回调订单状态更新成功: orderNo={}", outTradeNo);

                // 支付成功后生成分销佣金记录
                OrderMaster orderMaster = orderMasterMapper.selectByOrderNo(outTradeNo);
                if (orderMaster != null) {
                    distributionService.createForPaidOrder(orderMaster);
                }
            } else {
                log.warn("支付回调订单CAS更新失败(订单状态已变化，可能已被超时取消): orderNo={}", outTradeNo);
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
    public boolean handleRefundNotify(String wechatpayTimestamp, String wechatpayNonce,
                                       String wechatpaySignature, String wechatpaySerial,
                                       String notifyData) throws Exception {
        log.info("处理退款结果通知");

        // 0. 验签：确认回调确实来自微信支付
        if (!verifyCallbackSignature(wechatpayTimestamp, wechatpayNonce,
                wechatpaySignature, wechatpaySerial, notifyData)) {
            log.error("退款回调验签失败，拒绝处理");
            return false;
        }

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

            // 5. 如果退款成功，更新订单状态（CAS：只有待发货/已发货状态才改为已退款）
            if ("SUCCESS".equals(refundStatus)) {
                LambdaUpdateWrapper<OrderMaster> orderUpdateWrapper = new LambdaUpdateWrapper<>();
                orderUpdateWrapper
                    .set(OrderMaster::getStatus, 60) // 已退款
                    .set(OrderMaster::getUpdateTime, LocalDateTime.now())
                    .eq(OrderMaster::getOrderNo, paymentRecord.getOrderNo())
                    .in(OrderMaster::getStatus, 10, 20); // CAS：仅待发货(10)或已发货(20)可退款
                boolean orderUpdated = orderMasterMapper.update(null, orderUpdateWrapper) > 0;
                if (orderUpdated) {
                    log.info("退款回调订单状态更新成功: orderNo={}", paymentRecord.getOrderNo());
                } else {
                    log.warn("退款回调订单CAS更新失败(订单状态不满足退款条件): orderNo={}", paymentRecord.getOrderNo());
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
     * 验证微信支付回调签名
     * <p>
     * 根据微信支付 V3 规范，回调签名串格式为:
     * {@code timestamp + "\n" + nonce + "\n" + body + "\n"}
     * 使用微信平台公钥 RSA-SHA256 验签。
     *
     * @return true 签名有效，false 签名无效或缺失必要参数
     */
    private boolean verifyCallbackSignature(String timestamp, String nonce,
                                             String signature, String serial,
                                             String body) {
        if (timestamp == null || nonce == null || signature == null || body == null) {
            log.warn("回调验签参数缺失: timestamp={}, nonce={}, signature={}, bodyLen={}",
                    timestamp, nonce, signature, body != null ? body.length() : 0);
            return false;
        }
        try {
            // 检查时间戳是否在5分钟内
            long ts = Long.parseLong(timestamp);
            long now = System.currentTimeMillis() / 1000;
            if (Math.abs(now - ts) > 300) {
                log.warn("回调时间戳已过期: timestamp={}, now={}, diff={}s", timestamp, now, Math.abs(now - ts));
                return false;
            }

            // 构建验签消息串: timestamp\nnonce\nbody\n
            String message = timestamp + "\n" + nonce + "\n" + body + "\n";

            PublicKey pubKey = wxPayClient.getWechatPayPublicKey();
            boolean valid = WxPayUtil.verify(message, signature, "SHA256withRSA", pubKey);
            if (!valid) {
                log.warn("回调签名不匹配: serial={}", serial);
            }
            return valid;
        } catch (NumberFormatException e) {
            log.warn("回调时间戳格式异常: timestamp={}", timestamp, e);
            return false;
        }
    }

    /**
     * 生成支付流水号
     */
    private String generatePaymentNo() {
        return "PAY" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) +
               UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private WxPayParamsVO buildMiniAppPayParams(String appid, String prepayId) throws Exception {
        String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String packageValue = "prepay_id=" + prepayId;
        String signType = "RSA";
        String signStr = appid + "\n" + timeStamp + "\n" + nonceStr + "\n" + packageValue + "\n";
        String paySign = WxPayUtil.sign(
            signStr,
            "SHA256withRSA",
            WxPayUtil.loadPrivateKeyFromPath(wxPayConfig.getPrivateKeyPath())
        );

        WxPayParamsVO vo = new WxPayParamsVO();
        vo.setAppId(appid);
        vo.setTimeStamp(timeStamp);
        vo.setNonceStr(nonceStr);
        vo.setPackageValue(packageValue);
        vo.setSignType(signType);
        vo.setPaySign(paySign);
        vo.setPrepayId(prepayId);
        return vo;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
