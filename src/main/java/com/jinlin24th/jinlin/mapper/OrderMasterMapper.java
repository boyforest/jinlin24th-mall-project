package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 订单主表Mapper接口
 */
@Mapper
public interface OrderMasterMapper extends BaseMapper<OrderMaster> {

    /**
     * 根据订单号查询订单
     * @param orderNo 订单号
     * @return 订单信息
     */
    @Select("SELECT * FROM order_master WHERE order_no = #{orderNo} LIMIT 1")
    OrderMaster selectByOrderNo(String orderNo);
}
