package com.jinlin24th.jinlin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinlin24th.jinlin.common.mq.dto.OperationLogMessageDTO;
import com.jinlin24th.jinlin.mapper.SysOperationLogMapper;
import com.jinlin24th.jinlin.pojo.entity.SysOperationLog;
import com.jinlin24th.jinlin.service.SysOperationLogService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * 系统操作日志 Service 实现。
 */
@Service
public class SysOperationLogServiceImpl extends ServiceImpl<SysOperationLogMapper, SysOperationLog>
    implements SysOperationLogService {

    /**
     * 将 MQ 日志消息转换为实体并落库。
     */
    @Override
    public void saveFromMessage(OperationLogMessageDTO message) {
        SysOperationLog entity = new SysOperationLog();
        BeanUtils.copyProperties(message, entity);
        save(entity);
    }
}
