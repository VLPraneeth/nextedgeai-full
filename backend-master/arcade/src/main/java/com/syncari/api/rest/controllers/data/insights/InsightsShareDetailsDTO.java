package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.model.insights.sharing.SharedItemInvitationStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
public class InsightsShareDetailsDTO {

    private String emailId;
    private SharedItemInvitationStatus status;
    private Instant expiryDate;
    private Instant lastVisitedDate;
    private String sharedItemId;
}
