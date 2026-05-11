package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.entity.ProductCategory;
import com.jinlin24th.jinlin.service.ProductCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin/product/category")
public class AdminProductCategoryController {

    @Autowired
    private ProductCategoryService productCategoryService;

    @GetMapping("/list")
    public Result<List<ProductCategory>> list(@RequestParam(required = false) Integer status) {
        return Result.success(
            productCategoryService.lambdaQuery()
                .eq(status != null, ProductCategory::getStatus, status)
                .orderByAsc(ProductCategory::getSort)
                .orderByAsc(ProductCategory::getId)
                .list()
        );
    }

    @GetMapping("/{id}")
    public Result<ProductCategory> get(@PathVariable Long id) {
        ProductCategory category = productCategoryService.getById(id);
        if (category == null) {
            return Result.error(404, "分类不存在");
        }
        return Result.success(category);
    }

    @PostMapping
    public Result<ProductCategory> create(@RequestBody ProductCategory category) {
        productCategoryService.save(category);
        return Result.success(category);
    }

    @PutMapping("/{id}")
    public Result<ProductCategory> update(@PathVariable Long id, @RequestBody ProductCategory category) {
        category.setId(id);
        productCategoryService.updateById(category);
        return Result.success(productCategoryService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(productCategoryService.removeById(id));
    }
}

