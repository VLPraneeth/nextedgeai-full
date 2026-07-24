package com.syncari.core.model.insights.provider.ts;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
public class TSMetadataSearchResponse {
    private String metadata_id;
    private String metadata_name;
    private String metadata_type;
    private Map<String, Object> metadata_header;
    private Map<String, Object> metadata_detail;
}
