package com.syncari.connector;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CampaignMember {
    String id;
    String objectType;
    String objectId;
    String campaignId;
    String status;
}
