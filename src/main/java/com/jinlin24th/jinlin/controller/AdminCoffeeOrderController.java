package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.auth.CurrentAdminId;
import com.jinlin24th.jinlin.common.coffee.CoffeeValues;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.service.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/coffee")
@RequiredArgsConstructor
public class AdminCoffeeOrderController {
    private final CoffeeOrderService orders;
    private final CoffeePaymentService payment;

    @GetMapping("/workbench")
    public Result<?> workbench(@CurrentAdminId Long actor, @RequestParam String storeId) {
        return Result.success(orders.workbench(actor, CoffeeValues.id(storeId)));
    }

    @GetMapping("/orders/list")
    public Result<?> list(
            @CurrentAdminId Long actor,
            @RequestParam String storeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String fulfillmentStatus,
            @RequestParam(required = false) String refundStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo) {
        return Result.success(
                orders.list(
                        actor,
                        CoffeeValues.id(storeId),
                        page,
                        size,
                        fulfillmentStatus,
                        refundStatus,
                        keyword,
                        dateFrom,
                        dateTo));
    }

    @GetMapping("/orders/{id}")
    public Result<?> detail(@CurrentAdminId Long actor, @PathVariable String id) {
        return Result.success(orders.adminDetail(actor, CoffeeValues.id(id)));
    }

    @PostMapping("/orders/{id}/accept")
    public Result<?> accept(
            @CurrentAdminId Long actor,
            @PathVariable String id,
            @Valid @RequestBody CoffeeOrderActionDTO dto) {
        return Result.success(orders.action(actor, CoffeeValues.id(id), "ACCEPT", dto));
    }

    @PostMapping("/orders/{id}/start")
    public Result<?> start(
            @CurrentAdminId Long actor,
            @PathVariable String id,
            @Valid @RequestBody CoffeeOrderActionDTO dto) {
        return Result.success(orders.action(actor, CoffeeValues.id(id), "START", dto));
    }

    @PostMapping("/orders/{id}/ready")
    public Result<?> ready(
            @CurrentAdminId Long actor,
            @PathVariable String id,
            @Valid @RequestBody CoffeeOrderActionDTO dto) {
        return Result.success(orders.action(actor, CoffeeValues.id(id), "READY", dto));
    }

    @PostMapping("/orders/{id}/complete")
    public Result<?> complete(
            @CurrentAdminId Long actor,
            @PathVariable String id,
            @Valid @RequestBody CoffeeOrderActionDTO dto) {
        return Result.success(orders.action(actor, CoffeeValues.id(id), "COMPLETE", dto));
    }

    @PostMapping("/orders/{id}/reject")
    public Result<?> reject(
            @CurrentAdminId Long actor,
            @PathVariable String id,
            @Valid @RequestBody CoffeeOrderRefundDTO dto) {
        return Result.success(payment.refund(actor, CoffeeValues.id(id), true, dto));
    }

    @PostMapping("/orders/{id}/refund")
    public Result<?> refund(
            @CurrentAdminId Long actor,
            @PathVariable String id,
            @Valid @RequestBody CoffeeOrderRefundDTO dto) {
        return Result.success(payment.refund(actor, CoffeeValues.id(id), false, dto));
    }

    @GetMapping("/ledger")
    public Result<?> ledger(
            @CurrentAdminId Long actor,
            @RequestParam String storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return Result.success(orders.ledger(actor, CoffeeValues.id(storeId), dateFrom, dateTo));
    }
}
