package com.syncari.core.template;

import java.util.Map;

public interface TemplateRenderer {
	String render(String template, Map<String, Object> context);
}
