package com.syncari.core.schema;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.*;

@Data
@AllArgsConstructor
public class ClonePipelineEntityDef {
    String apiName;
    String displayName;
    String dataStoreName;
    String description;
    Set<String> tags;
    boolean cloneFromDraft;
}
