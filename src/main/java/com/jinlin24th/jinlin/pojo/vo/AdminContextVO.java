package com.jinlin24th.jinlin.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AdminContextVO {
    private String adminId;
    private String username;
    private String realName;
    private List<String> roles;
    private List<String> permissions;
    private List<CoffeeAdminContextVO.Store> stores;
    private String defaultRoute;
}
