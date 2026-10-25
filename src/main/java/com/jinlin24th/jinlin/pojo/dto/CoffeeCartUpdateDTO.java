package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jinlin24th.jinlin.common.coffee.*;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CoffeeCartUpdateDTO extends CoffeeRequestDTO {
    @Min(1)
    @Max(99)
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer quantity;

    @Min(0)
    @Max(1)
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer checked;

    @NotNull
    @Min(0)
    @Max(9007199254740991L)
    @JsonDeserialize(using = StrictLongDeserializer.class)
    private Long expectedVersion;
}
