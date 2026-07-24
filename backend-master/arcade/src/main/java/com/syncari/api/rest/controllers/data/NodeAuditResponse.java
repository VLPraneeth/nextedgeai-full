package com.syncari.api.rest.controllers.data;

import com.syncari.core.event.store.model.NodeAudit;
import com.syncari.core.model.pagination.PageInfo;
import lombok.Data;

import java.util.List;

@Data
public class NodeAuditResponse {
    List<NodeAudit> records;
    PageInfo pageInfo;
    String syncariEntityId;

    public NodeAuditResponse(List<NodeAudit> records, PageInfo pageInfo, String syncariEntityId) {
        this.records = records;
        this.pageInfo = pageInfo;
        this.syncariEntityId = syncariEntityId;
    }
}
