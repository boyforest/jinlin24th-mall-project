package com.jinlin24th.jinlin.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AdminAccountVO {
    private String id;
    private String username;
    private String realName;
    private List<String> roles;
    private String storeId;
    private String storeName;
    private Integer status;
    private Boolean mustChangePwd;
    private String lastLoginTime;
}
