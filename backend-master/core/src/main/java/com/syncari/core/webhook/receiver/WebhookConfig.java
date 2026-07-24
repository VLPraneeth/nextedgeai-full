package com.syncari.core.webhook.receiver;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WebhookConfig{
    private String schema;
    private String recordSelector;
    private String idSelector;
	
    public void copyFrom(WebhookConfig other) {
      this.schema = other.schema;
      this.recordSelector = other.recordSelector;
      this.idSelector = other.idSelector;
  }
}