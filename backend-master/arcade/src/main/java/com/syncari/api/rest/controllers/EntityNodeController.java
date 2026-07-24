package com.syncari.api.rest.controllers;

import com.syncari.api.rest.controllers.data.ConnectorMetadataDTO;
import com.syncari.api.rest.controllers.data.ConnectoryEntityNodeDTO;
import com.syncari.api.rest.controllers.data.RenderType;
import com.syncari.connector.Constants;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.SchedulingType;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.service.SchemaService;
import com.syncari.restutils.data.PortDTO;
import com.syncari.restutils.data.PortType;
import com.syncari.utils.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/connectorEntities")
public class EntityNodeController {
    @Autowired
    SchemaService schemaService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    DataServiceFactory factory;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET)
    public List<ConnectoryEntityNodeDTO> getConnectorEntityNodes() {
        return getEntityList();
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/{entityDefinitionId}")
    public List<ConnectoryEntityNodeDTO> getConnectorEntityNodes(@PathVariable String entityDefinitionId) {
        ConnectoryEntityNodeDTO coreNodeConfig = getAdvancedCoreNodeConfig(entityDefinitionId);
        List<ConnectoryEntityNodeDTO> connectorEntityNodes = getEntityList();
        connectorEntityNodes.add(coreNodeConfig);
        return connectorEntityNodes;
    }

    private ConnectoryEntityNodeDTO getCoreNodeConfig(@PathVariable String entityDefinitionId) {
        var syncariEntity =schemaService.getSyncariSchema(true).findEntityById(entityDefinitionId)
                .orElseThrow(()->
                        new SyncariValidationException("Could not find syncari entity with id %s",entityDefinitionId));

        var syncariConnector = connectorService.getSyncariConnector();

        var dataAuthorityStrategyMapping = List.of(
                new KeyValue("graphKey","configuration.dataAuthorityStrategy").set("configKey","value")
        );
        var dataAuthorityConnectorIdMapping = List.of(
                new KeyValue("graphKey","configuration.connectorId").set("configKey","value")
        );

        var dedupeConfigFieldListMapping = List.of(
                new KeyValue("graphKey","configuration.dedupeFields").set("configKey","value")
        );
        var dedupeConfigWinnerStrategyMapping = List.of(
                new KeyValue("graphKey","configuration.winnerStrategy").set("configKey","value")
        );
        var dedupeConfigMergeStrategyMapping = List.of(
                new KeyValue("graphKey","configuration.mergeStrategy").set("configKey","value")
        );
        var dedupeConfigSelectedConnectorIdMapping = List.of(
                new KeyValue("graphKey","configuration.selectedConnectorId").set("configKey","value")
        );
        var  enableDeduplicateReqVal = new KeyValue()
                .set("graphKey", "configuration.enableDeduplicate")
                .set("value", true);

        var  winningSynapseReqVal = new KeyValue()
                .set("graphKey", "configuration.winnerStrategy")
                .set("value", WinnerStrategy.SELECTED_CONNECTOR.name());
        var  selectedSynapseReqVal = new KeyValue()
                .set("graphKey", "configuration.dataAuthorityStrategy")
                .set("value", DatAuthorityStrategy.SELECTED_CONNECTOR.name());
        return new ConnectoryEntityNodeDTO().setCoreNode(true)
                .setIconPath("/icons/syncari.png")
                .setId(syncariConnector.getId())
                .setName(syncariConnector.getName())
                .setConfiguration(List.of(
                        new KeyValue()
                                .set("datatype", "picklist")
                                .set("name", "dataAuthorityStrategy")
                                .set("label", "Authoritative Source Strategy")
                                .set("implicit", false)
                                .set("parentGroup", "dataAuthority")
                                .set("mapping", dataAuthorityStrategyMapping)
                                .set("values",

                                        List.of(
                                                new KeyValue().set("value", DatAuthorityStrategy.LATEST_RECORD.name()).set("label",i18n("da_latest")),
                                                new KeyValue().set("value", DatAuthorityStrategy.SELECTED_CONNECTOR.name()).set("label",i18n("da_selected_synapse")),
                                                new KeyValue().set("value", DatAuthorityStrategy.NONE.name()).set("label",i18n("da_none"))
                                        )),
                        new KeyValue()
                                .set("datatype", "picklist")
                                .set("name", "connectorId")
                                .set("label", "Synapse")
                                .set("implicit", false)
                                .set("parentGroup", "dataAuthority")
                                .set("requiredValue", selectedSynapseReqVal)
                                .set("mapping", dataAuthorityConnectorIdMapping)
                                .set("dependsOn", Map.of("dependantType", "DataAuthorityStrategy", "dependantField", "configuration.dataAuthorityStrategy")),
                        new KeyValue()
                                .set("datatype", "boolean")
                                .set("name", "enableDeduplicate")
                                .set("label", "Deduplicate")
                                .set("implicit", false)
                                .set("parentGroup", "deduplicate")
                                .set("helpLink", "https://syncari.helpdocs.io/deduplicate")
                                .set("mapping", List.of(
                                        new KeyValue("graphKey","configuration.enableDeduplicate").set("configKey","value")
                                )),
                        new KeyValue()
                                .set("datatype", "multiselect")
                                .set("name", "dedupeFields")
                                .set("label", "Dedupe Fields")
                                .set("implicit", false)
                                .set("parentGroup", "deduplicate")
                                .set("requiredValue", enableDeduplicateReqVal)
                                .set("mapping", dedupeConfigFieldListMapping)
                                .set("values", syncariEntity.getFields().stream().map(attribute -> new KeyValue()
                                        .set("value", attribute.getId())
                                        .set("label",attribute.getDisplayName()))
                                        .collect(Collectors.toSet())),
                        new KeyValue()
                                .set("datatype", "picklist")
                                .set("name", "winnerStrategy")
                                .set("label", "Choosing a Dedupe Winner")
                                .set("implicit", false)
                                .set("parentGroup", "deduplicate")
                                .set("requiredValue", enableDeduplicateReqVal)
                                .set("mapping", dedupeConfigWinnerStrategyMapping)
                                .set("values",
                                        List.of(
                                                new KeyValue().set("value", WinnerStrategy.LATEST.name()).set("label","Latest Record"),
                                                new KeyValue().set("value", WinnerStrategy.SELECTED_CONNECTOR.name()).set("label","Record from Selected Synapse"),
                                                new KeyValue().set("value", WinnerStrategy.LATEST_EXISTING.name()).set("label","Latest Existing Record"),
                                                new KeyValue().set("value", WinnerStrategy.DO_NOTHING.name()).set("label","Do Nothing")
                                        )),
                        new KeyValue()
                                .set("datatype", "picklist")
                                .set("name", "selectedConnectorId")
                                .set("label", "Winning Synapse")
                                .set("implicit", false)
                                .set("parentGroup", "deduplicate")
                                .set("requiredValue", winningSynapseReqVal)
                                .set("mapping", dedupeConfigSelectedConnectorIdMapping)
                                .set("dependsOn", Map.of("dependantType", "WinnerStrategy", "dependantField", "configuration.winnerStrategy")),
                        new KeyValue()
                                .set("datatype", "picklist")
                                .set("name", "mergeStrategy")
                                .set("label", "Merge Policy for Losing Records")
                                .set("implicit", false)
                                .set("parentGroup", "deduplicate")
                                .set("requiredValue", enableDeduplicateReqVal)
                                .set("mapping", dedupeConfigMergeStrategyMapping)
                                .set("values",

                                        List.of(
                                                new KeyValue().set("value", MergeStrategy.INTELLIGENT_MERGE).set("label","Intelligent Merge"),
                                                new KeyValue().set("value", MergeStrategy.INCOMING_RECORD).set("label","Use Non-empty Incoming Values"),
                                                new KeyValue().set("value", MergeStrategy.WINNER_TAKES_ALL.name()).set("label","Use Winning Record")
                                        )),
                        new KeyValue()
                                .set("datatype", "tab")
                                .set("name", "deduplicate")
                                .set("iconPath", "/assets/icons/deduplicate.svg")
                                .set("label", "Deduplicate")
                                .set("implicit", false),
                        new KeyValue()
                                .set("datatype", "tab")
                                .set("name", "dataAuthority")
                                .set("iconPath", "/assets/icons/data-authority.svg")
                                .set("label", "Data Authority")
                                .set("implicit", false)
                        )

                );
    }

    private List<ConnectoryEntityNodeDTO> getEntityList() {
        List<Connector> connectors = connectorService.list().stream()
                .filter(c -> (c.getStatus() != ConnectorStatus.NEW || c.getStatus() != ConnectorStatus.INACTIVE))
                .collect(Collectors.toList());
        Set<String> connectorIds = connectors.stream()
                .map(c->c.getId())
                .collect(Collectors.toSet());
        var  entityDefinitionsMap= entityProxyRepo.findActiveEntitiesByConnectorIds(connectorIds).stream().collect(Collectors.groupingBy(e->e.getConnectorId()));

        return new ArrayList<>( connectors.stream().map(connector -> {
            return new ConnectoryEntityNodeDTO()
                    .setIconPath(ConnectorMetadataDTO.getIconURIForDTO(connector.getMetadata()))
                    .setId(connector.getId())
                    .setName(connector.getName())
                    .setConfiguration(getConnectorEntityConfigurations(entityDefinitionsMap, connector, connectorService.isHttpSource(connector)));
        }).collect(Collectors.toList()));
    }

    private List<KeyValue> getConnectorEntityConfigurations(Map<String, List<EntityDefinition>> entityDefinitionsMap, Connector connector, boolean httpSource) {
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

        String connectorName = connector.getName().equalsIgnoreCase("syncari") ? "NextEdge AI" : connector.getName();

        var entityConfig = new KeyValue()
                .set("datatype", "picklist")
                .set("name", "entity")
                .set("label", "Entity")
                .set("implicit", false)
                .set("mapping", entityKeyMapping)
                .set("values", entityDefList);
        if(httpSource) {
        	entityConfig.set("isHttpSourceEntity", true);
        	entityConfig.set("hasAdditionalConfig", true);
        	entityConfig.set("additionalConfigParams", new KeyValue("configLoaderType", "entitysource"));
        }
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
                entityConfig,
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
                        .set("datatype", "string")
                        .set("name", "entity")
                        .set("label", "Entity")
                        .set("helpText", "")
                        .set("implicit", true)
                        .set("mapping",List.of(
                                new KeyValue("graphKey","configuration.entity", "configKey","value"))
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
            sourceParamEntityList.forEach(sourceEntity -> {
                List<AttributeDefinition> sourceParams = sourceEntity.getSourceParams();
                sourceParams.forEach(sourceParam -> {
                    configurations.add(new KeyValue()
                            .set("datatype", sourceParam.getDataType().getName())
                            .set("name", sourceParam.getApiName())
                            .set("label", sourceParam.getDisplayName())
                            .set("helpText", sourceParam.getDescription())
                            .set("visibilityDependsOnFieldValue", new ArrayList<>(List.of(
                                    new KeyValue("fieldName", "direction").set("fieldValue", MappingNodeType.ENTITY_SOURCE),
                                    new KeyValue("fieldName", "configuration.entityDefinition").set("fieldValue", entityDefList.stream()
                                            .filter(keyValue -> sourceEntity.getDisplayName().equalsIgnoreCase((String) keyValue.getOrDefault("label", "")))
                                            .map(keyValue -> (String) keyValue.getOrDefault("value", ""))
                                            .findFirst())
                            )))
                            .set("dependsOnFieldValue", true)
                            .set("implicit", false)
                            .set("mapping", List.of(
                                    new KeyValue("graphKey", "configuration." + sourceParam.getApiName())
                            ))
                            .set("hideTokenPicker", true)
                    );
                });
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
                                .set("helpText", destParam.getDescription())
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

    private ConnectoryEntityNodeDTO getAdvancedCoreNodeConfig(@PathVariable String entityDefinitionId) {
        var syncariEntity =schemaService.getSyncariSchema(true).findEntityById(entityDefinitionId)
                .orElseThrow(()->
                        new SyncariValidationException("Could not find syncari entity with id %s",entityDefinitionId));

        var syncariConnector = connectorService.getSyncariConnector();

        var renderer = new KeyValue("renderType","wizard").set("title","Merge Studio").set("steps",
                List.of(
                        new KeyValue("stepName","Find Duplicates").set("fields",List.of("selectMergeAction","maxDupes", "skipWhen", "findDupes")),
                        new KeyValue("stepName","Select Winner").set("fields",List.of("progressiveSelection","selectWinnerValue")),
                        new KeyValue("stepName","Merge Records").set("fields",List.of("defaultMergePolicy","defaultOverridePolicy","fieldMergePolicies"))
                )
        );

        List<KeyValue> attributes =syncariEntity.getActiveFields().stream().map(field->
                new KeyValue("datatype", "picklist")
                        .set("picklistGroup","Fields")
                        .set("label", field.getDisplayName() + " (" + field.getApiName() + ")")
                        .set("type","variable")
                        .set("value",field.getId())
        ).collect(Collectors.toList());

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
//                .set("configuration",List.of(
//                        new KeyValue("id","winnerSelectionType").set("datatype","picklist").set("name","winnerSelectionType").set("values",winnerSelectionTypes).set("mapping",new KeyValue("graphKey","configuration.winnerSelectionType").set("configKey","value")),
//                        new KeyValue("id","winnerSelectionValue").set("datatype","picklist").set("name","winnerSelectionValue").set("dependsOn",new KeyValue("dependantField","configuration.winnerSelectionType").set("dependantType","WinnerSelectionType"))
//                        //new KeyValue("id","priorityValues").set("datatype","string").set("name","priorityValues")
//
//                    )
//                );

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
//                .set("requiredValue", enableDeduplicateReqVal)
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
//                .set("requiredValue", enableDeduplicateReqVal)
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

        var fieldLevelOverrides = new KeyValue("id","fieldLevelOverrides").set("mapping",new KeyValue("graphKey","configuration.fieldLevelOverrides").set("configKey","value"))
                .set("datatype","composite")
                .set("defaultValue","")
                .set("repeatable",true)
                .set("name","fieldLevelOverrides")
                .set("helpSummary",i18n("field_override_policy_help"))
//                .set("requiredValue", enableDeduplicateReqVal)
                .set("parentGroup", "deduplicate")
                .set("label","Field Level Merge Policies")
                .set("configuration",List.of(
                        new KeyValue("id","mergePolicyTargetField").set("datatype","picklist").set("name","field").set("values",attributes),
                        new KeyValue("id","fieldMergePolicy").set("datatype","picklist").set("name","fieldMergePolicy").set("values",mergePolicies),
                        new KeyValue("id","fieldOverridePolicy").set("datatype","picklist").set("name","fieldOverridePolicy").set("values",overridePolicies)
                        )
                )
                .set("coreNodeConfig", true);
        var dedupeTab = new KeyValue()
                .set("datatype", "tab")
                .set("name", "deduplicate")
                .set("iconPath", "/assets/icons/deduplicate.svg")
                .set("label", "Merge Studio")
                .set("implicit", false)
                .set("coreNodeConfig", true);
        var settingsTab = new KeyValue()
                .set("datatype", "tab")
                .set("name", "settings")
                .set("iconPath", "/assets/icons/settings.svg")
                .set("label", "Settings")
                .set("implicit", false);
        var duplicateSwitch = new KeyValue()
                .set("datatype", "boolean")
                .set("name", "enableDeduplicate")
                .set("label", "Deduplicate")
                .set("implicit", false)
                .set("parentGroup", "deduplicate")
                .set("helpLink", "https://syncari.helpdocs.io/deduplicate")
                .set("mapping", List.of(
                        new KeyValue("graphKey","configuration.enableDeduplicate").set("configKey","value")
                ));

        var continuousSync = new KeyValue()
                .set("datatype", "boolean")
                .set("name", "continuousSync")
                .set("label", "Continuous Sync")
                .set("implicit", false)
                .set("parentGroup", "settings")
                .set("helpLink", "https://syncari.helpdocs.io/deduplicate")
                .set("mapping", List.of(
                        new KeyValue("graphKey", "configuration.continuousSync").set("configKey", "value")
                ));
        var enableNodeLogs = new KeyValue()
                .set("datatype", "boolean")
                .set("name", "enableNodeLogs")
                .set("label", "Enable Node Logs")
                .set("implicit", false)
                .set("parentGroup", "settings")
                .set("helpLink", "https://syncari.helpdocs.io/enableNodeLogs")
                .set("mapping", List.of(
                        new KeyValue("graphKey", "configuration.enableNodeLogs").set("configKey", "value")
                ));

        var entityDefinitionsMap = Map.of(syncariConnector.getId(), schemaService.getSyncariEntities().stream().filter(EntityDefinition::isSyncariSource).collect(Collectors.toList()));

        List<KeyValue> configs = getConnectorEntityConfigurations(entityDefinitionsMap, syncariConnector, false);

        ConnectoryEntityNodeDTO connectoryEntityNodeDTO = new ConnectoryEntityNodeDTO().setCoreNode(true)
                .setIconPath(ConnectorMetadataDTO.getIconURIForDTO(syncariConnector.getMetadata()))
                .setBackgroundColor(syncariConnector.getMetadata().getBackgroundColor())
                .setId(syncariConnector.getId())
                .setName(syncariConnector.getMetadata().getDisplayName())
                .setRenderer(renderer)
                .setConfiguration(new ArrayList<>(List.of(
                        selectMergeAction,maxDupes,skipWhenCriteria,findDupescriteria,progressiveSelection, selectWinner, defaultMergePolicy,defaultOverridePolicy,fieldMergePolicies,dedupeTab
                )));

        connectoryEntityNodeDTO.getConfiguration().addAll(configs);
        return connectoryEntityNodeDTO;
    }
}
