package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.CoffeeCart;

import org.apache.ibatis.annotations.*;

@Mapper
public interface CoffeeCartMapper extends BaseMapper<CoffeeCart> {

    @Update(
            "UPDATE coffee_cart SET quantity=#{quantity}, checked=#{checked}, version=version+1, "
                    + "update_time=CURRENT_TIMESTAMP WHERE id=#{id} AND user_id=#{userId} "
                    + "AND version=#{version} AND version < 9007199254740991")
    int updateVersioned(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("quantity") Integer quantity,
            @Param("checked") Integer checked,
            @Param("version") Long version);
}
