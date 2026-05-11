package com.jinlin24th.jinlin.pojo.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class AdminLoginVO implements Serializable {
    private String token;
    private String username;
}
