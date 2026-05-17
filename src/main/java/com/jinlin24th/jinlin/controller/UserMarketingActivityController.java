package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.vo.MarketingActivityVO;
import com.jinlin24th.jinlin.service.MarketingActivityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user/marketing/activity")
public class UserMarketingActivityController {

    private final MarketingActivityService marketingActivityService;

    public UserMarketingActivityController(MarketingActivityService marketingActivityService) {
        this.marketingActivityService = marketingActivityService;
    }

    @GetMapping("/list")
    public Result<List<MarketingActivityVO>> list(@RequestParam(required = false) String position) {
        return Result.success(marketingActivityService.activeList(position));
    }
}
