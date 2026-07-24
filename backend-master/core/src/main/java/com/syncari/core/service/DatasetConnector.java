package com.syncari.core.service;

import com.syncari.connector.*;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataOffsetIterator;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetConfig;
import com.syncari.core.model.insights.Projection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component(Constants.DATASETS)
public class DatasetConnector implements SynapseInfoService, CommonDataService, MetadataService, AuthenticationService {

    @Autowired
    DatasetService datasetService;

    @Autowired
    DatasetSchemaService datasetSchemaService;

    @Override
    public boolean validateEntityConfig(EntityParams params) {
        if (params.getSourceParams() == null || !params.getSourceParams().containsKey("orderBy"))
            throw new SyncariValidationException("OrderBy Field is not available in dataset source config");

        String orderByValue = String.valueOf(params.getSourceParams().getOrDefault("orderBy", ""));
        if (StringUtils.isBlank(orderByValue))
            throw new SyncariValidationException(
                    "OrderBy field must be configured in the source node configuration for dataset to be used as a source");

        if (params.getSchema() == null || params.getSchema().getAdditionalProperties() == null) {
            throw new SyncariValidationException("Entity schema or additional properties not available");
        }

        Object datasetIdObj = params.getSchema().getAdditionalProperties().get("datasetId");
        if (datasetIdObj == null) {
            throw new SyncariValidationException("Dataset id not defined for entity " + params.getSchema().getApiName());
        }

        Dataset dataset = datasetService.getDataset(datasetIdObj.toString());
        if (dataset == null) {
            throw new SyncariValidationException("Dataset not found for id: " + datasetIdObj.toString());
        }

        DatasetConfig datasetConfig = dataset.getDatasetConfig();

        Set<String> validFields = new HashSet<>();
        if (datasetConfig != null && datasetConfig.getProjectionsList() != null) {
            for (Projection projection : datasetConfig.getProjectionsList()) {
                if (projection != null && projection.getAliasName() != null) {
                    validFields.add(projection.getAliasName());
                }
            }
        }

        List<String> orderByFields = parseOrderByFields(orderByValue);
        for (String field : orderByFields) {
            String fieldName = field.trim();
            if (fieldName.toUpperCase().endsWith(" ASC") || fieldName.toUpperCase().endsWith(" DESC")) {
                fieldName = fieldName.substring(0, fieldName.lastIndexOf(" ")).trim();
            }

            if (!validFields.contains(fieldName)) {
                throw new SyncariValidationException(
                        String.format("Invalid orderBy field '%s'. Valid fields are: %s",
                        fieldName, String.join(", ", validFields)));
            }
        }

        return true;
    }

