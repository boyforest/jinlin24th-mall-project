package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.entity.BizCustomer;
import com.jinlin24th.jinlin.pojo.vo.CustomerVO;
import com.jinlin24th.jinlin.service.BizCustomerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequestMapping("user/bizcustomer")
public class BizCustomerController {
    @Autowired
    private BizCustomerService bizCustomerService;

    @GetMapping("/list")
    public Result<IPage<CustomerVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status
    ) {
        return Result.success(bizCustomerService.adminPage(page, size, status, null));
    }

    @GetMapping("/get/{id}")
    public Result<BizCustomer> get(@PathVariable Long id){
        BizCustomer bizCustomer = bizCustomerService.getById(id);
        if (bizCustomer == null) {
            return Result.error(404, "不存在此商户，请重新输入");
        }
        return Result.success(bizCustomer);
    }
}


