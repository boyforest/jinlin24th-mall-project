package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.ProductSkuDTO;
import com.jinlin24th.jinlin.pojo.vo.ProductSkuVO;
import com.jinlin24th.jinlin.service.ProductSkuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/product/sku")
public class AdminProductSkuController {

    @Autowired
    private ProductSkuService productSkuService;

    @GetMapping("/list")
    public Result<IPage<ProductSkuVO>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long size,
        @RequestParam(required = false) Long productId,
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) String keyword
    ) {
        return Result.success(productSkuService.adminPage(page, size, productId, status, keyword));
    }

    @GetMapping("/{id}")
    public Result<ProductSkuVO> get(@PathVariable Long id) {
        ProductSkuVO vo = productSkuService.getVO(id);
        if (vo == null) {
            throw BizException.of(BizCode.SKU_NOT_FOUND);
        }
        return Result.success(vo);
    }

    @PostMapping
    public Result<ProductSkuVO> create(@RequestBody ProductSkuDTO dto) {
        ProductSkuVO vo = productSkuService.create(dto);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    public Result<ProductSkuVO> update(@PathVariable Long id, @RequestBody ProductSkuDTO dto) {
        ProductSkuVO vo = productSkuService.update(id, dto);
        if (vo == null) {
            throw BizException.of(BizCode.SKU_NOT_FOUND);
        }
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(productSkuService.delete(id));
    }
}
