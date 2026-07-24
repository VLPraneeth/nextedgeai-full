package com.syncari.core.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.utils.Pair;

@Component
public class DefaultDiffInfoService implements DiffInfoService {
	private static List<String> VALUE_EXCLUSION_FUNCTIONS = List.of("formatPhone");
	 @Override
		public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
		 if(context != null && context.getCurrentNode() != null && VALUE_EXCLUSION_FUNCTIONS.contains(context.getCurrentNode().getApiName())) {
			 if("value".equals(configProperty)) { // Skip the property
				 return List.of();
			 }
		 }
			return DiffInfoService.super.toUserFriendlyValue(context, configProperty);
		}
}
