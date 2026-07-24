package com.syncari.api.rest.controllers.data.insights;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@AllArgsConstructor
public class FieldInfo {

    String name;
    String displayName;
    String displayFormat;
}
