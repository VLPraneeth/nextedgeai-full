package com.syncari.core.functions;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(FunctionConstants.EXTRACT_DOMAIN_ON_FIELD)
public class ExtractDomainFunction extends DefaultFunction {
    
	@Override
	public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
		if ("option".equals(configProperty) && context != null && context.getCurrentNode() != null) {
			SimpleFunctionNodeConfig functionNodeConfig = context.getCurrentNode().getTypedConfiguration();
			Map<String, Object> configMap = functionNodeConfig.getConfigMap();
			String option = (String) configMap.get(configProperty);
			if(option != null) {
				return List.of(Pair.of(configProperty, i18n(String.format("extract_domain_%s", option))));
			}
		}
		return super.toUserFriendlyValue(context, configProperty);
	}
}
