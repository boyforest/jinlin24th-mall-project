package com.jinlin24th.jinlin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.CustomerDTO;
import com.jinlin24th.jinlin.pojo.vo.CustomerVO;
import com.jinlin24th.jinlin.service.BizCustomerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/customer")
public class AdminCustomerController {

    @Autowired
    private BizCustomerService bizCustomerService;

    @GetMapping("/list")
    public Result<IPage<CustomerVO>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "10") long size,
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) Long adminId
    ) {
        return Result.success(bizCustomerService.adminPage(page, size, status, adminId));
    }

    @GetMapping("/{id}")
    public Result<CustomerVO> get(@PathVariable Long id) {
        CustomerVO vo = bizCustomerService.getVO(id);
        if (vo == null) {
            return Result.error(404, "客户不存在");
        }
        return Result.success(vo);
    }

    @PostMapping
    public Result<CustomerVO> create(@RequestBody CustomerDTO dto) {
        if(dto.getAdminId()== null){
            return Result.error(400, "请指定销售人员");
        }
        return Result.success(bizCustomerService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<CustomerVO> update(@PathVariable Long id, @RequestBody CustomerDTO dto) {
        CustomerVO vo = bizCustomerService.update(id, dto);
        if (vo == null) {
            return Result.error(404, "客户不存在");
        }
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(bizCustomerService.delete(id));
    }
}
