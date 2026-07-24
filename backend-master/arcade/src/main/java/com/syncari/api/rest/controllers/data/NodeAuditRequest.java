package com.syncari.api.rest.controllers.data;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class NodeAuditRequest {
    String start;
    String end;
    String syncariRecordId;
    String syncariEntityId;
    String status;
}
