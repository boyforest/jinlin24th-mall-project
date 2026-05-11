package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.ProductDTO;
import com.jinlin24th.jinlin.pojo.vo.ProductVO;
import com.jinlin24th.jinlin.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/product")
public class AdminProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/list")
    public Result<IPage<ProductVO>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long size,
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) Long categoryId
    ) {
        return Result.success(productService.adminPage(page, size, status, categoryId));
    }

    @GetMapping("/{id}")
    public Result<ProductVO> get(@PathVariable Long id) {
        ProductVO vo = productService.getVO(id);
        if (vo == null) {
            return Result.error(404, "商品不存在");
        }
        return Result.success(vo);
    }

    @PostMapping
    public Result<ProductVO> create(@RequestBody ProductDTO dto) {
        ProductVO vo = productService.create(dto);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    public Result<ProductVO> update(@PathVariable Long id, @RequestBody ProductDTO dto) {
        ProductVO vo = productService.update(id, dto);
        if (vo == null) {
            return Result.error(404, "商品不存在");
        }
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(productService.delete(id));
    }
}
