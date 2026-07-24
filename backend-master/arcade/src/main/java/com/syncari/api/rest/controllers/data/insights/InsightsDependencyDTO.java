package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.misc.ComponentType;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class InsightsDependencyDTO {

    private String id;
    private String name;
    private ComponentType type;
    private boolean isNestedDraft;
    private DraftStatus draftStatus;
    private String author;
}
