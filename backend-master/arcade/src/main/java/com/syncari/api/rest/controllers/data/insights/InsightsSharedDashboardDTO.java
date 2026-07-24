package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
public class InsightsSharedDashboardDTO {

    private String dashboardId;
    private String dashboardDiplayName;
    private String dashboardDescription;
    private Instant expiredTime;
    private String dashboardInstanceId;
}


