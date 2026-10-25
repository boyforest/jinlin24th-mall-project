package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.auth.CurrentUserId;
import com.jinlin24th.jinlin.common.coffee.CoffeeValues;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.pojo.vo.CoffeeCartVO;
import com.jinlin24th.jinlin.service.CoffeeCartAppService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/coffee/cart")
@RequiredArgsConstructor
public class UserCoffeeCartController {
    private final CoffeeCartAppService service;

    @GetMapping("/list")
    public Result<List<CoffeeCartVO>> list(
            @CurrentUserId Long userId, @RequestParam String storeId) {
        return Result.success(service.list(userId, CoffeeValues.id(storeId)));
    }

    @PostMapping
    public Result<CoffeeCartVO> add(
            @CurrentUserId Long userId, @Valid @RequestBody CoffeeCartAddDTO dto) {
        return Result.success(service.add(userId, dto));
    }

    @PutMapping("/{id}")
    public Result<CoffeeCartVO> update(
            @CurrentUserId Long userId,
            @PathVariable String id,
            @Valid @RequestBody CoffeeCartUpdateDTO dto) {
        return Result.success(service.update(userId, CoffeeValues.id(id), dto));
    }

    @PutMapping("/selection")
    public Result<List<CoffeeCartVO>> selection(
            @CurrentUserId Long userId, @Valid @RequestBody CoffeeCartSelectionDTO dto) {
        return Result.success(service.selection(userId, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@CurrentUserId Long userId, @PathVariable String id) {
        return Result.success(service.delete(userId, CoffeeValues.id(id)));
    }
}
