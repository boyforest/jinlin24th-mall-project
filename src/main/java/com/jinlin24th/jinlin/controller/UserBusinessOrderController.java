package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.auth.CurrentUserId;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.BusinessOrderQueryDTO;
import com.jinlin24th.jinlin.pojo.vo.BusinessOrderPageVO;
import com.jinlin24th.jinlin.service.BusinessOrderQueryService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/business-order")
@RequiredArgsConstructor
public class UserBusinessOrderController {
    private final BusinessOrderQueryService service;

    @GetMapping("/list")
    public Result<BusinessOrderPageVO> list(
            @CurrentUserId Long userId, @Valid @ModelAttribute BusinessOrderQueryDTO query) {
        return Result.success(service.list(userId, query));
    }
}
