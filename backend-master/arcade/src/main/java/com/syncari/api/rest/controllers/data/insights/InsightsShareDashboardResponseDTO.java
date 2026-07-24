package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.model.SharedItem;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class InsightsShareDashboardResponseDTO {
    String recipientEmailId;
    SharedItem sharedItem;
    String errorMessage;
}
