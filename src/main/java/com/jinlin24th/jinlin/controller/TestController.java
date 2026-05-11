package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("Hello, 閲戦湒浜屽崄鍥涘吇!");
    }
}

