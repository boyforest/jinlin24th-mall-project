package com.jinlin24th.jinlin.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CoffeeAdminMenuVO {
    private String storeId;
    private List<Category> categories;
    private List<CoffeeAdminProductVO> products;

    @Data
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Category {
        private String id;
        private String name;
        private Integer sort;
        private Integer status;
    }
}
