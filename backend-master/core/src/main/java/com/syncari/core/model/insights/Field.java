package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Field {

    // this can be same as column name or user defined alias
    String alias;
    // this can hold plain column name or complex functions e.g SUM(revenue) or CONCAT(field1, field2)
    String columnName;
    // deduce this based on the function applied - Check if this is really needed
    String dataType;
    String description;
    AggFunctions aggFunction = AggFunctions.NONE;
    String displayFormat; // TODO: convert this into enum when we have enough info
    //KeyValue config; // Q: Do we need nested config or flat structure - this will hold info such as color, thickness etc. Can we define first class configuration with all possible configs? Confirm with Francis?
    String color;
}
