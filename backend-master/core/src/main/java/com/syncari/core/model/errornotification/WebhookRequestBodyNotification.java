package com.syncari.core.model.errornotification;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookRequestBodyNotification {
    private Date timestamp;
    private String summary;
    private String message;
}
