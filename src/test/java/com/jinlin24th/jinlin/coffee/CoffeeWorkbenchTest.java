package com.jinlin24th.jinlin.coffee;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jinlin24th.jinlin.common.auth.*;
import com.jinlin24th.jinlin.common.coffee.CoffeeTime;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.pojo.vo.*;
import com.jinlin24th.jinlin.service.*;

import org.junit.jupiter.api.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringJUnitConfig(WorkbenchTestConfig.class)
@TestPropertySource(properties = {"app.coffee.catalog-enabled=true"})
class CoffeeWorkbenchTest extends WorkbenchDatabaseTestSupport {
    @Test
    void loginUsesStoredCanonicalUsernameForRevocableSession() {
        var dto = new com.jinlin24th.jinlin.pojo.dto.AdminLoginDTO();
        dto.setUsername("super");
        dto.setPassword("super-password");
        var result = admins.login(dto, new org.springframework.mock.web.MockHttpServletRequest());
        assertEquals("super", result.getUsername());
        // MySQL 默认排序规则不区分大小写；H2 中用同名请求验证相同会话命名。
        if (System.getProperty("coffee.test.mysql.url") != null) {
            dto.setUsername("SUPER");
            assertEquals(
                    "super",
                    admins.login(dto, new org.springframework.mock.web.MockHttpServletRequest())
                            .getUsername());
        }
    }

    @Test
    void fixedRolesContextAndEveryOldModuleAreScoped() {
        assertEquals("/coffee/workbench", accounts.context(coffeeAdmin).getDefaultRoute());
        assertEquals(store.toString(), accounts.context(coffeeAdmin).getStores().get(0).getId());
        assertEquals("/dashboard", accounts.context(goodsAdmin).getDefaultRoute());
        assertTrue(accounts.context(goodsAdmin).getStores().isEmpty());
        for (String path :
                List.of(
                        "/admin/product/list",
                        "/admin/order/list",
                        "/admin/payment/refund",
                        "/admin/inventory/log/list",
                        "/admin/warehouse/list",
                        "/admin/user/list",
                        "/admin/customer/list",
                        "/admin/followRecord/list",
                        "/admin/admins/options",
                        "/admin/coupon/list",
                        "/admin/distribution/list")) {
            error(BizCode.FORBIDDEN, () -> permissions.authorize(coffeeAdmin, "GET", path));
            assertDoesNotThrow(() -> permissions.authorize(goodsAdmin, "GET", path));
        }
        error(
                BizCode.FORBIDDEN,
                () -> permissions.authorize(goodsAdmin, "GET", "/admin/coffee/context"));
        error(
                BizCode.FORBIDDEN,
                () -> permissions.authorize(goodsAdmin, "GET", "/admin/marketing/activity/list"));
        error(BizCode.FORBIDDEN, () -> permissions.authorize(goodsAdmin, "GET", "/admin/unknown"));
        error(BizCode.FORBIDDEN, () -> accounts.list(goodsAdmin, 1, 20, null, null, null));
        error(BizCode.NOT_FOUND, () -> catalog.adminStore(coffeeAdmin, store + 100));
        jdbc.update("DELETE FROM sys_admin_coffee_store WHERE admin_id=?", coffeeAdmin);
        assertTrue(catalog.context(coffeeAdmin).getStores().isEmpty());
        error(BizCode.NOT_FOUND, () -> catalog.adminMenu(coffeeAdmin, store));
        error(
                BizCode.FORBIDDEN,
                () -> catalog.saveStore(coffeeAdmin, null, new CoffeeStoreSaveDTO()));
    }

