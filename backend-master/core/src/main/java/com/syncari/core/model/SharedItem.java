package com.syncari.core.model;

import com.syncari.core.model.misc.Sharable;
import com.syncari.core.share.SharedItemObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode
@Accessors(chain = true)
public class SharedItem extends UUIDAuditModel {

    Sharable itemType;
    String sourceInstance;
    String sourceId;
    String orgId;
    // Key: Instance Id, Value: Id of shared record in that instance
    Map<String, String> sharingInstances = new HashMap<>();
    String ownerUserId;

    SharedItemObject itemObject;
    boolean publishedToMarketplace;
    boolean sharedWithOrg;
    String recipientsUserId;
    String recipientsEmailId;

}
