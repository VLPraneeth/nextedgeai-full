package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class VizRange {
    String name;
    String color;
    Integer minimumValue;
    Integer maximumValue;
}
