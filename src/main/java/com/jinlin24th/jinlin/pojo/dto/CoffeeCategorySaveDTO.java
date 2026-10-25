package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jinlin24th.jinlin.common.coffee.*;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CoffeeCategorySaveDTO extends CoffeeRequestDTO {
    @NotBlank
    @Size(max = 64)
    private String name;

    @NotNull
    @Min(0)
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer sort;

    @NotNull
    @Min(0)
    @Max(1)
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer status;
}
