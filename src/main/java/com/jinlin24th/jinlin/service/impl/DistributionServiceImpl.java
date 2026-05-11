package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.mapper.DistributionMapper;
import com.jinlin24th.jinlin.pojo.entity.Distribution;
import com.jinlin24th.jinlin.pojo.vo.DistributionVO;
import com.jinlin24th.jinlin.service.DistributionService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class DistributionServiceImpl extends ServiceImpl<DistributionMapper, Distribution>
        implements DistributionService {

    @Override
    public IPage<DistributionVO> adminPage(long page, long size, Integer status) {
        // 管理端分页：按状态筛选分销记录
        Page<Distribution> p = new Page<>(page, size);
        IPage<Distribution> entityPage = lambdaQuery()
            .eq(status != null, Distribution::getStatus, status)
            .orderByDesc(Distribution::getId)
            .page(p);
        Page<DistributionVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(e -> {
            DistributionVO vo = new DistributionVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Distribution getRequired(Long id) {
        // 简化实现：直接按 id 查询；后续可改成“不存在则抛业务异常”
        return getById(id);
    }

    @Override
    public Distribution settle(Long id) {
        // 结算：只有 status=1（待结算/可结算）才允许变更为 2（已结算）
        Distribution distribution = getById(id);
        if (distribution == null) {
            return null;
        }
        if (distribution.getStatus() == null || distribution.getStatus() != 1) {
            return null;
        }
        distribution.setStatus(2);
        distribution.setSettleTime(LocalDateTime.now());
        updateById(distribution);
        return distribution;
    }

}
