package com.syncari.core.webhook.receiver;

import org.springframework.web.multipart.MultipartFile;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthType;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WebhookReceiverMetadataDTO{
	private String name;
	private String displayName;
	private AuthType authType;
	private AuthConfig authConfig;
    private String schema;
    private String recordSelector;
    private String idSelector;
    private MultipartFile icon;
    private Integer responseCode;
    private String responseTemplate;
}
