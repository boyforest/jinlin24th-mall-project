package com.jinlin24th.jinlin.pojo.dto;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class BusinessOrderQueryDTO {
    @NotNull
    @Min(1)
    private Integer page = 1;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer size = 20;

    private String businessType;
    private String statusGroup = "ALL";
    private String status;
}
