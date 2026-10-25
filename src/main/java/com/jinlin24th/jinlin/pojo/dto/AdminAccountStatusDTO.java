package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jinlin24th.jinlin.common.coffee.StrictIntegerDeserializer;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class AdminAccountStatusDTO extends CoffeeRequestDTO {
    @NotNull
    @Min(0)
    @Max(1)
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer status;
}
