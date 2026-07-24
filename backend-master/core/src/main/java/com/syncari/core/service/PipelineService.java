package com.syncari.core.service;

import com.syncari.connector.data.*;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.core.DataTransformer;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.SchemaMappingRepo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PipelineService {

    @Autowired
    private MappingGraphService mappingGraphService;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private ResyncService resyncService;

    @Autowired
    private DataServiceFactory dataServiceFactory;

    @Autowired
    private EntityDefinitionRepo entityProxyRepo;

    @Autowired
    private AttributeRepo attributeProxyRepo;

    @Autowired
    private DataTransformer transformer;

    @Autowired
    private LayoutService layoutService;

    @Autowired
    private SchemaMappingRepo schemaMappingRepo;

    /**
     * Result object for extract-load pipeline creation
     */
    @Data
    public static class ExtractLoadResult {
        private String pipelineId;
        private String pipelineName;
        private String sourceEntityId;
        private String sourceEntityName;
        private String syncariEntityId;
        private String syncariEntityName;
        private String destinationEntityId;
        private String destinationEntityName;
        private int fieldsMapped;
        private int fieldsCreated;
        private List<MappedFieldInfo> mappedFields;
        private boolean published;
        private boolean resyncStarted;
        private String resyncId;
        private boolean destEntityCreated;
        private boolean autoSchemaSyncEnabled;

        public ExtractLoadResult() {
        }
    }

    /**
     * Info about a mapped field
     */
    @Data
    public static class MappedFieldInfo {
        private String sourceFieldId;
        private String sourceFieldName;
        private String sourceFieldApiName;
        private String syncariFieldId;
        private String syncariFieldName;
        private String syncariFieldApiName;
        private String destinationFieldId;
        private String destinationFieldName;
        private String destinationFieldApiName;
        private boolean newlyCreated;

        // Getters and setters
        public MappedFieldInfo() {
        }
    }

    /**
     * Creates a simple extract-load pipeline: Source Entity → Syncari Entity → Destination Entity
     * <p>
     * Simplified 6-step flow:
     * 1. Create destination entity/fields by looking at source fields
     * 2. Get or create syncari entity (with fields matching source)
     * 3. Get or create entity pipeline (source -> syncari -> dest)
     * 4. Build field mapping: source field -> syncari field -> dest field
     * 5. For each source field, check if field pipeline exists with source and dest
     * 6. If no pipeline exists, create it. If exists but no dest, add dest node
     *
     * @param sourceEntityId          Source entity ID
     * @param destinationEntityId     Destination entity ID (optional if createDestinationEntity=true)
     * @param destinationConnectorId  Destination connector ID
     * @param destinationEntityName   Name for new destination entity (required if createDestinationEntity=true)
     * @param createDestinationEntity Whether to create destination entity if not provided
     * @param syncariEntityName       Name for the Syncari entity
     * @param pipelineName            Name for the pipeline
     * @param publish                 Whether to publish the pipeline
     * @param startResync             Whether to start a resync after publishing
     * @param autoSchemaSync          Whether to enable auto schema sync for future field additions
     * @return ExtractLoadResult with pipeline details and field mappings
     */
    public ExtractLoadResult createExtractLoadPipeline(
            String sourceEntityId,
            String destinationEntityId,
            String destinationConnectorId,
            String destinationEntityName,
            Boolean createDestinationEntity,
            String syncariEntityName,
            String pipelineName,
            Boolean publish,
            Boolean startResync,
            Boolean autoSchemaSync) {

        log.info("Extract-Load request: sourceEntityId={}, destConnectorId={}, syncariEntityName={}",
                sourceEntityId, destinationConnectorId, syncariEntityName);

        // Validate inputs
        if (Boolean.TRUE.equals(createDestinationEntity) && StringUtils.isBlank(destinationEntityName)) {
            throw new SyncariValidationException("destinationEntityName is required when createDestinationEntity=true");
        }
        if (Boolean.TRUE.equals(startResync) && Boolean.FALSE.equals(publish)) {
            throw new SyncariValidationException("Cannot start resync on a draft pipeline. Set publish=true");
        }

        // Fetch source entity
        EntityDefinition sourceEntity;
        try {
            sourceEntity = schemaService.getEntity(sourceEntityId);
            if (sourceEntity == null) {
                throw new NotFoundException("Source entity not found: " + sourceEntityId);
            }
        } catch (RuntimeException e) {
            throw new NotFoundException("Source entity not found: " + sourceEntityId);
        }

        // Validate source entity has watermark field (unless connector supports no watermark)
        boolean supportsNoWatermark = connectorService.supportsNoWatermark(sourceEntity.getConnectorId());
        if (!supportsNoWatermark && sourceEntity.getWatermarkField().isEmpty()) {
            throw new SyncariValidationException(
                    "Watermark field not defined for " + sourceEntity.getDisplayName() +
                            ". Please configure a watermark field for the source entity before creating a pipeline.");
        }

        // Fetch destination connector
        Connector destConnector = connectorService.find(destinationConnectorId)
                .orElseThrow(() -> new NotFoundException("Destination connector not found: " + destinationConnectorId));

        // ============================================================
        // STEP 1: Create destination entity/fields by looking at SOURCE
        // ============================================================
        EntityDefinition destEntity;
        boolean destEntityCreated = false;
        int fieldsCreated;

        if (StringUtils.isNotBlank(destinationEntityId)) {
            destEntity = schemaService.getEntity(destinationEntityId);
            if (destEntity == null) {
                throw new NotFoundException("Destination entity not found: " + destinationEntityId);
            }
        } else if (Boolean.TRUE.equals(createDestinationEntity)) {
            destEntity = createDestinationEntity(destConnector, destinationEntityName);
            destEntityCreated = true;
        } else {
            throw new SyncariValidationException("Either destinationEntityId or createDestinationEntity=true must be provided");
        }

        // Track existing destination field names before creating new ones
        Set<String> existingDestFieldNames = destEntity.getActiveAttributes() != null
                ? destEntity.getActiveAttributes().stream().map(AttributeDefinition::getApiName).collect(Collectors.toSet())
                : new HashSet<>();

        // Create missing destination fields based on SOURCE fields
        fieldsCreated = ensureDestinationFieldsExist(sourceEntity, destEntity, destConnector);
        if (fieldsCreated > 0) {
            destEntity = schemaService.getEntity(destEntity.getId());
        }

        // ============================================================
        // STEP 2: Get or create syncari entity (with fields matching source)
        // ============================================================
        EntityDefinition syncariEntity = getOrCreateSyncariEntity(syncariEntityName, sourceEntity);
        syncariEntity = schemaService.getEntity(syncariEntity.getId());

        // Track existing syncari field names before creating new ones
        Set<String> existingSyncariFieldNames = syncariEntity.getActiveAttributes() != null
                ? syncariEntity.getActiveAttributes().stream().map(AttributeDefinition::getApiName).collect(Collectors.toSet())
                : new HashSet<>();

        // Ensure all source fields exist in syncari entity
        int syncariFieldsCreated = ensureSyncariFieldsExist(sourceEntity, syncariEntity);
        if (syncariFieldsCreated > 0) {
            syncariEntity = schemaService.getEntity(syncariEntity.getId());
        }

        // Collect newly created field names (fields not in original sets)
        Set<String> newlyCreatedFieldNames = new HashSet<>();
        if (destEntity.getActiveAttributes() != null) {
            destEntity.getActiveAttributes().stream()
                    .map(AttributeDefinition::getApiName)
                    .filter(name -> !existingDestFieldNames.contains(name))
                    .forEach(newlyCreatedFieldNames::add);
        }
        if (syncariEntity.getActiveAttributes() != null) {
            syncariEntity.getActiveAttributes().stream()
                    .map(AttributeDefinition::getApiName)
                    .filter(name -> !existingSyncariFieldNames.contains(name))
                    .forEach(newlyCreatedFieldNames::add);
        }
        String syncariEntityId = syncariEntity.getId();

        // ============================================================
        // STEP 3: Get or create entity pipeline (source -> syncari -> dest)
        // ============================================================
        MappingGraph pipeline = getOrCreatePipeline(syncariEntity, pipelineName);
        List<Layout> layoutsToSave = new ArrayList<>();

        if (!pipeline.hasSource(sourceEntity.getId())) {
            addSourceEntityNode(pipeline, sourceEntity, layoutsToSave);
        }
        if (!pipeline.hasSink(destEntity.getId())) {
            addDestEntityNode(pipeline, destEntity, layoutsToSave);
        }

        // ============================================================
        // STEP 4: Build field mapping (source -> syncari -> dest) by apiName
        // ============================================================
        List<FieldMapping> fieldMappings = new ArrayList<>();
        for (AttributeDefinition sourceField : sourceEntity.getActiveAttributes()) {
            String apiName = sourceField.getApiName();
            AttributeDefinition syncariField = findFieldByName(syncariEntity, apiName);
            AttributeDefinition destField = findFieldByName(destEntity, apiName);

            if (syncariField != null) {
                fieldMappings.add(new FieldMapping(sourceField, syncariField, destField));
            }
        }

        // ============================================================
        // STEPS 5-6: For each mapping, ensure field pipeline has source and dest
        // ============================================================
        ensureFieldPipelinesExist(fieldMappings, layoutsToSave);

        // Save entity pipeline and layouts
        mappingGraphService.upsertEntityGraph(pipeline);
        if (!layoutsToSave.isEmpty()) {
            layoutService.upsert(layoutsToSave);
        }

        // Validate the pipeline before publishing
        mappingGraphService.validateGraph(pipeline.getId());

        // Publish if requested
        if (Boolean.TRUE.equals(publish)) {
            // Use approveDraft to properly approve the entity pipeline and all child attribute pipelines
            pipeline = mappingGraphService.approveDraft(pipeline, false, false);
        }

        // Start resync if requested
        String resyncId = null;
        boolean resyncStarted = false;
        if (Boolean.TRUE.equals(startResync) && Boolean.TRUE.equals(publish)) {
            try {
                // Use epoch as start time (full resync) and current time as end time
                Instant startTime = Instant.EPOCH;
                Instant endTime = Instant.now();
                ResyncDetail resyncDetail = resyncService.createResyncRequest(
                        syncariEntity.getId(),
                        Collections.singletonList(sourceEntity.getId()),
                        startTime, endTime
                );
                resyncId = resyncDetail.getId();
                resyncStarted = true;
            } catch (Exception e) {
                log.error("Failed to start resync", e);
            }
        }

        // Set up auto schema sync if requested
        boolean autoSchemaSyncEnabled = false;
        if (Boolean.TRUE.equals(autoSchemaSync)) {
            // Create SchemaMapping entries for all field pipelines
            createSchemaMappings(fieldMappings, sourceEntity, destEntity);

            // Check if setting already exists for this source entity
            List<ConnectorSchemaSetting> existingSettings = connectorService.getSetting(sourceEntity.getConnectorId());
            Optional<ConnectorSchemaSetting> existingSetting = existingSettings.stream()
                    .filter(s -> sourceEntity.getId().equals(s.getFromEntityId()) && sourceEntityId.equals(s.getSyncariEntityId()))
                    .findFirst();

            if (existingSetting.isPresent()) {
                // Update existing setting to include new destination if not already present
                ConnectorSchemaSetting setting = existingSetting.get();
                List<String> toEntityIds = new ArrayList<>(setting.getToEntityIds());
                if (!toEntityIds.contains(destEntity.getId())) {
                    toEntityIds.add(destEntity.getId());
                    setting.setToEntityIds(toEntityIds);
                    setting.setAutoPublish(publish);
                    connectorService.upsertSetting(setting);
                    log.info("Updated auto schema sync setting to include destination entity: {}",
                            destEntity.getApiName());
                } else {
                    log.info("Auto schema sync already configured for source entity: {} -> destination entity: {}",
                            sourceEntity.getApiName(), destEntity.getApiName());
                }
            } else {
                // Create new setting
                ConnectorSchemaSetting setting = new ConnectorSchemaSetting();
                setting.setFromConnectorId(sourceEntity.getConnectorId());
                setting.setFromEntityId(sourceEntity.getId());
                setting.setSyncariEntityId(syncariEntity.getId());
                setting.setToConnectorId(destConnector.getId());
                setting.setAutoPublish(publish);
                setting.setToEntityIds(Collections.singletonList(destEntity.getId()));
                connectorService.upsertSetting(setting);
                log.info("Auto schema sync enabled for source entity: {} -> destination entity: {}",
                        sourceEntity.getApiName(), destEntity.getApiName());
            }
            autoSchemaSyncEnabled = true;
        }

        // Build result
        return buildResult(pipeline, sourceEntity, syncariEntity, destEntity,
                fieldMappings, fieldsCreated, destEntityCreated, resyncId, resyncStarted,
                Boolean.TRUE.equals(publish), autoSchemaSyncEnabled, newlyCreatedFieldNames);
    }

    /**
     * Simple holder for source -> syncari -> dest field mapping
     */
    private static class FieldMapping {
        final AttributeDefinition sourceField;
        final AttributeDefinition syncariField;
        final AttributeDefinition destField;

        FieldMapping(AttributeDefinition source, AttributeDefinition syncari, AttributeDefinition dest) {
            this.sourceField = source;
            this.syncariField = syncari;
            this.destField = dest;
        }
    }

    private ExtractLoadResult buildResult(MappingGraph pipeline, EntityDefinition sourceEntity,
                                          EntityDefinition syncariEntity, EntityDefinition destEntity,
                                          List<FieldMapping> fieldMappings, int fieldsCreated,
                                          boolean destEntityCreated, String resyncId,
                                          boolean resyncStarted, boolean published, boolean autoSchemaSyncEnabled,
                                          Set<String> newlyCreatedFieldNames) {
        List<MappedFieldInfo> mappedFieldsList = new ArrayList<>();
        int newlyCreatedCount = 0;
        for (FieldMapping m : fieldMappings) {
            MappedFieldInfo info = new MappedFieldInfo();
            info.setSourceFieldId(m.sourceField.getId());
            info.setSourceFieldName(m.sourceField.getDisplayName());
            info.setSourceFieldApiName(m.sourceField.getApiName());
            info.setSyncariFieldId(m.syncariField.getId());
            info.setSyncariFieldName(m.syncariField.getDisplayName());
            info.setSyncariFieldApiName(m.syncariField.getApiName());
            if (m.destField != null) {
                info.setDestinationFieldId(m.destField.getId());
                info.setDestinationFieldName(m.destField.getDisplayName());
                info.setDestinationFieldApiName(m.destField.getApiName());
            }
            // Mark as newly created if the field name is in the newlyCreatedFieldNames set
            boolean isNew = newlyCreatedFieldNames.contains(m.sourceField.getApiName());
            info.setNewlyCreated(isNew);
            if (isNew) {
                newlyCreatedCount++;
            }
            mappedFieldsList.add(info);
        }

        ExtractLoadResult result = new ExtractLoadResult();
        result.setPipelineId(pipeline.getId());
        result.setPipelineName(pipeline.getName());
        result.setSourceEntityId(sourceEntity.getId());
        result.setSourceEntityName(sourceEntity.getDisplayName());
        result.setSyncariEntityId(syncariEntity.getId());
        result.setSyncariEntityName(syncariEntity.getDisplayName());
        result.setDestinationEntityId(destEntity.getId());
        result.setDestinationEntityName(destEntity.getDisplayName());
        result.setFieldsMapped(mappedFieldsList.size());
        result.setFieldsCreated(fieldsCreated);
        result.setMappedFields(mappedFieldsList);
        result.setPublished(published);
        result.setResyncStarted(resyncStarted);
        result.setResyncId(resyncId);
        result.setDestEntityCreated(destEntityCreated);
        result.setAutoSchemaSyncEnabled(autoSchemaSyncEnabled);
        return result;
    }

    /**
     * Ensure all source fields exist in destination, creating any that are missing.
     * Also updates existing fields to ensure idField and watermarkField flags match the source.
     *
     * @return number of fields created
     */
    private int ensureDestinationFieldsExist(EntityDefinition sourceEntity, EntityDefinition destEntity,
                                             Connector destConnector) {
        List<AttributeDefinition> fieldsToCreate = new ArrayList<>();
        List<AttributeDefinition> fieldsToUpdate = new ArrayList<>();

        for (AttributeDefinition sourceField : sourceEntity.getActiveAttributes()) {
            AttributeDefinition destField = findFieldByName(destEntity, sourceField.getApiName());
            if (destField == null) {
                fieldsToCreate.add(sourceField.makeCopy());
            } else {
                // Update existing fields with idField/watermarkField flags if needed
                boolean needsUpdate = false;
                if (sourceField.isIdField() && !destField.isIdField()) {
                    destField.setIdField(true);
                    needsUpdate = true;
                }
                if (sourceField.isWatermarkField() && !destField.isWatermarkField()) {
                    destField.setWatermarkField(true);
                    needsUpdate = true;
                }
                if (needsUpdate) {
                    fieldsToUpdate.add(destField);
                }
            }
        }

        // Update existing fields with idField/watermarkField flags
        if (!fieldsToUpdate.isEmpty()) {
            attributeProxyRepo.saveAll(fieldsToUpdate);
            log.info("Updated {} existing destination fields with idField/watermarkField flags", fieldsToUpdate.size());
        }

        if (fieldsToCreate.isEmpty()) {
            return 0;
        }

        int created = createFieldsInDestination(destConnector, destEntity, fieldsToCreate);
        log.info("Created {} destination fields from source", created);
        return created;
    }

    /**
     * Ensure all source fields exist in syncari entity, creating any that are missing.
     *
     * @return number of fields created
     */
    private int ensureSyncariFieldsExist(EntityDefinition sourceEntity, EntityDefinition syncariEntity) {
        List<AttributeDefinition> fieldsToCreate = new ArrayList<>();
        for (AttributeDefinition sourceField : sourceEntity.getActiveAttributes()) {
            if (findFieldByName(syncariEntity, sourceField.getApiName()) == null) {
                fieldsToCreate.add(sourceField);
            }
        }

        if (fieldsToCreate.isEmpty()) {
            return 0;
        }

        List<AttributeDefinition> syncariFields = new ArrayList<>();
        for (AttributeDefinition sourceField : fieldsToCreate) {
            AttributeDefinition syncariField = new AttributeDefinition();
            syncariField.setApiName(sourceField.getApiName());
            syncariField.setDisplayName(sourceField.getDisplayName());
            // Reference fields should be mapped to string in syncari
            syncariField.setDataType(normalizeDataType(sourceField.getDataType()));
            syncariField.setStatus(Status.ACTIVE);
            syncariField.setDraftStatus(DraftStatus.APPROVED);
            syncariField.setEntityId(syncariEntity.getId());
            syncariFields.add(syncariField);
        }

        attributeProxyRepo.saveAll(syncariFields);
        log.info("Created {} missing syncari fields from source entity", syncariFields.size());
        return syncariFields.size();
    }

    private EntityDefinition createDestinationEntity(Connector destConnector, String entityName) {
        try {
            String apiName = entityName.replaceAll("\\s+", "_").toLowerCase();

            // Check if EntityDefinition already exists in database
            Optional<EntityDefinition> existingEntity = schemaService.findEntity(destConnector.getId(), apiName);
            if (existingEntity.isPresent()) {
                log.info("EntityDefinition for {} already exists in connector: {}, returning existing",
                        entityName, destConnector.getName());
                return existingEntity.get();
            }

            MetadataService metadataService = dataServiceFactory.getSchemaService(destConnector.getMetadata());

            // Check if entity already exists in destination system
            DescribeRequest describeRequest = new DescribeRequest(transformer.toConnectorInfo(destConnector), apiName);
            Optional<EntitySchema> existingSchema = metadataService.describe(describeRequest);

            EntityDefinition newEntity = new EntityDefinition();
            newEntity.setDisplayName(entityName);
            newEntity.setApiName(apiName);
            newEntity.setStatus(Status.ACTIVE);
            newEntity.setDraftStatus(DraftStatus.APPROVED);
            newEntity.setConnectorId(destConnector.getId());
            newEntity.setCustom(false);

            if (existingSchema.isPresent()) {
                // Entity already exists in destination, just save the definition
                log.info("Destination entity {} already exists in connector: {}, skipping creation",
                        entityName, destConnector.getName());
                newEntity.setApiName(existingSchema.get().getApiName());
            } else {
                // Create in destination system
                CreateObjectRequest request = new CreateObjectRequest(
                        transformer.toConnectorInfo(destConnector),
                        transformer.toEntitySchema(newEntity, destConnector)
                );
                EntitySchema createdSchema = metadataService.createObject(request);
                newEntity.setApiName(createdSchema.getApiName());
                log.info("Created destination entity: {} in connector: {}", newEntity.getDisplayName(), destConnector.getName());
            }

            newEntity = entityProxyRepo.save(newEntity);
            return newEntity;

        } catch (Exception e) {
            log.error("Failed to create destination entity", e);
            throw new RuntimeException("Failed to create destination entity: " + e.getMessage(), e);
        }
    }

    private int createFieldsInDestination(Connector destConnector, EntityDefinition destEntity,
                                          List<AttributeDefinition> sourceFields) {
        if (sourceFields.isEmpty()) {
            return 0;
        }

        MetadataService metadataService = dataServiceFactory.getSchemaService(destConnector.getMetadata());

        // Build list of AttributeSchema objects for batch creation
        List<AttributeSchema> schemas = new ArrayList<>();
        List<AttributeDefinition> attributeDefinitions = new ArrayList<>();
        for (AttributeDefinition sourceField : sourceFields) {
            AttributeSchema schema = transformer.toAttrSchema(sourceField, destEntity, destConnector);
            // Reference fields should be mapped to string in destination
            schema.setDataType(normalizeDataTypeString(schema.getDataType()));
            // Ensure destination fields are updateable (not readonly)
            schema.setUpdateable(true);
            schemas.add(schema);
            AttributeDefinition newAttr = transformer.toAttributeDefinition(schema);
            newAttr.setEntityId(destEntity.getId());
            newAttr.setStatus(Status.ACTIVE);
            newAttr.setDraftStatus(DraftStatus.APPROVED);
            // Ensure destination fields are updatable (not readonly)
            newAttr.setUpdatable(true);
            // Preserve important field flags from source field
            if (sourceField.isIdField()) {
                newAttr.setIdField(true);
            }
            if (sourceField.isWatermarkField()) {
                newAttr.setWatermarkField(true);
            }
            attributeDefinitions.add(newAttr);
        }

        // Create all fields at once
        CreateFieldsRequest request = new CreateFieldsRequest(
                destEntity.getApiName(),
                transformer.toConnectorInfo(destConnector),
                schemas
        );

        try {
            metadataService.createFields(request);
            final List<AttributeDefinition> saved = attributeProxyRepo.saveAll(attributeDefinitions);
            // Save each created field to repository
            log.info("Created {} fields in entity: {}", saved.size(), destEntity.getDisplayName());
            return saved.size();

        } catch (Exception e) {
            log.error("Failed to create fields in entity: {}", destEntity.getDisplayName(), e);
            throw new RuntimeException("Failed to create fields in entity: " + destEntity.getDisplayName(), e);
        }
    }

    private EntityDefinition getOrCreateSyncariEntity(String syncariEntityName, EntityDefinition sourceEntity) {
        // Try to find existing Syncari entity by API name using SchemaService
        Optional<EntityDefinition> existing = schemaService.getSyncariEntityByName(syncariEntityName);
        if (existing.isPresent()) {
            log.info("Found existing Syncari entity with apiName: {}", syncariEntityName);
            return existing.get();
        }

        // Create new Syncari entity
        log.info("Creating new Syncari entity with apiName: {}", syncariEntityName);
        Connector syncariConnector = connectorService.getSyncariConnector();

        EntityDefinition syncariEntity = new EntityDefinition();
        syncariEntity.setApiName(syncariEntityName);
        syncariEntity.setDisplayName(syncariEntityName);
        syncariEntity.setStatus(Status.ACTIVE);
        syncariEntity.setDraftStatus(DraftStatus.APPROVED);
        syncariEntity.setConnectorId(syncariConnector.getId());
        syncariEntity.setConnectorTypeId(syncariConnector.getMetadataId());

        // Save entity first to get ID
        syncariEntity = entityProxyRepo.save(syncariEntity);

        // Create syncari fields matching source fields
        if (sourceEntity != null && sourceEntity.getActiveAttributes() != null) {
            List<AttributeDefinition> syncariFields = new ArrayList<>();
            for (AttributeDefinition sourceField : sourceEntity.getActiveAttributes()) {
                AttributeDefinition syncariField = new AttributeDefinition();
                syncariField.setApiName(sourceField.getApiName());
                syncariField.setDisplayName(sourceField.getDisplayName());
                // Reference fields should be mapped to string in syncari
                syncariField.setDataType(normalizeDataType(sourceField.getDataType()));
                syncariField.setStatus(Status.ACTIVE);
                syncariField.setDraftStatus(DraftStatus.APPROVED);
                syncariField.setEntityId(syncariEntity.getId());
                syncariFields.add(syncariField);
            }
            if (!syncariFields.isEmpty()) {
                attributeProxyRepo.saveAll(syncariFields);
                log.info("Created {} syncari fields from source entity", syncariFields.size());
            }
        }

        return syncariEntity;
    }

    private MappingGraph getOrCreatePipeline(EntityDefinition syncariEntity, String pipelineName) {
        String syncariEntityId = syncariEntity.getId();

        // First try to find DRAFT pipeline by targetId (syncariEntity.id)
        Optional<MappingGraph> draftPipeline = mappingGraphService.retrieveDraftEntityGraph(syncariEntityId);
        if (draftPipeline.isPresent()) {
            log.info("Found existing DRAFT pipeline for Syncari entity: {}", syncariEntity.getApiName());
            return draftPipeline.get();
        }

        // If no DRAFT, try to find APPROVED pipeline by targetId
        Optional<MappingGraph> approvedPipeline = mappingGraphService.retrieveApprovedEntityGraph(syncariEntityId);
        if (approvedPipeline.isPresent()) {
            log.info("Found existing APPROVED pipeline for Syncari entity: {}, creating DRAFT from it", syncariEntity.getApiName());
            return mappingGraphService.createDraftFor(approvedPipeline.get());
        }

        // No existing pipeline found, create new one with the given name
        log.info("No existing pipeline found for Syncari entity: {}, creating new pipeline with name: {}",
                syncariEntity.getApiName(), pipelineName);
        MappingGraph pipeline = mappingGraphService.createDefaultEntityGraph(syncariEntityId);
        pipeline.setName(pipelineName);
        pipeline.setDraftStatus(DraftStatus.NEW);

        return mappingGraphService.saveGraph(pipeline);
    }

    /**
     * Ensure field pipelines exist for all mappings, adding source/dest nodes as needed.
     */
    private void ensureFieldPipelinesExist(List<FieldMapping> fieldMappings, List<Layout> layouts) {
        for (FieldMapping mapping : fieldMappings) {
            MappingGraph fieldPipeline = getOrCreateFieldPipeline(mapping.syncariField.getId());

            // Add source if missing
            if (!fieldPipeline.hasSource(mapping.sourceField.getId())) {
                addSourceFieldNode(fieldPipeline, mapping.sourceField, layouts);
            }

            // Add dest if missing and exists
            if (mapping.destField != null && !fieldPipeline.hasSink(mapping.destField.getId())) {
                addDestFieldNode(fieldPipeline, mapping.destField, layouts);
            }

            // Save (upsertGraph saves nodes and edges which are @Transient)
            mappingGraphService.upsertGraph(fieldPipeline);
        }
    }

    /**
     * Get or create a field pipeline (attribute graph) for a syncari field
     */
    private MappingGraph getOrCreateFieldPipeline(String syncariFieldId) {
        // Try to get draft first
        Optional<MappingGraph> draftPipeline = mappingGraphService.retrieveDraftAttributeGraph(syncariFieldId);
        if (draftPipeline.isPresent()) {
            return draftPipeline.get();
        }

        // Try to get approved and create draft from it
        Optional<MappingGraph> approvedPipeline = mappingGraphService.retrieveApprovedAttributeGraph(syncariFieldId);
        if (approvedPipeline.isPresent()) {
            return mappingGraphService.createDraftFor(approvedPipeline.get());
        }

        // Create new field pipeline
        MappingGraph fieldPipeline = mappingGraphService.createDefaultAttributeGraph(syncariFieldId);
        fieldPipeline.setDraftStatus(DraftStatus.NEW);
        return mappingGraphService.saveGraph(fieldPipeline);
    }

    // ============================================================
    // Entity Pipeline Node Methods
    // ============================================================

    private void addSourceEntityNode(MappingGraph pipeline, EntityDefinition entity, List<Layout> layouts) {
        NodeConfiguration config = new EntitySourceNodeConfig().setEntityDefinition(entity);
        MappingNode node = createEntityNode(entity, config);
        long count = pipeline.getSources().count();
        addSourceNode(pipeline, node, config, count, layouts);
    }

    private void addDestEntityNode(MappingGraph pipeline, EntityDefinition entity, List<Layout> layouts) {
        NodeConfiguration config = new EntitySinkNodeConfig().setEntityDefinition(entity);
        MappingNode node = createEntityNode(entity, config);
        long count = pipeline.getSinks().count();
        addDestNode(pipeline, node, config, count, layouts);
    }

    private MappingNode createEntityNode(EntityDefinition entity, NodeConfiguration config) {
        MappingNode node = new MappingNode()
                .setName(entity.getDisplayName())
                .setApiName(entity.getApiName())
                .setScope(Scope.ENTITY)
                .setConfiguration(config);
        node.setId(UUID.randomUUID().toString());
        return node;
    }

    // ============================================================
    // Field Pipeline Node Methods
    // ============================================================

    private void addSourceFieldNode(MappingGraph pipeline, AttributeDefinition field, List<Layout> layouts) {
        NodeConfiguration config = new AttributeSourceNodeConfig().setAttributeDefinition(field);
        MappingNode node = createFieldNode(field, config);
        long count = pipeline.getSources().count();
        addSourceNode(pipeline, node, config, count, layouts);
    }

    private void addDestFieldNode(MappingGraph pipeline, AttributeDefinition field, List<Layout> layouts) {
        NodeConfiguration config = new AttributeSinkNodeConfig().setAttributeDefinition(field);
        MappingNode node = createFieldNode(field, config);
        long count = pipeline.getSinks().count();
        addDestNode(pipeline, node, config, count, layouts);
    }

    private MappingNode createFieldNode(AttributeDefinition field, NodeConfiguration config) {
        MappingNode node = new MappingNode()
                .setName(field.getDisplayName())
                .setApiName(field.getApiName())
                .setScope(Scope.ATTRIBUTE)
                .setConfiguration(config);
        node.setId(UUID.randomUUID().toString());
        return node;
    }

    // ============================================================
    // Common Node/Edge Creation
    // ============================================================

    private void addSourceNode(MappingGraph pipeline, MappingNode node, NodeConfiguration config,
                               long existingCount, List<Layout> layouts) {
        MappingNode coreNode = pipeline.getCoreNode();
        if (coreNode == null) {
            log.warn("Pipeline has no core node, adding node without edge: {}", node.getName());
            pipeline.addNode(node);
            return;
        }

        // Edge: source -> core
        Edge edge = new Edge()
                .setGraphId(pipeline.getId())
                .setSourceStage(node)
                .setDestinationStage(coreNode);
        edge.setId(UUID.randomUUID().toString());

        // Set ports
        setOutputPort(edge, config);
        setInputPort(edge, coreNode.getConfiguration());

        pipeline.addNode(node);
        pipeline.addEdge(edge);

        // Layout: left side
        layouts.add(Layout.node(node.getId(), SOURCE_X, computeY(existingCount)));
        layouts.add(Layout.edge(edge.getId(), "1", "3"));
    }

    private void addDestNode(MappingGraph pipeline, MappingNode node, NodeConfiguration config,
                             long existingCount, List<Layout> layouts) {
        MappingNode coreNode = pipeline.getCoreNode();
        if (coreNode == null) {
            log.warn("Pipeline has no core node, adding node without edge: {}", node.getName());
            pipeline.addNode(node);
            return;
        }

        // Edge: core -> dest
        Edge edge = new Edge()
                .setGraphId(pipeline.getId())
                .setSourceStage(coreNode)
                .setDestinationStage(node);
        edge.setId(UUID.randomUUID().toString());

        // Set ports
        setOutputPort(edge, coreNode.getConfiguration());
        setInputPort(edge, config);

        pipeline.addNode(node);
        pipeline.addEdge(edge);

        // Layout: right side
        layouts.add(Layout.node(node.getId(), DEST_X, computeY(existingCount)));
        layouts.add(Layout.edge(edge.getId(), "1", "3"));
    }

    private void setOutputPort(Edge edge, NodeConfiguration config) {
        if (config != null && config.getOutputPorts() != null && !config.getOutputPorts().isEmpty()) {
            edge.setOutput(config.getOutputPorts().get(0));
        }
    }

    private void setInputPort(Edge edge, NodeConfiguration config) {
        if (config != null && config.getInputPorts() != null && !config.getInputPorts().isEmpty()) {
            edge.setInput(config.getInputPorts().get(0));
        }
    }

    private static final String SOURCE_X = "100";
    private static final String DEST_X = "700";

    private String computeY(long count) {
        return String.valueOf(200 + (count * 100));
    }

    /**
     * Find field by name without throwing exception
     */
    private AttributeDefinition findFieldByName(EntityDefinition entity, String apiName) {
        if (entity == null || entity.getActiveAttributes() == null) {
            return null;
        }
        return entity.getActiveAttributes().stream()
                .filter(f -> apiName.equalsIgnoreCase(f.getApiName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Create SchemaMapping entries for source -> syncari and syncari -> dest field mappings.
     * These mappings are used by auto schema sync to track field relationships.
     */
    private void createSchemaMappings(List<FieldMapping> fieldMappings, EntityDefinition sourceEntity, EntityDefinition destEntity) {
        for (FieldMapping mapping : fieldMappings) {
            // Create source -> syncari mapping if not exists
            List<SchemaMapping> existingSourceMappings = schemaMappingRepo.findByConnectorAndSynapseObject(
                    sourceEntity.getConnectorId(), mapping.sourceField.getId(), Scope.ATTRIBUTE.name());
            if (existingSourceMappings.isEmpty()) {
                schemaMappingRepo.save(new SchemaMapping()
                        .setConnectorId(sourceEntity.getConnectorId())
                        .setSynapseObjectId(mapping.sourceField.getId())
                        .setSyncariId(mapping.syncariField.getId())
                        .setScope(Scope.ATTRIBUTE.name()));
            }

            // Create syncari -> dest mapping if not exists
            if (mapping.destField != null) {
                List<SchemaMapping> existingDestMappings = schemaMappingRepo.findByConnectorAndSyncariObject(
                        destEntity.getConnectorId(), mapping.syncariField.getId(), Scope.ATTRIBUTE.name());
                if (existingDestMappings.isEmpty()) {
                    schemaMappingRepo.save(new SchemaMapping()
                            .setConnectorId(destEntity.getConnectorId())
                            .setSynapseObjectId(mapping.destField.getId())
                            .setSyncariId(mapping.syncariField.getId())
                            .setScope(Scope.ATTRIBUTE.name()));
                }
            }
        }
        log.info("Created SchemaMapping entries for {} field mappings", fieldMappings.size());
    }

    /**
     * Normalize data type for syncari/destination fields.
     * Reference type fields should be mapped to string.
     */
    private Datatype normalizeDataType(Datatype dataType) {
        if (dataType == null) {
            return new StringType();
        }
        if ("reference".equalsIgnoreCase(dataType.getName())) {
            return new StringType();
        }
        return dataType;
    }

    /**
     * Normalize data type string for syncari/destination fields.
     * Reference type fields should be mapped to string.
     */
    private String normalizeDataTypeString(String dataType) {
        if (dataType == null) {
            return "string";
        }
        if ("reference".equalsIgnoreCase(dataType)) {
            return "string";
        }
        return dataType;
    }
}
