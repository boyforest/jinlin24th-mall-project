package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.mapper.DistributionMapper;
import com.jinlin24th.jinlin.pojo.entity.AppUser;
import com.jinlin24th.jinlin.pojo.entity.Distribution;
import com.jinlin24th.jinlin.pojo.entity.DistributionConfig;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.pojo.vo.DistributionVO;
import com.jinlin24th.jinlin.service.AppUserService;
import com.jinlin24th.jinlin.service.DistributionConfigService;
import com.jinlin24th.jinlin.service.DistributionService;
import com.jinlin24th.jinlin.service.OrderMasterService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class DistributionServiceImpl extends ServiceImpl<DistributionMapper, Distribution>
        implements DistributionService {

    private final AppUserService appUserService;
    private final DistributionConfigService distributionConfigService;
    private final OrderMasterService orderMasterService;

    /**
     * 单次 CSV 导出的最大行数。
     * <p>
     * 原实现用 {@code list()} 一次加载全部匹配记录，财务点一次"导出"就可能把整张表
     * 拉进内存。这里加上限；达到上限时会在 CSV 末尾写一行显眼提示，
     * 避免财务按不完整名单打款。
     */
    @Value("${app.distribution.max-export-rows:10000}")
    private long maxExportRows;

    public DistributionServiceImpl(AppUserService appUserService,
                                   DistributionConfigService distributionConfigService,
                                   OrderMasterService orderMasterService) {
        this.appUserService = appUserService;
        this.distributionConfigService = distributionConfigService;
        this.orderMasterService = orderMasterService;
    }

    @Override
    public IPage<DistributionVO> adminPage(long page, long size, Integer status, String orderNo, String keyword) {
        // 管理端分页：按状态、订单号、买家/分销员昵称手机号筛选
        List<Long> matchedUserIds = resolveUserIdsByKeyword(keyword);
        if (keyword != null && !keyword.isBlank() && matchedUserIds.isEmpty()) {
            return new Page<>(page, size, 0);
        }
        Page<Distribution> p = new Page<>(page, size);
        IPage<Distribution> entityPage = lambdaQuery()
            .eq(status != null, Distribution::getStatus, status)
            .like(orderNo != null && !orderNo.isBlank(), Distribution::getOrderNo, orderNo)
            .and(matchedUserIds != null && !matchedUserIds.isEmpty(), wrapper -> wrapper
                .in(Distribution::getBuyerUserId, matchedUserIds)
                .or()
                .in(Distribution::getLevel1UserId, matchedUserIds)
                .or()
                .in(Distribution::getLevel2UserId, matchedUserIds))
            .orderByDesc(Distribution::getId)
            .page(p);
        Map<Long, AppUser> userMap = loadRelatedUsers(entityPage.getRecords());
        Page<DistributionVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream()
            .map(e -> toVO(e, userMap))
            .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public DistributionVO getDetail(Long id) {
        Distribution distribution = getById(id);
        if (distribution == null) {
            return null;
        }
        return toVO(distribution, loadRelatedUsers(List.of(distribution)));
    }


    @Override
    public Distribution settle(Long id) {
        // 结算：只有 status=1（可结算）才允许变更为 2（已结算），
        // 用带状态条件的 CAS 更新防止两个管理员并发重复结算。
        Distribution distribution = getById(id);
        if (distribution == null) {
            return null;
        }
        if (Integer.valueOf(2).equals(distribution.getStatus())) {
            throw BizException.badRequest("该佣金已结算，请勿重复操作");
        }
        if (Integer.valueOf(3).equals(distribution.getStatus())) {
            throw BizException.badRequest("该佣金已退回，不可结算");
        }
        if (!Integer.valueOf(1).equals(distribution.getStatus())) {
            throw BizException.badRequest("当前状态不可结算");
        }
        int updated = getBaseMapper().update(null, Wrappers.<Distribution>lambdaUpdate()
            .set(Distribution::getStatus, 2)
            .set(Distribution::getSettleTime, LocalDateTime.now())
            .set(Distribution::getUpdateTime, LocalDateTime.now())
            .eq(Distribution::getId, id)
            .eq(Distribution::getStatus, 1));
        if (updated == 0) {
            throw BizException.badRequest("佣金状态已变化，请刷新后重试");
        }
        return getById(id);
    }

    /**
     * 将已到可结算时间的佣金记录从 0-待结算 变为 1-可结算。
     * <p>
     * 规则：订单已完成（status=30）、有收货完成时间、且 receiveTime + settleDays <= 当前时间。
     * 批量更新带 status=0 条件，多实例重复执行也幂等。
     *
     * @return 本次实际变为可结算的记录数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markEligibleDistributionsSettleable() {
        DistributionConfig config = distributionConfigService.getCurrentConfig();
        int settleDays = config != null && config.getSettleDays() != null ? config.getSettleDays() : 7;
        LocalDateTime now = LocalDateTime.now();

        List<Distribution> pending = lambdaQuery()
            .eq(Distribution::getStatus, 0)
            .list();
        if (pending.isEmpty()) {
            return 0;
        }

        Set<Long> orderIds = pending.stream()
            .map(Distribution::getOrderId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, OrderMaster> orderMap = orderIds.isEmpty()
            ? Map.of()
            : orderMasterService.listByIds(orderIds).stream()
                .collect(Collectors.toMap(OrderMaster::getId, Function.identity(), (a, b) -> a));

        List<Long> eligibleIds = pending.stream()
            .filter(d -> isEligibleForSettle(orderMap.get(d.getOrderId()), settleDays, now))
            .map(Distribution::getId)
            .collect(Collectors.toList());
        if (eligibleIds.isEmpty()) {
            return 0;
        }

        int updated = getBaseMapper().update(null, Wrappers.<Distribution>lambdaUpdate()
            .set(Distribution::getStatus, 1)
            .set(Distribution::getUpdateTime, now)
            .eq(Distribution::getStatus, 0)
            .in(Distribution::getId, eligibleIds));
        log.info("分销佣金到期可结算: count={}", updated);
        return updated;
    }

    /**
     * 判断佣金记录是否已到可结算时间。
     */
    private boolean isEligibleForSettle(OrderMaster order, int settleDays, LocalDateTime now) {
        if (order == null) {
            return false;
        }
        // 订单状态 30 = 已完成（见 order_master.status 注释）
        if (!Integer.valueOf(30).equals(order.getStatus()) || order.getReceiveTime() == null) {
            return false;
        }
        return !order.getReceiveTime().plusDays(settleDays).isAfter(now);
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
     * <p>
     * <b>关键：必须区分"钱有没有打出去"</b>
     * <ul>
     *   <li>未结算(0) / 可结算(1)：钱还没付，直接作废为已退回(3)；</li>
     *   <li>已结算(2)：佣金已按名单线下打款，**不得覆盖其状态**——那会抹掉财务的付款凭证，
     *       让记录看起来"从未结算过"。系统里没有可扣减的余额，自动化无法收回这笔钱，
     *       因此改为写备注 + 打 {@code [DIST-ALERT]} 告警，交财务人工追回。</li>
     * </ul>
     * 原实现使用 {@code ne(status, 3)}（"只要不是已退回就改成已退回"），
     * 会把已结算记录一并改掉且不留痕迹，属于会丢失财务凭证的缺陷。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRefundedByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return;
        }

        // 1) 只作废"钱还没打出去"的记录（正向枚举，避免误伤）
        boolean voided = lambdaUpdate()
            .set(Distribution::getStatus, 3)
            .set(Distribution::getRemark, "订单退款成功，佣金已作废（退款时尚未结算）")
            .set(Distribution::getUpdateTime, LocalDateTime.now())
            .eq(Distribution::getOrderNo, orderNo)
            .in(Distribution::getStatus, 0, 1)
            .update();
        if (voided) {
            log.info("退款作废佣金记录完成: orderNo={}", orderNo);
        }

        // 2) 已结算（已打款）的佣金：保留状态，只加备注并告警，交人工追回
        List<Distribution> settled = lambdaQuery()
            .eq(Distribution::getOrderNo, orderNo)
            .eq(Distribution::getStatus, 2)
            .list();
        if (settled == null || settled.isEmpty()) {
            return;
        }

        BigDecimal level1Total = settled.stream()
            .map(d -> Optional.ofNullable(d.getLevel1Amount()).orElse(BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal level2Total = settled.stream()
            .map(d -> Optional.ofNullable(d.getLevel2Amount()).orElse(BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        String remark = "订单退款成功，但该佣金已结算打款，需人工追回";
        for (Distribution record : settled) {
            lambdaUpdate()
                .set(Distribution::getRemark, remark)
                .set(Distribution::getUpdateTime, LocalDateTime.now())
                .eq(Distribution::getId, record.getId())
                .update();
        }

        log.error("[DIST-ALERT] 订单退款但佣金已结算打款，需人工追回。orderNo={}, 记录数={}, "
                + "一级佣金合计={}, 二级佣金合计={}。这些记录的状态已保留为 2（已结算）以便追溯付款凭证。",
            orderNo, settled.size(), level1Total.toPlainString(), level2Total.toPlainString());
    }

    /**
     * 导出佣金记录 CSV，补充买家/上级昵称手机号，方便财务线下打款。
     */
    @Override
    public void exportCsv(HttpServletResponse response, Integer status, String orderNo, String keyword) throws IOException {
        List<Long> resolvedUserIds = resolveUserIdsByKeyword(keyword);
        final List<Long> matchedUserIds;
        if (keyword != null && !keyword.isBlank() && resolvedUserIds.isEmpty()) {
            matchedUserIds = List.of(-1L);
        } else {
            matchedUserIds = resolvedUserIds;
        }
        long limit = maxExportRows > 0 ? maxExportRows : 10_000L;
        List<Distribution> records = lambdaQuery()
            .eq(status != null, Distribution::getStatus, status)
            .like(orderNo != null && !orderNo.isBlank(), Distribution::getOrderNo, orderNo)
            .and(matchedUserIds != null && !matchedUserIds.isEmpty(), wrapper -> wrapper
                .in(Distribution::getBuyerUserId, matchedUserIds)
                .or()
                .in(Distribution::getLevel1UserId, matchedUserIds)
                .or()
                .in(Distribution::getLevel2UserId, matchedUserIds))
            .orderByDesc(Distribution::getId)
            // 上限保护：多取 1 条用于判断"是否被截断"
            .last("LIMIT " + (limit + 1))
            .list();
        boolean truncated = records.size() > limit;
        if (truncated) {
            records = records.subList(0, (int) limit);
            log.error("[DIST-ALERT] 佣金导出触及上限被截断，请缩小筛选范围后分批导出。"
                    + "上限={}, status={}, orderNo={}, keyword={}",
                limit, status, orderNo, keyword);
        }
        Map<Long, AppUser> userMap = loadRelatedUsers(records);

        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=distribution.csv");

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("订单号,买家ID,买家昵称,买家手机号,订单金额,一级分销员ID,一级昵称,一级手机号,一级比例,一级佣金,二级分销员ID,二级昵称,二级手机号,二级比例,二级佣金,状态,创建时间\n");
        records.forEach(record -> appendCsvRow(csv, record, userMap));
        if (truncated) {
            // 必须写进文件本体：财务按这份名单打款，静默截断会少发佣金且无人察觉
            csv.append(truncationWarningRow(limit));
        }
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
            .flatMap(record -> Stream.of(record.getBuyerUserId(), record.getLevel1UserId(), record.getLevel2UserId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return appUserService.listByIds(userIds).stream()
            .collect(Collectors.toMap(AppUser::getId, Function.identity(), (a, b) -> a));
    }

    private List<Long> resolveUserIdsByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return appUserService.lambdaQuery()
            .select(AppUser::getId)
            .and(wrapper -> wrapper.like(AppUser::getNickname, keyword).or().like(AppUser::getPhone, keyword))
            .list()
            .stream()
            .map(AppUser::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private DistributionVO toVO(Distribution entity, Map<Long, AppUser> userMap) {
        DistributionVO vo = new DistributionVO();
        BeanUtils.copyProperties(entity, vo);
        AppUser buyer = userMap.get(entity.getBuyerUserId());
        AppUser level1 = userMap.get(entity.getLevel1UserId());
        AppUser level2 = userMap.get(entity.getLevel2UserId());
        vo.setBuyerNickname(buyer == null ? null : buyer.getNickname());
        vo.setBuyerPhone(buyer == null ? null : buyer.getPhone());
        vo.setLevel1Nickname(level1 == null ? null : level1.getNickname());
        vo.setLevel1Phone(level1 == null ? null : level1.getPhone());
        vo.setLevel2Nickname(level2 == null ? null : level2.getNickname());
        vo.setLevel2Phone(level2 == null ? null : level2.getPhone());
        vo.setTotalCommissionAmount(Optional.ofNullable(entity.getLevel1Amount()).orElse(BigDecimal.ZERO)
            .add(Optional.ofNullable(entity.getLevel2Amount()).orElse(BigDecimal.ZERO)));
        return vo;
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

    /**
     * CSV 单元格转义。
     * <p>
     * 除了常规的引号/逗号/换行转义，还必须**中和公式前缀**：
     * 昵称、备注等字段由用户可控，若以 {@code =}、{@code +}、{@code -}、{@code @}、
     * 制表符或回车开头，Excel / WPS 打开导出文件时会当作公式执行
     * （可外联请求、可触发命令执行）。业界通行做法是在值前加一个单引号，
     * 使其被当作纯文本。
     * <p>
     * 注意前缀必须在**双引号之内**：CSV 规范里引号内的前导单引号才会被表格软件
     * 识别为"强制文本"。
     */
    private String escapeCsv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        String sanitized = value;
        char first = sanitized.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r') {
            sanitized = "'" + sanitized;
        }

        String escaped = sanitized.replace("\"", "\"\"");
        return escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r")
                || escaped.contains("\"")
            ? "\"" + escaped + "\""
            : escaped;
    }

    /**
     * 生成"导出被截断"的显眼提示行。
     * <p>
     * 财务是按这份 CSV 线下打款的，静默截断会导致少发佣金且无人察觉。
     * 因此截断时必须写进文件本体（而不只是打日志），并以 {@code #} 开头以示非数据行。
     */
    private String truncationWarningRow(long maxRows) {
        return "#注意,#导出已达上限 " + maxRows + " 条,#结果可能不完整,#请缩小筛选范围后分批导出\n";
    }
}
