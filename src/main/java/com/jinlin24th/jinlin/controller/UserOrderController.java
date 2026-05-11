package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.auth.CurrentUserId;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.OrderCreateDTO;
import com.jinlin24th.jinlin.pojo.vo.OrderVO;
import com.jinlin24th.jinlin.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/user/order")
public class UserOrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    public Result<OrderVO> create(@CurrentUserId Long userId, @RequestBody OrderCreateDTO dto) {
        OrderVO vo = orderService.create(userId, dto);
        if (vo == null) {
            return Result.error(400, "创建订单失败");
        }
        return Result.success(vo);
    }

    @GetMapping("/list")
    public Result<IPage<OrderVO>> list(
        @CurrentUserId Long userId,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long size,
        @RequestParam(required = false) Integer status
    ) {
        return Result.success(orderService.userPage(userId, page, size, status));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> get(@CurrentUserId Long userId, @PathVariable Long id) {
        OrderVO vo = orderService.getForUser(userId, id);
        if (vo == null) {
            return Result.error(404, "订单不存在");
        }
        return Result.success(vo);
    }
}
