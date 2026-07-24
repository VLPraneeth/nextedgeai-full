package com.syncari.api.rest.controllers.data.insights;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@AllArgsConstructor
public class DatacardConfigMeta {

    String name;
    String displayName;
    @Deprecated(forRemoval = true) // remove once UI moves over dataType
    String component;
    String dataType;
    String helpSummary;
    boolean isMultiValueField;

    public DatacardConfigMeta(){

    }
}
