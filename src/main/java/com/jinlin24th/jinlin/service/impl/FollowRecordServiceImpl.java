package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.dto.FollowRecordDTO;
import com.jinlin24th.jinlin.pojo.entity.BizCustomer;
import com.jinlin24th.jinlin.pojo.entity.FollowRecord;
import com.jinlin24th.jinlin.mapper.FollowRecordMapper;
import com.jinlin24th.jinlin.pojo.entity.SysAdmin;
import com.jinlin24th.jinlin.pojo.vo.FollowRecordVO;
import com.jinlin24th.jinlin.service.BizCustomerService;
import com.jinlin24th.jinlin.service.FollowRecordService;
import com.jinlin24th.jinlin.service.SysAdminService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class FollowRecordServiceImpl extends ServiceImpl<FollowRecordMapper, FollowRecord>
    implements FollowRecordService {

    private final BizCustomerService bizCustomerService;
    private final SysAdminService sysAdminService;

    public FollowRecordServiceImpl(BizCustomerService bizCustomerService, SysAdminService sysAdminService) {
        this.bizCustomerService = bizCustomerService;
        this.sysAdminService = sysAdminService;
    }

    @Override
    public IPage<FollowRecordVO> adminPage(long page, long size, Long customerId) {
        Page<FollowRecord> p = new Page<>(page, size);
        IPage<FollowRecord> entityPage = lambdaQuery()
            .eq(customerId != null, FollowRecord::getCustomerId, customerId)
            .orderByDesc(FollowRecord::getId)
            .page(p);
        Page<FollowRecordVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(toVOList(entityPage.getRecords()));
        return voPage;
    }

    @Override
    public FollowRecordVO getVO(Long id) {
        FollowRecord record = getById(id);
        if (record == null) {
            return null;
        }
        return toVOList(Collections.singletonList(record)).get(0);
    }

    @Override
    public FollowRecordVO create(Long adminId, FollowRecordDTO dto) {
        FollowRecord record = new FollowRecord();
        BeanUtils.copyProperties(dto, record);
        record.setAdminId(adminId);
        if (record.getCreateTime() == null) {
            record.setCreateTime(LocalDateTime.now());
        }
        save(record);
        return toVOList(Collections.singletonList(record)).get(0);
    }

    @Override
    public Boolean delete(Long id) {
        return removeById(id);
    }

    private java.util.List<FollowRecordVO> toVOList(java.util.List<FollowRecord> records) {
        Set<Long> customerIds = records.stream().map(FollowRecord::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> adminIds = records.stream().map(FollowRecord::getAdminId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> customerNames = customerIds.isEmpty()
            ? Collections.emptyMap()
            : bizCustomerService.listByIds(customerIds).stream().collect(Collectors.toMap(BizCustomer::getId, BizCustomer::getName, (a, b) -> a));
        Map<Long, String> adminNames = adminIds.isEmpty()
            ? Collections.emptyMap()
            : sysAdminService.listByIds(adminIds).stream().collect(Collectors.toMap(SysAdmin::getId, this::adminDisplayName, (a, b) -> a));

        return records.stream().map(e -> {
            FollowRecordVO vo = new FollowRecordVO();
            BeanUtils.copyProperties(e, vo);
            vo.setCustomerName(customerNames.get(e.getCustomerId()));
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

