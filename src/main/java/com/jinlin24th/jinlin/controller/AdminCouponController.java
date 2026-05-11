package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.CouponDTO;
import com.jinlin24th.jinlin.pojo.vo.CouponVO;
import com.jinlin24th.jinlin.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/coupon")
public class AdminCouponController {

    @Autowired
    private CouponService couponService;

    @GetMapping("/list")
    public Result<IPage<CouponVO>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long size,
        @RequestParam(required = false) Integer status
    ) {
        return Result.success(couponService.adminPage(page, size, status));
    }

    @GetMapping("/{id}")
    public Result<CouponVO> get(@PathVariable Long id) {
        CouponVO vo = couponService.getVO(id);
        if (vo == null) {
            return Result.error(404, "优惠券不存在");
        }
        return Result.success(vo);
    }

    @PostMapping
    public Result<CouponVO> create(@RequestBody CouponDTO dto) {
        return Result.success(couponService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<CouponVO> update(@PathVariable Long id, @RequestBody CouponDTO dto) {
        CouponVO vo = couponService.update(id, dto);
        if (vo == null) {
            return Result.error(404, "优惠券不存在");
        }
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(couponService.delete(id));
    }
}
