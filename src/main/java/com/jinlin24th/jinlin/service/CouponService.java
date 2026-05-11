package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.dto.CouponDTO;
import com.jinlin24th.jinlin.pojo.entity.Coupon;
import com.jinlin24th.jinlin.pojo.vo.CouponVO;

public interface CouponService extends IService<Coupon> {
    IPage<CouponVO> adminPage(long page, long size, Integer status);

    CouponVO getVO(Long id);

    CouponVO create(CouponDTO dto);

    CouponVO update(Long id, CouponDTO dto);

    Boolean delete(Long id);
}


