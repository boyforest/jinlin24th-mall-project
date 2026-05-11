package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.dto.CouponDTO;
import com.jinlin24th.jinlin.pojo.entity.Coupon;
import com.jinlin24th.jinlin.mapper.CouponMapper;
import com.jinlin24th.jinlin.pojo.vo.CouponVO;
import com.jinlin24th.jinlin.service.CouponService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon>
    implements CouponService {

    @Override
    public IPage<CouponVO> adminPage(long page, long size, Integer status) {
        Page<Coupon> p = new Page<>(page, size);
        IPage<Coupon> entityPage = lambdaQuery()
            .eq(status != null, Coupon::getStatus, status)
            .orderByDesc(Coupon::getId)
            .page(p);
        Page<CouponVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(e -> {
            CouponVO vo = new CouponVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public CouponVO getVO(Long id) {
        Coupon coupon = getById(id);
        if (coupon == null) {
            return null;
        }
        CouponVO vo = new CouponVO();
        BeanUtils.copyProperties(coupon, vo);
        return vo;
    }

    @Override
    public CouponVO create(CouponDTO dto) {
        Coupon coupon = new Coupon();
        BeanUtils.copyProperties(dto, coupon);
        coupon.setReceivedCount(0);
        coupon.setUsedCount(0);
        save(coupon);
        CouponVO vo = new CouponVO();
        BeanUtils.copyProperties(coupon, vo);
        return vo;
    }

    @Override
    public CouponVO update(Long id, CouponDTO dto) {
        Coupon coupon = getById(id);
        if (coupon == null) {
            return null;
        }
        if (dto.getName() != null) {
            coupon.setName(dto.getName());
        }
        if (dto.getType() != null) {
            coupon.setType(dto.getType());
        }
        if (dto.getMinAmount() != null) {
            coupon.setMinAmount(dto.getMinAmount());
        }
        if (dto.getDiscountValue() != null) {
            coupon.setDiscountValue(dto.getDiscountValue());
        }
        if (dto.getStock() != null) {
            coupon.setStock(dto.getStock());
        }
        if (dto.getStartTime() != null) {
            coupon.setStartTime(dto.getStartTime());
        }
        if (dto.getEndTime() != null) {
            coupon.setEndTime(dto.getEndTime());
        }
        if (dto.getMemberLevelId() != null) {
            coupon.setMemberLevelId(dto.getMemberLevelId());
        }
        if (dto.getStatus() != null) {
            coupon.setStatus(dto.getStatus());
        }
        updateById(coupon);
        CouponVO vo = new CouponVO();
        BeanUtils.copyProperties(coupon, vo);
        return vo;
    }

    @Override
    public Boolean delete(Long id) {
        return removeById(id);
    }
}

