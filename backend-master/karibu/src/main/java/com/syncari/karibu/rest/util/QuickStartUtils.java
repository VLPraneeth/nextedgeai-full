package com.syncari.karibu.rest.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.syncari.api.rest.controllers.data.quickstart.v2.QSEntityPipelineDTO;
import com.syncari.api.rest.controllers.data.quickstart.v2.QSFieldPipelineDTO;
import com.syncari.api.rest.controllers.data.quickstart.v2.QSPipelineConfigDTO;
import com.syncari.api.rest.controllers.data.quickstart.v2.QuickStartDTO;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.quickstart.v2.*;
import com.syncari.core.schema.AttributeDef;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.service.*;
import com.syncari.karibu.rest.controllers.data.QuickstartRunTO;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.request.QuickStartCreateRequest;
import com.syncari.karibu.rest.request.QuickStartRunRequest;
import com.syncari.karibu.rest.response.QuickStartResponse;
import com.syncari.restutils.utils.ImageUtil;
import com.syncari.utils.KeyValue;
import org.apache.commons.lang.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Component
public class QuickStartUtils {

    @Autowired
    ObjectMapper mapper;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    MappingGraphService mappingGraphService;

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    ImageUtil imageUtil;


    List publishValues = Arrays.asList("publish", "dontPublish");

    ObjectMapper objectMapper = new ObjectMapper();

    private static String defaultValue = "dummy";


    public List<QuickStartResponse> getQuickStartListResponse (List<QuickStartDTO> quickStartDTOS, String quickStartType) {
        List<QuickStartResponse> responses = new ArrayList<>();

        for (QuickStartDTO quickStart : quickStartDTOS) {
            responses.add(getQuickStartResponse(quickStart, quickStartType));
        }

        return responses;
    }

    public QuickStartResponse getQuickStartResponse(QuickStartDTO quickStart, String quickStartType) {
        QuickStartResponse response = new QuickStartResponse();

        response.setId(quickStart.getId());
        if (null != quickStartType)
            response.setType(quickStartType);
        response.setDisplayName(quickStart.getDisplayName());
        response.setDescription(StringEscapeUtils.escapeHtml(quickStart.getDescription()));
        response.setPostInstallationInstruction(StringEscapeUtils.escapeHtml(quickStart.getPostInstallationInstruction()));
        response.setStatus(quickStart.getStatus());
        response.setIconPath(quickStart.getIconPath());
        response.setPublishToQuickStartLibrary((null == quickStart.getPublishToQuickStartLibrary()) ? "dontPublish" : quickStart.getPublishToQuickStartLibrary());
        response.setShareWithOrg(quickStart.isShareWithOrg());
        response.setTags(quickStart.getTags());
        response.setRequiredSynapses(quickStart.getRequiredSynapses());
        response.setShareWithInstances(quickStart.getShareWithInstances());
        response.setPipelines(getResponsePipeline(quickStart.getPipelines()));

        return response;
    }




    private List<Map<String, Object>> getResponsePipeline(QSPipelineConfigDTO qsPipelineConfigDTO) {
        List<Map<String, Object>> pipelines = new ArrayList<>();
        Map<String, Object> entities = new HashMap<>();
        List<Map<String, Object>> fields = new ArrayList<>();

        for (QSEntityPipelineDTO entity : qsPipelineConfigDTO.getEntities()) {
            Map<String, Object> e = new HashMap<>();
            e.put("entityId", entity.getId());
            e.put("apiName", entity.getApiName());
            e.put("displayName", entity.getDisplayName());

            for (QSFieldPipelineDTO field : entity.getFields()) {
                Map<String, Object> f = new HashMap<>();
                f.put("fieldId", field.getId());
                f.put("fieldApiName", field.getApiName());
                f.put("fieldDisplayName", field.getDisplayName());
                f.put("datatype", field.getDatatype());
                fields.add(f);
            }
            e.put("fields", fields);
            entities.put("entities", e);
        }
        pipelines.add(entities);

        return pipelines;
    }


