package com.syncari.core.model.misc.test;

import lombok.*;
import lombok.experimental.*;

@Data
@Accessors(chain=true)
@AllArgsConstructor
public class TestNodeResultAttributeValue {
    String apiName;
    String displayName;
    String dataType;
    Object value;

    public TestNodeResultAttributeValue(){

    }
}
