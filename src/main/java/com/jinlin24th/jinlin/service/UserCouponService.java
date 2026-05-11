package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.entity.Coupon;
import com.jinlin24th.jinlin.pojo.entity.UserCoupon;
import com.jinlin24th.jinlin.pojo.vo.UserCouponVO;

import java.util.List;

public interface UserCouponService extends IService<UserCoupon> {
    List<Coupon> availableCoupons();

    List<UserCouponVO> myCoupons(Long userId);
}


