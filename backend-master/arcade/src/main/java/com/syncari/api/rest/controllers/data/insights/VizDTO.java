package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class VizDTO {

    String id;
    String name;
    String displayName;
    @Deprecated(forRemoval = true)
    String component; // can make it enum once we have all components
    VizConfigDTO configuration;
    VizData data;
    VizDTO contents; // children
}
