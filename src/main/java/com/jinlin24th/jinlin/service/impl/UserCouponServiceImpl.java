package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.entity.Coupon;
import com.jinlin24th.jinlin.pojo.entity.UserCoupon;
import com.jinlin24th.jinlin.mapper.UserCouponMapper;
import com.jinlin24th.jinlin.pojo.vo.UserCouponVO;
import com.jinlin24th.jinlin.service.CouponService;
import com.jinlin24th.jinlin.service.UserCouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon>
    implements UserCouponService {

    @Autowired
    private CouponService couponService;

    @Override
    public List<Coupon> availableCoupons() {
        LocalDateTime now = LocalDateTime.now();
        return couponService.lambdaQuery()
            .eq(Coupon::getStatus, 1)
            .le(Coupon::getStartTime, now)
            .ge(Coupon::getEndTime, now)
            .orderByDesc(Coupon::getId)
            .list();
    }

    @Override
    public List<UserCouponVO> myCoupons(Long userId) {
        List<UserCoupon> list = lambdaQuery()
            .eq(UserCoupon::getUserId, userId)
            .orderByDesc(UserCoupon::getId)
            .list();
        if (list.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> couponIds = list.stream().map(UserCoupon::getCouponId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Coupon> couponById = couponIds.isEmpty()
            ? Collections.emptyMap()
            : couponService.listByIds(couponIds).stream().collect(Collectors.toMap(Coupon::getId, c -> c, (a, b) -> a));

        return list.stream().map(uc -> {
            UserCouponVO vo = new UserCouponVO();
            BeanUtils.copyProperties(uc, vo);
            Coupon c = couponById.get(uc.getCouponId());
            if (c != null) {
                vo.setName(c.getName());
                vo.setType(c.getType());
                vo.setMinAmount(c.getMinAmount());
                vo.setDiscountValue(c.getDiscountValue());
                vo.setStartTime(c.getStartTime());
                vo.setEndTime(c.getEndTime());
                vo.setCouponStatus(c.getStatus());
            }
            return vo;
        }).collect(Collectors.toList());
    }
}