    // prepare the base request body for calls to the core service layer
    public KeyValue convertQuickstartRunRequest(String quickstartId, QuickStartRunRequest quickstartRunRequest) {
        KeyValue keyValue = new KeyValue();
        keyValue.set("id", quickstartId);
        keyValue.set("quickStartTitle", defaultValue);
        keyValue.set("mergeOptionsTitle", defaultValue);
        keyValue.set("mergeOptionsDescription", defaultValue);
        keyValue.set("selectMergeOptions", new KeyValue("installStrategy", quickstartRunRequest.getInstallStrategy().toUpperCase(),
                "autoArrange", quickstartRunRequest.getAutoArrange().toString()));
        return keyValue;
    }

    // get the render type from the core response to help decide next step
    public List<String> getRenderTypes(QSAuthoringConfig qsAuthoringConfig) {
        List<String> renderTypes = new ArrayList<>();
        try {
            String configurationString = new ObjectMapper().writeValueAsString(qsAuthoringConfig.getConfiguration());
            JsonNode configs = mapper.readTree(configurationString);
            configs.forEach(config -> renderTypes.add(config.get("renderType").asText()));
        } catch (Exception e) { }
        return renderTypes;
    }

    // get synapse name to be use to get the right synapse data from the api request date
    public String getSynapseName(QSAuthoringConfig qsAuthoringConfig) {
        try {
            String configurationString = new ObjectMapper().writeValueAsString(qsAuthoringConfig.getConfiguration());
            JsonNode configs = mapper.readTree(configurationString);
            return configs.get(0).get("synapseName").asText();
        } catch (Exception e) {
            return null;
        }
    }

    // get the synapse name from the quickstart to map to the api request synapse
    public String getQsSynapseName(List<QuickstartRunTO> quickstartRunTOS, String synapseName) {
        try {
            for (QuickstartRunTO qsRunTO : quickstartRunTOS){
                if (qsRunTO.getSynapseName().equals(synapseName))
                    return qsRunTO.getQsSynapseName();
            }
        } catch (Exception e) {}
        return null;
    }

    public List<String> getQsSynapses(QuickStart quickStart){
        List<String> qsSynapses = new ArrayList<>();
        List<QSDependency> dependencies = new ArrayList<>();
        objectMapper.registerModule(new JavaTimeModule());

        try {
            var configuration = quickStart.getConfiguration();
            configuration.forEach(config -> {
                for (QSDependency dependency : ((PipelineQSConfig) config).findConnectorDependency(false)) {
                    try {
                        String sourceValue = objectMapper.writeValueAsString(dependency.getSourceValue());
                        JsonNode value = mapper.readTree(sourceValue);
                        if (!value.get("name").asText().equals("syncari"))
                            qsSynapses.add(value.get("name").asText());
                    } catch (Exception e) {
                    }
                }
            });
        } catch (Exception oe) { }

        return qsSynapses;
    }

    // Get quick start steps to determine what is required for the run
    public List<String> getQuickStartSteps(KeyValue keyValue) {
        List<String> returnedSteps = new ArrayList<>();

        try {
            String configString = new ObjectMapper().writeValueAsString(keyValue.get("config"));
            JsonNode configs = mapper.readTree(configString);
            for (JsonNode config : configs) {
                if (config.get("steps") != null) {
                    for (JsonNode step : config.get("steps")) {
                        returnedSteps.add(step.get("stepName").asText());
                    }
                }
            }

        } catch (Exception e) {}

        return returnedSteps;
    }

    // verify the synapses in the request are needed for the run and the synapses needed for the run are in the request
    public List<String> verifySynapses(QuickStartRunRequest quickstartRunRequest, List<String> qsSynapses) {

        List<String> errors = new ArrayList<>();

        List<Connector> connectors = connectorService.getAllActive();
        List<String> connectorNames = new ArrayList<>();
        connectors.forEach(c -> {connectorNames.add(c.getName());});

        for (Map<String, Object> synapse : quickstartRunRequest.getSynapses()) {
            if(!qsSynapses.contains(synapse.get("qsSynapseName").toString()))
                errors.add(i18n("qs_synapse_match_missing", synapse.get("qsSynapseName"), qsSynapses.toString()));

            if(!connectorNames.contains(synapse.get("synapseName").toString()))
                errors.add(i18n("qs_synapse_match_error_details", synapse.get("synapseName")));
        }

        return errors;

    }

