package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jinlin24th.jinlin.common.coffee.StrictStringDeserializer;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class CoffeeOrderRefundDTO extends CoffeeOrderActionDTO {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}")
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String requestKey;

    @NotBlank
    @Size(max = 255)
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String reason;
}
