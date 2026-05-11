package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class FollowRecordVO implements Serializable {
    private Long id;
    private Long customerId;
    private Long adminId;
    private String content;
    private LocalDateTime nextTime;
    private Integer type;
    private LocalDateTime createTime;
}

