package com.syncari.core.model.errornotification;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookRequestBody {
	private Date lastNotificationTimestamp;
    private Date timestamp;
    private int notificationCount;
    private String instanceId;
    private String instanceName;
    private List<WebhookRequestBodyNotification> notifications;
    
    public static WebhookRequestBody buildSample() {
    	return WebhookRequestBody.builder()
    			.lastNotificationTimestamp(new Date())
    			.timestamp(new Date())
    			.notificationCount(2)
    			.instanceId("F5XMSW")
    			.instanceName("Error Notification Test")
    			.notifications(List.of(
					WebhookRequestBodyNotification.builder()
					.timestamp(new Date())
					.summary("message summary 1")
					.message("detailed error message 1")
					.build(),
					WebhookRequestBodyNotification.builder()
					.timestamp(new Date())
					.summary("message summary 2")
					.message("detailed error message 2")
					.build()
    			))
    			.build();
    }
}
