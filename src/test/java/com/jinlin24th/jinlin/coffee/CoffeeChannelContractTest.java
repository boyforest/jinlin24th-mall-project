package com.jinlin24th.jinlin.coffee;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jinlin24th.jinlin.common.constant.BizCode;

import okhttp3.Headers;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

class CoffeeChannelContractTest extends WorkbenchDatabaseTestSupport {
    @Test
    void prepayUsesPersistedValidUniqueNumberAcrossRepeatedPayments() {
        var dto = request("repeat-payment");
        var first = orders.create(10L, dto);
        Long id = Long.valueOf(first.getId());
        var second = create();
        when(client.request(eq("POST"), eq("/v3/pay/transactions/jsapi"), any()))
                .thenReturn(json.createObjectNode().put("prepay_id", "test-prepay"));
        payment.payment(10L, id);
        var repeated = orders.create(10L, dto);
        payment.payment(10L, Long.valueOf(repeated.getId()));
        payment.payment(10L, Long.valueOf(second.getId()));

        var capture = ArgumentCaptor.forClass(Object.class);
        verify(client, times(3))
                .request(eq("POST"), eq("/v3/pay/transactions/jsapi"), capture.capture());
        var bodies = capture.getAllValues().stream().map(value -> (Map<?, ?>) value).toList();
        String saved = orders.payment(id).getMerchantOrderNo();
        assertEquals(first.getOrderNo(), saved);
        assertEquals(first.getId(), repeated.getId());
        assertEquals(saved, bodies.get(0).get("out_trade_no"));
        assertEquals(saved, bodies.get(1).get("out_trade_no"));
        assertNotEquals(saved, bodies.get(2).get("out_trade_no"));
        for (var body : bodies) {
            String number = (String) body.get("out_trade_no");
            assertTrue(number.length() >= 6 && number.length() <= 32);
            assertTrue(number.matches("[A-Za-z0-9_\\-|*]+"));
            assertEquals("coffee-merchant", body.get("mchid"));
            assertEquals(3600L, ((Map<?, ?>) body.get("amount")).get("total"));
            assertEquals("CNY", ((Map<?, ?>) body.get("amount")).get("currency"));
        }
    }

    @Test
    void existingChannelMappingIsReusedWithoutDerivingItFromBusinessNumber() {
        var order = create();
        Long id = Long.valueOf(order.getId());
        String businessNo = "CF" + "a".repeat(32), merchantNo = "legacy_paid_20261026_0001";
        jdbc.update("UPDATE coffee_order SET order_no=? WHERE id=?", businessNo, id);
        jdbc.update(
                "UPDATE coffee_payment SET merchant_order_no=? WHERE order_id=?", merchantNo, id);
        when(client.request(eq("POST"), eq("/v3/pay/transactions/jsapi"), any()))
                .thenReturn(json.createObjectNode().put("prepay_id", "legacy-prepay"));
        payment.payment(10L, id);
        payment.payment(10L, id);
        var capture = ArgumentCaptor.forClass(Object.class);
        verify(client, times(2))
                .request(eq("POST"), eq("/v3/pay/transactions/jsapi"), capture.capture());
        for (var body : capture.getAllValues())
            assertEquals(merchantNo, ((Map<?, ?>) body).get("out_trade_no"));
        assertEquals(businessNo, orders.userDetail(10L, id).getOrderNo());
        assertEquals(merchantNo, orders.payment(id).getMerchantOrderNo());
    }

    @Test
    void invalidLegacyNumberIsPreservedAndNeverSentOrReplaced() {
        var order = create();
        Long id = Long.valueOf(order.getId());
        String legacy = "CF" + "b".repeat(32);
        jdbc.update("UPDATE coffee_order SET order_no=? WHERE id=?", legacy, id);
        jdbc.update("UPDATE coffee_payment SET merchant_order_no=? WHERE order_id=?", legacy, id);
        error(BizCode.COFFEE_CHANNEL_INVALID, () -> payment.payment(10L, id));
        error(BizCode.COFFEE_CHANNEL_INVALID, () -> payment.payment(10L, id));
        verifyNoInteractions(client);
        assertEquals(legacy, orders.userDetail(10L, id).getOrderNo());
        assertEquals(legacy, orders.payment(id).getMerchantOrderNo());
        assertFalse(orders.payment(id).getInitiated());
        // 历史到账通知仍按原映射入账，不因新下单格式限制丢弃已有交易事实。
        var headers = new Headers.Builder().build();
        when(client.notification(headers, "legacy-payment"))
                .thenReturn(paid(order).put("out_trade_no", legacy));
        payment.paymentNotify(headers, "legacy-payment");
        assertEquals("PAID", orders.payment(id).getStatus());
        assertEquals(legacy, orders.payment(id).getMerchantOrderNo());
    }

