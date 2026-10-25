package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.auth.CurrentAdminId;
import com.jinlin24th.jinlin.common.coffee.CoffeeValues;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.service.AdminAccountService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AdminAccountController {
    private final AdminAccountService service;

    @GetMapping("/admin/auth/context")
    public Result<?> context(@CurrentAdminId Long actor) {
        return Result.success(service.context(actor));
    }

    @GetMapping("/admin/accounts/list")
    public Result<?> list(
            @CurrentAdminId Long actor,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) Integer status) {
        return Result.success(service.list(actor, page, size, keyword, roleCode, status));
    }

    @GetMapping("/admin/accounts/roles")
    public Result<?> roles(@CurrentAdminId Long actor) {
        return Result.success(service.roleOptions(actor));
    }

    @GetMapping("/admin/accounts/coffee-stores")
    public Result<?> stores(@CurrentAdminId Long actor) {
        return Result.success(service.stores(actor, true));
    }

    @PostMapping("/admin/accounts")
    public Result<?> create(
            @CurrentAdminId Long actor, @Valid @RequestBody AdminAccountSaveDTO dto) {
        return Result.success(service.create(actor, dto));
    }

    @PutMapping("/admin/accounts/{id}")
    public Result<?> profile(
            @CurrentAdminId Long actor,
            @PathVariable String id,
            @Valid @RequestBody AdminAccountProfileDTO dto) {
        return Result.success(service.profile(actor, CoffeeValues.id(id), dto));
    }

    @PutMapping("/admin/accounts/{id}/access")
    public Result<?> access(
            @CurrentAdminId Long actor,
            @PathVariable String id,
            @Valid @RequestBody AdminAccountAccessDTO dto) {
        return Result.success(service.access(actor, CoffeeValues.id(id), dto));
    }

    @PutMapping("/admin/accounts/{id}/status")
    public Result<?> status(
            @CurrentAdminId Long actor,
            @PathVariable String id,
            @Valid @RequestBody AdminAccountStatusDTO dto) {
        return Result.success(service.status(actor, CoffeeValues.id(id), dto.getStatus()));
    }

    @PostMapping("/admin/accounts/{id}/reset-password")
    public Result<?> reset(
            @CurrentAdminId Long actor,
            @PathVariable String id,
            @Valid @RequestBody AdminAccountResetDTO dto) {
        return Result.success(
                service.reset(actor, CoffeeValues.id(id), dto.getTemporaryPassword()));
    }
}
