package com.syncari.core.model.insights.provider.ts;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class TSMetadataListItemInput {

    private String identifier;
    private String type;

}
