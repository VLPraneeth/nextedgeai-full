package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

@Data
@Accessors(chain = true)
public class InsightsSharingDashboardDTO {

    private List<String> emails;
    private String message;
    private String expiryDate;
    private String dashboardId;

}
