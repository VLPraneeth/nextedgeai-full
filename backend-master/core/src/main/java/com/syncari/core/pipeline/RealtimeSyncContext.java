package com.syncari.core.pipeline;

import com.syncari.connector.EntityData;
import com.syncari.core.model.WebhookActionResponse;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Data
@Accessors(chain = true)
public class RealtimeSyncContext {
    EntityData record;
    CompletableFuture<WebhookActionResponse> syncResponse;
}
