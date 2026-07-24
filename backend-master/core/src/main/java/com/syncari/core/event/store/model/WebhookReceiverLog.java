package com.syncari.core.event.store.model;

import java.time.Instant;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WebhookReceiverLog {
    String id;
    String connectorId;
    Instant receivedOn;
    String payload;
    String headers;
    Boolean verified;
    Boolean authenticated;
}