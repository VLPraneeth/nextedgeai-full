package com.syncari.api.rest.controllers.data.insights;

import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.syncari.core.draft.DraftStatus;

@Data
@Accessors(chain = true)
public class DatacardDTO {

    String id;
    String name;
    String displayName;
    String description;
    LayoutDTO layout;
    boolean hidden;
    VizDTO contents;
    DraftStatus draftStatus;
    Set<String> tags = new LinkedHashSet<>();
    boolean seeded;
    List<DatacardConfigMeta> configurationMeta;
    KeyValue configuration;
    String errorMsg;
    String createdBy;
    String updatedBy;
    ZonedDateTime createdAt;
    ZonedDateTime updatedAt;
}
