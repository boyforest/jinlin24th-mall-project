package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.vo.ProductSkuVO;
import com.jinlin24th.jinlin.pojo.vo.ProductVO;
import com.jinlin24th.jinlin.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/product")
public class UserProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/list")
    public Result<IPage<ProductVO>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long size,
        @RequestParam(required = false) Long categoryId
    ) {
        return Result.success(productService.userPage(page, size, categoryId));
    }

    @GetMapping("/{id}")
    public Result<ProductVO> get(@PathVariable Long id) {
        ProductVO vo = productService.getUserVO(id);
        if (vo == null) {
            return Result.error(404, "商品不存在");
        }
        return Result.success(vo);
    }

    @GetMapping("/{id}/skus")
    public Result<List<ProductSkuVO>> skus(@PathVariable Long id) {
        return Result.success(productService.userSkus(id));
    }
}
