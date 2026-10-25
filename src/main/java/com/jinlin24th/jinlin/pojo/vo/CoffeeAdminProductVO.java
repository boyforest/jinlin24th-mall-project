package com.jinlin24th.jinlin.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CoffeeAdminProductVO {
    private String id;
    private String categoryId;
    private String name;
    private String imageUrl;
    private String description;
    private Integer sort;
    private Integer status;
    private List<Sku> skus;

    @Data
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Sku {
        private String id;
        private String productId;
        private String name;
        private String temperature;
        private String sugar;
        private Long priceCent;
        private Integer status;
        private Boolean available;
    }
}
