package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.result.Result;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
@Profile("dev")

public class TestController {

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("Hello, 金霖二十四养!");
    }
}

