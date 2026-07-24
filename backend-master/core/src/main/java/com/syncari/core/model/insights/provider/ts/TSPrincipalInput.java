package com.syncari.core.model.insights.provider.ts;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class TSPrincipalInput {
    private String identifier;
    // USER_GROUP or USER
    private String type=TSMetadataType.USER_GROUP.name();
}
