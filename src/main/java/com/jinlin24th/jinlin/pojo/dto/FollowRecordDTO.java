package com.jinlin24th.jinlin.pojo.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class FollowRecordDTO implements Serializable {
    private Long customerId;
    private String content;
    private LocalDateTime nextTime;
    private Integer type;
}

