package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.entity.Distribution;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.pojo.vo.DistributionVO;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface DistributionService extends IService<Distribution> {
    IPage<DistributionVO> adminPage(long page, long size, Integer status, String orderNo, String keyword);

    DistributionVO getDetail(Long id);

    Distribution getRequired(Long id);

    Distribution settle(Long id);

    /**
     * 将已到可结算时间的佣金记录从 0-待结算 变为 1-可结算。
     * 规则：订单已完成（status=30）、有收货完成时间、且 receiveTime + settleDays <= 当前时间。
     *
     * @return 本次实际变为可结算的记录数
     */
    int markEligibleDistributionsSettleable();

    /**
     * 支付成功后按买家上下级关系生成佣金记录。
     *
     * @param orderMaster 已支付订单
     */
    void createForPaidOrder(OrderMaster orderMaster);

    /**
     * 退款成功后将佣金记录标记为已退回/作废。
     *
     * @param orderNo 订单号
     */
    void markRefundedByOrderNo(String orderNo);

    /**
     * 导出佣金记录 CSV。
     */
    void exportCsv(HttpServletResponse response, Integer status, String orderNo, String keyword) throws IOException;
}
