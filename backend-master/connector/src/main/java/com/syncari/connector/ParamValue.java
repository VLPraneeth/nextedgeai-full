package com.syncari.connector;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ParamValue {

    String paramName;
    Integer paramNumber;
    String paramDataType;
    Object paramValue;
}
