package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.dto.CustomerDTO;
import com.jinlin24th.jinlin.pojo.entity.BizCustomer;
import com.jinlin24th.jinlin.mapper.BizCustomerMapper;
import com.jinlin24th.jinlin.pojo.entity.SysAdmin;
import com.jinlin24th.jinlin.pojo.vo.CustomerVO;
import com.jinlin24th.jinlin.service.BizCustomerService;
import com.jinlin24th.jinlin.service.SysAdminService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BizCustomerServiceImpl extends ServiceImpl<BizCustomerMapper, BizCustomer>
    implements BizCustomerService {

    private final SysAdminService sysAdminService;

    public BizCustomerServiceImpl(SysAdminService sysAdminService) {
        this.sysAdminService = sysAdminService;
    }

    @Override
    public IPage<CustomerVO> adminPage(long page, long size, Integer status, Long adminId, String keyword) {
        Page<BizCustomer> p = new Page<>(page, size);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        IPage<BizCustomer> entityPage = lambdaQuery()
            .eq(status != null, BizCustomer::getStatus, status)
            .eq(adminId != null, BizCustomer::getAdminId, adminId)
            .and(StringUtils.hasText(normalizedKeyword), w -> w
                .like(BizCustomer::getName, normalizedKeyword)
                .or()
                .like(BizCustomer::getContactName, normalizedKeyword)
                .or()
                .like(BizCustomer::getContactPhone, normalizedKeyword)
            )
            .orderByDesc(BizCustomer::getId)
            .page(p);
        Page<CustomerVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(toVOList(entityPage.getRecords()));
        return voPage;
    }

    @Override
    public CustomerVO getVO(Long id) {
        BizCustomer customer = getById(id);
        if (customer == null) {
            return null;
        }
        return toVOList(Collections.singletonList(customer)).get(0);
    }

    @Override
    public CustomerVO create(CustomerDTO dto) {
        BizCustomer customer = new BizCustomer();
        BeanUtils.copyProperties(dto, customer);
        customer.setDeleted(0);
        customer.setTotalAmount(BigDecimal.ZERO);
        customer.setOrderCount(0);
        save(customer);
        return toVOList(Collections.singletonList(customer)).get(0);
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
        return toVOList(Collections.singletonList(customer)).get(0);
    }

    @Override
    public Boolean delete(Long id) {
        return removeById(id);
    }

    private java.util.List<CustomerVO> toVOList(java.util.List<BizCustomer> records) {
        Set<Long> adminIds = records.stream().map(BizCustomer::getAdminId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> adminNames = adminIds.isEmpty()
            ? Collections.emptyMap()
            : sysAdminService.listByIds(adminIds).stream().collect(Collectors.toMap(SysAdmin::getId, this::adminDisplayName, (a, b) -> a));

        return records.stream().map(e -> {
            CustomerVO vo = new CustomerVO();
            BeanUtils.copyProperties(e, vo);
            vo.setAdminName(adminNames.get(e.getAdminId()));
            return vo;
        }).collect(Collectors.toList());
    }

    private String adminDisplayName(SysAdmin admin) {
        if (admin == null) {
            return null;
        }
        if (StringUtils.hasText(admin.getRealName())) {
            return admin.getRealName();
        }
        return admin.getUsername();
    }
}
