package com.syncari.core.model;

import com.syncari.core.datatype.Datatype;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class lookupField {
    private String name;
    private Datatype datatype;
}