    // get synapse mappings
    public List<QuickstartRunTO> getSynapseMappings(QuickStartRunRequest quickstartRunRequest, QSAuthoringConfig qsAuthoringConfig,
                                                    QuickStart quickStart) {

        List<QuickstartRunTO> connectorMappings = new ArrayList<>();

        if(quickstartRunRequest.getSynapses().isEmpty())
            return connectorMappings;

        try {
            String configurationString = new ObjectMapper().writeValueAsString(qsAuthoringConfig.getConfiguration());
            JsonNode qsConfig = mapper.readTree(configurationString);

            for (Map<String, Object> synapse : quickstartRunRequest.getSynapses()) {
                QuickstartRunTO quickstartRunTO = new QuickstartRunTO();
                quickstartRunTO.setQsSynapseName(synapse.get("qsSynapseName").toString());
                quickstartRunTO.setSynapseName(synapse.get("synapseName").toString());
                quickstartRunTO.setQsSynapseId(getQsSynapseId(synapse.get("qsSynapseName").toString(), quickStart));
                quickstartRunTO.setSynapseId(getSynapseId(synapse.get("synapseName").toString(), qsConfig));
                connectorMappings.add(quickstartRunTO);
            }
        } catch (Exception e) {
            throw new BadRequestException(i18n("qs_synapse_match_error"));
        }

        if(connectorMappings.isEmpty())
            throw new BadRequestException(i18n("qs_synapse_match_error_less_details", getRequestSynapseMap(quickstartRunRequest)));

        return connectorMappings;
    }

    // get quick start synapse id from the possible matches in the core response
    private String getQsSynapseId(String synapseName, QuickStart quickStart) {
        try {
            List<QSDependency> dependencies = new ArrayList<>();
            var configuration = quickStart.getConfiguration();
            configuration.forEach(config -> {
                dependencies.addAll(((PipelineQSConfig)config).findConnectorDependency(false));
            });

            for (QSDependency qsDependency : dependencies){
                Connector connector = (Connector) qsDependency.getSourceValue();
                if(connector.getName().equals(synapseName))
                    return qsDependency.getId();
            }
        } catch (Exception e) {}

        return null;
    }

    // get synapse id from the possible options in the core response
    private String getSynapseId(String synapseName, JsonNode qsConfig) {
        try {
            for (JsonNode config : qsConfig) {
                for (JsonNode match : config.get("resolutionData").get("matches")) {
                    for (JsonNode option : match.get("options")) {
                        if (synapseName.equals(option.get("label").asText())) {
                            return option.get("value").asText();
                        }
                    }
                }
            }
        } catch (Exception e) {}

        List<Connector> connectors = connectorService.getAllActive();
        for(Connector connector : connectors) {
            if(connector.getName().equals(synapseName))
                return connector.getId();
        }

        return null;
    }

    // format error response for unmapped synapses
    public String getRequestSynapseMap(QuickStartRunRequest quickstartRunRequest) {

        String synapseMap = quickstartRunRequest.getSynapses().stream()
                .map(p -> String.format("%s %s %s", p.get("qsSynapseName"), ":", p.get("synapseName")))
                .collect(Collectors.joining(" | "));

        return synapseMap;
    }

    // update the fiels mappings based on the api request data
    public List<QuickstartRunTO> getUpdateFieldMappings(QuickStartRunRequest quickstartRunRequest, QSAuthoringConfig qsAuthoringConfig,
                                                        List<QuickstartRunTO> synapseTOS, String synapseName) {

        List<QuickstartRunTO> returnTOS = new ArrayList<>();
        QuickstartRunTO synapseTO = new QuickstartRunTO();
        for(QuickstartRunTO qsRunTO : synapseTOS) {
            if(qsRunTO.getSynapseName().equals(synapseName))
                synapseTO = qsRunTO;
        }

        try {
            String configurationString = new ObjectMapper().writeValueAsString(qsAuthoringConfig.getConfiguration());
            JsonNode qsConfig = mapper.readTree(configurationString);

            List<QuickstartRunTO> qsRunTOS = buildQuickstartRunTo(quickstartRunRequest, synapseTO, qsConfig);
            return qsRunTOS;
        } catch (Exception e) {
            return null;
        }
    }

