package com.syncari.core.model.insights;

import com.syncari.core.model.SharedItem;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class InsightsShareDashboardResponse {
    String recipientEmailId;
    SharedItem sharedItem;
    String errorMessage;
}
