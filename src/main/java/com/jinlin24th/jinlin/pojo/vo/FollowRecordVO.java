package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class FollowRecordVO implements Serializable {
    private Long id;
    private Long customerId;
    private String customerName;
    private Long adminId;
    private String adminName;
    private String content;
    private LocalDateTime nextTime;
    private Integer type;
    private LocalDateTime createTime;
}
