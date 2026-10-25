package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.CoffeeOrder;

import org.apache.ibatis.annotations.*;

@Mapper
public interface CoffeeOrderMapper extends BaseMapper<CoffeeOrder> {
    @Select("SELECT * FROM coffee_order WHERE id=#{id} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    CoffeeOrder lockById(Long id);
}
