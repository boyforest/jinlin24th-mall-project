package com.jinlin24th.jinlin.service;

import com.jinlin24th.jinlin.pojo.dto.BusinessOrderQueryDTO;
import com.jinlin24th.jinlin.pojo.vo.BusinessOrderPageVO;

public interface BusinessOrderQueryService {
    BusinessOrderPageVO list(Long userId, BusinessOrderQueryDTO query);
}
