package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ProductVO implements Serializable {
    private Long id;
    private Long categoryId;
    private String name;
    private String subtitle;
    private String mainImage;
    private String images;
    private String videoUrl;
    private String detail;
    private Integer sales;
    private Integer status;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

