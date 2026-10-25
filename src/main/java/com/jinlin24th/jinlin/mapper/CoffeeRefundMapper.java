package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.CoffeeRefund;

import org.apache.ibatis.annotations.*;

@Mapper
public interface CoffeeRefundMapper extends BaseMapper<CoffeeRefund> {
    @Select(
            "SELECT COALESCE(SUM(r.amount_cent),0) FROM coffee_refund r JOIN coffee_order o ON"
                    + " o.id=r.order_id WHERE o.store_id=#{storeId} AND r.status='SUCCESS' AND"
                    + " r.succeeded_at>=#{start} AND r.succeeded_at<#{end}")
    java.math.BigDecimal ledger(
            @Param("storeId") Long storeId,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    @Select("SELECT * FROM coffee_refund WHERE id=#{id} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    CoffeeRefund lockById(Long id);
}
