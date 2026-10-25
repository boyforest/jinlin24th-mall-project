package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jinlin24th.jinlin.common.coffee.StrictIntegerDeserializer;
import com.jinlin24th.jinlin.common.coffee.StrictStringDeserializer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.Data;

import java.util.List;

@Data
public class CoffeeOrderCreateDTO extends CoffeeRequestDTO {
    @NotBlank
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String storeId;

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}")
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String requestKey;

    @NotBlank
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String menuVersion;

    @NotEmpty
    @Size(max = 100)
    @Valid
    private List<@NotNull Item> items;

    @Size(max = 255)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String remark;

    @Data
    public static class Item extends CoffeeRequestDTO {
        @NotBlank
        @JsonDeserialize(using = StrictStringDeserializer.class)
        private String skuId;

        @NotNull
        @Min(1)
        @Max(99)
        @JsonDeserialize(using = StrictIntegerDeserializer.class)
        private Integer quantity;
    }
}