    @Test
    void accountsAuditRevocationResetAndLastSuper() {
        assertTrue(
                accounts.list(1L, 1, 20, "coffee", null, null)
                        .getRecords()
                        .get(0)
                        .getMustChangePwd());
        accounts.reset(1L, coffeeAdmin, "reset-password");
        assertTrue(
                encoder.matches(
                        "reset-password",
                        jdbc.queryForObject(
                                "SELECT password_hash FROM sys_admin WHERE id=?",
                                String.class,
                                coffeeAdmin)));
        verify(sessions).onLogoutAdmin("coffee");
        accounts.status(1L, coffeeAdmin, 0);
        error(
                BizCode.UNAUTHORIZED,
                () -> permissions.authorize(coffeeAdmin, "GET", "/admin/coffee/context"));
        var access = new AdminAccountAccessDTO();
        access.setRoleCode("GOODS_ADMIN");
        accounts.access(1L, coffeeAdmin, access);
        assertTrue(accounts.stores(coffeeAdmin, false).isEmpty());
        error(BizCode.ADMIN_LAST_SUPER, () -> accounts.status(1L, 1L, 0));
        error(BizCode.ADMIN_LAST_SUPER, () -> accounts.access(1L, 1L, access));
        String audit =
                jdbc.queryForList("SELECT request_params FROM sys_operation_log", String.class)
                        .toString();
        assertFalse(audit.contains("password"));
        assertFalse(audit.contains("temporary"));
        assertTrue(audit.contains("targetId"));
        error(
                BizCode.ADMIN_ACCOUNT_CONFLICT,
                () -> accounts.create(1L, account("coffee", "GOODS_ADMIN", null)));
    }

    @Test
    void unknownLegacyRoleIsPreservedWithoutElevation() {
        jdbc.update(
                "INSERT INTO sys_role(role_code,role_name,status,sort)"
                        + " VALUES('LEGACY_TEST','旧角色',1,99) ON DUPLICATE KEY UPDATE status=1");
        jdbc.update("DELETE FROM sys_admin_role WHERE admin_id=?", goodsAdmin);
        jdbc.update(
                "INSERT INTO sys_admin_role(admin_id,role_id) SELECT ?,id FROM sys_role WHERE"
                        + " role_code='LEGACY_TEST'",
                goodsAdmin);
        assertEquals(List.of("LEGACY_TEST"), accounts.context(goodsAdmin).getRoles());
        assertNull(accounts.context(goodsAdmin).getDefaultRoute());
        error(
                BizCode.FORBIDDEN,
                () -> permissions.authorize(goodsAdmin, "GET", "/admin/product/list"));
    }

    @Test
    void snapshotMenuVersionIdempotencyAndOwner() {
        var req = request("same");
        var o = orders.create(10L, req);
        assertEquals(o.getId(), orders.create(10L, req).getId());
        assertEquals(3600L, o.getAmounts().getPayableAmountCent());
        assertEquals("WAIT_PAYMENT", o.getFulfillmentStatus());
        assertEquals("0", o.getVersion().toString());
        assertNull(o.getAmounts().getPaidAmountCent());
        assertTrue(o.getCreatedAt().endsWith("+08:00"));
        req.setRemark("changed");
        error(BizCode.COFFEE_REQUEST_CONFLICT, () -> orders.create(10L, req));
        jdbc.update("UPDATE coffee_sku SET price_cent=999 WHERE id=?", sku);
        assertEquals(
                1800L,
                orders.userDetail(10L, Long.valueOf(o.getId()))
                        .getItems()
                        .get(0)
                        .getUnitPriceCent());
        error(BizCode.COFFEE_MENU_CONFLICT, () -> orders.create(10L, requestWithOldMenu(req)));
        error(
                BizCode.COFFEE_ORDER_NOT_FOUND,
                () -> orders.userDetail(11L, Long.valueOf(o.getId())));
    }

    @Test
    void flagsAndStorePauseOnlyBlockNewTrade() {
        var o = create();
        config.setOrderingEnabled(false);
        error(BizCode.COFFEE_ORDERING_DISABLED, () -> create());
        assertEquals(o.getId(), orders.userDetail(10L, Long.valueOf(o.getId())).getId());
        var paid = pay(o);
        assertEquals("WAIT_ACCEPT", paid.getFulfillmentStatus());
        orders.action(coffeeAdmin, Long.valueOf(o.getId()), "ACCEPT", version(paid));
        config.setOrderingEnabled(true);
        jdbc.update("UPDATE coffee_store SET business_status='PAUSED' WHERE id=?", store);
        error(BizCode.COFFEE_STORE_CLOSED, () -> create());
    }

