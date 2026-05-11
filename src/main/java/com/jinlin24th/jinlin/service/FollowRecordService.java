package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.pojo.dto.FollowRecordDTO;
import com.jinlin24th.jinlin.pojo.entity.FollowRecord;
import com.jinlin24th.jinlin.pojo.vo.FollowRecordVO;

public interface FollowRecordService extends IService<FollowRecord> {
    IPage<FollowRecordVO> adminPage(long page, long size, Long customerId);

    FollowRecordVO getVO(Long id);

    FollowRecordVO create(Long adminId, FollowRecordDTO dto);

    Boolean delete(Long id);
}


