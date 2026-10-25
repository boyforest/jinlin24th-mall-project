package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.CoffeePayment;

import org.apache.ibatis.annotations.*;

@Mapper
public interface CoffeePaymentMapper extends BaseMapper<CoffeePayment> {
    @Select(
            "SELECT COUNT(*) AS paidCount, COALESCE(SUM(p.amount_cent),0) AS received FROM"
                    + " coffee_payment p JOIN coffee_order o ON o.id=p.order_id WHERE"
                    + " o.store_id=#{storeId} AND p.status='PAID' AND p.paid_at>=#{start} AND"
                    + " p.paid_at<#{end}")
    java.util.Map<String, Object> ledger(
            @Param("storeId") Long storeId,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Select("SELECT * FROM coffee_payment WHERE id=#{id} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    CoffeePayment lockById(Long id);
}
