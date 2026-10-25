package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jinlin24th.jinlin.common.coffee.CoffeeTime;
import com.jinlin24th.jinlin.common.config.CoffeePayConfig;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.util.WxPayUtil;
import com.jinlin24th.jinlin.mapper.*;
import com.jinlin24th.jinlin.pojo.dto.CoffeeOrderRefundDTO;
import com.jinlin24th.jinlin.pojo.entity.*;
import com.jinlin24th.jinlin.pojo.vo.*;

import lombok.RequiredArgsConstructor;

import okhttp3.Headers;

import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 渠道 I/O 不持有订单数据库行锁。稳定的商户单号与持久化意图供重启恢复。 */
@Service
@RequiredArgsConstructor
public class CoffeePaymentService {
    private final CoffeeOrderService orders;
    private final CoffeePayClient client;
    private final CoffeePayConfig config;
    private final CoffeeOrderMapper orderMapper;
    private final CoffeePaymentMapper payments;
    private final CoffeeRefundMapper refunds;
    private final ObjectMapper json;

    public WxPayParamsVO payment(Long userId, Long id) {
        CoffeePayment p = orders.preparePayment(userId, id);
        CoffeeOrder o = orders.owner(userId, id, false);
        AppUser user = orders.user(userId);
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank())
            throw BizException.badRequest("缺少微信用户身份");
        try {
            JsonNode result =
                    client.request(
                            "POST",
                            "/v3/pay/transactions/jsapi",
                            Map.of(
                                    "appid",
                                    p.getAppId(),
                                    "mchid",
                                    p.getMerchantId(),
                                    "description",
                                    o.getStoreName() + "咖啡订单",
                                    "out_trade_no",
                                    p.getMerchantOrderNo(),
                                    "notify_url",
                                    config.getNotifyUrl(),
                                    "time_expire",
                                    CoffeeTime.text(o.getExpiresAt()),
                                    "amount",
                                    Map.of("total", p.getAmountCent(), "currency", "CNY"),
                                    "payer",
                                    Map.of("openid", user.getOpenid())));
            String prepayId = result.path("prepay_id").asText();
            if (prepayId.isBlank()) throw BizException.of(BizCode.COFFEE_CHANNEL_UNAVAILABLE);
            orders.savePrepay(id, prepayId);
            if (orders.payment(id).getCloseRequested()) {
                reconcile(id);
                throw BizException.of(BizCode.COFFEE_ORDER_CONFLICT);
            }
            return client.params(prepayId);
        } catch (WxPayUtil.ApiException e) {
            orders.channelError(id, false, "PREPAY_REJECTED");
            throw BizException.of(BizCode.COFFEE_CHANNEL_UNAVAILABLE);
        }
    }

    public CoffeeOrderVO cancel(Long userId, Long id) {
        CoffeePayment p = orders.cancel(userId, id, false);
        if (p.getInitiated() && !"PAID".equals(p.getStatus())) reconcile(id);
        return orders.userDetail(userId, id);
    }

    public CoffeeOrderVO refund(Long actor, Long id, boolean reject, CoffeeOrderRefundDTO dto) {
        orders.requestRefund(actor, id, reject, dto);
        recoverRefund(id);
        return orders.adminDetail(actor, id);
    }

    public void paymentNotify(Headers headers, String body) {
        JsonNode fact = client.notification(headers, body);
        CoffeePayment p =
                payments.selectOne(
                        Wrappers.<CoffeePayment>lambdaQuery()
                                .eq(
                                        CoffeePayment::getMerchantOrderNo,
                                        fact.path("out_trade_no").asText()));
        if (p == null) throw BizException.of(BizCode.COFFEE_CHANNEL_INVALID);
        orders.paymentFact(p.getOrderId(), fact);
    }

    public void refundNotify(Headers headers, String body) {
        JsonNode fact = client.notification(headers, body);
        CoffeeRefund r =
                refunds.selectOne(
                        Wrappers.<CoffeeRefund>lambdaQuery()
                                .eq(
                                        CoffeeRefund::getRefundNo,
                                        fact.path("out_refund_no").asText()));
        if (r == null) throw BizException.of(BizCode.COFFEE_CHANNEL_INVALID);
        orders.refundFact(r.getOrderId(), fact);
    }

    public void reconcile(Long id) {
        CoffeePayment p = orders.payment(id);
        orders.merchant(p);
        if (!p.getInitiated() || "PAID".equals(p.getStatus())) return;
        try {
            JsonNode fact =
                    client.request(
                            "GET",
                            "/v3/pay/transactions/out-trade-no/"
                                    + encode(p.getMerchantOrderNo())
                                    + "?mchid="
                                    + encode(p.getMerchantId()),
                            null);
            orders.paymentFact(id, fact);
            p = orders.payment(id);
            if (p.getCloseRequested() && "UNPAID".equals(p.getStatus())) {
                client.request(
                        "POST",
                        "/v3/pay/transactions/out-trade-no/"
                                + encode(p.getMerchantOrderNo())
                                + "/close",
                        Map.of("mchid", p.getMerchantId()));
                orders.paymentFact(id, closed(p));
            }
        } catch (WxPayUtil.ApiException e) {
            // 支付与关单竞争时重新可信查询；无法确认则保留待查事实，下一轮恢复。
            if ("ORDERPAID".equals(e.getErrorCode())) {
                try {
                    JsonNode fact =
                            client.request(
                                    "GET",
                                    "/v3/pay/transactions/out-trade-no/"
                                            + encode(p.getMerchantOrderNo())
                                            + "?mchid="
                                            + encode(p.getMerchantId()),
                                    null);
                    orders.paymentFact(id, fact);
                } catch (Exception uncertain) {
                    orders.channelError(id, false, "PAYMENT_QUERY_UNCERTAIN");
                }
            } else if ("ORDER_NOT_EXIST".equals(e.getErrorCode()) && p.getCloseRequested()) {
                CoffeeOrder o = orderMapper.selectById(id);
                if (!o.getExpiresAt().isAfter(CoffeeTime.now())) orders.paymentFact(id, closed(p));
                else orders.channelError(id, false, "CLOSE_WAIT_PREPAY_RESULT");
            } else orders.channelError(id, false, "PAYMENT_QUERY_REJECTED");
        }
    }

    private ObjectNode closed(CoffeePayment p) {
        ObjectNode f = json.createObjectNode();
        f.put("appid", p.getAppId());
        f.put("mchid", p.getMerchantId());
        f.put("out_trade_no", p.getMerchantOrderNo());
        f.put("trade_state", "CLOSED");
        return f;
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    public void recoverRefund(Long id) {
        CoffeeRefund r = orders.claimRefund(id);
        if (r == null) return;
        CoffeePayment p = orders.payment(id);
        orders.merchant(p);
        try {
            JsonNode fact;
            try {
                fact =
                        client.request(
                                "GET",
                                "/v3/refund/domestic/refunds/" + encode(r.getRefundNo()),
                                null);
            } catch (WxPayUtil.ApiException e) {
                if (!"RESOURCE_NOT_EXISTS".equals(e.getErrorCode())
                        && !"REFUND_NOT_EXIST".equals(e.getErrorCode())) throw e;
                // 查询确认不存在后，仍使用原退款号。超时/重启均不生成第二个退款号。
                orders.refundSubmitted(id);
                fact =
                        client.request(
                                "POST",
                                "/v3/refund/domestic/refunds",
                                Map.of(
                                        "transaction_id",
                                        p.getTransactionId(),
                                        "out_refund_no",
                                        r.getRefundNo(),
                                        "reason",
                                        r.getReason(),
                                        "notify_url",
                                        config.getRefundNotifyUrl(),
                                        "amount",
                                        Map.of(
                                                "refund",
                                                r.getAmountCent(),
                                                "total",
                                                p.getAmountCent(),
                                                "currency",
                                                "CNY")));
            }
            // 退款查询应答无 mchid 字段；只有经独立商户签名请求与平台应答验签后才能补入该请求的商户。
            if (!fact.has("mchid") && fact instanceof ObjectNode object)
                object.put("mchid", p.getMerchantId());
            orders.refundFact(id, fact);
        } catch (Exception e) {
            orders.channelError(id, true, "REFUND_RESULT_UNCERTAIN");
        }
    }
}
