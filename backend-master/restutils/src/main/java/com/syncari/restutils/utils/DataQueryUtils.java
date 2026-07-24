package com.syncari.restutils.utils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.dfi.ScoreRulesSeed;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.service.*;
import com.syncari.core.utils.DataCriteriaVisitor;
import com.syncari.core.utils.ExternalIdVisitor;
import com.syncari.restutils.data.EntityRecord;
import com.syncari.utils.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class DataQueryUtils {

    @Autowired
    SchemaService schemaService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    MappingGraphService mappingGraphService;
    @Autowired
    DfiRuleAssignmentService dfiRuleAssignmentService;
    @Autowired
    IdMappingService mappingService;


    ObjectMapper mapper = new ObjectMapper();


    public Optional<Expression> getExpression(String predicate) {
        try {
            Optional<Expression> input = Optional.empty();
            if (!StringUtils.isBlank(predicate)) {
                byte[] base64Decoded = DatatypeConverter.parseBase64Binary(predicate);
                Map<String, Object> map = mapper.readValue(new String(base64Decoded), Map.class);
                validateExpression(map);
                input = Optional.of(new PredicateParser(StringUtils.EMPTY).fromMap(map));
            }
            return input;
        } catch (JsonParseException | JsonMappingException e) {
            log.error(e.getMessage());
            throw new SyncariValidationException(i18n("invalid_predicate"));
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new SyncariValidationException(i18n("predicate_parse_error"));
        }
    }

    public void validateExpression(Map<String, Object> map) {
        List<String> externalIds = new ArrayList<>();
        map.forEach((k, v) -> {
            try {
                Map values = (Map)v;
                if("left".equalsIgnoreCase(k) && values.containsKey("value") && values.get("value").toString().startsWith("datastudio")) {
                    externalIds.add(values.get("value").toString());
                }
            } catch (Exception e) {
            }
        });
        if(externalIds.size() > 1) {
            throw new SyncariValidationException(i18n("only_one_external_id_allowed"));
        }
    }

    public Map<String, KeyValue> getFilterFields(EntityDefinition entity) {
        Map<String, KeyValue> fields = new LinkedHashMap<String, KeyValue>();

        fields.put(
                "syncariId",
                KeyValue.of(
                        "dataType", "string",
                        "label", "Syncari Id",
                        "fieldId", ExternalIdVisitor.DATASTUDIO_SYNCARI_ID,
                        "canFilter", true,
                        "canDisplay", true,
                        "canEdit", false,
                        "isSystem", true
                )
        );
        fields.put(
                "idMapping",
                KeyValue.of(
                        "dataType", "idMapping",
                        "label", "Id Mapping",
                        "fieldId", "idMapping",
                        "canDisplay", true,
                        "canEdit", false,
                        "canFilter", false,
                        "isSystem", true
                )
        );
        if (dfiRuleAssignmentService.isCustomRulesExists()){
            fields.put(
                    "dfi",
                    KeyValue.of(
                            "dataType", "score",
                            "label", "Data Fitness Index",
                            "fieldId", "dfi",
                            "canFilter", false,
                            "canDisplay", true,
                            "canEdit", false,
                            "isSystem", true
                    )
            );
        }
        fields.put(
                "datastudio_isDeleted",
                KeyValue.of(
                        "dataType", "boolean",
                        "label", "Is Syncari Deleted",
                        "fieldId", "datastudio_isDeleted",
                        "canFilter", true,
                        "canDisplay", true,
                        "canEdit", false,
                        "isSystem", true
                )
        );

        entity.getActiveAttributes().stream().forEach(a -> {
            if(!a.isIdField()) {
                fields.put(
                        a.getApiName(),
                        KeyValue.of(
                                "dataType", a.getDataType().getName(),
                                "label", a.getDisplayName(),
                                "fieldId", a.getId(),
                                "canFilter", true,
                                "canDisplay", true,
                                "canEdit", (a.isUpdatable() && !a.isSystem())
                        )
                );
            }
        });
        fields.putAll(getExternalIds(entity));

        Map<String, List<RuleAssignment>> rules = dfiRuleAssignmentService.getRulesForEntityByField(entity.getApiName());
        rules.forEach((k, r) -> {
            r.forEach(ruleAssignment -> {
                ruleAssignment.getConditions().forEach(c -> {
                    String id = "rule" + DataCriteriaVisitor.FILTER_DELIMITER + c.getName() + DataCriteriaVisitor.FILTER_DELIMITER + k;
                    RuleDefinition ruleDef = ScoreRulesSeed.get(c.getName(), Scope.ATTRIBUTE);
                    fields.put(
                            id,
                            KeyValue.of(
                                    "value", id,
                                    "type", "variable",
                                    "datatype", "double",
                                    "label", k + " / " + ruleDef.getLabel() + " / Score",
                                    "canEdit", false,
                                    "canDisplay", false,
                                    "canFilter", true,
                                    "fieldId", id
                            )
                    );
                });
            });
        });

        return fields;
    }

    public Map<String, KeyValue> getExternalIds(EntityDefinition entity) {
        // From the pipeline find all external entities for the given syncari entity
        // fieldId will be in the format synapseid_entityid_id
        Map<String, KeyValue> fields = new HashMap<String, KeyValue>();
        List<EntityDefinition> externalEntities = mappingGraphService.findExternalEntities(entity.getId());
        externalEntities.forEach(e -> {
            EntityDefinition definition = schemaService.getEntity(e.getId());
            definition.getIdField().ifPresent(id -> {
                String fieldId = "datastudio_"+definition.getConnectorId()+"_"+e.getId()+"_id";
                String apiName = definition.getConnectorId()+"_"+e.getId()+"_id";
                Connector connector = connectorService.find(definition.getConnectorId()).get();
                String label = StringUtils.capitalize(connector.getName())+" "+StringUtils.capitalize(definition.getDisplayName())+" Id";
                KeyValue keyValue = new KeyValue("dataType", id.getDataType(), "label", label, "fieldId", fieldId, "canFilter", true, "canDisplay", false);
                keyValue.put("picklistGroup", "Synapses");
                fields.put(apiName, keyValue);
            });
        });
        return fields;
    }

    public void populateConnectorsAndEntities(String entityId, List<Connector> sourceConnectors, List<EntityDefinition> connectedEntities) {
        Optional<MappingGraph> mappingGraph = mappingGraphService.retrieveApprovedEntityGraph(entityId).or(()->mappingGraphService.retrieveDraftEntityGraph(entityId));
        Set<String> connectedExternalEntityDefIds = new LinkedHashSet<>();
        mappingGraph.ifPresent(g->{
            g.getSources().forEach(source->{
                EntitySourceNodeConfig config = source.getTypedConfiguration();
                connectorService.find(config.getEntityDefinition().getConnectorId()).ifPresent(connector -> sourceConnectors.add(connector));
                connectedExternalEntityDefIds.add(config.getEntityDefinition().getId());
            });
            g.getSinks().forEach(sink->{
                EntitySinkNodeConfig config = sink.getTypedConfiguration();
                connectorService.find(config.getEntityDefinition().getConnectorId()).ifPresent(connector -> sourceConnectors.add(connector));
                connectedExternalEntityDefIds.add(config.getEntityDefinition().getId());
            });
        });
        connectedEntities.addAll(schemaService.getEntities(connectedExternalEntityDefIds, false));
    }

    public void populateIdMappings(EntityDefinition entity, List<EntityRecord> entityRecords, List<Connector> connectors, List<EntityDefinition> connectedEntities) {
        Map<String, EntityRecord> recordMap = new HashMap<String, EntityRecord>();
        for (EntityRecord record : entityRecords) {
            recordMap.put(record.getSyncariId(), record);
        }
        Collection<String> syncariId = entityRecords.stream().map(e -> e.getSyncariId()).collect(Collectors.toList());
        Map<String, Connector> connectorMap = new HashMap<>();
        connectors.forEach(c-> connectorMap.put(c.getId(),c));
        Map<String, EntityDefinition> entityMap = new HashMap<>();
        connectedEntities.forEach(e-> entityMap.put(e.getId(),e));
        List<IdMapping> idMappings = mappingService.findBySyncariIds(entity.getApiName(), syncariId);
        idMappings.forEach(m -> {
            EntityRecord record = recordMap.get(m.getSyncariId());
            m.getMappings().forEach(mapping -> {
                if(!connectorMap.containsKey(mapping.getConnectorId())) {
                    connectorMap.put(mapping.getConnectorId(), connectorService.find(mapping.getConnectorId()).get());
                }
                if(!entityMap.containsKey(mapping.getEntityDefinitionId())) {
                    try {
                        entityMap.put(mapping.getEntityDefinitionId(), schemaService.getEntity(mapping.getEntityDefinitionId(), false));
                    }catch (Exception e) {
                        log.debug("Cannot find entity", e);
                    }
                }

                record.getIdMapping()
                        .put(idMappingLabel(connectorMap.get(mapping.getConnectorId()),entityMap.get(mapping.getEntityDefinitionId())), getExternalRecordIdWithAnnotation(mapping));
            });
        });
    }

    public String idMappingLabel(Connector connector, EntityDefinition entityDefinition){
        return (connector==null?"Unknown Connector":connector.getName()) +" / " +(entityDefinition==null?"Unknown Entity":entityDefinition.getDisplayName());

    }

    public String getExternalRecordIdWithAnnotation(IdMapping.Mapping mapping) {
        return mapping.isDisconnected()? mapping.getEntityId() + i18n("idmapping_disconnected") : mapping.getEntityId();
    }
}
