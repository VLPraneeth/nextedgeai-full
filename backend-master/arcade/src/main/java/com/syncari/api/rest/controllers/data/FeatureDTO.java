package com.syncari.api.rest.controllers.data;

import com.syncari.core.model.misc.FeatureStage;
import com.syncari.core.model.misc.FeatureStatus;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FeatureDTO {

    private String name;
    private String displayName;
    private String description;
    private FeatureStage stage;
    private FeatureStatus status;
    private String params;
    private boolean hidden;

    public boolean isEnabled() {
        return status != null && status == FeatureStatus.active;
    }
}
