package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.rest.controllers.data.CreateEntitySetting;
import com.syncari.api.rest.controllers.data.EntityMapping;
import com.syncari.api.rest.controllers.data.FieldMapping;
import com.syncari.api.rest.controllers.data.FieldSelectionSetting;
import com.syncari.connector.Constants;
import com.syncari.core.SyncariContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.Event;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.schema.AttributeDef;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.schema.PipelineStatus;
import com.syncari.core.schema.Schema;
import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.KeyValue;
import static com.syncari.utils.I18n.i18n;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/schema")
@Setter
public class SchemaController {
    @Autowired
    SchemaService schemaService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    MappingGraphService mappingService;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    Publisher publisher;

    private static final String ICON_PATH = "/assets/icons/%s.svg";

    //@Secured(READ_STUDIO)
    @PreAuthorize("hasAnyAuthority('READ_STUDIO', 'READ_DATA_STUDIO')")
    @RequestMapping(method = RequestMethod.GET)
    public Schema getSyncariSchema() {
        Schema schema = schemaService.getSyncariSchema(true, true);
        schema.getEntities().forEach(entityDef -> {
            entityDef.setIconPath(getIconPath(entityDef.getPipelineStatus()));
            entityDef.setSubLabel(entityDef.getPipelineStatus().name());
            //DO not show Id Fields
            List<AttributeDef> attributes = entityDef.getActiveFields().stream().collect(Collectors.toList());
            attributes.sort(Comparator.comparing(AttributeDef::getDisplayName));
            entityDef.setFields(attributes);
        });
        schema.getEntities().sort(Comparator.comparing(EntityDef::getDisplayName));
        return schema;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/{connectorId}")
    public Schema getSchemaFor(@PathVariable String connectorId, @RequestParam(defaultValue = "true") boolean detailed) {
        return schemaService.getSchemaFor(connectorId, detailed);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entity/{entityId}")
    public Schema getSchemaForEntity(@PathVariable String entityId) {
        return schemaService.getSchemaByEntityId(entityId);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityMapping/{connectorId}")
    public CreateEntitySetting getEntityMapping(@PathVariable String connectorId) {
        CreateEntitySetting entry = new CreateEntitySetting();
        List<CreateEntitySetting> mappings = new ArrayList<>();
        Schema synapseSchema = schemaService.getSchemaDetailedWithoutTags(connectorId);

        Map<String, EntityDef> syncariNameToDef = new HashMap<String, EntityDef>();
        synapseSchema.getEntities().forEach(e -> syncariNameToDef.put(e.getId(), e));

        ConnectorMetadata connectorMetadata = connectorService
                .describeById(connectorService.get(connectorId).getMetadataId());
        for (EntityDef e : synapseSchema.getEntities()) {
            if((connectorMetadata.getName() == Constants.SYNCARI && !e.isSyncariSource())) {
                continue;
            }
            EntityMapping entityMapping = new EntityMapping();
            entityMapping.setId(e.getId());
            entityMapping.setName(e.getDisplayName());
            entityMapping.setApiName(e.getApiName());

            if (connectorService.supportsNoWatermark(connectorId)) {
                entityMapping.setNeedsOffsetField(false);
            }

            String selectedOffsetFieldId = null;
            List<KeyValue> offsetFieldList = new ArrayList<>();

            for (AttributeDef a : e.getFields()) {
                if (connectorMetadata.getWatermarkFieldName() != null
                        && connectorMetadata.getWatermarkFieldName().equalsIgnoreCase(a.getApiName())) {
                    selectedOffsetFieldId = a.getId();
                } else if (a.isWatermarkField()) {
                    selectedOffsetFieldId = a.getId();
                    entityMapping.setOffsetFieldReadOnly(true);
                } else if (!a.isPotentialWatermarkField()) {
                    continue;
                }
                offsetFieldList.add(new KeyValue("id", a.getId(), "name", a.getApiName(), "label", a.getDisplayName()));
            }
            entityMapping.setOffsetFieldList(offsetFieldList);
            if (!connectorMetadata.isWatermarkCustomizable()) {
                entityMapping.setOffsetFieldReadOnly(true);
            }
            entityMapping.setSelectedOffsetFieldId(selectedOffsetFieldId);
            entry.getEntityMapping().add(entityMapping);
            mappings.add(entry);
        }
        return entry;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityMapping/{connectorId}")
    public CreateEntitySetting createSynapseEntities(@PathVariable String connectorId,
            @RequestBody CreateEntitySetting setting) {
        for (EntityMapping mapping : setting.getEntityMapping()) {
            EntityDefinition sourceEntity = schemaService.getEntity(mapping.getId());
            // Create the source entity in Syncari
            schemaService.createEntityLike(sourceEntity, mapping.getSelectedConnectorIds());
        }
        return setting;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/refresh/{connectorId}")
    public void refreshSchema(@PathVariable String connectorId) {
        Event event = new Event().setType(EventTypes.REFRESH_SCHEMA).setDetails(Map.of("connectorId", connectorId));
        Message msg = new Message(SyncariContext.getSyncariId(), event);
        try {
            String eventString = mapper.writeValueAsString(msg);
            log.info(String.format("Sending Message: %s", eventString));
            publisher.publishToGenericQueue(eventString);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new SyncariValidationException("Error during schema refresh. Please contact Syncari support");
        }
    }
    
    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/entity/{entityId}")
    public void deleteEntity(@PathVariable String entityId) {
        schemaService.deleteEntity(entityId);
    }
    
    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entity/{entityId}/{graphVersion}")
    public EntityDef getSyncariEntity(@PathVariable String entityId, @PathVariable String graphVersion) {
        return getSyncariEntityDef(entityId, graphVersion);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entity/{entityId}/{graphVersion}/quickstart")
    public ResponseEntity<?> getSyncariEntityForQuickStart(@PathVariable String entityId, @PathVariable String graphVersion) {
        if(mappingService.checkForDraftSynapse(entityId)) {
            return ResponseEntity.ok(Map.of("errorMessage", "This pipeline has a custom synapse draft and cannot be included in a Quickstart."));
        }
        return ResponseEntity.ok(getSyncariEntityDef(entityId, graphVersion));
    }

    private EntityDef getSyncariEntityDef(String entityId, String graphVersion) {
        try {
			DraftStatus status = DraftStatus.valueOf(graphVersion);
			List<MappingGraph> approvedAttribs = mappingService.retrieveEntityGraphLite(entityId, DraftStatus.APPROVED)
					.map(graph -> mappingService.retrieveAttributeGraphsLiteForEntityGraph(graph.getId()))
					.orElse(Collections.emptyList());
			List<MappingGraph> draftAttribs = mappingService.retrieveEntityGraphLite(entityId, DraftStatus.NEW)
					.map(graph -> mappingService.retrieveAttributeGraphsLiteForEntityGraph(graph.getId()))
					.orElse(Collections.emptyList());
			EntityDef entityDefinition = schemaService.getSchemaByEntityId(entityId).getEntities().stream().findFirst()
					.orElseThrow(() -> new SyncariValidationException(
							String.format(i18n("syncari_entity_not_found"), entityId)));
			Optional<MappingGraph> entityGraph = mappingService.retrieveEntityGraphLite(entityId, status);

            entityGraph.ifPresent(eg -> {
                List<MappingGraph> attributeGraphs = mappingService.retrieveAttributeGraphsLiteForEntityGraph(eg.getId());
                for (AttributeDef a : entityDefinition.getFields()) {
                	if(filterAttrBy(approvedAttribs, a).isPresent() && filterAttrBy(draftAttribs, a).isPresent()) {
                		a.setPipelineStatus(PipelineStatus.PUBLISHED_WITH_DRAFT);
                	} else if(filterAttrBy(approvedAttribs, a).isPresent()) {
                		a.setPipelineStatus(PipelineStatus.PUBLISHED);
                	} else if(filterAttrBy(draftAttribs, a).isPresent()) {
                		a.setPipelineStatus(PipelineStatus.DRAFT);
                	} else {
                		a.setPipelineStatus(PipelineStatus.UNMAPPED);
                	}
                	a.setHasPublishedPipeline(filterAttrBy(approvedAttribs, a).isPresent());
                    filterAttrBy(attributeGraphs, a).ifPresent(g -> {
                    	if(!g.isDeleted()) {
                    		a.setMapped(true);
                    	}
                        if(status == DraftStatus.NEW) {
                            a.setHasChanges(g.isChanged());
                        }
                        a.setReady(g.isReady());
                    });
                }
            });
            return entityDefinition;
        } catch (IllegalArgumentException ex) {
            new SyncariValidationException(String.format(i18n("unknown_graph_version"), graphVersion));
        }
        return null;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/fieldMapping/{syncariEntityId}/{synapseEntityId}")
    public List<FieldMapping> getUnmappedAttributesFor(@PathVariable String syncariEntityId,
            @PathVariable String synapseEntityId) {
        List<FieldMapping> mappings = new ArrayList<>();
        List<AttributeDefinition> unmappedAttributesFor = schemaService.getUnmappedAttributesFor(synapseEntityId);
        for (AttributeDefinition attr : unmappedAttributesFor) {
            FieldMapping mapping = new FieldMapping();
            mapping.setSynapseEntityId(attr.getEntityId());
            mapping.setSynapseFieldId(attr.getId());
            mapping.setSynapseFieldName(attr.getDisplayName());
            mapping.setSynapseApiName(attr.getApiName());
            mapping.setSyncariEntityId(syncariEntityId);
            mapping.setDataType(attr.getDataType().getName());
            mappings.add(mapping);
        }
        return mappings;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/fieldMapping/{connectorId}")
    public String createGraphForSynapseFields(@PathVariable String connectorId,
            @RequestBody FieldSelectionSetting setting) {
        if (setting.getFieldMapping() == null || setting.getFieldMapping().isEmpty())
            return "";
        String graphDraftId = setting.getFieldMapping().get(0).getGraphDraftId();
        Map<String, EntityDefinition> sinkMap = mappingService.getConnectorToEntityMapForSinks(graphDraftId);
        for (FieldMapping mapping : setting.getFieldMapping()) {
            EntityDefinition syncariEntity = schemaService.getEntity(mapping.getSyncariEntityId());
            AttributeDefinition fromSynapseAttr = schemaService.getAttribute(mapping.getSynapseFieldId());
            String referenceEntityId = StringUtils.isBlank(mapping.getReferenceEntityId())? null : mapping.getReferenceEntityId();
            schemaService.createAttributeLike(fromSynapseAttr, Optional.ofNullable(referenceEntityId), syncariEntity, sinkMap, mapping.getSelectedConnectorIds());
        }
        return "success";
    }

    /**
     * Generates and returns the iconPath associated with pipelineStatus
     * 
     * @param pipelineStatus
     * @return subLabel as String
     */
    private String getIconPath(PipelineStatus pipelineStatus) {
        String name = StringUtils.isBlank(pipelineStatus.name()) ? "default"
                : pipelineStatus.name().toLowerCase().replace("_", "-");
        return String.format(ICON_PATH, name);
    }
    
    private Optional<MappingGraph> filterAttrBy(List<MappingGraph> attributeGraphs, AttributeDef a) {
        return attributeGraphs.stream()
                .filter(g -> g.getTargetId().equalsIgnoreCase(a.getId())).findFirst();
    }
}
