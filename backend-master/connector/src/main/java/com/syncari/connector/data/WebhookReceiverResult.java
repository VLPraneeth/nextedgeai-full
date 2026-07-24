package com.syncari.connector.data;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WebhookReceiverResult {
    private List<Map<String, Object>> records;
    private ZonedDateTime receivedAt;
    private boolean authenticated;
}
