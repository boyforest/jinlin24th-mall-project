package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.pojo.dto.OrderCreateDTO;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.pojo.vo.OrderVO;

public interface OrderService {
    OrderVO create(Long userId, OrderCreateDTO dto);

    /**
     * 取消未支付订单，并恢复订单占用库存。
     *
     * @param orderNo 订单编号
     * @param reason 取消原因
     * @return true-本次成功取消，false-订单不存在或已不是待付款状态
     */
    boolean cancelUnpaidOrder(String orderNo, String reason);

    /**
     * 管理端取消待付款订单，并恢复库存。
     */
    OrderVO adminCancelUnpaid(Long id, Long adminId);

    /**
     * 管理端发货：待发货 -> 待收货。
     */
    OrderVO adminShip(Long id, Long adminId);

    /**
     * 管理端完成订单：待收货 -> 已完成。
     */
    OrderVO adminComplete(Long id, Long adminId);

    /**
     * C 端确认收货：待收货 -> 已完成。
     */
    OrderVO confirmReceive(Long userId, Long id);

    IPage<OrderVO> userPage(Long userId, long page, long size, Integer status);

    IPage<OrderVO> adminPage(long page, long size, Integer status, Long userId, String orderNo, String receiverPhone);

    /**
     * C 端：获取用户自己的订单详情（带归属校验）
     */
    OrderVO getForUser(Long userId, Long id);

    /**
     * 管理端：按订单ID获取详情（不做 userId 归属校验）
     */
    OrderVO get(Long id);
}
