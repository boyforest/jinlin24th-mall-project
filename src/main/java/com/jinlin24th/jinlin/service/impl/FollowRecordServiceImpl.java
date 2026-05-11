package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.dto.FollowRecordDTO;
import com.jinlin24th.jinlin.pojo.entity.FollowRecord;
import com.jinlin24th.jinlin.mapper.FollowRecordMapper;
import com.jinlin24th.jinlin.pojo.vo.FollowRecordVO;
import com.jinlin24th.jinlin.service.FollowRecordService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class FollowRecordServiceImpl extends ServiceImpl<FollowRecordMapper, FollowRecord>
    implements FollowRecordService {

    @Override
    public IPage<FollowRecordVO> adminPage(long page, long size, Long customerId) {
        Page<FollowRecord> p = new Page<>(page, size);
        IPage<FollowRecord> entityPage = lambdaQuery()
            .eq(customerId != null, FollowRecord::getCustomerId, customerId)
            .orderByDesc(FollowRecord::getId)
            .page(p);
        Page<FollowRecordVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(e -> {
            FollowRecordVO vo = new FollowRecordVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public FollowRecordVO getVO(Long id) {
        FollowRecord record = getById(id);
        if (record == null) {
            return null;
        }
        FollowRecordVO vo = new FollowRecordVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
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
        FollowRecordVO vo = new FollowRecordVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }

    @Override
    public Boolean delete(Long id) {
        return removeById(id);
    }
}


