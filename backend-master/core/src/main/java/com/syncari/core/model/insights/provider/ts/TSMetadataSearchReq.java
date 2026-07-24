package com.syncari.core.model.insights.provider.ts;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class TSMetadataSearchReq {

    private String dependent_object_version;
    private boolean include_auto_created_objects;
    private boolean include_dependent_objects;
    private int dependent_objects_record_size;
    private boolean include_details;
    private boolean include_headers=true;
    private boolean include_hidden_objects;
    private boolean include_incomplete_objects;
    private boolean include_visualization_headers;
    private boolean include_stats;
    private int record_offset;
    private int record_size;
    private List<TSMetadataListItemInput> metadata;
    private TSSearchMetadataSort sort_options;
    private List<TSPermission> permissions;
}
