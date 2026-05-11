package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.entity.ProductCategory;
import com.jinlin24th.jinlin.service.ProductCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user/product/category")
public class UserProductCategoryController {

    @Autowired
    private ProductCategoryService productCategoryService;

    @GetMapping("/list")
    public Result<List<ProductCategory>> list() {
        return Result.success(
            productCategoryService.lambdaQuery()
                .eq(ProductCategory::getStatus, 1)
                .orderByAsc(ProductCategory::getSort)
                .orderByAsc(ProductCategory::getId)
                .list()
        );
    }
}

