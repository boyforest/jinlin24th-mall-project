package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.CoffeeOrderEvent;

import org.apache.ibatis.annotations.*;

@Mapper
public interface CoffeeOrderEventMapper extends BaseMapper<CoffeeOrderEvent> {
    @Select("SELECT * FROM coffee_order_event WHERE id=#{id} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    CoffeeOrderEvent lockById(Long id);
}
