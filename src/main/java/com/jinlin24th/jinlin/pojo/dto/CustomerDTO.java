package com.jinlin24th.jinlin.pojo.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CustomerDTO implements Serializable {
    private String name;
    private String contactName;
    private String contactPhone;
    private Integer source;
    private Integer level;
    private Long adminId;
    private String tags;
    private String remark;
    private Integer status;
}

