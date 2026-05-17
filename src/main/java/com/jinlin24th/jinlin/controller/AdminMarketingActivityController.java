package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.MarketingActivityDTO;
import com.jinlin24th.jinlin.pojo.vo.MarketingActivityVO;
import com.jinlin24th.jinlin.service.MarketingActivityService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/marketing/activity")
public class AdminMarketingActivityController {

    private final MarketingActivityService marketingActivityService;

    public AdminMarketingActivityController(MarketingActivityService marketingActivityService) {
        this.marketingActivityService = marketingActivityService;
    }

    @GetMapping("/list")
    public Result<IPage<MarketingActivityVO>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long size,
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) String position
    ) {
        return Result.success(marketingActivityService.adminPage(page, size, status, position));
    }

    @GetMapping("/{id}")
    public Result<MarketingActivityVO> get(@PathVariable Long id) {
        MarketingActivityVO vo = marketingActivityService.getVO(id);
        if (vo == null) {
            throw BizException.badRequest("活动不存在");
        }
        return Result.success(vo);
    }

    @PostMapping
    public Result<MarketingActivityVO> create(@RequestBody MarketingActivityDTO dto) {
        return Result.success(marketingActivityService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<MarketingActivityVO> update(@PathVariable Long id, @RequestBody MarketingActivityDTO dto) {
        MarketingActivityVO vo = marketingActivityService.update(id, dto);
        if (vo == null) {
            throw BizException.badRequest("活动不存在");
        }
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(marketingActivityService.delete(id));
    }
}
