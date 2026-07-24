package com.syncari.core.model;

import com.syncari.core.model.insights.DatacardViewerPreference;
import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Accessors(chain = true)
public class InsightsUserPreference extends UUIDAuditModel {

    @NotNull(message = "User id is required")
    private String userId;
    private String lastVisitedDashboardId;
    private List<DatacardViewerPreference> datacardViewerPreferences;
}