    // build the quick start run transfer object to be used to remap the fields based on the api request
    private List<QuickstartRunTO> buildQuickstartRunTo(QuickStartRunRequest quickstartRunRequest, QuickstartRunTO synapseTO,
                                                       JsonNode qsConfig) {

        List<QuickstartRunTO> qsRunTOS = new ArrayList<>();

        for (Map<String, Object> requestSynapse : quickstartRunRequest.getSynapses()) {
            if(requestSynapse.get("qsSynapseName").toString().equals(synapseTO.getQsSynapseName()) &&
                    requestSynapse.get("synapseName").toString().equals(synapseTO.getSynapseName())) {
                for (Map<String, Object> requestEntity : (List<Map<String, Object>>) requestSynapse.get("entities")) {
                    for (Map<String, Object> requestField : (List<Map<String, Object>>) requestEntity.get("fields")) {
                        QuickstartRunTO quickstartRunTO = new QuickstartRunTO();
                        // synapse mapping
                        quickstartRunTO.setQsSynapseName(requestSynapse.get("qsSynapseName").toString());
                        quickstartRunTO.setQsSynapseId(synapseTO.getQsSynapseId());
                        quickstartRunTO.setSynapseName(requestSynapse.get("synapseName").toString());
                        quickstartRunTO.setSynapseId(synapseTO.getSynapseId());
                        //entity mapping
                        String qsEntityId = getQsEntityId(requestEntity.get("qsEntityApiName").toString(), qsConfig);
                        quickstartRunTO.setQsEntityId(qsEntityId);
                        quickstartRunTO.setQsEntityApiName(requestEntity.get("qsEntityApiName").toString());
                        String entityId = getEntityId(synapseTO.getSynapseId(), requestEntity.get("entityApiName").toString());
                        quickstartRunTO.setEntityId(entityId);
                        quickstartRunTO.setEntityApiName(requestEntity.get("entityApiName").toString());
                        //field mapping
                        quickstartRunTO.setQsFieldId(getQsFieldId(requestEntity.get("qsEntityApiName").toString(),
                                (requestField.get("qsFieldApiName") != null) ? requestField.get("qsFieldApiName").toString() : null,
                                (requestField.get("qsFieldDisplayName") != null) ? requestField.get("qsFieldDisplayName").toString() : null,
                                qsConfig));
                        quickstartRunTO.setQsFieldApiName((requestField.get("qsFieldApiName") != null) ?
                                requestField.get("qsFieldApiName").toString() : requestField.get("qsFieldDisplayName").toString());
                        quickstartRunTO.setFieldId(getFieldId(entityId,
                                (requestField.get("fieldApiName") != null) ? requestField.get("fieldApiName").toString() : null,
                                (requestField.get("fieldDisplayName") != null) ? requestField.get("fieldDisplayName").toString() : null));
                        quickstartRunTO.setFieldApiName((requestField.get("fieldApiName") != null) ?
                                requestField.get("fieldApiName").toString() : requestField.get("fieldDisplayName").toString());

                        qsRunTOS.add(quickstartRunTO);
                    }
                }
            }
        }
        return qsRunTOS;
    }


    // get synapses from getSynapseMappings to be used in the larger quick start run to
    private String getSynapsesTO(String qsEntityApiName, JsonNode qsConfig){
        String qsEntityId = null;
        for (JsonNode config : qsConfig) {
            for (JsonNode item : config.get("items")) {
                if (qsEntityApiName.equals(item.get("entityApiName").asText())) {
                    return item.get("entityId").asText();
                }
            }
        }
        return qsEntityId;
    }


    // get the quick start entity id from the core response
    private String getQsEntityId(String qsEntityApiName, JsonNode qsConfig){
        String qsEntityId = null;
        for (JsonNode config : qsConfig) {
            for (JsonNode item : config.get("items")) {
                if (qsEntityApiName.equals(item.get("entityApiName").asText())) {
                    return item.get("entityId").asText();
                }
            }
        }
        return qsEntityId;
    }

