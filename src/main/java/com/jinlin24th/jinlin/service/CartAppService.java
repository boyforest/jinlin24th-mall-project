package com.jinlin24th.jinlin.service;

import com.jinlin24th.jinlin.pojo.dto.CartDTO;
import com.jinlin24th.jinlin.pojo.vo.CartVO;

import java.util.List;

public interface CartAppService {
    List<CartVO> list(Long userId);

    CartVO add(Long userId, CartDTO dto);

    CartVO update(Long userId, Long id, CartDTO dto);

    Boolean delete(Long userId, Long id);
}

