package com.syncari.connector;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WebhookConfigInfo{
    private String schema;
    private String recordSelector;
    private String idSelector;
	
	public WebhookConfigInfo copy() {
        WebhookConfigInfo copy = new WebhookConfigInfo();
        copy.setSchema(this.schema)
            .setRecordSelector(this.recordSelector)
            .setIdSelector(this.idSelector);
        return copy;
    }
}