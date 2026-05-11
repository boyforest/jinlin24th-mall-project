package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.pojo.entity.OrderMaster;
import com.jinlin24th.jinlin.mapper.OrderMasterMapper;
import com.jinlin24th.jinlin.service.OrderMasterService;
import org.springframework.stereotype.Service;

@Service
public class OrderMasterServiceImpl extends ServiceImpl<OrderMasterMapper, OrderMaster>
    implements OrderMasterService {
}


