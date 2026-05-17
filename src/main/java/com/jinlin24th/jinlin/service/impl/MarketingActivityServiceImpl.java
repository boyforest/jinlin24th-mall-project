package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.mapper.MarketingActivityMapper;
import com.jinlin24th.jinlin.pojo.dto.MarketingActivityDTO;
import com.jinlin24th.jinlin.pojo.entity.MarketingActivity;
import com.jinlin24th.jinlin.pojo.vo.MarketingActivityVO;
import com.jinlin24th.jinlin.service.MarketingActivityService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MarketingActivityServiceImpl extends ServiceImpl<MarketingActivityMapper, MarketingActivity>
    implements MarketingActivityService {

    @Override
    public IPage<MarketingActivityVO> adminPage(long page, long size, Integer status, String position) {
        Page<MarketingActivity> p = new Page<>(page, size);
        IPage<MarketingActivity> entityPage = lambdaQuery()
            .eq(status != null, MarketingActivity::getStatus, status)
            .eq(position != null && !position.isBlank(), MarketingActivity::getPosition, position)
            .orderByDesc(MarketingActivity::getSort)
            .orderByDesc(MarketingActivity::getId)
            .page(p);
        Page<MarketingActivityVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public MarketingActivityVO getVO(Long id) {
        MarketingActivity entity = getById(id);
        return entity == null ? null : toVO(entity);
    }

    @Override
    public MarketingActivityVO create(MarketingActivityDTO dto) {
        MarketingActivity entity = new MarketingActivity();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        if (entity.getSort() == null) {
            entity.setSort(0);
        }
        save(entity);
        return toVO(entity);
    }

    @Override
    public MarketingActivityVO update(Long id, MarketingActivityDTO dto) {
        MarketingActivity entity = getById(id);
        if (entity == null) {
            return null;
        }
        BeanUtils.copyProperties(dto, entity);
        updateById(entity);
        return toVO(entity);
    }

    @Override
    public Boolean delete(Long id) {
        return removeById(id);
    }

    @Override
    public List<MarketingActivityVO> activeList(String position) {
        LocalDateTime now = LocalDateTime.now();
        return lambdaQuery()
            .eq(MarketingActivity::getStatus, 1)
            .eq(position != null && !position.isBlank(), MarketingActivity::getPosition, position)
            .and(w -> w.isNull(MarketingActivity::getStartTime).or().le(MarketingActivity::getStartTime, now))
            .and(w -> w.isNull(MarketingActivity::getEndTime).or().ge(MarketingActivity::getEndTime, now))
            .orderByDesc(MarketingActivity::getSort)
            .orderByDesc(MarketingActivity::getId)
            .list()
            .stream()
            .map(this::toVO)
            .collect(Collectors.toList());
    }

    private MarketingActivityVO toVO(MarketingActivity entity) {
        MarketingActivityVO vo = new MarketingActivityVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
