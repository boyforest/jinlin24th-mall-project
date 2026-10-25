package com.jinlin24th.jinlin.pojo.dto;

import com.jinlin24th.jinlin.common.coffee.*;

import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CoffeeStoreSaveDTO extends CoffeeRequestDTO {
    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String address;

    @NotBlank
    @Size(max = 255)
    private String openingHours;

    @NotNull
    @Pattern(regexp = "OPEN|CLOSED|PAUSED")
    private String businessStatus;

    @Size(max = 1000)
    private String announcement;
}
