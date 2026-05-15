package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.mapper.DistributionMapper;
import com.jinlin24th.jinlin.pojo.entity.AppUser;
import com.jinlin24th.jinlin.pojo.entity.Distribution;
import com.jinlin24th.jinlin.pojo.entity.DistributionConfig;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.pojo.vo.DistributionVO;
import com.jinlin24th.jinlin.service.AppUserService;
import com.jinlin24th.jinlin.service.DistributionConfigService;
import com.jinlin24th.jinlin.service.DistributionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DistributionServiceImpl extends ServiceImpl<DistributionMapper, Distribution>
        implements DistributionService {

    private final AppUserService appUserService;
    private final DistributionConfigService distributionConfigService;

    public DistributionServiceImpl(AppUserService appUserService, DistributionConfigService distributionConfigService) {
        this.appUserService = appUserService;
        this.distributionConfigService = distributionConfigService;
    }

    @Override
    public IPage<DistributionVO> adminPage(long page, long size, Integer status) {
        // 管理端分页：按状态筛选分销记录
        Page<Distribution> p = new Page<>(page, size);
        IPage<Distribution> entityPage = lambdaQuery()
            .eq(status != null, Distribution::getStatus, status)
            .orderByDesc(Distribution::getId)
            .page(p);
        Page<DistributionVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(e -> {
            DistributionVO vo = new DistributionVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Distribution getRequired(Long id) {
        // 简化实现：直接按 id 查询；后续可改成“不存在则抛业务异常”
        return getById(id);
    }

    @Override
    public Distribution settle(Long id) {
        // 结算：只有 status=1（待结算/可结算）才允许变更为 2（已结算）
        Distribution distribution = getById(id);
        if (distribution == null) {
            return null;
        }
        if (distribution.getStatus() == null || distribution.getStatus() != 1) {
            return null;
        }
        distribution.setStatus(2);
        distribution.setSettleTime(LocalDateTime.now());
        updateById(distribution);
        return distribution;
    }

    /**
     * 支付成功后生成佣金记录。
     * <p>
     * 幂等策略：distribution 表已有 uk_order_id，同一订单只允许一条佣金记录；
     * 代码层先查一次，数据库唯一键兜底，避免微信支付重复回调造成重复佣金。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createForPaidOrder(OrderMaster orderMaster) {
        if (orderMaster == null || orderMaster.getId() == null || orderMaster.getUserId() == null) {
            return;
        }
        boolean exists = lambdaQuery()
            .eq(Distribution::getOrderId, orderMaster.getId())
            .exists();
        if (exists) {
            log.info("分销佣金记录已存在，跳过重复生成: orderNo={}", orderMaster.getOrderNo());
            return;
        }

        DistributionConfig config = distributionConfigService.getCurrentConfig();
        if (config == null || !Integer.valueOf(1).equals(config.getStatus())) {
            log.info("分销配置未启用，跳过佣金生成: orderNo={}", orderMaster.getOrderNo());
            return;
        }

        AppUser buyer = appUserService.getById(orderMaster.getUserId());
        if (buyer == null && orderMaster.getRecommenderUserId() == null) {
            return;
        }

        Long level1UserId = orderMaster.getRecommenderUserId() != null
            ? orderMaster.getRecommenderUserId()
            : buyer.getParentUserId();
        AppUser level1 = getEnabledDistributor(level1UserId);
        if (level1 == null) {
            return;
        }
        Long level2UserId = orderMaster.getLevel2RecommenderUserId() != null
            ? orderMaster.getLevel2RecommenderUserId()
            : level1.getParentUserId();
        AppUser level2 = getEnabledDistributor(level2UserId);

        BigDecimal buyerAmount = Optional.ofNullable(orderMaster.getPayAmount()).orElse(orderMaster.getTotalAmount());
        BigDecimal level1Amount = calculateCommission(buyerAmount, config.getLevel1Rate());
        BigDecimal level2Amount = level2 == null ? BigDecimal.ZERO : calculateCommission(buyerAmount, config.getLevel2Rate());

        Distribution distribution = new Distribution();
        distribution.setOrderId(orderMaster.getId());
        distribution.setOrderNo(orderMaster.getOrderNo());
        distribution.setBuyerUserId(orderMaster.getUserId());
        distribution.setBuyerAmount(buyerAmount);
        distribution.setLevel1UserId(level1.getId());
        distribution.setLevel1Rate(Optional.ofNullable(config.getLevel1Rate()).orElse(0));
        distribution.setLevel1Amount(level1Amount);
        distribution.setLevel2UserId(level2 == null ? null : level2.getId());
        distribution.setLevel2Rate(level2 == null ? 0 : Optional.ofNullable(config.getLevel2Rate()).orElse(0));
        distribution.setLevel2Amount(level2Amount);
        distribution.setStatus(0);
        distribution.setRemark("支付成功自动生成佣金记录");
        distribution.setCreateTime(LocalDateTime.now());
        distribution.setUpdateTime(LocalDateTime.now());
        save(distribution);
    }

    /**
     * 退款成功后作废佣金记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRefundedByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return;
        }
        lambdaUpdate()
            .set(Distribution::getStatus, 3)
            .set(Distribution::getRemark, "订单退款成功，佣金已退回/作废")
            .set(Distribution::getUpdateTime, LocalDateTime.now())
            .eq(Distribution::getOrderNo, orderNo)
            .ne(Distribution::getStatus, 3)
            .update();
    }

    /**
     * 导出佣金记录 CSV，补充买家/上级昵称手机号，方便财务线下打款。
     */
    @Override
    public void exportCsv(HttpServletResponse response, Integer status) throws IOException {
        List<Distribution> records = lambdaQuery()
            .eq(status != null, Distribution::getStatus, status)
            .orderByDesc(Distribution::getId)
            .list();
        Map<Long, AppUser> userMap = loadRelatedUsers(records);

        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=distribution.csv");

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("订单号,买家ID,买家昵称,买家手机号,订单金额,一级分销员ID,一级昵称,一级手机号,一级比例,一级佣金,二级分销员ID,二级昵称,二级手机号,二级比例,二级佣金,状态,创建时间\n");
        records.forEach(record -> appendCsvRow(csv, record, userMap));
        response.getWriter().write(csv.toString());
    }

    /**
     * 查询具备资格的分销员。
     */
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

    /**
     * 按百分比计算佣金，保留两位小数。
     */
    private BigDecimal calculateCommission(BigDecimal amount, Integer rate) {
        if (amount == null || rate == null || rate <= 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(BigDecimal.valueOf(rate))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * 批量加载导出所需用户信息，避免 N+1 查询。
     */
    private Map<Long, AppUser> loadRelatedUsers(List<Distribution> records) {
        Set<Long> userIds = records.stream()
            .flatMap(record -> List.of(record.getBuyerUserId(), record.getLevel1UserId(), record.getLevel2UserId()).stream())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return appUserService.listByIds(userIds).stream()
            .collect(Collectors.toMap(AppUser::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * 追加一行 CSV，并对逗号、换行、双引号做转义。
     */
    private void appendCsvRow(StringBuilder csv, Distribution record, Map<Long, AppUser> userMap) {
        AppUser buyer = userMap.get(record.getBuyerUserId());
        AppUser level1 = userMap.get(record.getLevel1UserId());
        AppUser level2 = userMap.get(record.getLevel2UserId());
        List<String> values = List.of(
            value(record.getOrderNo()),
            value(record.getBuyerUserId()),
            value(buyer == null ? null : buyer.getNickname()),
            value(buyer == null ? null : buyer.getPhone()),
            value(record.getBuyerAmount()),
            value(record.getLevel1UserId()),
            value(level1 == null ? null : level1.getNickname()),
            value(level1 == null ? null : level1.getPhone()),
            value(record.getLevel1Rate()),
            value(record.getLevel1Amount()),
            value(record.getLevel2UserId()),
            value(level2 == null ? null : level2.getNickname()),
            value(level2 == null ? null : level2.getPhone()),
            value(record.getLevel2Rate()),
            value(record.getLevel2Amount()),
            value(record.getStatus()),
            value(record.getCreateTime())
        );
        csv.append(values.stream().map(this::escapeCsv).collect(Collectors.joining(","))).append('\n');
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String escapeCsv(String value) {
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")
            ? "\"" + escaped + "\""
            : escaped;
    }
}