    @Test
    void amountMerchantCurrencyAndReplayValidation() {
        var o = create();
        Long id = Long.valueOf(o.getId());
        ObjectNode f = paid(o);
        f.put("mchid", "goods-merchant");
        error(BizCode.COFFEE_CHANNEL_INVALID, () -> orders.paymentFact(id, f));
        f.put("mchid", "coffee-merchant");
        ((ObjectNode) f.path("amount")).put("total", 1);
        error(BizCode.COFFEE_CHANNEL_INVALID, () -> orders.paymentFact(id, f));
        ((ObjectNode) f.path("amount")).put("total", 3600).put("currency", "USD");
        error(BizCode.COFFEE_CHANNEL_INVALID, () -> orders.paymentFact(id, f));
        ((ObjectNode) f.path("amount")).put("currency", "CNY");
        orders.paymentFact(id, f);
        orders.paymentFact(id, f);
        f.put("trade_state", "CLOSED");
        orders.paymentFact(id, f);
        assertEquals("PAID", orders.userDetail(10L, id).getPaymentStatus());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM coffee_order_event WHERE order_id=? AND"
                                + " action='PAYMENT_SUCCESS'",
                        Integer.class,
                        id));
    }

    @Test
    void wholeOrderFlowVersionAndPersistentNotification() {
        var o = pay(create());
        Long id = Long.valueOf(o.getId());
        assertEquals(List.of("ACCEPT", "REJECT", "REFUND"), o.getAllowedActions());
        var stale = version(o);
        o = orders.action(coffeeAdmin, id, "ACCEPT", stale);
        assertEquals("WAIT_MAKE", o.getFulfillmentStatus());
        error(BizCode.COFFEE_ORDER_CONFLICT, () -> orders.action(coffeeAdmin, id, "START", stale));
        o = orders.action(coffeeAdmin, id, "START", version(o));
        o = orders.action(coffeeAdmin, id, "READY", version(o));
        assertEquals("PENDING", o.getNotificationStatus());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM coffee_notification_task", Integer.class));
        orders.action(coffeeAdmin, id, "READY", version(o));
        recovery.recover();
        assertEquals("SKIPPED", orders.adminDetail(coffeeAdmin, id).getNotificationStatus());
        o = orders.action(coffeeAdmin, id, "COMPLETE", version(o));
        assertEquals("COMPLETED", o.getFulfillmentStatus());
        assertNotNull(o.getCompletedAt());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM coffee_order_event WHERE action='READY'",
                        Integer.class));
    }

    @Test
    void rejectedRefundFreezesAndChannelSuccessDoesNotLosePaidFacts() {
        var o = pay(create());
        Long id = Long.valueOf(o.getId());
        var dto = refund(o, "reject-key");
        o = orders.requestRefund(coffeeAdmin, id, true, dto);
        assertEquals("REJECTED", o.getFulfillmentStatus());
        assertEquals("PENDING", o.getRefundStatus());
        assertTrue(o.getAllowedActions().isEmpty());
        assertEquals(o.getId(), orders.requestRefund(coffeeAdmin, id, true, dto).getId());
        dto.setReason("different");
        error(
                BizCode.COFFEE_REQUEST_CONFLICT,
                () -> orders.requestRefund(coffeeAdmin, id, true, dto));
        ObjectNode fact = refundFact(id, "SUCCESS");
        orders.refundFact(id, fact);
        orders.refundFact(id, fact);
        var done = orders.adminDetail(coffeeAdmin, id);
        assertEquals("SUCCESS", done.getRefundStatus());
        assertEquals("PAID", done.getPaymentStatus());
        assertEquals(3600L, done.getAmounts().getRefundedAmountCent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM coffee_refund", Integer.class));
        assertEquals(
                0L,
                orders.list(coffeeAdmin, store, 1, 20, "WAIT_ACCEPT", null, null, null, null)
                        .getTotal());
    }

    @Test
    void latePaymentAfterCancellationCreatesTrackedRefund() {
        var o = create();
        Long id = Long.valueOf(o.getId());
        orders.cancel(10L, id, false);
        orders.paymentFact(id, paid(o));
        var v = orders.adminDetail(coffeeAdmin, id);
        assertEquals("CANCELLED", v.getFulfillmentStatus());
        assertEquals("PAID", v.getPaymentStatus());
        assertEquals("PENDING", v.getRefundStatus());
        assertNotNull(orders.refund(id));
    }

    @Test
    void expiryNeverCancelsPaidUnacceptedAndCrossDayWorkbenchLoads() {
        var unpaid = create();
        var paid = pay(create());
        jdbc.update(
                "UPDATE coffee_order SET created_at=?,stage_entered_at=?,expires_at=?",
                CoffeeTime.now().minusDays(2),
                CoffeeTime.now().minusDays(2),
                CoffeeTime.now().minusDays(2));
        recovery.recover();
        assertEquals(
                "CANCELLED",
                orders.userDetail(10L, Long.valueOf(unpaid.getId())).getFulfillmentStatus());
        assertEquals(
                "WAIT_ACCEPT",
                orders.userDetail(10L, Long.valueOf(paid.getId())).getFulfillmentStatus());
        assertEquals(
                1L,
                orders.list(coffeeAdmin, store, 1, 20, "WAIT_ACCEPT", null, null, null, null)
                        .getTotal());
        assertEquals(
                0L,
                orders.list(coffeeAdmin, store, 1, 20, null, null, null, null, null).getTotal());
    }

    @Test
    void refundFailureRemainsFrozenAndSuccessCanArriveLater() {
        var o = pay(create());
        Long id = Long.valueOf(o.getId());
        orders.requestRefund(coffeeAdmin, id, false, refund(o, "failed-key"));
        orders.refundFact(id, refundFact(id, "ABNORMAL"));
        var failed = orders.adminDetail(coffeeAdmin, id);
        assertEquals("FAILED", failed.getRefundStatus());
        assertTrue(failed.getAllowedActions().isEmpty());
        error(
                BizCode.COFFEE_ORDER_CONFLICT,
                () -> orders.action(coffeeAdmin, id, "START", version(failed)));
        orders.refundFact(id, refundFact(id, "SUCCESS"));
        assertEquals("SUCCESS", orders.adminDetail(coffeeAdmin, id).getRefundStatus());
    }

    @Test
    void ledgerUsesBeijingPaymentAndRefundDatesIndependently() {
        var o = pay(create());
        Long id = Long.valueOf(o.getId());
        orders.requestRefund(coffeeAdmin, id, false, refund(o, "ledger"));
        orders.refundFact(id, refundFact(id, "SUCCESS"));
        LocalDate today = CoffeeTime.now().toLocalDate();
        jdbc.update(
                "UPDATE coffee_payment SET paid_at=? WHERE order_id=?",
                today.minusDays(1).atTime(23, 59, 59),
                id);
        var ledger = orders.ledger(coffeeAdmin, store, today, today);
        assertEquals(0L, ledger.get("receivedAmountCent"));
        assertEquals(3600L, ledger.get("refundedAmountCent"));
        assertEquals(-3600L, ledger.get("netReceivedAmountCent"));
    }

    @Test
    void concurrentSameKeyAndPickupNumbersAreUnique() throws Exception {
        var dto = request("concurrent");
        List<Object> result = race(() -> orders.create(10L, dto), () -> orders.create(10L, dto));
        assertEquals(
                ((CoffeeOrderVO) result.get(0)).getId(), ((CoffeeOrderVO) result.get(1)).getId());
        result =
                race(
                        () -> orders.create(10L, request("unique-a")),
                        () -> orders.create(11L, request("unique-b")));
        assertNotEquals(
                ((CoffeeOrderVO) result.get(0)).getPickupCode(),
                ((CoffeeOrderVO) result.get(1)).getPickupCode());
    }

    @Test
    void acceptAndRejectRaceProducesOneTransition() throws Exception {
        var o = pay(create());
        Long id = Long.valueOf(o.getId());
        var a = version(o);
        var r = refund(o, "race-refund");
        List<Object> result =
                race(
                        () -> orders.action(coffeeAdmin, id, "ACCEPT", a),
                        () -> orders.requestRefund(coffeeAdmin, id, true, r));
        assertEquals(1, result.stream().filter(x -> x instanceof BizException).count());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM coffee_order_event WHERE action IN"
                                + " ('ACCEPT','REJECT')",
                        Integer.class));
    }

    @Test
    void concurrentSuperDisablesLeaveOneEnabled() throws Exception {
        Long second =
                Long.valueOf(
                        accounts.create(1L, account("super-two", "SUPER_ADMIN", null)).getId());
        List<Object> result =
                race(() -> accounts.status(1L, 1L, 0), () -> accounts.status(second, second, 0));
        assertEquals(1, result.stream().filter(x -> x instanceof BizException).count());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_admin WHERE id IN (1,?) AND status=1",
                        Integer.class,
                        second));
    }

    @Test
    void refundTimeoutRecoversWithSameMerchantRefundNumberAfterRestart() {
        var o = pay(create());
        Long id = Long.valueOf(o.getId());
        orders.requestRefund(coffeeAdmin, id, true, refund(o, "recover-refund"));
        String no = orders.refund(id).getRefundNo();
        when(client.request(eq("GET"), contains("/refunds/"), isNull()))
                .thenThrow(BizException.of(BizCode.COFFEE_CHANNEL_UNAVAILABLE));
        payment.recoverRefund(id);
        assertEquals("PENDING", orders.refund(id).getStatus());
        assertEquals(no, orders.refund(id).getRefundNo());
        jdbc.update(
                "UPDATE coffee_refund SET next_query_at=? WHERE order_id=?",
                CoffeeTime.now().minusSeconds(1),
                id);
        when(client.request(eq("GET"), contains("/refunds/"), isNull()))
                .thenReturn(refundFact(id, "SUCCESS"));
        recovery.recover();
        assertEquals("SUCCESS", orders.refund(id).getStatus());
        assertEquals(no, orders.refund(id).getRefundNo());
        verify(client, never()).request(eq("POST"), anyString(), any());
    }

    @Test
    void missingRefundIsSubmittedWithStableFullAmountAndProcessingIsNotSuccess() {
        var o = pay(create());
        Long id = Long.valueOf(o.getId());
        orders.requestRefund(coffeeAdmin, id, false, refund(o, "new-refund"));
        when(client.request(eq("GET"), contains("/refunds/"), isNull()))
                .thenThrow(
                        new com.jinlin24th.jinlin.common.util.WxPayUtil.ApiException(
                                404,
                                "{\"code\":\"RESOURCE_NOT_EXISTS\"}",
                                new okhttp3.Headers.Builder().build()));
        when(client.request(eq("POST"), eq("/v3/refund/domestic/refunds"), any()))
                .thenReturn(refundFact(id, "PROCESSING"));
        payment.recoverRefund(id);
        assertEquals("PENDING", orders.refund(id).getStatus());
        assertTrue(orders.refund(id).getSubmitted());
        var capture = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(client).request(eq("POST"), eq("/v3/refund/domestic/refunds"), capture.capture());
        Map<?, ?> body = (Map<?, ?>) capture.getValue();
        assertEquals(orders.refund(id).getRefundNo(), body.get("out_refund_no"));
        assertEquals(3600L, ((Map<?, ?>) body.get("amount")).get("refund"));
    }

    @Test
    void paymentCloseRaceTracksActualReceiptAndRefundsCancelledOrder() {
        var o = create();
        Long id = Long.valueOf(o.getId());
        orders.preparePayment(10L, id);
        ObjectNode unpaid = paid(o);
        unpaid.put("trade_state", "NOTPAY");
        when(client.request(eq("GET"), contains("/out-trade-no/"), isNull()))
                .thenReturn(unpaid, paid(o));
        when(client.request(eq("POST"), endsWith("/close"), any()))
                .thenThrow(
                        new com.jinlin24th.jinlin.common.util.WxPayUtil.ApiException(
                                400,
                                "{\"code\":\"ORDERPAID\"}",
                                new okhttp3.Headers.Builder().build()));
        var result = payment.cancel(10L, id);
        assertEquals("PAID", result.getPaymentStatus());
        assertEquals("CANCELLED", result.getFulfillmentStatus());
        assertEquals("PENDING", result.getRefundStatus());
    }

    @Test
    void storePauseAndOrderingDisabledDoNotDiscardAnInitiatedPayment() {
        var o = create();
        Long id = Long.valueOf(o.getId());
        orders.preparePayment(10L, id);
        jdbc.update("UPDATE coffee_store SET business_status='PAUSED' WHERE id=?", store);
        config.setOrderingEnabled(false);
        when(client.request(eq("GET"), contains("/out-trade-no/"), isNull())).thenReturn(paid(o));
        payment.reconcile(id);
        assertEquals("WAIT_ACCEPT", orders.userDetail(10L, id).getFulfillmentStatus());
        error(BizCode.COFFEE_ORDERING_DISABLED, () -> orders.preparePayment(10L, id));
    }

    @Test
    void notificationNetworkUncertaintyIsPersistentAndNotBlindlyRetried() {
        var o = pay(create());
        Long id = Long.valueOf(o.getId());
        o = orders.action(coffeeAdmin, id, "ACCEPT", version(o));
        o = orders.action(coffeeAdmin, id, "START", version(o));
        orders.action(coffeeAdmin, id, "READY", version(o));
        config.setReadyTemplateId("test-template");
        when(wx.getMsgService()).thenThrow(new RuntimeException("simulated timeout"));
        recovery.recover();
        assertEquals("UNKNOWN", orders.adminDetail(coffeeAdmin, id).getNotificationStatus());
        recovery.recover();
        verify(wx, times(1)).getMsgService();
        assertEquals("READY", orders.adminDetail(coffeeAdmin, id).getFulfillmentStatus());
    }

    @Test
    void notificationSentOnlyAfterSdkAcceptsAndIsUnique() throws Exception {
        var o = pay(create());
        Long id = Long.valueOf(o.getId());
        o = orders.action(coffeeAdmin, id, "ACCEPT", version(o));
        o = orders.action(coffeeAdmin, id, "START", version(o));
        orders.action(coffeeAdmin, id, "READY", version(o));
        config.setReadyTemplateId("test-template");
        var msg = mock(cn.binarywang.wx.miniapp.api.WxMaMsgService.class);
        when(wx.getMsgService()).thenReturn(msg);
        recovery.recover();
        recovery.recover();
        assertEquals("SENT", orders.adminDetail(coffeeAdmin, id).getNotificationStatus());
        verify(msg, times(1)).sendSubscribeMsg(any());
    }

    @Test
    void workbenchPaginationPreservesRemainingCountAndStageOrder() {
        for (int i = 0; i < 22; i++) pay(create());
        var columns = (List<?>) orders.workbench(coffeeAdmin, store).get("columns");
        Map<?, ?> first = (Map<?, ?>) columns.get(0);
        assertEquals(22L, first.get("total"));
        assertEquals(20, ((List<?>) first.get("records")).size());
        var second = orders.list(coffeeAdmin, store, 2, 20, "WAIT_ACCEPT", null, null, null, null);
        assertEquals(2, second.getRecords().size());
        assertEquals(2L, second.getPages());
    }
}
