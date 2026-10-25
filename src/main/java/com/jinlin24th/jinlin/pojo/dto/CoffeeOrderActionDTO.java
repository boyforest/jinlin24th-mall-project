package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jinlin24th.jinlin.common.coffee.StrictLongDeserializer;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class CoffeeOrderActionDTO extends CoffeeRequestDTO {
    @NotNull
    @Min(0)
    @Max(9007199254740991L)
    @JsonDeserialize(using = StrictLongDeserializer.class)
    private Long expectedVersion;
}
