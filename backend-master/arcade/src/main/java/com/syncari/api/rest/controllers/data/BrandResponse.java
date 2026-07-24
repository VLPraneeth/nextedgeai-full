package com.syncari.api.rest.controllers.data;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class BrandResponse {
    String brandLogoUri;
    String brandLogoSquareUri;
    String name;
    String color;
}
