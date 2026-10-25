package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jinlin24th.jinlin.common.coffee.StrictStringDeserializer;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class AdminAccountProfileDTO extends CoffeeRequestDTO {
    @Size(max = 64)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String realName;
}
