package com.syncari.core.model;

import java.util.Map;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthType;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PipelineTestWebhook {
    AuthType authType;
    AuthConfig authConfig;
    String payload; //For Webhook
    Map<String, Object> headers; //For Webhook
}
