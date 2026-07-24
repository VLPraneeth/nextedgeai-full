package com.syncari.connector.data;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ForeignKey {
    private String referenceTargetField;
    private String referenceTo;
}