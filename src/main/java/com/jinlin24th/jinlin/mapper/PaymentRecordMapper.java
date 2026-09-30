package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 支付记录Mapper接口
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    /**
     * 根据订单号查询支付记录
     * @param orderNo 订单号
     * @return 支付记录
     */
    @Select("SELECT * FROM payment_record WHERE order_no = #{orderNo} LIMIT 1")
    PaymentRecord selectByOrderNo(String orderNo);

    /**
     * 根据微信退款单号（refund_id）查询支付记录。
     * <p>
     * 重要：退款回调 Body 里同时包含 {@code out_refund_no}（商户自己的退款单号）和
     * {@code refund_id}（微信生成的退款单号）。{@code refund()} 申请退款成功后落库的是
     * {@code refund_id}，因此回调必须按 {@code refund_id} 查询——按 {@code out_refund_no}
     * 查询恒为空，会导致"退款成功但订单不变、佣金不退"。
     *
     * @param refundId 微信退款单号（回调 Body 的 refund_id）
     * @return 支付记录
     */
    @Select("SELECT * FROM payment_record WHERE refund_id = #{refundId} LIMIT 1")
    PaymentRecord selectByRefundId(String refundId);
}
