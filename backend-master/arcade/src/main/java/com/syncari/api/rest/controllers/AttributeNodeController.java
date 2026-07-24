package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.utils.I18n.i18n;

import java.util.*;
import java.util.stream.Collectors;

import com.syncari.api.rest.controllers.data.studio.AttributeNodeConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.syncari.api.rest.controllers.exceptions.ResourceNotFoundException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.DatAuthorityStrategy;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.EntitySinkNodeConfig;
import com.syncari.core.model.EntitySourceNodeConfig;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.I18n;
import com.syncari.utils.KeyValue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/attributeNodes")
public class AttributeNodeController {
    @Autowired
    SchemaService schemaService;

    @Autowired
    MappingGraphService graphService;
    @Autowired
    ConnectorService connectorService;

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/{attributeId}")
    public List<KeyValue> getAvailableAttributeNodes(@PathVariable String attributeId) {
        var attributeDefinition = schemaService.getActiveAttribute(attributeId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Active attribute with id %s not found", attributeId)));


        KeyValue coreNodeConfig = createCoreAttributeConfig(attributeDefinition);

        var entityGraphOption = graphService.retrieveDraftEntityGraph(attributeDefinition.getEntityId());
        if(entityGraphOption.isEmpty()){
            entityGraphOption = graphService.retrieveApprovedEntityGraph(attributeDefinition.getEntityId());
        }
        if(entityGraphOption.isEmpty()){
            throw new ResourceNotFoundException("Graph not found");
        }
        var entityGraph = entityGraphOption.get();
        var sources = entityGraph.getSources();
        var sinks = entityGraph.getSinks();
        List<Connector> list = connectorService.list();
        list.add(connectorService.getSyncariConnector());
        var connectors = list.stream().collect(Collectors.toMap(c->c.getId(), c->c));
        var sourceNodes = sources.map(source -> {
            EntitySourceNodeConfig configuration = (EntitySourceNodeConfig) source.getConfiguration();
            EntityDefinition entityDefinition = configuration.getEntityDefinition();
            return toAttributeNode(entityDefinition, true, connectors.get(entityDefinition.getConnectorId()));
        });
        var sinkNodes = sinks.map(sink -> {
            EntitySinkNodeConfig configuration = (EntitySinkNodeConfig) sink.getConfiguration();
            EntityDefinition entityDefinition = configuration.getEntityDefinition();
            return toAttributeNode(entityDefinition, false, connectors.get(entityDefinition.getConnectorId()));
        });

        List<KeyValue> allNodes = new ArrayList<>(sourceNodes.collect(Collectors.toList()));
        allNodes.addAll(sinkNodes.collect(Collectors.toList()));
        allNodes.add(coreNodeConfig);
        return allNodes;
    }

    private KeyValue createCoreAttributeConfig(AttributeDefinition attributeDefinition) {
        var rejectEmptyStringConfigVisibility = Set.of("string", "text", "textarea", "email", "url", "picklist", "date");
        var dataAuthorityStrategyMapping = List.of(
                new KeyValue("graphKey","configuration.dataAuthority.dataAuthorityStrategy").set("configKey","value")
        );
        var dataAuthorityConnectorIdMapping = List.of(
                new KeyValue("graphKey","configuration.dataAuthority.connectorId").set("configKey","value")
        );
        var defaultValueMapping = List.of(
                new KeyValue("graphKey","configuration.defaultValue")
        );
        var rejectEmptyValueMapping = List.of(
                new KeyValue("graphKey","configuration.rejectEmptyValue")
        );
        var rejectEmptyStringMapping = List.of(
                new KeyValue("graphKey","configuration.rejectEmptyString")
        );

        List<KeyValue> configList = new ArrayList<>(List.of(
                new KeyValue()
                        .set("datatype", "string")
                        .set("name", "apiName")
                        .set("label", "API Name")
                        .set("uneditable", true)
                        .set("defaultValue", attributeDefinition.getApiName()),
                new KeyValue()
                        .set("datatype", "string")
                        .set("name", "dataType")
                        .set("label", "Data Type")
                        .set("uneditable", true)
                        .set("defaultValue", attributeDefinition.getDataType().getName()),
                new KeyValue()
                        .set("datatype", "boolean")
                        .set("name", "multiValue")
                        .set("label", "Multi Value")
                        .set("uneditable", true)
                        .set("defaultValue", attributeDefinition.isMultiValueField()),

                new KeyValue()
                        .set("datatype", "boolean")
                        .set("name", "requried")
                        .set("label", "Required")
                        .set("uneditable", true)
                        .set("defaultValue", !attributeDefinition.isNillable()),
                new KeyValue()
                        .set("datatype", "picklist")
                        .set("name", I18n.i18n("data_authority_strategy"))
                        .set("implicit", false)
                        .set("mapping", dataAuthorityStrategyMapping)
                        .set("values",
                                List.of(
                                        new KeyValue().set("value", DatAuthorityStrategy.LATEST_RECORD.name()).set("label",i18n("da_latest")),
                                        new KeyValue().set("value", DatAuthorityStrategy.SELECTED_CONNECTOR.name()).set("label",i18n("da_selected_synapse")),
                                        new KeyValue().set("value", DatAuthorityStrategy.NONE.name()).set("label",i18n("da_none"))
                                )),
                new KeyValue()
                        .set("datatype", "picklist")
                        .set("name", I18n.i18n("synapse"))
                        .set("implicit", false)
                        .set("mapping", dataAuthorityConnectorIdMapping)
                        .set("dependsOn", Map.of("dependantType", "DataAuthorityStrategy", "dependantField", "configuration.dataAuthority.dataAuthorityStrategy")),
                new KeyValue()
                        .set("datatype", "boolean")
                        .set("label", I18n.i18n("reject_empty_value"))
                        .set("name", "rejectEmptyValue")
                        .set("helpText", I18n.i18n("reject_empty_value_help"))
                        .set("mapping", rejectEmptyValueMapping)
                        .set("hideTokenPicker", true)
        ));

        if(rejectEmptyStringConfigVisibility.contains(attributeDefinition.getDataType().getName())) {
            configList.add(
                    new KeyValue()
                            .set("datatype", "boolean")
                            .set("label", I18n.i18n("reject_empty_string"))
                            .set("name", "rejectEmptyString")
                            .set("helpText", I18n.i18n("reject_empty_string_help"))
                            .set("mapping", rejectEmptyStringMapping)
            );
        }

        return new KeyValue("isCoreNode",true)
                .set("id",attributeDefinition.getId())
                .set("name",attributeDefinition.getApiName())
                .set("label",attributeDefinition.getDisplayName())
                .set("iconPath","/icons/syncari.png")
                .set("type", "core")
                .set("entityDefinitionId", attributeDefinition.getEntityId())
                .set("configuration", configList
                );
    }

    private KeyValue toAttributeNode(EntityDefinition entityDefinition, boolean isSource, Connector connector) {
        log.debug("Entity source {} {} with connector {}", entityDefinition.getApiName(), entityDefinition.getId(), connector);
        return new AttributeNodeConfig(schemaService.getEntity(entityDefinition.getId()), null, isSource, connector).getNode();
    }
}
