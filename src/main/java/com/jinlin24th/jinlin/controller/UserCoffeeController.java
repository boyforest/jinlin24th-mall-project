package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.coffee.CoffeeValues;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.vo.*;
import com.jinlin24th.jinlin.service.CoffeeCatalogService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/coffee")
@RequiredArgsConstructor
public class UserCoffeeController {
    private final CoffeeCatalogService service;

    @GetMapping("/store")
    public Result<CoffeeStoreVO> store() {
        return Result.success(service.currentStore());
    }

    @GetMapping("/menu")
    public Result<CoffeeMenuVO> menu(@RequestParam String storeId) {
        return Result.success(service.menu(CoffeeValues.id(storeId)));
    }
}
