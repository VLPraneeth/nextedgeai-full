package com.syncari.core.service;

import com.syncari.connector.Capability;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.core.DataTransformer;
import com.syncari.core.SyncariContext;
import com.syncari.core.abac.AbacContext;
import com.syncari.core.abac.AbacService;
import com.syncari.core.config.AppConfig;
import com.syncari.core.datatype.*;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.event.Publisher;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.InsightsProviderIntegrator;
import com.syncari.core.model.*;
import com.syncari.core.model.abac.Permission;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.misc.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.repositories.DraftableRepo;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.schema.*;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.utils.I18n;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;
import static java.util.Comparator.comparing;

@Slf4j
@Component
public class SchemaService {
    private static final int MAX_WAIT_TRIES = 300;
    //Syncari ids are 24 char
    private static final int SYNCARI_ID_FIELD_LENGTH = 24;
    private static final int SCHEMA_REFRESH_LOCK_TIMEOUT_MINUTES = 5;
    @Autowired
    private FunctionService functionService;
    @Autowired
    DatastoreService datastoreService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    ConnectorMetadataService connectorMetadataService;
    @Autowired
    DataServiceFactory factory;
    @Autowired
    DataTransformer transformer;
    @Autowired
    LockRepo lockRepo;
    @Autowired
    TagRepo tagRepo;
    @Autowired
    MappingGraphService mappingGraphService;
    @Autowired
    EntityRepo entityRepo;

    @Autowired
    SchemaMappingRepo schemaMappingRepo;

    @Autowired
    EdgeRepo edgeRepo;

    @Autowired
    UnresolvedReferenceService unresolvedReferenceService;

    @Autowired
    NotificationService notificationService;
    @Autowired
    UserService userService;
    @Autowired
    TagService tagService;
    @Autowired
    TextUtil textUtil;
    @Autowired
    ActionService actionService;

    @Autowired
    ResyncService resyncService;

    @Autowired
    DataTransformer dataTransformer;

    @Autowired
    ErrorNotificationService errorNotificationService;

    @Autowired
    private ConnectorMetadataService metaService;

    @Autowired
    AttributeRepo attributeProxyRepo;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;
    
    @Autowired
    IdMappingRepo idMappingRepo;

    @Autowired
    ComponentDependencyService componentDependencyService;

    @Autowired
    DatasetSchemaService datasetSchemaService;

    @Autowired
    InsightsProviderIntegrator insightsProviderIntegrator;

    @Autowired
    DatasetService datasetService;
    
    @Autowired
    private AbacService abac;
    
    @Autowired
    private Publisher publisher;

    @Autowired
    @Qualifier("defaultEmailService")
    private EmailService emailService;

    @Autowired
    private AppConfig appConfig;

    public Optional<AttributeDefinition> getActiveAttribute(String attributeId) {
        return attributeProxyRepo.findById(attributeId).stream().filter(a -> a.isActive()).findFirst();
    }

    public List<AttributeDefinition> getActiveAttributes(String connectorId, String entityName) {
        Optional<EntityDefinition> entityByName = getEntityByName(connectorId, entityName);
        return entityByName.map(e -> e.getActiveAttributes()).orElse(List.of());
    }

    public List<AttributeDefinition> getAttributes(List<String> attributeIds) {
        List<AttributeDefinition> attributes = new ArrayList<>();
        attributeProxyRepo.findAllById(attributeIds).forEach(attributeDefinition -> attributes.add(attributeDefinition));
        return attributes;
    }

    public List<EntityDefinition> getEntities(String connectorId) {
        return getEntities(connectorId, true);
    }

    public List<EntityDefinition> getEntities(String connectorId, boolean withAttributes) {
        List<EntityDefinition> entities = entityProxyRepo.findByConnectorId(connectorId).stream()
                .filter(EntityDefinition::isApproved).collect(Collectors.toList());
        entities = (List<EntityDefinition>) abac.check(new AbacContext().withResourceType(ResourceType.ENTITY).withAction(Permission.READ), entities);
        if(withAttributes){
            populateAttributess(entities);
        }
        return entities;
    }

    public List<EntityDefinition> getActiveApprovedEntities(String connectorId, boolean withAttributes) {
        List<EntityDefinition> entities = entityProxyRepo.findActiveEntitiesByConnectorIds(Set.of(connectorId));
        if(withAttributes){
            populateAttributess(entities);
        }
        return entities;
    }

    private void populateAttributess(List<EntityDefinition> entities) {
        Map<String, List<AttributeDefinition>> entityToAttrMap = getEntityIdToAttribMap(entities);
        entities.forEach(e -> {
            e.setAttributes(entityToAttrMap.get(e.getId()));
        });
    }

    public List<EntityDefinition> getEntities(Set<String> entityDefIds, boolean withAttributes) {
        Iterable<EntityDefinition> entities = entityProxyRepo.findAllById(entityDefIds);
        List<EntityDefinition> entityDefinitions = IterableUtils.toList(entities);
        if(withAttributes){
            populateAttributess(entityDefinitions);
        }
        return entityDefinitions;
    }

    public List<EntityDefinition> getAllEntities(String connectorId) {
        List<EntityDefinition> entities = entityProxyRepo.findAllByConnectorId(connectorId);
        populateAttributess(entities);
        return entities;
    }

    private List<AttributeDef> getFieldsFromEntity(EntityDefinition entity){
        List<AttributeDef> attrs = new ArrayList<>();
        for (AttributeDefinition attr: entity.getAttributes()){
            AttributeDef newAttribute = new AttributeDef();
            newAttribute.setDisplayName(attr.getDisplayName());
            newAttribute.setApiName(attr.getApiName());
            newAttribute.setDataStoreName(attr.getDataStoreName());
            newAttribute.setDescription(attr.getDescription());
            newAttribute.setDataType(attr.getDataType().getName());
            newAttribute.setMultiValueField(attr.isMultiValueField());
            newAttribute.setIdField(attr.isIdField());
            newAttribute.setWatermarkField(attr.isWatermarkField());
            if (attr.isIdField() || attr.isWatermarkField())
                newAttribute.setRequired(true);
            if (attr.isIdField())
                newAttribute.setUnique(true);
            if (attr.isWatermarkField())
                newAttribute.setReadOnly(true);
            newAttribute.setSystem(attr.isSystem());
            newAttribute.setReferenceTo(attr.getReferenceTo());
            newAttribute.setReferenceTargetField(attr.getReferenceTargetField());
            Set<String> tags = new HashSet<>();
            for (Tag t : attr.getTags()) {
                tags.add(t.getName());
            }
            newAttribute.setTags(tags);
            attrs.add(newAttribute);
            }
        return attrs;
    }

    public EntityDef copyFields(String sourceEntityDefinitionId, EntityDef entityDef) {
        EntityDefinition entityInfo = getEntity(sourceEntityDefinitionId);
        List<AttributeDef> attributes = getFieldsFromEntity(entityInfo);
        entityDef.setFields(attributes);

        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition entity = dataTransformer.toEntityDefinition(entityDef);
        entity.setConnectorId(syncariConnector.getId());
        entity.setConnectorTypeId(syncariConnector.getMetadataId());
        EntityDefinition saved = createDraftEntity(entity, true);
        return dataTransformer.toEntityDef(saved);
    }


    public List<EntityDefinition> getEntitiesByDraftStatus(String connectorId, DraftStatus draftStatus, boolean includeAttributes,
                                                           String entityId, int limit) {
        // get entities
        // Do we cache this? There is a limit and usage limited to API

        List<EntityDefinition> entities = entityProxyRepo.findByConnectorIdAndDraftStatus(connectorId, draftStatus.name(), entityId, limit);
        entities = (List<EntityDefinition>) abac.check(new AbacContext().withResourceType(ResourceType.ENTITY).withAction(Permission.READ), entities);

        // get tags
        Map<String, EntityDefinition> entityIdToDef = new HashMap<>();
        entities.stream().forEach(e -> {entityIdToDef.put(e.getId(), e);});
        List<Tag> entityTags = tagRepo.findByTaggableAndTaggedIdIn(Taggable.entity, entityIdToDef.keySet());
        entityTags.stream().forEach(t -> {
            entityIdToDef.get(t.getTaggedId()).getTags().add(new Tag(t.getName(), t.getValue(), t.getTaggable(), t.getTaggedId()));
        });

        // get fields
        if (includeAttributes) {
            populateAttributess(entities);
            for(EntityDefinition entity : entities) {
                List<AttributeDefinition> attributes = entity.getAttributes();
                Map<String, AttributeDefinition> fieldIdToDef = new HashMap<>();
                attributes.stream().forEach(e -> {fieldIdToDef.put(e.getId(), e);});
                List<Tag> fieldTags = tagRepo.findByTaggableAndTaggedIdIn(Taggable.attribute, fieldIdToDef.keySet());
                fieldTags.stream().forEach(t -> {
                    fieldIdToDef.get(t.getTaggedId()).getTags().add(new Tag(t.getName(), t.getValue(), t.getTaggable(), t.getTaggedId()));
                });
            }
        }
        return entities;
    }

    public List<AttributeDefinition> getAttributesByEntityId(String entityId) {
        // get attributes
        List<AttributeDefinition> attributes = attributeProxyRepo.findActiveByEntityId(entityId);

        // get tags
        Map<String, AttributeDefinition> fieldIdToDef = new HashMap<>();
        attributes.stream().forEach(e -> {fieldIdToDef.put(e.getId(), e);});
        List<Tag> fieldTags = tagRepo.findByTaggableAndTaggedIdIn(Taggable.attribute, fieldIdToDef.keySet());
        fieldTags.stream().forEach(t -> {
            fieldIdToDef.get(t.getTaggedId()).getTags().add(new Tag(t.getName(), t.getValue(), t.getTaggable(), t.getTaggedId()));       //.add(t.getName());
        });

        return attributes;
    }

    public List<EntityDefinition> getAllPublishedEntities(String connectorId) {
        return getAllEntities(connectorId).stream().filter(x -> x.isApproved()).collect(Collectors.toList());
    }

    public EntityDefinition getEntity(String connectorId, String apiName) {
        return findEntity(connectorId, apiName).orElseThrow(() -> new RuntimeException(format("Entity with name %s not found", apiName)));
    }

    public Optional<EntityDefinition> findEntity(String connectorId, String apiName) {
        return entityProxyRepo.findAllByConnectorId(connectorId).stream().filter(e -> e.isApproved() && e.getApiName().equals(apiName)).findFirst()
                .map(entity-> entity.setAttributes(attributeProxyRepo.findActiveByEntityId(entity.getId())));
    }

    public EntityDefinition getDraftEntity(String connectorId, String apiName) {
        return getDraft(connectorId, apiName).orElseThrow(() -> new RuntimeException(format("Entity with name %s not found in Draft status", apiName)));
    }


    public Optional<EntityDefinition> getDraft(String connectorId, String apiName) {
        Optional<EntityDefinition> entity = entityProxyRepo.findEntities(connectorId, apiName).stream().filter(EntityDefinition::isDraft).findFirst();
        entity.ifPresent(e -> {
            e.setAttributes(attributeProxyRepo.findActiveByEntityId(e.getId()));
        });
        return entity;
    }

    public Optional<EntityDefinition> getSyncariEntityByName(String apiName) {
        String syncariConnectorId = connectorService.getSyncariConnector().getId();
        return (Optional<EntityDefinition>) abac.check(
            new AbacContext().withResourceType(ResourceType.ENTITY).withAction(Permission.READ),
            findEntity(syncariConnectorId, apiName).map(
                entity -> entity.setAttributes(attributeProxyRepo.findByEntityId(entity.getId()))));
    }

    public Optional<EntityDefinition> getSyncariEntityById(String entityId) {
        String syncariConnectorId = connectorService.getSyncariConnector().getId();
        return  entityProxyRepo.findById(entityId)
                .map(entity -> entity.setAttributes(attributeProxyRepo.findByEntityId(entity.getId())));
    }

    public Optional<EntityDefinition> getEntityByName(String connectorId, String apiName) {
        return findEntity(connectorId, apiName)
                .map(entity -> entity.setAttributes(attributeProxyRepo.findActiveByEntityId(entity.getId())));
    }

    public List<EntityDefinition> getEntityVersionsByName(String connectorId, String apiName) {
        List<EntityDefinition> entities = entityProxyRepo.findEntityVersions(connectorId, apiName);
        entities.stream().forEach(e -> {
            e.setAttributes(attributeProxyRepo.findActiveByEntityId(e.getId()));
        });
        return entities;
    }


    public EntityDefinition getEntity(String entityDefinitionId) {
        return getEntity(entityDefinitionId, true);
    }

    public EntityDefinition getEntity(String entityDefinitionId, boolean includeAttributes) {
        EntityDefinition entity = entityProxyRepo.findById(entityDefinitionId).orElseThrow(() -> new RuntimeException(format("Entity with id %s not found", entityDefinitionId)));
        entity = (EntityDefinition) abac.check(
            new AbacContext().withResourceType(ResourceType.ENTITY).withAction(Permission.READ)
                .withThrowException(true).withThrowExceptionMessage(i18n("abac_permission_error")),
            entity);
        if (includeAttributes) {
            entity.setAttributes(attributeProxyRepo.findActiveByEntityId(entity.getId()));
            List<AttributeDefinition> attributes = entity.getAttributes();
            Map<String, AttributeDefinition> fieldIdToDef = new HashMap<>();
            attributes.stream().forEach(e -> {
                fieldIdToDef.put(e.getId(), e);
            });
            List<Tag> fieldTags = tagRepo.findByTaggableAndTaggedIdIn(Taggable.attribute, fieldIdToDef.keySet());
            fieldTags.stream().forEach(t -> {
                fieldIdToDef.get(t.getTaggedId()).getTags().add(new Tag(t.getName(), t.getValue(), t.getTaggable(), t.getTaggedId()));
            });
        } else {
            entity.setAttributes(new ArrayList<>());
        }
        return entity;
    }

    public Optional<EntityDefinition> findEntity(String entityDefinitionId) {
        return entityProxyRepo.findById(entityDefinitionId).map(entity ->
                entity.setAttributes(attributeProxyRepo.findActiveByEntityId(entity.getId()))
        );
    }


    public List<AttributeDefinition> getUnmappedAttributesFor(String synapseEntityId) {
        List<AttributeDefinition> unmapped = new ArrayList<>();
        EntityDefinition synapseEntity = getEntity(synapseEntityId);
        List<String> syanpseAttrIds = synapseEntity.getAttributes().stream().map(a -> a.getId())
                .collect(Collectors.toList());
        Set<String> attributesWithExistingGraphs = mappingGraphService
                .findAttributeGraphsWithSourceOrSink(syanpseAttrIds);
        for (AttributeDefinition attr : synapseEntity.getAttributes()) {
            if (!attributesWithExistingGraphs.contains(attr.getId())) {
                unmapped.add(attr);
            }
        }
        return unmapped;
    }

    public List<AttributeDefinition> getUnmappedAttributesForSink(String synapseEntityId, boolean readyOnly) {
        List<AttributeDefinition> unmapped = new ArrayList<>();
        EntityDefinition synapseEntity = getEntity(synapseEntityId);
        List<String> syanpseAttrIds = synapseEntity.getAttributes().stream().map(a -> a.getId())
                .collect(Collectors.toList());
        Set<String> attributesWithExistingGraphs = mappingGraphService
                .findAttributeGraphsWithSink(syanpseAttrIds, readyOnly);
        for (AttributeDefinition attr : synapseEntity.getAttributes()) {
            if (!attributesWithExistingGraphs.contains(attr.getId())) {
                unmapped.add(attr);
            }
        }
        return unmapped;
    }

