package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.auth.CurrentUserId;
import com.jinlin24th.jinlin.common.coffee.CoffeeValues;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.CoffeeOrderCreateDTO;
import com.jinlin24th.jinlin.service.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/coffee/orders")
@RequiredArgsConstructor
public class UserCoffeeOrderController {
    private final CoffeeOrderService orders;
    private final CoffeePaymentService payment;

    @PostMapping
    public Result<?> create(
            @CurrentUserId Long user, @Valid @RequestBody CoffeeOrderCreateDTO dto) {
        return Result.success(orders.create(user, dto));
    }

    @GetMapping("/{id}")
    public Result<?> detail(@CurrentUserId Long user, @PathVariable String id) {
        return Result.success(orders.userDetail(user, CoffeeValues.id(id)));
    }

    @PostMapping("/{id}/payment")
    public Result<?> payment(@CurrentUserId Long user, @PathVariable String id) {
        return Result.success(payment.payment(user, CoffeeValues.id(id)));
    }

    @PostMapping("/{id}/cancel")
    public Result<?> cancel(@CurrentUserId Long user, @PathVariable String id) {
        return Result.success(payment.cancel(user, CoffeeValues.id(id)));
    }
}
