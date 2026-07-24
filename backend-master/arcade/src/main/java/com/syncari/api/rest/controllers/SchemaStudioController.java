package com.syncari.api.rest.controllers;

import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.studio.Entity;
import com.syncari.api.rest.controllers.data.studio.EntityVersions;
import com.syncari.api.rest.controllers.data.studio.SchemaResponse;
import com.syncari.core.DataTransformer;
import com.syncari.core.Link;
import com.syncari.core.Route;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.schema.AttributeDef;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.*;
import com.syncari.utils.I18n;
import com.syncari.utils.KeyValue;
import com.syncari.utils.TextUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/studio/schema")
@Data
public class SchemaStudioController {
    private static final String DATA_TYPE = "dataType";
    private static final String VALUE = "value";
    private static final String KEY = "label";
    @Autowired
    SchemaService schemaService;
    @Autowired
    ObjectTransformer transformer;
    @Autowired
    DataTransformer dataTransformer;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    MappingGraphService mappingGraphService;
    @Autowired
    TagService tagService;
    @Autowired
    EntityRepoService entityRepoService;
    @Autowired
    UserService userService;
    @Autowired
    TextUtil textUtil;


    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/{connectorId}")
    public SchemaResponse getSchema(@PathVariable String connectorId) {
        Schema schema = schemaService.getAllSchemaFor(connectorId,false);
        boolean isSyncari = isSyncari(connectorId);
        SchemaResponse response = new SchemaResponse();

        response.setMeta(getMetaFieldsForEntities(isSyncari));

        Map<String, EntityVersions> dataMap = new LinkedHashMap<String, EntityVersions>();
        Set<String> userIds = schema.getEntities().stream().map(e1 -> e1.getUpdatedBy()).collect(Collectors.toSet());
        Map<String, User> usersById = userService.getUsersById(userIds);

        List<String> entityDefIds = schema.getEntities().stream().map(x -> x.getId()).collect(Collectors.toList());
        Map<String, List<Link>> entityLinks = getUsedInDependencyLinksForEntity(entityDefIds);

        schema.getEntities().forEach(e -> {
            dataMap.putIfAbsent(e.getApiName(), new EntityVersions());
            EntityVersions data = dataMap.get(e.getApiName());
            String userName = getUserName(usersById, e.getUpdatedBy());
            Entity entity = populateEntity(e, isSyncari, userName);
            // Add entity references to graphs.
            if (entityLinks.containsKey(entity.getId()) && !isSyncari) {
                entity.addField("usedIn", entityLinks.get(entity.getId()));
            }
            data.setApiName(e.getApiName());

            if(e.getDraftStatus() == DraftStatus.NEW) {
                data.setDraft(entity);
            } else {
                data.setPublished(entity);
            }
        });
        response.getData().addAll(dataMap.values());
        return response;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entity/{entityId}")
    public SchemaResponse getSchemaForEntity(@PathVariable String entityId) {

        long startTime = System.currentTimeMillis();
        EntityDefinition schema = schemaService.getEntity(entityId);
        List<EntityDefinition> allEntities = schemaService.getEntityVersionsByName(schema.getConnectorId(), schema.getApiName());
        log.debug("Retrieved entities and attributes for entity {} in {} ms", entityId, System.currentTimeMillis() - startTime);

        Connector synapse = connectorService.find(schema.getConnectorId()).get();
        boolean isSyncari = isSyncari(schema.getConnectorId());
        SchemaResponse response = new SchemaResponse();
        response.setMeta(getMetaFieldsForFields(isSyncari));

        Map<String, EntityVersions> dataMap = new LinkedHashMap<String, EntityVersions>();
        Set<String> userIds = allEntities.stream().map(e -> e.getUpdatedBy()).collect(Collectors.toSet());
        Map<String, User> usersById = userService.getUsersById(userIds);
        allEntities.forEach(e -> {
            e.getAttributes().forEach(a -> {
                dataMap.putIfAbsent(a.getApiName(), new EntityVersions());
                EntityVersions data = dataMap.get(a.getApiName());
                String userName = getUserName(usersById, e.getUpdatedBy());
                Entity entity = populateField(a, userName, synapse.getName(), e.getApiName(), isSyncari);

                data.setApiName(a.getApiName());
                if(e.getDraftStatus() == DraftStatus.NEW) {
                    data.setDraft(entity);
                } else {
                    data.setPublished(entity);
                }
            });
        });

        response.getData().addAll(dataMap.values());

        return response;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entityDetail/{entityId}")
    public EntityDef getEntityDetail(@PathVariable String entityId){
        EntityDefinition entity = schemaService.getEntity(entityId);
        return dataTransformer.toEntityDef(entity);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entity")
    public EntityDef createEntityDraft(@RequestBody EntityDef entityDef) {
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition entity = dataTransformer.toEntityDefinition(entityDef);
        entity.setConnectorId(syncariConnector.getId());
        entity.setConnectorTypeId(syncariConnector.getMetadataId());
        EntityDefinition saved = schemaService.createDraftEntity(entity, false);
        return dataTransformer.toEntityDef(saved);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.PUT, value = "/entity/{syncariEntityId}")
    public EntityDef updateEntityDraft(@PathVariable String syncariEntityId, @RequestBody EntityDef entityDef) {
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition updatedEntity = dataTransformer.toEntityDefinition(entityDef);
        updatedEntity.setId(syncariEntityId);
        updatedEntity.setConnectorId(syncariConnector.getId());
        updatedEntity.setConnectorTypeId(syncariConnector.getMetadataId());
        EntityDefinition saved = schemaService.updateDraftEntity(updatedEntity);
        return dataTransformer.toEntityDef(saved);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entityDraft/{syncariEntityId}")
    public EntityDef createEntityDraftFor(@PathVariable String syncariEntityId) {
        EntityDefinition draft = schemaService.createEntityDraftFor(syncariEntityId);
        return dataTransformer.toEntityDef(draft);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/approveEntity/{syncariEntityId}")
    public void approveEntity(@PathVariable String syncariEntityId) {
        EntityDefinition schema = schemaService.getEntity(syncariEntityId);
        EntityDefinition entityToApprove = schema.isDraft() ? schema : schemaService.getDraftEntity(schema.getConnectorId(), schema.getApiName());
        schemaService.approveDraftEntity(entityToApprove);

    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/discardEntityDraft/{syncariEntityId}")
    public void discardEntityDraft(@PathVariable String syncariEntityId) {
        // check if syncariEntity exists in draft mode
        EntityDefinition schema = schemaService.getEntity(syncariEntityId);
        EntityDefinition entityToDiscard = schema.isDraft() ? schema : schemaService.getDraftEntity(schema.getConnectorId(), schema.getApiName());
        schemaService.discardDraftEntity(entityToDiscard);

    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/entity/{entityId}/attribute")
    public AttributeDef addField(@PathVariable String entityId, @RequestBody AttributeDef attribute) {
        validateAttribute(attribute,entityId);
        AttributeDefinition attr = dataTransformer.toAttributeDefinition(attribute);
        attr = schemaService.createDraftAttribute(entityId, attr);
        return dataTransformer.toAttributeDef(attr);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.PUT, value = "/entity/{entityId}/attribute/{attributeId}")
    public AttributeDef updateField(@PathVariable String entityId, @PathVariable String attributeId, @RequestBody AttributeDef attribute) {
        validateAttribute(attribute,entityId);
        AttributeDefinition updatedAttr = dataTransformer.toAttributeDefinition(attribute);
        updatedAttr.setId(attributeId);
        updatedAttr = schemaService.updateDraftAttribute(entityId, updatedAttr);
        return dataTransformer.toAttributeDef(updatedAttr);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/entity/{syncariEntityId}/discardAttributeDraft/{attributeId}")
    public void discardField(@PathVariable String syncariEntityId, @PathVariable String attributeId) {
        AttributeDefinition attributeToDiscard = schemaService.getAttribute(attributeId);
        schemaService.discardDraftAttribute(syncariEntityId, attributeToDiscard);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/entity/{syncariEntityId}/attribute/{fieldId}")
    public void deleteField(@PathVariable String syncariEntityId, @PathVariable String fieldId) {
        AttributeDefinition attributeTobeDeleted = schemaService.getAttribute(fieldId);
        validateCondition(attributeTobeDeleted.isSystem(),I18n.i18n("attribute_system_delete_notallowed"));
        schemaService.deleteField(syncariEntityId, fieldId);
    }

    private Map<String, KeyValue> getMetaFieldsForEntities(boolean isSyncari) {
        Map<String, KeyValue> meta = new TreeMap<String, KeyValue>();
        meta.put("displayName", new KeyValue(KEY, "displayName", VALUE, "Display Name").set(DATA_TYPE, "string"));
        meta.put("apiName", new KeyValue(KEY, "apiName", VALUE, "Api Name").set(DATA_TYPE, "string"));
        meta.put("dataStoreName", new KeyValue(KEY, "dataStoreName", VALUE, "Data Store Name").set(DATA_TYPE, "string"));
        meta.put("description", new KeyValue(KEY, "description", VALUE, "Description").set(DATA_TYPE, "string"));
        if(!isSyncari) {
            meta.put("type", new KeyValue(KEY, "type", VALUE, "Type").set(DATA_TYPE, "string"));
        }
        meta.put("status", new KeyValue(KEY, "status", VALUE, "Status").set(DATA_TYPE, "string"));
        meta.put("readonly", new KeyValue(KEY, "readonly", VALUE, "Read Only").set(DATA_TYPE, "boolean"));
        meta.put("totalRecords", new KeyValue(KEY, "totalRecords", VALUE, "Total Records").set(DATA_TYPE, "number"));
        meta.put("lastUpdated", new KeyValue(KEY, "lastUpdated", VALUE, "Data Last Updated").set(DATA_TYPE, "datetime"));
        meta.put("updatedBy", new KeyValue(KEY, "updatedBy", VALUE, "Updated By").set(DATA_TYPE, "string"));
        meta.put("tags", new KeyValue(KEY, "tags", VALUE, "Tags").set(DATA_TYPE, "list"));
        if(!isSyncari) {
            meta.put("usedIn", new KeyValue(KEY, "usedIn", VALUE, "Used In").set(DATA_TYPE, "list"));
        }
        meta.put("id", new KeyValue(KEY, "id", VALUE, "Id").set(DATA_TYPE, "string"));
        return meta;
    }

    private Map<String, KeyValue> getMetaFieldsForFields(boolean isSyncari) {
        Map<String, KeyValue> meta = new TreeMap<>();
        meta.put("displayName", new KeyValue(KEY, "displayName", VALUE, "Display Name").set(DATA_TYPE, "string"));
        meta.put("apiName", new KeyValue(KEY, "apiName", VALUE, "Api Name").set(DATA_TYPE, "string"));
        meta.put("dataType", new KeyValue(KEY, "dataType", VALUE, "Data Type").set(DATA_TYPE, "string"));
        meta.put("defaultValue", new KeyValue(KEY, "defaultValue", VALUE, "Default Value").set(DATA_TYPE, "string"));
        meta.put("length", new KeyValue(KEY, "length", VALUE, "Length").set(DATA_TYPE, "number"));
        meta.put("precision", new KeyValue(KEY, "precision", VALUE, "Precision").set(DATA_TYPE, "number"));
        meta.put("isIdField", new KeyValue(KEY, "isIdField", VALUE, "Id Field").set(DATA_TYPE, "boolean"));
        meta.put("compositeKey", new KeyValue(KEY, "compositeKey", VALUE, "Composite Key").set(DATA_TYPE, "string"));
        meta.put("isMultiValueField", new KeyValue(KEY, "isMultiValueField", VALUE, "Multi Value Field").set(DATA_TYPE, "boolean"));
        meta.put("isWatermarkField", new KeyValue(KEY, "isWatermarkField", VALUE, "Watermark Field").set(DATA_TYPE, "boolean"));
        meta.put("dataStoreName", new KeyValue(KEY, "dataStoreName", VALUE, "Data Store Name").set(DATA_TYPE, "string"));
        meta.put("description", new KeyValue(KEY, "description", VALUE, "Description").set(DATA_TYPE, "string"));
        meta.put("status", new KeyValue(KEY, "status", VALUE, "Status").set(DATA_TYPE, "string"));
        meta.put("isRequired", new KeyValue(KEY, "isRequired", VALUE, "Required").set(DATA_TYPE, "boolean"));
        meta.put("isCalculated", new KeyValue(KEY, "isCalculated", VALUE, "Calculated").set(DATA_TYPE, "boolean"));
        meta.put("isUnique", new KeyValue(KEY, "isUnique", VALUE, "Unique").set(DATA_TYPE, "boolean"));
        meta.put("isSystem", new KeyValue(KEY, "isSystem", VALUE, "System").set(DATA_TYPE, "boolean"));
        meta.put("referenceTo", new KeyValue(KEY, "referenceTo", VALUE, "Reference To").set(DATA_TYPE, "string"));
        meta.put("referenceTargetField", new KeyValue(KEY, "referenceTargetField", VALUE, "Reference Target Field").set(DATA_TYPE, "string"));
        meta.put("picklistValues", new KeyValue(KEY, "picklistValues", VALUE, "Picklist Values").set(DATA_TYPE, "list"));
        meta.put("references", new KeyValue(KEY, "references", VALUE, "References").set(DATA_TYPE, "list"));
        meta.put("lastUpdated", new KeyValue(KEY, "lastUpdated", VALUE, "Data Last Updated").set(DATA_TYPE, "datetime"));
        meta.put("updatedBy", new KeyValue(KEY, "updatedBy", VALUE, "Updated By").set(DATA_TYPE, "string"));
        meta.put("tags", new KeyValue(KEY, "tags", VALUE, "Tags").set(DATA_TYPE, "list"));
        meta.put("id", new KeyValue(KEY, "id", VALUE, "Id").set(DATA_TYPE, "string"));
        meta.put("isSyncariDefined", new KeyValue(KEY, "isSyncariDefined", VALUE, "Syncari Defined").set(DATA_TYPE, "boolean"));
        meta.put("parentAttributeId", new KeyValue(KEY, "parentAttributeId", VALUE, "Parent Attribute Id").set(DATA_TYPE, "string"));
        meta.put("tokenCode", new KeyValue(KEY, "tokenCode", VALUE, "Token Code").set(DATA_TYPE, "string"));
        meta.put("isReadonly", new KeyValue(KEY, "isReadonly", VALUE, "Read only").set(DATA_TYPE, "boolean"));

        if(!isSyncari){
            meta.put("isCreateonly", new KeyValue(KEY, "isCreateonly", VALUE, "Create only").set(DATA_TYPE, "boolean"));
        }
        return meta;
    }

    private boolean isSyncari(String connectorId) {
        return connectorId.equalsIgnoreCase(connectorService.getSyncariConnector().getId());
    }

    protected Map<String, List<Link>> getUsedInDependencyLinksForEntity(List<String> entityIds){
        Map<String, List<MappingGraph>> allGraphs = mappingGraphService.findEntityGraphsByConnectorEntityId(entityIds);

        Map<String, List<Link>> linksByEntity = new HashMap<>();
        allGraphs.keySet().forEach(entityDefId -> {
            // process graphsWithEntity such that each syncariEntity id has APPROVED graph if exists else DRAFT graph
            Map<String, MappingGraph> syncariEntityIdToGraphMap = new HashMap<>();
            allGraphs.get(entityDefId).forEach(graph -> {
                if(syncariEntityIdToGraphMap.containsKey(graph.getTargetId())){
                    // check if map has draft graph replace it with Approved
                    var graphInMap = syncariEntityIdToGraphMap.get(graph.getTargetId());
                    if(graphInMap.getDraftStatus().equals(DraftStatus.NEW) && graph.getDraftStatus().equals(DraftStatus.APPROVED)){
                        syncariEntityIdToGraphMap.put(graph.getTargetId(), graph);
                    }
                }else{
                    syncariEntityIdToGraphMap.put(graph.getTargetId(), graph);
                }
            });
            List<Link> usedInReferenceLinks = new ArrayList<>();
            syncariEntityIdToGraphMap.values().forEach(g -> {

                var route = new Route(Route.RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION);
                route.setRouteParams(new com.syncari.utils.KeyValue("entityId", g.getTargetId()).set("graphVersion", g.getDraftStatus().name()));
                usedInReferenceLinks.add(new Link().setDisplayText(g.getName()).setRoute(route));
            });
            linksByEntity.put(entityDefId, usedInReferenceLinks);
        });
        return linksByEntity;
    }

    private Entity populateEntity(EntityDef e, boolean isSyncari, String userName){
        Entity entity = new Entity();

        entity.addField("id", e.getId())
        .addField("displayName", e.getDisplayName())
                .addField("apiName", e.getApiName())
                .addField("dataStoreName", e.getDataStoreName())
                .addField("description", e.getDescription())
                .addField("status", e.getStatus())
                .addField("readonly", e.isReadonly())
                .addField("totalRecords", entityRepoService.getCount(e.getApiName()))
                .addField("lastUpdated", e.getUpdatedAt())
                .addField("updatedBy", userName)
                .addField("tags", e.getTags());

        if(!isSyncari) {
            entity.addField("type", e.getType());
        }

        return entity;
    }

    private Entity populateField(AttributeDefinition a, String userName, String synapseName, String entityName, boolean isSyncariField){
        String token = "syncari".equalsIgnoreCase(synapseName) ? "{{record.values." + a.getApiName() + "}}"
                : "{{" + synapseName + "." + entityName + "." + a.getApiName() + "}}";
        Entity entity = new Entity()
                .addField("id", a.getId())
                .addField("displayName", a.getDisplayName())
                .addField("apiName", a.getApiName())
                .addField("dataType", a.getDataType().getName())
                .addField("defaultValue", a.getDefaultValue())
                .addField("length", a.getLength())
                .addField("precision", a.getPrecision())
                .addField("isIdField", a.isIdField())
                .addField("compositeKey",a.getCompositeKey())
                .addField("isMultiValueField", a.isMultiValueField())
                .addField("isWatermarkField", a.isWatermarkField())
                .addField("dataStoreName", a.getDataStoreName())
                .addField("description", a.getDescription())
                .addField("status", a.getStatus())
                .addField("isRequired", !a.isNillable())
                .addField("isCalculated", a.isCalculated())
                .addField("isUnique", a.isUnique())
                .addField("isSystem", a.isSystem())
                .addField("picklistValues", a.getPicklistValues())
                .addField("referenceTo", a.getReferenceTo())
                .addField("referenceTargetField", a.getReferenceTargetField())
                .addField("references", new ArrayList<>())
                .addField("lastUpdated", a.getUpdatedAt())
                .addField("updatedBy", userName)
                .addField("tags", tagService.getTagNames(Taggable.attribute, a.getId()))
                .addField("isSyncariDefined", a.isSyncariDefined())
                .addField("parentAttributeId", a.getParentAttributeId())
                .addField("tokenCode", token)
                .addField("isReadonly", !a.isUpdatable());

        if(!isSyncariField){
            entity.addField("isCreateonly", a.isCreateOnly());
        }

        return entity;
    }

    private String getUserName(Map<String, User> usersById, String id) {
        User user = usersById.get(id);
        return user == null ? id : user.getName();
    }

    private void validateAttribute(AttributeDef attribute, String entityDefId) {
        validateCondition(!textUtil.isValidApiName(attribute.getApiName()), I18n.i18n("attribute_invalid_apiName"));
        validateCondition(StringUtils.isBlank(attribute.getDisplayName()), I18n.i18n("attribute_invalid_displayName"));
        validateCondition(StringUtils.isBlank(attribute.getDataType()), I18n.i18n("attribute_invalid_dataType"));

        //validate if the reference field is part of the chosen reference entity
        if (attribute.isReference() && StringUtils.isNotEmpty(attribute.getDataType()) && !attribute.getDataType().equalsIgnoreCase("string")) {
            EntityDefinition refEntity = schemaService.getEntity(schemaService.getEntity(entityDefId).getConnectorId(), attribute.getReferenceTo());
            boolean isInvalidAttribute = refEntity.getAttributes().stream().noneMatch(attributeDefinition -> attributeDefinition.getApiName().equals(attribute.getReferenceTargetField()));
            validateCondition(isInvalidAttribute, I18n.i18n("reference_attribute_invalid"));
        }

        if (attribute.isIdField()) {
            EntityDefinition entityDefinition = schemaService.getEntity(entityDefId);
            entityDefinition.getIdField().ifPresentOrElse(attributeDefinition -> {
                validateCondition(!attributeDefinition.getApiName().equalsIgnoreCase(attribute.getApiName()), I18n.i18n("entity_no_multiple_id_field"));
            }, () -> {
                validateCondition(!attribute.isUnique(), I18n.i18n("id_field_unique_readonly"));
                //validateCondition(!attribute.isReadOnly(), I18n.i18n("id_field_unique_readonly"));
            });
        }
    }
}
