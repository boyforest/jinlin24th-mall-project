package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jinlin24th.jinlin.mapper.DistributionMapper;
import com.jinlin24th.jinlin.pojo.entity.Distribution;
import com.jinlin24th.jinlin.service.AppUserService;
import com.jinlin24th.jinlin.service.DistributionConfigService;
import com.jinlin24th.jinlin.service.OrderMasterService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款时作废佣金的正确性测试。
 * <p>
 * 修复的缺陷：原实现用 {@code .ne(status, 3)}（"只要不是已退回就改成已退回"）
 * 把所有状态的佣金一并改为 3，**包括已经结算并打款（status=2）的记录**。
 * 后果是：财务的付款凭证被抹掉——记录看起来"从未结算过"，且没有任何冲正或告警，
 * 已付出的钱无法追溯、无法追回。
 * <p>
 * 正确语义：
 * <ul>
 *   <li>未结算(0)/可结算(1) → 3（钱还没打出去，可以直接作废）；</li>
 *   <li>已结算(2) → **不得覆盖状态**（钱已付出），改为写备注 + 打告警交人工追回。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistributionRefundVoidTest {

    private static final String ORDER_NO = "JL20260921000077";

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
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new Configuration(), ""), Distribution.class);
        service = new DistributionServiceImpl(appUserService, distributionConfigService, orderMasterService);
        ReflectionTestUtils.setField(service, "baseMapper", distributionMapper);
        when(distributionMapper.update(isNull(), any())).thenReturn(1);
    }

    private Distribution distributionWithStatus(Long id, int status) {
        Distribution d = new Distribution();
        d.setId(id);
        d.setOrderNo(ORDER_NO);
        d.setStatus(status);
        d.setLevel1Amount(new BigDecimal("9.90"));
        d.setLevel2Amount(new BigDecimal("4.95"));
        d.setCreateTime(LocalDateTime.now());
        return d;
    }

    /** 捕获所有 update 包装器，便于逐个检查"改了哪些列、匹配哪些行"。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<LambdaUpdateWrapper<Distribution>> capturedUpdates() {
        ArgumentCaptor<Wrapper<Distribution>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(distributionMapper, org.mockito.Mockito.atLeastOnce()).update(isNull(), captor.capture());
        return captor.getAllValues().stream()
            .filter(LambdaUpdateWrapper.class::isInstance)
            .map(w -> (LambdaUpdateWrapper<Distribution>) w)
            .collect(Collectors.toList());
    }

    @Test
    @DisplayName("作废必须用 status IN (0,1) 正向枚举，不能用 status <> 3 反向排除")
    void voidingMustEnumerateVoidableStatuses() {
        when(distributionMapper.selectList(any())).thenReturn(List.of());

        service.markRefundedByOrderNo(ORDER_NO);

        List<LambdaUpdateWrapper<Distribution>> updates = capturedUpdates();
        assertFalse(updates.isEmpty(), "应发出作废更新");

        String segment = updates.get(0).getSqlSegment();
        assertTrue(segment.contains("IN") || segment.contains("in"),
            "必须正向枚举可作废状态，实际条件：" + segment);
        assertFalse(segment.contains("<>") || segment.contains("!="),
            "不得使用 '不等于已退回' 这种反向条件，它会把已结算记录一起改掉：" + segment);
    }

    @Test
    @DisplayName("已结算(2)的佣金不得被改成已退回，否则抹掉财务付款凭证")
    void settledCommissionStatusMustNotBeOverwritten() {
        // 存在一条"已结算打款"的佣金
        when(distributionMapper.selectList(any()))
            .thenReturn(List.of(distributionWithStatus(9L, 2)));

        service.markRefundedByOrderNo(ORDER_NO);

        List<LambdaUpdateWrapper<Distribution>> updates = capturedUpdates();

        // 只有"作废未结算记录"那一次更新允许改 status；针对已结算记录的更新不得包含 status
        List<String> statusSettingUpdates = updates.stream()
            .map(LambdaUpdateWrapper::getSqlSet)
            .filter(sqlSet -> sqlSet != null && sqlSet.contains("status"))
            .collect(Collectors.toList());

        assertTrue(statusSettingUpdates.size() == 1,
            "只允许对未结算记录改状态；已结算记录不得改 status，实际改 status 的更新数="
                + statusSettingUpdates.size() + "，内容=" + statusSettingUpdates);
    }

    @Test
    @DisplayName("存在已结算佣金时必须去查它，以便写备注并告警（不能视而不见）")
    void settledCommissionMustBeQueriedForManualFollowUp() {
        when(distributionMapper.selectList(any()))
            .thenReturn(List.of(distributionWithStatus(9L, 2)));

        service.markRefundedByOrderNo(ORDER_NO);

        // 必须发生一次针对该订单的查询，用于找出已结算记录
        verify(distributionMapper, org.mockito.Mockito.atLeastOnce()).selectList(any());
    }

    @Test
    @DisplayName("订单号为空时不做任何更新")
    void blankOrderNoMustBeIgnored() {
        service.markRefundedByOrderNo("   ");

        verify(distributionMapper, org.mockito.Mockito.never()).update(isNull(), any());
    }
}
