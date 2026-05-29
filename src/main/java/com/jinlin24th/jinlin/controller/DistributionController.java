package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.DistributionConfigDTO;
import com.jinlin24th.jinlin.pojo.entity.Distribution;
import com.jinlin24th.jinlin.pojo.vo.DistributionVO;
import com.jinlin24th.jinlin.pojo.vo.DistributionConfigVO;
import com.jinlin24th.jinlin.service.DistributionConfigService;
import com.jinlin24th.jinlin.service.DistributionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

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
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) String orderNo,
        @RequestParam(required = false) String keyword
    ) {
        return Result.success(distributionService.adminPage(page, size, status, orderNo, keyword));
    }

    @GetMapping("/{id}")
    public Result<DistributionVO> getById(@PathVariable Long id) {
        DistributionVO distribution = distributionService.getDetail(id);
        if (distribution == null) {
            throw BizException.of(BizCode.DISTRIBUTION_NOT_FOUND);
        }
        return Result.success(distribution);
    }

    @PutMapping("/settle")
    public Result<Distribution> settle(@RequestParam Long id) {
        Distribution distribution = distributionService.settle(id);
        if (distribution == null) {
            throw BizException.of(BizCode.DISTRIBUTION_SETTLE_FAILED);
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

    /**
     * 导出佣金记录 CSV。
     * <p>
     * 示例：GET /admin/distribution/export?status=1
     */
    @GetMapping("/export")
    public void export(
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) String orderNo,
        @RequestParam(required = false) String keyword,
        HttpServletResponse response
    ) throws IOException {
        distributionService.exportCsv(response, status, orderNo, keyword);
    }
}
