package com.jinlin24th.jinlin.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CoffeeMenuVO {
    private String storeId;
    private String version;
    private List<Category> categories;
    private List<Product> products;

    @Data
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Category {
        private String id;
        private String name;
        private Integer sort;
    }

    @Data
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Product {
        private String id;
        private String categoryId;
        private String name;
        private String imageUrl;
        private String description;
        private Integer sort;
        private List<Sku> skus;
    }

    @Data
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Sku {
        private String id;
        private String productId;
        private String name;
        private String temperature;
        private String sugar;
        private Long priceCent;
        private Boolean available;
        private String unavailableReason;
    }
}
