package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jinlin24th.jinlin.common.coffee.CoffeeValues;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.mapper.*;
import com.jinlin24th.jinlin.pojo.dto.BusinessOrderQueryDTO;
import com.jinlin24th.jinlin.pojo.entity.*;
import com.jinlin24th.jinlin.pojo.vo.*;
import com.jinlin24th.jinlin.service.BusinessOrderQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BusinessOrderQueryServiceImpl implements BusinessOrderQueryService {
    private static final Map<Integer, String> STATUSES =
            Map.of(
                    0,
                    "PENDING_PAY",
                    10,
                    "PENDING_SHIP",
                    20,
                    "PENDING_RECEIVE",
                    30,
                    "COMPLETED",
                    40,
                    "CANCELLED",
                    50,
                    "REFUNDING",
                    60,
                    "REFUNDED");
    private static final Map<String, List<Integer>> GROUPS =
            Map.of(
                    "ALL",
                    List.of(0, 10, 20, 30, 40, 50, 60),
                    "IN_PROGRESS",
                    List.of(0, 10, 20),
                    "COMPLETED",
                    List.of(30),
                    "CLOSED",
                    List.of(40),
                    "AFTER_SALES",
                    List.of(50, 60));
    private static final Set<String> COFFEE_STATUSES =
            Set.of(
                    "PENDING_PAY",
                    "PAID",
                    "MAKING",
                    "READY",
                    "COMPLETED",
                    "CANCELLED",
                    "REFUNDING",
                    "REFUNDED");
    private final OrderMasterMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final PaymentRecordMapper paymentMapper;

    @Value("${app.business-order.goods-store-name:金霖二十四养}")
    private String goodsStoreName;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BusinessOrderPageVO list(Long userId, BusinessOrderQueryDTO query) {
        if (userId == null) {
            throw BizException.unauthorized("未登录");
        }
        List<Integer> statuses = filters(query);
        long page = query.getPage();
        long size = query.getSize();
        if ("COFFEE".equals(query.getBusinessType()) || statuses.isEmpty()) {
            return new BusinessOrderPageVO(List.of(), 0, size, page, 0);
        }
        LambdaQueryWrapper<OrderMaster> wrapper =
                Wrappers.<OrderMaster>lambdaQuery()
                        .eq(OrderMaster::getUserId, userId)
                        .in(OrderMaster::getStatus, statuses)
                        .orderByDesc(OrderMaster::getCreateTime, OrderMaster::getId);
        // 阶段 A 仅 GOODS，businessType 排序键是常量；不改旧商品查询与交易实现。
        Page<OrderMaster> orders = orderMapper.selectPage(new Page<>(page, size), wrapper);
        if (orders.getRecords().isEmpty()) {
            return new BusinessOrderPageVO(
                    List.of(), orders.getTotal(), size, page, orders.getPages());
        }
        List<Long> ids = orders.getRecords().stream().map(OrderMaster::getId).toList();
        Map<Long, List<OrderItem>> items =
                itemMapper
                        .selectList(
                                Wrappers.<OrderItem>lambdaQuery()
                                        .in(OrderItem::getOrderId, ids)
                                        .orderByAsc(OrderItem::getId))
                        .stream()
                        .collect(Collectors.groupingBy(OrderItem::getOrderId));
        List<String> orderNos = orders.getRecords().stream().map(OrderMaster::getOrderNo).toList();
        Map<String, PaymentRecord> payments = new HashMap<>();
        paymentMapper
                .selectList(
                        Wrappers.<PaymentRecord>lambdaQuery()
                                .eq(PaymentRecord::getUserId, userId)
                                .in(PaymentRecord::getOrderNo, orderNos)
                                .and(
                                        p ->
                                                p.in(PaymentRecord::getStatus, List.of(1, 3))
                                                        .or()
                                                        .isNotNull(PaymentRecord::getPayTime))
                                .orderByDesc(PaymentRecord::getId))
                .forEach(p -> payments.putIfAbsent(p.getOrderNo(), p));
        List<BusinessOrderSummaryVO> records =
                orders.getRecords().stream()
                        .map(
                                order ->
                                        summary(
                                                order,
                                                items.getOrDefault(order.getId(), List.of()),
                                                payments.get(order.getOrderNo())))
                        .toList();
        return new BusinessOrderPageVO(records, orders.getTotal(), size, page, orders.getPages());
    }

    private List<Integer> filters(BusinessOrderQueryDTO query) {
        String business = query.getBusinessType();
        String group = Objects.requireNonNullElse(query.getStatusGroup(), "ALL");
        String status = query.getStatus();
        if (query.getPage() == null
                || query.getPage() < 1
                || query.getSize() == null
                || query.getSize() < 1
                || query.getSize() > 100
                || (business != null && !Set.of("GOODS", "COFFEE").contains(business))
                || !GROUPS.containsKey(group)) {
            throw CoffeeValues.invalid("订单筛选或分页参数错误");
        }
        if (status == null) {
            return GROUPS.get(group);
        }
        if (!"ALL".equals(group)) {
            throw CoffeeValues.invalid("指定 status 时不能同时指定状态分组");
        }
        boolean valid =
                business == null
                        ? STATUSES.containsValue(status) || COFFEE_STATUSES.contains(status)
                        : "GOODS".equals(business)
                                ? STATUSES.containsValue(status)
                                : COFFEE_STATUSES.contains(status);
        if (!valid) {
            throw CoffeeValues.invalid("当前业务不支持该订单状态");
        }
        return STATUSES.entrySet().stream()
                .filter(e -> e.getValue().equals(status))
                .map(Map.Entry::getKey)
                .toList();
    }

    private BusinessOrderSummaryVO summary(
            OrderMaster order, List<OrderItem> items, PaymentRecord payment) {
        BusinessOrderSummaryVO vo = new BusinessOrderSummaryVO();
        vo.setBusinessType("GOODS");
        vo.setId(order.getId().toString());
        vo.setOrderNo(order.getOrderNo());
        vo.setStoreName(goodsStoreName);
        vo.setFulfillmentType("DELIVERY");
        vo.setStatus(STATUSES.get(order.getStatus()));
        vo.setCreatedAt(
                order.getCreateTime().atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime());
        vo.setItemCount(items.stream().mapToLong(OrderItem::getQuantity).sum());
        vo.setSummaryItems(
                items.stream()
                        .limit(3)
                        .map(
                                item -> {
                                    BusinessOrderSummaryVO.Item result =
                                            new BusinessOrderSummaryVO.Item();
                                    result.setProductId(item.getProductId().toString());
                                    result.setSkuId(item.getSkuId().toString());
                                    result.setProductName(item.getProductName());
                                    result.setSpecName(item.getSkuName());
                                    result.setImageUrl(item.getProductImage());
                                    result.setUnitPriceCent(CoffeeValues.cents(item.getPrice()));
                                    result.setQuantity(item.getQuantity());
                                    result.setLineTotalCent(
                                            CoffeeValues.cents(item.getTotalPrice()));
                                    return result;
                                })
                        .toList());
        BusinessOrderSummaryVO.Amounts amounts = new BusinessOrderSummaryVO.Amounts();
        amounts.setTotalAmountCent(CoffeeValues.cents(order.getTotalAmount()));
        amounts.setDiscountAmountCent(
                CoffeeValues.cents(
                        Objects.requireNonNullElse(order.getDiscountAmount(), BigDecimal.ZERO)));
        amounts.setFreightAmountCent(
                CoffeeValues.cents(
                        Objects.requireNonNullElse(order.getFreightAmount(), BigDecimal.ZERO)));
        amounts.setPayableAmountCent(CoffeeValues.cents(order.getPayAmount()));
        if (payment != null && payment.getPayAmount() != null) {
            amounts.setPaidAmountCent(CoffeeValues.cents(payment.getPayAmount()));
        } else if (order.getPayTime() != null) {
            amounts.setPaidAmountCent(CoffeeValues.cents(order.getPayAmount()));
        }
        vo.setAmounts(amounts);
        return vo;
    }
}
