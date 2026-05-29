package com.jinlin24th.jinlin.controller;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.vo.AppUserVO;
import com.jinlin24th.jinlin.service.AppUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/user")
@Slf4j
public class AdminUserController {
    @Autowired private AppUserService appUserService;

    @GetMapping("/list")
    public Result<IPage<AppUserVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer isDistributor,
            @RequestParam(required = false) String keyword
    ) {
        return Result.success(appUserService.adminPage(page, size, status, isDistributor, keyword));
    }
    @GetMapping("/{id}")
    public Result<AppUserVO> get(@PathVariable Long id) {
        AppUserVO vo = appUserService.getUserInfo(id);
        if (vo == null) {
            throw BizException.of(BizCode.USER_NOT_FOUND);
        }
        return Result.success(vo);
    }

    @PutMapping("/status")
    public Result<AppUserVO> updateStatus(@RequestParam Long id, @RequestParam Integer status) {
        return Result.success(appUserService.updateStatus(id, status));
    }

    /**
     * 管理端开关用户分销资格。
     * <p>
     * 兼容当前后台风格：PUT /admin/user/distributor?id=1&enabled=1
     */
    @PutMapping("/distributor")
    public Result<AppUserVO> updateDistributor(@RequestParam Long id, @RequestParam Integer enabled) {
        AppUserVO vo = appUserService.updateDistributor(id, enabled);
        if (vo == null) {
            throw BizException.of(BizCode.USER_NOT_FOUND);
        }
        return Result.success(vo);
    }

}
