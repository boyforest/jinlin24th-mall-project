package com.jinlin24th.jinlin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jinlin24th.jinlin.common.mq.dto.OperationLogMessageDTO;
import com.jinlin24th.jinlin.pojo.entity.SysOperationLog;

/**
 * 系统操作日志 Service。
 */
public interface SysOperationLogService extends IService<SysOperationLog> {

    /**
     * 根据 MQ 消息写入操作日志。
     *
     * @param message 操作日志消息
     */
    void saveFromMessage(OperationLogMessageDTO message);
}
