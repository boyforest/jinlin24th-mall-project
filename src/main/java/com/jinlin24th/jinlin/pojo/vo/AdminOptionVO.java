package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class AdminOptionVO implements Serializable {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private Integer status;
}