    // get the entity id from getEntity service call in core
    private String getEntityId(String synapseId, String entityApiName){
        try {
            EntityDefinition entityDefinition = schemaService.getEntity(synapseId, entityApiName);
            return entityDefinition.getId();
        } catch(Exception e) {
            return null;
        }
    }

    // get the quick start field id from the core response
    private String getQsFieldId(String qsEntityApiName, String qsFieldApiName, String qsFieldDisplayName, JsonNode qsConfig){
        String qsFieldId = null;
        for (JsonNode config : qsConfig) {
            for (JsonNode item : config.get("items")) {
                // if field api name that is passed in is not null then loop through the config to find a matching api name
                if(qsFieldApiName != null) {
                    for (JsonNode field : item.get("fields")) {
                        if (qsEntityApiName.equals(item.get("entityApiName").asText()) &&
                                qsFieldApiName.equals(field.get("apiName").asText())) {
                            return field.get("id").asText();
                        }
                    }
                }
                // if field display name that is passed in is not null then loop through the config to find a matching api name.
                // this is a separate loop so not confuse the loopings between api name and display name
                if(qsFieldDisplayName != null) {
                    List<String> qsFieldIds = new ArrayList<>();
                    for (JsonNode field : item.get("fields")) {
                        if (qsEntityApiName.equals(item.get("entityApiName").asText()) &&
                                qsFieldDisplayName.equals(field.get("displayName").asText())) {
                            qsFieldIds.add(field.get("id").asText());
                        }
                    }
                    if(qsFieldIds.size() == 1)
                        return qsFieldIds.get(0);
                }
            }
        }
        return qsFieldId;
    }

    // get the entity id from getAttributeByName service call in core
    private String getFieldId(String entityId, String fieldApiName, String fieldDisplayName){
        try {
            if (fieldApiName != null) {
                AttributeDefinition attrributeDefinition = schemaService.getAttributeByName(entityId, fieldApiName);
                return attrributeDefinition.getId();
            }
            if (fieldDisplayName != null){
                List<String> attributeDefinitionIds = new ArrayList<>();
                List<AttributeDefinition> attributeDefinitions = schemaService.getAttributesByEntityId(entityId);
                attributeDefinitions.forEach(a -> {
                   if(a.getDisplayName().equals(fieldDisplayName))
                        attributeDefinitionIds.add(a.getId());
                });
                if (attributeDefinitionIds.size() == 1)
                    return attributeDefinitionIds.get(0);
            }

        } catch(Exception e) {}
        return null;
    }


    // get matched synapses to be used in the core call requests
    public KeyValue getMatchSynapse(List<QuickstartRunTO> matchSynapsesTOS) {
        List<QuickstartRunTO> synapseTOS = new ArrayList<>();
        for (QuickstartRunTO qrTO : matchSynapsesTOS){
            if(qrTO.getQsSynapseId() != null && qrTO.getSynapseId() != null)
                synapseTOS.add(qrTO);
        }

        KeyValue matchSynapse = new KeyValue();

        switch (synapseTOS.size()) {
            case 1:
                matchSynapse = KeyValue.of(synapseTOS.get(0).getQsSynapseId(), synapseTOS.get(0).getSynapseId());
                break;
            case 2:
                matchSynapse = KeyValue.of(synapseTOS.get(0).getQsSynapseId(), synapseTOS.get(0).getSynapseId(),
                        synapseTOS.get(1).getQsSynapseId(), synapseTOS.get(1).getSynapseId());
                break;
            case 3:
                matchSynapse = KeyValue.of(synapseTOS.get(0).getQsSynapseId(), synapseTOS.get(0).getSynapseId(),
                        synapseTOS.get(1).getQsSynapseId(), synapseTOS.get(1).getSynapseId(),
                        synapseTOS.get(2).getQsSynapseId(), synapseTOS.get(2).getSynapseId());
                break;
            case 4:
                matchSynapse = KeyValue.of(synapseTOS.get(0).getQsSynapseId(), synapseTOS.get(0).getSynapseId(),
                        synapseTOS.get(1).getQsSynapseId(), synapseTOS.get(1).getSynapseId(),
                        synapseTOS.get(2).getQsSynapseId(), synapseTOS.get(2).getSynapseId(),
                        synapseTOS.get(3).getQsSynapseId(), synapseTOS.get(3).getSynapseId());
                break;
            default:
                throw new RuntimeException(i18n("qs_synapse_match_error"));
        }
        return matchSynapse;
    }

