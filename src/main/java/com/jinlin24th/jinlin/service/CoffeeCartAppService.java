package com.jinlin24th.jinlin.service;

import com.jinlin24th.jinlin.pojo.dto.*;
import com.jinlin24th.jinlin.pojo.vo.CoffeeCartVO;

import java.util.List;

public interface CoffeeCartAppService {
    List<CoffeeCartVO> list(Long userId, Long storeId);

    CoffeeCartVO add(Long userId, CoffeeCartAddDTO dto);

    CoffeeCartVO update(Long userId, Long id, CoffeeCartUpdateDTO dto);

    List<CoffeeCartVO> selection(Long userId, CoffeeCartSelectionDTO dto);

    boolean delete(Long userId, Long id);
}
