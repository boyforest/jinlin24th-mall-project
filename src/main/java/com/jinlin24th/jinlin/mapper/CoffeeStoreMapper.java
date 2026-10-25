package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.CoffeeStore;

import org.apache.ibatis.annotations.*;

@Mapper
public interface CoffeeStoreMapper extends BaseMapper<CoffeeStore> {

    /** 单店阶段统一锁顺序：先门店，再目录/购物车，避免加购与菜单维护交错。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM coffee_store WHERE id = #{id} FOR UPDATE")
    CoffeeStore lockById(@Param("id") Long id);
}
