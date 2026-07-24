package com.syncari.core.model.errornotification;

import java.util.Map;

import com.syncari.core.model.misc.ErrorNotificationChannelType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestRequest {
	private ErrorNotificationChannelType type;
	private Map<String, Object> configuration;

}
