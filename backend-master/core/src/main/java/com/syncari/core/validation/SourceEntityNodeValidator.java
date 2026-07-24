package com.syncari.core.validation;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.syncari.core.model.util.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.connector.data.EntityParams;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.core.DataTransformer;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Edge;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.EntitySourceNodeConfig;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.utils.ScheduleUtils;

@Slf4j
@Component
public class SourceEntityNodeValidator implements ValidationService {
    @Autowired
    DataServiceFactory factory;
    @Autowired
    ConnectorService connService;
    @Autowired
    DataTransformer transformer;

    @Override
    public void validate(ValidationContext validationContext) {
    	var errors = validateWithoutException(validationContext);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }
    
    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
    	List<ValidationError> errors = new ArrayList<>();

        MappingNode node = validationContext.getNode();
        MappingGraph graph = validationContext.getGraph();

        Map<String, EntityDefinition> sourceEntityMap = validationContext.getSourceEntityMap();

        if (graph == null || node == null)
			return errors;
        
        // validate if source nodes are terminal nodes with no inbound edges
        EntitySourceNodeConfig srcNodeConfig = node.getTypedConfiguration();
        EntityDefinition entity = sourceEntityMap.getOrDefault(srcNodeConfig.getEntityDefinition().getId(), srcNodeConfig.getEntityDefinition());
        
        connService.find(entity.getConnectorId()).ifPresent(connector -> {
			// validate source entity node is not a syncari entity
			if(connector.isSyncariConnector() && !entity.isSyncariSource()) {
				validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
						connector.isSyncariConnector(),
						i18n("invalid_source_node", node.getName(), graph.getName()), ErrorCode.E1169.getCode())
						.ifPresent(e -> errors.add(e));
			} else {
				SynapseInfoService synapseService = factory.getSynapseService(connector.getMetadata());
				EntityParams params = new EntityParams().setConnector(transformer.toConnectorInfo(connector))
						.setSchema(transformer.toEntitySchema(entity, connector)).setSourceParams(srcNodeConfig.getSourceParams());
				try {
					synapseService.validateEntityConfig(params);
				} catch (RuntimeException e) {
					log.error(e.getMessage(), e);
					errors.add(ValidationError.scopedError(node.getScope(), node.getId()).withMessage(e.getMessage()));
				}
			}
        });
        //Validate cron schedule
        boolean isValidSchedule = StringUtils.isBlank(srcNodeConfig.getSchedule()) || ScheduleUtils.isValidCronExpression(srcNodeConfig.getSchedule());
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), !isValidSchedule,
				i18n("invalid_schedule_in_source", node.getName()), ErrorCode.E1195.getCode()).ifPresent(e -> errors.add(e));
        
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), entity == null,
				i18n("invalid_source_node", node.getName(), graph.getName()), ErrorCode.E1170.getCode()).ifPresent(e -> errors.add(e));
		if (entity != null) {
			validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
					entity.isArchived() || entity.isDeleted(),
					i18n("deleted_source_node_entity", node.getName(), graph.getName()), ErrorCode.E1171.getCode()).ifPresent(e -> errors.add(e));
		}

        List<Edge> inboundEdges = graph.getInboundEdges(node);
		validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), !inboundEdges.isEmpty(),
				i18n("error_source_node_with_inbound_edge", node.getName(), graph.getName()), ErrorCode.E1172.getCode())
						.ifPresent(e -> errors.add(e));
        
        return errors;
    }
}
