package com.syncari.core.model.insights.provider.ts;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class TSSearchMetadataSort {

    private String field_name="CREATED";
    private String order = "DESC";
}
