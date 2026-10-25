package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.jinlin24th.jinlin.common.auth.AdminPermissionService;
import com.jinlin24th.jinlin.common.coffee.*;
import com.jinlin24th.jinlin.common.config.CoffeePayConfig;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.mapper.*;
import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.pojo.entity.*;
import com.jinlin24th.jinlin.pojo.vo.*;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** 只负责数据库事实和状态机；渠道请求由 CoffeePaymentService 在事务外执行。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class CoffeeOrderService {
    public static final List<String> STAGES =
            List.of("WAIT_ACCEPT", "WAIT_MAKE", "MAKING", "READY");
    private static final Set<String> STATUSES =
            Set.of(
                    "WAIT_PAYMENT",
                    "WAIT_ACCEPT",
                    "WAIT_MAKE",
                    "MAKING",
                    "READY",
                    "COMPLETED",
                    "CANCELLED",
                    "REJECTED");
    private final CoffeeOrderMapper orders;
    private final CoffeeOrderItemMapper items;
    private final CoffeePaymentMapper payments;
    private final CoffeeRefundMapper refunds;
    private final CoffeeOrderEventMapper events;
    private final CoffeePickupSequenceMapper sequences;
    private final CoffeeNotificationTaskMapper notifications;
    private final AppUserMapper users;
    private final SysAdminMapper admins;
    private final CoffeeStoreMapper stores;
    private final CoffeeCatalogService catalog;
    private final AdminPermissionService permissions;
    private final CoffeePayConfig config;

    @Value("${app.order.timeout-cancel-seconds:1800}")
    private long timeoutSeconds = 1800;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CoffeeOrderVO create(Long userId, CoffeeOrderCreateDTO dto) {
        if (userId == null || users.lockById(userId) == null)
            throw BizException.unauthorized("用户已失效");
        String hash = createHash(dto);
        CoffeeOrder existing =
                orders.selectOne(
                        Wrappers.<CoffeeOrder>lambdaQuery()
                                .eq(CoffeeOrder::getUserId, userId)
                                .eq(CoffeeOrder::getRequestKey, dto.getRequestKey()));
        if (existing != null) {
            sameHash(existing.getRequestHash(), hash);
            return view(existing, null, false);
        }
        ordering();
        Long storeId = CoffeeValues.id(dto.getStoreId());
        CoffeeStore store = stores.lockById(storeId);
        if (store == null || store.getStatus() != 1)
            throw BizException.of(BizCode.COFFEE_STORE_NOT_FOUND);
        if (!"OPEN".equals(store.getBusinessStatus()))
            throw BizException.of(BizCode.COFFEE_STORE_CLOSED);
        CoffeeMenuVO menu = catalog.menu(storeId);
        if (!menu.getVersion().equals(dto.getMenuVersion()))
            throw BizException.of(BizCode.COFFEE_MENU_CONFLICT);
        Map<Long, CoffeeMenuVO.Sku> bySku = new HashMap<>();
        Map<Long, CoffeeMenuVO.Product> byProduct = new HashMap<>();
        for (var p : menu.getProducts())
            for (var sku : p.getSkus()) {
                bySku.put(Long.valueOf(sku.getId()), sku);
                byProduct.put(Long.valueOf(sku.getId()), p);
            }
        SortedMap<Long, Integer> quantities = quantities(dto);
        List<CoffeeOrderItem> snapshots = new ArrayList<>();
        long total = 0;
        int count = 0;
        for (var entry : quantities.entrySet()) {
            var sku = bySku.get(entry.getKey());
            var p = byProduct.get(entry.getKey());
            if (sku == null) throw BizException.of(BizCode.COFFEE_SKU_NOT_FOUND);
            if (!Boolean.TRUE.equals(sku.getAvailable()))
                throw BizException.of(BizCode.COFFEE_SKU_UNAVAILABLE);
            CoffeeOrderItem item = new CoffeeOrderItem();
            item.setSkuId(entry.getKey());
            item.setProductId(Long.valueOf(p.getId()));
            item.setProductName(p.getName());
            item.setSpecName(sku.getName());
            item.setImageUrl(p.getImageUrl());
            item.setTemperature(sku.getTemperature());
            item.setSugar(sku.getSugar());
            item.setUnitPriceCent(sku.getPriceCent());
            item.setQuantity(entry.getValue());
            try {
                item.setLineTotalCent(Math.multiplyExact(sku.getPriceCent(), entry.getValue()));
                total = Math.addExact(total, item.getLineTotalCent());
                count = Math.addExact(count, entry.getValue());
            } catch (ArithmeticException e) {
                throw BizException.badRequest("金额超出范围");
            }
            if (total > 9007199254740991L) throw BizException.badRequest("金额超出范围");
            snapshots.add(item);
        }
        if (total <= 0) throw BizException.badRequest("应付金额必须大于0");
        LocalDateTime now = CoffeeTime.now();
        LocalDate date = now.toLocalDate();
        CoffeePickupSequence sequence =
                sequences.selectOne(
                        Wrappers.<CoffeePickupSequence>lambdaQuery()
                                .eq(CoffeePickupSequence::getStoreId, storeId)
                                .eq(CoffeePickupSequence::getPickupDate, date));
        if (sequence == null) {
            sequence = new CoffeePickupSequence();
            sequence.setStoreId(storeId);
            sequence.setPickupDate(date);
            sequence.setNextNumber(1L);
            sequences.insert(sequence);
        }
        long number = sequence.getNextNumber();
        sequence.setNextNumber(Math.addExact(number, 1));
        sequences.updateById(sequence);
        CoffeeOrder order = new CoffeeOrder();
        // 完整 UUID 的 32 位十六进制表示，在创建时持久化并用于所有支付重试。
        order.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
        order.setUserId(userId);
        order.setStoreId(storeId);
        order.setStoreName(store.getName());
        order.setRequestKey(dto.getRequestKey());
        order.setRequestHash(hash);
        order.setPickupDate(date);
        order.setPickupCode(String.format(Locale.ROOT, "%04d", number));
        order.setFulfillmentStatus("WAIT_PAYMENT");
        order.setPaymentStatus("UNPAID");
        order.setRefundStatus("NONE");
        order.setRemark(normalize(dto.getRemark()));
        order.setItemCount(count);
        order.setTotalAmountCent(total);
        order.setPayableAmountCent(total);
        order.setRefundedAmountCent(0L);
        order.setVersion(0L);
        order.setCreatedAt(now);
        order.setExpiresAt(now.plusSeconds(timeoutSeconds));
        order.setStageEnteredAt(now);
        orders.insert(order);
        for (var item : snapshots) {
            item.setOrderId(order.getId());
            items.insert(item);
        }
        CoffeePayment payment = new CoffeePayment();
        payment.setOrderId(order.getId());
        payment.setMerchantOrderNo(order.getOrderNo());
        payment.setMerchantId(config.getMchId());
        payment.setAppId(config.getAppId());
        payment.setAmountCent(total);
        payment.setStatus("UNPAID");
        payment.setInitiated(false);
        payment.setCloseRequested(false);
        payment.setNextQueryAt(now);
        payments.insert(payment);
        event(order, "CREATE", null, null);
        return view(order, null, false);
    }

    private SortedMap<Long, Integer> quantities(CoffeeOrderCreateDTO dto) {
        SortedMap<Long, Integer> result = new TreeMap<>();
        if (dto.getItems() == null || dto.getItems().isEmpty() || dto.getItems().size() > 100)
            throw BizException.badRequest("明细无效");
        for (var i : dto.getItems()) {
            if (i == null || i.getQuantity() == null || i.getQuantity() < 1 || i.getQuantity() > 99)
                throw BizException.badRequest("数量必须为1至99");
            Long id = CoffeeValues.id(i.getSkuId());
            int sum = result.getOrDefault(id, 0) + i.getQuantity();
            if (sum > 99) throw BizException.badRequest("同一规格合计不能超过99杯");
            result.put(id, sum);
        }
        return result;
    }

    private String createHash(CoffeeOrderCreateDTO dto) {
        return hash(
                CoffeeValues.id(dto.getStoreId())
                        + "\n"
                        + dto.getMenuVersion()
                        + "\n"
                        + quantities(dto)
                        + "\n"
                        + Objects.toString(normalize(dto.getRemark()), ""));
    }

    private static String normalize(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    public static String hash(String s) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sameHash(String stored, String requested) {
        if (!stored.equals(requested)) throw BizException.of(BizCode.COFFEE_REQUEST_CONFLICT);
    }

    public void ordering() {
        if (!config.isOrderingEnabled()) throw BizException.of(BizCode.COFFEE_ORDERING_DISABLED);
        if (!config.isPaymentEnabled()) throw BizException.of(BizCode.COFFEE_CHANNEL_UNAVAILABLE);
    }

    public CoffeeOrderVO userDetail(Long userId, Long id) {
        return view(owner(userId, id, false), null, false);
    }

    public CoffeeOrderVO adminDetail(Long actor, Long id) {
        CoffeeOrder o = load(id, false);
        permissions.requireStore(actor, o.getStoreId(), "coffee:orders:read");
        return view(o, actor, true);
    }

    public CoffeeOrder owner(Long userId, Long id, boolean lock) {
        CoffeeOrder o = load(id, lock);
        if (!o.getUserId().equals(userId)) throw BizException.of(BizCode.COFFEE_ORDER_NOT_FOUND);
        return o;
    }

    private CoffeeOrder load(Long id, boolean lock) {
        CoffeeOrder o = lock ? orders.lockById(id) : orders.selectById(id);
        if (o == null) throw BizException.of(BizCode.COFFEE_ORDER_NOT_FOUND);
        return o;
    }

    public CoffeePayment payment(Long orderId) {
        return payments.selectOne(
                Wrappers.<CoffeePayment>lambdaQuery().eq(CoffeePayment::getOrderId, orderId));
    }

    public CoffeeRefund refund(Long orderId) {
        return refunds.selectOne(
                Wrappers.<CoffeeRefund>lambdaQuery().eq(CoffeeRefund::getOrderId, orderId));
    }

    public AppUser user(Long userId) {
        return users.selectById(userId);
    }

    public Map<String, Object> workbench(Long actor, Long storeId) {
        permissions.requireStore(actor, storeId, "coffee:orders:read");
        List<Map<String, Object>> columns = new ArrayList<>();
        for (String status : STAGES) {
            var p = list(actor, storeId, 1, 20, status, null, null, null, null);
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("status", status);
            c.put("records", p.getRecords());
            c.put("total", p.getTotal());
            c.put("current", p.getCurrent());
            c.put("size", p.getSize());
            c.put("pages", p.getPages());
            columns.add(c);
        }
        return Map.of(
                "storeId",
                storeId.toString(),
                "serverTime",
                CoffeeTime.text(CoffeeTime.now()),
                "columns",
                columns);
    }

    public IPage<CoffeeOrderVO> list(
            Long actor,
            Long storeId,
            int page,
            int size,
            String status,
            String refundStatus,
            String keyword,
            LocalDate from,
            LocalDate to) {
        permissions.requireStore(actor, storeId, "coffee:orders:read");
        AdminAccountService.page(page, size);
        if (status != null && !STATUSES.contains(status)) throw BizException.badRequest("订单状态无效");
        if (refundStatus != null
                && !Set.of("NONE", "PENDING", "SUCCESS", "FAILED").contains(refundStatus))
            throw BizException.badRequest("退款状态无效");
        boolean stage = status != null && STAGES.contains(status) && from == null && to == null;
        var q =
                Wrappers.<CoffeeOrder>lambdaQuery()
                        .eq(CoffeeOrder::getStoreId, storeId)
                        .eq(status != null, CoffeeOrder::getFulfillmentStatus, status)
                        .eq(refundStatus != null, CoffeeOrder::getRefundStatus, refundStatus);
        if (stage) {
            q.eq(CoffeeOrder::getRefundStatus, "NONE")
                    .orderByAsc(CoffeeOrder::getStageEnteredAt)
                    .orderByAsc(CoffeeOrder::getId);
        } else {
            LocalDate[] dates = dates(from, to);
            q.ge(CoffeeOrder::getCreatedAt, dates[0].atStartOfDay())
                    .lt(CoffeeOrder::getCreatedAt, dates[1].plusDays(1).atStartOfDay())
                    .orderByDesc(CoffeeOrder::getCreatedAt)
                    .orderByDesc(CoffeeOrder::getId);
        }
        if (keyword != null && !keyword.isBlank())
            q.and(
                    w ->
                            w.like(CoffeeOrder::getOrderNo, keyword.trim())
                                    .or()
                                    .like(CoffeeOrder::getPickupCode, keyword.trim()));
        return orders.selectPage(new Page<>(page, size), q).convert(o -> view(o, actor, false));
    }

    private LocalDate[] dates(LocalDate from, LocalDate to) {
        LocalDate today = CoffeeTime.now().toLocalDate();
        from = from == null ? (to == null ? today : to) : from;
        to = to == null ? from : to;
        if (to.isBefore(from)) throw BizException.badRequest("日期范围无效");
        return new LocalDate[] {from, to};
    }

    public Map<String, Object> ledger(Long actor, Long storeId, LocalDate from, LocalDate to) {
        permissions.requireStore(actor, storeId, "coffee:ledger:read");
        LocalDate[] d = dates(from, to);
        LocalDateTime start = d[0].atStartOfDay(), end = d[1].plusDays(1).atStartOfDay();
        // 独立支付/退款成功时间口径，绝不使用制作状态推导收入。
        Map<String, Object> totals = payments.ledger(storeId, start, end);
        long count = number(totals, "paidCount"), received = number(totals, "received");
        long returned = safe(refunds.ledger(storeId, start, end));
        return Map.of(
                "storeId",
                storeId.toString(),
                "dateFrom",
                d[0].toString(),
                "dateTo",
                d[1].toString(),
                "timeZone",
                "Asia/Shanghai",
                "paidOrderCount",
                count,
                "receivedAmountCent",
                received,
                "refundedAmountCent",
                returned,
                "netReceivedAmountCent",
                received - returned);
    }

    private static long number(Map<String, Object> row, String name) {
        return row.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(e -> safe(new java.math.BigDecimal(e.getValue().toString())))
                .findFirst()
                .orElseThrow();
    }

    private static long safe(java.math.BigDecimal value) {
        long n = value.longValueExact();
        if (n < 0 || n > CoffeeValues.MAX_SAFE_INTEGER)
            throw BizException.badRequest("流水统计金额超出安全整数范围");
        return n;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CoffeeOrderVO action(Long actor, Long id, String action, CoffeeOrderActionDTO dto) {
        CoffeeOrder o = load(id, true);
        permissions.requireStore(actor, o.getStoreId(), "coffee:orders:operate");
        String source, target;
        switch (action) {
            case "ACCEPT" -> {
                source = "WAIT_ACCEPT";
                target = "WAIT_MAKE";
            }
            case "START" -> {
                source = "WAIT_MAKE";
                target = "MAKING";
            }
            case "READY" -> {
                source = "MAKING";
                target = "READY";
            }
            case "COMPLETE" -> {
                source = "READY";
                target = "COMPLETED";
            }
            default -> throw BizException.badRequest("动作无效");
        }
        if (!"PAID".equals(o.getPaymentStatus()) || !"NONE".equals(o.getRefundStatus())) conflict();
        if (target.equals(o.getFulfillmentStatus())) return view(o, actor, false);
        version(o, dto.getExpectedVersion());
        if (!source.equals(o.getFulfillmentStatus())) conflict();
        LocalDateTime now = CoffeeTime.now();
        switch (action) {
            case "ACCEPT" -> o.setAcceptedAt(now);
            case "START" -> o.setMakingAt(now);
            case "READY" -> o.setReadyAt(now);
            case "COMPLETE" -> o.setCompletedAt(now);
        }
        transition(o, target);
        CoffeeOrderEvent e = event(o, action, actor, null);
        if (action.equals("READY")) {
            CoffeeNotificationTask task = new CoffeeNotificationTask();
            task.setOrderId(id);
            task.setReadyEventId(e.getId());
            task.setTemplateType("READY");
            task.setStatus("PENDING");
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            notifications.insert(task);
        }
        return view(o, actor, false);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CoffeeOrderVO requestRefund(
            Long actor, Long id, boolean reject, CoffeeOrderRefundDTO dto) {
        CoffeeOrder o = load(id, true);
        permissions.requireStore(actor, o.getStoreId(), "coffee:orders:refund");
        String action = reject ? "REJECT" : "REFUND";
        String hash =
                hash(
                        id
                                + "\n"
                                + action
                                + "\n"
                                + dto.getExpectedVersion()
                                + "\n"
                                + dto.getReason().trim());
        CoffeeRefund byKey =
                refunds.selectOne(
                        Wrappers.<CoffeeRefund>lambdaQuery()
                                .eq(CoffeeRefund::getRequestKey, dto.getRequestKey()));
        if (byKey != null) {
            sameHash(byKey.getRequestHash(), hash);
            return view(o, actor, false);
        }
        version(o, dto.getExpectedVersion());
        if (!"PAID".equals(o.getPaymentStatus())
                || !"NONE".equals(o.getRefundStatus())
                || refund(id) != null) conflict();
        if (reject && !"WAIT_ACCEPT".equals(o.getFulfillmentStatus())) conflict();
        makeRefund(o, actor, dto.getRequestKey(), hash, dto.getReason().trim());
        transition(
                o,
                reject
                        ? "REJECTED"
                        : "COMPLETED".equals(o.getFulfillmentStatus()) ? "COMPLETED" : "CANCELLED");
        event(o, action, actor, dto.getReason().trim());
        return view(o, actor, false);
    }

    private void makeRefund(CoffeeOrder o, Long actor, String key, String hash, String reason) {
        CoffeePayment p = payment(o.getId());
        if (!"PAID".equals(p.getStatus()) || p.getAmountCent() <= 0) conflict();
        CoffeeRefund r = new CoffeeRefund();
        r.setOrderId(o.getId());
        r.setRefundNo("CR" + UUID.randomUUID().toString().replace("-", ""));
        r.setRequestKey(key);
        r.setRequestHash(hash);
        r.setReason(reason);
        r.setOperatorId(actor);
        r.setAmountCent(p.getAmountCent());
        r.setStatus("PENDING");
        r.setSubmitted(false);
        r.setCreatedAt(CoffeeTime.now());
        r.setNextQueryAt(CoffeeTime.now());
        try {
            refunds.insert(r);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw BizException.of(BizCode.COFFEE_REQUEST_CONFLICT);
        }
        o.setRefundStatus("PENDING");
        o.setRefundReason(reason);
    }

    private static void version(CoffeeOrder o, Long expected) {
        if (!o.getVersion().equals(expected)) conflict();
    }

    private static void conflict() {
        throw BizException.of(BizCode.COFFEE_ORDER_CONFLICT);
    }

    private void transition(CoffeeOrder o, String target) {
        o.setFulfillmentStatus(target);
        o.setStageEnteredAt(CoffeeTime.now());
        bump(o);
    }

    private void bump(CoffeeOrder o) {
        if (o.getVersion() >= 9007199254740991L) conflict();
        o.setVersion(o.getVersion() + 1);
        orders.updateById(o);
    }

    private CoffeeOrderEvent event(CoffeeOrder o, String action, Long actor, String reason) {
        CoffeeOrderEvent e = new CoffeeOrderEvent();
        e.setOrderId(o.getId());
        e.setAction(action);
        e.setOperatorId(actor);
        SysAdmin a = actor == null ? null : admins.selectById(actor);
        e.setOperatorName(a == null ? "系统/顾客" : Objects.toString(a.getRealName(), a.getUsername()));
        e.setReason(reason);
        e.setCreatedAt(CoffeeTime.now());
        events.insert(e);
        return e;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CoffeePayment preparePayment(Long userId, Long id) {
        ordering();
        CoffeeOrder o = owner(userId, id, true);
        CoffeePayment p = payment(id);
        if (!"WAIT_PAYMENT".equals(o.getFulfillmentStatus())
                || !"UNPAID".equals(o.getPaymentStatus())
                || !o.getExpiresAt().isAfter(CoffeeTime.now())
                || p.getCloseRequested()) conflict();
        merchant(p);
        // 历史映射保持不变；不向渠道提交不符合协议的旧单号，也不临时换号。
        if (p.getMerchantOrderNo() == null
                || !p.getMerchantOrderNo().matches("[A-Za-z0-9_\\-|*]{6,32}")) invalid();
        p.setInitiated(true);
        p.setNextQueryAt(CoffeeTime.now().plusSeconds(30));
        payments.updateById(p);
        return p;
    }

    public void merchant(CoffeePayment p) {
        if (!Objects.equals(config.getMchId(), p.getMerchantId())
                || !Objects.equals(config.getAppId(), p.getAppId()))
            throw BizException.of(BizCode.COFFEE_CHANNEL_INVALID);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void savePrepay(Long id, String prepayId) {
        CoffeeOrder o = load(id, true);
        CoffeePayment p = payment(id);
        p.setPrepayId(prepayId);
        payments.updateById(p);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CoffeePayment cancel(Long userId, Long id, boolean expired) {
        CoffeeOrder o = userId == null ? load(id, true) : owner(userId, id, true);
        CoffeePayment p = payment(id);
        if ("PAID".equals(o.getPaymentStatus())) {
            if (expired) return p;
            conflict();
        }
        if (expired && o.getExpiresAt().isAfter(CoffeeTime.now())) return p;
        if (!"CANCELLED".equals(o.getFulfillmentStatus())) {
            if (!"WAIT_PAYMENT".equals(o.getFulfillmentStatus())) conflict();
            transition(o, "CANCELLED");
            event(o, expired ? "EXPIRE" : "CANCEL", null, null);
        }
        p.setCloseRequested(true);
        p.setNextQueryAt(CoffeeTime.now());
        if (!p.getInitiated()) {
            p.setStatus("CLOSED");
            o.setPaymentStatus("CLOSED");
            orders.updateById(o);
        }
        payments.updateById(p);
        return p;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void paymentFact(Long id, JsonNode fact) {
        CoffeeOrder o = load(id, true);
        CoffeePayment p = payment(id);
        merchant(p);
        channelEqual(fact, "mchid", p.getMerchantId());
        channelEqual(fact, "appid", p.getAppId());
        channelEqual(fact, "out_trade_no", p.getMerchantOrderNo());
        String state = fact.path("trade_state").asText();
        if ("SUCCESS".equals(state)) {
            if (!fact.path("amount").path("total").isIntegralNumber()
                    || !fact.path("amount").path("total").canConvertToLong()
                    || fact.path("amount").path("total").asLong() != p.getAmountCent()
                    || !"CNY".equals(fact.path("amount").path("currency").asText())
                    || fact.path("transaction_id").asText().isBlank()) invalid();
            if ("PAID".equals(p.getStatus())) {
                channelEqual(fact, "transaction_id", p.getTransactionId());
                return;
            }
            LocalDateTime paidAt = channelTime(fact, "success_time");
            p.setStatus("PAID");
            p.setTransactionId(fact.path("transaction_id").asText());
            p.setPaidAt(paidAt);
            payments.updateById(p);
            boolean invalidated =
                    !"WAIT_PAYMENT".equals(o.getFulfillmentStatus())
                            || p.getCloseRequested()
                            || paidAt.isAfter(o.getExpiresAt());
            o.setPaymentStatus("PAID");
            o.setPaidAmountCent(p.getAmountCent());
            o.setPaidAt(paidAt);
            if (invalidated) {
                makeRefund(o, null, "late_" + id, hash("late_" + id), "失效订单收到渠道付款");
                transition(o, "CANCELLED");
            } else transition(o, "WAIT_ACCEPT");
            event(o, "PAYMENT_SUCCESS", null, invalidated ? "已登记全额退款" : null);
        } else if (Set.of("CLOSED", "REVOKED", "PAYERROR").contains(state)
                && !"PAID".equals(p.getStatus())
                && !"CLOSED".equals(p.getStatus())) {
            p.setStatus("CLOSED");
            payments.updateById(p);
            o.setPaymentStatus("CLOSED");
            if ("WAIT_PAYMENT".equals(o.getFulfillmentStatus())) {
                transition(o, "CANCELLED");
                event(o, "PAYMENT_CLOSED", null, null);
            } else bump(o);
        }
    }

    private static void channelEqual(JsonNode n, String key, String expected) {
        if (expected == null || !expected.equals(n.path(key).asText())) invalid();
    }

    private static LocalDateTime channelTime(JsonNode n, String key) {
        try {
            return CoffeeTime.channel(n.path(key).asText());
        } catch (Exception e) {
            throw BizException.of(BizCode.COFFEE_CHANNEL_INVALID);
        }
    }

    private static void invalid() {
        throw BizException.of(BizCode.COFFEE_CHANNEL_INVALID);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CoffeeRefund claimRefund(Long id) {
        CoffeeOrder o = load(id, true);
        CoffeeRefund r = refund(id);
        if (r == null || "SUCCESS".equals(r.getStatus())) return null;
        if (r.getNextQueryAt().isAfter(CoffeeTime.now())) return null;
        r.setNextQueryAt(CoffeeTime.now().plusSeconds(60));
        refunds.updateById(r);
        return r;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void refundSubmitted(Long id) {
        load(id, true);
        CoffeeRefund r = refund(id);
        r.setSubmitted(true);
        refunds.updateById(r);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void refundFact(Long id, JsonNode fact) {
        applyRefundFact(id, fact, false);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void refundNotificationFact(Long id, JsonNode fact) {
        applyRefundFact(id, fact, true);
    }

    private void applyRefundFact(Long id, JsonNode fact, boolean notification) {
        CoffeeOrder o = load(id, true);
        CoffeeRefund r = refund(id);
        CoffeePayment p = payment(id);
        merchant(p);
        if (r == null) invalid();
        channelEqual(fact, "mchid", p.getMerchantId());
        channelEqual(fact, "out_trade_no", p.getMerchantOrderNo());
        channelEqual(fact, "out_refund_no", r.getRefundNo());
        channelEqual(fact, "transaction_id", p.getTransactionId());
        JsonNode a = fact.path("amount");
        if (!a.path("refund").isIntegralNumber()
                || !a.path("refund").canConvertToLong()
                || a.path("refund").asLong() != r.getAmountCent()
                || !a.path("total").isIntegralNumber()
                || !a.path("total").canConvertToLong()
                || a.path("total").asLong() != p.getAmountCent()
                // 官方退款通知不含 currency；查询/申请应答仍必须提供，通知提供时也校验。
                || ((!notification || a.has("currency"))
                        && !"CNY".equals(a.path("currency").asText()))) invalid();
        String state = fact.path(notification ? "refund_status" : "status").asText();
        if ("SUCCESS".equals(r.getStatus())) return;
        if ("SUCCESS".equals(state)) {
            if (fact.path("refund_id").asText().isBlank()) invalid();
            r.setStatus("SUCCESS");
            r.setSucceededAt(channelTime(fact, "success_time"));
            r.setChannelRefundId(fact.path("refund_id").asText());
            o.setRefundStatus("SUCCESS");
            o.setRefundedAmountCent(r.getAmountCent());
            refunds.updateById(r);
            bump(o);
            event(o, "REFUND_SUCCESS", null, null);
        } else if (Set.of("CLOSED", "ABNORMAL").contains(state)) {
            if (!"FAILED".equals(r.getStatus())) {
                r.setStatus("FAILED");
                r.setLastError(state);
                o.setRefundStatus("FAILED");
                refunds.updateById(r);
                bump(o);
                event(o, "REFUND_FAILED", null, state);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public boolean claimPayment(Long id) {
        load(id, true);
        CoffeePayment p = payment(id);
        if (!"UNPAID".equals(p.getStatus()) || p.getNextQueryAt().isAfter(CoffeeTime.now()))
            return false;
        p.setNextQueryAt(CoffeeTime.now().plusSeconds(60));
        payments.updateById(p);
        return true;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void channelError(Long id, boolean refund, String reason) {
        load(id, true);
        if (refund) {
            CoffeeRefund r = refund(id);
            r.setLastError(reason);
            refunds.updateById(r);
        } else {
            CoffeePayment p = payment(id);
            p.setLastError(reason);
            payments.updateById(p);
        }
    }

    private CoffeeOrderVO view(CoffeeOrder o, Long actor, boolean detail) {
        var v = new CoffeeOrderVO();
        BeanUtils.copyProperties(
                o,
                v,
                "id",
                "storeId",
                "pickupDate",
                "createdAt",
                "paidAt",
                "acceptedAt",
                "makingAt",
                "readyAt",
                "completedAt",
                "stageEnteredAt");
        v.setId(o.getId().toString());
        v.setStoreId(o.getStoreId().toString());
        v.setPickupDate(o.getPickupDate().toString());
        v.setCreatedAt(CoffeeTime.text(o.getCreatedAt()));
        v.setPaidAt(CoffeeTime.text(o.getPaidAt()));
        v.setAcceptedAt(CoffeeTime.text(o.getAcceptedAt()));
        v.setMakingAt(CoffeeTime.text(o.getMakingAt()));
        v.setReadyAt(CoffeeTime.text(o.getReadyAt()));
        v.setCompletedAt(CoffeeTime.text(o.getCompletedAt()));
        v.setStageEnteredAt(CoffeeTime.text(o.getStageEnteredAt()));
        v.setItems(
                items
                        .selectList(
                                Wrappers.<CoffeeOrderItem>lambdaQuery()
                                        .eq(CoffeeOrderItem::getOrderId, o.getId())
                                        .orderByAsc(CoffeeOrderItem::getId))
                        .stream()
                        .map(
                                i -> {
                                    var item = new CoffeeOrderVO.Item();
                                    BeanUtils.copyProperties(i, item, "productId", "skuId");
                                    item.setProductId(i.getProductId().toString());
                                    item.setSkuId(i.getSkuId().toString());
                                    return item;
                                })
                        .toList());
        var a = new CoffeeOrderVO.Amounts();
        BeanUtils.copyProperties(o, a);
        v.setAmounts(a);
        v.setAllowedActions(allowed(o, actor));
        var tasks =
                notifications.selectList(
                        Wrappers.<CoffeeNotificationTask>lambdaQuery()
                                .eq(CoffeeNotificationTask::getOrderId, o.getId())
                                .orderByDesc(CoffeeNotificationTask::getId));
        v.setNotificationStatus(tasks.isEmpty() ? "NONE" : tasks.get(0).getStatus());
        if (detail)
            v.setEvents(
                    events
                            .selectList(
                                    Wrappers.<CoffeeOrderEvent>lambdaQuery()
                                            .eq(CoffeeOrderEvent::getOrderId, o.getId())
                                            .orderByAsc(CoffeeOrderEvent::getId))
                            .stream()
                            .map(
                                    e -> {
                                        var ev = new CoffeeOrderVO.Event();
                                        BeanUtils.copyProperties(e, ev, "createdAt");
                                        ev.setCreatedAt(CoffeeTime.text(e.getCreatedAt()));
                                        return ev;
                                    })
                            .toList());
        return v;
    }

    private List<String> allowed(CoffeeOrder o, Long actor) {
        if (actor == null
                || !"PAID".equals(o.getPaymentStatus())
                || !"NONE".equals(o.getRefundStatus())) return List.of();
        List<String> p = permissions.permissions(actor), result = new ArrayList<>();
        if (p.contains("coffee:orders:operate")) {
            String a =
                    switch (o.getFulfillmentStatus()) {
                        case "WAIT_ACCEPT" -> "ACCEPT";
                        case "WAIT_MAKE" -> "START";
                        case "MAKING" -> "READY";
                        case "READY" -> "COMPLETE";
                        default -> null;
                    };
            if (a != null) result.add(a);
        }
        if (p.contains("coffee:orders:refund")) {
            if (o.getFulfillmentStatus().equals("WAIT_ACCEPT")) result.add("REJECT");
            result.add("REFUND");
        }
        return result;
    }
}
