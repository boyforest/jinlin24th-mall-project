package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.dto.CustomerDTO;
import com.jinlin24th.jinlin.pojo.entity.BizCustomer;
import com.jinlin24th.jinlin.mapper.BizCustomerMapper;
import com.jinlin24th.jinlin.pojo.vo.CustomerVO;
import com.jinlin24th.jinlin.service.BizCustomerService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Service
public class BizCustomerServiceImpl extends ServiceImpl<BizCustomerMapper, BizCustomer>
    implements BizCustomerService {

    @Override
    public IPage<CustomerVO> adminPage(long page, long size, Integer status, Long adminId) {
        Page<BizCustomer> p = new Page<>(page, size);
        IPage<BizCustomer> entityPage = lambdaQuery()
            .eq(status != null, BizCustomer::getStatus, status)
            .eq(adminId != null, BizCustomer::getAdminId, adminId)
            .orderByDesc(BizCustomer::getId)
            .page(p);
        Page<CustomerVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(e -> {
            CustomerVO vo = new CustomerVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public CustomerVO getVO(Long id) {
        BizCustomer customer = getById(id);
        if (customer == null) {
            return null;
        }
        CustomerVO vo = new CustomerVO();
        BeanUtils.copyProperties(customer, vo);
        return vo;
    }

    @Override
    public CustomerVO create(CustomerDTO dto) {
        BizCustomer customer = new BizCustomer();
        BeanUtils.copyProperties(dto, customer);
        customer.setDeleted(0);
        customer.setTotalAmount(BigDecimal.ZERO);
        customer.setOrderCount(0);
        save(customer);
        CustomerVO vo = new CustomerVO();
        BeanUtils.copyProperties(customer, vo);
        return vo;
    }

    @Override
    public CustomerVO update(Long id, CustomerDTO dto) {
        BizCustomer customer = getById(id);
        if (customer == null) {
            return null;
        }
        if (dto.getName() != null) {
            customer.setName(dto.getName());
        }
        if (dto.getContactName() != null) {
            customer.setContactName(dto.getContactName());
        }
        if (dto.getContactPhone() != null) {
            customer.setContactPhone(dto.getContactPhone());
        }
        if (dto.getSource() != null) {
            customer.setSource(dto.getSource());
        }
        if (dto.getLevel() != null) {
            customer.setLevel(dto.getLevel());
        }
        if (dto.getAdminId() != null) {
            customer.setAdminId(dto.getAdminId());
        }
        if (dto.getTags() != null) {
            customer.setTags(dto.getTags());
        }
        if (dto.getRemark() != null) {
            customer.setRemark(dto.getRemark());
        }
        if (dto.getStatus() != null) {
            customer.setStatus(dto.getStatus());
        }
        updateById(customer);
        CustomerVO vo = new CustomerVO();
        BeanUtils.copyProperties(customer, vo);
        return vo;
    }

    @Override
    public Boolean delete(Long id) {
        return removeById(id);
    }
}


