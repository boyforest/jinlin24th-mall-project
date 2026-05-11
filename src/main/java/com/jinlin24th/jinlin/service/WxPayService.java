package com.jinlin24th.jinlin.service;

import com.jinlin24th.jinlin.pojo.dto.WxPayPrepayDTO;

import java.math.BigDecimal;

/**
 * 微信支付服务接口
 */
public interface WxPayService {

    /**
     * 统一下单（小程序支付）
     * @param prepayDTO 预支付请求参数
     * @param userId 用户ID
     * @return 预支付ID
     * @throws Exception 支付异常
     */
    String createOrder(WxPayPrepayDTO prepayDTO, Long userId) throws Exception;

    /**
     * 查询订单
     * @param outTradeNo 商户订单号
     * @return 订单状态信息
     * @throws Exception 查询异常
     */
    String queryOrder(String outTradeNo) throws Exception;

    /**
     * 关闭订单
     * @param outTradeNo 商户订单号
     * @return 是否成功
     * @throws Exception 关闭异常
     */
    boolean closeOrder(String outTradeNo) throws Exception;

    /**
     * 申请退款
     * @param outTradeNo 商户订单号
     * @param outRefundNo 退款单号
     * @param totalAmount 订单总金额
     * @param refundAmount 退款金额
     * @return 退款单号
     * @throws Exception 退款异常
     */
    String refund(String outTradeNo, String outRefundNo, BigDecimal totalAmount, BigDecimal refundAmount) throws Exception;

    /**
     * 查询退款
     * @param outRefundNo 退款单号
     * @return 退款状态信息
     * @throws Exception 查询异常
     */
    String queryRefund(String outRefundNo) throws Exception;

    /**
     * 下载对账单
     * @param billDate 账单日期(格式:20210101)
     * @param billType 账单类型(ALL/SUCCESS/REFUND)
     * @return 账单内容
     * @throws Exception 下载异常
     */
    String downloadBill(String billDate, String billType) throws Exception;

    /**
     * 处理支付结果通知
     * @param notifyData 通知数据
     * @return 是否处理成功
     * @throws Exception 处理异常
     */
    boolean handlePaymentNotify(String notifyData) throws Exception;

    /**
     * 处理退款结果通知
     * @param notifyData 通知数据
     * @return 是否处理成功
     * @throws Exception 处理异常
     */
    boolean handleRefundNotify(String notifyData) throws Exception;
}
