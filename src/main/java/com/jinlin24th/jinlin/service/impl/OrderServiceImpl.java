package com.jinlin24th.jinlin.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.mq.OrderCreatedEvent;
import com.jinlin24th.jinlin.common.mq.OrderCreatedSpringEvent;
import com.jinlin24th.jinlin.common.mq.producer.OrderTimeoutMessageProducer;
import com.jinlin24th.jinlin.common.mq.producer.SmsMessageProducer;
import com.jinlin24th.jinlin.pojo.dto.OrderCreateDTO;
import com.jinlin24th.jinlin.pojo.entity.*;
import com.jinlin24th.jinlin.pojo.vo.OrderVO;
import com.jinlin24th.jinlin.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

   // 订单状态（给状态吗提供名字定义，方便查阅）
    private static final int STATUS_PENDING_PAY = 0;
    private static final int STATUS_PENDING_SHIP = 10;
    private static final int STATUS_PENDING_RECEIVE = 20;
    private static final int STATUS_COMPLETED = 30;
    private static final int STATUS_CANCELLED = 40;

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

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryLogService inventoryLogService;

    @Autowired
    private AppUserService appUserService;

    @Autowired
    private ObjectProvider<OrderTimeoutMessageProducer> orderTimeoutMessageProducerProvider;

    @Autowired
    private ObjectProvider<SmsMessageProducer> smsMessageProducerProvider;

    /** 会员价规则唯一事实来源（只有分销会员享受会员价）。 */
    @Autowired
    private EffectivePriceService effectivePriceService;

    /**
     * 订单超时判定时长（秒）。兜底任务据此计算"创建时间早于 现在-N 秒"的待付款订单。
     * 默认 1800 秒（30 分钟），与 RocketMQ 延迟消息的延迟时长保持一致。
     */
    @Value("${app.order.timeout-cancel-seconds:1800}")
    private long timeoutCancelSeconds;

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        // 买家只查一次：既用于会员价判定，也用于推荐官归属（避免重复查询）
        AppUser buyer = appUserService.getById(userId);
        boolean distributorMember = effectivePriceService.isDistributorMember(buyer);

        // 将 DTO items 转成订单明细（包含价格/商品名/图片等快照信息）
        //
        // 重要：任何一条明细不可下单都必须**明确报错**，绝不能返回 null 让上层过滤掉。
        // 否则购物车 3 件商品有 1 件失效时会生成一张只含 2 件的订单且不报错，
        // 用户收到的货比预期少、金额也按少的算——属于"缩水订单"，事后极难对账。
        List<OrderItem> items = dto.getItems().stream().map(i -> {
            if (i == null || i.getSkuId() == null) {
                throw BizException.badRequest("订单明细缺少商品规格，请重新选择商品");
            }
            if (i.getQuantity() == null || i.getQuantity() <= 0) {
                throw BizException.badRequest("商品数量必须大于 0");
            }
            ProductSku sku = productSkuService.getById(i.getSkuId());
            if (sku == null) {
                throw BizException.of(BizCode.SKU_NOT_FOUND);
            }
            // 可售性校验必须在占用库存之前：否则下架/停售商品也会被扣库存。
            // 前端隐藏入口不等于服务端安全——旧的分享链接或直接 POST skuId 都能绕过。
            if (!Integer.valueOf(1).equals(sku.getStatus())) {
                throw BizException.badRequest("该规格已停售，请重新选择商品");
            }
            Product product = productService.getById(sku.getProductId());
            if (product == null) {
                throw BizException.of(BizCode.PRODUCT_NOT_FOUND);
            }
            if (!Integer.valueOf(1).equals(product.getStatus())) {
                throw BizException.badRequest("商品已下架，无法下单：" + product.getName());
            }

            // 下单时先占用库存，超时未支付取消时再归还库存。
            Inventory occupiedInventory = occupyInventory(orderNo, sku.getId(), i.getQuantity());

            // 价格由 EffectivePriceService 统一决定（唯一事实来源）：
            // 只有分销会员享受会员价，其余人按售价。
            // 此前是无条件取 memberPrice，等于给所有用户打折。
            BigDecimal price = effectivePriceService.effectivePrice(sku, distributorMember);
            //乘法 → .multiply()
            //加法 → .add()
            //减法 → .subtract()
            //除法 → .divide()
            BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(i.getQuantity()));

            OrderItem item = new OrderItem();
            item.setOrderNo(orderNo);
            item.setProductId(product.getId());
            item.setSkuId(sku.getId());
            item.setWarehouseId(occupiedInventory.getWarehouseId());
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
        AppUser level1Recommender = getEnabledDistributor(buyer == null ? null : buyer.getParentUserId());
        AppUser level2Recommender = level1Recommender == null ? null : getEnabledDistributor(level1Recommender.getParentUserId());
        OrderMaster master = new OrderMaster();
        master.setOrderNo(orderNo);
        master.setUserId(userId);
        master.setRecommenderUserId(level1Recommender == null ? null : level1Recommender.getId());
        master.setLevel2RecommenderUserId(level2Recommender == null ? null : level2Recommender.getId());
        master.setTotalAmount(totalAmount);
        master.setPayAmount(totalAmount);
        master.setFreightAmount(BigDecimal.ZERO);
        master.setDiscountAmount(BigDecimal.ZERO);
        master.setPointsUsed(0);
        master.setPointsGained(0);
        master.setStatus(STATUS_PENDING_PAY);
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

        // MQ 开启时发送订单超时取消延迟消息；未开启时 ObjectProvider 返回 null，不影响下单主流程。
        OrderTimeoutMessageProducer orderTimeoutProducer = orderTimeoutMessageProducerProvider.getIfAvailable();
        if (orderTimeoutProducer != null) {
            orderTimeoutProducer.sendTimeoutCancel(master.getId(), master.getOrderNo(), master.getUserId());
        }

        // 下单成功短信异步通知：真实短信平台未接入前，消费者会打印模拟发送日志。
        SmsMessageProducer smsProducer = smsMessageProducerProvider.getIfAvailable();
        if (smsProducer != null && master.getReceiverPhone() != null && !master.getReceiverPhone().isBlank()) {
            smsProducer.sendOrderSuccess(master.getReceiverPhone(), master.getOrderNo());
        }

        return toVO(master, items);
    }

    /**
     * 兜底取消超时未付款订单。
     * <p>
     * 与 MQ 延迟消息互补：MQ 开启时即时取消，MQ 关闭/不可用时由本方法兜底，
     * 避免未付款订单永久占用库存。因为复用 {@link #cancelUnpaidOrder} 的状态 CAS，
     * 两条路径同时触达也不会重复取消或重复归还库存。
     * <p>
     * 注意：本方法**不走事务**——每笔订单的取消由 {@code cancelUnpaidOrder} 各自成事务，
     * 这样单个订单失败（例如库存记录缺失）只回滚它自己，不影响整批。
     */
    @Override
    public int cancelTimeoutPendingOrders(int limit) {
        if (limit <= 0) {
            return 0;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(Math.max(timeoutCancelSeconds, 1L));

        List<OrderMaster> candidates = orderMasterService.lambdaQuery()
            .eq(OrderMaster::getStatus, STATUS_PENDING_PAY)
            .lt(OrderMaster::getCreateTime, cutoff)
            .orderByAsc(OrderMaster::getId)
            .last("LIMIT " + limit)
            .list();
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        int cancelled = 0;
        int failed = 0;
        for (OrderMaster candidate : candidates) {
            String orderNo = candidate.getOrderNo();
            if (orderNo == null || orderNo.isBlank()) {
                continue;
            }
            try {
                if (cancelUnpaidOrder(orderNo, "订单超时未支付，系统自动取消")) {
                    cancelled++;
                }
            } catch (Exception e) {
                failed++;
                // 单笔失败（如库存记录缺失）不能中断整批，下一轮会重试这一单
                log.error("[ORDER-ALERT] 兜底取消订单失败，将在下一轮重试: orderNo={}", orderNo, e);
            }
        }

        if (cancelled > 0 || failed > 0) {
            log.info("订单超时兜底取消完成: 扫描={}, 成功取消={}, 失败={}", candidates.size(), cancelled, failed);
        }
        if (failed > 0) {
            log.error("[ORDER-ALERT] 本轮有 {} 笔订单兜底取消失败（多为库存记录缺失，需人工核对）：{}",
                failed, candidates.size());
        }
        return cancelled;
    }

    /**
     * 取消未支付订单，并恢复库存。
     * <p>
     * 使用 LambdaUpdate 做状态 CAS：只有 status=0 的待付款订单才能改为 40-已取消，
     * 避免支付回调刚把订单改成已支付时，延迟消息又错误关闭订单。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelUnpaidOrder(String orderNo, String reason) {
        if (orderNo == null || orderNo.isBlank()) {
            return false;
        }

        OrderMaster master = orderMasterService.lambdaQuery()
            .eq(OrderMaster::getOrderNo, orderNo)
            .one();
        if (master == null || !Integer.valueOf(STATUS_PENDING_PAY).equals(master.getStatus())) {
            log.info("订单无需超时取消: orderNo={}, status={}", orderNo, master == null ? null : master.getStatus());
            return false;
        }

        boolean updated = orderMasterService.lambdaUpdate()
            .set(OrderMaster::getStatus, STATUS_CANCELLED)
            .set(OrderMaster::getUpdateTime, LocalDateTime.now())
            .eq(OrderMaster::getOrderNo, orderNo)
            .eq(OrderMaster::getStatus, STATUS_PENDING_PAY)
            .update();
        if (!updated) {
            log.info("订单状态已变化，跳过超时取消: orderNo={}", orderNo);
            return false;
        }

        List<OrderItem> items = orderItemService.lambdaQuery()
            .eq(OrderItem::getOrderNo, orderNo)
            .list();
        items.forEach(item -> restoreInventory(orderNo, item, reason));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO adminCancelUnpaid(Long id, Long adminId) {
        OrderMaster master = requireOrder(id);
        if (!Integer.valueOf(STATUS_PENDING_PAY).equals(master.getStatus())) {
            throw BizException.badRequest("只有待付款订单可以取消；已支付订单请走退款售后流程");
        }
        boolean cancelled = cancelUnpaidOrder(master.getOrderNo(), "后台取消待付款订单");
        if (!cancelled) {
            throw BizException.badRequest("订单状态已变化，请刷新后重试");
        }
        if (adminId != null) {
            orderMasterService.lambdaUpdate()
                .set(OrderMaster::getAdminId, adminId)
                .eq(OrderMaster::getId, id)
                .update();
        }
        return get(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO adminShip(Long id, Long adminId) {
        OrderMaster master = requireOrder(id);
        if (!Integer.valueOf(STATUS_PENDING_SHIP).equals(master.getStatus())) {
            throw BizException.badRequest("只有待发货订单可以发货");
        }
        boolean updated = orderMasterService.lambdaUpdate()
            .set(OrderMaster::getStatus, STATUS_PENDING_RECEIVE)
            .set(OrderMaster::getDeliveryTime, LocalDateTime.now())
            .set(adminId != null, OrderMaster::getAdminId, adminId)
            .set(OrderMaster::getUpdateTime, LocalDateTime.now())
            .eq(OrderMaster::getId, id)
            .eq(OrderMaster::getStatus, STATUS_PENDING_SHIP)
            .update();
        if (!updated) {
            throw BizException.badRequest("订单状态已变化，请刷新后重试");
        }
        return get(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO adminComplete(Long id, Long adminId) {
        OrderMaster master = requireOrder(id);
        if (!Integer.valueOf(STATUS_PENDING_RECEIVE).equals(master.getStatus())) {
            throw BizException.badRequest("只有待收货订单可以完成");
        }
        boolean updated = orderMasterService.lambdaUpdate()
            .set(OrderMaster::getStatus, STATUS_COMPLETED)
            .set(OrderMaster::getReceiveTime, LocalDateTime.now())
            .set(adminId != null, OrderMaster::getAdminId, adminId)
            .set(OrderMaster::getUpdateTime, LocalDateTime.now())
            .eq(OrderMaster::getId, id)
            .eq(OrderMaster::getStatus, STATUS_PENDING_RECEIVE)
            .update();
        if (!updated) {
            throw BizException.badRequest("订单状态已变化，请刷新后重试");
        }
        return get(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO confirmReceive(Long userId, Long id) {
        if (userId == null || id == null) {
            throw BizException.badRequest("订单参数错误");
        }
        OrderMaster master = orderMasterService.lambdaQuery()
            .eq(OrderMaster::getId, id)
            .eq(OrderMaster::getUserId, userId)
            .one();
        if (master == null) {
            throw BizException.badRequest("订单不存在");
        }
        if (!Integer.valueOf(STATUS_PENDING_RECEIVE).equals(master.getStatus())) {
            throw BizException.badRequest("只有待收货订单可以确认收货");
        }
        boolean updated = orderMasterService.lambdaUpdate()
            .set(OrderMaster::getStatus, STATUS_COMPLETED)
            .set(OrderMaster::getReceiveTime, LocalDateTime.now())
            .set(OrderMaster::getUpdateTime, LocalDateTime.now())
            .eq(OrderMaster::getId, id)
            .eq(OrderMaster::getUserId, userId)
            .eq(OrderMaster::getStatus, STATUS_PENDING_RECEIVE)
            .update();
        if (!updated) {
            throw BizException.badRequest("订单状态已变化，请刷新后重试");
        }
        return getForUser(userId, id);
    }

    /**
     * 下单时占用库存，并记录出库流水。
     * <p>
     * 按 skuId 选择第一条有库存的库存记录扣减，并把实际扣减的库存记录返回给调用方，
     * 已把扣减仓库快照到订单明细，取消订单时可原仓恢复库存。
     *
     * @return 实际扣减库存的库存记录（含 warehouseId）
     */
    private Inventory occupyInventory(String orderNo, Long skuId, Integer quantity) {
        Inventory inventory = inventoryService.lambdaQuery()
            .eq(Inventory::getSkuId, skuId)
            .ge(Inventory::getStock, quantity)
            .orderByAsc(Inventory::getId)
            .last("LIMIT 1")
            .one();
        if (inventory == null) {
            // 售罄是可预期的业务情况，必须是 BizException（400），
            // 否则会被全局异常处理器兜成"系统繁忙"（500），前端无法提示"库存不足"
            throw BizException.badRequest("商品库存不足，请调整数量或稍后再试");
        }

        int beforeStock = inventory.getStock() == null ? 0 : inventory.getStock();
        int afterStock = beforeStock - quantity;
        boolean updated = inventoryService.lambdaUpdate()
            .set(Inventory::getStock, afterStock)
            .set(Inventory::getUpdateTime, LocalDateTime.now())
            .eq(Inventory::getId, inventory.getId())
            .eq(Inventory::getStock, beforeStock)
            .update();
        if (!updated) {
            // 并发抢购导致的 CAS 失败：同样是可预期情况，提示用户重试即可
            throw BizException.badRequest("商品库存正在被其他订单占用，请重试");
        }

        InventoryLog logEntity = new InventoryLog();
        logEntity.setWarehouseId(inventory.getWarehouseId());
        logEntity.setSkuId(skuId);
        logEntity.setType(2);
        logEntity.setQuantity(-quantity);
        logEntity.setBeforeStock(beforeStock);
        logEntity.setAfterStock(afterStock);
        logEntity.setOrderNo(orderNo);
        logEntity.setRemark("用户下单占用库存");
        logEntity.setOperatorId(0L);
        logEntity.setCreateTime(LocalDateTime.now());
        inventoryLogService.save(logEntity);

        return inventory;
    }

    /**
     * 按订单明细恢复库存，并记录库存流水。
     * <p>
     * 优先按下单时快照的 warehouseId 恢复（哪仓扣的哪仓还）；
     * 历史订单没有仓库快照时，退回按 skuId 查第一条的旧逻辑。
     * <p>
     * 实现要点（修复"静默丢库存"）：
     * <ul>
     *   <li>用单条原子 SQL {@code stock = stock + N} 在数据库端自增，并发安全；</li>
     *   <li><b>不再</b>做"读改写 + CAS + 同事务内重试"——REPEATABLE-READ 下重试会读到
     *       同一份快照、注定失败，而失败后只打日志会导致订单已取消却不还库存；</li>
     *   <li>任何失败（找不到库存记录 / 影响 0 行）都**抛异常**，让 {@code cancelUnpaidOrder}
     *       事务回滚，宁可保持订单待支付也不要丢掉库存；由超时兜底任务稍后重试。</li>
     * </ul>
     */
    private void restoreInventory(String orderNo, OrderItem item, String reason) {
        Integer quantity = item.getQuantity();
        if (quantity == null || quantity <= 0) {
            throw new IllegalStateException(
                "订单取消恢复库存失败：明细数量非法，需人工核对。orderNo=" + orderNo
                    + ", skuId=" + item.getSkuId() + ", quantity=" + quantity);
        }

        Inventory inventory = findInventoryForRestore(item);
        if (inventory == null) {
            throw new IllegalStateException(
                "订单取消恢复库存失败：未找到库存记录，需人工核对。orderNo=" + orderNo
                    + ", skuId=" + item.getSkuId() + ", warehouseId=" + item.getWarehouseId());
        }

        int affected = inventoryService.incrementStock(inventory.getId(), quantity);
        if (affected == 0) {
            throw new IllegalStateException(
                "订单取消恢复库存失败：库存记录未更新，需人工核对。orderNo=" + orderNo
                    + ", inventoryId=" + inventory.getId() + ", quantity=" + quantity);
        }

        // 重新读取以获得准确的归还后库存，仅用于写流水（失败也不影响归还结果）
        Inventory latest = inventoryService.getById(inventory.getId());
        int afterStock = (latest != null && latest.getStock() != null)
            ? latest.getStock()
            : (inventory.getStock() == null ? 0 : inventory.getStock()) + quantity;
        int beforeStock = afterStock - quantity;

        InventoryLog logEntity = new InventoryLog();
        logEntity.setWarehouseId(inventory.getWarehouseId());
        logEntity.setSkuId(item.getSkuId());
        logEntity.setType(1);
        logEntity.setQuantity(quantity);
        logEntity.setBeforeStock(beforeStock);
        logEntity.setAfterStock(afterStock);
        logEntity.setOrderNo(orderNo);
        logEntity.setRemark(reason);
        logEntity.setOperatorId(0L);
        logEntity.setCreateTime(LocalDateTime.now());
        inventoryLogService.save(logEntity);
    }

    /**
     * 查找待恢复库存记录：优先按订单明细快照的 warehouseId 查同仓记录；
     * 历史订单 warehouseId 为空时，退回按 skuId 查第一条的旧逻辑。
     */
    private Inventory findInventoryForRestore(OrderItem item) {
        if (item.getWarehouseId() == null) {
            log.warn("历史订单无仓库快照，按旧逻辑恢复: orderNo={}, skuId={}", item.getOrderNo(), item.getSkuId());
            return inventoryService.lambdaQuery()
                .eq(Inventory::getSkuId, item.getSkuId())
                .orderByAsc(Inventory::getId)
                .last("LIMIT 1")
                .one();
        }
        return inventoryService.lambdaQuery()
            .eq(Inventory::getSkuId, item.getSkuId())
            .eq(Inventory::getWarehouseId, item.getWarehouseId())
            .orderByAsc(Inventory::getId)
            .last("LIMIT 1")
            .one();
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
        Map<Long, AppUser> userMap = loadOrderUsers(entityPage.getRecords());
        voPage.setRecords(entityPage.getRecords().stream()
            .map(m -> toVO(m, null, userMap, false))
            .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public IPage<OrderVO> adminPage(long page, long size, Integer status, Long userId, String userKeyword, String orderNo, String receiverPhone) {
        // 管理端分页：支持按状态/用户/订单号/用户昵称手机号筛选
        List<Long> matchedUserIds = resolveUserIdsByKeyword(userKeyword);
        if (userKeyword != null && !userKeyword.isBlank() && matchedUserIds.isEmpty()) {
            return new Page<>(page, size, 0);
        }
        Page<OrderMaster> p = new Page<>(page, size);
        IPage<OrderMaster> entityPage = orderMasterService.lambdaQuery()
            .eq(status != null, OrderMaster::getStatus, status)
            .eq(userId != null, OrderMaster::getUserId, userId)
            .in(matchedUserIds != null && !matchedUserIds.isEmpty(), OrderMaster::getUserId, matchedUserIds)
            .like(orderNo != null && !orderNo.isBlank(), OrderMaster::getOrderNo, orderNo)
            .like(receiverPhone != null && !receiverPhone.isBlank(), OrderMaster::getReceiverPhone, receiverPhone)
            .orderByDesc(OrderMaster::getId)
            .page(p);
        Page<OrderVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        Map<Long, AppUser> userMap = loadOrderUsers(entityPage.getRecords());
        voPage.setRecords(entityPage.getRecords().stream()
            .map(m -> toVO(m, null, userMap, false))
            .collect(Collectors.toList()));
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
        Map<Long, AppUser> userMap = loadOrderUsers(List.of(master));
        return toVO(master, items, userMap, true);
    }

    private OrderVO toVO(OrderMaster master, List<OrderItem> items, Map<Long, AppUser> userMap, boolean withItems) {
        // 组装 VO：master + items + 买家/推荐官信息
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(master, vo);
        AppUser buyer = userMap.get(master.getUserId());
        AppUser recommender = userMap.get(master.getRecommenderUserId());
        AppUser level2Recommender = userMap.get(master.getLevel2RecommenderUserId());
        vo.setUserNickname(buyer == null ? null : buyer.getNickname());
        vo.setUserPhone(buyer == null ? null : buyer.getPhone());
        vo.setRecommenderNickname(recommender == null ? null : recommender.getNickname());
        vo.setRecommenderPhone(recommender == null ? null : recommender.getPhone());
        vo.setLevel2RecommenderNickname(level2Recommender == null ? null : level2Recommender.getNickname());
        vo.setLevel2RecommenderPhone(level2Recommender == null ? null : level2Recommender.getPhone());
        if (withItems && items != null) {
            vo.setItems(items.stream().map(i -> {
                OrderVO.Item iv = new OrderVO.Item();
                BeanUtils.copyProperties(i, iv);
                return iv;
            }).collect(Collectors.toList()));
        } else {
            vo.setItems(null);
        }
        return vo;
    }

    private List<Long> resolveUserIdsByKeyword(String userKeyword) {
        if (userKeyword == null || userKeyword.isBlank()) {
            return null;
        }
        return appUserService.lambdaQuery()
            .select(AppUser::getId)
            .and(wrapper -> wrapper.like(AppUser::getNickname, userKeyword).or().like(AppUser::getPhone, userKeyword))
            .list()
            .stream()
            .map(AppUser::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private Map<Long, AppUser> loadOrderUsers(List<OrderMaster> orders) {
        Set<Long> userIds = orders.stream()
            .flatMap(order -> Stream.of(order.getUserId(), order.getRecommenderUserId(), order.getLevel2RecommenderUserId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return appUserService.listByIds(new ArrayList<>(userIds)).stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(AppUser::getId, user -> user, (a, b) -> a));
    }

    private OrderMaster requireOrder(Long id) {
        if (id == null) {
            throw BizException.badRequest("订单参数错误");
        }
        OrderMaster master = orderMasterService.getById(id);
        if (master == null) {
            throw BizException.badRequest("订单不存在");
        }
        return master;
    }


    private AppUser getEnabledDistributor(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        return appUserService.lambdaQuery()
            .eq(AppUser::getId, userId)
            .eq(AppUser::getStatus, 1)
            .eq(AppUser::getDeleted, 0)
            .eq(AppUser::getIsDistributor, 1)
            .one();
    }

    private String generateOrderNo() {
        //原订单号生成依赖时间戳 + 随机数，唯一性保障不够工程化；
        //改为雪花 ID 后，订单号更稳定，也更适合作为核心业务编号。
        long snowId = IdUtil.getSnowflakeNextId();
        return "JL" + snowId;
    }
}
