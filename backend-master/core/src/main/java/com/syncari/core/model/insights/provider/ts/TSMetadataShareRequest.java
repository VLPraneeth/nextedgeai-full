package com.syncari.core.model.insights.provider.ts;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class TSMetadataShareRequest {

    // Could be Liveboard, Answer or LOGICAL_TABLE
    private String metadata_type="LOGICAL_TABLE";
    private List<String> metadata_identifiers;
    private List<TSPermission> permissions;
    private List<String> emails;
    private String message="Shared";
    private boolean notify_on_share;
    private boolean has_lenient_discoverability;
    private boolean enable_custom_url;

    enum SHAREMODE{
        READ_ONLY, MODIFY, NO_ACCESS
    }
}
