package com.syncari.api.rest.controllers;

import com.syncari.api.core.util.NodeHelper;
import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.*;
import com.syncari.api.rest.controllers.data.studio.AttributeNodeConfig;
import com.syncari.connector.Constants;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.functions.CaseFunction;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.functions.FunctionsSeed;
import com.syncari.core.functions.SalesIntelFunctionsSeed;
import com.syncari.core.model.*;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.Variable;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.SchedulingType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.AbstractNodeConfigurationVisitor;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DatasetService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.restutils.data.*;
import com.syncari.restutils.transformers.GraphTransformer;
import com.syncari.utils.I18n;
import com.syncari.utils.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/nodeConfig")
public class DynamicNodeConfigController {

    @Autowired
    SchemaService schemaService;
    @Autowired
    GraphTransformer transformer;
    @Autowired
    ObjectTransformer objectTransformer;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    NodeHelper nodeHelper;
    @Autowired
    MappingGraphService graphService;
    @Autowired
    TokenController tokenController;

    @Autowired
    DatasetService datasetService;
    Map<String, Function<AdditionalNodeConfigRequest, AdditionalNodeConfigResponse>> configLoaders = Map.of(
            "dataset", this::loadDatasetConfig,
            "entitysource", this::loadEntitySourceConfig
    );

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/loadAdditionalConfig")
    public AdditionalNodeConfigResponse getNodeConfig(@RequestBody AdditionalNodeConfigRequest nodeConfigRequest) {
        final String configLoaderType = nodeConfigRequest.getConfigLoaderType();
        if (configLoaderType != null) {
            return configLoaders.getOrDefault(configLoaderType, this::loadDefaults).apply(nodeConfigRequest);
        }
        return loadDefaults(nodeConfigRequest);
    }

    private AdditionalNodeConfigResponse loadDatasetConfig(AdditionalNodeConfigRequest additionalNodeConfigRequest) {
        final AdditionalNodeConfigResponse response = additionalNodeConfigRequest.createBaseResponse();
        String datasetId = (String) additionalNodeConfigRequest.getCurrentConfiguration().get(additionalNodeConfigRequest.getConfigName());
        datasetService.findDataset(datasetId).ifPresent(ds->{
            if (ds.getVariablesMap() != null) {
                ds.getVariablesMap().forEach((vName,variable)->{
                final KeyValue config = new KeyValue("name", variable.getApiName(), "datatype",
                        getDatatype(variable), "label", variable.getDisplayName(), "required", variable.isRequired())
                        .set("defaultValue", variable.getVariableValue().getDefaultValue())
                        .set("mapping", new KeyValue("graphKey", "configuration." + variable.getApiName()).set("configKey", "value")
                        );

                response.addConfig(config);
                });
            }
        });
        return response;
    }
    
	private AdditionalNodeConfigResponse loadEntitySourceConfig(
			AdditionalNodeConfigRequest additionalNodeConfigRequest) {
		final AdditionalNodeConfigResponse response = additionalNodeConfigRequest.createBaseResponse();
		String entityId = (String) additionalNodeConfigRequest.getCurrentConfiguration()
				.get(additionalNodeConfigRequest.getConfigName());
		List<KeyValue> entityVarConfigs = new ArrayList<>();
		schemaService.findEntity(entityId).ifPresent(entityDef -> {
			connectorService.find(entityDef.getConnectorId(), false).ifPresent(c -> {
				if (CollectionUtils.isNotEmpty(c.getMetadata().getHttpSources())) {
					c.getMetadata().getHttpSources().stream().filter(hc -> hc.getApiName().equals(entityDef.getApiName())).findFirst()
							.ifPresent(hc -> {
								if (hc.getVariables() != null) {
									entityVarConfigs.addAll(hc.getVariables().stream().map(v -> new KeyValue()
											.set("name", v.get("name")).set("datatype", v.get("dataType"))
											.set("label", v.get("displayName")).set("defaultValue", null)
											.set("helpSummary", v.get("helpText")).set("required", v.get("required"))
											.set("readOnly", false).set("mapping", Map.of("graphKey",
													"configuration." + v.get("name"), "configKey", "value")))
											.collect(Collectors.toList()));
								}
							});
				}
			});
		});
		response.setAdditionalConfigs(entityVarConfigs);
		return response;
	}

    private static String getDatatype(Variable variable) {
        return variable.isMultiValueField() ? "emailList" : variable.getDatatype();
    }

    private AdditionalNodeConfigResponse loadDefaults(AdditionalNodeConfigRequest additionalNodeConfigRequest) {
        return additionalNodeConfigRequest.createBaseResponse();
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{currentNodeId}")
    public NodeDef getNodeConfig(@PathVariable String currentNodeId, @RequestBody MappingGraphDTO currentGraph, @RequestParam Map<String, String> params) {

        var shouldIgnoreValidationError = getShouldIgnoreValidationError(params);
        var graph = transformer.toMappingGraph(currentGraph, shouldIgnoreValidationError);

        Optional<MappingNode> currentNode = graph.getNode(currentNodeId);
       if(currentNode.isEmpty() && currentGraph.getDraft() !=null){
            graph = transformer.toMappingGraph(currentGraph.getDraft(), shouldIgnoreValidationError);
            currentNode = graph.getNode(currentNodeId);
        }
        List<Connector> connectors = connectorService.list();
        connectors.add(connectorService.getSyncariConnector());
        NodeConfigGenerator nodeConfigGenerator = new NodeConfigGenerator(graph, schemaService, objectTransformer,
                connectors, nodeHelper, graphService, connectorService, tokenController, datasetService);
        currentNode.ifPresent(node -> node.getConfiguration().accept(nodeConfigGenerator, node));
        return nodeConfigGenerator.getNodeConfig().orElseThrow(
                () -> new SyncariValidationException("No dynamic node config found for node " + currentNodeId));
    }

    private boolean getShouldIgnoreValidationError(Map<String,String> params) {
        var ignoreValidationErrorParam = params.get("ignoreValidationError");
        return ignoreValidationErrorParam != null ? ignoreValidationErrorParam.equalsIgnoreCase("true") : false;
    }
}

@Slf4j
class NodeConfigGenerator extends AbstractNodeConfigurationVisitor {
    private final Map<String, Connector> connectors;
    private MappingGraph graph;
    private SchemaService schemaService;
    private ObjectTransformer objectTransformer;
    private Optional<NodeDef> nodeConfig = Optional.empty();
    private NodeHelper nodeHelper;
    private MappingGraphService graphService;
    private ConnectorService connectorService;
    TokenController tokenController;
    private DatasetService datasetService;

    public NodeConfigGenerator(MappingGraph graph, SchemaService schemaService, ObjectTransformer objectTransformer,
                               List<Connector> connectors, NodeHelper nodeHelper, MappingGraphService graphService,
                               ConnectorService connectorService, TokenController tokenController, DatasetService datasetService) {
        this.graph = graph;
        this.schemaService = schemaService;
        this.objectTransformer = objectTransformer;
        this.connectors = connectors.stream().collect(Collectors.toMap(c -> c.getId(), c -> c));
        this.nodeHelper = nodeHelper;
        this.graphService = graphService;
        this.connectorService = connectorService;
        this.tokenController = tokenController;

        this.datasetService = datasetService;
    }

    public Optional<NodeDef> getNodeConfig() {
        return nodeConfig;
    }

    @Override
    public void visit(AttributeSinkNodeConfig attributeSinkNodeConfig, MappingNode node) {
        var entityDefinitionId = attributeSinkNodeConfig.getEntityDefinitionId();
        var entityDef = entityDefinitionId != null ? schemaService.getEntity(entityDefinitionId) : schemaService.getEntity(attributeSinkNodeConfig.getAttributeDefinition().getEntityId());
        this.nodeConfig = getAttributeConfig(entityDef, attributeSinkNodeConfig.getAttributeDefinition(), false, node);
    }

    @Override
    public void visit(AttributeSourceNodeConfig attributeSinkNodeConfig, MappingNode node) {
        var entityDefinitionId = attributeSinkNodeConfig.getEntityDefinitionId();
        var entityDef = entityDefinitionId != null ? schemaService.getEntity(entityDefinitionId) : schemaService.getEntity(attributeSinkNodeConfig.getAttributeDefinition().getEntityId());
        this.nodeConfig = getAttributeConfig(entityDef, attributeSinkNodeConfig.getAttributeDefinition(), true, node);
    }

    public void visit(GenericActionConfig actionConfig, MappingNode node) {
        this.nodeConfig = schemaService.getAction(actionConfig.getApiName()).map(a -> objectTransformer.toActionDef(a));

        if (com.syncari.core.actions.ActionConstants.UPDATE_EXTERNAL_RECORD.equals(actionConfig.getApiName())) {
            List<Edge> inboundEdges = graph.getInboundEdges(node);
            this.nodeConfig = addToUpdateExternalRecordConfig(this.nodeConfig, inboundEdges, node);
        }
    }

