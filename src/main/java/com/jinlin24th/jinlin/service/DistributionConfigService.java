package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.dto.DistributionConfigDTO;
import com.jinlin24th.jinlin.pojo.entity.DistributionConfig;
import com.jinlin24th.jinlin.pojo.vo.DistributionConfigVO;

public interface DistributionConfigService extends IService<DistributionConfig> {
    DistributionConfig getCurrentConfig();

    DistributionConfig updateCurrentConfig(DistributionConfigDTO dto);

    DistributionConfigVO getVO();

    DistributionConfigVO updateVO(DistributionConfigDTO dto);
}

