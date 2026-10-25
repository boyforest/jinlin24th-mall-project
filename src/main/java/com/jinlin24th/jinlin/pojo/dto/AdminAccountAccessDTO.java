package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jinlin24th.jinlin.common.coffee.StrictStringDeserializer;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class AdminAccountAccessDTO extends CoffeeRequestDTO {
    @NotBlank
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String roleCode;

    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String storeId;
}
