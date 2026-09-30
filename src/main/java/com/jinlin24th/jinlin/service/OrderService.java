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

    IPage<OrderVO> adminPage(long page, long size, Integer status, Long userId, String userKeyword, String orderNo, String receiverPhone);

    /**
     * C 端：获取用户自己的订单详情（带归属校验）
     */
    OrderVO getForUser(Long userId, Long id);

    /**
     * 管理端：按订单ID获取详情（不做 userId 归属校验）
     */
    OrderVO get(Long id);

    /**
     * 兜底取消超时未付款订单（返回本次成功取消的订单数）。
     * <p>
     * 为什么需要：订单在**下单时**就占用库存，而超时取消原本只依赖 RocketMQ 延迟消息
     * （`app.mq.enabled` 默认为 false）。MQ 关闭或不可用时没有任何兜底，未付款订单会把
     * 库存永久锁死，只能靠管理端逐单手工取消。
     * <p>
     * 实现要求：
     * <ul>
     *   <li>只扫描 {@code status=0}（待付款）且创建时间早于超时阈值的订单；</li>
     *   <li>必须带批量上限，避免一次把全部待付款订单拉进内存；</li>
     *   <li>复用 {@link #cancelUnpaidOrder}（内含状态 CAS），因此与 MQ 路径并发触达也幂等；</li>
     *   <li>单个订单失败不得中断整批，失败的订单留待下一轮重试。</li>
     * </ul>
     *
     * @param limit 单次处理的最大订单数（必须为正）
     * @return 本次真正取消（CAS 命中）的订单数
     */
    int cancelTimeoutPendingOrders(int limit);
}