    // 微信退款通知的解密 resource 形状：refund_status、payer 金额，无 currency。
    private ObjectNode notification(Long id, String state) {
        var refund = orders.refund(id);
        var paid = orders.payment(id);
        var fact = json.createObjectNode();
        fact.put("mchid", paid.getMerchantId());
        fact.put("out_trade_no", paid.getMerchantOrderNo());
        fact.put("transaction_id", paid.getTransactionId());
        fact.put("out_refund_no", refund.getRefundNo());
        fact.put("refund_id", "5030020190608201900000000001");
        fact.put("refund_status", state);
        if ("SUCCESS".equals(state)) fact.put("success_time", "2026-10-26T10:00:00+08:00");
        fact.put("user_received_account", "支付用户零钱");
        fact.putObject("amount")
                .put("total", 3600)
                .put("refund", 3600)
                .put("payer_total", 3600)
                .put("payer_refund", 3600);
        return fact;
    }

    private Long pendingRefund() {
        var order = pay(create());
        Long id = Long.valueOf(order.getId());
        orders.requestRefund(coffeeAdmin, id, true, refund(order, "refund-" + id));
        return id;
    }

    private void notify(ObjectNode fact) {
        var headers = new Headers.Builder().build();
        when(client.notification(headers, "verified-refund-body")).thenReturn(fact);
        payment.refundNotify(headers, "verified-refund-body");
    }

    @Test
    void officialSuccessNotificationWithoutCurrencyIsIdempotentAndMonotonic() {
        Long id = pendingRefund();
        var fact = notification(id, "SUCCESS");
        assertFalse(fact.path("amount").has("currency"));
        notify(fact);
        var first = orders.adminDetail(coffeeAdmin, id);
        notify(fact);
        notify(notification(id, "ABNORMAL"));
        var repeated = orders.adminDetail(coffeeAdmin, id);
        assertEquals("SUCCESS", repeated.getRefundStatus());
        assertEquals(3600L, repeated.getAmounts().getRefundedAmountCent());
        assertEquals(first.getVersion(), repeated.getVersion());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM coffee_order_event WHERE order_id=? AND"
                                + " action='REFUND_SUCCESS'",
                        Integer.class,
                        id));
    }

    @Test
    void officialFailureNotificationsWithoutCurrencyAreIdempotentAndCanBeCompensated() {
        for (String state : List.of("CLOSED", "ABNORMAL")) {
            Long id = pendingRefund();
            var fact = notification(id, state);
            notify(fact);
            var failed = orders.adminDetail(coffeeAdmin, id);
            notify(fact);
            assertEquals("FAILED", orders.refund(id).getStatus());
            assertEquals(failed.getVersion(), orders.adminDetail(coffeeAdmin, id).getVersion());
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM coffee_order_event WHERE order_id=? AND"
                                    + " action='REFUND_FAILED'",
                            Integer.class,
                            id));
            notify(notification(id, "SUCCESS"));
            assertEquals("SUCCESS", orders.refund(id).getStatus());
        }
    }

    @Test
    void notificationStillValidatesMerchantIdentifiersAmountsAndOptionalCurrency() {
        Long id = pendingRefund();
        var valid = notification(id, "SUCCESS");
        for (String field : List.of("mchid", "out_trade_no", "out_refund_no", "transaction_id")) {
            var wrong = valid.deepCopy().put(field, "wrong");
            error(BizCode.COFFEE_CHANNEL_INVALID, () -> notify(wrong));
        }
        for (String field : List.of("total", "refund")) {
            var wrong = valid.deepCopy();
            ((ObjectNode) wrong.path("amount")).put(field, 1);
            error(BizCode.COFFEE_CHANNEL_INVALID, () -> notify(wrong));
        }
        var wrongCurrency = valid.deepCopy();
        ((ObjectNode) wrongCurrency.path("amount")).put("currency", "USD");
        error(BizCode.COFFEE_CHANNEL_INVALID, () -> notify(wrongCurrency));
        assertEquals("PENDING", orders.refund(id).getStatus());
        ((ObjectNode) valid.path("amount")).put("currency", "CNY");
        notify(valid);
        assertEquals("SUCCESS", orders.refund(id).getStatus());
    }

    @Test
    void refundQueryResponseStillRequiresCurrency() {
        Long id = pendingRefund();
        var fact = refundFact(id, "SUCCESS");
        ((ObjectNode) fact.path("amount")).remove("currency");
        error(BizCode.COFFEE_CHANNEL_INVALID, () -> orders.refundFact(id, fact));
        assertEquals("PENDING", orders.refund(id).getStatus());
        ((ObjectNode) fact.path("amount")).put("currency", "CNY");
        orders.refundFact(id, fact);
        assertEquals("SUCCESS", orders.refund(id).getStatus());
    }
}
