package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FieldProperty {

    String column;
    String name;
    String displayName;
    String color;
    String displayFormat;
}
