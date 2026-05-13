package com.jinlin24th.jinlin.controller;

import com.jinlin24th.jinlin.common.auth.CurrentUserId;
import com.jinlin24th.jinlin.common.constant.BizCode;
import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.result.Result;
import com.jinlin24th.jinlin.pojo.dto.CartDTO;
import com.jinlin24th.jinlin.pojo.vo.CartVO;
import com.jinlin24th.jinlin.service.CartAppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user/cart")
public class UserCartController {

    @Autowired
    private CartAppService cartAppService;

    @GetMapping("/list")
    public Result<List<CartVO>> list(@CurrentUserId Long userId) {
        return Result.success(cartAppService.list(userId));
    }

    @PostMapping
    public Result<CartVO> add(@CurrentUserId Long userId, @RequestBody CartDTO dto) {
        CartVO vo = cartAppService.add(userId, dto);
        if (vo == null) {
            throw BizException.of(BizCode.CART_ADD_FAILED);
        }
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    public Result<CartVO> update(@CurrentUserId Long userId, @PathVariable Long id, @RequestBody CartDTO dto) {
        CartVO vo = cartAppService.update(userId, id, dto);
        if (vo == null) {
            throw BizException.of(BizCode.CART_RECORD_NOT_FOUND);
        }
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@CurrentUserId Long userId, @PathVariable Long id) {
        Boolean ok = cartAppService.delete(userId, id);
        if (!ok) {
            throw BizException.of(BizCode.CART_RECORD_NOT_FOUND);
        }
        return Result.success(true);
    }
}
