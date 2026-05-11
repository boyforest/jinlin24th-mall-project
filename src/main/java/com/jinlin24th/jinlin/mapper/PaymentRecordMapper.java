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
     * 根据退款单号查询支付记录
     * @param refundNo 退款单号
     * @return 支付记录
     */
    @Select("SELECT * FROM payment_record WHERE refund_id = #{refundNo} LIMIT 1")
    PaymentRecord selectByRefundNo(String refundNo);
}
