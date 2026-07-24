package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.core.util.NodeHelper;
import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import com.syncari.restutils.data.MappingGraphDTO;
import com.syncari.restutils.transformers.GraphTransformer;
import com.syncari.utils.KeyValue;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/token")
public class TokenController {
    private static final String TOKEN_LABEL = "%s / %s / %s (%s)";
	private static final String SYNCARI = "syncari";
    private static final String SYNCARI_ID = "syncariEntityId";
    private static final String SYNCARI_GROUP = "Syncari";
    private static final String SYNAPSE_GROUP = "Synapse";
    private static final String LOOKUP_GROUP = "Lookup Results";
    private static final String PREVIOUS_GROUP = "Previous";
    private static final String TEMP_GROUP = "Temporary Variables";
    private static final String ERROR_GROUP = "error";
    @Autowired
    SchemaService schemaService;
    @Autowired
    GraphTransformer transformer;
    @Autowired
    ObjectTransformer objectTransformer;
    @Autowired
    private ConnectorService connectorService;
    ObjectMapper mapper = new ObjectMapper();
    @Autowired
    NodeHelper nodeHelper;
    @Autowired
	TextUtil textUtil;

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{currentNodeId}")
    public Map<String, List<KeyValue>> getTokens(@PathVariable String currentNodeId, @RequestBody MappingGraphDTO currentGraph) {
        log.info("Fetching tokens for node {} and graph {}", currentNodeId, currentGraph == null ? "" : currentGraph.getName());
        var graph = transformer.toMappingGraph(currentGraph);
        MappingNode node = null;
        Optional<MappingNode> n = graph.getNode(currentNodeId);
        if(n.isPresent()) {
            node = n.get();
        } else {
            if(currentGraph.hasDraft()) {
                graph = transformer.toMappingGraph(currentGraph.getDraft());
                node = graph.getNode(currentNodeId).get();
            }
        }
        List<KeyValue> response = new ArrayList<KeyValue>();
        Optional<Connector> httpSourceConnector = getHttpSourceConnector(node);
        if(httpSourceConnector.isPresent()) {
        	response.addAll(getHttpSourceTokens(httpSourceConnector.get()));
        } else {
        	Set<MappingNode> sources = nodeHelper.findConnectedSources(node, graph);
        	Stream<EntityDefinition> entityDefinitions = nodeHelper.findConnectedEntities(sources);
        	List<EntityDefinition> entityDefinitionList = entityDefinitions.collect(Collectors.toList());
        	response.addAll(createFieldPicklistFromInput(entityDefinitionList.stream()));
        	response.addAll(getLookUpTokens(graph, node));
        	response.addAll(getPreviousTokens(graph, node));
        	response.addAll(getTempVariableTokens(graph, node));
        	if (CollectionUtils.isEmpty(response)){
        		response.add(KeyValue.of("error",List.of(KeyValue.of("message",i18n("empty_token_msg"))
        				,KeyValue.of("description",i18n("empty_token_desc")))).set("group", ERROR_GROUP));
        	}
        }
		
        return new TreeMap<String, List<KeyValue>>(response.stream().collect(Collectors.groupingBy(a -> a.get("group"))));
    }

