package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.auth.CurrentUserId;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.entity.Coupon;
import com.jinlin24th.jinlin.pojo.vo.UserCouponVO;
import com.jinlin24th.jinlin.service.UserCouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user/coupon")
public class UserCouponController {

    @Autowired
    private UserCouponService userCouponService;

    @GetMapping("/available")
    public Result<List<Coupon>> available() {
        return Result.success(userCouponService.availableCoupons());
    }

    @GetMapping("/my")
    public Result<List<UserCouponVO>> my(@CurrentUserId Long userId) {
        return Result.success(userCouponService.myCoupons(userId));
    }
}
