package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jinlin24th.jinlin.common.coffee.*;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CoffeeSkuSaveDTO extends CoffeeRequestDTO {
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String id;

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotNull
    @Pattern(regexp = "HOT|ICED")
    private String temperature;

    @NotNull
    @Pattern(regexp = "REGULAR|LESS|NONE")
    private String sugar;

    @NotNull
    @Min(1)
    @Max(9007199254740991L)
    @JsonDeserialize(using = StrictLongDeserializer.class)
    private Long priceCent;

    @NotNull
    @Min(0)
    @Max(1)
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer status;

    @NotNull
    @JsonDeserialize(using = StrictBooleanDeserializer.class)
    private Boolean available;
}
