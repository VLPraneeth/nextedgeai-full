package com.syncari.core.model.insights.sharing;
import com.syncari.core.share.SharedItemObject;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Date;


@Data
@Accessors(chain = true)
public class InsightsDashboardSharedItem implements SharedItemObject {

    String emailMessage;
    String dashboardSourceInstanceId;
    String dashboardId;
    String dashboardDisplayName;
    String dashboardDescription;
    String senderUserId;
    long expiredTime;
    SharedItemInvitationStatus invitationStatus = SharedItemInvitationStatus.NOT_OPENED;
    long lastVisitedDate;
}