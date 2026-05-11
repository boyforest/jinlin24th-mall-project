package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.dto.DistributionConfigDTO;
import com.jinlin24th.jinlin.pojo.entity.DistributionConfig;
import com.jinlin24th.jinlin.mapper.DistributionConfigMapper;
import com.jinlin24th.jinlin.pojo.vo.DistributionConfigVO;
import com.jinlin24th.jinlin.service.DistributionConfigService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
public class DistributionConfigServiceImpl extends ServiceImpl<DistributionConfigMapper, DistributionConfig>
    implements DistributionConfigService {

    @Override
    public DistributionConfig getCurrentConfig() {
        return lambdaQuery().orderByAsc(DistributionConfig::getId).last("limit 1").one();
    }

    @Override
    public DistributionConfig updateCurrentConfig(DistributionConfigDTO dto) {
        DistributionConfig config = getCurrentConfig();
        if (config == null) {
            config = new DistributionConfig();
            config.setLevel1Rate(dto.getLevel1Rate());
            config.setLevel2Rate(dto.getLevel2Rate());
            config.setMinWithdraw(dto.getMinWithdraw());
            config.setSettleDays(dto.getSettleDays());
            config.setStatus(dto.getStatus());
            save(config);
            return config;
        }
        if (dto.getLevel1Rate() != null) {
            config.setLevel1Rate(dto.getLevel1Rate());
        }
        if (dto.getLevel2Rate() != null) {
            config.setLevel2Rate(dto.getLevel2Rate());
        }
        if (dto.getMinWithdraw() != null) {
            config.setMinWithdraw(dto.getMinWithdraw());
        }
        if (dto.getSettleDays() != null) {
            config.setSettleDays(dto.getSettleDays());
        }
        if (dto.getStatus() != null) {
            config.setStatus(dto.getStatus());
        }
        updateById(config);
        return config;
    }

    @Override
    public DistributionConfigVO getVO() {
        DistributionConfig config = getCurrentConfig();
        DistributionConfigVO vo = new DistributionConfigVO();
        if (config != null) {
            BeanUtils.copyProperties(config, vo);
        }
        return vo;
    }

    @Override
    public DistributionConfigVO updateVO(DistributionConfigDTO dto) {
        DistributionConfig config = updateCurrentConfig(dto);
        DistributionConfigVO vo = new DistributionConfigVO();
        if (config != null) {
            BeanUtils.copyProperties(config, vo);
        }
        return vo;
    }

}
