package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import com.jinlin24th.jinlin.pojo.entity.OrderItem;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.service.AppUserService;
import com.jinlin24th.jinlin.service.InventoryLogService;
import com.jinlin24th.jinlin.service.InventoryService;
import com.jinlin24th.jinlin.service.OrderItemService;
import com.jinlin24th.jinlin.service.OrderMasterService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单取消时恢复库存的正确性测试。
 * <p>
 * 修复的缺陷：原实现用「读当前库存 → 计算新值 → CAS 更新」的方式恢复库存，
 * 且 CAS 失败后的"重试"是在**同一个事务内**重新查询——MySQL REPEATABLE-READ
 * 下会读到同一份快照，重试必然再次失败；最终只打一条 error 日志然后正常返回，
 * 订单被取消、库存却永久丢失，没有任何异常或补偿。
 * <p>
 * 正确做法：单条原子语句 {@code stock = stock + N}，失败则抛异常让取消事务回滚。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderCancelRestoreInventoryTest {

    private static final String ORDER_NO = "JL20260921000042";

    @Mock
    private OrderMasterService orderMasterService;
    @Mock
    private OrderItemService orderItemService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private InventoryLogService inventoryLogService;
    @Mock
    private AppUserService appUserService;

    @Mock
    private LambdaQueryChainWrapper<OrderMaster> orderMasterQuery;
    @Mock
    private LambdaUpdateChainWrapper<OrderMaster> orderMasterUpdate;
    @Mock
    private LambdaQueryChainWrapper<OrderItem> orderItemQuery;
    @Mock
    private LambdaQueryChainWrapper<Inventory> inventoryQuery;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        initTableInfo(Inventory.class);
        initTableInfo(OrderMaster.class);
        initTableInfo(OrderItem.class);

        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMasterService", orderMasterService);
        ReflectionTestUtils.setField(service, "orderItemService", orderItemService);
        ReflectionTestUtils.setField(service, "inventoryService", inventoryService);
        ReflectionTestUtils.setField(service, "inventoryLogService", inventoryLogService);
        ReflectionTestUtils.setField(service, "appUserService", appUserService);
        ReflectionTestUtils.setField(service, "effectivePriceService", new com.jinlin24th.jinlin.service.EffectivePriceService());

        // 订单：待付款
        OrderMaster pending = new OrderMaster();
        pending.setId(1L);
        pending.setOrderNo(ORDER_NO);
        pending.setStatus(0);
        pending.setCreateTime(LocalDateTime.now().minusMinutes(40));
        when(orderMasterService.lambdaQuery()).thenReturn(orderMasterQuery);
        when(orderMasterQuery.eq(any(), any())).thenReturn(orderMasterQuery);
        when(orderMasterQuery.one()).thenReturn(pending);

        // 状态 CAS 成功
        when(orderMasterService.lambdaUpdate()).thenReturn(orderMasterUpdate);
        when(orderMasterUpdate.set(any(), any())).thenReturn(orderMasterUpdate);
        when(orderMasterUpdate.eq(any(), any())).thenReturn(orderMasterUpdate);
        when(orderMasterUpdate.update()).thenReturn(true);

        // 一条订单明细，占用 3 件
        OrderItem item = new OrderItem();
        item.setOrderNo(ORDER_NO);
        item.setSkuId(99L);
        item.setWarehouseId(7L);
        item.setQuantity(3);
        when(orderItemService.lambdaQuery()).thenReturn(orderItemQuery);
        when(orderItemQuery.eq(any(), any())).thenReturn(orderItemQuery);
        when(orderItemQuery.list()).thenReturn(List.of(item));

        // 库存记录
        Inventory inventory = new Inventory();
        inventory.setId(5L);
        inventory.setWarehouseId(7L);
        inventory.setSkuId(99L);
        inventory.setStock(10);
        when(inventoryService.lambdaQuery()).thenReturn(inventoryQuery);
        when(inventoryQuery.eq(any(), any())).thenReturn(inventoryQuery);
        when(inventoryQuery.orderByAsc(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class))).thenReturn(inventoryQuery);
        when(inventoryQuery.last(anyString())).thenReturn(inventoryQuery);
        when(inventoryQuery.one()).thenReturn(inventory);
    }

    private static void initTableInfo(Class<?> entityClass) {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
    }

    @Test
    @DisplayName("恢复库存必须走原子自增 incrementStock，不能读改写 + CAS")
    void restoreShouldUseAtomicIncrement() {
        when(inventoryService.incrementStock(any(), anyInt())).thenReturn(1);

        service.cancelUnpaidOrder(ORDER_NO, "订单超时未支付");

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> qtyCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(inventoryService).incrementStock(idCaptor.capture(), qtyCaptor.capture());

        assertTrue(Long.valueOf(5L).equals(idCaptor.getValue()), "应对订单占用的那条库存记录归还");
        assertTrue(Integer.valueOf(3).equals(qtyCaptor.getValue()), "归还数量应为订单占用量 3");
        // 关键：绝不能再走读改写（CAS）路径
        verify(inventoryService, never()).lambdaUpdate();
    }

    @Test
    @DisplayName("原子恢复失败（影响 0 行）必须抛异常，让取消事务回滚而不是静默丢库存")
    void restoreFailureMustThrowSoCancelRollsBack() {
        when(inventoryService.incrementStock(any(), anyInt())).thenReturn(0); // 影响 0 行

        assertThrows(RuntimeException.class,
            () -> service.cancelUnpaidOrder(ORDER_NO, "订单超时未支付"),
            "恢复库存失败必须抛异常让事务回滚，否则订单已取消但库存永久丢失");
    }

    @Test
    @DisplayName("库存记录缺失时也必须抛异常，不能取消订单却不还库存")
    void missingInventoryMustThrow() {
        when(inventoryQuery.one()).thenReturn(null);

        assertThrows(RuntimeException.class,
            () -> service.cancelUnpaidOrder(ORDER_NO, "订单超时未支付"));
    }

    @Test
    @DisplayName("恢复成功时必须写库存流水，且数量为订单占用量")
    void restoreShouldWriteInventoryLog() {
        when(inventoryService.incrementStock(any(), anyInt())).thenReturn(1);

        service.cancelUnpaidOrder(ORDER_NO, "订单超时未支付");

        ArgumentCaptor<com.jinlin24th.jinlin.pojo.entity.InventoryLog> logCaptor =
            ArgumentCaptor.forClass(com.jinlin24th.jinlin.pojo.entity.InventoryLog.class);
        verify(inventoryLogService).save(logCaptor.capture());
        var saved = logCaptor.getValue();

        assertTrue(Integer.valueOf(3).equals(saved.getQuantity()),
            "流水数量应为订单占用量 3，实际：" + saved.getQuantity());
        assertTrue(ORDER_NO.equals(saved.getOrderNo()));
        assertTrue(Integer.valueOf(1).equals(saved.getType()), "1 = 入库");
    }

    @Test
    @DisplayName("已支付订单不允许被超时取消（不碰库存）")
    void paidOrderMustNotBeCancelled() {
        OrderMaster paid = new OrderMaster();
        paid.setOrderNo(ORDER_NO);
        paid.setStatus(10);
        when(orderMasterQuery.one()).thenReturn(paid);

        boolean result = service.cancelUnpaidOrder(ORDER_NO, "订单超时未支付");

        assertTrue(!result, "已支付订单不应被取消");
        verify(inventoryService, never()).incrementStock(any(), anyInt());
    }
}
