package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jinlin24th.jinlin.common.coffee.StrictStringDeserializer;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.ToString;

@Data
public class AdminAccountSaveDTO extends CoffeeRequestDTO {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_.-]{3,64}")
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String username;

    @Size(max = 64)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String realName;

    @NotBlank
    @Size(min = 8, max = 72)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @ToString.Exclude
    private String temporaryPassword;

    @NotBlank
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String roleCode;

    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String storeId;
}
