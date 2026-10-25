package com.jinlin24th.jinlin.pojo.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jinlin24th.jinlin.common.coffee.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class CoffeeProductSaveDTO extends CoffeeRequestDTO {
    @NotBlank
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String categoryId;

    @NotBlank
    @Size(max = 128)
    private String name;

    @Size(max = 1024)
    @Pattern(regexp = "^(https://[^\\s]+|/uploads/[^\\s]+)$")
    private String imageUrl;

    @NotNull
    @Size(max = 2000)
    private String description;

    @NotNull
    @Min(0)
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer sort;

    @NotNull
    @Min(0)
    @Max(1)
    @JsonDeserialize(using = StrictIntegerDeserializer.class)
    private Integer status;

    @NotNull
    @Size(min = 1)
    @Valid
    private List<CoffeeSkuSaveDTO> skus;
}
