package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.entity.MemberLevel;
import com.jinlin24th.jinlin.mapper.MemberLevelMapper;
import com.jinlin24th.jinlin.service.MemberLevelService;
import org.springframework.stereotype.Service;

@Service
public class MemberLevelServiceImpl extends ServiceImpl<MemberLevelMapper, MemberLevel>
    implements MemberLevelService {
}


