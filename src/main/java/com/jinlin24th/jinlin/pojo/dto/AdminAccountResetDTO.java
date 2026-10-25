package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jinlin24th.jinlin.common.coffee.StrictStringDeserializer;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.ToString;

@Data
public class AdminAccountResetDTO extends CoffeeRequestDTO {
    @NotBlank
    @Size(min = 8, max = 72)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @ToString.Exclude
    private String temporaryPassword;
}
