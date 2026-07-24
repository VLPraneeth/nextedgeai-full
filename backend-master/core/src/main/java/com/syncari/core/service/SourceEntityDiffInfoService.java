package com.syncari.core.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.EntityDefinition;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.utils.Pair;
import static com.syncari.utils.I18n.i18n;

@Component
public class SourceEntityDiffInfoService implements DiffInfoService {
	@Autowired
	ConnectorService connectorService;
	@Autowired
	SchemaService schemaService;
	
	@Override
	public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
		if(context != null && context.getCurrentNode() != null) {
			List<Pair<String, String>> response = new ArrayList<Pair<String,String>>();
			if("connectorId".equals(configProperty)) {
				var config = context.getCurrentNode().getConfiguration().getConfigMap();
				var connector = connectorService.findLite((String) config.get("connectorId"));
				var connectorName = connector != null ? connector.getName(): (String) config.get("connectorId");
				response.add(Pair.of("synapse", connectorName));
				return response;
			} else if("entityDefinition".equals(configProperty)) {
				var config = context.getCurrentNode().getConfiguration().getConfigMap();
				var entity = schemaService.findEntity((String) config.get("entityDefinition"));
				var entityName = entity.orElse(new EntityDefinition()).getDisplayName();
				entityName = entityName == null ? (String) config.get("entityDefinition") : entityName;
				
				response.add(Pair.of(configProperty, entityName));
				return response;
			} else if("exhaustAllRecords".equals(configProperty)) {
				var config = context.getCurrentNode().getConfiguration().getConfigMap();
				var scheduleType = i18n("scheduling_type_" + config.getOrDefault(configProperty, "").toString().toLowerCase());
				response.add(Pair.of(configProperty, scheduleType));
				return response;
			}
		}
		return DiffInfoService.super.toUserFriendlyValue(context, configProperty);
	}
}
