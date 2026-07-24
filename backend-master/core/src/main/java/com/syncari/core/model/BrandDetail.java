package com.syncari.core.model;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;

@Data
@Accessors(chain = true)
public class BrandDetail extends UUIDAuditModel{
    @NotNull(message = "Org id is required")
    String orgId;
    String logoLocation;
    String logoSquareLocation;
    String name;
    String color;
}
