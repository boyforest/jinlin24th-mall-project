package com.jinlin24th.jinlin.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinlin24th.jinlin.common.config.WxPayConfig;
import com.jinlin24th.jinlin.common.constant.BizCode;
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
import java.math.RoundingMode;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

    /**
     * 告警日志前缀。便于运维用 `docker logs | grep '[PAY-ALERT]'` 或日志平台按关键字告警。
     */
    private static final String ALERT_TAG = "[PAY-ALERT]";

    /**
     * "用户已支付但订单已失效，等待人工退款"的标记值，写入 payment_record.refund_remark。
     */
    private static final String MANUAL_REFUND_PENDING = "MANUAL_REFUND_PENDING";

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

    /**
     * 申请退款。
     * <p>
     * 关键安全约束（修复"退款接口无任何校验，任意 admin token 可退"）：
     * <ol>
     *   <li>订单总金额以 {@code payment_record.pay_amount} 为准，不接受调用方传入；</li>
     *   <li>退款单号 {@code out_refund_no} 由订单号确定性生成（{@code RF+orderNo}），
     *       微信侧对同一 out_refund_no 只会退款一次，天然防重复退款；</li>
     *   <li>用 CAS 占位"退款中"，防止两个并发请求同时发起退款；</li>
     *   <li>校验订单状态（仅已支付未完成可退）与退款金额上限。</li>
     * </ol>
     * 注意：微信 HTTP 调用放在 DB 写操作之后但在同一事务内，失败时回滚并把
     * 占位的"退款中"状态一并回滚，保证不会出现"标记了退款中但实际没退"的悬挂状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String refund(String outTradeNo, BigDecimal refundAmount) throws Exception {
        if (isBlank(outTradeNo)) {
            throw BizException.badRequest("订单号不能为空");
        }

        // 1. 支付记录是金额与退款状态的唯一事实来源
        PaymentRecord paymentRecord = paymentRecordMapper.selectByOrderNo(outTradeNo);
        if (paymentRecord == null) {
            throw BizException.of(BizCode.PAYMENT_RECORD_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(paymentRecord.getStatus())) {
            throw BizException.of(BizCode.PAYMENT_NOT_REFUNDABLE, "订单未支付成功，不能退款");
        }

        BigDecimal paidAmount = paymentRecord.getPayAmount();
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.of(BizCode.ORDER_AMOUNT_MISMATCH, "支付记录金额异常");
        }

        // 2. 退款金额校验：**仅支持全额退款**
        //
        // 为什么不支持部分退款（这不是"少做功能"，而是避免资金死局）：
        // 退款成功回调会把 refund_status 置 2、订单状态置 60（已退款），
        // 之后任何退款请求都会被这两处校验拦下 —— 于是 ¥100 的订单退了 ¥30 后，
        // 剩下 ¥70 在系统里**永远退不出去**（只能人工转账，账实不符）。
        // 更糟的是回调里作废佣金没有金额判断，部分退款会把该订单**全部**佣金作废。
        //
        // 在实现"累计已退金额 + 唯一退款单号 + 退款流水表"之前（见 docs 规格），
        // 唯一安全的做法就是只允许全额；传部分金额必须在这里明确拒绝。
        BigDecimal actualRefund = refundAmount == null ? paidAmount : refundAmount;
        if (actualRefund.compareTo(paidAmount) != 0) {
            throw BizException.of(BizCode.REFUND_AMOUNT_INVALID,
                "仅支持全额退款（实付 " + paidAmount.toPlainString() + " 元）。"
                    + "部分退款会使剩余金额无法再退，暂不支持。");
        }

        // 3. 幂等校验放在订单状态校验之前：
        //    已退款完成的订单无论当前处于什么状态，都应直接明确拒绝。
        if (Integer.valueOf(2).equals(paymentRecord.getRefundStatus())) {
            throw BizException.of(BizCode.REFUND_NOT_ALLOWED, "该订单已退款完成");
        }

        // 4. 订单状态校验：仅待发货/待收货可退；已退款、已取消、已完成不允许直接退
        OrderMaster order = orderMasterMapper.selectByOrderNo(outTradeNo);
        if (order == null) {
            throw BizException.of(BizCode.ORDER_NOT_FOUND);
        }
        Integer orderStatus = order.getStatus();
        if (!Integer.valueOf(10).equals(orderStatus) && !Integer.valueOf(20).equals(orderStatus)) {
            throw BizException.of(BizCode.PAYMENT_NOT_REFUNDABLE,
                "仅待发货/待收货订单可退款，当前状态：" + orderStatus);
        }

        // 5. 并发防护：CAS 占位退款中（仅允许 未退款(0) / 退款失败(3) 进入）
        int occupied = paymentRecordMapper.update(null, new LambdaUpdateWrapper<PaymentRecord>()
            .set(PaymentRecord::getRefundStatus, 1)          // 退款中
            .set(PaymentRecord::getRefundAmount, actualRefund)
            .set(PaymentRecord::getRefundRemark, null)
            .set(PaymentRecord::getUpdateTime, LocalDateTime.now())
            .eq(PaymentRecord::getId, paymentRecord.getId())
            .in(PaymentRecord::getRefundStatus, 0, 3));
        if (occupied == 0) {
            throw BizException.of(BizCode.REFUND_IN_PROGRESS);
        }

        // 6. 退款单号由服务端确定性生成：同一订单重复调用不会产生第二笔退款
        String outRefundNo = buildOutRefundNo(outTradeNo);

        Map<String, Object> params = new HashMap<>();
        params.put("out_trade_no", outTradeNo);
        params.put("out_refund_no", outRefundNo);
        params.put("notify_url", wxPayConfig.getRefundNotifyUrl());

        Map<String, Object> amount = new HashMap<>();
        amount.put("refund", toFen(actualRefund));
        // total 必须等于原支付订单总额，取服务端记录，绝不信客户端
        amount.put("total", toFen(paidAmount));
        amount.put("currency", "CNY");
        params.put("amount", amount);

        // 7. 发送请求到微信支付（相对路径固定，见 WxPayClient）
        String url = "/v3/refund/domestic/refunds";
        String response;
        try {
            response = wxPayClient.sendPost(url, objectMapper.writeValueAsString(params));
        } catch (Exception e) {
            // 事务会回滚，占位的"退款中"自动还原为 0，允许后续重试
            log.error("调用微信退款接口失败，订单号：{}，退款单号：{}", outTradeNo, outRefundNo, e);
            throw e;
        }

        // 8. 解析响应
        JsonNode jsonNode = objectMapper.readTree(response);
        if (!jsonNode.has("refund_id")) {
            throw new BizException(BizCode.WECHAT_PAY_ERROR, "微信退款申请失败，请查看服务端日志");
        }

        String refundId = jsonNode.get("refund_id").asText();
        log.info("微信退款申请成功，订单号：{}，退款单号：{}，退款ID：{}，金额：{}",
            outTradeNo, outRefundNo, refundId, actualRefund);

        // 9. 落库微信退款单号；refund_status 保持 1（退款中），等回调置 2
        paymentRecordMapper.update(null, new LambdaUpdateWrapper<PaymentRecord>()
            .set(PaymentRecord::getRefundId, refundId)
            .set(PaymentRecord::getRefundAmount, actualRefund)
            .set(PaymentRecord::getUpdateTime, LocalDateTime.now())
            .eq(PaymentRecord::getId, paymentRecord.getId()));

        return refundId;
    }

    /**
     * 生成商户退款单号：{@code RF + 订单号}。
     * <p>
     * 确定性生成是刻意设计——微信对同一 out_refund_no 只允许一次成功退款，
     * 因此重复调用/重试不会造成重复退款。
     */
    private String buildOutRefundNo(String outTradeNo) {
        String candidate = "RF" + outTradeNo;
        return candidate.length() <= 64 ? candidate : candidate.substring(0, 64);
    }

    /**
     * 元转分。使用 {@code movePointRight(2)} + {@code intValueExact()}，
     * 避免 {@code multiply(100).intValue()} 的静默截断与溢出。
     */
    private int toFen(BigDecimal yuan) {
        try {
            return yuan.movePointRight(2).setScale(0, java.math.RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException e) {
            throw BizException.of(BizCode.ORDER_AMOUNT_MISMATCH, "金额精度或数值超出范围：" + yuan.toPlainString());
        }
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
            BigDecimal notifiedAmount = new BigDecimal(jsonNode.get("amount").get("total").asText())
                .divide(new BigDecimal("100"));

            log.info("支付回调，订单号：{}，交易号：{}，状态：{}，金额：{}",
                outTradeNo, transactionId, tradeState, notifiedAmount);

            // 4. 更新支付记录
            PaymentRecord paymentRecord = paymentRecordMapper.selectByOrderNo(outTradeNo);
            if (paymentRecord == null) {
                log.error("支付记录不存在，订单号：{}", outTradeNo);
                return false;
            }

            // 幂等性检查：如果已处理则跳过
            if (Integer.valueOf(1).equals(paymentRecord.getStatus())
                    || Integer.valueOf(3).equals(paymentRecord.getStatus())) {
                log.info("订单已处理，订单号：{}，支付记录状态：{}", outTradeNo, paymentRecord.getStatus());
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

            // 4.1 金额校验（新增）：回调金额必须等于订单实付金额，否则拒绝入账。
            //     防止篡改/串单导致"小额支付换大额订单"。
            BigDecimal expectedAmount = paymentRecord.getPayAmount();
            if (expectedAmount == null || notifiedAmount.compareTo(expectedAmount) != 0) {
                log.error("{} 支付回调金额不一致，拒绝入账。orderNo={}, 回调金额={}, 应付金额={}",
                    ALERT_TAG, outTradeNo, notifiedAmount, expectedAmount);
                return false; // 不落库、返回 FAIL，微信会重试并告警，需人工核对
            }

            // 5. 先做订单状态 CAS（只有待支付(0)才能改为已支付(10)），
            //    成功后再落库支付记录——避免"支付记录已成功但订单没更新"的不一致。
            LambdaUpdateWrapper<OrderMaster> orderUpdateWrapper = new LambdaUpdateWrapper<>();
            orderUpdateWrapper
                .set(OrderMaster::getStatus, 10) // 待发货
                .set(OrderMaster::getPayType, 1) // 微信支付
                .set(OrderMaster::getPayTime, LocalDateTime.now())
                .set(OrderMaster::getUpdateTime, LocalDateTime.now())
                .eq(OrderMaster::getOrderNo, outTradeNo)
                .eq(OrderMaster::getStatus, 0);  // CAS：只有待支付(0)才能更新
            boolean orderUpdated = orderMasterMapper.update(null, orderUpdateWrapper) > 0;

            if (!orderUpdated) {
                return handlePaymentCasConflict(outTradeNo, paymentRecord, notifiedAmount);
            }

            // 5.1 订单已置为已支付，落库支付记录
            paymentRecordMapper.update(null, new LambdaUpdateWrapper<PaymentRecord>()
                .set(PaymentRecord::getTransactionId, transactionId)
                .set(PaymentRecord::getStatus, 1) // 支付成功
                .set(PaymentRecord::getPayTime, LocalDateTime.now())
                .set(PaymentRecord::getNotifyTime, LocalDateTime.now())
                .set(PaymentRecord::getUpdateTime, LocalDateTime.now())
                .eq(PaymentRecord::getId, paymentRecord.getId()));

            log.info("支付回调订单状态更新成功: orderNo={}", outTradeNo);

            // 6. 支付成功后生成分销佣金记录
            OrderMaster orderMaster = orderMasterMapper.selectByOrderNo(outTradeNo);
            if (orderMaster != null) {
                distributionService.createForPaidOrder(orderMaster);
            }

            log.info("支付处理完成，订单号：{}", outTradeNo);
            return true;
        } catch (Exception e) {
            log.error("处理支付回调失败", e);
            throw new Exception("处理支付回调失败：" + e.getMessage(), e);
        }
    }

    /**
     * 处理"用户已支付，但订单已不是待支付状态"的冲突。
     * <p>
     * CAS 失败说明订单在回调到达前已被改变（最典型：被超时任务取消并已归还库存）。
     * 此时钱已经进了商户账户，如果只是打一条 warn 日志就返回成功，就会出现
     * "用户付了钱、订单却是已取消、系统不退款"的资损场景。
     * <p>
     * 处理策略：
     * <ul>
     *   <li>支付记录已被标记为"待人工退款"：返回 false，让微信继续重试直到人工处理；</li>
     *   <li>已经退过款（存在 refund_id）：无需重复处理，返回 true 结束重试；</li>
     *   <li>订单已取消（40）：记录待人工退款标记并返回 false，人工退款完成后
     *       支付记录会被置为已退款，微信再次回调时走上面的分支自动收敛；</li>
     *   <li>其它状态（如已完成）：无法自动退款，打告警并返回 false 交人工处理。</li>
     * </ul>
     */
    private boolean handlePaymentCasConflict(String outTradeNo, PaymentRecord paymentRecord, BigDecimal notifiedAmount) {
        if (MANUAL_REFUND_PENDING.equals(paymentRecord.getRefundRemark())) {
            log.error("{} 已支付订单仍在等待人工退款，继续让微信重试: orderNo={}, 金额={}",
                ALERT_TAG, outTradeNo, notifiedAmount);
            return false;
        }
        if (!isBlank(paymentRecord.getRefundId())) {
            log.warn("支付回调到达时订单已退款，无需重复处理: orderNo={}, refundId={}",
                outTradeNo, paymentRecord.getRefundId());
            return true;
        }

        OrderMaster current = orderMasterMapper.selectByOrderNo(outTradeNo);
        Integer currentStatus = current == null ? null : current.getStatus();

        // 订单已取消且未退款 → 必须人工介入退款，否则用户钱货两空
        paymentRecordMapper.update(null, new LambdaUpdateWrapper<PaymentRecord>()
            .set(PaymentRecord::getRefundStatus, 3) // 退款失败 → 语义为"待人工退款"
            .set(PaymentRecord::getRefundRemark, MANUAL_REFUND_PENDING
                + "：支付回调到达时订单状态=" + currentStatus + "（已支付 " + notifiedAmount.toPlainString() + " 元）")
            .set(PaymentRecord::getUpdateTime, LocalDateTime.now())
            .eq(PaymentRecord::getId, paymentRecord.getId()));

        log.error("{} 用户已支付但订单不可置为已支付，已标记待人工退款。"
                + "orderNo={}, transactionId={}, 金额={}, 订单当前状态={}，请立即人工核实并退款",
            ALERT_TAG, outTradeNo, paymentRecord.getTransactionId(), notifiedAmount, currentStatus);
        return false;
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

            log.info("退款回调，商户退款单号：{}，微信退款ID：{}，状态：{}，金额：{}",
                outRefundNo, refundId, refundStatus, refundAmount);

            // 4. 按微信退款单号（refund_id）查询——这是本方法此前恒失败的原因：
            //    申请退款时落库的是 refund_id，而这里曾用 out_refund_no 去查 refund_id 列。
            PaymentRecord paymentRecord = paymentRecordMapper.selectByRefundId(refundId);
            if (paymentRecord == null) {
                log.error("退款记录不存在，微信退款ID：{}，商户退款单号：{}", refundId, outRefundNo);
                return false;
            }

            // 幂等性检查：已退款成功则直接确认，避免重复改订单/重复作废佣金
            if (Integer.valueOf(2).equals(paymentRecord.getRefundStatus())) {
                log.info("退款已处理，退款ID：{}", refundId);
                return true;
            }

            String mappedStatus;
            if ("SUCCESS".equals(refundStatus)) {
                mappedStatus = "2";  // 退款成功
            } else if ("ABNORMAL".equals(refundStatus) || "CLOSED".equals(refundStatus)) {
                mappedStatus = "3";  // 退款失败/已关闭 → 需人工核实后重试
            } else {
                mappedStatus = "1";  // PROCESSING 等中间态
            }

            boolean refundSucceeded = "2".equals(mappedStatus);
            paymentRecordMapper.update(null, new LambdaUpdateWrapper<PaymentRecord>()
                .set(PaymentRecord::getRefundStatus, Integer.valueOf(mappedStatus))
                .set(PaymentRecord::getRefundAmount, refundAmount)
                .set(PaymentRecord::getRefundTime, LocalDateTime.now())
                // 退款成功时清掉"待人工退款"标记
                .set(refundSucceeded, PaymentRecord::getRefundRemark, null)
                .set(PaymentRecord::getUpdateTime, LocalDateTime.now())
                .eq(PaymentRecord::getId, paymentRecord.getId()));

            // 5. 如果退款成功，更新订单状态（CAS：只有待发货/已发货状态才改为已退款）
            if (refundSucceeded) {
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
            } else if ("3".equals(mappedStatus)) {
                log.error("{} 退款未成功，需人工核实。orderNo={}, refundId={}, refund_status={}",
                    ALERT_TAG, paymentRecord.getOrderNo(), refundId, refundStatus);
            }

            log.info("退款处理完成，退款ID：{}，状态：{}", refundId, mappedStatus);
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
        // 回调头 Wechatpay-Serial 必须等于本地配置的平台公钥 ID，防止用错误公钥验签
        if (!Objects.equals(serial, wxPayClient.getWechatPayPublicKeyId())) {
            log.warn("回调序列号不匹配: headerSerial={}, localPublicKeyId={}",
                    serial, wxPayClient.getWechatPayPublicKeyId());
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
