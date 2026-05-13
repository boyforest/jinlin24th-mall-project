package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.vo.OrderVO;
import com.jinlin24th.jinlin.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/order")
public class AdminOrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/list")
    public Result<IPage<OrderVO>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long size,
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String orderNo
    ) {
        return Result.success(orderService.adminPage(page, size, status, userId, orderNo));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> get(@PathVariable Long id) {
        OrderVO vo = orderService.get(id);
        if (vo == null) {
            throw BizException.of(BizCode.ORDER_NOT_FOUND);
        }
        return Result.success(vo);
    }
}
