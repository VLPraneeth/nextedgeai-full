package com.syncari.core.service;

import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.DatAuthorityStrategy;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.utils.Pair;

@Component
public class CoreAttributeDiffInfoService implements DiffInfoService {
	@Autowired
	ConnectorService connectorService;
	@Override
	public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
		if(context != null && context.getCurrentNode() != null) {
			if("dataAuthority".equals(configProperty)) {
				List<Pair<String, String>> response = new ArrayList<Pair<String,String>>();
				var config = context.getCurrentNode().getConfiguration().getConfigMap();
				if(MapUtils.isNotEmpty(config)) {
					var authorityMap = (Map)config.get("dataAuthority");
					if(MapUtils.isNotEmpty(authorityMap)) {
						if(authorityMap.containsKey("dataAuthorityStrategy")) {
							String strategy = (String) authorityMap.getOrDefault("dataAuthorityStrategy", DatAuthorityStrategy.NONE.name());
							String strategyName = "";
							if(strategy.equals(DatAuthorityStrategy.NONE.name())) {
								strategyName = i18n("da_none");
							} else if(strategy.equals(DatAuthorityStrategy.LATEST_RECORD.name())) {
								strategyName = i18n("da_latest");
							} else if(strategy.equals(DatAuthorityStrategy.SELECTED_CONNECTOR.name())) {
								strategyName = i18n("da_selected_synapse");
							}
							response.add(Pair.of("data_authority_strategy", strategyName));
						}
						if(authorityMap.containsKey("connectorId")) {
							var connector = connectorService.findLite((String) authorityMap.get("connectorId"));
							var connectorName = connector != null ? connector.getName(): (String) authorityMap.get("connectorId");
							response.add(Pair.of("synapse", connectorName));
						}
					}
				}
				return response;
			}
		}
		return DiffInfoService.super.toUserFriendlyValue(context, configProperty);
	}
}
