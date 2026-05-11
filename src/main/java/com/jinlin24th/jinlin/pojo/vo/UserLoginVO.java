package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginVO implements Serializable {
    private String token;
    private Long userId;
}

