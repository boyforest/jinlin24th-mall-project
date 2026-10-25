package com.jinlin24th.jinlin.coffee;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jinlin24th.jinlin.common.auth.*;
import com.jinlin24th.jinlin.common.coffee.CoffeeTime;
import com.jinlin24th.jinlin.common.config.CoffeePayConfig;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.pojo.vo.*;
import com.jinlin24th.jinlin.service.*;
import com.jinlin24th.jinlin.task.CoffeeRecoveryTask;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringJUnitConfig(WorkbenchTestConfig.class)
@TestPropertySource(properties = {"app.coffee.catalog-enabled=true"})
abstract class WorkbenchDatabaseTestSupport {
    @Autowired CoffeeOrderService orders;
    @Autowired CoffeeCatalogService catalog;
    @Autowired AdminAccountService accounts;
    @Autowired AdminPermissionService permissions;
    @Autowired SysAdminService admins;
    @Autowired AuthSessionService sessions;
    @Autowired CoffeePaymentService payment;
    @Autowired CoffeePayClient client;
    @Autowired CoffeePayConfig config;
    @Autowired CoffeeRecoveryTask recovery;
    @Autowired CoffeeNotificationService notification;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired cn.binarywang.wx.miniapp.api.WxMaService wx;
    Long store, coffeeAdmin, goodsAdmin;
    String sku;

    @BeforeEach
    void setup() {
        reset(sessions, client, wx);
        config.setOrderingEnabled(true);
        config.setPaymentEnabled(true);
        config.setReadyTemplateId(null);
        for (String t :
                List.of(
                        "coffee_notification_task",
                        "coffee_order_event",
                        "coffee_refund",
                        "coffee_payment",
                        "coffee_order_item",
                        "coffee_order",
                        "coffee_pickup_sequence",
                        "sys_admin_coffee_store",
                        "coffee_cart",
                        "coffee_sku",
                        "coffee_product",
                        "coffee_category",
                        "coffee_store",
                        "sys_operation_log",
                        "sys_admin_role",
                        "sys_admin",
                        "app_user")) jdbc.update("DELETE FROM " + t);
        jdbc.update(
                "INSERT INTO sys_admin(id,username,password_hash,status,must_change_pwd,deleted)"
                        + " VALUES(1,'super',?,1,0,0)",
                encoder.encode("super-password"));
        jdbc.update(
                "INSERT INTO sys_admin_role(admin_id,role_id) SELECT 1,id FROM sys_role WHERE"
                        + " role_code='SUPER_ADMIN'");
        jdbc.update(
                "INSERT INTO app_user(id,nickname,openid,status,deleted)"
                        + " VALUES(10,'测试','test-openid',1,0),(11,'另一人','second-openid',1,0)");
        CoffeeStoreSaveDTO s = new CoffeeStoreSaveDTO();
        s.setName("测试咖啡");
        s.setAddress("测试");
        s.setOpeningHours("09:00-18:00");
        s.setBusinessStatus("OPEN");
        store = Long.valueOf(catalog.saveStore(1L, null, s).getId());
        CoffeeCategorySaveDTO c = new CoffeeCategorySaveDTO();
        c.setName("咖啡");
        c.setSort(0);
        c.setStatus(1);
        String category = catalog.saveCategory(1L, store, null, c).getId();
        CoffeeSkuSaveDTO spec = new CoffeeSkuSaveDTO();
        spec.setName("热标准糖");
        spec.setTemperature("HOT");
        spec.setSugar("REGULAR");
        spec.setPriceCent(1800L);
        spec.setStatus(1);
        spec.setAvailable(true);
        CoffeeProductSaveDTO p = new CoffeeProductSaveDTO();
        p.setCategoryId(category);
        p.setName("拿铁");
        p.setDescription("测试");
        p.setSort(0);
        p.setStatus(1);
        p.setSkus(List.of(spec));
        sku = catalog.saveProduct(1L, store, null, p).getSkus().get(0).getId();
        coffeeAdmin =
                Long.valueOf(accounts.create(1L, account("coffee", "COFFEE_ADMIN", store)).getId());
        goodsAdmin =
                Long.valueOf(accounts.create(1L, account("goods", "GOODS_ADMIN", null)).getId());
    }

    AdminAccountSaveDTO account(String username, String role, Long store) {
        var d = new AdminAccountSaveDTO();
        d.setUsername(username);
        d.setRealName(username);
        d.setTemporaryPassword("temporary-password");
        d.setRoleCode(role);
        d.setStoreId(store == null ? null : store.toString());
        return d;
    }

    CoffeeOrderCreateDTO request(String key) {
        var d = new CoffeeOrderCreateDTO();
        d.setStoreId(store.toString());
        d.setRequestKey(key);
        d.setMenuVersion(catalog.menu(store).getVersion());
        var i = new CoffeeOrderCreateDTO.Item();
        i.setSkuId(sku);
        i.setQuantity(2);
        d.setItems(List.of(i));
        return d;
    }

    CoffeeOrderVO create() {
        return orders.create(10L, request(UUID.randomUUID().toString()));
    }

    ObjectNode paid(CoffeeOrderVO o) {
        ObjectNode f = json.createObjectNode();
        f.put("mchid", "coffee-merchant");
        f.put("appid", "coffee-app");
        f.put("out_trade_no", o.getOrderNo());
        f.put("trade_state", "SUCCESS");
        f.put("transaction_id", "wx_" + o.getId());
        f.put("success_time", CoffeeTime.text(CoffeeTime.now()));
        f.putObject("amount").put("total", 3600).put("currency", "CNY");
        return f;
    }

    CoffeeOrderVO pay(CoffeeOrderVO o) {
        orders.paymentFact(Long.valueOf(o.getId()), paid(o));
        return orders.adminDetail(coffeeAdmin, Long.valueOf(o.getId()));
    }

    CoffeeOrderActionDTO version(CoffeeOrderVO o) {
        var d = new CoffeeOrderActionDTO();
        d.setExpectedVersion(o.getVersion());
        return d;
    }

    CoffeeOrderRefundDTO refund(CoffeeOrderVO o, String key) {
        var d = new CoffeeOrderRefundDTO();
        d.setExpectedVersion(o.getVersion());
        d.setRequestKey(key);
        d.setReason("顾客取消");
        return d;
    }

    void error(BizCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BizException.class, action).getBizCode());
    }

    CoffeeOrderCreateDTO requestWithOldMenu(CoffeeOrderCreateDTO d) {
        d.setRequestKey("new-key");
        return d;
    }

    ObjectNode refundFact(Long id, String state) {
        var r = orders.refund(id);
        var p = orders.payment(id);
        ObjectNode f = json.createObjectNode();
        f.put("mchid", p.getMerchantId());
        f.put("out_trade_no", p.getMerchantOrderNo());
        f.put("out_refund_no", r.getRefundNo());
        f.put("transaction_id", p.getTransactionId());
        f.put("refund_id", "wx_refund_" + id);
        f.put("status", state);
        f.put("success_time", CoffeeTime.text(CoffeeTime.now()));
        f.putObject("amount")
                .put("total", p.getAmountCent())
                .put("refund", r.getAmountCent())
                .put("currency", "CNY");
        return f;
    }

    List<Object> race(Callable<?> a, Callable<?> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> action : List.of(a, b))
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    try {
                                        return action.call();
                                    } catch (BizException e) {
                                        return e;
                                    }
                                }));
            start.countDown();
            return List.of(
                    futures.get(0).get(20, TimeUnit.SECONDS),
                    futures.get(1).get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }
}
