package com.jinlin24th.jinlin.pojo.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ProductDTO implements Serializable {
    private Long categoryId;
    private String name;
    private String subtitle;
    private String mainImage;
    private String images;
    private String videoUrl;
    private String detail;
    private String effects;
    private String precautions;
    private Integer status;
    private Integer sort;
    private BigDecimal price;
}
