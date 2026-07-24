package com.syncari.core.model.misc;

import java.util.Map;

import lombok.Data;

@Data
public class WidgetSetting {
	String widgetId;
	Map<String, Object> layout;
	Map<String, Object> temporal;
}
