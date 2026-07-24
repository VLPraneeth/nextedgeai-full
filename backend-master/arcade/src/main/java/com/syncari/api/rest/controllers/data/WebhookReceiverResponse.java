package com.syncari.api.rest.controllers.data;

import java.util.List;
import com.syncari.core.event.store.model.WebhookReceiverLog;
import com.syncari.core.model.pagination.PageInfo;
import lombok.Data;

@Data
public class WebhookReceiverResponse {
    List<WebhookReceiverLog> records;
    PageInfo pageInfo;
    String connectorId;

    public WebhookReceiverResponse(List<WebhookReceiverLog> records, PageInfo pageInfo, String connectorId) {
        this.records = records;
        this.pageInfo = pageInfo;
        this.connectorId = connectorId;
    }
}
