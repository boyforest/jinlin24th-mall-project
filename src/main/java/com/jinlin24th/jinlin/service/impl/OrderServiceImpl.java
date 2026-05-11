package com.jinlin24th.jinlin.service.impl;

import com.jinlin24th.jinlin.common.mq.OrderCreatedEvent;
import com.jinlin24th.jinlin.common.mq.OrderCreatedSpringEvent;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jinlin24th.jinlin.pojo.dto.OrderCreateDTO;
import com.jinlin24th.jinlin.pojo.entity.OrderItem;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.pojo.entity.Product;
import com.jinlin24th.jinlin.pojo.entity.ProductSku;
import com.jinlin24th.jinlin.pojo.vo.OrderVO;
import com.jinlin24th.jinlin.service.OrderItemService;
import com.jinlin24th.jinlin.service.OrderMasterService;
import com.jinlin24th.jinlin.service.OrderService;
import com.jinlin24th.jinlin.service.ProductService;
import com.jinlin24th.jinlin.service.ProductSkuService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMasterService orderMasterService;

    @Autowired
    private OrderItemService orderItemService;

    @Autowired
    private ProductSkuService productSkuService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    public OrderVO create(Long userId, OrderCreateDTO dto) {
        // 下单（简化版）：
        // 1) 校验入参
        // 2) 生成订单号
        // 3) 逐条校验 SKU/商品，并生成订单明细快照
        // 4) 计算总金额，落库 order_master + order_item
        //
        // 注意：当前实现未扣库存/未做防超卖，后续可加“原子扣减库存 + 库存流水”
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            return null;
        }

        String orderNo = generateOrderNo();
        // 将 DTO items 转成订单明细（包含价格/商品名/图片等快照信息）
        List<OrderItem> items = dto.getItems().stream().map(i -> {
            if (i == null || i.getSkuId() == null || i.getQuantity() == null || i.getQuantity() <= 0) {
                return null;
            }
            ProductSku sku = productSkuService.getById(i.getSkuId());
            if (sku == null) {
                return null;
            }
            Product product = productService.getById(sku.getProductId());
            if (product == null) {
                return null;
            }

            // 价格以 SKU 的 memberPrice 优先（简化实现：未根据会员等级折扣动态计算）
            BigDecimal price = sku.getMemberPrice() != null ? sku.getMemberPrice() : sku.getPrice();
            BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(i.getQuantity()));

            OrderItem item = new OrderItem();
            item.setOrderNo(orderNo);
            item.setProductId(product.getId());
            item.setSkuId(sku.getId());
            item.setProductName(product.getName());
            item.setSkuName(sku.getSkuName());
            item.setProductImage(product.getMainImage());
            item.setPrice(price);
            item.setQuantity(i.getQuantity());
            item.setTotalPrice(totalPrice);
            item.setCreateTime(LocalDateTime.now());
            return item;
        }).filter(Objects::nonNull).collect(Collectors.toList());

        if (items.isEmpty()) {
            return null;
        }

        BigDecimal totalAmount = items.stream()
            .map(OrderItem::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 构建订单主表（master）
        OrderMaster master = new OrderMaster();
        master.setOrderNo(orderNo);
        master.setUserId(userId);
        master.setTotalAmount(totalAmount);
        master.setPayAmount(totalAmount);
        master.setFreightAmount(BigDecimal.ZERO);
        master.setDiscountAmount(BigDecimal.ZERO);
        master.setPointsUsed(0);
        master.setPointsGained(0);
        master.setStatus(0);
        master.setPayType(null);
        master.setPayTime(null);
        master.setDeliveryTime(null);
        master.setReceiveTime(null);
        master.setReceiverName(dto.getReceiverName());
        master.setReceiverPhone(dto.getReceiverPhone());
        master.setReceiverAddress(dto.getReceiverAddress());
        master.setRemark(dto.getRemark());
        master.setAdminId(null);
        master.setCreateTime(LocalDateTime.now());
        master.setUpdateTime(LocalDateTime.now());

        orderMasterService.save(master);

        // 绑定 order_item 的 order_id（依赖 master 保存后生成的自增 id）
        for (OrderItem item : items) {
            item.setOrderId(master.getId());
        }
        orderItemService.saveBatch(items);

        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(master.getId());
        event.setOrderNo(master.getOrderNo());
        event.setUserId(master.getUserId());
        event.setTotalAmount(master.getTotalAmount());
        event.setItemCount(items.size());
        event.setCreateTime(LocalDateTime.now().toString());
        applicationEventPublisher.publishEvent(new OrderCreatedSpringEvent(this, event));

        return toVO(master, items);
    }

    @Override
    public IPage<OrderVO> userPage(Long userId, long page, long size, Integer status) {
        // 用户端分页：只返回当前用户订单，默认不返回 items（列表场景避免数据量过大）
        Page<OrderMaster> p = new Page<>(page, size);
        IPage<OrderMaster> entityPage = orderMasterService.lambdaQuery()
            .eq(OrderMaster::getUserId, userId)
            .eq(status != null, OrderMaster::getStatus, status)
            .orderByDesc(OrderMaster::getId)
            .page(p);
        Page<OrderVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(m -> {
            OrderVO vo = new OrderVO();
            BeanUtils.copyProperties(m, vo);
            vo.setItems(null);
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public IPage<OrderVO> adminPage(long page, long size, Integer status, Long userId, String orderNo) {
        // 管理端分页：支持按状态/用户/订单号筛选
        Page<OrderMaster> p = new Page<>(page, size);
        IPage<OrderMaster> entityPage = orderMasterService.lambdaQuery()
            .eq(status != null, OrderMaster::getStatus, status)
            .eq(userId != null, OrderMaster::getUserId, userId)
            .like(orderNo != null && !orderNo.isBlank(), OrderMaster::getOrderNo, orderNo)
            .orderByDesc(OrderMaster::getId)
            .page(p);
        Page<OrderVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(m -> {
            OrderVO vo = new OrderVO();
            BeanUtils.copyProperties(m, vo);
            vo.setItems(null);
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public OrderVO get(Long id) {
        // 管理端查看订单详情：按订单ID查询（不做 userId 归属校验）
        OrderMaster master = orderMasterService.getById(id);
        if (master == null) {
            return null;
        }
        List<OrderItem> items = orderItemService.lambdaQuery()
            .eq(OrderItem::getOrderId, id)
            .orderByAsc(OrderItem::getId)
            .list();
        return toVO(master, items);
    }

    @Override
    public OrderVO getForUser(Long userId, Long id) {
        // 用户端查看订单详情：必须带 userId 归属校验（防越权）
        if (userId == null || id == null) {
            return null;
        }
        OrderMaster master = orderMasterService.lambdaQuery()
            .eq(OrderMaster::getId, id)
            .eq(OrderMaster::getUserId, userId)
            .one();
        if (master == null) {
            return null;
        }
        List<OrderItem> items = orderItemService.lambdaQuery()
            .eq(OrderItem::getOrderId, id)
            .orderByAsc(OrderItem::getId)
            .list();
        return toVO(master, items);
    }

    private OrderVO toVO(OrderMaster master, List<OrderItem> items) {
        // 组装 VO：master + items
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(master, vo);
        vo.setItems(items.stream().map(i -> {
            OrderVO.Item iv = new OrderVO.Item();
            BeanUtils.copyProperties(i, iv);
            return iv;
        }).collect(Collectors.toList()));
        return vo;
    }

    private String generateOrderNo() {
        // 订单号（简化）：JL + 当前毫秒 + 4位随机数
        // 说明：学习项目可用；生产建议用雪花算法/数据库唯一约束等保证全局唯一
        long now = System.currentTimeMillis();
        int suffix = new Random().nextInt(9000) + 1000;
        return "JL" + now + suffix;
    }
}
