package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.mapper.DistributionMapper;
import com.jinlin24th.jinlin.pojo.entity.AppUser;
import com.jinlin24th.jinlin.pojo.entity.Distribution;
import com.jinlin24th.jinlin.pojo.entity.DistributionConfig;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.service.AppUserService;
import com.jinlin24th.jinlin.service.DistributionConfigService;
import com.jinlin24th.jinlin.service.OrderMasterService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 分销佣金“到期可结算”链路单元测试。
 */
@ExtendWith(MockitoExtension.class)
class DistributionServiceImplTest {

    @Mock
    private AppUserService appUserService;
    @Mock
    private DistributionConfigService distributionConfigService;
    @Mock
    private OrderMasterService orderMasterService;
    @Mock
    private DistributionMapper distributionMapper;

    private DistributionServiceImpl service;

    @BeforeEach
    void setUp() {
        // 单元测试无 MyBatis 会话，需手动初始化实体 lambda 缓存，否则 Wrappers.lambda 无法解析列名
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), ""), Distribution.class);
        service = new DistributionServiceImpl(appUserService, distributionConfigService, orderMasterService);
        ReflectionTestUtils.setField(service, "baseMapper", distributionMapper);
    }

    private DistributionConfig configWithSettleDays(int settleDays) {
        DistributionConfig config = new DistributionConfig();
        config.setSettleDays(settleDays);
        return config;
    }

    @Test
    void loadRelatedUsersShouldTolerateNullRecommenderIds() throws Exception {
        Distribution record = new Distribution();
        record.setBuyerUserId(1L);
        record.setLevel1UserId(null);
        record.setLevel2UserId(null);

        AppUser buyer = new AppUser();
        buyer.setId(1L);
        when(appUserService.listByIds(any())).thenReturn(List.of(buyer));

        java.lang.reflect.Method method = DistributionServiceImpl.class
                .getDeclaredMethod("loadRelatedUsers", List.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, AppUser> result = (Map<Long, AppUser>) method.invoke(service, List.of(record));

        assertEquals(1, result.size());
        assertTrue(result.containsKey(1L));
    }

    private Distribution pendingDistribution(Long id, Long orderId) {
        Distribution distribution = new Distribution();
        distribution.setId(id);
        distribution.setOrderId(orderId);
        distribution.setStatus(0);
        return distribution;
    }

    private OrderMaster completedOrder(Long id, LocalDateTime receiveTime) {
        OrderMaster order = new OrderMaster();
        order.setId(id);
        order.setStatus(30);
        order.setReceiveTime(receiveTime);
        return order;
    }

    /**
     * 已到结算天数的佣金记录应变为 1-可结算，并返回实际变更条数。
     */
    @Test
    void markEligibleShouldFlipEligibleRecordsToSettleable() {
        LocalDateTime now = LocalDateTime.now();
        when(distributionConfigService.getCurrentConfig()).thenReturn(configWithSettleDays(7));
        when(distributionMapper.selectList(any())).thenReturn(List.of(
            pendingDistribution(1L, 101L),
            pendingDistribution(2L, 102L)
        ));
        when(orderMasterService.listByIds(any())).thenReturn(List.of(
            completedOrder(101L, now.minusDays(8)),
            completedOrder(102L, now.minusDays(10))
        ));
        when(distributionMapper.update(isNull(), any())).thenReturn(2);

        int updated = service.markEligibleDistributionsSettleable();

        assertEquals(2, updated);
        verify(distributionMapper).update(isNull(), any());
    }

    /**
     * 订单未完成（status != 30）的佣金记录应保持 0-待结算。
     */
    @Test
    void markEligibleShouldKeepPendingWhenOrderNotCompleted() {
        LocalDateTime now = LocalDateTime.now();
        when(distributionConfigService.getCurrentConfig()).thenReturn(configWithSettleDays(7));
        when(distributionMapper.selectList(any())).thenReturn(List.of(pendingDistribution(1L, 101L)));
        OrderMaster order = completedOrder(101L, now.minusDays(8));
        order.setStatus(10);
        when(orderMasterService.listByIds(any())).thenReturn(List.of(order));

        int updated = service.markEligibleDistributionsSettleable();

        assertEquals(0, updated);
        verify(distributionMapper, never()).update(isNull(), any());
    }

    /**
     * 收货时间为空的佣金记录应保持 0-待结算。
     */
    @Test
    void markEligibleShouldKeepPendingWhenReceiveTimeMissing() {
        when(distributionConfigService.getCurrentConfig()).thenReturn(configWithSettleDays(7));
        when(distributionMapper.selectList(any())).thenReturn(List.of(pendingDistribution(1L, 101L)));
        when(orderMasterService.listByIds(any())).thenReturn(List.of(completedOrder(101L, null)));

        int updated = service.markEligibleDistributionsSettleable();

        assertEquals(0, updated);
        verify(distributionMapper, never()).update(isNull(), any());
    }

    /**
     * 订单不存在的佣金记录应保持 0-待结算。
     */
    @Test
    void markEligibleShouldKeepPendingWhenOrderMissing() {
        when(distributionConfigService.getCurrentConfig()).thenReturn(configWithSettleDays(7));
        when(distributionMapper.selectList(any())).thenReturn(List.of(pendingDistribution(1L, 999L)));
        when(orderMasterService.listByIds(any())).thenReturn(List.of());

        int updated = service.markEligibleDistributionsSettleable();

        assertEquals(0, updated);
        verify(distributionMapper, never()).update(isNull(), any());
    }

    /**
     * 未到结算天数的佣金记录应保持 0-待结算。
     */
    @Test
    void markEligibleShouldKeepPendingWhenNotDue() {
        LocalDateTime now = LocalDateTime.now();
        when(distributionConfigService.getCurrentConfig()).thenReturn(configWithSettleDays(7));
        when(distributionMapper.selectList(any())).thenReturn(List.of(pendingDistribution(1L, 101L)));
        when(orderMasterService.listByIds(any())).thenReturn(List.of(completedOrder(101L, now.minusDays(3))));

        int updated = service.markEligibleDistributionsSettleable();

        assertEquals(0, updated);
        verify(distributionMapper, never()).update(isNull(), any());
    }

    /**
     * settleDays = 0 时，订单完成当天即可变为可结算。
     */
    @Test
    void markEligibleShouldSettleSameDayWhenSettleDaysIsZero() {
        LocalDateTime now = LocalDateTime.now();
        when(distributionConfigService.getCurrentConfig()).thenReturn(configWithSettleDays(0));
        when(distributionMapper.selectList(any())).thenReturn(List.of(pendingDistribution(1L, 101L)));
        when(orderMasterService.listByIds(any())).thenReturn(List.of(completedOrder(101L, now.minusHours(1))));
        when(distributionMapper.update(isNull(), any())).thenReturn(1);

        int updated = service.markEligibleDistributionsSettleable();

        assertEquals(1, updated);
    }

    /**
     * 配置为空时使用默认 7 天。
     */
    @Test
    void markEligibleShouldUseDefaultSettleDaysWhenConfigMissing() {
        LocalDateTime now = LocalDateTime.now();
        when(distributionConfigService.getCurrentConfig()).thenReturn(null);
        when(distributionMapper.selectList(any())).thenReturn(List.of(pendingDistribution(1L, 101L)));
        when(orderMasterService.listByIds(any())).thenReturn(List.of(completedOrder(101L, now.minusDays(8))));
        when(distributionMapper.update(isNull(), any())).thenReturn(1);

        int updated = service.markEligibleDistributionsSettleable();

        assertEquals(1, updated);
    }

    /**
     * 无待结算记录时直接返回 0，且不查询订单。
     */
    @Test
    void markEligibleShouldReturnZeroWhenNoPendingRecords() {
        when(distributionConfigService.getCurrentConfig()).thenReturn(configWithSettleDays(7));
        when(distributionMapper.selectList(any())).thenReturn(List.of());

        int updated = service.markEligibleDistributionsSettleable();

        assertEquals(0, updated);
        verifyNoInteractions(orderMasterService);
        verify(distributionMapper, never()).update(isNull(), any());
    }

    /**
     * 批量更新必须携带 status = 0 条件（多实例幂等）。
     */
    @Test
    void markEligibleBatchUpdateShouldCarryStatusZeroCondition() {
        LocalDateTime now = LocalDateTime.now();
        when(distributionConfigService.getCurrentConfig()).thenReturn(configWithSettleDays(7));
        when(distributionMapper.selectList(any())).thenReturn(List.of(pendingDistribution(1L, 101L)));
        when(orderMasterService.listByIds(any())).thenReturn(List.of(completedOrder(101L, now.minusDays(8))));
        when(distributionMapper.update(isNull(), any())).thenReturn(1);

        service.markEligibleDistributionsSettleable();

        ArgumentCaptor<LambdaUpdateWrapper<Distribution>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(distributionMapper).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("status"));
    }

    /**
     * 结算不存在的佣金记录应返回 null。
     */
    @Test
    void settleShouldReturnNullWhenRecordMissing() {
        when(distributionMapper.selectById(1L)).thenReturn(null);

        assertNull(service.settle(1L));
    }

    /**
     * 重复结算已结算记录应报错。
     */
    @Test
    void settleShouldRejectAlreadySettledRecord() {
        Distribution distribution = pendingDistribution(1L, 101L);
        distribution.setStatus(2);
        when(distributionMapper.selectById(1L)).thenReturn(distribution);

        BizException exception = assertThrows(BizException.class, () -> service.settle(1L));

        assertEquals("该佣金已结算，请勿重复操作", exception.getMessage());
    }

    /**
     * 已退回记录不可结算。
     */
    @Test
    void settleShouldRejectRefundedRecord() {
        Distribution distribution = pendingDistribution(1L, 101L);
        distribution.setStatus(3);
        when(distributionMapper.selectById(1L)).thenReturn(distribution);

        BizException exception = assertThrows(BizException.class, () -> service.settle(1L));

        assertEquals("该佣金已退回，不可结算", exception.getMessage());
    }

    /**
     * 待结算（0）状态不可直接结算，必须先经定时任务变为 1。
     */
    @Test
    void settleShouldRejectPendingStatus() {
        Distribution distribution = pendingDistribution(1L, 101L);
        distribution.setStatus(0);
        when(distributionMapper.selectById(1L)).thenReturn(distribution);

        BizException exception = assertThrows(BizException.class, () -> service.settle(1L));

        assertEquals("当前状态不可结算", exception.getMessage());
    }

    /**
     * CAS 更新失败（并发结算/状态已被他人变更）应报错。
     */
    @Test
    void settleShouldFailWhenCasUpdateConflicts() {
        Distribution distribution = pendingDistribution(1L, 101L);
        distribution.setStatus(1);
        when(distributionMapper.selectById(1L)).thenReturn(distribution);
        when(distributionMapper.update(isNull(), any())).thenReturn(0);

        BizException exception = assertThrows(BizException.class, () -> service.settle(1L));

        assertEquals("佣金状态已变化，请刷新后重试", exception.getMessage());
    }

    /**
     * 结算成功应返回已结算记录。
     */
    @Test
    void settleShouldReturnSettledRecordOnSuccess() {
        Distribution distribution = pendingDistribution(1L, 101L);
        distribution.setStatus(1);
        when(distributionMapper.selectById(1L)).thenReturn(distribution);
        when(distributionMapper.update(isNull(), any())).thenReturn(1);
        Distribution settled = pendingDistribution(1L, 101L);
        settled.setStatus(2);
        settled.setSettleTime(LocalDateTime.now());
        when(distributionMapper.selectById(1L)).thenReturn(distribution, settled);

        Distribution result = service.settle(1L);

        assertNotNull(result);
        assertEquals(2, result.getStatus());
        assertNotNull(result.getSettleTime());
    }
}
