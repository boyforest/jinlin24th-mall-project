package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.CoffeePickupSequence;

import org.apache.ibatis.annotations.*;

@Mapper
public interface CoffeePickupSequenceMapper extends BaseMapper<CoffeePickupSequence> {
    @Select("SELECT * FROM coffee_pickup_sequence WHERE id=#{id} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    CoffeePickupSequence lockById(Long id);
}
