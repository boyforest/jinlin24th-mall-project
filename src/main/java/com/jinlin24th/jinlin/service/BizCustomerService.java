package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.dto.CustomerDTO;
import com.jinlin24th.jinlin.pojo.entity.BizCustomer;
import com.jinlin24th.jinlin.pojo.vo.CustomerVO;

public interface BizCustomerService extends IService<BizCustomer> {
    IPage<CustomerVO> adminPage(long page, long size, Integer status, Long adminId, String keyword);

    CustomerVO getVO(Long id);

    CustomerVO create(CustomerDTO dto);

    CustomerVO update(Long id, CustomerDTO dto);

    Boolean delete(Long id);
}