    /**
     * Approve the draft version of syncari entity
     * @param draft
     */
    public void approveDraftEntity(EntityDefinition draft){
        validateCondition(!draft.isDraft(), i18n("entity_approve_failed_no_draft"), draft.getApiName());
        validateCondition(draft.getAttributes().isEmpty(), i18n("entity_approve_no_attribute"));
        log.info("Approving syncari entity {} draft", draft.getApiName());
        var entityDraftService = getDraftService(draft);
        Connector c = connectorService.find(draft.getConnectorId()).orElseThrow(() -> new SyncariValidationException(
                format(i18n("not_found"), "Connector", "Id", draft.getConnectorId())));

        ConnectorMetadata cm = connectorMetadataService.findById(c.getMetadataId()).orElseThrow(() -> new SyncariValidationException(
                format(i18n("not_found"), "ConnectorMetadata", "Id", c.getMetadataId())));

        // Always validate that entity does not have multiple watermark fields
        if(!c.isSyncariConnector()) {
            draft.validateMultipleWatermarks();
        }

        // Validate watermark requirements only for connectors that require watermarks
        if(!c.isSyncariConnector() && !connectorService.supportsNoWatermark(draft.getConnectorId())) {
            draft.validateWatermark();
        }
        abac.check(new AbacContext()
            .withResourceType(ResourceType.ENTITY)
            .withAction(Permission.APPROVE_DRAFT)
            .withThrowException(true)
            .withThrowExceptionMessage(i18n("abac_permission_error")), draft);
        var approved = entityDraftService.approveDraft(draft);

        // approve all attribute draft
        approveDraftAttributeList(draft.getAttributes(),approved.getId(), c.isSyncariConnector());

        /*draft.getAttributes().forEach(a -> {
            approveDraftAttribute(a, approved.getId(), c.isSyncariConnector());
        });*/


        // delete fields not in draft but in approved.
        var approvedEntity = this.getEntity(approved.getId());
        if (CollectionUtils.isNotEmpty(approvedEntity.getAttributes()) &&  CollectionUtils.isNotEmpty(draft.getAttributes())){
            List<String> listOfApprovedAttribs = approvedEntity.getAttributes().stream().map(x -> x.getApiName()).collect(Collectors.toList());
            List<String> listOfDraftAttribs = draft.getAttributes().stream().map(x -> x.getApiName()).collect(Collectors.toList());
            listOfApprovedAttribs.removeAll(listOfDraftAttribs);

            List<AttributeDefinition> attributesToBeDeleted = approvedEntity.getAttributes().stream().filter(x -> listOfApprovedAttribs.contains(x.getApiName())).collect(Collectors.toList());
            attributesToBeDeleted.forEach( a -> this.deleteField(approvedEntity.getId(), a.getId()));
        }

        // associate draft tags with approved entity if it has different entity
        if(!approved.getId().equals(draft.getId())){
            List<Tag> draftTags = tagService.findTagsFor(Taggable.entity, draft.getId());
            tagService.updateTagsFor(approved.getId(), Taggable.entity, draftTags);

            // remove tags of draft entity and its attributes
            tagService.removeTagsFor(Taggable.entity, draft.getId());
        }
    }

    public void approveDraftAttributeList(List <AttributeDefinition> draftAttributes, String approvedEntityId, boolean isSyncariAttributes){

        Map<String, AttributeDefinition> attributeIdMap = draftAttributes.stream().collect(Collectors.toMap(AttributeDefinition :: getId, Function.identity()));
        Map<String, AttributeDefinition> attributeParentIdMap = draftAttributes.stream().filter(a -> (a.getParentId() != null)).collect(Collectors.toMap(AttributeDefinition :: getParentId, Function.identity()));
        Map<String, List<Tag>> attributeIdTagMap = draftAttributes.stream().collect(Collectors.toMap(t -> t.getApiName(), p -> p.getTags()));

        // IMPORTANT: Capture field type changes BEFORE approval process modifies the data types
        List<FieldTypeChange> fieldTypeChanges = new ArrayList<>();
        if (isSyncariAttributes) {
            fieldTypeChanges = captureFieldTypeChanges(draftAttributes, approvedEntityId);
        }

        List<AttributeDefinition> dummyApprovedList = new ArrayList<>();
        for (AttributeDefinition att : attributeIdMap.values()){
            validateCondition(!att.isDraft(), i18n("attribute_approve_failed_no_draft"), att.getApiName());
            // assign entityId to new fields in draft, existing attributes will retain their entityId

            if(att.getParentId() == null){
                att.setEntityId(approvedEntityId);
                attributeProxyRepo.save(att);
            }
            var attribDraftService = getDraftService(att);
            AttributeDefinition dummyApproved = (AttributeDefinition) attribDraftService.approveDummyDraft(att, DraftStatus.APPROVED);
            dummyApprovedList.add(dummyApproved);
        }

        List<AttributeDefinition> approvedList = attributeProxyRepo.saveAll(dummyApprovedList);
        List<String> approvedIds = approvedList.stream().map(a -> a.getId()).collect(Collectors.toList());
        Map<String, Optional<MappingGraph>> draftGraphsMap = mappingGraphService.retrieveDraftAttributeGraphs(approvedIds);
        Map<String, Optional<MappingGraph>> approvedGraphsMap = mappingGraphService.retrieveApprovedAttributeGraphs(approvedIds);

        approvedList.forEach(approved -> {
            String attribDefId = approved.getId();
            AttributeDefinition draftOnes = attributeParentIdMap.get(attribDefId);
            AttributeDefinition prevApproved = null;
            if ((null != draftOnes) && (draftOnes.getParentId() != null)){
                prevApproved = attributeProxyRepo.findById(draftOnes.getParentId()).orElse(null);
            }
            // Update all graphs associated with syncari attribute
            if(isSyncariAttributes) {
                mappingGraphService.updateSyncariAttributeChangeForGivenGraph(draftGraphsMap.getOrDefault(attribDefId, Optional.empty()),approved);
                mappingGraphService.updateSyncariAttributeChangeForGivenGraph(approvedGraphsMap.getOrDefault(attribDefId, Optional.empty()),approved);
            } else  if(prevApproved != null && !isSameDataType(prevApproved.getDataType(), approved.getDataType())) {
                mappingGraphService.updateGraphOnSynapseAttributeChange(approved);
            }

            // associate draft tags with approved entity if it has different entity
            if((null == draftOnes) || (!approved.getId().equals(draftOnes.getId()))){
                List<Tag> draftTags = attributeIdTagMap.getOrDefault(approved.getApiName(), List.of());
                tagService.updateTagsFor(approved.getId(), Taggable.attribute, draftTags);

                // remove tags of draft entity and its attributes
                if (null != draftOnes){
                    tagService.removeTagsFor(Taggable.attribute, draftOnes.getId());
                }
            }
        });
        
        // Field type migration: Trigger async migration AFTER successful approval for Syncari entities
        if (isSyncariAttributes && !fieldTypeChanges.isEmpty()) {
            triggerFieldTypeMigrations(fieldTypeChanges);
        }
    }
    public boolean isSameDataType(Datatype prev, Datatype current) {
    	if(prev == null && current == null) {
    		return true;
    	} else if(prev == null && current != null) {
    		return false;
    	} else if(prev != null && current == null) {
    		return false;
    	}
    	return prev.getName().equals(current.getName());
    }

    public void upsertExternalAttributes(String connectorType, String connectorName, String externalEntityDefId, String syncariEntityApiName) {
        // add to both draft and approved (if present)
        EntityDefinition externalEntity = getEntity(externalEntityDefId);
        Optional<AttributeDefinition> idField = externalEntity.getIdField();
        idField.ifPresent(id -> {
            getSyncariEntityByName(syncariEntityApiName).ifPresent(syncariEntity -> {
                createExtField(externalEntity, syncariEntity, connectorName, connectorType, id);
            });
            String syncariConnectorId = connectorService.getSyncariConnector().getId();
            getDraft(syncariConnectorId, syncariEntityApiName).ifPresent(syncariEntity -> {
                createExtField(externalEntity, syncariEntity, connectorName, connectorType, id);
            });
        });
    }

    public String getSyncarizedExternalFieldApiName(String connectorType, String connectorName, String extEntityName, String fieldName) {
        return toApiName(sanitizedFieldName("syncari_"+connectorType+"_"+connectorName+"_"+extEntityName+"_"+fieldName));
    }

    public void createExtField(EntityDefinition extEntity, EntityDefinition syncariEntity, String connectorName,
                                String connectorType, AttributeDefinition fromField) {
        String apiName = getSyncarizedExternalFieldApiName(connectorType, connectorName, extEntity.getApiName(), fromField.getApiName());
        if(syncariEntity.hasField(apiName)) {
            log.warn("Ext Field {} already exists on {} entity {}, validating if field is correct or not", apiName, syncariEntity.getDraftStatus().name(), syncariEntity.getApiName());
            Optional<AttributeDefinition> externalField = syncariEntity.getField(apiName);
            externalField.ifPresent(f -> {
                if ((null != f.getReferenceTo()) && (null != f.getReferenceTargetField()) && f.getReferenceTo().equals(fromField.getEntityId()) && f.getReferenceTargetField().equals(fromField.getId())){
                    log.info("Existing external field {} on entity {} is correct with reference entity id {} ", apiName, syncariEntity.getApiName(), fromField.getEntityId());
                }else{
                    // fix the reference attribute.
                    log.info("Fixing external field {} on entity {} with reference entity id {} and fieldid {}", apiName, syncariEntity.getApiName(), fromField.getEntityId(),fromField.getId());
                    f.markExternal(fromField);
                    attributeProxyRepo.save(f);
                }
            });
            return;
        }
        String email = (SyncariContext.getUser() == null ? User.SYSTEM_USER_PREFIX : SyncariContext.getUser().getEmail());
        String id = (SyncariContext.getUser() == null ? null : SyncariContext.getUser().getId());
        AttributeDefinition newIdField = new AttributeDefinition();
        newIdField.setApiName(apiName);
        newIdField.setDisplayName("Syncari "+replaceSpace(connectorName)+" "+ replaceSpace(extEntity.getDisplayName()) +" " +replaceSpace(fromField.getApiName()));
        newIdField.setDescription(String.format("Field added to Entity by user %s when entity %s from synapse %s was mapped",
                email, extEntity.getApiName(), connectorName));
        // mark External or Reference check is inside each method
        newIdField.markExternal(fromField);
        newIdField.markReference(fromField);
        newIdField.setStatus(Status.ACTIVE);
        newIdField.setDataStoreName(apiName);
        newIdField.setCreatedAt(new Date());
        newIdField.setCreatedBy(id);
        newIdField.setEntityId(syncariEntity.getId());
        newIdField = attributeProxyRepo.save(newIdField);
        syncariEntity.addField(newIdField);
        entityProxyRepo.save(syncariEntity);
        log.info("Ext Field {} added successfully to {} entity {} when source entity {} from synapse {} was mapped",
                newIdField.getApiName(), syncariEntity.getDraftStatus().name(), syncariEntity.getApiName(), extEntity.getApiName(), connectorName);
    }
    
	public void deleteExtFields(Connector connector) {
		List<EntityDefinition> entities = getAllPublishedEntities(connector.getId());
		List<String> entityIds = entities.stream().map(e -> e.getId()).collect(Collectors.toList());
		if (entityIds != null && !entityIds.isEmpty()) {
			List<AttributeDefinition> attributes = attributeProxyRepo.findExternalId(entityIds);
			log.info("Following attributes {} with ids {} will be deleted.",
					attributes.stream().map(x -> x.getApiName()).collect(Collectors.toList()),
					attributes.stream().map(x -> x.getId()).collect(Collectors.toList()));
			attributes.forEach(attr -> {
				deleteField(attr.getEntityId(), attr.getId());
			});
			//remove external id from actual entity
			var externalIds = attributes.stream().map(attr -> attr.getApiName()).collect(Collectors.toSet());
			entities.forEach(entity -> {
				entityRepo.removeExternalIdFields(entity.getApiName(), externalIds);
			});
			//remove id mappings
			idMappingRepo.removeExternalIdRef(connector.getId());
		}

	}

    private String replaceSpace(String value) {
        return value.replace(" ", "_");
    }

    /**
     * Discard the draft version of syncari entity
     * @param toDiscard
     */
    public void discardDraftEntity(EntityDefinition toDiscard){
        validateCondition(!toDiscard.isDraft(), i18n("entity_discard_failed_no_draft"), toDiscard.getApiName());
        log.info("Discarding syncari entity {} draft", toDiscard.getApiName());
        abac.check(
            new AbacContext()
            .withResourceType(ResourceType.ENTITY)
            .withAction(Permission.DELETE_DRAFT)
            .withThrowException(true)
            .withThrowExceptionMessage(i18n("abac_permission_error")),
            toDiscard);
        var entityDraftService = getDraftService(toDiscard);
        toDiscard.getAttributes().forEach(a -> {
            var attribDraftService = getDraftService(a);
            attribDraftService.discardDraft(a);
        });

        entityDraftService.discardDraft(toDiscard);
        // delete tags associated with discarded draft
        tagService.removeTagsFor(Taggable.entity, toDiscard.getId());
    }

    /**
     * Discard the draft version of syncari attribute
     * @param toDiscard
     */
    public void discardDraftAttribute(String entityId, AttributeDefinition toDiscard){
        EntityDefinition draftEntity = getEntity(entityId);
        validateCondition(!toDiscard.isDraft(), i18n("attribute_discard_failed_no_draft"), toDiscard.getApiName());
        validateCondition(!draftEntity.hasField(toDiscard.getApiName()),
                i18n("attribute_not_in_entity"), toDiscard.getApiName(), draftEntity.getApiName());
        // check if this is the only id field in entity
        var idField = draftEntity.getAttributes().stream().filter(a -> a.isIdField() && !a.getId().equals(toDiscard.getId())).findAny();
        validateCondition(toDiscard.isIdField() && idField.isEmpty(), i18n("id_field_delete", toDiscard.getDisplayName()));

        // check if this is the only wm field in entity
        var wmField = draftEntity.getAttributes().stream().filter(a -> a.isWatermarkField() && !a.getId().equals(toDiscard.getId())).findAny();
        validateCondition(toDiscard.isWatermarkField() && wmField.isEmpty(), i18n("wm_field_delete", toDiscard.getDisplayName()));

        log.info("Discarding attribute {} draft from syncari entity {}", toDiscard.getApiName(), draftEntity.getApiName());
        var draftService = getDraftService(toDiscard);

        draftService.discardDraft(toDiscard);
        // delete tags associated with discarded draft
        tagService.removeTagsFor(Taggable.attribute, toDiscard.getId());
    }

    private DraftService getDraftService(DraftableModel draft){
        if (draft instanceof EntityDefinition) {
            return new DraftService<EntityDefinition>() {
                @Override
                protected DraftableRepo<EntityDefinition> getDraftableRepo() {
                    return entityProxyRepo;
                }

                @Override
                protected void processArchived(EntityDefinition archived) {
                    archived.setApiName(format("%s_%s_%s", archived.getApiName(), archived.getId(), DELETED));
                    archived.setDraftStatus(DraftStatus.ARCHIVED);
                }
            };
        }else{
            return new DraftService<AttributeDefinition>() {
                @Override
                protected DraftableRepo<AttributeDefinition> getDraftableRepo() {
                    return attributeProxyRepo;
                }

                @Override
                protected void processArchived(AttributeDefinition archived) {
                    archived.setApiName(format("%s_%s_%s", archived.getApiName(), archived.getId(), DELETED));
                }
            };
        }
    }

    /**
     * Retrieves all the mandatory fields need to be mapped in an entity
     */
    public List<AttributeDefinition> getMandatoryMappingFieldsFor(String synapseEntityId){
        EntityDefinition synapseEntity = getEntity(synapseEntityId);
        return synapseEntity.getAttributes().stream()
                .filter(a -> isMandatoryMappingField(a))
                .collect(Collectors.toList());
    }

    /**
     * Returns true if the mapping for an entity is mandatory
     */
    public boolean isMandatoryMappingField(AttributeDefinition a){
        return !a.isNillable() && !a.isIdField() && !a.isSystem() && a.getStatus().equals(Status.ACTIVE)
                && a.isUpdatable() && !a.hasDefaultValue();
    }

    public Optional<AttributeDefinition> findAttribute(String attributeDefinitionId) {
        return attributeProxyRepo.findById(attributeDefinitionId);
    }

    public AttributeDefinition getAttribute(String attributeDefinitionId) {
        return attributeProxyRepo.findById(attributeDefinitionId).orElseThrow(
                () -> new RuntimeException(format("Attribute with id %s not found", attributeDefinitionId)));
    }

    public AttributeDefinition getAttributeByName(String entityDefId, String apiName) {
        return attributeProxyRepo.findByEntityIdAndApiName(entityDefId, apiName).orElseThrow(
                () -> new RuntimeException(format("Attribute with apiName %s not found", apiName)));
    }

    public EntityDefinition createGraphFor(EntityDefinition syncariEntity, EntityDefinition synapseEntity) {
        if (syncariEntity == null) {
            syncariEntity = createEntityLike(synapseEntity, List.of());
        }
        mappingGraphService.initializeEntityGraph(syncariEntity, synapseEntity);
        return syncariEntity;
    }

    public EntityDefinition createGraphForCreateEntity(EntityDefinition syncariEntity, EntityDefinition synapseEntity) {
        if (syncariEntity == null) {
            syncariEntity = createEntityLike(synapseEntity, List.of());
        }
        mappingGraphService.initializeEntityGraphWithoutDefaultMapping(syncariEntity, synapseEntity);
        return syncariEntity;
    }

