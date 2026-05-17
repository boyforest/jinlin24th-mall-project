package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.auth.CurrentUserId;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.OrderCreateDTO;
import com.jinlin24th.jinlin.pojo.vo.OrderVO;
import com.jinlin24th.jinlin.pojo.vo.WxPayParamsVO;
import com.jinlin24th.jinlin.service.OrderService;
import com.jinlin24th.jinlin.service.WxPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/user/order")
public class UserOrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ObjectProvider<WxPayService> wxPayServiceProvider;

    @PostMapping("/create")
    public Result<OrderVO> create(@CurrentUserId Long userId, @RequestBody OrderCreateDTO dto) {
        OrderVO vo = orderService.create(userId, dto);
        if (vo == null) {
            throw BizException.of(BizCode.ORDER_CREATE_FAILED);
        }
        return Result.success(vo);
    }

    @PostMapping("/{id}/pay")
    public Result<WxPayParamsVO> pay(@CurrentUserId Long userId, @PathVariable Long id) throws Exception {
        WxPayService wxPayService = wxPayServiceProvider.getIfAvailable();
        if (wxPayService == null) {
            throw BizException.badRequest("微信支付未启用，请先配置 wx.pay.enabled=true 及商户参数");
        }
        return Result.success(wxPayService.createMiniAppPayParams(userId, id));
    }

    @PostMapping("/{id}/receive")
    public Result<OrderVO> receive(@CurrentUserId Long userId, @PathVariable Long id) {
        return Result.success(orderService.confirmReceive(userId, id));
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
            throw BizException.of(BizCode.ORDER_NOT_FOUND);
        }
        return Result.success(vo);
    }
}
