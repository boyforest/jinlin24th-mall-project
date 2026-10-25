package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.CoffeeNotificationTask;

import org.apache.ibatis.annotations.*;

@Mapper
public interface CoffeeNotificationTaskMapper extends BaseMapper<CoffeeNotificationTask> {
    @Select("SELECT * FROM coffee_notification_task WHERE id=#{id} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    CoffeeNotificationTask lockById(Long id);
}