    public AttributeDefinition createGraphFor(AttributeDefinition syncariField, Optional<String> referenceEntityId, AttributeDefinition synapseField,
                                              EntityDefinition syncariEntity) {
        EntityDefinition synapseEntity = getEntity(synapseField.getEntityId());
        if (syncariField == null) {
            syncariField = addAttributeToEntity(synapseField, referenceEntityId, syncariEntity, true);
        }

        Map<String, AttributeDefinition> syncariNameToDef = createNameToDefMap(syncariEntity);
        Map<String, AttributeDefinition> synapseNameToDef = createNameToDefMap(synapseEntity);
        mappingGraphService.initializeAttrGraph(syncariEntity, synapseEntity, synapseField, syncariNameToDef,
                synapseNameToDef, Optional.empty());
        return syncariField;
    }

    public List<EntityDefinition> refreshSynapseSchema(String connectorId) {
        return refreshSynapseSchema(connectorId, null, connectorId);
    }

    public void resetDataStoreName(EntityDefinition entity) {
        EntityDefinition e = entityProxyRepo.findById(entity.getId()).get();
        e.resetDataStoreName(entity.getDataStoreName());
        entityProxyRepo.save(e);
    }

    public void resetDataStoreName(AttributeDefinition attr) {
        AttributeDefinition e = attributeProxyRepo.findById(attr.getId()).get();
        e.resetDataStoreName(attr.getDataStoreName());
        attributeProxyRepo.save(e);
    }

    public void instantiateFromSyncari(Connector c) {
        log.info("Calling instantiateFromSyncari for {}", c.getName());
        Schema syncariSchema = getSyncariSchema();
        MetadataService dataService = factory.getSchemaService(c.getMetadata());
        syncariSchema.getEntities().forEach(e -> {
            // TODO check if entity published
            log.info("Creating entity {}", e.getApiName());
            CreateObjectRequest request = new CreateObjectRequest(transformer.toConnectorInfo(c), transformer.toEntitySchema(e, c));
            dataService.createObject(request);
        });
        refreshSynapseSchema(c.getId());
    }

    /**
     *
     * @param connectorId
     * @param entity
     * @param lockOwnerId - A string that is stable, and reconstructible by client
     * @return
     */
    public List<EntityDefinition> refreshSynapseSchema(String connectorId, EntityDefinition entity, String lockOwnerId) {
        boolean isEntireSchemaRefresh = entity == null;
        Connector c = connectorService.find(connectorId).orElseThrow(() -> new RuntimeException("Connector with Id "+connectorId+" not found"));
        AsyncStatus finalStatus = AsyncStatus.SUCCESS;
        if (c.getStatus() == ConnectorStatus.NEW || c.getStatus() == ConnectorStatus.ERROR) {
            log.error("Skipping Schema refresh for {} as refresh can be done only on active connectors", connectorId);
            return List.of();
        }
        var lockId = entity == null ? c.getId() : entity.getId();
        try {
            var locked = lockRepo.lock(lockId, lockOwnerId, Duration.ofMinutes(SCHEMA_REFRESH_LOCK_TIMEOUT_MINUTES));
            if(isEntireSchemaRefresh && locked.isEmpty() && AsyncStatus.PROCESSING.equals(c.getSchemaRefreshStatus())){
                // This is from async schema refresh and is processing duplicate event from retry
                log.warn("Schema Refresh for connector {} already being processed", c.getName());
                return Collections.emptyList();
            }
            var waitTries = 0;
            while (locked.isEmpty() && waitTries < MAX_WAIT_TRIES) {
                log.info("Connector {} is locked, waiting to get a lock before starting schema sync with retry count {}", connectorId, waitTries);
                Thread.sleep(1000);
                locked = lockRepo.lock(lockId, lockOwnerId, Duration.ofMinutes(SCHEMA_REFRESH_LOCK_TIMEOUT_MINUTES));
                waitTries++;
            }
            if (locked.isEmpty()) {
                log.info("Max tries for lock acquisition exhausted. Forcefully acquiring lock to start schema sync");
                lockRepo.forceLock(lockId, lockOwnerId);
            }
            connectorService.setSchemaStatus(c.getId(), AsyncStatus.PROCESSING);
            return doSchemaRefresh(c, entity);
        } catch (InterruptedException e) {
            log.error("Error while getting lock for connector {} and stack trace of exception is {}", connectorId, ExceptionUtils.getStackTrace(e));
            finalStatus = AsyncStatus.ERROR;
            throw new RuntimeException(e);
        } catch (Exception e) {
            log.error("Error during schema refresh for connector {} and stack trace of exception is {}", connectorId, ExceptionUtils.getStackTrace(e));
            finalStatus = AsyncStatus.ERROR;
            throw e;
        } finally {
            lockRepo.unlock(lockId, lockOwnerId);
            log.debug("Connector {} with lockId {} , ownerId {} is unlocked from schema sync", connectorId, lockId,
                    lockOwnerId);
            connectorService.setSchemaStatus(c.getId(), finalStatus);
        }
    }

    public AttributeDefinition addAttributeToSchema(String connectorId, AttributeDefinition fromAttr, Optional<String> referenceEntityId, EntityDefinition syncariEntity,
                                                    EntityDefinition synapseEntity, Optional<SyncDirection> direction) {
        return addAttributeToSchema(connectorId, fromAttr,syncariEntity,synapseEntity, Optional.empty(), referenceEntityId, direction);
    }

    private AttributeDefinition addAttributeToSchema(String connectorId, AttributeDefinition fromAttr, EntityDefinition syncariEntity,
                                                     EntityDefinition synapseEntity, Optional<AttributeDefinition> syncariAttribute, Optional<String> referenceEntityId,
                                                     Optional<SyncDirection> direction) {
        if(syncariAttribute.isEmpty()) {
            // Create attribute in Syncari if doesnt exist
            addAttributeToEntity(fromAttr, referenceEntityId, syncariEntity, true);
        }
        // Create field in toSynapse schema if doesnt exist
        AttributeDefinition synapseAttr = addAttributeToEntity(fromAttr, Optional.empty(), synapseEntity, false);
        // Create default mapping between fromSynapse <> Syncari
        Map<String, AttributeDefinition> syncariNameToDef = createNameToDefMap(syncariEntity);
        // Create default mapping between Syncari <> toSynapse
        Map<String, AttributeDefinition> synapseNameToDef = createNameToDefMap(synapseEntity);
        // Add the newly created synapse attribute to the map to avoid NPE in initializeAttrGraph
        synapseNameToDef.put(synapseAttr.getApiName().toLowerCase(), synapseAttr);
        mappingGraphService.initializeAttrGraph(syncariEntity, synapseEntity, synapseAttr, syncariNameToDef,
                synapseNameToDef, syncariAttribute, direction);
        return synapseAttr;
    }

    public AttributeDefinition  createAttributeInSynapse(String connectorId, EntityDefinition entity, AttributeDefinition fromAttr) {
        validateCondition(EntityData.SYNCARI_DEFINED_FIELDS.contains(sanitizedFieldName(fromAttr.getApiName())),
                i18n("attribute_with_apiname_is_syncari_defined"), fromAttr.getApiName(), entity.getApiName());
        Connector connector = connectorService.get(connectorId);
        MetadataService dataService = factory.getSchemaService(connector.getMetadata());
        AttributeSchema attrSchema = transformer.toAttrSchema(fromAttr, entity, connector);
        //clear the id!
        attrSchema.setId(null);
        CreateFieldRequest request = new CreateFieldRequest(entity.getApiName(), transformer.toConnectorInfo(connector),
                attrSchema);
        AttributeSchema createdField = dataService.createField(request);
        Optional<AttributeDefinition> existingAttribute = attributeProxyRepo.findByEntityIdAndApiName(entity.getId(), createdField.getApiName());
        var newAttr = existingAttribute.orElseGet(() -> {
            AttributeDefinition temp = transformer.toAttributeDefinition(createdField);
            temp.setEntityId(entity.getId());
            temp.setStatus(Status.ACTIVE);
            temp.setDraftStatus(DraftStatus.APPROVED);
            AttributeDefinition saved = attributeProxyRepo.save(temp);
            entity.addField(saved);
            return saved;
        });
        // The synapse does some transformation on the metadata to make it consumable by
        // endsystem. Save those changes back to AttributeDefinition
        log.info("Field {} created successfully in {}", newAttr.getApiName(), connector.getName());
        return newAttr;
    }

    public void initializeEndSystemSchema(Connector connector) {
        Connector conn = connectorService.find(connector.getId()).get();
        MetadataService dataService = factory.getSchemaService(conn.getMetadata());
        ConnectorInfo connectorInfo = transformer.toConnectorInfo(conn);
        connectorInfo.setRequiredScopes(getAdditionalScopes());
        connectorInfo.setOptionalScopes(getOptionalScopes());
        log.info("Starting Schema refresh for all entities on connector {}", conn.getId());
        DescribeAllRequest request = new DescribeAllRequest(connectorInfo, List.of());
        // NOTE: The initializeEndSystemSchema method is invoked only during the first-time
        // activation when the list of entities is empty. Therefore, setting activeEntities in
        // DescribeAllRequest is unnecessary.
        List<EntityDefinition> allEntities = transformer.toEntityDefinition(dataService.describeAll(request), conn);

        allEntities.stream().forEach(e -> {
            Optional<EntityDefinition> existing = findEntity(conn.getId(), e.getApiName());
            List<AttributeDefinition> attributes = new ArrayList<>();
            e.setStatus(Status.ACTIVE);
            e.setConnectorId(conn.getId());
            e.setConnectorTypeId(conn.getMetadata().getId());
            e.setId(null);
            e.setDraftStatus(DraftStatus.APPROVED);
            // A scenario of concurrent schema initialization should not happen since the connection activation would be locked.
            // However, in such a scenario we would want to skip initializing otherwise the schema initialization never recovers.
            // For any discrepancies post initialization, the refresh schema can be invoked to correct the issue.
            if (existing.isPresent()) {
            	log.info(format("Found existing Initialized entity : %s, with %s attributes for %s", existing.get().getApiName(),
            			existing.get().getAttributes().size(), connector.getName()));
            	return;
            }
            EntityDefinition saved = entityProxyRepo.save(e);
            Set<String> uniqueAttribs = new HashSet<>();
            e.getAttributes().stream().forEach(a -> {
                if(!uniqueAttribs.contains(a.getApiName())) {
                    a.setStatus(Status.ACTIVE);
                    a.setId(null);
                    a.setEntityId(saved.getId());
                    a.setDraftStatus(DraftStatus.APPROVED);
                    uniqueAttribs.add(a.getApiName());
                    attributes.add(a);
                }
            });
            attributeProxyRepo.saveAll(attributes);
            log.info(format("Initialized entity : %s, with %s attributes for %s", saved.getApiName(), attributes.size(),
                    connector.getName()));
        });
    }

    private List<String> getAdditionalScopes() {
        return (SyncariContext.getOrganziation().getOauthConfigs() != null && SyncariContext.getOrganziation().getOauthConfigs().containsKey(Constants.HUBSPOT)) ? SyncariContext.getOrganziation().getOauthConfigs().get(Constants.HUBSPOT).getAdditionalScopes() : List.of();
    }
    private List<String> getOptionalScopes() {
        return (SyncariContext.getOrganziation().getOauthConfigs() != null && SyncariContext.getOrganziation().getOauthConfigs().containsKey(Constants.HUBSPOT)) ? SyncariContext.getOrganziation().getOauthConfigs().get(Constants.HUBSPOT).getOptionalScopes() : List.of();
    }

    private void evictEntityDefinition() {
        // evict entity definition



    }

    public void activateMapping(Connector connector) {
        log.info("Activating initial mappings for connector {}", connector.getName());
        List<EntityDefinition> syncariSchema = getEntities(connectorService.getSyncariConnector().getId());
        List<EntityDefinition> synapseSchema = getEntities(connector.getId());
        Map<String, EntityDefinition> syncariIdToDef = new HashMap<>();
        syncariSchema.forEach(e -> syncariIdToDef.put(e.getApiName(), e));
        Map<String, EntityDefinition> synapseEntityApiNameToDef = new HashMap<>();
        synapseSchema.forEach(e -> synapseEntityApiNameToDef.put(e.getApiName().toLowerCase(), e));

        // Create the default graphs for EP and FP in draft mode
        SynapseInfoService dataService = factory.getSynapseService(connector.getMetadata());
        Map<String, String> defaultMappings = dataService.getEntityMappings();
        log.info("Found {} default mappings for connector {}", defaultMappings.size(), connector.getName());
        for (Entry<String, String> mapping : defaultMappings.entrySet()) {
            EntityDefinition entityDefinition = syncariIdToDef.get(mapping.getKey());
            if (synapseEntityApiNameToDef.containsKey(mapping.getValue().toLowerCase()) && (null != entityDefinition) && entityDefinition.isActive()) {
                log.info("Creating graph for {}", mapping.getValue());
                EntityDefinition synapseEntity = synapseEntityApiNameToDef.get(mapping.getValue().toLowerCase());
                synapseEntity.setConnectorTypeId(connector.getMetadataId());
                createGraphFor(entityDefinition, synapseEntity);
            }
        }
    }

    public Schema getSyncariSchema() {
        return getSyncariSchema(false);
    }

    public Schema getSyncariSchema(boolean activeOnly) {
        return getSyncariSchema(activeOnly, true);
    }

    public Schema getSyncariSchema(boolean activeOnly, boolean details) {
        // TODO the schema needs to be cached instead of recomputing
        String syncariConnectorId = connectorService.getSyncariConnector().getId();
        List<EntityDefinition> defs = activeOnly ? findActiveEntities(syncariConnectorId)
                : findApprovedEntities(syncariConnectorId);
        defs = (List<EntityDefinition>) abac.check(new AbacContext().withResourceType(ResourceType.ENTITY).withAction(Permission.READ), defs);
        return getSchema(defs,details, true);
    }

    private List<EntityDefinition> findActiveEntities(String connectorId) {
      return entityProxyRepo.findByConnectorId(connectorId).stream().filter(e -> e.isActive() && e.isApproved()).collect(Collectors.toList());
    }

    private List<EntityDefinition> findApprovedEntities(String connectorId) {
        return entityProxyRepo.findByConnectorId(connectorId).stream().filter(EntityDefinition::isApproved).collect(Collectors.toList());
    }

    public Schema getSchemaByEntityId(String entityId) {
        return getSchema(List.of(entityProxyRepo.findById(entityId).get()));
    }

    public Schema getSchemaFor(String connectorId, boolean detailed) {
        return getSchema(findActiveEntities(connectorId), detailed, true);
    }

    public Schema getSchemaDetailedWithoutTags(String connectorId) {
        return getSchema(findApprovedEntities(connectorId), true, false);
    }

    public Schema getSchemaFor(String connectorId) {
        return getSchemaFor(connectorId, true);
    }

    public Schema getAllSchemaFor(String connectorId, boolean detailed) {

        long startTime = System.currentTimeMillis();
        String syncariConnectorId = connectorService.getSyncariConnector().getId();
        var entities = entityProxyRepo.findAllByConnectorId(connectorId);
        if(StringUtils.equals(connectorId, syncariConnectorId)) {
          entities = (List<EntityDefinition>) abac.check(new AbacContext()
              .withResourceType(ResourceType.ENTITY).withAction(Permission.READ),
              entities);
        }
        log.debug("Time to retrieve all entities for connector {} {} ms", connectorId, System.currentTimeMillis() - startTime);
        return getSchema(entities, detailed, true);
    }

