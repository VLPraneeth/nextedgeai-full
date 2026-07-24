package com.syncari.core.model.insights.sharing;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

@Data
@Accessors(chain = true)
public class InsightsDashboardSharedObj {

    private List<String> emailIds;
    private String dashboardId;
    private String emailMessage;
    private Long expiryDate;
}
