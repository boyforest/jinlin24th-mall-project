package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.DistributionConfigDTO;
import com.jinlin24th.jinlin.pojo.entity.Distribution;
import com.jinlin24th.jinlin.pojo.vo.DistributionVO;
import com.jinlin24th.jinlin.pojo.vo.DistributionConfigVO;
import com.jinlin24th.jinlin.service.DistributionConfigService;
import com.jinlin24th.jinlin.service.DistributionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/distribution")
@Slf4j
public class DistributionController {
    @Autowired
    private DistributionService distributionService;

    @Autowired
    private DistributionConfigService distributionConfigService;

    @GetMapping("/list")
    public Result<com.baomidou.mybatisplus.core.metadata.IPage<DistributionVO>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long size,
        @RequestParam(required = false) Integer status
    ) {
        return Result.success(distributionService.adminPage(page, size, status));
    }

    @GetMapping("/{id}")
    public Result<Distribution> getById(@PathVariable Long id) {
        Distribution distribution = distributionService.getRequired(id);
        if (distribution == null) {
            return Result.error(404, "分销记录不存在");
        }
        return Result.success(distribution);
    }

    @PutMapping("/settle")
    public Result<Distribution> settle(@RequestParam Long id) {
        Distribution distribution = distributionService.settle(id);
        if (distribution == null) {
            return Result.error(400, "结算失败");
        }
        return Result.success(distribution);
    }

    @GetMapping("/config")
    public Result<DistributionConfigVO> getConfig() {
        return Result.success(distributionConfigService.getVO());
    }

    @PutMapping("/config")
    public Result<DistributionConfigVO> updateConfig(@RequestBody DistributionConfigDTO dto) {
        return Result.success(distributionConfigService.updateVO(dto));
    }
}
