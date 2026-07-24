package com.syncari.core.template;

import java.util.Map;

import org.jtwig.JtwigModel;
import org.jtwig.JtwigTemplate;
import org.springframework.stereotype.Component;

@Component
public class JTwigTemplateRenderer implements TemplateRenderer {

	@Override
	public String render(String templateName, Map<String, Object> context) {
		JtwigTemplate jtwigTemplate = JtwigTemplate.classpathTemplate(templateName);
		JtwigModel model = JtwigModel.newModel(context);
		return jtwigTemplate.render(model);
	}

}
