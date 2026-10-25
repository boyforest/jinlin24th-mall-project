package com.jinlin24th.jinlin.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CoffeeAdminContextVO {
    private List<Store> stores;
    private List<String> permissions;

    @Data
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Store {
        private String id;
        private String name;
    }
}