    private static List<String> parseOrderByFields(String input) {
        if (input == null || StringUtils.isBlank(input))
            return List.of();
        String[] parts = input.split(",");
        return Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        Object datasetIdObj = request.getEntitySchema().getAdditionalProperties().get("datasetId");

        if (datasetIdObj == null) {
            throw new RuntimeException("Dataset id not defined for entity " + request.getEntityName());
        }

        Function3<WatermarkInfo, Integer, Long, DataWithOffset> generator = (wm, pageSize, offset) -> {
            log.info("Fetching dataset page for offset {}", offset);
            List<EntityData> results = fetchDataset(request, 1000, offset);
            log.debug("Dataset page fetched with size of {}", results.size());
            log.debug("Setting next offset to {}", offset + results.size());
            return new DataWithOffset(offset, offset + results.size(), results, List.of());
        };

        int pgSize = (request.getPageSize() <= 0) ? 1000 : request.getPageSize();
        DefaultDataOffsetIterator iterator = new DefaultDataOffsetIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pgSize, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private List<EntityData> fetchDataset(SyncRequest request, int limit, Long offset) {
        Object datasetIdObj = request.getEntitySchema().getAdditionalProperties().get("datasetId");
        if (datasetIdObj == null) {
            throw new RuntimeException("Dataset id not defined for entity " + request.getEntityName());
        }
        var orderByConfig = request.getAdditionalParams().getOrDefault("orderBy", "");
        List<String> orderBy = parseOrderByFields(orderByConfig == null ? null : String.valueOf(orderByConfig));

        Dataset dataset = datasetService.getDataset(datasetIdObj.toString());
        List<EntityData> currentData;
        Map<String, Object> dataAndCols = dataset.isSQLMode() ? datasetService.readFromDatasetQuery(dataset, limit, offset, orderBy) : datasetService.readDataWithPagination(dataset, Map.of(), limit, offset, orderBy);
        currentData = parseData(dataAndCols, request);
        return new ArrayList<>(currentData);
    }

    private List<EntityData> parseData(Map<String, Object> dataAndCols, SyncRequest request) {
        EntitySchema entitySchema = request.getEntitySchema();
        AttributeSchema idField = entitySchema.getIdField();
        List<Map<String, Object>> data = (List<Map<String, Object>>) dataAndCols.get("data");
        List<EntityData> results = new ArrayList<>();
        data.forEach(d -> {
            EntityData entityData = new EntityData();
            d.forEach((k, v) -> {
                String apiName = datasetSchemaService.sanitizeApiName(k);
                if(apiName.equalsIgnoreCase(idField.getApiName())) {
                    String id = null;
                    if (v instanceof String) {
                        id = (String) v;
                    } else if (v instanceof Number) {
                        id = ((Number) v).toString();
                    }
                    entityData.setId(id);
                }
                entityData.addValue(apiName, v);
            });
            entityData.setName(entitySchema.getApiName());
            entityData.setLastModified(request.getWatermark() != null ? request.getWatermark().getEnd() : Instant.now().toEpochMilli());
            results.add(entityData);
        });
        return results;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        log.info("DatasetService received getByIds request for {}", request.getEntityName());
        if (!request.getEntitySchema().hasIdField()) {
            throw new RuntimeException("Id field not defined for entity " + request.getEntityName());
        }
        Set<String> ids = getIds(request);
        if (CollectionUtils.isEmpty(ids)) {
            throw new RuntimeException("Incoming Ids field not defined for entity " + request.getEntityName());
        }
        Object datasetIdObj = request.getEntitySchema().getAdditionalProperties().get("datasetId");
        if (datasetIdObj == null) {
            throw new RuntimeException("Dataset id not defined for entity " + request.getEntityName());
        }
        Dataset dataset = datasetService.getDataset(datasetIdObj.toString());
        Long offset = 0L;
        List<EntityData> results = new ArrayList<>();
        List<EntityData> currentData;
        do {
            Map<String, Object> dataAndCols = datasetService.readDataWithPagination(dataset, Map.of(), 1000, offset);
            currentData = parseData(dataAndCols, request);
            currentData.stream().forEach(d -> {
                if (ids.contains(d.getId())) {
                    results.add(d);
                }
            });
            if (results.size() == ids.size()) {
                break;
            }
            offset += currentData.size();
        } while (currentData.size() >= 1000);
        return results;
    }

    private Set<String> getIds(SyncRequest request) {
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        return entityList.stream().map(e -> e.getId()).collect(Collectors.toSet());
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        throw new RuntimeException("Dataset does not support create");
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        throw new RuntimeException("Dataset does not support update");
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        throw new RuntimeException("Dataset does not support delete");
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of();
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of();
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    public String getName() {
        return Constants.DATASETS;
    }

    @Override
    public String getCategory() {
        return "Productivity";
    }

    @Override
    public boolean isSink() {
        return false;
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/dataset.svg")
                .setDisplayName(Constants.DATASETS_DISPLAY_NAME)
                .setBackgroundColor("#EFEFEF")
                .setHelpUrl(helpArticlesBaseUrl + "/23055125072276");
    }

    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<Capability>();
        capabilities.add(Capability.schemaEditInSyncari);
        capabilities.add(Capability.userEditableId);
        return capabilities;
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        log.info("DatasetService received describe request for {}", request.getEntity());
        DescribeAllRequest req = new DescribeAllRequest(request.getConnector(), List.of(request.getEntity()));
        return describeAll(req).stream().filter(e -> e.getApiName().equalsIgnoreCase(request.getEntity())).findFirst();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        log.info("DatasetService received describeAll request");
        List<EntitySchema> schemaList = new ArrayList<>();
        List<Dataset> allDatasets = datasetService.getAllApprovedDatasetsWithVersion();
        log.debug("Processing {} datasets for schema refresh", allDatasets.size());

        int processedCount = 0;

        for (Dataset dataset : allDatasets) {
            String datasetName = dataset.getName() != null ? dataset.getName() : "null-name";
            String datasetId = dataset.getId() != null ? dataset.getId() : "null-id";

            try {
                log.debug("Processing dataset: {} (id: {})", datasetName, datasetId);
                EntityDefinition entityDef = datasetSchemaService.fetchDatasetSchema(dataset);
                schemaList.add(convertEntityDefToEntitySchema(entityDef));
                processedCount++;
            } catch (Exception e) {
                log.error("Failed to fetch schema for dataset: {} (id: {})",
                         datasetName, datasetId, e);
                throw new RuntimeException(String.format("Dataset schema refresh failed for dataset: %s (id: %s)", datasetName, datasetId), e);
            }
        }

        log.debug("Successfully processed all {} datasets", processedCount);
        return schemaList;
    }

    private EntitySchema convertEntityDefToEntitySchema(EntityDefinition entityDefinition) {
        EntitySchema entitySchema = new EntitySchema();
        entitySchema.setApiName(entityDefinition.getApiName());
        entitySchema.setDisplayName(entityDefinition.getDisplayName());
        entitySchema.setAttributes(entityDefinition.getAttributes().stream().map(a -> convertAttributeDefToAttributeSchema(a)).collect(Collectors.toList()));
        entitySchema.setReadOnly(entityDefinition.isReadOnly());
        entitySchema.setId(entityDefinition.getId());
        entitySchema.setDescription(entityDefinition.getDescription());
        entitySchema.setPluralName(entityDefinition.getPluralName());
        entitySchema.setAdditionalProperties(entityDefinition.getAdditionalProperties());
        entitySchema.setVersion(entityDefinition.getVersion());
        entitySchema.setCustom(entityDefinition.isCustom());
        entitySchema.setSourceParams(entityDefinition.getSourceParams().stream().map(a -> convertAttributeDefToAttributeSchema(a)).collect(Collectors.toList()));
        entitySchema.setDestParams(entityDefinition.getDestinationParams().stream().map(a -> convertAttributeDefToAttributeSchema(a)).collect(Collectors.toList()));
        return entitySchema;
    }

    private AttributeSchema convertAttributeDefToAttributeSchema(AttributeDefinition attributeDefinition) {
        return new AttributeSchema()
                .setApiName(attributeDefinition.getApiName())
                .setDisplayName(attributeDefinition.getDisplayName())
                .setDataType(attributeDefinition.getDataType().getName())
                .setWatermarkField(attributeDefinition.isWatermarkField())
                .setIdField(attributeDefinition.isIdField())
                .setReferenceTo(attributeDefinition.getReferenceTo())
                .setUnique(attributeDefinition.isUnique())
                .setNillable(attributeDefinition.isNillable())
                .setSystem(attributeDefinition.isSystem())
                .setUpdateable(attributeDefinition.isUpdatable())
                .setCreatedAtField(attributeDefinition.isCreatedAtField())
                .setUpdatedAtField(attributeDefinition.isUpdatedAtField())
                .setExternalId(attributeDefinition.getExternalId())
                .setReferenceTargetField(attributeDefinition.getReferenceTargetField())
                .setLength(attributeDefinition.getLength())
                .setPrecision(attributeDefinition.getPrecision())
                .setScale(attributeDefinition.getScale())
                .setDefaultValue(attributeDefinition.getDefaultValue());
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in datasets yet");
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("createField not supported in datasets yet");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("deleteField not supported in datasets yet");
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        return new TestConnectionResponse();
    }
}
