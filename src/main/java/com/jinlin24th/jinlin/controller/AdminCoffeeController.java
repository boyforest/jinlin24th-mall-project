package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.auth.CurrentAdminId;
import com.jinlin24th.jinlin.common.coffee.CoffeeValues;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.pojo.vo.*;
import com.jinlin24th.jinlin.service.CoffeeCatalogService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/coffee")
@RequiredArgsConstructor
public class AdminCoffeeController {
    private final CoffeeCatalogService service;

    @GetMapping("/context")
    public Result<CoffeeAdminContextVO> context(@CurrentAdminId Long adminId) {
        return Result.success(service.context(adminId));
    }

    @PostMapping("/store")
    public Result<CoffeeStoreVO> createStore(
            @CurrentAdminId Long adminId, @Valid @RequestBody CoffeeStoreSaveDTO dto) {
        return Result.success(service.saveStore(adminId, null, dto));
    }

    @GetMapping("/store/{storeId}")
    public Result<CoffeeStoreVO> store(@CurrentAdminId Long adminId, @PathVariable String storeId) {
        return Result.success(service.adminStore(adminId, CoffeeValues.id(storeId)));
    }

    @PutMapping("/store/{storeId}")
    public Result<CoffeeStoreVO> updateStore(
            @CurrentAdminId Long adminId,
            @PathVariable String storeId,
            @Valid @RequestBody CoffeeStoreSaveDTO dto) {
        return Result.success(service.saveStore(adminId, CoffeeValues.id(storeId), dto));
    }

    @GetMapping("/menu")
    public Result<CoffeeAdminMenuVO> menu(
            @CurrentAdminId Long adminId, @RequestParam String storeId) {
        return Result.success(service.adminMenu(adminId, CoffeeValues.id(storeId)));
    }

    @PostMapping("/category")
    public Result<CoffeeAdminMenuVO.Category> createCategory(
            @CurrentAdminId Long adminId,
            @RequestParam String storeId,
            @Valid @RequestBody CoffeeCategorySaveDTO dto) {
        return Result.success(service.saveCategory(adminId, CoffeeValues.id(storeId), null, dto));
    }

    @PutMapping("/category/{id}")
    public Result<CoffeeAdminMenuVO.Category> updateCategory(
            @CurrentAdminId Long adminId,
            @PathVariable String id,
            @Valid @RequestBody CoffeeCategorySaveDTO dto) {
        return Result.success(service.saveCategory(adminId, null, CoffeeValues.id(id), dto));
    }

    @PostMapping("/product")
    public Result<CoffeeAdminProductVO> createProduct(
            @CurrentAdminId Long adminId,
            @RequestParam String storeId,
            @Valid @RequestBody CoffeeProductSaveDTO dto) {
        return Result.success(service.saveProduct(adminId, CoffeeValues.id(storeId), null, dto));
    }

    @PutMapping("/product/{id}")
    public Result<CoffeeAdminProductVO> updateProduct(
            @CurrentAdminId Long adminId,
            @PathVariable String id,
            @Valid @RequestBody CoffeeProductSaveDTO dto) {
        return Result.success(service.saveProduct(adminId, null, CoffeeValues.id(id), dto));
    }
}