    // build the core request for field mappings based on the mappings from getUpdateFieldMappings
    public KeyValue getRequestFieldMapping(List<QuickstartRunTO> qsRunTOS, QSAuthoringConfig qsAuthoringConfig, String synapseName) {

        KeyValue main = new KeyValue();
        var fieldDefaultValues = new KeyValue();
        List<QuickstartRunTO> updateFieldMappings = new ArrayList<>();

        for (QuickstartRunTO qsRunTO : qsRunTOS){
            if(qsRunTO.getSynapseName().equals(synapseName))
                updateFieldMappings.add(qsRunTO);
        }

        try {
            String configurationString = new ObjectMapper().writeValueAsString(qsAuthoringConfig.getConfiguration());
            JsonNode qsConfig = mapper.readTree(configurationString);
            for (JsonNode config : qsConfig) {
                for (JsonNode defaultValue : config) {
                    for (JsonNode fields : defaultValue) {
                        if (null != fields.get("matchValue")) {
                            List<String> keys = new ArrayList<>();
                            String fieldsfields = new ObjectMapper().writeValueAsString(fields.get("fields"));
                            JsonNode jsonNode = mapper.readTree(fieldsfields);
                            Iterator<String> iterator = jsonNode.fieldNames();
                            iterator.forEachRemaining(e -> keys.add(e));
                            for (String key : keys) {
                                String value = jsonNode.get(key).asText();
                                for (QuickstartRunTO changeFields : updateFieldMappings) {
                                    if(changeFields.getQsFieldId().equals(key)) {
                                        value = changeFields.getFieldId();
                                    }
                                }
                                fieldDefaultValues.put(key, value);
                            }
                        }
                    }
                }
            }

            // if there was no initial map to update add a new entry to the mapping
            for (QuickstartRunTO changeFields : updateFieldMappings) {
                if(!fieldDefaultValues.containsKey(changeFields.getQsFieldId())) {
                    fieldDefaultValues.put(changeFields.getQsFieldId(), changeFields.getFieldId());
                }
            }

            List<QuickstartRunTO> entitiesTO = new ArrayList<>();
            for (QuickstartRunTO qsrTO : updateFieldMappings) {
                QuickstartRunTO to = new QuickstartRunTO();
                to.setQsEntityId(qsrTO.getQsEntityId());
                to.setEntityId(qsrTO.getEntityId());
                entitiesTO.add(to);
            }

            switch (entitiesTO.size()) {
                case 1:
                    main = KeyValue.of(entitiesTO.get(0).getQsEntityId(),
                            KeyValue.of("matchValue", entitiesTO.get(0).getEntityId(),
                                    "fields", fieldDefaultValues));
                    break;
                case 2:
                    main = KeyValue.of(entitiesTO.get(0).getQsEntityId(),
                            KeyValue.of("matchValue", entitiesTO.get(0).getEntityId(),
                                    "fields", fieldDefaultValues),
                            entitiesTO.get(1).getQsEntityId(),
                            KeyValue.of("matchValue", entitiesTO.get(1).getEntityId(),
                                    "fields", fieldDefaultValues));
                    break;
                case 3:
                    main = KeyValue.of(entitiesTO.get(0).getQsEntityId(),
                            KeyValue.of("matchValue", entitiesTO.get(0).getEntityId(),
                                    "fields", fieldDefaultValues),
                            entitiesTO.get(1).getQsEntityId(),
                            KeyValue.of("matchValue", entitiesTO.get(1).getEntityId(),
                                    "fields", fieldDefaultValues),
                            entitiesTO.get(2).getQsEntityId(),
                            KeyValue.of("matchValue", entitiesTO.get(2).getEntityId(),
                                    "fields", fieldDefaultValues));
                    break;
                case 4:
                    main = KeyValue.of(entitiesTO.get(0).getQsEntityId(),
                            KeyValue.of("matchValue", entitiesTO.get(0).getEntityId(),
                                    "fields", fieldDefaultValues),
                            entitiesTO.get(1).getQsEntityId(),
                            KeyValue.of("matchValue", entitiesTO.get(1).getEntityId(),
                                    "fields", fieldDefaultValues),
                            entitiesTO.get(2).getQsEntityId(),
                            KeyValue.of("matchValue", entitiesTO.get(2).getEntityId(),
                                    "fields", fieldDefaultValues),
                            entitiesTO.get(3).getQsEntityId(),
                            KeyValue.of("matchValue", entitiesTO.get(3).getEntityId(),
                                    "fields", fieldDefaultValues));
                    break;
                default:
                    throw new RuntimeException(i18n("qs_field_match_error"));
            }
        } catch (Exception e) {
            throw new BadRequestException(i18n("qs_field_match_error"));
        }
        return main;
    }


