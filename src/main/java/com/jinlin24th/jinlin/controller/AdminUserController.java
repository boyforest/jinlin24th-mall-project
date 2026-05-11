package com.jinlin24th.jinlin.controller;


import com.baomidou.mybatisplus.core.metadata.IPage;
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
            @RequestParam(required = false)Integer status
    ) {
        return Result.success(appUserService.adminPage(page, size, status));
    }
    @GetMapping("/{id}")
    public Result<AppUserVO> get(@PathVariable Long id) {
        AppUserVO vo = appUserService.getUserInfo(id);
        if (vo == null) {
            return Result.error(404, "用户不存在");
        }
        return Result.success(vo);
    }

    @PutMapping("/status")
    public Result<AppUserVO> updateStatus(@RequestParam Long id, @RequestParam Integer status) {
        return Result.success(appUserService.updateStatus(id, status));
    }


}
