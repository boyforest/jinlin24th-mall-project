package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.entity.Distribution;
import com.jinlin24th.jinlin.pojo.vo.DistributionVO;

public interface DistributionService extends IService<Distribution> {
    IPage<DistributionVO> adminPage(long page, long size, Integer status);

    Distribution getRequired(Long id);

    Distribution settle(Long id);
}
