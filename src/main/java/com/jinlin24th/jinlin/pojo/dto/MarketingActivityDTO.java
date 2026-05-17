package com.jinlin24th.jinlin.pojo.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class MarketingActivityDTO implements Serializable {
    private String title;
    private String subtitle;
    private String imageUrl;
    private String content;
    private String position;
    private String linkType;
    private String linkValue;
    private Integer status;
    private Integer sort;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
