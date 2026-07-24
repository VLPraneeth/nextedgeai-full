package com.syncari.core.model.insights.provider.ts;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class TSChangeOwnerRequest {

    private List<TSMetadataListItemInput> metadata;
    private String user_identifier;
    private String current_owner_identifier;
}