    public ArrayList<KeyValue> getLookUpTokens(MappingGraph graph, MappingNode node) {
        Set<MappingNode> lookUps = nodeHelper.findConnectedLookup(node, graph);
        Stream<EntityDefinition> entities = lookUps.stream()
                .map(source -> source.getConfiguration().getConfigMap().getOrDefault("syncariEntityDefId", "").toString())
                .filter(entityId -> !StringUtils.isBlank(entityId))
                .map(entityId -> schemaService.getEntity(entityId));
        var lookupTokens = new ArrayList<>(entities.flatMap(e -> e.getActiveAttributes().stream()
                .map(attribute -> {
                    return new KeyValue("value", attribute.getId())
                    		.set("label", constructLabel(e, attribute, SYNCARI))
                            .set("shortLabel", attribute.getDisplayName())
                            .set("token", String.format("{{previousLookup.values.%s}}", attribute.getApiName()))
                            .set("datatype", attribute.getDataType().getName())
                            .set("group", LOOKUP_GROUP);
                })).collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(x -> ((String) x.get("value")))))));

        return new ArrayList<>(lookupTokens.stream().sorted(Comparator.comparing(x -> ((String) x.get("shortLabel")))).collect(Collectors.toList()));
    }
    
    private ArrayList<KeyValue> getPreviousTokens(MappingGraph graph, MappingNode node) {
        ArrayList<KeyValue> previousValues = new ArrayList<>();

        if((null != node) && (node.getType() != MappingNodeType.ENTITY_SOURCE && graph.getScope() == Scope.ATTRIBUTE) && CollectionUtils.isNotEmpty(graph.getInboundEdges(node))) {
            previousValues.add(new KeyValue("value", "previous")
                    .set("label", "Output from previous node")
                    .set("shortLabel", "Output from previous node")
                    .set("token", "{{previous}}")
                    .set("datatype", "object")
                    .set("group", PREVIOUS_GROUP));
        }
        return previousValues;
    }
    
    public ArrayList<KeyValue> getTempVariableTokens(MappingGraph graph, MappingNode node) {
        Set<MappingNode> setValues = nodeHelper.findConnectedSetValues(node, graph);

        Set<String> variableNames = new HashSet<>();
        List<KeyValue> variables = new ArrayList<>();
        for (MappingNode source : setValues) {

            var setValueFieldMap = (Map) source.getConfig("setValueField");
            if(setValueFieldMap == null) {
                setValueFieldMap = Map.of();
            }
            String apiName = (String) setValueFieldMap.get("apiName");
            apiName = StringUtils.isNotEmpty(apiName) ? apiName : source.getStringConfig("configId");
            apiName = textUtil.createApiName(apiName);
            if (variableNames.contains(apiName)) {
                continue;
            }
            variableNames.add(apiName);

            String displayName = (String) setValueFieldMap.get("displayName");
            displayName = StringUtils.isNotEmpty(displayName) ? displayName : apiName;
            String dataType = (String) setValueFieldMap.get("dataType");
            variables.add(
            new KeyValue("value", String.format("syncari.temp.%s", apiName))
                    .set("shortLabel", displayName)
                    .set("label", apiName)
                    .set("token", String.format("{{syncari.temp.%s}}", apiName))
                    .set("datatype", StringUtils.isNotEmpty(dataType) ? dataType : "string")
                    .set("multiValueField", BooleanUtils.isTrue((Boolean) setValueFieldMap.get("multiValueField")))
                    .set("group", TEMP_GROUP));
        }
        return new ArrayList<>(variables.stream().sorted(Comparator.comparing(x -> ((String) x.get("shortLabel")))).collect(Collectors.toList()));
    }

    private ArrayList<KeyValue> createFieldPicklistFromInput(Stream<EntityDefinition> entityDefinitions) {
        Map<String, Connector> connectors = connectorService.list().stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c));
        String syncariConnectorName = connectorService.getSyncariConnector().getName();

        var fieldPicklist = new ArrayList<>(entityDefinitions
                .sorted(Comparator.comparing(EntityDefinition::getConnectorId)).flatMap(e -> getAttributes(e, connectors, syncariConnectorName)
                        .stream().sorted(Comparator.comparing(AttributeDefinition::getDisplayName)).map(attribute -> {
                            Connector connector = connectors.get(e.getConnectorId());
                            String synapseName = getSynapseName(syncariConnectorName, connector);

                            String token = (isSyncari(synapseName) && !e.isSyncariSource())
                                    ? (SYNCARI_ID.equalsIgnoreCase(attribute.getApiName()) ? "{{record." + attribute.getApiName() + "}}" : "{{record.values." + attribute.getApiName() + "}}")
                                    : "{{" + synapseName + "." + e.getApiName() + "." + attribute.getApiName() + "}}";
                            return new KeyValue("value", attribute.getId())
									.set("label", constructLabel(e, attribute, synapseName))
                                    .set("shortLabel", attribute.getDisplayName())
									.set("token", token)
                                    .set("datatype", attribute.getDataType().getName())
                                    .set("group", String.format("%s", connectors.containsKey(e.getConnectorId()) ? SYNAPSE_GROUP : SYNCARI_GROUP));
                        }))
                .collect(Collectors.toList()));
        return fieldPicklist;
    }

	private String constructLabel(EntityDefinition e, AttributeDefinition attribute, String synapseName) {
		return String.format(TOKEN_LABEL, synapseName, e.getDisplayName(), attribute.getDisplayName(), attribute.getApiName());
	}

    private boolean isSyncari(String synapseName) {
        return SYNCARI.equalsIgnoreCase(synapseName);
    }

    private String getSynapseName(String syncariConnectorName, Connector connector) {
        return connector == null ? syncariConnectorName : connector.getName();
    }

    private List<AttributeDefinition> getAttributes(EntityDefinition e, Map<String, Connector> connectors, String syncariConnectorName) {
        List<AttributeDefinition> activeAttributes = new ArrayList<>();
        Connector connector = connectors.get(e.getConnectorId());
        e.getActiveAttributes().forEach(a -> activeAttributes.add(a));
        String synapseName = getSynapseName(syncariConnectorName, connector);
        if(isSyncari(synapseName)) {
            AttributeDefinition attr = new AttributeDefinition();
            attr.setId(SYNCARI_ID);
            attr.setApiName(SYNCARI_ID);
            attr.setDisplayName("Syncari Id");
            attr.setDataType(new StringType());
            attr.setStatus(Status.ACTIVE);
            activeAttributes.add(attr);
        }
        return activeAttributes;
    }
    
    private Optional<Connector> getHttpSourceConnector(MappingNode node) {
		if (node.getType() == MappingNodeType.ENTITY_SOURCE) {
			var entityDefId = node.getEntityDefinitionId();
			if(entityDefId.isPresent()) {
				var entityDef = schemaService.findEntity(entityDefId.get());
				if(entityDef.isPresent()) {
					var connector = connectorService.find(entityDef.get().getConnectorId(), false);
					if(connector.isPresent() && connector.get().getMetadata().isHttpSource()) {
						return connector;
					}
				}
			}
		}
		return Optional.empty();
    }
    
    public List<KeyValue> getHttpSourceTokens(Connector connector) {
    	String synapseName = getSynapseName("", connector);
    	return List.of(
    			new KeyValue("value", "syncari.system.currentTimeInMillis")
        		.set("label", "Current Time in Millis")
                .set("shortLabel", "Current Time in Millis")
                .set("token", "{{syncari.system.currentTimeInMillis}}")
                .set("datatype", IntegerType.NAME)
                .set("group", SYNCARI_GROUP),
                new KeyValue("value", "syncari.system.currentDate")
        		.set("label", "Current Date")
                .set("shortLabel", "Current Date")
                .set("token", "{{syncari.system.currentDate}}")
                .set("datatype", StringType.NAME)
                .set("group", SYNCARI_GROUP),
                new KeyValue("value", "syncari.system.currentDateTime")
        		.set("label", "Current Date Time")
                .set("shortLabel", "Current Date Time")
                .set("token", "{{syncari.system.currentDateTime}}")
                .set("datatype", IntegerType.NAME)
                .set("group", SYNCARI_GROUP),
                new KeyValue("value", String.format("%s.watermark.start", synapseName))
        		.set("label", "Watermark Start")
                .set("shortLabel", "Watermark Start")
                .set("token", String.format("{{%s.watermark.start}}", synapseName))
                .set("datatype", IntegerType.NAME)
                .set("group", SYNAPSE_GROUP),
                new KeyValue("value", String.format("%s.watermark.end", synapseName))
        		.set("label", "Watermark End")
                .set("shortLabel", "Watermark End")
                .set("token", String.format("{{%s.watermark.end}}", synapseName))
                .set("datatype", IntegerType.NAME)
                .set("group", SYNAPSE_GROUP)
                );
    }

}