    @Override
    public void visit(SimpleFunctionNodeConfig simpleFunctionNodeConfig, MappingNode node) {
        Optional<FunctionDefinition> functionDefinition = schemaService.getFunction(simpleFunctionNodeConfig.getApiName(), node.getScope());
        if(simpleFunctionNodeConfig.getApiName().equals("setFields")){
            functionDefinition = functionDefinition.map( f-> {
                FunctionDefinition clone = f.clone();
                Optional<FunctionConfiguration> setFields = clone.findConfiguration("setFields");
                setFields.ifPresent(setFieldsconfig ->{
                    setFieldsconfig.getConfiguration().forEach(cf->{
                        if(cf.getName().equals("setField")){
                            Map<String, Object> additionalProperties = new HashMap<>(cf.getAdditionalProperties());
                            additionalProperties.put("values",getChildFields(node));
                            cf.setAdditionalProperties(additionalProperties);
                        }
                    });
                });
                return clone;
            });
        }

        this.nodeConfig = functionDefinition
                .map(f -> objectTransformer.toFunctionDef(f));
        if ("filter".equals(simpleFunctionNodeConfig.getApiName())) {
            Set<MappingNode> sources = nodeHelper.findConnectedSources(node, graph);
            List<Edge> inboundEdges = graph.getInboundEdges(node);
            Stream<EntityDefinition> entityDefinitions = findConnectedEntities(sources);

            EntityDefinition coreEntity = getCoreEntity();
            this.nodeConfig = addToFilterConfig(entityDefinitions, this.nodeConfig, inboundEdges, coreEntity, node);
        } else if ("loop".equals(simpleFunctionNodeConfig.getApiName())) {
            this.nodeConfig = addToLoopConfig(this.nodeConfig, node);
        }else if ( "concatenate".equals(simpleFunctionNodeConfig.getApiName())) {
            Set<MappingNode> sources = nodeHelper.findConnectedSources(node, graph);
            List<Edge> inboundEdges = graph.getInboundEdges(node);
            Stream<EntityDefinition> entityDefinitions = findConnectedEntities(sources);
            EntityDefinition coreEntity = getCoreEntity();
            List<KeyValue> fieldPicklist = getContextFields(entityDefinitions, inboundEdges, coreEntity);
             this.nodeConfig.ifPresent(n->{
                 n.replaceConfigValue("values","values",fieldPicklist);
                 n.replaceConfigValue("values","datatype","multiselect");
                 n.replaceConfigValue("values","mapping",List.of(new KeyValue("graphKey", "configuration.values")
                         .set("configKey", "value")));
             });
        } else if ( "attachRecord".equals(simpleFunctionNodeConfig.getApiName())) {
            Set<MappingNode> sources = nodeHelper.findConnectedSources(node, graph);

            var entityDefinitions = sources.stream().filter(s -> s.getType() == MappingNodeType.ENTITY_SOURCE || s.getType() == MappingNodeType.CORE_ENTITY)
                    .map(source -> schemaService
                            .getEntity(source.getConfiguration().getConfigMap().get("entityDefinition").toString()));
            Stream<EntityDefinition> allSources = graph.getSources().map(s -> schemaService
                    .getEntity(s.getConfiguration().getConfigMap().get("entityDefinition").toString()));
            Stream<EntityDefinition> allSinks = graph.getSinks().map(s-> schemaService
                    .getEntity(s.getConfiguration().getConfigMap().get("entityDefinition").toString()));
            Set<KeyValue> allExternalEntities = new LinkedHashSet<>();
            Function<EntityDefinition, String> entityDefinitionToSynapseName = e->Optional.ofNullable(connectors.get(e.getConnectorId()))
                    .map(c->c.getName()).orElse(i18n("unknown_synapse"));
            allSources.forEach(source-> allExternalEntities.add(new KeyValue("label",String.format("%s (%s)",
                    source.getDisplayName(),entityDefinitionToSynapseName.apply(source) ) ,"value",source.getId())));
            allSinks.forEach(sink-> allExternalEntities.add(new KeyValue("label",String.format("%s (%s)",
                    sink.getDisplayName(),entityDefinitionToSynapseName.apply(sink) ),"value",sink.getId())));

            this.nodeConfig.ifPresent(cfg -> {
                var fieldPicklist = createFieldPicklistFromInput(entityDefinitions);
                var inputFielId = new KeyValue().set("name", "inputFieldId").set("datatype", "picklist")
                        .set("label", "With Input Field").set("defaultValue", "").set("values", fieldPicklist)
                        .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "inputFieldId")
                                .set("configKey", "value")));
                cfg.replaceConfig(inputFielId);
                cfg.replaceConfigValue("externalEntityDefId","values", List.copyOf(allExternalEntities));
            });

        }  else if ( "advancedAttachRecord".equals(simpleFunctionNodeConfig.getApiName())) {
            EntityDefinition entity = schemaService.getEntity(graph.getTargetId());
            List<KeyValue> conditionFields = getContextFields(Stream.empty(), List.of(), entity);
            this.nodeConfig.ifPresent(config-> config.replaceConfigValue("field","values",conditionFields));

        } else if (
                ("firstOnEntity".equals(simpleFunctionNodeConfig.getApiName()) ||
                "setValueOnEntity".equals(simpleFunctionNodeConfig.getApiName())
                )
                && simpleFunctionNodeConfig.getFunctionCall().getFunctionDefinition().getScope() == Scope.ENTITY) {
            Set<MappingNode> sources = nodeHelper.findConnectedSources(node, graph);

            var entityDefinitions = sources.stream().filter(s -> s.getType() == MappingNodeType.ENTITY_SOURCE || s.getType() == MappingNodeType.CORE_ENTITY)
                    .map(source -> schemaService
                            .getEntity(source.getConfiguration().getConfigMap().get("entityDefinition").toString()));
            this.nodeConfig.ifPresent(cfg -> {
                var fieldPicklist = createFieldPicklistFromInput(entityDefinitions);
                if("setValueOnEntity".equals(simpleFunctionNodeConfig.getApiName())) {
                	cfg.replaceConfigValue("setValueField", "attributeValues", fieldPicklist);
                	cfg.removeConfig("attributeDefinitionId");
                } else {
	                var inputFielId = new KeyValue().set("name", "attributeDefinitionId").set("datatype", "picklist")
	                        .set("label", "Field Name").set("defaultValue", "").set("values", fieldPicklist)
	                        .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "attributeDefinitionId")
	                                .set("configKey", "value")));
	                cfg.replaceConfig(inputFielId);
                }
            });
        } else if ("enrichPerson".equalsIgnoreCase(simpleFunctionNodeConfig.getApiName())) {
            nodeConfig.ifPresent(cfg -> cfg.setConfiguration(setEnrichPersonConfig(node)));
        } else if ("enrichCompany".equalsIgnoreCase(simpleFunctionNodeConfig.getApiName())) {
            nodeConfig.ifPresent(cfg -> cfg.setConfiguration(setEnrichCompanyConfig(node)));
        } else if (SalesIntelFunctionsSeed.SALESINTEL_PERSON_ENRICH.equalsIgnoreCase(simpleFunctionNodeConfig.getApiName())) {
            nodeConfig.ifPresent(cfg -> cfg.setConfiguration(setSalesIntelPersonEnrichConfig(node)));
        }else if (SalesIntelFunctionsSeed.SALESINTEL_COMPANY_ENRICH.equalsIgnoreCase(simpleFunctionNodeConfig.getApiName())) {
            nodeConfig.ifPresent(cfg -> cfg.setConfiguration(setSalesIntelCompanyEnrichConfig(node)));
        } else if ("setValue".equals(simpleFunctionNodeConfig.getApiName())) {
            nodeConfig.ifPresent(cfg -> {
                var dataTypeValues = cfg.findConfigurationByName("dataType").map(kv -> kv.get("values")).orElse(null);
                cfg.replaceConfigValue("setValueField", "dataTypeValues", dataTypeValues);
                cfg.removeConfig("dataType");

            });
        } else if ("lookupDataset".equals(simpleFunctionNodeConfig.getApiName()) || "lookupDatasetOnField".equals(simpleFunctionNodeConfig.getApiName())) {
            nodeConfig.ifPresent(cfg -> {
                final Optional<KeyValue> datasetId = cfg.findConfigurationByName("datasetId");
                datasetId.ifPresent(dsId -> {
                    dsId.set("values", datasetService.getAllActiveDatasets().stream().map(d ->
                            new KeyValue("label", d.getDisplayName(), "value", d.getId())
                    ).collect(Collectors.toList()));
                    if (dsId.get("value") != null) {
                        Dataset dataset = datasetService.getDataset(dsId.get("value"));
                        final Map<String, Variable> variablesMap = dataset.getVariablesMap();
                        variablesMap.forEach((variableName, variableConfig) -> {
                            cfg.addConfiguration(new KeyValue("value", variableName)
                                    .set("label", variableConfig.getDisplayName())
                                    .set("datatype", variableConfig.getDatatype())
                                    .set("defaultValue", variableConfig.getVariableValue().getDefaultValue())
                            );
                        });
                    }
                });
            });
        } else if(FunctionConstants.CASE.equalsIgnoreCase(simpleFunctionNodeConfig.getApiName())){
            nodeConfig.ifPresent(cfg -> {
                Set<MappingNode> sources = nodeHelper.findConnectedSources(node, graph);
                List<Edge> inboundEdges = graph.getInboundEdges(node);
                Stream<EntityDefinition> entityDefinitions = findConnectedEntities(sources);
                EntityDefinition coreEntity = getCoreEntity();
                if (cfg.findConfigurationByName(CaseFunction.CASE).isPresent()){
                    List<KeyValue> fieldValues = getCaseConditionFields(entityDefinitions, inboundEdges, coreEntity, node);
                    KeyValue cases = new KeyValue();
                    cases.set("datatypes", FunctionsSeed.getSupportedDataTypes());
                    KeyValue fields = new KeyValue();
                    fields.set("fieldValues", fieldValues);
                    cases.set(CaseFunction.PREDICATE, fields);
                    cfg.findConfigurationByName(CaseFunction.CASE).get().set(CaseFunction.CASES, cases);
                }
            });
        } else if(FunctionConstants.CASE_BRANCH.equalsIgnoreCase(simpleFunctionNodeConfig.getApiName())) {
            nodeConfig.ifPresent(cfg -> {
                String currNodeId = node.getId();
                List<Edge> connectingEdge = graph.getEdges().stream().filter(e -> e.getDestinationStage().getId().equals(currNodeId)).collect(Collectors.toList());
                if (connectingEdge.size() == 1) {
                    String caseFunctionNodeId = connectingEdge.get(0).getSourceStage().getId();
                    List<String> cases = getCasesFromCaseNode(caseFunctionNodeId);
                    List<Map<String, String>> values = new ArrayList<>();
                    for (String caseName: cases){
                        Map<String, String> m = new HashMap<>();
                        m.put("label", caseName);
                        m.put("value", caseName);
                        values.add(m);
                    }
                    if (cfg.findConfigurationByName(FunctionConstants.CASE_BRANCH).isPresent()){
                        cfg.findConfigurationByName(FunctionConstants.CASE_BRANCH).get().set("values", values);
                    }
                }
            });
        }
    }

    private List<String> getCasesFromCaseNode(String nodeId){
        Optional<MappingNode> caseNode = graph.getNodes().stream().filter(n -> n.getId().equals(nodeId)).findAny();
        if (caseNode.isPresent()){
            MappingNode node = caseNode.get();
            return new ArrayList<>(CaseFunction.getCaseListFromConfig(node.getConfiguration().getConfigMap()));
        }
        return List.of();
    }

    private List<KeyValue> getChildFields(MappingNode node) {

        List<MappingNode> sinks = graph.getConnectedSinksWithNode(node).collect(Collectors.toList());
        //node is connected to a sink. so return sink child schema fields
        if(!sinks.isEmpty()){
            AttributeSinkNodeConfig sinkNodeConfig = sinks.get(0).getTypedConfiguration();
            EntityDefinition sinkEntity = schemaService.getEntity(sinkNodeConfig.getAttributeDefinition().getEntityId());
            Optional<EntityDefinition> childSinkEntity = schemaService.findChildEntity(sinkEntity.getConnectorId(), sinkEntity.getFieldByName(sinkNodeConfig.getAttributeDefinition().getApiName()).getReferenceTo());
            return childSinkEntity.map(c->c.getActiveAttributes().stream().map(a->new KeyValue("label",a.getDisplayName()).set("value",a.getId())).collect(Collectors.toList())).orElse(List.of());
        }
        MappingNode coreNode = graph.getCoreNode();
        //node is connected to core. So return core child scgema fields
        if(graph.pathToNodeMatches(coreNode, n->n.getId().equals(node.getId()))){
            CoreAttributeNodeConfig coreNodeConfig = coreNode.getTypedConfiguration();
            EntityDefinition coreEntity = schemaService.getEntity(coreNodeConfig.getAttributeDefinition().getEntityId());
            Optional<EntityDefinition> childCoreEntity = schemaService.findChildEntity(coreEntity.getConnectorId(), coreEntity.getFieldByName(coreNodeConfig.getAttributeDefinition().getApiName()).getReferenceTo());
            return childCoreEntity.map(c->c.getActiveAttributes().stream().map(a->new KeyValue("label",a.getDisplayName()).set("value",a.getId())).collect(Collectors.toList())).orElse(List.of());
        }
        return List.of();
    }

    public Stream<EntityDefinition> findConnectedEntities(Set<MappingNode> sources) {
        return sources
                        .stream().map(
                                source -> source.getType() == MappingNodeType.ATTRIBUTE_SOURCE
                                        || source.getType() == MappingNodeType.CORE_ATTRIBUTE
                                                ? schemaService
                                                        .getEntity(schemaService
                                                                .getAttribute(source.getConfiguration().getConfigMap()
                                                                        .get("attributeDefinition").toString())
                                                                .getEntityId())
                                                : schemaService.getEntity(source.getConfiguration().getConfigMap()
                                                        .get("entityDefinition").toString()));
    }

    private EntityDefinition getCoreEntity() {
        String entityId = graph.getScope() == Scope.ATTRIBUTE ? getEntityIdFromAttributeConfig() : getEntityIdFromEntityConfig();
        return schemaService.getEntity(entityId);
    }

    private String getEntityIdFromEntityConfig() {
        CoreEntityNodeConfig config = graph.getCoreNode().getTypedConfiguration();
        return config.getEntityDefinition().getId();
    }

    private  String getEntityIdFromAttributeConfig() {
        CoreAttributeNodeConfig config = graph.getCoreNode().getTypedConfiguration();
        return config.getAttributeDefinition().getEntityId();
    }

    private ArrayList<KeyValue> createFieldPicklistFromInput(Stream<EntityDefinition> entityDefinitions) {
        var fieldPicklist = new ArrayList<>(entityDefinitions.flatMap(e -> e.getActiveAttributes().stream()
                .map(attribute -> new KeyValue("value", attribute.getId())
                        .set("label", String.format("%s (%s)", attribute.getDisplayName(), attribute.getApiName())).set("type", "variable")
                        .set("datatype", attribute.getDataType().getName()).set("picklistGroup",
                                String.format("%s (%s)", e.getDisplayName(),
                                        connectors.containsKey(e.getConnectorId())
                                                ? connectors.get(e.getConnectorId()).getName()
                                                : "Syncari"))))
                .collect(Collectors.toList()));
        return fieldPicklist;
    }

    private Optional<NodeDef> addToLoopConfig(Optional<NodeDef> loopFunction, MappingNode node ) {

        return loopFunction.map(loopNodeConfig -> {
            var newConfigs = new ArrayList<KeyValue>();
            KeyValue attributeDefinition = getLoopConditionFields(node);
            newConfigs.add(attributeDefinition);

            loopNodeConfig.getConfiguration().stream().map(kv -> {
                if (kv.get("name").equals("startIndex")) {
                    kv.set("dependsOnFieldValue", true);
                    kv.set("visibilityDependsOnFieldValue", Map.of("fieldName", "configuration.option", "fieldValue", "index"));
                } else if (kv.get("name").equals("endIndex")) {
                    kv.set("dependsOnFieldValue", true);
                    kv.set("visibilityDependsOnFieldValue", Map.of("fieldName", "configuration.option", "fieldValue", "index"));
                } else if (kv.get("name").equals("variable")) {
                    kv.set("dependsOnFieldValue", true);
                    kv.set("visibilityDependsOnFieldValue", Map.of("fieldName", "configuration.option", "fieldValue", "variable"));
                } else if (kv.get("name").equals("predicate")) {
                    kv.set("dependsOnFieldValue", true);
                    kv.set("visibilityDependsOnFieldValue", Map.of("fieldName", "configuration.option", "fieldValue", "condition"));
                } else if (kv.get("name").equals("maxLoop")) {
                    kv.set("dependsOnFieldValue", true);
                    kv.set("visibilityDependsOnFieldValue", Map.of("fieldName", "configuration.option", "fieldValue", "condition"));
                }
                return kv;
            }).forEach(kv -> {
                newConfigs.add(kv);
            });

            loopNodeConfig.setConfiguration(newConfigs);
            return loopNodeConfig;
        });
    }

    private Optional<NodeDef> addToFilterConfig(Stream<EntityDefinition> entityDefinitions,
                                                Optional<NodeDef> filterFunction, List<Edge> inboundEdges, EntityDefinition coreEntity, MappingNode node) {
        return filterFunction.map(filterNodeConfig -> {
            Set<String> overriddenConfigNames = Set.of("field");
            KeyValue attributeDefinition = getConditionFields(entityDefinitions, inboundEdges, coreEntity, node);
            var newConfig = new ArrayList<KeyValue>();
            var valueConfig = filterNodeConfig.getConfiguration().stream().filter(c -> c.get("name").equals("value"))
                    .findFirst();
            valueConfig.ifPresent(config -> {
                config.set("type", "literal");
            });
            newConfig.add(attributeDefinition);
            newConfig.addAll(filterNodeConfig.getConfiguration().stream()
                    .filter(kv -> !overriddenConfigNames.contains(kv.get("name"))).collect(Collectors.toList()));
            filterNodeConfig.setConfiguration(newConfig);
            return filterNodeConfig;
        });
    }

    private Optional<NodeDef> addToUpdateExternalRecordConfig(Optional<NodeDef> actionDef, List<Edge> inboundEdges, MappingNode node) {
        return actionDef.map(actionNodeConfig -> {
            List<KeyValue> fieldPicklist = getUpdateExternalRecordFields(inboundEdges, node);

            // Find and update the updateField configuration within updateFields
            actionNodeConfig.getConfiguration().stream()
                    .filter(c -> "updateFields".equals(c.get("name")))
                    .findFirst()
                    .ifPresent(updateFieldsConfig -> {
                        List<Object> configuration = (List<Object>) updateFieldsConfig.get("configuration");
                        if (configuration != null) {
                            configuration.stream()
                                    .filter(cfg -> cfg instanceof KeyValue)
                                    .map(cfg -> (KeyValue) cfg)
                                    .filter(kv -> "updateField".equals(kv.get("name")))
                                    .findFirst()
                                    .ifPresent(updateFieldKv -> {
                                        updateFieldKv.set("values", fieldPicklist);
                                    });
                        }
                    });

            return actionNodeConfig;
        });
    }

    private List<KeyValue> getUpdateExternalRecordFields(List<Edge> inboundEdges, MappingNode currentNode) {
        List<KeyValue> fieldPicklist = new ArrayList<>();

        // 2. Previous Nodes (outputs from previous nodes)
        var inputPicklist = inboundEdges.stream().map(
                edge -> new KeyValue("value", String.format("output_%s.x.typedValue", edge.getSourceStage().getId()))
                        .set("label", String.format("Output from %s", edge.getSourceStage().getName()))
                        .set("type", "variable").set("datatype", edge.getInput().getDatatype().getName())
                        .set("picklistGroup", "Previous Nodes"))
                .collect(Collectors.toList());

        // 4. Previous Lookups
        var lookupResultPicklist = inboundEdges.stream().filter(edge -> isFromLookupSyncariRecord(edge)).flatMap(
                edge -> {
                    List<KeyValue> lookupResultFilters = new ArrayList<>();
                    lookupResultFilters.add(new KeyValue("value", String.format("output_%s.x.lookupResult", edge.getSourceStage().getId()))
                            .set("label", String.format("Lookup Result from %s", edge.getSourceStage().getName()))
                            .set("type", "variable").set("datatype", edge.getInput().getDatatype())
                            .set("picklistGroup", "Previous Lookups")
                    );

                    if ("true".equals(edge.getSourceStage().getConfiguration().getConfigMap().getOrDefault("count", "false").toString())) {
                        lookupResultFilters.add(new KeyValue("value", String.format("output_%s.x.lookupCount", edge.getSourceStage().getId()))
                                .set("label", i18n("lookup_count_filter_label", edge.getSourceStage().getName()))
                                .set("type", "variable").set("datatype", IntegerType.VALUE)
                                .set("picklistGroup", "Previous Lookups")
                        );
                    }
                    return lookupResultFilters.stream();
                }).collect(Collectors.toList());

        var lookupExternalPicklist = inboundEdges.stream().filter(edge -> edge.getSourceStage().getConfiguration().getApiName().equalsIgnoreCase("lookupExternalRecord")).flatMap(
                edge -> {
                    List<KeyValue> lookupResultFilters = new ArrayList<>();
                    lookupResultFilters.add(new KeyValue("value", String.format("Records from %s", edge.getSourceStage().getName()))
                            .set("label", String.format("Records from %s", edge.getSourceStage().getName()))
                            .set("type", "variable").set("datatype", edge.getInput().getDatatype())
                            .set("picklistGroup", "Previous Lookups")
                    );

                    return lookupResultFilters.stream();
                }).collect(Collectors.toList());

        // 5. Previous Actions
        List<KeyValue> actionPicklist = inboundEdges.stream().filter(edge -> isAction(edge)).flatMap(
                edge -> {
                    return List.of(
                            new KeyValue("value", String.format("action_output_%s_status", edge.getSourceStage().getId()))
                                    .set("label", String.format(i18n("status_of_action_filter_label"), edge.getSourceStage().getName()))
                                    .set("type", "variable").set("datatype", BooleanType.VALUE.getName())
                                    .set("picklistGroup", "Previous Actions"),
                            new KeyValue("value", String.format("action_output_%s_result", edge.getSourceStage().getId()))
                                    .set("label", String.format(i18n("result_of_action_filter_label"), edge.getSourceStage().getName()))
                                    .set("type", "variable").set("datatype", edge.getInput().getDatatype())
                                    .set("picklistGroup", "Previous Actions")
                    ).stream();
                }).collect(Collectors.toList());

        // 6. Destination
        List<KeyValue> sinkNodePicklist = inboundEdges.stream().filter(edge -> isSinkNode(edge)).flatMap(
                edge -> {
                    return List.of(
                            new KeyValue("value", "destination_status")
                                    .set("label", i18n("destination_status_label"))
                                    .set("type", "variable").set("datatype", BooleanType.VALUE.getName())
                                    .set("picklistGroup", "Destination"),
                            new KeyValue("value", "destination_operation")
                                    .set("label", i18n("destination_operation_label"))
                                    .set("type", "variable").set("datatype", StringType.VALUE.getName())
                                    .set("picklistGroup", "Destination"),
                            new KeyValue("value", "destination_error")
                                    .set("label", i18n("destination_error_label"))
                                    .set("type", "variable").set("datatype", StringType.VALUE.getName())
                                    .set("picklistGroup", "Destination")
                    ).stream();
                }).collect(Collectors.toList());

        // 7. Temporary Variables from Tokens
        List<KeyValue> tokens = new ArrayList<>();
        tokens.addAll(tokenController.getLookUpTokens(graph, currentNode));
        tokens.addAll(tokenController.getTempVariableTokens(graph, currentNode));
        tokens.stream().forEach(temp -> {
            String datatype = BooleanUtils.isTrue(temp.get("multiValueField")) ? "picklist" : temp.get("datatype");
            fieldPicklist.add(new KeyValue("value", temp.get("token"))
                    .set("label", temp.get("shortLabel"))
                    .set("type", "variable").set("datatype", datatype)
                    .set("picklistGroup", temp.get("group")));
        });

        // 8. Misc
        fieldPicklist.add(new KeyValue("value", "incoming_change")
                .set("label", I18n.i18n("incoming_change"))
                .set("type", "variable").set("datatype", "exact_match")
                .set("picklistGroup", "Misc"));

        // Add all groups to fieldPicklist
        fieldPicklist.addAll(inputPicklist);
        fieldPicklist.addAll(lookupResultPicklist);
        fieldPicklist.addAll(lookupExternalPicklist);
        fieldPicklist.addAll(actionPicklist);
        fieldPicklist.addAll(sinkNodePicklist);

        return fieldPicklist;
    }


    private List<KeyValue> getCaseConditionFields(Stream<EntityDefinition> entityDefinitions, List<Edge> inboundEdges, EntityDefinition coreEntity, MappingNode currentNode) {
        List<KeyValue> fieldPicklist = getContextFields(entityDefinitions, inboundEdges, coreEntity);
        List<KeyValue> tokens = new ArrayList<>();
        tokens.addAll(tokenController.getLookUpTokens(graph, currentNode));
        tokens.addAll(tokenController.getTempVariableTokens(graph, currentNode));
        tokens.stream().forEach(temp -> {
            String datatype = BooleanUtils.isTrue(temp.get("multiValueField")) ? "picklist" : temp.get("datatype");
            fieldPicklist.add(new KeyValue("value", temp.get("token"))
                    .set("label", temp.get("shortLabel"))
                    .set("type", "variable").set("datatype", datatype)
                    .set("picklistGroup", temp.get("group")));
        });
        return fieldPicklist;
    }


    // TODO: add system fields
    private KeyValue getConditionFields(Stream<EntityDefinition> entityDefinitions, List<Edge> inboundEdges, EntityDefinition coreEntity, MappingNode currentNode) {
        List<KeyValue> fieldPicklist = getContextFields(entityDefinitions, inboundEdges, coreEntity);
        //Enhance with tokens
        List<KeyValue> tokens = new ArrayList<>();
        tokens.addAll(tokenController.getLookUpTokens(graph, currentNode));
        tokens.addAll(tokenController.getTempVariableTokens(graph, currentNode));
        tokens.stream().forEach(temp -> {
        	String datatype = BooleanUtils.isTrue(temp.get("multiValueField")) ? "picklist" : temp.get("datatype");
        	fieldPicklist.add(new KeyValue("value", temp.get("token"))
            .set("label", temp.get("shortLabel"))
            .set("type", "variable").set("datatype", datatype)
            .set("picklistGroup", temp.get("group")));
        });
        return new KeyValue().set("name", "field").set("datatype", "picklist").set("label", "Field")
                .set("defaultValue", "").set("fieldSet", "conditionFields").set("values", fieldPicklist)
                .set("mapping", List.of(
                        new KeyValue("graphKey", "configuration." + "attributeDefinition").set("configKey", "value")));
    }

    private KeyValue getLoopConditionFields(MappingNode currentNode) {
        List<KeyValue> fieldPicklist = new ArrayList<>();
        //Enhance with tokens
        List<KeyValue> tokens = new ArrayList<>();
        tokens.addAll(tokenController.getTempVariableTokens(graph, currentNode));
        tokens.stream().forEach(temp -> {
            String datatype = BooleanUtils.isTrue(temp.get("multiValueField")) ? "picklist" : temp.get("datatype");
            fieldPicklist.add(new KeyValue("value", temp.get("token"))
                    .set("label", temp.get("shortLabel"))
                    .set("type", "variable").set("datatype", datatype)
                    .set("picklistGroup", temp.get("group")));
        });
        return new KeyValue().set("name", "field").set("datatype", "picklist").set("label", "Field")
                .set("defaultValue", "").set("fieldSet", "conditionFields").set("values", fieldPicklist)
                .set("mapping", List.of(
                        new KeyValue("graphKey", "configuration." + "attributeDefinition").set("configKey", "value")));
    }


    private List<KeyValue> getContextFields(Stream<EntityDefinition> entityDefinitions, List<Edge> inboundEdges, EntityDefinition coreEntity) {
        List<EntityDefinition> entityDefinitionList = entityDefinitions.collect(Collectors.toList());
        var fieldPicklist = createFieldPicklistFromInput(entityDefinitionList.stream());
        var inputPicklist = inboundEdges.stream().map(
                edge -> new KeyValue("value", String.format("output_%s.x.typedValue", edge.getSourceStage().getId()))
                        .set("label", String.format("Output from %s", edge.getSourceStage().getName()))
                        .set("type", "variable").set("datatype", edge.getInput().getDatatype().getName())
                        .set("picklistGroup", "Previous Nodes"))
                .collect(Collectors.toList());
        List<KeyValue> syncariPicklist = new ArrayList<>();
        //Add core attributes to filter, if not present already
        if(entityDefinitionList.stream().filter(e->e.getId().equals(coreEntity.getId())).findFirst().isEmpty()){
            coreEntity.getActiveAttributes().forEach(coreAttribute->{
                syncariPicklist.add(new KeyValue("value", coreAttribute.getId())
                        .set("label", String.format("Syncari: %s", coreAttribute.getDisplayName()))
                        .set("type", "variable").set("datatype", coreAttribute.getDataType().getName())
                        .set("picklistGroup", "Existing Record"));
            });
            syncariPicklist.add(new KeyValue("value", "syncari.isDeleted")
                    .set("label", "Syncari: Is Record Deleted")
                    .set("type", "variable").set("datatype", "boolean")
                    .set("picklistGroup", "Existing Record"));

        }
        var lookupResultPicklist = inboundEdges.stream().filter(edge -> isFromLookupSyncariRecord(edge)).flatMap(
                edge -> {
                    List<KeyValue> lookupResultFilters = new ArrayList<>();
                    lookupResultFilters.add(new KeyValue("value", String.format("output_%s.x.lookupResult", edge.getSourceStage().getId()))
                            .set("label", String.format("Lookup Result from %s", edge.getSourceStage().getName()))
                            .set("type", "variable").set("datatype", edge.getInput().getDatatype())
                            .set("picklistGroup", "Previous Lookups")
                    );

                    if ("true".equals(edge.getSourceStage().getConfiguration().getConfigMap().getOrDefault("count", "false").toString())) {
                        lookupResultFilters.add(new KeyValue("value", String.format("output_%s.x.lookupCount", edge.getSourceStage().getId()))
                                .set("label", i18n("lookup_count_filter_label", edge.getSourceStage().getName()))
                                .set("type", "variable").set("datatype", IntegerType.VALUE)
                                .set("picklistGroup", "Previous Lookups")
                        );
                    }
                    return lookupResultFilters.stream();
                }).collect(Collectors.toList());

        var lookupExternalPicklist = inboundEdges.stream().filter(edge -> edge.getSourceStage().getConfiguration().getApiName().equalsIgnoreCase("lookupExternalRecord")).flatMap(
                edge -> {
                    List<KeyValue> lookupResultFilters = new ArrayList<>();
                    lookupResultFilters.add(new KeyValue("value", String.format("Records from %s", edge.getSourceStage().getName()))
                            .set("label", String.format("Records from %s", edge.getSourceStage().getName()))
                            .set("type", "variable").set("datatype", edge.getInput().getDatatype())
                            .set("picklistGroup", "Previous Lookups")
                    );

                    return lookupResultFilters.stream();
                }).collect(Collectors.toList());

        List<KeyValue> actionPicklist = inboundEdges.stream().filter(edge -> isAction(edge)).flatMap(
                edge -> {
                    return List.of(
                            new KeyValue("value", String.format("action_output_%s_status", edge.getSourceStage().getId()))
                                    .set("label", String.format(i18n("status_of_action_filter_label"), edge.getSourceStage().getName()))
                                    .set("type", "variable").set("datatype", BooleanType.VALUE.getName())
                                    .set("picklistGroup", "Previous Actions"),
                            new KeyValue("value", String.format("action_output_%s_result", edge.getSourceStage().getId()))
                                    .set("label", String.format(i18n("result_of_action_filter_label"), edge.getSourceStage().getName()))
                                    .set("type", "variable").set("datatype", edge.getInput().getDatatype())
                                    .set("picklistGroup", "Previous Actions")
                    ).stream();
                }).collect(Collectors.toList());

        List<KeyValue> sinkNodePicklist = inboundEdges.stream().filter(edge -> isSinkNode(edge)).flatMap(
                edge -> {
                    return List.of(
                            new KeyValue("value", "destination_status")
                                    .set("label", i18n("destination_status_label"))
                                    .set("type", "variable").set("datatype", BooleanType.VALUE.getName())
                                    .set("picklistGroup", "Destination"),
                            new KeyValue("value", "destination_operation")
                                    .set("label", i18n("destination_operation_label"))
                                    .set("type", "variable").set("datatype", StringType.VALUE.getName())
                                    .set("picklistGroup", "Destination"),
                            new KeyValue("value", "destination_error")
                                    .set("label", i18n("destination_error_label"))
                                    .set("type", "variable").set("datatype", StringType.VALUE.getName())
                                    .set("picklistGroup", "Destination")
                    ).stream();
                }).collect(Collectors.toList());

        fieldPicklist.addAll(inputPicklist);
        fieldPicklist.addAll(syncariPicklist);
        fieldPicklist.addAll(lookupResultPicklist);
        fieldPicklist.addAll(lookupExternalPicklist);
        fieldPicklist.addAll(actionPicklist);
        fieldPicklist.addAll(sinkNodePicklist);
        fieldPicklist.add(new KeyValue("value", "incoming_change")
                .set("label", I18n.i18n("incoming_change"))
                .set("type", "variable").set("datatype", "exact_match")
                .set("picklistGroup", "Misc"));
        return fieldPicklist;
    }

    private boolean isAction(Edge edge) {
        return edge.getSourceStage().getConfiguration().getNodeType() == MappingNodeType.ACTION;
    }

    private boolean isSinkNode(Edge edge) {
        return edge.getSourceStage().getConfiguration().getNodeType() == MappingNodeType.ATTRIBUTE_SINK || edge.getSourceStage().getConfiguration().getNodeType() == MappingNodeType.ENTITY_SINK;
    }

    private boolean isFromLookupSyncariRecord(Edge edge) {
        return isLookupFunction(edge.getSourceStage().getConfiguration().getApiName());
    }

    private boolean isLookupFunction(String apiName) {
        return apiName.equalsIgnoreCase("lookupSyncariRecord") ||
                apiName.equalsIgnoreCase("advancedLookupSyncariRecord")
                || apiName.equalsIgnoreCase("advancedLookUpSyncariRecordOnField");
    }

    private List<MappingNode> getEntityGraphSources() {
        return getEntityGraph().stream().flatMap(g->g.getSources()).collect(Collectors.toList());
    }

    private Optional<MappingGraph> getEntityGraph() {
        var attributeDefinition = schemaService.getActiveAttribute(graph.getTargetId());
        return graphService.retrieveEntityGraph(attributeDefinition.get().getEntityId(), graph.getDraftStatus());
    }

    private ArrayList<KeyValue> setSalesIntelPersonEnrichConfig(MappingNode currentNode) {
        Set<String> overriddenConfigNames = Set.of("entityDefinition", "email","phoneNumber","lastName","firstName", "lookUpKey");
        Stream<KeyValue> entityPicklist = getEnrichSourceEntities(currentNode);
        var entityDefConfig = new KeyValue().set("name", "entityDefinition")
                .set("datatype", "picklist")
                .set("label", "Source Entity")
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", "Source entity to use for lookup")
                .set("required", true)
                .set("values", entityPicklist)
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "entityDefinition").set("configKey", "value")));
        var email = new KeyValue().set("name", "email")
                .set("datatype", "picklist")
                .set("label", i18n("si_email_label"))
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", i18n("si_email_help"))
                .set("required", true)
                .set("dependsOn", Map.of("dependantType","AttributeList","dependantField","configuration.entityDefinition"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "email").set("configKey", "value")));
        var phoneNumber = new KeyValue().set("name", "phoneNumber")
                .set("datatype", "picklist")
                .set("label", i18n("si_phoneNumber_label"))
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", i18n("si_phoneNumber_help"))
                .set("dependsOn", Map.of("dependantType","AttributeList","dependantField","configuration.entityDefinition"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "phoneNumber").set("configKey", "value")));
        var lastName = new KeyValue().set("name", "lastName")
                .set("datatype", "picklist")
                .set("label", i18n("si_last_name_label"))
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", i18n("si_last_name_help"))
                .set("dependsOn", Map.of("dependantType","AttributeList","dependantField","configuration.entityDefinition"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "lastName").set("configKey", "value")));
        var firstName = new KeyValue().set("name", "firstName")
                .set("datatype", "picklist")
                .set("label", i18n("si_first_name_label"))
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", i18n("si_first_name_help"))
                .set("dependsOn", Map.of("dependantType","AttributeList","dependantField","configuration.entityDefinition"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "firstName").set("configKey", "value")));
        var lookupKeyConfig = new KeyValue().set("name", "lookUpKey")
                .set("datatype", "picklist")
                .set("label", "Enrichment Field")
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", "Field from enrichment source to use for enriching records")
                .set("required", true)
                .set("dependsOn", Map.of("dependantType", "Contact.LookupFields", "dependantField", "configuration.serviceId"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "lookUpKey").set("configKey", "value")));
        var newConfig = new ArrayList<KeyValue>();
        newConfig.addAll(this.nodeConfig.get().getConfiguration().stream().filter(kv -> !overriddenConfigNames.contains(kv.get("name"))).collect(Collectors.toList()));
        newConfig.add(entityDefConfig);
        newConfig.add(email);
        newConfig.add(phoneNumber);
        newConfig.add(lastName);
        newConfig.add(firstName);
        newConfig.add(lookupKeyConfig);
        return newConfig;
    }

    private ArrayList<KeyValue> setSalesIntelCompanyEnrichConfig(MappingNode currentNode) {
        Set<String> overriddenConfigNames = Set.of("entityDefinition", "companyName","companyDomain","companyIndustries","companyLocationStates", "lookUpKey");
        Stream<KeyValue> entityPicklist = getEnrichSourceEntities(currentNode);
        var entityDefConfig = new KeyValue().set("name", "entityDefinition")
                .set("datatype", "picklist")
                .set("label", "Source Entity")
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", "Source entity to use for lookup")
                .set("required", true)
                .set("values", entityPicklist)
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "entityDefinition").set("configKey", "value")));
        var companyNameConfig = new KeyValue().set("name", "companyName")
                .set("datatype", "picklist")
                .set("label", i18n("si_company_name_label"))
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", i18n("si_company_name_help"))
                .set("dependsOn", Map.of("dependantType","AttributeList","dependantField","configuration.entityDefinition"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "companyName").set("configKey", "value")));
        var companyDomainConfig = new KeyValue().set("name", "companyDomain")
                .set("datatype", "picklist")
                .set("label", i18n("si_company_domain_label"))
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", i18n("si_company_domain_help"))
                .set("required", true)
                .set("dependsOn", Map.of("dependantType","AttributeList","dependantField","configuration.entityDefinition"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "companyDomain").set("configKey", "value")));
        var companyIndustriesConfig = new KeyValue().set("name", "companyIndustries")
                .set("datatype", "picklist")
                .set("label", i18n("si_company_industries_label"))
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", i18n("si_company_industries_help"))
                .set("dependsOn", Map.of("dependantType","AttributeList","dependantField","configuration.entityDefinition"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "companyIndustries").set("configKey", "value")));
        var companyLocationStatesConfig = new KeyValue().set("name", "companyLocationStates")
                .set("datatype", "picklist")
                .set("label", i18n("si_company_location_states_label"))
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", i18n("si_company_location_states_help"))
                .set("dependsOn", Map.of("dependantType","AttributeList","dependantField","configuration.entityDefinition"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "companyLocationStates").set("configKey", "value")));
        var lookupKeyConfig = new KeyValue().set("name", "lookUpKey")
                .set("datatype", "picklist")
                .set("label", "Enrichment Field")
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", "Field from enrichment source to use for enriching records")
                .set("required", true)
                .set("dependsOn", Map.of("dependantType", "Company.LookupFields", "dependantField", "configuration.serviceId"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "lookUpKey").set("configKey", "value")));
        var newConfig = new ArrayList<KeyValue>();
        newConfig.addAll(this.nodeConfig.get().getConfiguration().stream().filter(kv -> !overriddenConfigNames.contains(kv.get("name"))).collect(Collectors.toList()));
        newConfig.add(entityDefConfig);
        newConfig.add(companyNameConfig);
        newConfig.add(companyDomainConfig);
        newConfig.add(companyIndustriesConfig);
        newConfig.add(companyLocationStatesConfig);
        newConfig.add(lookupKeyConfig);
        return newConfig;
    }

    private List<String> getZoomInfoServiceIds(MappingNode currentNode) {
        Optional<KeyValue> serviceIdList = this.nodeConfig.get().getConfiguration().stream().filter(kv->kv.get("name").equals("serviceId")).findFirst();
        List<String> zoomInfoIds = new ArrayList<>();
        if (!serviceIdList.isEmpty() && serviceIdList.get().get("values") != null){
            List<KeyValue> keyValueList = (List<KeyValue>)serviceIdList.get().get("values");
            if (!keyValueList.isEmpty()){
                for (KeyValue keyvalue:keyValueList){
                    String connectorId = (String)keyvalue.get("value");
                    Optional<Connector> connector = connectorService.find(connectorId);
                    if(!connector.isEmpty() && "zoominfo".equalsIgnoreCase(connector.get().getMetadata().getName())){
                        zoomInfoIds.add(connectorId);
                    }

                }
            }
        }
        // Add a default Id to skip showing the fields
        if (zoomInfoIds.isEmpty()){
            zoomInfoIds.add("defaultZoomId");
        }
        return zoomInfoIds;
    }

    private ArrayList<KeyValue> setEnrichPersonConfig(MappingNode currentNode) {
        Set<String> overriddenConfigNames = Set.of("entityDefinition", "emailField", "additionalEnrichUsing", "additionalEnrichField", "lookUpKey");
        Stream<KeyValue> entityPicklist = getEnrichSourceEntities(currentNode);
        List<String> zoomInfoIds = getZoomInfoServiceIds(currentNode);
        var entityDefConfig = new KeyValue().set("name", "entityDefinition")
                .set("datatype", "picklist")
                .set("label", "Source Entity")
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", "Source entity to use for lookup")
                .set("required", true)
                .set("values", entityPicklist)
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "entityDefinition").set("configKey", "value")));
        var emailFieldConfig = new KeyValue().set("name", "emailField")
                .set("datatype", "picklist")
                .set("label", "Input Field")
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", "Field from source entity to use for lookup")
                .set("required", true)
                .set("dependsOn", Map.of("dependantType","AttributeList","dependantField","configuration.entityDefinition"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "emailField").set("configKey", "value")));
        var additionalEnrichUsingConfig = new KeyValue().set("name", "additionalEnrichUsing")
                .set("datatype", "picklist")
                .set("dependsOnFieldValue", true)
                .set("visibilityDependsOnFieldValue",List.of(new KeyValue("fieldName","configuration."+"serviceId").set("fieldValue",String.join("|",zoomInfoIds))))
                .set("label", "Additional Enrichment Using")
                .set("defaultValue", "")
                .set("implicit", false)
                .set("allowClear", true)
                .set("helpSummary", "Additional Enrichment Using")
                .set("values", List.of(
                                new KeyValue("value", "").set("label", ""),
                                new KeyValue("value", "companyId").set("label", "Company Id"),
                                new KeyValue("value", "companyName").set("label", "Company Name")
                ))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "additionalEnrichUsing").set("configKey", "value")));
        var additionalEnrichFieldConfig = new KeyValue().set("name", "additionalEnrichField")
                .set("datatype", "picklist")
                .set("dependsOnFieldValue", true)
                .set("visibilityDependsOnFieldValue",List.of(new KeyValue("fieldName","configuration."+"serviceId").set("fieldValue",String.join("|",zoomInfoIds))))
                .set("label", "Additional Enrichment Field")
                .set("defaultValue", "")
                .set("implicit", false)
                .set("allowClear", true)
                .set("helpSummary", "Additional Enrichment Field")
                .set("dependsOn", Map.of("dependantType","AttributeList","dependantField","configuration.entityDefinition"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "additionalEnrichField").set("configKey", "value")));
        var lookupKeyConfig = new KeyValue().set("name", "lookUpKey")
                .set("datatype", "picklist")
                .set("label", "Enrichment Field")
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", "Field from enrichment source to use for enriching records")
                .set("required", true)
                .set("dependsOn", Map.of("dependantType", "Contact.LookupFields", "dependantField", "configuration.serviceId"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "lookUpKey").set("configKey", "value")));
        var newConfig = new ArrayList<KeyValue>();
        newConfig.addAll(this.nodeConfig.get().getConfiguration().stream().filter(kv -> !overriddenConfigNames.contains(kv.get("name"))).collect(Collectors.toList()));
        newConfig.add(entityDefConfig);
        newConfig.add(emailFieldConfig);
        newConfig.add(additionalEnrichUsingConfig);
        newConfig.add(additionalEnrichFieldConfig);
        newConfig.add(lookupKeyConfig);
        return newConfig;
    }

    private Stream<KeyValue> getEnrichSourceEntities(MappingNode currentNode) {
        boolean connectedToCore = graph.pathToNodeMatches(currentNode, node -> node == graph.getCoreNode());
        if(connectedToCore){
            return populateEntityPicklistFromEntityNodes(getEntityGraph().stream().map(g->g.getCoreNode()).collect(Collectors.toList()));
        }else{
            return populateEntityPicklistFromEntityNodes(getEntityGraphSources());
        }
    }

    private ArrayList<KeyValue> setEnrichCompanyConfig(MappingNode currentNode) {
        Set<String> overriddenConfigNames = Set.of("entityDefinition", "domainField", "lookUpKey");
        var entityPicklist = getEnrichSourceEntities(currentNode);
        var entityDefConfig = new KeyValue().set("name", "entityDefinition")
                .set("datatype", "picklist")
                .set("label", "Source Entity")
                .set("implicit", false)
                .set("defaultValue", "")
                .set("helpSummary", "Source entity to use for lookup")
                .set("required", true)
                .set("values",entityPicklist)
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "entityDefinition").set("configKey", "value")));
        var emailFieldConfig = new KeyValue().set("name", "domainField")
                .set("datatype", "picklist")
                .set("label", "Input Field")
                .set("implicit", false)
                .set("defaultValue", "")
                .set("helpSummary", "Field from source entity to use for lookup")
                .set("required", true)
                .set("dependsOn", Map.of("dependantType","AttributeList","dependantField","configuration.entityDefinition"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "domainField").set("configKey", "value")));
        var lookupKeyConfig = new KeyValue().set("name", "lookUpKey")
                .set("datatype", "picklist")
                .set("label", "Enrichment Field")
                .set("defaultValue", "")
                .set("implicit", false)
                .set("helpSummary", "Field from enrichment source to use for enriching records")
                .set("required", true)
                .set("dependsOn", Map.of("dependantType", "Company.LookupFields", "dependantField", "configuration.serviceId"))
                .set("mapping", List.of(new KeyValue("graphKey", "configuration." + "lookUpKey").set("configKey", "value")));
        var newConfig = new ArrayList<KeyValue>();
        newConfig.addAll(this.nodeConfig.get().getConfiguration().stream().filter(kv -> !overriddenConfigNames.contains(kv.get("name"))).collect(Collectors.toList()));
        newConfig.add(entityDefConfig);
        newConfig.add(emailFieldConfig);
        newConfig.add(lookupKeyConfig);
        return newConfig;
    }

    private Stream<KeyValue> populateEntityPicklistFromEntityNodes(List<MappingNode> sources){
        return sources.stream().map(s -> {
            var entity = schemaService.getEntity(s.getConfiguration().getConfigMap().get("entityDefinition").toString());

            var label =  entity.getDisplayName() + " (" + this.connectors.get(entity.getConnectorId()).getName() + ")";
            return new KeyValue("value",entity.getId()).set("label",label);
        });
    }

    private Optional<NodeDef> getAttributeConfig(EntityDefinition entityDef, AttributeDefinition attribute, boolean isSource, MappingNode node) {
        var connector = connectors.get(entityDef.getConnectorId());
        var configuration = new AttributeNodeConfig(entityDef, attribute, isSource, connector).getNodeConfiguration();

        var id = entityDef.getId() + (isSource ? "_source" : "_sink");
        var name = entityDef.getApiName();
        var iconPath = ConnectorMetadataDTO.getIconURIForDTO(connector.getMetadata());

        return Optional.of(new NodeDef(id, name, entityDef.getDisplayName(), null, null, null, iconPath, null, 
        null, null, null, null, configuration, true, false, null));
    }
    
    @Override
    public void visit(CoreEntityNodeConfig coreEntityNodeConfig, MappingNode node) {
    	this.nodeConfig = Optional.ofNullable(getAdvancedCoreNodeConfig(coreEntityNodeConfig.getEntityDefinition().getId(), node));
    }
    
    private NodeDef getAdvancedCoreNodeConfig(String entityDefinitionId, MappingNode node) {
        var syncariEntity =schemaService.getSyncariSchema(true).findEntityById(entityDefinitionId)
                .orElseThrow(()->
                        new SyncariValidationException("Could not find syncari entity with id %s",entityDefinitionId));

        var syncariConnector = connectorService.getSyncariConnector();

        Renderer renderer = new Renderer();
        renderer.setRenderType(RenderType.wizard);
        renderer.setTitle("Merge Studio");
        renderer.setSteps(List.of(
                new WizardStep().setStepName("Find Duplicates").setFields(List.of("selectMergeAction","maxDupes", "skipWhen", "findDupes")),
                new WizardStep().setStepName("Select Winner").setFields(List.of("progressiveSelection","selectWinnerValue")),
                new WizardStep().setStepName("Merge Records").setFields(List.of("defaultMergePolicy","defaultOverridePolicy","fieldMergePolicies"))
        ));

        List<KeyValue> attributes =syncariEntity.getActiveFields().stream().map(field->
                new KeyValue("datatype", "picklist")
                        .set("picklistGroup","Fields")
                        .set("label", field.getDisplayName() + " (" + field.getApiName() + ")")
                        .set("type","variable")
                        .set("value",field.getId())
        ).collect(Collectors.toList());
        
        List<KeyValue> skipWhenLhs = new ArrayList<>();
        skipWhenLhs.addAll(syncariEntity.getActiveFields().stream().map(field->
        new KeyValue("datatype", field.getDataType())
                .set("picklistGroup","Incoming Fields")
                .set("label", field.getDisplayName() + " (" + field.getApiName() + ")")
                .set("type","variable")
                .set("value",field.getId())
        ).collect(Collectors.toList()));
        List<KeyValue> tokens = new ArrayList<>();
        tokens.addAll(tokenController.getLookUpTokens(graph, node));
        tokens.addAll(tokenController.getTempVariableTokens(graph, node));
        tokens.stream().forEach(temp -> {
        	String datatype = BooleanUtils.isTrue(temp.get("multiValueField")) ? "picklist" : temp.get("datatype");
        	skipWhenLhs.add(new KeyValue("value", temp.get("token"))
            .set("label", temp.get("shortLabel"))
            .set("type", "variable").set("datatype", datatype)
            .set("picklistGroup", temp.get("group")));
        });

        List<KeyValue> winnerSelectionTypes =new ArrayList<>();
        winnerSelectionTypes.add(
                new KeyValue()
                        .set("label", "Record")
                        .set("value","record")
                        .set("type","variable")
                        .set("picklistGroup","Record Level Selection")

        );
        winnerSelectionTypes.addAll(syncariEntity.getActiveFields().stream().map(field->
                new KeyValue()
                        .set("label", field.getDisplayName())
                        .set("type","variable")
                        .set("value",field.getId())
                        .set("picklistGroup","Field Level Selection")
        ).collect(Collectors.toList()));

        var selectMergeAction = new KeyValue("id","selectMergeAction").set("mapping",new KeyValue("graphKey","configuration.selectMergeAction").set("configKey","value"))
                .set("datatype","boolean")
                .set("name","selectMergeAction")
                .set("helpSummary",i18n("select_merge_action_help"))
                .set("repeatable",true)
                .set("defaultValue",false)
                .set("parentGroup", "deduplicate")
                .set("label",i18n("select_merge_action_label"))
                .set("coreNodeConfig", true);

        var maxDupes = new KeyValue("id","maxDupes").set("mapping",new KeyValue("graphKey","configuration.maxDupes").set("configKey","value"))
                .set("datatype","string")
                .set("name","maxDupes")
                .set("helpSummary",i18n("max_dupes_help"))
                .set("repeatable",true)
                .set("defaultValue","")
                .set("parentGroup", "deduplicate")
                .set("label",i18n("max_dupes_label"))
                .set("coreNodeConfig", true);

        
        var skipWhenCriteria = new KeyValue("id","skipWhenCriteria").set("mapping", new KeyValue("graphKey","configuration.skipWhen").set("configKey","value"))
                .set("implicit",false)
                .set("datatype","predicate")
                .set("defaultValue","")
                .set("name","skipWhen")
                .set("helpSummary",i18n("skip_when_help"))
                .set("parentGroup", "deduplicate")
                .set("layout","row")
                .set("label",i18n("skip_when_label"))
                .set("fieldSet","conditionFields")
                .set("coreNodeConfig", true)
        		.set("fieldValues", skipWhenLhs);
        
        var fieldSkipWhenCriteria = new KeyValue()
        		.set("mapping", new KeyValue("graphKey","configuration.field").set("configKey","value"))
        		.set("name","field")
                .set("datatype","picklist")
                .set("defaultValue","")
                .set("helpSummary",null)
                .set("label","Field")
                .set("fieldSet","conditionFields")
                .set("dependsOn", new KeyValue("dependantField","configuration.attributeDefinition").set("dependantType","AttributeList"))
                .set("allowUserToken",true)
                .set("required", false)
                .set("readOnly", false)
                .set("coreNodeConfig", true)
        		.set("values", skipWhenLhs);
        var operatorSkipWhenCriteria = new KeyValue()
        		.set("mapping", new KeyValue("graphKey","configuration.operator").set("configKey","value"))
        		.set("name","operator")
                .set("datatype","picklist")
                .set("defaultValue","")
                .set("helpSummary",null)
                .set("label","Operator")
                .set("fieldSet","conditionFields")
                .set("dependsOn", new KeyValue("dependantField","configuration.attributeDefinition").set("dependantType","Operator").set("dependantField","configuration.attributeDefinition"))
                .set("required", false)
                .set("readOnly", false)
                .set("coreNodeConfig", true);
        var valueSkipWhenCriteria = new KeyValue()
        		.set("mapping", new KeyValue("graphKey","configuration.value"))
        		.set("name","value")
                .set("datatype","string")
                .set("defaultValue","")
                .set("helpSummary",null)
                .set("label","Value")
                .set("fieldSet","conditionFields")
                .set("required", false)
                .set("readOnly", false)
                .set("coreNodeConfig", true);
        
        var findDupescriteria = new KeyValue("id","findDupesCriteria").set("mapping", new KeyValue("graphKey","configuration.findDupes").set("configKey","value"))
                .set("implicit",false)
                .set("datatype","composite")
                .set("defaultValue","")
                .set("name","findDupes")
                .set("helpSummary",i18n("find_dupes_help"))
                .set("parentGroup", "deduplicate")
//                .set("requiredValue", enableDeduplicateReqVal)
                .set("repeatable",true)
                .set("layout","row")
                .set("label",i18n("find_dupes_label"))
                .set("configuration",List.of(new KeyValue("id","findDupesPredicate").set("datatype","predicate")
                        .set("operatorType","findDupesOperator")
                        .set("rightType","findDupesValue")
                        .set("name","findDupesPredicate").set("values",attributes)))
                .set("coreNodeConfig", true);

        //Progressive Selection
        var progressiveSelection = new KeyValue("id","progressiveSelection").set("mapping",new KeyValue("graphKey","configuration.progressiveSelection").set("configKey","value"))
                .set("datatype","boolean")
                .set("name","progressiveSelection")
                .set("helpSummary",i18n("progressive_selection_help"))
                .set("repeatable",true)
                .set("defaultValue",false)
                .set("parentGroup", "deduplicate")
                .set("label",i18n("progressive_selection_label"))
                .set("coreNodeConfig", true);


        var selectWinner = new KeyValue("id","selectWinner").set("mapping",new KeyValue("graphKey","configuration.selectWinner").set("configKey","value"))
                .set("datatype","composite")
                .set("name","selectWinnerValue")
                .set("helpSummary",i18n("select_winner_help"))
                .set("repeatable",true)
                .set("defaultValue","")
                .set("parentGroup", "deduplicate")
                .set("label","Select Winner")
                .set("configuration",List.of(new KeyValue("id","winnerSelectionPredicate").set("datatype","predicate")
                        .set("operatorType","winnerSelectionOperator")
                        .set("singleCondition",true)
                        .set("rightType","winnerSelectionValue")
                        .set("name","winnerSelectionPredicate").set("values",winnerSelectionTypes)))
                .set("coreNodeConfig", true);

        List<KeyValue> mergePolicies = List.of(
                new KeyValue("label", "Most Frequent Value").set("value", WinnerValueSelectionPolicy.MOST_FREQUENT.name()),
                new KeyValue("label", "Least Frequent Value").set("value", WinnerValueSelectionPolicy.LEAST_FREQUENT.name()),
                new KeyValue("label", "Highest Value").set("value", WinnerValueSelectionPolicy.MAX.name()),
                new KeyValue("label", "Lowest Value").set("value", WinnerValueSelectionPolicy.MIN.name()),
                new KeyValue("label", "Latest With a Value").set("value", WinnerValueSelectionPolicy.LATEST_WITH_VALUE.name()),
                new KeyValue("label", "Earliest With a Value").set("value", WinnerValueSelectionPolicy.EARLIEST_WITH_VALUE.name())
        );
        var defaultMergePolicy = new KeyValue("id","defaultMergePolicy").set("mapping",new KeyValue("graphKey","configuration.defaultMergePolicy").set("configKey","value"))
                .set("datatype","picklist")
                .set("name","defaultMergePolicy")
                .set("helpSummary",i18n("default_merge_policy_help"))
                .set("defaultValue","")
                .set("label","Default Merge Policy")
                .set("parentGroup", "deduplicate")
                .set("values", mergePolicies)
                .set("coreNodeConfig", true);
        List<KeyValue> overridePolicies = List.of(
                new KeyValue("label", "Override when winner is blank").set("value", WinnerOverridePolicy.WHEN_BLANK.name()),
                new KeyValue("label", "Never override winner").set("value", WinnerOverridePolicy.NEVER.name()),
                new KeyValue("label", "Always override winner").set("value", WinnerOverridePolicy.ALWAYS.name())
        );
        var defaultOverridePolicy = new KeyValue("id","defaultOverridePolicy").set("mapping",new KeyValue("graphKey","configuration.defaultOverridePolicy").set("configKey","value"))
                .set("datatype","picklist")
                .set("defaultValue","")
                .set("name","defaultOverridePolicy")
                .set("helpSummary",i18n("default_override_policy_help"))
                .set("parentGroup", "deduplicate")
                .set("label","Default Override Policy")
                .set("values", overridePolicies)
                .set("coreNodeConfig", true);


        var fieldMergePolicies = new KeyValue("id","fieldMergePolicies").set("mapping",new KeyValue("graphKey","configuration.fieldMergePolicies").set("configKey","value"))
                .set("datatype","composite")
                .set("name","fieldMergePolicies")
                .set("helpSummary",i18n("field_override_policy_help"))
                .set("repeatable",true)
                .set("defaultValue","")
                .set("parentGroup", "deduplicate")
                .set("label","Field Level Merge Policies")
                .set("configuration",List.of(
                        new KeyValue("id","fieldMergePredicate")
                                .set("datatype","predicate")
                                .set("operatorType","fieldMergeOperator")
                                .set("singleCondition",true)
                                .set("rightType","fieldMergeValue")
                                .set("name","fieldMergePredicate")
                                .set("width","75%")
                                .set("values",attributes),
                        new KeyValue("id","fieldOverridePolicy")
                                .set("datatype","picklist")
                                .set("name","fieldOverridePolicy")
                                .set("values",overridePolicies)
                                .set("width","25%")
                ))
                .set("coreNodeConfig", true);

        var dedupeTab = new KeyValue()
                .set("datatype", "tab")
                .set("name", "deduplicate")
                .set("iconPath", "/assets/icons/deduplicate.svg")
                .set("label", "Merge Studio")
                .set("implicit", false)
                .set("coreNodeConfig", true);

        var  entityDefinitionsMap= Map.of(syncariConnector.getId(), schemaService.getSyncariEntities().stream().filter(EntityDefinition::isSyncariSource).collect(Collectors.toList()));

        List<KeyValue> configs = new ArrayList<>();
        configs.addAll(List.of(selectMergeAction,maxDupes,skipWhenCriteria,fieldSkipWhenCriteria,operatorSkipWhenCriteria,valueSkipWhenCriteria,findDupescriteria,progressiveSelection, selectWinner, defaultMergePolicy,defaultOverridePolicy,fieldMergePolicies,dedupeTab));
        configs.addAll(getConnectorEntityConfigurations(entityDefinitionsMap, syncariConnector));
        return new NodeDef(syncariConnector.getId(), syncariConnector.getMetadata().getDisplayName(), null, null, null, null, ConnectorMetadataDTO.getIconURIForDTO(syncariConnector.getMetadata()), null, null, null, null, null, configs, true, false, renderer);
    }
    
    private List<KeyValue> getConnectorEntityConfigurations(Map<String, List<EntityDefinition>> entityDefinitionsMap, Connector connector) {
        var entityDefinitions= entityDefinitionsMap.getOrDefault(connector.getId(),List.of());
        List<KeyValue> entityDefList = entityDefinitions.stream()
                .map(entity -> new KeyValue()
                        .set("value", entity.getId())
                        .set("subLabel",  connector.getName())
                        .set("label",  entity.getDisplayName())
                        .set("inputPorts",  List.of(new PortDTO().setDatatype(ObjectType.VALUE.getName()).setPortType(PortType.INPUT).setMaxConnections(Integer.MAX_VALUE)))
                        .set("outputPorts",  List.of(new PortDTO().setDatatype(ObjectType.VALUE.getName()).setPortType(PortType.OUTPUT).setMaxConnections(Integer.MAX_VALUE))))
                .collect(Collectors.toList());
        var entityKeyMapping = List.of(
                new KeyValue("graphKey","configuration.entityDefinition").set("configKey","value"),
                new KeyValue("graphKey","inputPorts").set("configKey","inputPorts"),
                new KeyValue("graphKey","outputPorts").set("configKey","outputPorts")
        );
        var labelMapping = List.of(
                new KeyValue("graphKey","label")
        );
        var directionKeyMapping = List.of(
                new KeyValue("graphKey","nodeType").set("configKey","value"),
                new KeyValue("graphKey","icon").set("configKey","icon")
        );
        var scheduleKeyMapping = List.of(
                new KeyValue("graphKey","configuration.schedule")
        );
        var connectorIdMapping = List.of(
                new KeyValue("graphKey","configuration.connectorId")
        );
        var exhaustAllRecordsKeyMapping = List.of(
                new KeyValue("graphKey","configuration.exhaustAllRecords").set("configKey", "value")
        );

        String connectorName = connector.getName().equalsIgnoreCase("syncari") ? "Syncari" : connector.getName();

        List<KeyValue> configurations = new ArrayList<>(List.of(
                new KeyValue()
                        .set("datatype", "string")
                        .set("name", "label")
                        .set("label", "Label")
                        .set("implicit", true)
                        .set("mapping", labelMapping)
                        //Token replacement by UI
                        .set("value", "{direction} {entity}"),
                new KeyValue("datatype", "string")
                        .set("name", "defaultSubLabel")
                        .set("value",  connectorName)
                        .set("implicit", true)
                        .set("mapping", List.of(new KeyValue("graphKey", "subLabel"))),

                new KeyValue()
                        .set("datatype", "picklist")
                        .set("name", "entity")
                        .set("label", "Entity")
                        .set("implicit", false)
                        .set("mapping", entityKeyMapping)
                        .set("values", entityDefList),
                new KeyValue()
                        .set("datatype", "string")
                        .set("name", "connectorId")
                        .set("label", "Synapse")
                        .set("implicit", true)
                        .set("mapping", connectorIdMapping)
                        .set("value", connector.getId()),
                new KeyValue()
                        .set("datatype", "picklist")
                        .set("name", "direction")
                        .set("label", "Type")
                        .set("implicit", false)
                        .set("mapping", directionKeyMapping)
                        .set("dependsOn",new KeyValue("dependantField", "configuration.entityDefinition")
                                .set("dependantType","DirectionSelectionType")),
                new KeyValue()
                        .set("datatype", "string")
                        .set("renderType", RenderType.schedule.name())
                        .set("dependsOnFieldValue", true)
                        .set("visibilityDependsOnFieldValue", new KeyValue("fieldName","direction").set("fieldValue",MappingNodeType.ENTITY_SOURCE.name()))
                        .set("name", "schedule")
                        .set("label", "Schedule (Cron format with seconds)")
                        .set("implicit", false)
                        .set("mapping", scheduleKeyMapping),
                new KeyValue()
                        .set("datatype", "multiselect")
                        .set("dependsOnFieldValue", true)
                        .set("visibilityDependsOnFieldValue", new KeyValue("fieldName","direction").set("fieldValue",MappingNodeType.ENTITY_SINK.name()))
                        .set("name", "acceptsDeletesFrom")
                        .set("label", i18n("accept_deletes_label"))
                        .set("helpText", i18n("accept_deletes_help"))
                        .set("implicit", false)
                        .set("dependsOn",new KeyValue("dependantField", "configuration.targetId")
                                .set("dependantType","SourceList")
                                .set("params",List.of(KeyValue.of("name","entityDefinition","value","configuration.entityDefinition"),
                                        KeyValue.of("name","graphVersion","value","configuration.graphVersion")))
                        )
                        .set("mapping",List.of(
                                new KeyValue("graphKey","configuration.acceptsDeletesFrom", "configKey","value"))
                        ),
                new KeyValue()
                        .set("datatype", "boolean")
                        .set("dependsOnFieldValue", true)
                        .set("visibilityDependsOnFieldValue", new KeyValue("fieldName","direction").set("fieldValue",MappingNodeType.ENTITY_SINK.name()))
                        .set("name", "createDisconnectedMapping")
                        .set("label", i18n("create_disconnected_record_label"))
                        .set("helpText", i18n("create_disconnected_record_help"))
                        .set("implicit", false)
                        .set("hideTokenPicker", true)
                        .set("mapping",List.of(
                                new KeyValue("graphKey","configuration.createDisconnectedMapping", "configKey","value"))
                        ),
                new KeyValue()
                        .set("datatype", "boolean")
                        .set("dependsOnFieldValue", true)
                        .set("visibilityDependsOnFieldValue", new KeyValue("fieldName","direction").set("fieldValue",MappingNodeType.ENTITY_SINK.name()))
                        .set("name", "syncOnTxnLog")
                        .set("label", i18n("sync_on_txn_log_label"))
                        .set("helpText", i18n("sync_on_txn_log_help"))
                        .set("implicit", false)
                        .set("hideTokenPicker", true)
                        .set("mapping",List.of(
                                new KeyValue("graphKey","configuration.syncOnTxnLog", "configKey","value"))
                        ),
                new KeyValue()
                        .set("datatype", "picklist")
                        .set("dependsOnFieldValue", true)
                        .set("visibilityDependsOnFieldValue", List.of(
                                new KeyValue("fieldName","direction").set("fieldValue",MappingNodeType.ENTITY_SOURCE.name()),
                                // Match any schedule
                                new KeyValue("fieldName","configuration.schedule").set("fieldValue", "^(?!\\s*$).+"),
                                // Match any non default schedule
                                new KeyValue("fieldName","configuration.schedule").set("fieldValue", "^((?!\\* \\* \\* \\* \\* \\*).)*$")

                        ))
                        .set("name", "exhaustAllRecords")
                        .set("label", "Record Processing")
                        .set("defaultValue", SchedulingType.PROCESS_ALL)
                        .set("values", List.of(
                                new KeyValue("label", "Process all changed records", "value", SchedulingType.PROCESS_ALL),
                                new KeyValue("label", "Process single batch per cycle", "value", SchedulingType.PROCESS_SINGLE_BATCH)
                        ))
                        .set("implicit", false)
                        .set("mapping", exhaustAllRecordsKeyMapping)));

        List<EntityDefinition> sourceParamEntityList = entityDefinitions.stream().filter(e -> CollectionUtils.isNotEmpty(e.getSourceParams())).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(sourceParamEntityList)){
            sourceParamEntityList.stream().findFirst().get().getSourceParams().forEach(sourceParam -> {
                configurations.add(new KeyValue()
                        .set("datatype", sourceParam.getDataType().getName())
                        .set("dependsOnFieldValue", true)
                        .set("name", sourceParam.getApiName())
                        .set("label", sourceParam.getDisplayName())
                        .set("helpText", sourceParam.getDescription())
                        .set("visibilityDependsOnFieldValue", List.of(
                                //new KeyValue("fieldName","configuration.entityDefinition").set("fieldValue",e.getId()),
                                new KeyValue("fieldName", "direction").set("fieldValue", MappingNodeType.ENTITY_SOURCE)
                        ))
                        .set("implicit", false)
                        .set("mapping", List.of(
                                new KeyValue("graphKey", "configuration." + sourceParam.getApiName())
                        ))
                );
            });

        }

        List<EntityDefinition> destParamEntityList = entityDefinitions.stream().filter(e -> CollectionUtils.isNotEmpty(e.getDestinationParams())).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(destParamEntityList)) {
            Map<String, KeyValue> destinationValues = new HashMap<>();
            destParamEntityList.stream()
                    .filter(destParamEntity -> CollectionUtils.isNotEmpty(destParamEntity.getDestinationParams()))
                    .flatMap(destParamEntity -> destParamEntity.getDestinationParams().stream())
                    .forEach(destParam -> {
                        KeyValue value = new KeyValue()
                                .set("datatype", destParam.getDataType().getName())
                                .set("dependsOnFieldValue", true)
                                .set("name", destParam.getApiName())
                                .set("label", destParam.getDisplayName())
                                .set("visibilityDependsOnFieldValue", new ArrayList<>(List.of(
                                        new KeyValue("fieldName", "direction").set("fieldValue", MappingNodeType.ENTITY_SINK)
                                )))
                                .set("defaultValue", destParam.getDefaultValue())
                                .set("implicit", false);
                        if (destParam.getApiName().contains(Constants.BQ_INSERT_OPTION)) {
                            value.set("helpText", i18n("destination_insert_option_type"));
                        }
                        if (CollectionUtils.isNotEmpty(destParam.getPicklist())) {
                            value.set("values", destParam.getPicklist().stream()
                                    .map(picklist -> new KeyValue("label", picklist.getLabel(), "value", picklist.getId()))
                                    .collect(Collectors.toList()))
                                    .set("mapping", List.of(new KeyValue("graphKey", "configuration." + destParam.getApiName()).set("configKey", "value")));
                        } else {
                            value.set("mapping", List.of(
                                    new KeyValue("graphKey", "configuration." + destParam.getApiName())
                            ));
                        }
                        if (destParam.getApiName().contains(Constants.BQ_CHANGESET_FIELD)) {
                            value.set("mapping", List.of(new KeyValue("graphKey", "configuration." + Constants.BQ_CHANGESET_FIELD).set("configKey", "value")))
                                    .set("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.entityDefinition"))
                                    .set("helpText", i18n("changeset_field"));
                            List<KeyValue> visibilityList = value.get("visibilityDependsOnFieldValue");
                            visibilityList.add(new KeyValue("fieldName", "configuration.insertOption")
                                    .set("fieldValue", String.join("|", Constants.BQ_FULL_RECORD_TO_INSERT_OPTION, Constants.BQ_PARTIAL_RECORD_TO_INSERT_OPTION)));
                        }
                        if (destParam.getApiName().contains(Constants.MARKETO_LOOK_UP_FIELD)) {
                            value.set("mapping", List.of(new KeyValue("graphKey", "configuration." + Constants.MARKETO_LOOK_UP_FIELD).set("configKey", "value")))
                                    .set("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.entityDefinition"))
                                    .set("helpText", i18n("marketo_lookup_field"));
                            value.set("visibilityDependsOnFieldValue", new ArrayList<>(List.of(
                                    new KeyValue("fieldName", "direction").set("fieldValue", MappingNodeType.ENTITY_SINK),
                                    new KeyValue("fieldName", "configuration.entityDefinition").set("fieldValue", entityDefList.stream()
                                            .filter(keyValue -> "lead".equalsIgnoreCase((String) keyValue.getOrDefault("label", "")))
                                            .map(keyValue -> (String) keyValue.getOrDefault("value", ""))
                                            .findFirst())
                            )));
                        }
                        if (destParam.getApiName().contains(Constants.MARKETO_ACTION)) {
                            value.set("helpText", i18n("marketo_action"));
                            value.set("visibilityDependsOnFieldValue", new ArrayList<>(List.of(
                                    new KeyValue("fieldName", "direction").set("fieldValue", MappingNodeType.ENTITY_SINK),
                                    new KeyValue("fieldName", "configuration.entityDefinition").set("fieldValue", entityDefList.stream()
                                            .filter(keyValue -> "lead".equalsIgnoreCase((String) keyValue.getOrDefault("label", "")))
                                            .map(keyValue -> (String)keyValue.getOrDefault("value", ""))
                                            .findFirst())
                            )));
                        }
                        if (Constants.PIPELINE_BATCH_SIZE.equalsIgnoreCase(destParam.getApiName())) {
                            value.set("helpText", i18n("pipeline_batch_size_help"))
                                    .set("hideTokenPicker", true);

                        }
                        if (Constants.LEAD_MERGE_IN_CRM.equalsIgnoreCase(destParam.getApiName())) {
                            value.set("hideTokenPicker", true);
                        }
                        destinationValues.putIfAbsent(destParam.getApiName(), value);
                    });
            configurations.addAll(destinationValues.values());
        }
        return configurations;
    }
}