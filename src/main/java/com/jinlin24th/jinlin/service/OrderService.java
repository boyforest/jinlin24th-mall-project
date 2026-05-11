package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.pojo.dto.OrderCreateDTO;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.pojo.vo.OrderVO;

public interface OrderService {
    OrderVO create(Long userId, OrderCreateDTO dto);

    IPage<OrderVO> userPage(Long userId, long page, long size, Integer status);

    IPage<OrderVO> adminPage(long page, long size, Integer status, Long userId, String orderNo);

    /**
     * C 端：获取用户自己的订单详情（带归属校验）
     */
    OrderVO getForUser(Long userId, Long id);

    /**
     * 管理端：按订单ID获取详情（不做 userId 归属校验）
     */
    OrderVO get(Long id);
}
