package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LastVisitedDashboardDTO {

    private String lastVisitedDashboardId;
    private boolean useNestedDraft;
}
