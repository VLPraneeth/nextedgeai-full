package com.syncari.core.quickstart.v2;

import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class QSAuthoringConfig {
    private List<KeyValue> configuration;
    private String description;
    private String displayName;
    private String helpLink;
    private String helpSummary;
    private String iconPath;
    private String name;
    private KeyValue renderer;
    private List<KeyValue> steps;
}
