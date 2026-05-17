package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.dto.MarketingActivityDTO;
import com.jinlin24th.jinlin.pojo.entity.MarketingActivity;
import com.jinlin24th.jinlin.pojo.vo.MarketingActivityVO;

import java.util.List;

public interface MarketingActivityService extends IService<MarketingActivity> {

    IPage<MarketingActivityVO> adminPage(long page, long size, Integer status, String position);

    MarketingActivityVO getVO(Long id);

    MarketingActivityVO create(MarketingActivityDTO dto);

    MarketingActivityVO update(Long id, MarketingActivityDTO dto);

    Boolean delete(Long id);

    List<MarketingActivityVO> activeList(String position);
}