    // verify request mappings and return errors based on null ids
    public List<String> getRequestErrors(List<QuickstartRunTO> qsRunTOS, boolean synapsesOnly) {

        List<String> errors = new ArrayList<>();

        for (QuickstartRunTO qsRunTO : qsRunTOS) {
            // synapses
            if (qsRunTO.getSynapseId() == null)
                errors.add(i18n("qs_unable_match_synapse",qsRunTO.getSynapseName(), qsRunTO.getQsSynapseName()));

            if (!synapsesOnly) {
                // entities
                if (qsRunTO.getQsEntityId() == null)
                    errors.add(i18n("qs_unable_match_qs_entity", qsRunTO.getQsEntityApiName(), qsRunTO.getEntityApiName()));
                if (qsRunTO.getEntityId() == null)
                    errors.add(i18n("qs_unable_match_entity", qsRunTO.getEntityApiName(), qsRunTO.getQsEntityApiName()));

                // fields
                if (qsRunTO.getQsFieldId() == null)
                    errors.add(i18n("qs_unable_match_qs_field", qsRunTO.getQsFieldApiName(), qsRunTO.getFieldApiName()));
                if (qsRunTO.getFieldId() == null)
                    errors.add(i18n("qs_unable_match_field", qsRunTO.getFieldApiName(), qsRunTO.getQsFieldApiName()));
            }
        }
        return errors;
    }

    // check core response for unsupported conflicts which the API currently does not handle
    public List<String> getUnsupportedConflictErrors(KeyValue keyValueQSConfig){
        List<KeyValue> configs = ((KeyValue)keyValueQSConfig.get("config")).get("configuration");
        List<String> errorResponse = new ArrayList<>();

        Map<String, String> errorMap = Map.of( "service_credentials","Missing service creds",
                "reference_data", "Missing required reference dataset");

        configs.forEach(c -> {
            if(!c.containsKey("resolutionData")) return;
            String type = ((KeyValue)c.get("resolutionData")).get("type");
            if("quickStartInstallErrorResolution".equalsIgnoreCase(c.get("renderType")) &&
                    errorMap.containsKey(type)) {
                errorResponse.add(errorMap.get(type));
            }
        });
        return errorResponse;
    }