    public void autoSyncSchemaFor(ConnectorSchemaSetting setting) {
        log.debug("Starting auto schema sync for {}", setting);
        EntityDefinition fromEntity = getEntity(setting.getFromEntityId());

        List<String> activeConnectors = connectorService.getAllActive().stream().map(Connector::getId).collect(Collectors.toList());

        EntityDefinition syncariEntity = getEntity(setting.getSyncariEntityId());
        int updatedCount = 0;
        for (AttributeDefinition attr : fromEntity.getAttributes()) {
            // Skip inactive, reference and standard fields
            if (attr.isActive() && !attr.isSyncariDefined() && !EntityData.SYNCARI_DEFINED_FIELDS.contains(attr.getApiName())) {
                log.debug("Starting auto schema sync for field {}", attr.getApiName());
                // Add syncari attribute, synapse attribute and create attr graph
                List<SchemaMapping> existingMappings = schemaMappingRepo.findByConnectorAndSynapseObject(
                        fromEntity.getConnectorId(), attr.getId(), Scope.ATTRIBUTE.name());
                // if schemaMapping record belongs to this syncari entity
                boolean isMappedWithCurrentSyncariEntity = existingMappings.stream().anyMatch(mapping -> syncariEntity.getFieldById(mapping.getSyncariId()).isPresent());
                boolean hasExistingMapping = !existingMappings.isEmpty() && isMappedWithCurrentSyncariEntity;
                if (!hasExistingMapping) {
                    addAttributeToSchema(fromEntity.getConnectorId(), attr, syncariEntity, fromEntity, Optional.empty(), Optional.empty(), Optional.of(SyncDirection.INBOUND));
                } else {
                    log.debug(
                            "Syncari attribute found through mapping, while doing autosync from connector {} , field {}",
                            fromEntity.getConnectorId(), attr.getApiName());
                }
                // Should be available on syncariEntity because of above call
                AttributeDefinition syncariAttribute = !hasExistingMapping
                        ? syncariEntity.getFieldByName(attr.getApiName())
                        : existingMappings.stream()
                            .filter(m -> syncariEntity.getFieldById(m.getSyncariId()).isPresent())
                            .map(m -> syncariEntity.getAttribute(m.getSyncariId()))
                            .findFirst().orElse(null);

                // In some cases, Syncari Attribute not available for the entry in schema mapping collection.
                // Need to fix the Auto schema sync feature. For now a defensive check
                if (!existingMappings.isEmpty() && syncariAttribute == null) {
                    log.error("Syncari Attribute with ID {} does not exist. Check Schema mapping collection", existingMappings.get(0).getSyncariId());
                    continue;
                }

                if (!hasExistingMapping) {
                    schemaMappingRepo.save(new SchemaMapping().setConnectorId(fromEntity.getConnectorId())
                            .setSynapseObjectId(attr.getId()).setSyncariId(syncariAttribute.getId())
                            .setScope(Scope.ATTRIBUTE.name()));
                    updatedCount++;
                }

                List<String> toEntityIds = setting.getToEntityIds().stream().filter(x -> StringUtils.isNotEmpty(x)).collect(Collectors.toList());
                for (String toEntityId : toEntityIds) {
                    EntityDefinition toEntity = getEntity(toEntityId);
                    log.debug("For synapse {}", toEntity.getConnectorId());
                    try {
                        if(activeConnectors.contains(toEntity.getConnectorId())) {
                            List<SchemaMapping> existingDestinationMappings = schemaMappingRepo
                                    .findByConnectorAndSyncariObject(toEntity.getConnectorId(),
                                            syncariAttribute.getId(), Scope.ATTRIBUTE.name());
                            if (existingDestinationMappings.isEmpty()) {
                                AttributeDefinition synapseAttr = createAttributeInSynapse(toEntity.getConnectorId(),
                                        toEntity, attr);
                                addAttributeToSchema(toEntity.getConnectorId(), synapseAttr, syncariEntity, toEntity,
                                        Optional.ofNullable(syncariAttribute), Optional.empty(), Optional.of(SyncDirection.OUTBOUND));
                                schemaMappingRepo.save(new SchemaMapping().setConnectorId(toEntity.getConnectorId())
                                        .setSynapseObjectId(synapseAttr.getId()).setSyncariId(syncariAttribute.getId())
                                        .setScope(Scope.ATTRIBUTE.name()));
                                updatedCount++;
                            } else {
                                log.info(
                                        "Mapping found for to-entity {} on connector {} while doing autosync from connector {} , field {}",
                                        toEntity.getApiName(), toEntity.getConnectorId(), fromEntity.getConnectorId(),
                                        attr.getApiName());
                            }
                        }else{
                            log.debug("Skipping schema sync to entityId {} as connector is not active.", fromEntity.getId());
                        }
                    } catch (Exception e) {
                        log.error("Error during auto schema refresh {}", e.getMessage(), e);
                        log.error(ExceptionUtils.getStackTrace(e));
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        //if autopublish flag is set, and auto sync has modified the pipeline
        if (setting.isAutoPublish() && updatedCount > 0) {
            final Optional<MappingGraph> mappingGraph = mappingGraphService.retrieveDraftEntityGraph(syncariEntity.getId());
            mappingGraph.ifPresent(g -> {
                final List<ValidationError> validationErrors = g.validateWithoutException();
                if (!validationErrors.isEmpty()) {
                    final String validationError = validationErrors.stream().map(ValidationError::getMessage)
                            .reduce((e1, e2) -> e1 + "\n" + e2).orElse("");
                    final Notification notification = new Notification();
                    notification.setType(NotificationType.WARN);
                    notification.setSubject(i18n("auto_schema_sync_pipeline_invalid", syncariEntity.getDisplayName()));
                    notification.setBody(i18n("auto_schema_sync_pipeline_invalid", validationError));
                    notification.setUserId(g.getUpdatedBy());
                    notificationService.send(notification);
                } else {
                    mappingGraphService.approveDraft(g);
                }
            });
        }
    }

    public List<FunctionDefinition> getFunctions(Scope scope) {
        //TODO temporarily hide the lookUpSyncariRecord function untill its removed
        return functionService.findByScope(scope).stream()
                .filter(f -> !"lookUpSyncariRecord".equalsIgnoreCase(f.getName()))
                .sorted(comparing(NodeDefinition::getDisplayName)).collect(Collectors.toList());
    }

    public Optional<FunctionDefinition> getFunction(String name,Scope scope) {
        return functionService.findByNameAndScope(name,scope);
    }

    public List<ActionDefinition> getActions() {
        return actionService.getAllActions();
    }

    public Optional<ActionDefinition> getAction(String name) {
        return actionService.getAction(name);
    }

    public EntityDefinition createEntityLike(EntityDefinition sourceEntity, List<String> sinkSynapseIds) {
        if (StringUtils.isNotEmpty(sourceEntity.getConnectorId()) && StringUtils.isNotEmpty(sourceEntity.getId())){
            List<EntityDefinition> edef = this.refreshSynapseSchema(sourceEntity.getConnectorId(),sourceEntity,"entitycreate_"+UUID.randomUUID());
            if (CollectionUtils.isNotEmpty(edef)){
                sourceEntity = edef.stream().findFirst().get();
            }
        }
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition syncariEntity = createEntityFor(sourceEntity, syncariConnector, DraftStatus.APPROVED);
        // Create the pipeline between source and Syncari entity
        createGraphForCreateEntity(syncariEntity, sourceEntity);

        if(sinkSynapseIds != null) {
            sinkSynapseIds.forEach(sink -> {
                Connector sinkConnector = connectorService.find(sink).get();
                log.info("Creating entity {} in synapse {}", syncariEntity.getApiName(), sinkConnector.getName());
                // For each end system, create the entity in the end system
                EntityDefinition sinkEntity = createEntityFor(syncariEntity, sinkConnector, DraftStatus.APPROVED);
                MetadataService dataService = factory.getSchemaService(sinkConnector.getMetadata());
                CreateObjectRequest request = new CreateObjectRequest(transformer.toConnectorInfo(sinkConnector), transformer.toEntitySchema(sinkEntity, sinkConnector));
                dataService.createObject(request);
                // Create the pipeline between sink and Syncari entity
                createGraphForCreateEntity(syncariEntity, sinkEntity);
                log.info("Successfully created entity {} in synapse {}", syncariEntity.getApiName(), sinkConnector.getName());
            });
        }
        if (StringUtils.isNotBlank(sourceEntity.getDatasetId())) {
            componentDependencyService.addDependency(syncariEntity.getId(), ComponentType.entity, sourceEntity.getDatasetId(), ComponentType.dataset);
        }
        return syncariEntity;
    }

    /**
     * Add new syncari entity in draft status
     * @param sourceEntity - EntityDefinition with draft status to save
     * @return - Added syncari entity
     */
    public EntityDefinition createDraftEntity(EntityDefinition sourceEntity, Boolean ignoreDefaultFields) {
        Connector syncariConnector = connectorService.getSyncariConnector();
        abac.check(new AbacContext().withAction(Permission.CREATE_ENTITY)
            .withResourceType(ResourceType.GLOBAL).withThrowException(true)
            .withThrowExceptionMessage(i18n("abac_permission_error")), sourceEntity);
        validateCondition(StringUtils.isEmpty(sourceEntity.getDisplayName()), I18n.i18n("entity_invalid_displayname"));
        validateCondition(!sourceEntity.isDraft(), i18n("entity_non_draft_upsert"), sourceEntity.getApiName());
        validateCondition(!StringUtils.isEmpty(sourceEntity.getId()), i18n("entity_with_id_already_exists"), sourceEntity.getId());
        List<EntityDefinition> existing = entityProxyRepo.findEntities(syncariConnector.getId(), sourceEntity.getApiName());
        validateCondition(!existing.isEmpty(), i18n("entity_with_apiname_already_exists"), sourceEntity.getApiName());
        existing = entityProxyRepo.findAllByConnectorId(syncariConnector.getId());
        validateCondition(existing.stream().anyMatch(entity -> !sourceEntity.getApiName().equals(entity.getApiName()) &&
                entity.getResolvedDataStoreName().equals(sourceEntity.getResolvedDataStoreName())),
        i18n("entity_datastore_name_duplicate_error", sourceEntity.getDataStoreName()));
        validateCondition(!textUtil.isValidApiName(sourceEntity.getApiName()), i18n("entity_api_name_invalid"), sourceEntity.getApiName());
        validateCondition(!textUtil.isValidApiName(sourceEntity.getDataStoreName()), i18n("entity_datastore_name_update_error"), sourceEntity.getDataStoreName());
        log.info("Creating new Draft syncari entity {}", sourceEntity.getApiName());
        var draftService = getDraftService(sourceEntity);
        sourceEntity.setStatus(sourceEntity.getStatus() != null ? sourceEntity.getStatus() : Status.ACTIVE);
        var draft = (EntityDefinition) draftService.createDraftFor(sourceEntity);

        // save tags
        var tagMap = sourceEntity.getTags().stream().collect(Collectors.toMap(t -> t.getName(), t -> t.getValue()));
        List<Tag> tags = tagService.assign(tagMap, Taggable.entity, draft.getId());
        draft.setTags(tags);
        // save attributes
        if(!ignoreDefaultFields)
            getDefaultAttributes(draft).forEach(a -> createDraftAttribute(draft.getId(), a));
        sourceEntity.getAttributes().forEach(a -> createDraftAttribute(draft.getId(), a));
        return getEntity(draft.getId());
    }

    /**
     * Update Draft syncari entity
     * @param sourceEntity - EntityDefinition with draft status to update
     * @return - Updated syncari entity
     */
    public EntityDefinition updateDraftEntity(EntityDefinition sourceEntity) {
        validateCondition(!sourceEntity.isDraft(), i18n("entity_non_draft_upsert"), sourceEntity.getApiName());
        var existingDraft = getEntity(sourceEntity.getId());
        validateCondition(!existingDraft.isDraft(), i18n("entity_with_status_not_found"), DraftStatus.NEW.name(), existingDraft.getId());
        validateCondition(!existingDraft.getApiName().equals(sourceEntity.getApiName()),
                i18n("entity_api_name_update_error"), existingDraft.getApiName());
        validateCondition(!textUtil.isValidApiName(sourceEntity.getDataStoreName()), i18n("entity_datastore_name_update_error"), sourceEntity.getDataStoreName());
        log.info("Updating Draft syncari entity {}", sourceEntity.getApiName());
        validateCondition(getSyncariEntities().stream().filter(entity -> !sourceEntity.getApiName().equals(entity.getApiName()) &&
                        entity.getResolvedDataStoreName().equals(sourceEntity.getResolvedDataStoreName())).findFirst().isPresent(),
                i18n("entity_datastore_name_duplicate_error", sourceEntity.getDataStoreName()));
        sourceEntity.setStatus(sourceEntity.getStatus() != null ? sourceEntity.getStatus() : existingDraft.getStatus());
        sourceEntity.setParentId(sourceEntity.getParentId() != null ? sourceEntity.getParentId(): existingDraft.getParentId());
        // make sure to set the oldName in store config
        sourceEntity.resetDataStoreName(sourceEntity.getDataStoreOldName());

        var newDraft = entityProxyRepo.save(sourceEntity);
        Connector syncariConnector = connectorService.getSyncariConnector();
        // add/update provided attributes
        sourceEntity.getAttributes().forEach(a -> {
            a.setEntityId(newDraft.getId());
            if(StringUtils.isBlank(a.getId())){
                createDraftAttribute(newDraft.getId(), a);
            }else{
                //skip id updates on Syncari entities
                if(! (a.isIdField() && syncariConnector.getId().equals(newDraft.getConnectorId()))) {
                    updateDraftAttribute(newDraft.getId(), a);
                }
            }
        });

        // discard removed attributes
        var sourceAttribIds = sourceEntity.getAttributes().stream().map(a -> a.getId()).collect(Collectors.toList());
        var attribsToDiscard = existingDraft.getAttributes().stream().filter(a -> !sourceAttribIds.contains(a.getId())).collect(Collectors.toList());
        attribsToDiscard.forEach(a -> discardDraftAttribute(existingDraft.getId(), a));

        tagService.updateTagsFor(existingDraft.getId(), Taggable.entity, sourceEntity.getTags());

        return getEntity(newDraft.getId());
    }

    /**
     * Creates draft entity from existing approved
     * @param approvedEntityId - Id of Approved entity
     * @return
     */
    public EntityDefinition createEntityDraftFor(String approvedEntityId) {
        var existing = getEntity(approvedEntityId);
        validateCondition(!existing.isApproved(), i18n("entity_with_status_not_found"), DraftStatus.APPROVED.name(), existing.getId());
        var entityDraftService = getDraftService(existing);
        validateCondition(entityDraftService.hasDraft(existing), i18n("entity_draft_exists"), existing.getApiName());
        log.info("Creating draft for entity {}", existing.getApiName());
        abac.check(
            new AbacContext()
            .withResourceType(ResourceType.ENTITY)
            .withAction(Permission.CREATE_DRAFT)
            .withThrowException(true)
            .withThrowExceptionMessage(i18n("abac_permission_error")),
            existing);
        var savedDraft = (EntityDefinition) entityDraftService.createDraftFor(existing);
        tagService.cloneTags(existing.getId(), savedDraft.getId(), Taggable.entity);
        existing.getAttributes().forEach(a -> {
            var attribDraftService = getDraftService(a);
            a.setEntityId(savedDraft.getId());
            var draftAttrib = attribDraftService.createDraftFor(a);
            tagService.cloneTags(a.getId(), draftAttrib.getId(), Taggable.attribute);
        });
        return getEntity(savedDraft.getId());
    }

    /**
     * Add new attribute to draft entity
     * @param entityId - EntityDefinition: id of EntityDefinition to which field belongs
     * @param attribute - attribute to be saved
     * @return - Newly added draft attribute
     */
    public AttributeDefinition createDraftAttribute(String entityId, AttributeDefinition attribute){
        EntityDefinition entity = getEntity(entityId);
        abac.check(new AbacContext()
            .withResourceType(ResourceType.ENTITY)
            .withAction(Permission.CREATE_DRAFT)
            .withThrowException(true)
            .withThrowExceptionMessage(i18n("abac_permission_error")), entity);
        Connector connector = connectorService.find(entity.getConnectorId()).get();
        validateCondition(!StringUtils.isEmpty(attribute.getId()), i18n("attribute_with_id_exists"), attribute.getId());
        validateCondition(entity.hasField(attribute.getApiName()),
                i18n("attribute_with_apiname_exists_in_entity"), attribute.getApiName(), entity.getApiName());
        validateDraftAttribute(entity, attribute);
        log.info("Adding field {} in entity {} of {} connector", attribute.getApiName(), entity.getApiName(), connector.getName());


        var draftService = getDraftService(attribute);
        attribute.setEntityId(entity.getId());
        attribute.setStatus(attribute.getStatus() != null ? attribute.getStatus() : Status.ACTIVE);
        updateAttributesForCompositeKeyFields(entityId, attribute);
        var draft =  (AttributeDefinition) draftService.createDraftFor(attribute);

        // save tags
        var tagMap = attribute.getTags().stream().collect(Collectors.toMap(t -> t.getName(), t -> t.getValue()));
        List<Tag> tags = tagService.assign(tagMap, Taggable.attribute, draft.getId());
        draft.setTags(tags);

        return draft;
    }


    /**
     * Update existing draft attribute
     * @param entityId - EntityDefinition: id of EntityDefinition to which field belongs
     * @param attribute - attribute to be updated
     * @return - updated draft Attribute
     */
    public AttributeDefinition updateDraftAttribute(String entityId, AttributeDefinition attribute){
        EntityDefinition entity = getEntity(entityId);
        abac.check(new AbacContext()
            .withResourceType(ResourceType.ENTITY)
            .withAction(Permission.CREATE_DRAFT)
            .withThrowException(true)
            .withThrowExceptionMessage(i18n("abac_permission_error")), entity);
        Connector connector = connectorService.find(entity.getConnectorId()).get();
        var existingDraft = getAttribute(attribute.getId());
        // If the existing one is an id field and the new coming is also an id field.
        validateCondition(existingDraft.isIdField() && attribute.isIdField() && connector.isSyncariConnector(),
                i18n("attribute_upsert_failed_no_update_to_syncari_id_field"));
        validateCondition(!existingDraft.isDraft(), i18n("attribute_upsert_failed_no_draft"), existingDraft.getApiName());
        validateCondition(!existingDraft.getApiName().equals(attribute.getApiName()),
                i18n("attribute_api_name_update_error"), existingDraft.getApiName());
        validateCondition(!textUtil.isValidApiName(attribute.getDataStoreName()),
                i18n("attribute_datastore_name_update_error"), attribute.getDataStoreName());
        try {
            validateDraftAttribute(entity, attribute);
        } catch (SyncariValidationException e) {
            if(!connector.isSyncariConnector() && !attribute.isSyncariDefined()) {
                entity.getField(attribute.getApiName()).get().setWatermarkField(attribute.isWatermarkField());
                entity.getField(attribute.getApiName()).get().setIdField(attribute.isIdField());
                existingDraft.setWatermarkField(attribute.isWatermarkField());
                existingDraft.setIdField(attribute.isIdField());
                existingDraft.setDataType(attribute.getDataType());
                existingDraft.setUnique(attribute.isUnique());
                existingDraft.setUpdatable(attribute.isUpdatable());
                existingDraft.setNillable(attribute.isNillable());
                return attributeProxyRepo.save(existingDraft);
            } else {
                throw e;
            }
        }
        log.info("Updating Draft syncari attribute {} in entity {}", attribute.getApiName(), entity.getApiName());

        attribute.setEntityId(entityId);
        attribute.setStatus(attribute.getStatus() != null ? attribute.getStatus() : existingDraft.getStatus());
        attribute.setParentId(attribute.getParentId() != null ? attribute.getParentId(): existingDraft.getParentId());
        updateAttributesForCompositeKeyFields(entityId, attribute);

        // save the newly added tags and delete the removed tags
        List<Tag> incomingTags = attribute.getTags();
        tagService.updateTagsFor(existingDraft.getId(), Taggable.attribute, incomingTags);
        return attributeProxyRepo.save(attribute);
    }

    private void updateAttributesForCompositeKeyFields(String entityId, AttributeDefinition attribute){
        if (attribute.isIdField() && StringUtils.isNotEmpty(attribute.getCompositeKey())){
            String [] compositeKeyFieldsApiNames = attribute.getCompositeKey().split(Pattern.quote(EntitySchema.COMPOSITE_KEY_DELIMETER));
            if (ArrayUtils.isNotEmpty(compositeKeyFieldsApiNames)){
                for (String apiName: compositeKeyFieldsApiNames){
                    Optional<AttributeDefinition> compositeKeyPartAttrib = attributeProxyRepo.findByEntityIdAndApiName(entityId, apiName);
                    compositeKeyPartAttrib.ifPresent(a -> {
                        a.setCreateOnly(true);
                        attributeProxyRepo.save(a);
                    });
                }
            }
        }
    }


    public AttributeDefinition createAttributeLike(AttributeDefinition sourceAttr, Optional<String> referenceEntityId, EntityDefinition syncariEntity, Map<String, EntityDefinition> sinkMap, List<String> sinkSynapseIds) {
        // Create the pipeline between source attribute and Syncari attribute
        String sanitizedFieldName = sanitizedFieldName(sourceAttr.getApiName());
        AttributeDefinition syncariField = syncariEntity.hasField(sanitizedFieldName) ? syncariEntity.getFieldByName(sanitizedFieldName) : null;
        AttributeDefinition newAttr = createGraphFor(syncariField, referenceEntityId, sourceAttr, syncariEntity);
        if (sinkSynapseIds != null) {
            sinkSynapseIds.forEach(sink -> {
                Connector sinkConnector = connectorService.find(sink).get();
                log.info("Creating attr {} in synapse {}", sourceAttr.getApiName(), sinkConnector.getName());
                // For each end system, create the attr in the end system
                EntityDefinition toSynapseEntity = sinkMap.get(sink);
                AttributeDefinition toSynapseAttr = addAttributeToSchema(sink, sourceAttr, syncariEntity, toSynapseEntity, Optional.empty(), referenceEntityId, Optional.empty());
                createAttributeInSynapse(sink, toSynapseEntity, toSynapseAttr);
                // Create the pipeline between sink and Syncari entity
                createGraphFor(syncariEntity.getFieldByName(sourceAttr.getApiName()), Optional.empty(), toSynapseAttr, syncariEntity);
                log.info("Successfully created attr {} in synapse {}", sourceAttr.getApiName(), sinkConnector.getName());
            });
        }

        return newAttr;
    }

    public AttributeDefinition addAttributeToSyncariEntity(AttributeDefinition synapseField, String apiName, String displayName,
                                                           Optional<String> referenceEntityId, EntityDefinition syncariEntity){

        var newAttr = createAttributeFromExisting(synapseField, sanitizedFieldName(apiName), displayName,
                referenceEntityId, syncariEntity, true, Optional.empty(), Optional.empty(), Optional.ofNullable(synapseField.getDataType().getName()));
        syncariEntity.addField(attributeProxyRepo.save(newAttr));
        log.info("Field {} added successfully to connector {}", newAttr.getApiName(), syncariEntity.getConnectorId());
        return newAttr;
    }

    private AttributeDefinition addAttributeToEntity(AttributeDefinition field, Optional<String> referenceEntityId, EntityDefinition entity, boolean isSyncari) {
        Optional<AttributeDefinition> existing = attributeProxyRepo.findByEntityIdAndApiName(entity.getId(),
                field.getApiName());

        String apiName = sanitizedFieldName(field.getApiName());
        if (existing.isPresent()) {
            log.warn("Field {} already exists on connector {} on entity {}", apiName, entity.getConnectorId(),
                    entity.getApiName());
            return existing.get();
        }
        var newAttr = createAttributeFromExisting(field, apiName, null, referenceEntityId, entity, isSyncari, Optional.empty(), Optional.empty(),Optional.empty());
        entity.addField(attributeProxyRepo.save(newAttr));
        log.info("Field {} added successfully to connector {}", newAttr.getApiName(), entity.getConnectorId());
        return newAttr;
    }

    public AttributeDefinition createAttributeFromExisting(AttributeDefinition existingField, String apiName, String displayName,
                                                            Optional<String> referenceEntityId, EntityDefinition entity,
                                                            boolean isSyncari, Optional<Boolean> isMulti,Optional<Boolean> isRequired, Optional<String> dataType){
        final AttributeDefinition newAttr = existingField.withEntityId(entity.getId());
        newAttr.setDraftStatus(DraftStatus.APPROVED);
        //Syncari API names cannot have "." in them
        newAttr.setApiName(StringUtils.isBlank(apiName) ? sanitizedFieldName(existingField.getApiName()) : apiName);
        newAttr.setDataStoreName(StringUtils.isBlank(apiName) ? sanitizedFieldName(existingField.getApiName()) : apiName);
        newAttr.setDisplayName(StringUtils.isBlank(displayName) ? existingField.getDisplayName() : displayName);
        newAttr.setId(ObjectId.get().toHexString());
        newAttr.setCreatedAt(null);
        newAttr.setUpdatedAt(null);
        newAttr.setCreatedBy(null);
        newAttr.setUpdatedBy(null);
        newAttr.setSystem(false);
        newAttr.setUpdatable(true);
        dataType.ifPresent(dT -> newAttr.setDataType(DatatypeFactory.getDatatype(dT)));
        isMulti.ifPresent(multivalued -> newAttr.setMultiValueField(multivalued));
        isRequired.ifPresent(required -> newAttr.setNillable(!required));
        if(referenceEntityId.isPresent()) {
            EntityDefinition referencedEntity = getEntity(referenceEntityId.get());
            if(referencedEntity.getIdField().isEmpty()) {
                throw new SyncariValidationException(String.format(i18n("no_id_found"), referencedEntity.getApiName()));
            }
            newAttr.setReferenceTo(referencedEntity.getApiName());
            newAttr.setReferenceTargetField(referencedEntity.getIdField().get().getApiName());
            if(isSyncari) {
                newAttr.setLength(32);
            }
        }else if (newAttr.isReference()){
            Optional<EntityDefinition> entityDef = entityProxyRepo.findById(existingField.getEntityId());
            entityDef.ifPresent(synapseEntity -> {
                Optional<EntityDefinition> referencedSchema = findEntity(synapseEntity.getConnectorId(),newAttr.getReferenceTo());
                referencedSchema.ifPresent(refschema -> {
                    List<MappingGraph> entityGraphsWithSourceOrSink = mappingGraphService.findEntityGraphsWithSourceOrSink(refschema.getId());
                    entityGraphsWithSourceOrSink.stream().findFirst().ifPresent(graph -> {
                        newAttr.setReferenceTo(graph.getCoreNode().getApiName());
                    });
                    refschema.getIdField().ifPresent(idf -> {
                        newAttr.setReferenceTargetField(idf.getApiName());
                    });
                });
            });
        }
        if(isSyncari) {
            // check if entity already has id field remove the idField flag
            if (entity.getIdField().isPresent()) {
                newAttr.setIdField(false);
            }

            // check if entity already has wm field remove the wmField flag
            if (entity.getWatermarkField().isPresent()) {
                newAttr.setWatermarkField(false);
            }
        }
        return newAttr;
    }

    protected String sanitizedFieldName(String fieldName) {
        return textUtil.sanitizeFieldName(fieldName);
    }

    public static String toApiName(String value) {
        return StringUtils.isBlank(value) ? value : value.replaceAll("[^a-zA-Z0-9_]+", "_");
    }

    private Schema getSchema(List<EntityDefinition> defs) {
        return getSchema(defs, true, true);
    }

    private Schema getSchema(List<EntityDefinition> defs, boolean detailed, boolean includeTags) {
        Schema schema = new Schema();
        Map<String, String> entityNameToIdMap = new HashMap<>();
        Map<String, EntityDef> entityIdToDef = new HashMap<>();
        Map<String, AttributeDef> attrIdToDef = new HashMap<>();
        for (EntityDefinition def : defs) {
            entityNameToIdMap.put(def.getApiName().toLowerCase(), def.getId());
        }

        // create a map of entity id and list of MappingGraph for lookup
        long startTime = System.currentTimeMillis();
        Map<String, List<MappingGraph>> mapOfEntityMappingGraphs = mappingGraphService.retrieveEntityGraphsLite().stream()
                .filter(graph -> !graph.isArchived()).collect(Collectors.groupingBy(MappingGraph::getTargetId));
        Map<String, List<AttributeDefinition>> entityToAttrMap = detailed ? getEntityIdToAttribMap(defs) : new HashMap<>();
        log.info("entityToAttrMap count {} ", entityToAttrMap.size());
        log.debug("Schema Retrieval time {} ms", System.currentTimeMillis() - startTime);

        Map<String, Connector> connectorMap = new HashMap<>();
        Map<String, String> userNamesById = new HashMap<>();
        for (EntityDefinition def : defs) {
            Connector connector = connectorMap.containsKey(def.getConnectorId()) ? connectorMap.get(def.getConnectorId())
                    : connectorService.get(def.getConnectorId());
            connectorMap.put(def.getConnectorId(), connector);
            EntityDef entityDef = new EntityDef(def.getId(), def.getApiName());
            entityDef.setDescription(def.getDescription());
            entityDef.setPipelineStatus(getPipelineStatus(mapOfEntityMappingGraphs.get(def.getId())));
            entityDef.setDisplayName(def.getDisplayName());
            entityDef.setDataStoreName(def.getDataStoreName());
            entityDef.setType(def.isCustom() ? EntityType.custom : EntityType.standard);
            entityDef.setCreatedAt(def.getCreatedAt());
            entityDef.setUpdatedAt(def.getUpdatedAt());
            entityDef.setCreatedBy(getUserName(def.getCreatedBy(), userNamesById));
            entityDef.setUpdatedBy(getUserName(def.getUpdatedBy(), userNamesById));
            entityDef.setDraftStatus(def.getDraftStatus());
            entityDef.setStatus(def.getStatus());
            entityDef.setReadonly(def.isReadOnly());
            entityDef.setSyncariSource(def.isSyncariSource());
            List<AttributeDefinition> fields = entityToAttrMap.getOrDefault(def.getId(), new ArrayList<>());
            for (AttributeDefinition field : fields) {
                AttributeDef f = transformer.toAttributeDef(field);
                f.setDraftStatus(field.getDraftStatus());
                if(!connector.isSyncariConnector()) {
                    SynapseInfoService synapseService = factory.getSynapseService(connector.getMetadata());
                    f.setReadOnly((synapseService.isSource() && !synapseService.isSink()) || def.isReadOnly() || f.isReadOnly());
                }

                entityDef.getFields().add(f);
                attrIdToDef.put(field.getId(), f);
                if (field.getDataType().getClass().isAssignableFrom(ReferenceType.class)) {
                    if (!StringUtils.isBlank(field.getReferenceTo())
                            && !field.getReferenceTo().equalsIgnoreCase(def.getApiName())) {
                        String refEntityId = entityNameToIdMap.get(field.getReferenceTo().toLowerCase());
                        if (StringUtils.isBlank(refEntityId))
                            continue;
                        entityDef.getConnectedTo().add(refEntityId);
                    }
                }
            }
            entityIdToDef.put(def.getId(), entityDef);
            schema.addEntity(entityDef);
            schema.setLastRefreshedAt(def.getUpdatedAt() == null ? def.getCreatedAt() : def.getUpdatedAt());
        }
        log.info("attributes processed for total {} schemas ", schema.getEntities().size());
        if (includeTags) {
            List<Tag> tags = tagRepo.findByTaggableAndTaggedIdIn(Taggable.attribute, attrIdToDef.keySet());
            tags.stream().forEach(t -> {
                attrIdToDef.get(t.getTaggedId()).getTags().add(t.getName());
            });
            List<Tag> entityTags = tagRepo.findByTaggableAndTaggedIdIn(Taggable.entity, entityIdToDef.keySet());
            entityTags.stream().forEach(t -> {
                entityIdToDef.get(t.getTaggedId()).getTags().add(t.getName());
            });
            log.info("Total tags included {}, total entityTags included {} ", tags.size(), entityTags.size());
        }
        return schema;
    }

    /**
     * Deletes an unmapped entity
     * @param entityId
     * @throws RuntimeException
     */
    public void deleteEntity(String entityId) throws RuntimeException{
        EntityDefinition entity = getEntity(entityId);
        abac.check(new AbacContext()
            .withResourceType(ResourceType.ENTITY)
            .withAction(Permission.DELETE)
            .withThrowException(true)
            .withThrowExceptionMessage(i18n("abac_permission_error")), entity);
        validateCondition(entity.getApiName().equalsIgnoreCase("timeTicker"), i18n("entity_delete",entity.getDisplayName()));
        Schema s = getSchema(List.of(entity));
        EntityDef ed = s.getEntities().get(0);

        // Check if entity is referred by other entities
        List<Reference> reference = getReferringAttributes(entity)
                .stream()
                .filter(r -> !r.getFromEntity().equals(r.getToEntity()))
                .collect(Collectors.toList());
        if(!reference.isEmpty()){
            throw new RuntimeException(i18n("referred_entity_delete",entity.getApiName(),reference.size()));
        }

        // Check if the entity graph is unmapped
        List<MappingGraph> graph = mappingGraphService.retrieveMappingGraphForEntity(entityId);
        if(!getPipelineStatus(graph).equals(PipelineStatus.UNMAPPED)){
            throw new RuntimeException(i18n("mapped_entity_delete",entity.getApiName()));
        }

        //Delete pipeline versions if any
        mappingGraphService.discardAllVersionsEntityGraph(entityId);

        // Delete SchemaMapping and synapse entities/attributes
        List<SchemaMapping> schemaMappings = schemaMappingRepo
                .findByConnectorAndSyncariObject(entity.getConnectorId(), entityId, Scope.ENTITY.name());
        schemaMappingRepo.deleteAll(schemaMappings);

        // delete unresolved reference
        unresolvedReferenceService.removeBy(entityId);

        // delete the entity data collection
        entityRepo.delete(entity.getApiName());

        // Delete syncari entities and attributes
        entityProxyRepo.delete(entity); // delete syncari entity
        attributeProxyRepo.deleteAll(entity.getAttributes()); // delete attributes
    }

    /**
     * Accepts the publishedField since a draft field will never be used in a pipeline
     * @param publishedField
     * @throws RuntimeException
     */
    public void canDeleteField(AttributeDefinition publishedField) throws RuntimeException {
        // Only fields that are on the Syncari entity or where isSyncariDefined
        // is true can be deleted.

        if (publishedField.isSyncariDefined()) {
            List<MappingGraph> sourceOrSinkNodes = mappingGraphService.findAttributeGraphsWithSourceOrSink(
                    publishedField.getId()
            );
            // avoid versioned graphs, check for draft or published graph
            List<MappingGraph> sourceOrSinkNodeUnversionedGraphs = sourceOrSinkNodes.stream().filter(g -> !g.isVersioned()).collect(Collectors.toList());
            if (!sourceOrSinkNodeUnversionedGraphs.isEmpty()) {
                MappingGraph firstGraph = sourceOrSinkNodes.get(0);
                throw new SyncariValidationException(
                        i18n("external_field_used_in_pipeline", sourceOrSinkNodes.size(), firstGraph.getName())
                );
            }
        } else {
            String entityId = publishedField.getEntityId();
            EntityDefinition entity = getEntity(entityId);
            String connectorId = entity.getConnectorId();

            Boolean entityIsSyncari = connectorId.equalsIgnoreCase(connectorService.getSyncariConnector().getId());

            if (!entityIsSyncari) {
                throw new SyncariValidationException(i18n("only_delete_syncari_defined_fields"));
            }
            // check if this is the only id field in entity
            var idField = entity.getAttributes().stream().filter(a -> a.isIdField() && !a.getId().equals(publishedField.getId())).findAny();
            validateCondition(publishedField.isIdField() && idField.isEmpty(), i18n("id_field_delete", publishedField.getDisplayName()));

            // check if this is the only wm field in entity
            var wmField = entity.getAttributes().stream().filter(a -> a.isWatermarkField() && !a.getId().equals(publishedField.getId())).findAny();
            validateCondition(publishedField.isWatermarkField() && wmField.isEmpty(), i18n("wm_field_delete", publishedField.getDisplayName()));

            // Check if the field is used in any pipelines
            Optional<MappingGraph> graph = mappingGraphService.retrieveAttributeGraph(publishedField.getId());
            if (graph.isPresent()) {
                throw new SyncariValidationException(String.format(i18n("pipline_exists"), publishedField.getApiName()));
            }
        }
    }

    /**
     * Deletes an unused syncari schema field
     * @param entityId
     * @param fieldId
     * @throws RuntimeException
     */
    public void deleteField(String entityId, String fieldId) throws RuntimeException {
        AttributeDefinition field = getAttribute(fieldId);

        String publishedFieldId = field.getId();
        if (field.isDraft() && !StringUtils.isBlank(field.getParentId())) {
            publishedFieldId = field.getParentId();
        }

        AttributeDefinition publishedField = getAttribute(publishedFieldId);
        canDeleteField(publishedField);

        EntityDefinition entity = getEntity(field.getEntityId());
        abac.check(new AbacContext()
            .withResourceType(ResourceType.ENTITY)
            .withAction(Permission.CREATE_DRAFT)
            .withThrowException(true)
            .withThrowExceptionMessage(i18n("abac_permission_error")), entity);
        
        attributeProxyRepo.deleteById(field.getId());

        Connector connector = connectorService
                .find(entity.getConnectorId())
                .orElseThrow(() -> new SyncariValidationException("connector_not_found_for_field"));

        // Delete the data from the datastore
        AttributeSchema attrSchema = transformer.toAttrSchema(field, entity, connector);
        datastoreService.deleteField(entity.getDataStoreName(), attrSchema);
    }

    public PipelineStatus getPiplelineStatusForEntity(String entityId){
        return getPipelineStatus(mappingGraphService.retrieveMappingGraphForEntity(entityId));
    }
    /**
     * PipelineStatus inferred from the MappingGraph for an entity
     *
     * @return PipelineStatus
     */
    private PipelineStatus getPipelineStatus(List<MappingGraph> mappingGraphs) {
        if (mappingGraphs == null || mappingGraphs.isEmpty()) {
            return PipelineStatus.UNMAPPED;
        } else if (mappingGraphs.size() == 1) {
            return mappingGraphs.get(0).isApproved() ? PipelineStatus.PUBLISHED : PipelineStatus.DRAFT;
        } else {
            return PipelineStatus.PUBLISHED_WITH_DRAFT;
        }
    }

    public void setMappingGraphService(MappingGraphService mappingGraphService) {
        this.mappingGraphService = mappingGraphService;
    }

    public List<Reference> getReferringAttributes(EntityDefinition entityDefinition) {
        List<EntityDefinition> defs = getSyncariEntities();
        var defMap = defs.stream().collect(Collectors.toMap(e -> e.getId(), e -> e));
        List<AttributeDefinition> allActiveAttributes = attributeProxyRepo
                .findActiveByEntityIds(defs.stream().map(e -> e.getId()).collect(Collectors.toList()));
        var attribMap = allActiveAttributes.stream()
                .collect(Collectors.toMap(a -> a.getEntityId() + "_" + a.getApiName(), a -> a));
        List<Reference> references = allActiveAttributes.stream()
                .filter(attributeDefinition -> attributeDefinition.isReferenceTo(entityDefinition.getApiName()))
                .map(attrib -> {
                    Reference reference = new Reference().setFromAttribute(attrib).setFromEntity(defMap.get(attrib.getEntityId()))
                            .setToEntity(entityDefinition).setToAttribute(
                            attribMap.get(entityDefinition.getId() + "_" + attrib.getReferencedAttributeName()));
                    if (null == attribMap.get(entityDefinition.getId() + "_" + attrib.getReferencedAttributeName())){
                        log.info("For entity {} and attribute {}, toAttribute would be null, could not find active attribute", entityDefinition.getApiName(), attrib.getReferencedAttributeName());
                    }
                    return reference;
                })
                .collect(Collectors.toList());
        entityDefinition.setReferences(references);
        return references;
    }

    public List<EntityDefinition> getSyncariEntities() {
        return (List<EntityDefinition>) abac.check(new AbacContext().withResourceType(ResourceType.ENTITY).withAction(Permission.READ), entityProxyRepo.findByConnectorTypeId(metaService.findByName(Constants.SYNCARI).get().getId()));
    }
    
    public List<EntityDefinition> getSyncariEntitiesWithoutAbac() {
      return entityProxyRepo.findByConnectorTypeId(metaService.findByName(Constants.SYNCARI).get().getId());
    }


    private List<EntityDefinition> doSchemaRefresh(Connector c, EntityDefinition entityDef) {
        MetadataService dataService = factory.getSchemaService(c.getMetadata());

        List<EntityDefinition> existingEntities = List.of();
        if(entityDef == null) {
        	existingEntities = getEntities(c.getId());
        } else if(entityDef.getId() == null) {
        	var entityByApiName = findEntity(c.getId(), entityDef.getApiName());
        	if(entityByApiName.isPresent()) {
        		existingEntities = List.of(entityByApiName.get());
        	}
        } else {
        	existingEntities = List.of(getEntity(entityDef.getId()));
        }
        Map<String, EntityDefinition> entityMap = new HashMap<>();
        existingEntities.forEach(e -> {
            String apiName = e.getApiName().toLowerCase();
            if (!entityMap.containsKey(apiName)) {
                entityMap.put(apiName, e);
            }
        });
        List<EntityDefinition> allEntities = new ArrayList<>();
        if (entityDef == null) {
            log.info("Starting Schema refresh for all entities on connector {}", c.getId());
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(c);
            connectorInfo.setRequiredScopes(getAdditionalScopes());
            connectorInfo.setOptionalScopes(getOptionalScopes());
            DescribeAllRequest request = new DescribeAllRequest(connectorInfo, List.of());
            request.setActiveEntities(existingEntities.stream().filter(e -> e.isActive())
                .map(e -> e.getApiName()).collect(Collectors.toList()));
            List<EntitySchema> describedAll = new ArrayList<>();
            try {
                describedAll = dataService.describeAll(request);
            } catch (Exception ex) {
                log.error("Error describing all entities.");
                log.error("ErrorMsg: " + ex.getMessage(), ex);
                throw ex;
            }
            if(describedAll == null || describedAll.isEmpty()) {
                log.warn("Synapse describe returned empty for all on synapse {}", c.getName());
                return existingEntities;
            }
            allEntities = transformer.toEntityDefinition(describedAll, c);
            Map<String, EntityDefinition> refreshedEntityMap = new HashMap<>();

            // compare existing with all entities to check if any entity is disabled
            allEntities.forEach(e -> {
                String apiName = e.getApiName().toLowerCase();
                if (!refreshedEntityMap.containsKey(apiName)) {
                    refreshedEntityMap.put(apiName, e);
                }
            });

            entityMap.keySet().forEach(e -> {
                if(!refreshedEntityMap.containsKey(e)){
                    // described entities does not contain this entity so mark it inactive
                    EntityDefinition entity = entityMap.get(e);
                    deactivateEntity(entity, c);
                }
            });

        } else {
            log.debug("Starting Schema refresh for {} on connector {}", entityDef.getApiName(), c.getName());
            DescribeRequest request = new DescribeRequest(transformer.toConnectorInfo(c), entityDef.getApiName(), Optional.of(transformer.toEntitySchema(entityDef, c)));

            // validate entity schema
            Optional<EntitySchema> entitySchema;
            try {
                entitySchema = dataService.describe(request);
            } catch (NonRetriableException ex){
                // In case of non retriable exception, deactivate this source entity and continue with the pipeline
                log.error("Error describing entity {} ErrCode: {} ErrorMsg:", entityDef.getApiName(), ex.getErrorCode(), ex.getMessage());
                throw ex;
            }
            if(!entitySchema.isPresent()) {
                log.warn("Synapse describe returned empty for {}", entityDef.getApiName());
                return handleSchemaError(existingEntities, entityDef, c);
                //return existingEntities.stream().filter(e -> e.getApiName().equalsIgnoreCase(entityDef.getApiName())).collect(Collectors.toList());
            }
            log.debug("Refreshed Schema for {} on connector {} {}", entityDef.getApiName(), c.getName(),entitySchema.map(e->e.getAttributes().size()).orElse(0));
            allEntities = transformer.toEntityDefinition(List.of(entitySchema.get()), c);
            log.debug("Transformed Schema to Entities for {} on connector {} {}", entityDef.getApiName(), c.getName(), entitySchema.map(e->e.getAttributes().size()).orElse(0));
        }
        log.debug("Doing refresh for {} entities", allEntities.size());
        List<EntityDefinition> updatedEntities = allEntities.stream().map( e -> doSchemaRefreshForEntity(c, e, entityMap)).collect(Collectors.toList());

        updatedEntities = saveAll(updatedEntities);
        log.info("Done schema refresh for connector: {}, total: {}", c.getId(), allEntities.size());
        return updatedEntities;
    }

    private List<EntityDefinition> handleSchemaError(List<EntityDefinition> existingEntities, EntityDefinition entityDef, Connector c) {
        var entity = existingEntities.stream().filter(e -> e.getApiName().equalsIgnoreCase(entityDef.getApiName())).findFirst().get();
        deactivateEntity(entity, c);
        return List.of(entity);
    }

    private EntityDefinition doSchemaRefreshForEntity(Connector c, EntityDefinition entity, Map<String, EntityDefinition> entityMap){
        EntityDefinition existing = entityMap.get(entity.getApiName().toLowerCase());
        Optional<EntityDefinition> existingDraft = getDraft(c.getId(), entity.getApiName());
        
        if (existing == null) {
            // Check for case-insensitive duplicates with APPROVED draft status ONLY
            log.debug("Checking for APPROVED duplicate entity with connectorId='{}', apiName='{}' (case-insensitive)", 
                      c.getId(), entity.getApiName());
            List<EntityDefinition> allEntitiesFound = entityProxyRepo.findEntities(c.getId(), entity.getApiName());
            List<EntityDefinition> duplicateCheck = allEntitiesFound.stream()
                .filter(e -> DraftStatus.APPROVED.equals(e.getDraftStatus()))
                .collect(Collectors.toList());
            log.debug("Found {} APPROVED duplicate entities for apiName='{}'", duplicateCheck.size(), entity.getApiName());
            
            if (CollectionUtils.isNotEmpty(duplicateCheck)) {
                // Use the existing APPROVED duplicate instead of creating new
                EntityDefinition duplicate = duplicateCheck.get(0);
                // Load attributes for the duplicate entity (findEntities doesn't include attributes)
                List<AttributeDefinition> duplicateAttrs = attributeProxyRepo.findActiveByEntityId(duplicate.getId());
                duplicate.setAttributes(duplicateAttrs);
                log.debug("Using existing APPROVED entity '{}' (id: {}) with {} attributes", 
                          duplicate.getApiName(), duplicate.getId(), duplicateAttrs != null ? duplicateAttrs.size() : 0);
                
                log.info("Found existing APPROVED entity with case-insensitive match: entityId='{}', apiName='{}' (requested: '{}'), draftStatus='{}', using existing entity", 
                         duplicate.getId(), duplicate.getApiName(), entity.getApiName(), duplicate.getDraftStatus());
                
                // Handle attribute merging from incoming entity to duplicate (similar to existing entity logic)
                if (CollectionUtils.isNotEmpty(entity.getAttributes())) {
                    List<AttributeDefinition> attribsToSave = new ArrayList<>();
                    entity.getAttributes().forEach(a -> {
                        if (!duplicate.hasField(a.getApiName())) {
                            // Add new field that doesn't exist in duplicate
                            addNewField(c, duplicate, a, DraftStatus.APPROVED);
                        } else if (!duplicate.getField(a.getApiName()).get().isSyncariDefined()) {
                            // Update existing field if it's not Syncari-defined
                            Optional<EntityDefinition> duplicateDraft = getDraft(c.getId(), duplicate.getApiName());
                            attribsToSave.addAll(updateExistingField(c, duplicate, a, duplicateDraft));
                        }
                        // Note: Syncari-defined fields are not updated to preserve system integrity
                    });
                    if (!attribsToSave.isEmpty()) {
                        attributeProxyRepo.saveAll(attribsToSave);
                    }
                    log.debug("Merged {} attributes into existing entity {}", 
                              entity.getAttributes().size(), duplicate.getId());
                }
                return duplicate;
            } else {
                // No APPROVED duplicates found, safe to create new entity
                entity.setConnectorId(c.getId());
                entity.setStatus(Status.ACTIVE);
                entity.setDraftStatus(DraftStatus.APPROVED);
                entity = entityProxyRepo.save(entity);
                log.info("Created new entity: entityId='{}', apiName='{}', connectorId='{}'", 
                         entity.getId(), entity.getApiName(), c.getId());
            }
            final String entityId = entity.getId();
            entity.getAttributes().stream().forEach(a -> {
                a.setEntityId(entityId);
                a.setStatus(Status.ACTIVE);
                a.setId(null);
                fixRefFieldLength(a);
                a.setDraftStatus(DraftStatus.APPROVED);
            });
            log.info("Added new custom object {} to connector {} schema", entity.getApiName(), c.getId());
            attributeProxyRepo.saveAll(entity.getAttributes());
            return entity;
        } else {
            // Compare all fields to see any additional field and save the delta
            List<AttributeDefinition> attribsToSave = new ArrayList<>();
            entity.getAttributes().forEach(a -> {
                existingDraft.ifPresent(existingDraftEntity ->{
                    if(!existingDraftEntity.hasField(a.getApiName())){
                        addNewField(c, existingDraftEntity, a.toBuilder().build(),DraftStatus.NEW);
                    }
                });

                if (!existing.hasField(a.getApiName())) {
                    getFieldByName(existing, a.getApiName()).ifPresentOrElse(
                            f -> {
                                if (f.getStatus() != Status.ACTIVE) {
                                    attribsToSave.addAll(updateExistingField(c, existing, f, a, existingDraft, existingDraft.flatMap(e -> getFieldByName(e, a.getApiName()))));
                                }
                            },
                            () -> {
                                addNewField(c, existing, a, DraftStatus.APPROVED);
                            }
                    );
                } else if (!existing.getField(a.getApiName()).get().isSyncariDefined()){
                    attribsToSave.addAll(updateExistingField(c, existing, a, existingDraft));
                }
            });
            attributeProxyRepo.saveAll(attribsToSave);
            if (!entity.getAttributes().isEmpty()) {
                // Compare all fields to see any deleted field and save the delta
                for (AttributeDefinition attr : existing.getAttributes()) {
                    if (!attr.isSeeded() && !attr.isSyncariDefined() && !entity.hasField(attr.getApiName())) {
                        // delete the references of deleted fields from FP
                        mappingGraphService.notifyAttributeDeletion(existing, attr, c);
                        attr.setStatus(Status.INACTIVE);
                        //attr.setApiName(format("%s_%s_%s", attr.getApiName(), attr.getId(), Status.DELETED.name()));
                        attributeProxyRepo.save(attr);
                        log.info("Marked field {} as deleted on connector {} schema", attr.getApiName(), c.getId());
                    }
                }
            } else {
                log.error("{} attributes received for entity {}. Not deleting current attributes", entity.getAttributes().size(), entity.getApiName());
            }
            existing.setStatus(Status.ACTIVE);
            if(entity.getDisplayName() != null) {
                existing.setDisplayName(entity.getDisplayName());
            }
            existing.setDescription(entity.getDescription());
            existing.setPluralName(entity.getPluralName());
            existing.setReadOnly(entity.isReadOnly());
            existing.setAdditionalProperties(entity.getAdditionalProperties());
            if(entity.getSourceParams() != null && !entity.getSourceParams().isEmpty()) {
                existing.setSourceParams(entity.getSourceParams());
            }
            if(entity.getDestinationParams() != null && !entity.getDestinationParams().isEmpty()) {
                existing.setDestinationParams(entity.getDestinationParams());
            }
            updateSynapseEntity(existing, existingDraft);
            return existing;
        }
    }

    private void addNewField(Connector c, EntityDefinition existing, AttributeDefinition a, DraftStatus draftStatus) {
        a.setEntityId(existing.getId());
        a.setDraftStatus(draftStatus);
        a.setStatus(Status.ACTIVE);
        attributeProxyRepo.save(a);
        existing.addField(a);
        fixRefFieldLength(a);
        log.info("Added new field {} to connector {} schema {}", a.getApiName(), c.getName(), existing.getApiName());
    }

    private void activateField(Connector c, EntityDefinition existing, AttributeDefinition a, DraftStatus draftStatus) {
        a.setEntityId(existing.getId());
        a.setDraftStatus(draftStatus);
        a.setStatus(Status.ACTIVE);
        attributeProxyRepo.save(a);
        existing.addField(a);
        fixRefFieldLength(a);
        log.info("Added new field {} to connector {} schema {}", a.getApiName(), c.getName(), existing.getApiName());
    }

    public EntityDefinition archiveEntity(EntityDefinition entity) {
        var draftService = getDraftService(entity);
        draftService.processArchived(entity);
        entity.setStatus(Status.DELETED);
        return entityProxyRepo.save(entity);
    }

    private EntityDefinition deactivateEntity(EntityDefinition entity, Connector c){
        // check if entity is already Inactive then no need of status change and notification
        if(!Status.INACTIVE.equals(entity.getStatus())) {
            entity.setStatus(Status.INACTIVE);
            entity = updateSynapseEntity(entity, Optional.empty());

            // if this entity is in ongoing resyncs
            resyncService.inactivateResyncForSynapseEntity(entity.getId());

            // send notification
            notificationService.broadcast(
                    format(i18n("entity_deactivated_subject"), entity.getDisplayName(), c.getName()),
                    format(i18n("entity_deactivated_body"), entity.getDisplayName(), c.getName()),
                    NotificationType.WARN);
            errorNotificationService.sendErrorNotification(ErrorCategory.PIPELINE, ErrorPriority.P1, entity.getId(),
                    format(i18n("entity_deactivated_subject"), entity.getDisplayName(), c.getName()),
                    format(i18n("entity_deactivated_body_error_notification"), entity.getDisplayName(), c.getName()));
        }

        return entity;
    }

    private Optional<AttributeDefinition> getFieldByName(EntityDefinition entityDefinition, String apiName){
        return attributeProxyRepo.findByEntityIdAndApiName(entityDefinition.getId(), apiName);
    }

    private List<AttributeDefinition> updateExistingField(Connector c, EntityDefinition existing, AttributeDefinition existingField,
                                                          AttributeDefinition a, Optional<EntityDefinition> existingDraft, Optional<AttributeDefinition> existingDraftField) {
        List<AttributeDefinition> attribsToSave = new ArrayList<>();

        SynapseInfoService synapseInfoService = factory.getSynapseService(c.getMetadata());
        List<Capability> capabilities = synapseInfoService.getCapabilities();

        boolean isDataTypeChanged = false;
        if (!a.getDataType().equals(existingField.getDataType())) {
            isDataTypeChanged = true;
            boolean isConvertible = a.getDataType().canConvert(existingField.getDataType());
            log.info("Changing datatype for field {} in entity {} for connector {} from {} to {}. IsConvertible: {}",
                    a.getApiName(), existing.getApiName(), c.getName(), existingField.getDataType().getName(), a.getDataType().getName(), isConvertible);
        }

        // If synapse got capabilities for user to edit id/wm field, then refresh should not override existing values
        if (!existingField.compare(a)) {
            boolean isApprovedWm = existingField.isWatermarkField();
            boolean isApprovedId = existingField.isIdField();
            String compositeKey = existingField.getCompositeKey();
            var dataType = existingField.getDataType();
            var picklists = existingField.getPicklist();
            var picklistsValues = existingField.getPicklistValues();
            var multivalued = existingField.isMultiValueField();
            existingField.copyValuesFrom(a);
            if (CollectionUtils.isNotEmpty(capabilities) && capabilities.contains(Capability.userEditableWm)) {
                existingField.setWatermarkField(isApprovedWm);
            }
            if (CollectionUtils.isNotEmpty(capabilities) && capabilities.contains(Capability.userEditableId)) {
                existingField.setIdField(isApprovedId);
            }
            if((Constants.IMPORTED_FILES.equalsIgnoreCase(c.getName()) || Constants.FILE_DATA.equalsIgnoreCase(c.getName()))) {
              existingField.setDataType(dataType);
              if(multivalued) {
            	existingField.setPicklist(picklists);
            	existingField.setMultiValueField(multivalued);
            	existingField.setPicklistValues(picklistsValues);
              }
            }
            existingField.setCompositeKey(compositeKey);
            existingField.setNoTimezoneWatermark(a.isNoTimezoneWatermark());
            fixRefFieldLength(existingField);
            attribsToSave.add(existingField);
        }

        if (isDataTypeChanged) {
            mappingGraphService.updateGraphOnSynapseAttributeChange(existingField);
        }

        existingDraft.ifPresent(entityDraft -> {
            // copy updated approved attribute fields to draft as well
            existingDraftField.ifPresent(attrDraft->{
                if (!attrDraft.compare(a)) {
                    boolean isWm = attrDraft.isWatermarkField();
                    boolean isId = attrDraft.isIdField();
                    String compositeKey = attrDraft.getCompositeKey();
                    attrDraft.copyValuesFrom(a);
                    if (CollectionUtils.isNotEmpty(capabilities) && capabilities.contains(Capability.userEditableWm)) {
                        attrDraft.setWatermarkField(isWm);
                    }
                    if (CollectionUtils.isNotEmpty(capabilities) && capabilities.contains(Capability.userEditableId)) {
                        attrDraft.setIdField(isId);
                    }
                    attrDraft.setCompositeKey(compositeKey);
                    attribsToSave.add(attributeProxyRepo.save(attrDraft));
                }
            });
        });
        return attribsToSave;
    }

    private List<AttributeDefinition> updateExistingField(Connector c, EntityDefinition existing, AttributeDefinition a, Optional<EntityDefinition> existingDraft) {
        return updateExistingField(c, existing, existing.getFieldByName(a.getApiName()), a, existingDraft, existingDraft.flatMap(draft -> draft.getField(a.getApiName())));
    }

    private void fixRefFieldLength(AttributeDefinition approvedField) {
        if(approvedField.isReference()){
            approvedField.setLength(Math.max(approvedField.getLength(),SYNCARI_ID_FIELD_LENGTH));
        }
    }

    private Map<String, List<AttributeDefinition>> getEntityIdToAttribMap(List<EntityDefinition> entities) {
        Map<String, List<AttributeDefinition>> entityToAttrMap = new HashMap<>();
        List<String> entityIds = new ArrayList<>();
        entities.stream().forEach(e -> {
            entityIds.add(e.getId());
            entityToAttrMap.putIfAbsent(e.getId(), new ArrayList<>());
        });
        List<AttributeDefinition> attrs = attributeProxyRepo.findActiveByEntityIds(entityIds);
        attrs.forEach(a -> {
            entityToAttrMap.get(a.getEntityId()).add(a);
        });
        return entityToAttrMap;
    }

    private Map<String, AttributeDefinition> createNameToDefMap(EntityDefinition entity) {
        entity = getEntity(entity.getId());
        Map<String, AttributeDefinition> map = new HashMap<>();
        entity.getAttributes().forEach(e -> map.put(e.getApiName().toLowerCase(), e));
        return map;
    }

    protected EntityDefinition createEntityFor(EntityDefinition sourceEntity, Connector synapse, DraftStatus draftStatus) {
        //validateEntityAndAttributes(sourceEntity);
        String apiName = populateApiName(sourceEntity, synapse);
        String datastoreName = SchemaHelper.curatedDataStoreName(apiName);
        EntityDefinition newEntity = new EntityDefinition()
                .setApiName(apiName)
                .setPluralName(sourceEntity.getPluralName())
                .setDisplayName(sourceEntity.getDisplayName())
                .setConnectorId(synapse.getId())
                .setConnectorTypeId(synapse.getMetadataId())
                .setChild(sourceEntity.isChild())
                .setCustom(sourceEntity.isCustom())
                .setSyncariSource(sourceEntity.isSyncariSource())
                .setDataStoreName(datastoreName)
                .setAdditionalProperties(sourceEntity.getAdditionalProperties());
        newEntity.setDraftStatus(draftStatus);
        newEntity.setStatus(Status.ACTIVE);
        newEntity = entityProxyRepo.save(newEntity);
        final String entityId = newEntity.getId();
        List<AttributeDefinition> attributesToCreate = sourceEntity.getAttributes();
        List<AttributeDefinition> clonedAttrList = new ArrayList<>();
        Set<String> apiNames = new HashSet<>();
        attributesToCreate.stream().forEach(a -> {
            Optional<EntityDefinition> syncariChildSchema = Optional.empty();
            if(a.isChild()){
                Optional<EntityDefinition> childSchema = findChildEntity(sourceEntity.getConnectorId(),a.getReferenceTo());
                syncariChildSchema =childSchema.map(child->createEntityFor(child,synapse,draftStatus));
            }
            var cloned = a.withEntityId(entityId);
            cloned.setUpdatable(true);
            cloned.setSystem(false);
            syncariChildSchema.ifPresent(c->{
                cloned.setReferenceTo(c.getApiName());
            });
            if (cloned.isReference()){
                Optional<EntityDefinition> referencedSchema = findEntity(sourceEntity.getConnectorId(),cloned.getReferenceTo());
                referencedSchema.ifPresent(refschema -> {
                    log.debug("Referenced schema entity id {} and apiName {} with connectorId {}", refschema.getId(), refschema.getApiName(), refschema.getConnectorId());
                    List<MappingGraph> entityGraphsWithSourceOrSink = mappingGraphService.findEntityGraphsWithSourceOrSink(refschema.getId());
                    entityGraphsWithSourceOrSink.stream().findFirst().ifPresent(graph -> {
                        cloned.setReferenceTo(graph.getCoreNode().getApiName());
                    });
                    refschema.getIdField().ifPresent(idf -> {
                        cloned.setReferenceTargetField(idf.getApiName());
                    });
                });
            }
            String sanitizedApiName = sanitizedFieldName(TextUtil.createApiNameWOLowercase(a.getApiName()));
            while(apiNames.contains(sanitizedApiName)){
                sanitizedApiName = populateApiNameWithCounter(sanitizedApiName);
            }
            apiNames.add(sanitizedApiName);
            cloned.setApiName(sanitizedApiName);
            cloned.setDataStoreName(SchemaHelper.curatedDataStoreName(sanitizedApiName));
            cloned.setId(null);
            cloned.setDraftStatus(draftStatus);
            setIdAndWatermarkFieldReadOnlynMand(cloned);
            fixRefFieldLength(cloned);
            clonedAttrList.add(cloned);
        });
        List<AttributeDefinition> savedAttributes = attributeProxyRepo.saveAll(clonedAttrList);
        newEntity.setAttributes(savedAttributes);
        log.info("Entity {} added successfully to synapse {} with {} fields", apiName, synapse.getName(),
                sourceEntity.getAttributes().size());
        return newEntity;
    }

    private void setIdAndWatermarkFieldReadOnlynMand(AttributeDefinition attributeDef){
        if ((null != attributeDef) && (attributeDef.isWatermarkField() || attributeDef.isIdField())){
            attributeDef.setNillable(false);
            attributeDef.setUpdatable(false);
            attributeDef.setSystem(true);
        }
    }

    public Optional<EntityDefinition> findChildEntity(String connectorId, String apiName) {
        return entityProxyRepo.findChildEntityByConnectorIdAndApiName(connectorId, apiName)
                .map(entity-> entity.setAttributes(attributeProxyRepo.findActiveByEntityId(entity.getId())));


    }

    protected String populateApiName(EntityDefinition sourceEntity, Connector syncariConnector) {
        String apiName = TextUtil.createApiNameWOLowercase(sourceEntity.getApiName());
        List<EntityDefinition> existing = entityProxyRepo.findEntities(syncariConnector.getId(),
                apiName);
        // Also check if a dataset with the same name exists to avoid name collision
        Optional<Dataset> datasetWithSameName = datasetService.findDatasetByName(apiName);

        while (CollectionUtils.isNotEmpty(existing) || datasetWithSameName.isPresent()){
            apiName = populateApiNameWithCounter(apiName);
            existing = entityProxyRepo.findEntities(syncariConnector.getId(),
                    apiName);
            datasetWithSameName = datasetService.findDatasetByName(apiName);
        }
        log.info("For synapse entity api name {} chosen syncari entity api name is {}", sourceEntity.getApiName(), apiName);
        return apiName;
    }

    public String populateApiNameWithCounter(String apiName){
        if (apiName.matches(".*\\d$")){
            Pattern p = Pattern.compile("\\d+$");
            Matcher m = p.matcher(apiName);
            if (m.find()) {
                String someNumberStr = m.group();
                int lastNumberInt = Integer.parseInt(someNumberStr);
                int indexOfNumber = apiName.indexOf(""+lastNumberInt);
                String toBeApiName = apiName.substring(0,indexOfNumber);
                log.info("Last number of end of api name is {}",lastNumberInt);
                return toBeApiName.concat(""+ ++lastNumberInt);
            }else{
                throw new RuntimeException(
                        format("Tried creating Syncari entity with name %s, but it already exists", apiName));
            }
        }else if (apiName.endsWith("__c")){
            return apiName.concat("1");
        }
        return apiName.concat("__c");
    }

    private String getUserName(String id, Map<String, String> userNamesById) {
        try {
            if (!userNamesById.containsKey(id)) {
                User userById = userService.getUserById(id);
                userNamesById.put(id, userById.getFirstName()+" "+userById.getLastName());
            }
            return userNamesById.getOrDefault(id, "");
        } catch (Exception e) {
        }
        return id;
    }

    protected void validateEntityAndAttributes(EntityDefinition sourceEntity) {
        sourceEntity.getAttributes().forEach(attribute -> {
            if (!attribute.isSystem()) {
                Set lowerCasedSyncariDefinedFields = EntityData.SYNCARI_DEFINED_FIELDS.stream().map(x -> x.toLowerCase()).collect(Collectors.toSet());
                validateCondition(lowerCasedSyncariDefinedFields.contains(sanitizedFieldName(attribute.getApiName()).toLowerCase()),
                        i18n("attribute_with_apiname_is_syncari_defined"), attribute.getApiName(), sourceEntity.getApiName());
            }
        });
    }

    protected void validateDraftAttribute(EntityDefinition entity, AttributeDefinition attribute){
        validateCondition(!entity.isDraft(), i18n("new_attribute_in_draft_entity"), entity.getApiName());
        validateCondition(!attribute.isDraft(), i18n("attribute_upsert_failed_no_draft"), attribute.getApiName());
        var existingFieldBySameDatastoreName = entity.getFieldByDatastoreName(attribute.getDataStoreName());
        existingFieldBySameDatastoreName.ifPresent(f -> {
            validateCondition((StringUtils.isEmpty(attribute.getId()) || !f.getId().equalsIgnoreCase(attribute.getId())) &&
                            f.getDataStoreName().equalsIgnoreCase(attribute.getDataStoreName()),
                    i18n("attribute_with_datastorename_exists_in_entity"), f.getDataStoreName(), entity.getApiName());
        });
        Connector connector = connectorService.find(entity.getConnectorId()).get();

        validateCondition(attribute.isIdField() && attribute.isWatermarkField(),
                i18n("attribute_id_watermark_error"));
        if (attribute.isIdField()) {
            if (!connector.getMetadata().supportsCapability(Capability.userEditableId)){
                validateCondition(attribute.isNillable() || !attribute.isUnique(),
                        i18n("attribute_idfield_required_unique_error"));
            }
            var existingIdField = entity.getIdField();
            existingIdField.ifPresent(f -> {
                validateCondition(!f.getApiName().equalsIgnoreCase(attribute.getApiName()),
                        i18n("attribute_idfield_duplicate_error"), f.getApiName());
            });

        } else if (attribute.isWatermarkField()) {
            if (!connector.getMetadata().supportsCapability(Capability.userEditableWm)){
                validateCondition(attribute.isNillable() || attribute.isUpdatable(),
                        i18n("attribute_watermarkfield_required_readonly_error"));
            }
            var existingWatermarkField = entity.getWatermarkField();
            existingWatermarkField.ifPresent(f -> {
                validateCondition(!f.getApiName().equalsIgnoreCase(attribute.getApiName()),
                        i18n("attribute_watermarkfield_duplicate_error"), f.getApiName());
            });
        } else if (!connector.isSyncariConnector())  {
            validateCondition(!attribute.isSyncariDefined(), i18n("attribute_upsert_error_non_syncari_defined"));

            if(!StringUtils.isBlank(attribute.getParentAttributeId())){
                var parent = getAttribute(attribute.getParentAttributeId());
                validateCondition(!entity.hasField(parent.getApiName()),
                        i18n("parent_attribute_not_in_entity_error"), parent.getId(), entity.getDisplayName());
            }
        }
    }

    public List<AttributeDefinition> getDefaultAttributes(EntityDefinition entity){
        AttributeDefinition idField = new AttributeDefinition().setApiName("Id").setDisplayName(entity.getDisplayName()+" Id")
                .setDataType(new IdType()).setSystem(true).setIdField(true).setNillable(false).setUnique(true);

        AttributeDefinition updatedAtField = new AttributeDefinition().setApiName("LastModifiedDate").setDisplayName("Last Modified Date")
                .setDataType(new DatetimeType()).setSystem(true).setWatermarkField(true).setNillable(false)
                .setUpdatedAtField(true).setUpdatable(false);

        AttributeDefinition createdAtField = new AttributeDefinition().setApiName("CreatedDate").setDisplayName("Created Date")
                .setDataType(new DatetimeType()).setSystem(true).setNillable(false).setCreatedAtField(true)
                .setUpdatable(false);

        return List.of(idField, updatedAtField, createdAtField);
    }

    public EntityDefinition getSourceEntityWithMappedAndSystemFields(String syncariEntityName, String sourceEntityId, boolean usePublishedGraph){
        EntityDefinition entity = getEntity(sourceEntityId);
        EntityDefinition syncariEntity = getSyncariEntityByName(syncariEntityName).orElseThrow(() -> new NotFoundException(EntityDefinition.class, "apiName", syncariEntityName));
        Optional<MappingGraph> entityGraphMaybe = usePublishedGraph
                ? mappingGraphService.retrieveApprovedEntityGraph(syncariEntity.getId())
                : mappingGraphService.retrieveDraftEntityGraph(syncariEntity.getId());
        MappingGraph entityGraph = entityGraphMaybe.orElseThrow(() -> new NotFoundException(MappingGraph.class, "targetId", syncariEntity.getId()));
        return getSourceEntityWithMappedAndSystemFields(syncariEntity, entity, entityGraph);
    }

    public EntityDefinition getSourceEntityWithMappedAndSystemFields(EntityDefinition syncariEntity, EntityDefinition sourceEntity, MappingGraph entityGraph){
        EntityDefinition entity = sourceEntity.makeCopy();
        List<AttributeDefinition> mappedAttributes = mappingGraphService.getMappedAndFilterAttributes(sourceEntity, syncariEntity, entityGraph);
        List<AttributeDefinition> systemAttributes = sourceEntity.getAttributes().stream().filter(a -> a.isSystem()).collect(Collectors.toList());
        Set<AttributeDefinition> attributesToRetrieve = new HashSet<AttributeDefinition>(mappedAttributes);
        attributesToRetrieve.addAll(systemAttributes);
        // add an id field if it does not already exist
        sourceEntity.getIdField().ifPresent(idField -> attributesToRetrieve.add(idField));
        entity.setAttributes(new ArrayList<>(attributesToRetrieve));
        log.debug("Finished getSourceEntityWithMappedAndSystemFields for source entity {}" , sourceEntity.getApiName());
        return entity;
    }

    public EntityDefinition getEntityWithSystemFields(String entityId){
        EntityDefinition entity = getEntity(entityId);
        List<AttributeDefinition> systemAttributes = entity.getAttributes().stream().filter(a -> a.isSystem()).collect(Collectors.toList());
        entity.setAttributes(systemAttributes);
        return entity;
    }

    public void upsertEntity(EntityDefinition entity){
        entityProxyRepo.save(entity);
        attributeProxyRepo.saveAll(entity.getAttributes());
    }

    public void upsertField(AttributeDefinition field){
        attributeProxyRepo.save(field);
    }

    private EntityDefinition updateSynapseEntity(EntityDefinition entity, Optional<EntityDefinition> existingDraft){
        existingDraft.ifPresent(draft -> {
            // copy updated approved entity fields to draft as well
            draft.copyValuesFrom(entity);
            entityProxyRepo.save(draft);
        });
        return entityProxyRepo.save(entity);
    }

    public List<EntityDefinition> saveAll(List<EntityDefinition> entities) {
        return entities.stream().map(e -> save(e)).collect(Collectors.toList());
    }

    public EntityDefinition save(EntityDefinition entity) {
        return entityProxyRepo.save(entity);
    }


    public EntityDefinition createDatasetAsSourceSchema(String datasetId, String apiName, String displayName, List<AttributeDefinition> attributeDefinitions, List<AttributeDefinition> sourceParams, boolean readOnly) {
        Optional<Connector> connectorOpt = connectorService.getDatasetConnector();
        if(connectorOpt.isPresent()) {
            Connector connector = connectorOpt.get();
            Optional<EntityDefinition> existingEntityDefinition =  this.findEntity(connector.getId(), apiName);
            if (!existingEntityDefinition.isPresent()){
                EntityDefinition entityDefinition = new EntityDefinition(apiName, displayName);
                entityDefinition.setReadOnly(readOnly);
                entityDefinition.setDraftStatus(DraftStatus.APPROVED);
                entityDefinition.setConnectorId(connector.getId());
                entityDefinition.setConnectorTypeId(connector.getMetadataId());
                entityDefinition.setStatus(Status.ACTIVE);
                entityDefinition.setAdditionalProperties(constructAdditionalProperties(entityDefinition.getAdditionalProperties(), datasetId));
                if (sourceParams != null && !sourceParams.isEmpty()) {
                    entityDefinition.setSourceParams(sourceParams);
                }
                EntityDefinition saved = entityProxyRepo.save(entityDefinition);
                attributeDefinitions.forEach(attributeDefinition -> {
                    attributeDefinition.setEntityId(saved.getId());
                });
                attributeProxyRepo.saveAll(attributeDefinitions);

                return saved;
            }else{
                return existingEntityDefinition.get();
            }
        } else {
            throw new RuntimeException("Failed to fetch Dataset Connector");
        }
    }

    public EntityDefinition updateDatasetAsSourceSchema(String apiName, String displayName, List<AttributeDefinition> attributeDefinitions,
                                                        List<AttributeDefinition> sourceParams, boolean readOnly, String entityDefinitionId, String datasetId) {
        Optional<Connector> connectorOpt = connectorService.getDatasetConnector();
        if (connectorOpt.isPresent()) {
            Connector connector = connectorOpt.get();
            Optional<EntityDefinition> entityDefinitionOpt = StringUtils.isNotEmpty(entityDefinitionId) ? entityProxyRepo.findById(entityDefinitionId) : Optional.empty();
            if (entityDefinitionOpt.isPresent()) {
                EntityDefinition entityDefinition = entityDefinitionOpt.get();
                entityDefinition.setApiName(apiName);
                entityDefinition.setDisplayName(displayName);
                entityDefinition.setSyncariSource(true);
                entityDefinition.setReadOnly(readOnly);
                entityDefinition.setDraftStatus(DraftStatus.APPROVED);
                entityDefinition.setConnectorId(connector.getId());
                entityDefinition.setConnectorTypeId(connector.getMetadataId());
                entityDefinition.setStatus(Status.ACTIVE);
                entityDefinition.setAdditionalProperties(constructAdditionalProperties(entityDefinition.getAdditionalProperties(), datasetId));
                if (sourceParams != null && !sourceParams.isEmpty()) {
                    entityDefinition.setSourceParams(sourceParams);
                }
                entityProxyRepo.save(entityDefinition);
                Map<String, AttributeDefinition> newAttributesMap = attributeDefinitions.stream().collect(Collectors.toMap(AttributeDefinition::getApiName, x -> x));
                Map<String, AttributeDefinition> existingAttributesMap = attributeProxyRepo.findByEntityId(entityDefinition.getId()).stream().collect(Collectors.toMap(AttributeDefinition::getApiName, x -> x));
                List<AttributeDefinition> toSave = new ArrayList<>();
                newAttributesMap.forEach((newApiName, newAttrDef) -> {
                    if(existingAttributesMap.containsKey(newApiName)) {
                        existingAttributesMap.get(newApiName).setDataType(newAttributesMap.get(newApiName).getDataType());
                        toSave.add(existingAttributesMap.get(newApiName));
                    } else {
                        newAttrDef.setEntityId(entityDefinitionId);
                        toSave.add(newAttrDef);
                    }
                });
                existingAttributesMap.forEach((existingApiName, existingAttrDef) -> {
                    if(!newAttributesMap.containsKey(existingApiName)) {
                        // delete the references of deleted fields from FP
                        mappingGraphService.notifyAttributeDeletion(entityDefinition, existingAttrDef, connector);
                        existingAttrDef.setStatus(Status.DELETED);
                        existingAttrDef.setDraftStatus(DraftStatus.ARCHIVED);
                        existingAttrDef.setApiName(format("%s_%s_%s", existingAttrDef.getApiName(), existingAttrDef.getId(), Status.DELETED.name()));
                        toSave.add(existingAttrDef);
                    }
                });
                attributeProxyRepo.saveAll(toSave);

                return entityDefinition;
            } else {
                throw new RuntimeException("EntityDefinition " + entityDefinitionId + " not found");
            }
        } else {
            throw new RuntimeException("Failed to fetch Dataset Connector");
        }
    }

    private Map<String, Object> constructAdditionalProperties(Map<String, Object> existingAdditionalProperties, String datasetId) {
        Map<String, Object> additionalProperties = existingAdditionalProperties;
        if (existingAdditionalProperties == null) {
            additionalProperties = new HashMap<>();
        }
        if (!additionalProperties.containsKey("datasetId")) {
            additionalProperties.put("datasetId", datasetId);
        }
        return additionalProperties;
    }

    public void deleteDatasetAsSourceSchema(String entityDefinitionId) {
        Optional<Connector> connectorOpt = connectorService.getDatasetConnector();
        if (connectorOpt.isPresent()) {
            Connector connector = connectorOpt.get();
            Optional<EntityDefinition> entityDefinitionOpt = entityProxyRepo.findById(entityDefinitionId);
            if (entityDefinitionOpt.isPresent()) {
                List<AttributeDefinition> attrs = attributeProxyRepo.findByEntityId(entityDefinitionId);
                attrs.forEach(attributeDefinition -> {
                    mappingGraphService.notifyAttributeDeletion(entityDefinitionOpt.get(), attributeDefinition, connector);
                });
                attributeProxyRepo.deleteAll(attrs);
                entityProxyRepo.delete(entityDefinitionOpt.get());
            }
        } else {
            throw new RuntimeException("Failed to fetch Dataset Connector");
        }
    }
    
    /**
     * Detect field type changes and trigger async migration for Syncari entities.
     * Called during draft approval process to handle data type migrations.
     */
    /**
     * Capture field type changes BEFORE approval process begins.
     * This is critical because after approval, both old and new data types become the same.
     */
    private List<FieldTypeChange> captureFieldTypeChanges(List<AttributeDefinition> draftAttributes, String approvedEntityId) {
        List<FieldTypeChange> changes = new ArrayList<>();
        
        for (AttributeDefinition draftAttr : draftAttributes) {
            // Check if there's a parent ID (indicating this is updating an existing attribute)
            if (draftAttr.getParentId() != null) {
                Optional<AttributeDefinition> prevApprovedOpt = attributeProxyRepo.findById(draftAttr.getParentId());
                
                if (prevApprovedOpt.isPresent()) {
                    AttributeDefinition prevApproved = prevApprovedOpt.get();
                    String oldDataType = prevApproved.getDataType().getName();
                    String newDataType = draftAttr.getDataType().getName();
                    
                    // Check if data type has changed
                    if (!oldDataType.equals(newDataType)) {
                        log.info("Field type change detected before approval: entity={}, field={}, {}→{}", 
                            approvedEntityId, draftAttr.getApiName(), oldDataType, newDataType);
                        
                        changes.add(new FieldTypeChange(
                            approvedEntityId,
                            draftAttr.getId(),
                            draftAttr.getApiName(), 
                            oldDataType,
                            newDataType
                        ));
                    }
                }
            }
        }
        
        return changes;
    }
    
    /**
     * Trigger field type migrations using the captured changes.
     * Called AFTER successful approval. Sends email notification instead of queuing for manual migration.
     */
    private void triggerFieldTypeMigrations(List<FieldTypeChange> fieldTypeChanges) {
        for (FieldTypeChange change : fieldTypeChanges) {
            log.info("Field type migration required: entity={}, field={}, {}→{}",
                change.entityId, change.fieldName, change.oldDataType, change.newDataType);

            try {
                // COMMENTED OUT: Auto queue publishing - migration should be done manually
                /*
                // Create event with migration details and publish to PubSub
                Event event = new Event()
                    .setType(EventTypes.MIGRATE_FIELD_TYPE)
                    .setLoggedTime(new Date())
                    .setDetails(Map.of(
                        "entityId", change.entityId,
                        "attributeId", change.attributeId,
                        "fieldName", change.fieldName,
                        "oldDataType", change.oldDataType,
                        "newDataType", change.newDataType
                    ));

                // Publish to generic PubSub queue - SyncariContext will be set automatically
                publisher.publishToGenericQueue(event);

                log.info("Successfully queued field type migration event for entity={}, field={}",
                    change.entityId, change.fieldName);
                */

            } catch (Exception e) {
                log.error("Failed to process field type migration for entity={}, field={}: {}",
                    change.entityId, change.fieldName, e.getMessage(), e);
                // Don't throw exception to avoid blocking schema approval for migration failures
            }
        }

        // Send email notification to dev team for manual migration
        if (!fieldTypeChanges.isEmpty()) {
            sendFieldTypeMigrationNotification(fieldTypeChanges);
        }
    }

    /**
     * Send email notification to development team about field type migrations that need manual intervention.
     */
    private void sendFieldTypeMigrationNotification(List<FieldTypeChange> fieldTypeChanges) {
        try {
            String subject = String.format(i18n("field_type_migration_subject"), SyncariContext.getSyncariId(), fieldTypeChanges.size());

            StringBuilder changeDetails = new StringBuilder();
            for (FieldTypeChange change : fieldTypeChanges) {
                changeDetails.append(String.format("Entity ID: %s\n", change.entityId));
                changeDetails.append(String.format("Attribute ID: %s\n", change.attributeId));
                changeDetails.append(String.format("Field Name: %s\n", change.fieldName));
                changeDetails.append(String.format("Old Data Type: %s\n", change.oldDataType));
                changeDetails.append(String.format("New Data Type: %s\n", change.newDataType));
                changeDetails.append("---\n");
            }

            String body = String.format(i18n("field_type_migration_body"),
                changeDetails.toString(),
                SyncariContext.getSyncariId());

            // Send to development team using configured error email addresses
            emailService.sendText(appConfig.getErrorEmail(), subject, body);

            log.info("Sent field type migration notification email for {} changes", fieldTypeChanges.size());

        } catch (Exception e) {
            log.error("Failed to send field type migration notification email: {}", e.getMessage(), e);
        }
    }

    /**
     * Inner class to hold field type change information captured before approval.
     * Immutable data class to safely pass field type change information across async boundaries.
     */
    private static class FieldTypeChange {
        final String entityId;
        final String attributeId;
        final String fieldName;
        final String oldDataType;
        final String newDataType;
        
        FieldTypeChange(String entityId, String attributeId, String fieldName, String oldDataType, String newDataType) {
            // Validate required parameters to prevent null pointer issues in async execution
            this.entityId = entityId != null ? entityId : "";
            this.attributeId = attributeId != null ? attributeId : "";
            this.fieldName = fieldName != null ? fieldName : "";
            this.oldDataType = oldDataType != null ? oldDataType : "";
            this.newDataType = newDataType != null ? newDataType : "";
        }
        
        @Override
        public String toString() {
            return String.format("FieldTypeChange{entity='%s', field='%s', %s→%s}", 
                entityId, fieldName, oldDataType, newDataType);
        }
    }
}
