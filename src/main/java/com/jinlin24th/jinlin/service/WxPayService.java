package com.jinlin24th.jinlin.service;

import com.jinlin24th.jinlin.pojo.dto.WxPayPrepayDTO;
import com.jinlin24th.jinlin.pojo.vo.WxPayParamsVO;

import java.math.BigDecimal;

/**
 * 微信支付服务接口
 */
public interface WxPayService {

    /**
     * 为当前小程序用户的业务订单创建微信 JSAPI 支付参数。
     *
     * @param userId 当前登录用户ID
     * @param orderId 业务订单ID
     * @return 小程序 wx.requestPayment 所需参数
     * @throws Exception 支付异常
     */
    WxPayParamsVO createMiniAppPayParams(Long userId, Long orderId) throws Exception;

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
     * 申请退款。
     * <p>
     * 安全约定（服务端为唯一事实来源）：
     * <ul>
     *   <li>订单总金额 {@code total} 一律取 {@code payment_record.pay_amount}，不接受调用方传入；</li>
     *   <li>退款单号 {@code out_refund_no} 由服务端按订单号确定性生成，保证同一订单重复调用不产生重复退款；</li>
     *   <li>退款金额不得超过实付金额，且仅允许订单处于已支付未完成状态时退款。</li>
     * </ul>
     *
     * @param outTradeNo 商户订单号
     * @param refundAmount 退款金额；传 {@code null} 表示全额退款
     * @return 微信退款单号 refund_id
     * @throws Exception 退款异常
     */
    String refund(String outTradeNo, BigDecimal refundAmount) throws Exception;

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
     * @param wechatpayTimestamp 回调Header: Wechatpay-Timestamp
     * @param wechatpayNonce     回调Header: Wechatpay-Nonce
     * @param wechatpaySignature 回调Header: Wechatpay-Signature
     * @param wechatpaySerial    回调Header: Wechatpay-Serial
     * @param notifyData 通知Body原文
     * @return 是否处理成功
     * @throws Exception 处理异常
     */
    boolean handlePaymentNotify(String wechatpayTimestamp, String wechatpayNonce,
                                String wechatpaySignature, String wechatpaySerial,
                                String notifyData) throws Exception;

    /**
     * 处理退款结果通知
     * @param wechatpayTimestamp 回调Header: Wechatpay-Timestamp
     * @param wechatpayNonce     回调Header: Wechatpay-Nonce
     * @param wechatpaySignature 回调Header: Wechatpay-Signature
     * @param wechatpaySerial    回调Header: Wechatpay-Serial
     * @param notifyData 通知Body原文
     * @return 是否处理成功
     * @throws Exception 处理异常
     */
    boolean handleRefundNotify(String wechatpayTimestamp, String wechatpayNonce,
                               String wechatpaySignature, String wechatpaySerial,
                               String notifyData) throws Exception;
}