    public List<String> validateQuickStartCreateRequest(QuickStartCreateRequest quickStartCreateRequest) {

        List<String> errors = new ArrayList<>();

        // validate display name
        if(null == quickStartCreateRequest.getDisplayName())
            errors.add(i18n("qs_create_field_mandatory", "displayName"));

        // validate entities and pipelines
        if(null == quickStartCreateRequest.getEntities()) {
            errors.add(i18n("qs_create_field_mandatory", "entities"));
        } else {
            List<Map<String, Object>> entities = quickStartCreateRequest.getEntities();
            entities.forEach(e -> {
                Optional<MappingGraph> graph = mappingGraphService.retrieve(e.get("pipelineId").toString());

                if (!graph.isPresent())
                    errors.add(i18n("mapping_graph_not_found", e.get("pipelineId").toString()));

                if (graph.isPresent()) {
                    if (!graph.get().getScope().equals(Scope.ENTITY))
                        errors.add(i18n("mapping_graph_not_entity", graph.get().getId()));

                    if (!graph.get().getDraftStatus().equals(DraftStatus.APPROVED))
                        errors.add(i18n("mapping_graph_not_approved", graph.get().getId()));

                }
            });
        }

        // validate publishToQuickStartLibrary
        if (!publishValues.contains(quickStartCreateRequest.getPublishToQuickStartLibrary()))
            errors.add(i18n("qs_invalid_publish_value", quickStartCreateRequest.getPublishToQuickStartLibrary(), publishValues.toString()));

        // validate instances
        if(null != quickStartCreateRequest.getShareWithInstances()) {
            for(String instance : quickStartCreateRequest.getShareWithInstances()) {
                try {
                    subscriptionService.getInstance(instance);
                } catch (Exception inf) {
                    errors.add(i18n("instance_not_found", instance));
                }
            }
        }

        // validate file
        try {
            imageUtil.validateFile(quickStartCreateRequest.getIcon());
        } catch (Exception e) {
            errors.add(e.getMessage());
        }

        return errors;
    }

    public QSPipelineConfigDTO getQsPipelineConfigDTO(List<Map<String, Object>> entities) {
        QSPipelineConfigDTO qsPipelineConfigDTO = new QSPipelineConfigDTO();
        List<QSEntityPipelineDTO> qsEntityPipelineDTOS = new ArrayList<>();
        entities.forEach(e -> {
            MappingGraph mappingGraph = mappingGraphService.retrieve(e.get("pipelineId").toString()).get();
            EntityDefinition entityDefinition = schemaService.getEntity(mappingGraph.getTargetId());
            QSEntityPipelineDTO qsEntityPipelineDTO = new QSEntityPipelineDTO();
            qsEntityPipelineDTO.setId(entityDefinition.getId());
            qsEntityPipelineDTO.setApiName(entityDefinition.getApiName());
            qsEntityPipelineDTO.setDisplayName(entityDefinition.getDisplayName());
            List<String> excludeFields = (null == e.get("excludeFields") ? new ArrayList<>() : (List<String>) e.get("excludeFields"));
            qsEntityPipelineDTO.setFields(getQsFields(entityDefinition.getId(), excludeFields));
            qsEntityPipelineDTOS.add(qsEntityPipelineDTO);
        });

        qsPipelineConfigDTO.setEntities(qsEntityPipelineDTOS);

        return qsPipelineConfigDTO;
    }

    private List<QSFieldPipelineDTO> getQsFields(String entityId, List<String> excludeFields) {
        List<QSFieldPipelineDTO> qsFieldPipelineDTOS = new ArrayList<>();

        DraftStatus status = DraftStatus.valueOf("APPROVED");
        EntityDef entityDefinition = schemaService.getSchemaByEntityId(entityId).getEntities().stream()
                .findFirst().orElseThrow(() -> new SyncariValidationException(String.format(i18n("syncari_entity_not_found"), entityId)));

        Optional<MappingGraph> entityGraph = mappingGraphService.retrieveEntityGraph(entityId, status);

        entityGraph.ifPresent(eg -> {
            List<MappingGraph> attributeGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(eg.getId());
            List<String> mappedIds = new ArrayList<>();
            attributeGraphs.forEach(ag -> {mappedIds.add(ag.getTargetId());});

            for (AttributeDef a : entityDefinition.getFields()) {
                if (!excludeFields.contains(a.getApiName()) && mappedIds.contains(a.getId())) {
                    QSFieldPipelineDTO qsFieldPipelineDTO = new QSFieldPipelineDTO();
                    qsFieldPipelineDTO.setId(a.getId());
                    qsFieldPipelineDTO.setApiName(a.getApiName());
                    qsFieldPipelineDTO.setDatatype(a.getDataType());
                    qsFieldPipelineDTO.setDisplayName(a.getDisplayName());
                    qsFieldPipelineDTOS.add(qsFieldPipelineDTO);
                }
            }
        });

        return qsFieldPipelineDTOS;
    }


}
