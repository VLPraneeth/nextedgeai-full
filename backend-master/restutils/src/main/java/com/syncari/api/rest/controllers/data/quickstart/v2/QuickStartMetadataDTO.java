package com.syncari.api.rest.controllers.data.quickstart.v2;

import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class QuickStartMetadataDTO {

    private String name;
    private String displayName;
    private String description;
    private String iconPath;
    private String helpLink;
    private String helpSummary;
    private String requirementsText;
    private List<KeyValue> configuration;
    private KeyValue renderer;
}
