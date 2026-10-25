package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.CoffeeOrderItem;

import org.apache.ibatis.annotations.*;

@Mapper
public interface CoffeeOrderItemMapper extends BaseMapper<CoffeeOrderItem> {
    @Select("SELECT * FROM coffee_order_item WHERE id=#{id} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    CoffeeOrderItem lockById(Long id);
}
