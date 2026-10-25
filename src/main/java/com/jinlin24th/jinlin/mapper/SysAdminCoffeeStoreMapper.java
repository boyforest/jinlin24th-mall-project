package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.SysAdminCoffeeStore;

import org.apache.ibatis.annotations.*;

@Mapper
public interface SysAdminCoffeeStoreMapper extends BaseMapper<SysAdminCoffeeStore> {
    @Select("SELECT * FROM sys_admin_coffee_store WHERE id=#{id} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    SysAdminCoffeeStore lockById(Long id);
}
