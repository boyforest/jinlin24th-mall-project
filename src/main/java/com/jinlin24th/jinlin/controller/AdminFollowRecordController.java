package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.auth.CurrentAdminId;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.FollowRecordDTO;
import com.jinlin24th.jinlin.pojo.vo.FollowRecordVO;
import com.jinlin24th.jinlin.service.FollowRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/followRecord")
public class AdminFollowRecordController {

    @Autowired
    private FollowRecordService followRecordService;

    @GetMapping("/list")
    public Result<IPage<FollowRecordVO>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long size,
        @RequestParam(required = false) Long customerId
    ) {
        return Result.success(followRecordService.adminPage(page, size, customerId));
    }

    @GetMapping("/{id}")
    public Result<FollowRecordVO> get(@PathVariable Long id) {
        FollowRecordVO vo = followRecordService.getVO(id);
        if (vo == null) {
            throw BizException.of(BizCode.FOLLOW_RECORD_NOT_FOUND);
        }
        return Result.success(vo);
    }

    @PostMapping
    public Result<FollowRecordVO> create(@RequestBody FollowRecordDTO dto, @CurrentAdminId Long adminId) {
        FollowRecordVO vo = followRecordService.create(adminId, dto);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(followRecordService.delete(id));
    }
}
