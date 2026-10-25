package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jinlin24th.jinlin.common.coffee.*;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CoffeeCartSelectionDTO extends CoffeeRequestDTO {
    @NotBlank
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String storeId;

    @NotNull
    @Min(0)
    @Max(1)
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer checked;
}
