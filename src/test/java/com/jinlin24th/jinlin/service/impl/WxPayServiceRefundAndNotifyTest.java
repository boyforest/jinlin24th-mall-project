package com.jinlin24th.jinlin.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jinlin24th.jinlin.common.config.WxPayClient;
import com.jinlin24th.jinlin.common.config.WxPayConfig;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.mapper.OrderMasterMapper;
import com.jinlin24th.jinlin.mapper.PaymentRecordMapper;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.pojo.entity.PaymentRecord;
import com.jinlin24th.jinlin.service.DistributionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资金安全回归测试。
 * <p>
 * 覆盖上线前审查发现的三个高危缺陷：
 * <ol>
 *   <li>退款回调按 {@code out_refund_no} 查 {@code refund_id} 列，恒查不到 → 退款成功但订单/佣金不变；</li>
 *   <li>退款接口无任何校验 → 任意金额、并发双退；</li>
 *   <li>支付回调不校验金额、CAS 失败时静默吞掉 → 用户付了钱订单却是已取消且不退款。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WxPayServiceRefundAndNotifyTest {

    private static final String PLATFORM_KEY_ID = "PUBKEY_ID_8888";

    /** AES-256-GCM 要求 32 字节密钥，这里正好 32 个 ASCII 字符。 */
    private static final String API_V3_KEY = "12345678901234567890123456789012";

    private static final String ORDER_NO = "JL20260921000001";

    @Mock
    private WxPayClient wxPayClient;
    @Mock
    private WxPayConfig wxPayConfig;
    @Mock
    private PaymentRecordMapper paymentRecordMapper;
    @Mock
    private OrderMasterMapper orderMasterMapper;
    @Mock
    private DistributionService distributionService;

    private WxPayServiceImpl service;
    private KeyPair platformKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        initTableInfo(PaymentRecord.class);
        initTableInfo(OrderMaster.class);
        initTableInfo(Inventory.class);

        service = new WxPayServiceImpl();
        ReflectionTestUtils.setField(service, "wxPayClient", wxPayClient);
        ReflectionTestUtils.setField(service, "wxPayConfig", wxPayConfig);
        ReflectionTestUtils.setField(service, "paymentRecordMapper", paymentRecordMapper);
        ReflectionTestUtils.setField(service, "orderMasterMapper", orderMasterMapper);
        ReflectionTestUtils.setField(service, "distributionService", distributionService);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        platformKeyPair = generator.generateKeyPair();
        when(wxPayClient.getWechatPayPublicKey()).thenReturn(platformKeyPair.getPublic());
        when(wxPayClient.getWechatPayPublicKeyId()).thenReturn(PLATFORM_KEY_ID);
        when(wxPayConfig.getApiV3Key()).thenReturn(API_V3_KEY);
        when(wxPayConfig.getRefundNotifyUrl()).thenReturn("https://api.example.com/api/payment/refund/notify");
        when(wxPayConfig.getNotifyUrl()).thenReturn("https://api.example.com/api/payment/notify");
    }

    // ══════════════════════════════════════════════════════════
    // 一、退款申请：服务端校验 + 服务端生成退款单号
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("refund: 金额与退款单号均由服务端决定，不接受调用方传入")
    void refundShouldDeriveAmountAndRefundNoFromServerSide() throws Exception {
        PaymentRecord record = paidRecord();
        record.setRefundStatus(0);
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(record);
        when(orderMasterMapper.selectByOrderNo(ORDER_NO)).thenReturn(orderWithStatus(10));
        when(paymentRecordMapper.update(any(), any())).thenReturn(1);
        when(wxPayClient.sendPost(anyString(), anyString()))
            .thenReturn("{\"refund_id\":\"wx-refund-001\",\"status\":\"PROCESSING\"}");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        String refundId = service.refund(ORDER_NO, null); // 不传金额 = 全额退款

        assertEquals("wx-refund-001", refundId);
        verify(wxPayClient).sendPost(anyString(), bodyCaptor.capture());

        var body = new ObjectMapper().readTree(bodyCaptor.getValue());
        // 退款金额取实付金额（99.00 元 → 9900 分），而不是调用方传的值
        assertEquals(9900, body.get("amount").get("refund").asInt());
        // total 同样取服务端记录的实付金额
        assertEquals(9900, body.get("amount").get("total").asInt());
        // 退款单号由服务端确定性生成：RF + 订单号
        assertEquals("RF" + ORDER_NO, body.get("out_refund_no").asText());
    }

    @Test
    @DisplayName("refund: 退款金额超过实付金额必须被拒绝")
    void refundShouldRejectAmountExceedingPaidAmount() {
        PaymentRecord record = paidRecord();
        record.setRefundStatus(0);
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(record);

        BizException ex = assertThrows(BizException.class,
            () -> service.refund(ORDER_NO, new BigDecimal("99.01")));

        assertEquals(30506, ex.getCode()); // REFUND_AMOUNT_INVALID
        // 校验失败时绝不能调用微信
        verify(wxPayClient, never()).sendPost(anyString(), anyString());
    }

    @Test
    @DisplayName("refund: 部分退款必须被拒绝——它会造成无法挽回的资金死局")
    void refundShouldRejectPartialAmount() {
        // 为什么必须拒绝部分退款：
        // 退款成功回调会把 refund_status 置 2、订单状态置 60（已退款），
        // 之后任何退款都会被这两处拦下 —— 于是 ¥100 订单退了 ¥30 后，剩下 ¥70 永远退不出去。
        // 更糟的是回调里作废佣金**没有金额判断**，部分退款也会把该订单全部佣金作废。
        // 在支持"累计已退金额"之前，唯一安全的做法是只允许全额退款。
        PaymentRecord record = paidRecord();
        record.setRefundStatus(0);
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(record);
        when(orderMasterMapper.selectByOrderNo(ORDER_NO)).thenReturn(orderWithStatus(10));

        BizException ex = assertThrows(BizException.class,
            () -> service.refund(ORDER_NO, new BigDecimal("30.00")));

        assertEquals(30506, ex.getCode()); // REFUND_AMOUNT_INVALID
        // 校验必须在 CAS 占位与调用微信之前失败，不能留下"退款中"的残留状态
        verify(paymentRecordMapper, never()).update(any(), any());
        verify(wxPayClient, never()).sendPost(anyString(), anyString());
    }

    @Test
    @DisplayName("refund: 传入金额恰好等于实付金额时按全额受理（不因严格相等而误拒）")
    void refundShouldAcceptAmountEqualToPaidAmount() throws Exception {
        PaymentRecord record = paidRecord();
        record.setRefundStatus(0);
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(record);
        when(orderMasterMapper.selectByOrderNo(ORDER_NO)).thenReturn(orderWithStatus(10));
        when(paymentRecordMapper.update(any(), any())).thenReturn(1);
        when(wxPayClient.sendPost(anyString(), anyString()))
            .thenReturn("{\"refund_id\":\"wx-refund-002\",\"status\":\"PROCESSING\"}");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        String refundId = service.refund(ORDER_NO, new BigDecimal("99.00"));

        assertEquals("wx-refund-002", refundId);
        verify(wxPayClient).sendPost(anyString(), bodyCaptor.capture());
        var body = new ObjectMapper().readTree(bodyCaptor.getValue());
        assertEquals(9900, body.get("amount").get("refund").asInt(), "等于实付金额时应按全额退");
        assertEquals(9900, body.get("amount").get("total").asInt());
    }

    @Test
    @DisplayName("refund: 金额为 0 或负数必须被拒绝")
    void refundShouldRejectNonPositiveAmount() {
        PaymentRecord record = paidRecord();
        record.setRefundStatus(0);
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(record);

        assertThrows(BizException.class, () -> service.refund(ORDER_NO, BigDecimal.ZERO));
        assertThrows(BizException.class, () -> service.refund(ORDER_NO, new BigDecimal("-1.00")));
        verify(wxPayClient, never()).sendPost(anyString(), anyString());
    }

    @Test
    @DisplayName("refund: 并发占位失败（CAS 返回 0）时必须拒绝，防止双退")
    void refundShouldRejectWhenAnotherRefundIsInFlight() {
        PaymentRecord record = paidRecord();
        record.setRefundStatus(1); // 已有退款在途
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(record);
        when(orderMasterMapper.selectByOrderNo(ORDER_NO)).thenReturn(orderWithStatus(10));
        when(paymentRecordMapper.update(any(), any())).thenReturn(0); // CAS 抢占失败

        BizException ex = assertThrows(BizException.class, () -> service.refund(ORDER_NO, null));

        assertEquals(30507, ex.getCode()); // REFUND_IN_PROGRESS
        verify(wxPayClient, never()).sendPost(anyString(), anyString());
    }

    @Test
    @DisplayName("refund: 已退款完成的订单不允许再次退款")
    void refundShouldRejectAlreadyRefundedOrder() {
        PaymentRecord record = paidRecord();
        record.setRefundStatus(2); // 退款成功
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(record);

        BizException ex = assertThrows(BizException.class, () -> service.refund(ORDER_NO, null));

        assertEquals(30508, ex.getCode()); // REFUND_NOT_ALLOWED
        verify(wxPayClient, never()).sendPost(anyString(), anyString());
    }

    @Test
    @DisplayName("refund: 订单状态不满足退款条件时必须拒绝（已取消/已完成）")
    void refundShouldRejectWhenOrderStatusNotRefundable() {
        PaymentRecord record = paidRecord();
        record.setRefundStatus(0);
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(record);

        when(orderMasterMapper.selectByOrderNo(ORDER_NO)).thenReturn(orderWithStatus(40)); // 已取消
        assertThrows(BizException.class, () -> service.refund(ORDER_NO, null));

        when(orderMasterMapper.selectByOrderNo(ORDER_NO)).thenReturn(orderWithStatus(30)); // 已完成
        assertThrows(BizException.class, () -> service.refund(ORDER_NO, null));

        verify(wxPayClient, never()).sendPost(anyString(), anyString());
    }

    @Test
    @DisplayName("refund: 支付记录不存在时必须报错，绝不能静默成功")
    void refundShouldFailWhenPaymentRecordMissing() {
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.refund(ORDER_NO, null));

        assertEquals(30504, ex.getCode()); // PAYMENT_RECORD_NOT_FOUND
        verify(wxPayClient, never()).sendPost(anyString(), anyString());
    }

    @Test
    @DisplayName("refund: 未支付成功的支付记录不能退款")
    void refundShouldRejectUnpaidPaymentRecord() {
        PaymentRecord record = paidRecord();
        record.setStatus(0); // 待支付
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(record);

        assertThrows(BizException.class, () -> service.refund(ORDER_NO, null));
        verify(wxPayClient, never()).sendPost(anyString(), anyString());
    }

    @Test
    @DisplayName("refund: 微信返回的响应体里不含错误信息时不泄漏给调用方")
    void refundShouldNotLeakWechatResponseBody() {
        PaymentRecord record = paidRecord();
        record.setRefundStatus(0);
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(record);
        when(orderMasterMapper.selectByOrderNo(ORDER_NO)).thenReturn(orderWithStatus(10));
        when(paymentRecordMapper.update(any(), any())).thenReturn(1);
        when(wxPayClient.sendPost(anyString(), anyString()))
            .thenReturn("{\"code\":\"PARAM_ERROR\",\"message\":\"商户号与子商户号不匹配\"}");

        BizException ex = assertThrows(BizException.class, () -> service.refund(ORDER_NO, null));

        assertEquals(50020, ex.getCode()); // WECHAT_PAY_ERROR
        assertFalse(ex.getMessage().contains("子商户号"),
            "不应把微信原始错误信息透传给前端: " + ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════
    // 二、退款回调：必须按 refund_id 查询（回归缺陷 #1）
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("退款回调: 按微信 refund_id 查记录，并把订单置为已退款、佣金作废")
    void refundNotifyShouldLookUpByRefundIdAndSettleOrder() throws Exception {
        PaymentRecord record = paidRecord();
        record.setId(7L);
        record.setRefundId("wx-refund-888");
        record.setRefundStatus(1); // 退款中
        when(paymentRecordMapper.selectByRefundId("wx-refund-888")).thenReturn(record);
        when(paymentRecordMapper.update(any(), any())).thenReturn(1);
        when(orderMasterMapper.update(any(), any())).thenReturn(1);

        String body = refundNotifyBody("RF" + ORDER_NO, "wx-refund-888", "SUCCESS", 9900);
        String[] headers = signedHeaders(body);
        boolean handled = service.handleRefundNotify(headers[0], headers[1], headers[2], headers[3], body);

        assertTrue(handled, "退款回调应被正常处理");
        // 关键回归点：必须按 refund_id 查，绝不能按 out_refund_no 查
        verify(paymentRecordMapper).selectByRefundId("wx-refund-888");
        verify(orderMasterMapper).update(any(), any());
        verify(distributionService).markRefundedByOrderNo(ORDER_NO);
    }

    @Test
    @DisplayName("退款回调: 用商户退款单号查不到记录时应返回失败（旧实现恒为失败）")
    void refundNotifyShouldReturnFalseWhenRecordMissing() throws Exception {
        when(paymentRecordMapper.selectByRefundId("wx-refund-unknown")).thenReturn(null);

        String body = refundNotifyBody("RF" + ORDER_NO, "wx-refund-unknown", "SUCCESS", 9900);
        String[] headers = signedHeaders(body);
        boolean handled = service.handleRefundNotify(headers[0], headers[1], headers[2], headers[3], body);

        assertFalse(handled);
        verify(orderMasterMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("退款回调: 已退款成功时幂等返回，不重复改订单/作废佣金")
    void refundNotifyShouldBeIdempotentWhenAlreadySucceeded() throws Exception {
        PaymentRecord record = paidRecord();
        record.setId(8L);
        record.setRefundId("wx-refund-999");
        record.setRefundStatus(2); // 已退款成功
        when(paymentRecordMapper.selectByRefundId("wx-refund-999")).thenReturn(record);

        String body = refundNotifyBody("RF" + ORDER_NO, "wx-refund-999", "SUCCESS", 9900);
        String[] headers = signedHeaders(body);
        boolean handled = service.handleRefundNotify(headers[0], headers[1], headers[2], headers[3], body);

        assertTrue(handled);
        verify(paymentRecordMapper, never()).update(any(), any());
        verify(distributionService, never()).markRefundedByOrderNo(anyString());
    }

    // ══════════════════════════════════════════════════════════
    // 三、支付回调：金额校验 + CAS 冲突不再静默吞掉
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("支付回调: 回调金额与订单实付金额不一致时必须拒绝入账")
    void paymentNotifyShouldRejectAmountMismatch() throws Exception {
        PaymentRecord record = unpaidRecord(); // 待支付，实付应为 99.00
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(record);

        // 回调声称只支付了 0.01 元
        String body = paymentNotifyBody(ORDER_NO, "wx-txn-1", "SUCCESS", 1);
        String[] headers = signedHeaders(body);
        boolean handled = service.handlePaymentNotify(headers[0], headers[1], headers[2], headers[3], body);

        assertFalse(handled, "金额不一致必须返回失败，让微信重试并告警");
        // 绝不能把订单改成已支付，也不能落库支付成功
        verify(orderMasterMapper, never()).update(any(), any());
        verify(paymentRecordMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("支付回调: 金额一致时正常置为已支付并生成佣金")
    void paymentNotifyShouldSettleOrderWhenAmountMatches() throws Exception {
        PaymentRecord record = unpaidRecord();
        record.setId(11L);
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(record);
        when(orderMasterMapper.update(any(), any())).thenReturn(1);
        OrderMaster paidOrder = orderWithStatus(10);
        when(orderMasterMapper.selectByOrderNo(ORDER_NO)).thenReturn(paidOrder);

        String body = paymentNotifyBody(ORDER_NO, "wx-txn-2", "SUCCESS", 9900);
        String[] headers = signedHeaders(body);
        boolean handled = service.handlePaymentNotify(headers[0], headers[1], headers[2], headers[3], body);

        assertTrue(handled);
        verify(paymentRecordMapper).update(any(), any());
        verify(distributionService).createForPaidOrder(paidOrder);
    }

    @Test
    @DisplayName("支付回调: 订单已被超时取消时返回失败并落库待人工退款标记（钱货两空回归）")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void paymentNotifyShouldNotSilentlySucceedWhenOrderWasCancelled() throws Exception {
        PaymentRecord record = unpaidRecord();
        record.setId(12L);
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(record);
        // 订单 CAS 失败：说明订单状态已变化（典型：已被超时任务取消）
        when(orderMasterMapper.update(any(), any())).thenReturn(0);
        when(orderMasterMapper.selectByOrderNo(ORDER_NO)).thenReturn(orderWithStatus(40)); // 已取消

        String body = paymentNotifyBody(ORDER_NO, "wx-txn-3", "SUCCESS", 9900);
        String[] headers = signedHeaders(body);
        boolean handled = service.handlePaymentNotify(headers[0], headers[1], headers[2], headers[3], body);

        assertFalse(handled, "CAS 失败时必须返回失败并落库待人工退款，不能返回成功");

        // 绝不能把支付记录改成"已支付成功"
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<PaymentRecord>> captor =
            ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(paymentRecordMapper).update(any(), captor.capture());
        String sqlSet = ((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PaymentRecord>)
            captor.getValue()).getSqlSet();
        assertNotNull(sqlSet, "必须落库待人工退款标记");
        assertTrue(sqlSet.contains("refund_status"), "应写入 refund_status 表示待人工退款: " + sqlSet);
        assertFalse(sqlSet.contains("status=1"), "不应把支付记录改成已支付: " + sqlSet);
    }

    @Test
    @DisplayName("支付回调: 已支付成功的通知重复到达时幂等返回")
    void paymentNotifyShouldBeIdempotentWhenAlreadyPaid() throws Exception {
        PaymentRecord record = paidRecord();
        record.setStatus(1); // 已支付成功
        when(paymentRecordMapper.selectByOrderNo(ORDER_NO)).thenReturn(record);

        String body = paymentNotifyBody(ORDER_NO, "wx-txn-4", "SUCCESS", 9900);
        String[] headers = signedHeaders(body);
        boolean handled = service.handlePaymentNotify(headers[0], headers[1], headers[2], headers[3], body);

        assertTrue(handled);
        verify(orderMasterMapper, never()).update(any(), any());
        verify(distributionService, never()).createForPaidOrder(any());
    }

    @Test
    @DisplayName("支付回调: 验签失败必须拒绝，不能因为构造了报文就入账")
    void paymentNotifyShouldRejectBadSignature() throws Exception {
        String body = paymentNotifyBody(ORDER_NO, "wx-txn-5", "SUCCESS", 9900);

        // 合法 base64 但内容不是有效签名 → 必须验签失败
        String bogusSignature = Base64.getEncoder().encodeToString(new byte[256]);
        boolean handled = service.handlePaymentNotify(
            String.valueOf(Instant.now().getEpochSecond()),
            "nonce", bogusSignature, PLATFORM_KEY_ID, body);

        assertFalse(handled);
        verify(paymentRecordMapper, never()).selectByOrderNo(anyString());
    }

    // ══════════════════════════════════════════════════════════
    // 辅助方法
    // ══════════════════════════════════════════════════════════

    /**
     * 在纯 Mockito 单测中初始化 MyBatis-Plus 的实体元数据缓存。
     * <p>
     * LambdaUpdateWrapper 需要实体的 TableInfo 才能把方法引用解析成列名，
     * 没有 Spring 上下文时必须手工初始化，否则会抛
     * "can not find lambda cache for this entity"。
     */
    private static void initTableInfo(Class<?> entityClass) {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
    }

    /** 已支付成功、未退款的支付记录。 */
    private PaymentRecord paidRecord() {
        PaymentRecord record = baseRecord();
        record.setStatus(1);          // 支付成功
        record.setRefundStatus(0);    // 未退款
        return record;
    }

    /** 待支付的支付记录（用于验证支付回调的金额校验与入账路径）。 */
    private PaymentRecord unpaidRecord() {
        PaymentRecord record = baseRecord();
        record.setStatus(0);          // 待支付
        record.setRefundStatus(0);
        return record;
    }

    private PaymentRecord baseRecord() {
        PaymentRecord record = new PaymentRecord();
        record.setId(1L);
        record.setOrderNo(ORDER_NO);
        record.setUserId(1L);
        record.setTotalAmount(new BigDecimal("99.00"));
        record.setPayAmount(new BigDecimal("99.00"));
        return record;
    }

    private OrderMaster orderWithStatus(int status) {
        OrderMaster order = new OrderMaster();
        order.setOrderNo(ORDER_NO);
        order.setStatus(status);
        order.setPayAmount(new BigDecimal("99.00"));
        return order;
    }

    /** 构造并签名一个微信支付回调报文（含 AES-GCM 加密的 resource）。 */
    private String paymentNotifyBody(String outTradeNo, String transactionId, String tradeState, int totalFen)
            throws Exception {
        String plain = "{\"out_trade_no\":\"" + outTradeNo + "\","
            + "\"transaction_id\":\"" + transactionId + "\","
            + "\"trade_state\":\"" + tradeState + "\","
            + "\"amount\":{\"total\":" + totalFen + ",\"payer_total\":" + totalFen + ",\"currency\":\"CNY\"}}";
        return encryptedEnvelope("transaction", plain);
    }

    private String refundNotifyBody(String outRefundNo, String refundId, String refundStatus, int refundFen)
            throws Exception {
        String plain = "{\"out_refund_no\":\"" + outRefundNo + "\","
            + "\"refund_id\":\"" + refundId + "\","
            + "\"refund_status\":\"" + refundStatus + "\","
            + "\"amount\":{\"total\":9900,\"refund\":" + refundFen + ",\"payer_total\":9900,\"currency\":\"CNY\"}}";
        return encryptedEnvelope("refund", plain);
    }

    /** 用 APIv3 密钥做 AES-256-GCM 加密，产出微信回调的 resource 信封。 */
    private String encryptedEnvelope(String originalType, String plaintext) throws Exception {
        String nonce = "0123456789ab"; // 12 字节
        String associatedData = originalType;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,
            new SecretKeySpec(API_V3_KEY.getBytes(StandardCharsets.UTF_8), "AES"),
            new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        String ciphertext = Base64.getEncoder()
            .encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));

        return "{\"id\":\"evt-" + UUID.randomUUID() + "\","
            + "\"create_time\":\"2026-09-21T12:00:00+08:00\","
            + "\"event_type\":\"" + originalType + ".SUCCESS\","
            + "\"resource_type\":\"encrypt-resource\","
            + "\"summary\":\"ok\","
            + "\"resource\":{"
            + "\"algorithm\":\"AEAD_AES_256_GCM\","
            + "\"original_type\":\"" + originalType + "\","
            + "\"ciphertext\":\"" + ciphertext + "\","
            + "\"associated_data\":\"" + associatedData + "\","
            + "\"nonce\":\"" + nonce + "\"}}";
    }

    /** 生成合法签名头：timestamp / nonce / signature / serial。 */
    private String[] signedHeaders(String body) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String message = timestamp + "\n" + nonce + "\n" + body + "\n";
        String signature = com.jinlin24th.jinlin.common.util.WxPayUtil.sign(
            message, "SHA256withRSA", platformKeyPair.getPrivate());
        return new String[]{timestamp, nonce, signature, PLATFORM_KEY_ID};
    }

    @Test
    @DisplayName("sanity: 测试用信封能被业务代码解密")
    void envelopeShouldBeDecryptableByService() throws Exception {
        String body = refundNotifyBody("RF" + ORDER_NO, "wx-refund-x", "SUCCESS", 9900);
        String cipherText = new ObjectMapper().readTree(body)
            .get("resource").get("ciphertext").asText();
        String decrypted = com.jinlin24th.jinlin.common.util.WxPayUtil.aesAeadDecrypt(
            API_V3_KEY.getBytes(StandardCharsets.UTF_8),
            "refund".getBytes(StandardCharsets.UTF_8),
            "0123456789ab".getBytes(StandardCharsets.UTF_8),
            Base64.getDecoder().decode(cipherText));
        assertNotNull(decrypted);
        assertTrue(decrypted.contains("wx-refund-x"));
    }
}
