package com.syncari.connector.OracleErpProcurement;

import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataOffsetIterator;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Oracle ERP Procurement Service.
 *
 * Handles Suppliers entity (POZ_SUPPLIERS) with child objects:
 * - Supplier Sites
 * - Site Assignments
 * - Supplier Contacts
 *
 * Per Oracle docs:
 * - Supports: CREATE (POST), UPDATE (PATCH), READ (GET)
 * - Does NOT support: DELETE, Incremental Sync (only CreationDate queryable, not LastUpdateDate)
 *
 * @see <a href="https://docs.oracle.com/en/cloud/saas/procurement/25c/fapra/">Oracle Fusion Cloud Procurement REST API</a>
 */
@Slf4j
@Component(Constants.ORACLE_ERP_PROCUREMENT)
public class OracleERPProcurementService implements CommonDataService, MetadataService, SynapseInfoService, AuthenticationService {

    // ===========================================
    // ENTITY NAMES
    // ===========================================

    public static final String SUPPLIERS_ENTITY_NAME = "suppliers";  // POZ_SUPPLIERS

    // ===========================================
    // REST API CONFIGURATION
    // ===========================================

    public static final String SUPPLIERS_ENDPOINT = "suppliers";
    public static final String REST_API_VERSION = "11.13.18.05";
    public static final int REST_API_MAX_PAGESIZE = 500;

    // ===========================================
    // ENTITY SETS
    // ===========================================

    public static final Set<String> REST_ENTITIES = Set.of(
            SUPPLIERS_ENTITY_NAME
    );

    // Entities that do NOT support incremental sync (no LastUpdateDate filter)
    // Per Oracle docs: "only CreationDate queryable, not LastUpdateDate"
    public static final Set<String> NO_INCREMENTAL_SYNC_ENTITIES = Set.of(
            SUPPLIERS_ENTITY_NAME
    );

    // Entities that do NOT support delete
    // Per Oracle docs: No DELETE operation available for suppliers
    public static final Set<String> NO_DELETE_ENTITIES = Set.of(
            SUPPLIERS_ENTITY_NAME
    );

    // ===========================================
    // DISPLAY NAMES
    // ===========================================

    public static final Map<String, String> ENTITY_DISPLAY_NAMES = Map.of(
            SUPPLIERS_ENTITY_NAME, "Supplier"
    );

    public static final Map<String, String> ENTITY_PLURAL_NAMES = Map.of(
            SUPPLIERS_ENTITY_NAME, "Suppliers"
    );

    // ===========================================
    // FIELD MAPPINGS
    // ===========================================

    private static final Map<String, String> SUPPORTED_ID_FIELDS = Map.of(
            SUPPLIERS_ENTITY_NAME, "SupplierId"
    );

    // Note: Suppliers do NOT support watermark/incremental sync
    // Only CreationDate is queryable, LastUpdateDate is NOT
    private static final Map<String, String> SUPPORTED_WM_FIELDS = Map.of();

    // ===========================================
    // REST ENDPOINT MAPPINGS
    // ===========================================

    private static final Map<String, String> ENTITY_TO_REST_ENDPOINT = Map.of(
            SUPPLIERS_ENTITY_NAME, SUPPLIERS_ENDPOINT
    );

    // ===========================================
    // SERVICE METHODS
    // ===========================================

    protected OracleERPProcurementRestClient getRESTClient() {
        return new OracleERPProcurementRestClient();
    }

    private String getBaseUrl(AuthConfig authConfig) {
        return authConfig.getEndpoint();
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();

        try {
            OracleERPProcurementRestClient restClient = getRESTClient();
            String url = restClient.buildFullSyncUrl(
                    getBaseUrl(config.getAuthConfig()),
                    SUPPLIERS_ENDPOINT,
                    SUPPLIERS_ENTITY_NAME,
                    1,
                    0
            );
            restClient.getResponse(url, config.getAuthConfig());
        } catch (Exception e) {
            handleAuthenticationErrorMessage(result, e);
        }

        return result;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        String entityName = request.getEntityName();

        if (!REST_ENTITIES.contains(entityName)) {
            throw new IllegalArgumentException(
                    String.format("Entity %s is not supported by Oracle ERP Procurement", entityName));
        }

        // Note: Suppliers do NOT support incremental sync - always full sync
        return getByWatermarkREST(request, entityName);
    }

    /**
     * Fetch REST entity data - FULL SYNC ONLY (no incremental sync support per Oracle docs).
     * Suppliers only has CreationDate queryable, not LastUpdateDate.
     */
    private FetchResponse getByWatermarkREST(SyncRequest request, String entityName) {
        String displayName = ENTITY_DISPLAY_NAMES.getOrDefault(entityName, entityName);
        OracleERPProcurementRestClient restClient = getRESTClient();

        Function3<WatermarkInfo, Integer, Long, DataWithOffset> generator = (wm, pageSize, offset) -> {
            try {
                String endpoint = ENTITY_TO_REST_ENDPOINT.get(entityName);
                String url = restClient.buildFullSyncUrl(
                        getBaseUrl(request.getConnector().getAuthConfig()),
                        endpoint,
                        entityName,
                        pageSize,
                        offset
                );

                log.debug("REST full sync URL: {}", url);
                return restClient.getDataWithOffset(url, offset, request);
            } catch (Exception e) {
                log.error("Error fetching {} : {}\n{}", displayName, e.getMessage(), ExceptionUtils.getStackTrace(e));
                throw new RuntimeException("Failed to fetch " + displayName, e);
            }
        };

        int pgSize = (request.getPageSize() <= 0) ? REST_API_MAX_PAGESIZE : Math.min(request.getPageSize(), REST_API_MAX_PAGESIZE);
        WatermarkInfo watermark = request.getWatermark();
        long offset = (watermark != null) ? watermark.getOffset() : 0L;
        int limit = (watermark != null) ? watermark.getLimit() : 0;

        DefaultDataOffsetIterator iterator = new DefaultDataOffsetIterator(
                watermark,
                offset,
                generator,
                new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(),
                pgSize,
                limit
        );

        return new FetchResponse(watermark, iterator);
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) {
            return new ArrayList<>();
        }

        String entityName = request.getEntityName();
        List<EntityData> data = new ArrayList<>();
        OracleERPProcurementRestClient restClient = getRESTClient();
        String endpoint = ENTITY_TO_REST_ENDPOINT.get(entityName);
        String baseUrl = getBaseUrl(request.getConnector().getAuthConfig());

        try {
            for (String id : request.getIds()) {
                String url = restClient.buildGetByIdUrl(baseUrl, endpoint, entityName, id);
                log.debug("REST getById URL: {}", url);
                EntityData entity = restClient.getById(url, request);
                if (entity != null) {
                    data.add(entity);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching {} by IDs: {}\n{}", entityName, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Failed to fetch " + entityName + " by IDs", e);
        }

        return data;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        String entityName = request.getEntityName();
        return createOrUpdateREST(request, entityName, false);
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        String entityName = request.getEntityName();
        return createOrUpdateREST(request, entityName, true);
    }

    /**
     * Create or update a REST entity.
     * Per Oracle docs: POST for create, PATCH for update
     *
     * For updates with child objects:
     * - Oracle REST PATCH does NOT support nested children in payload
     * - Child objects must be processed via separate child endpoints:
     *   - No child ID → CREATE: POST /suppliers/{id}/child/sites
     *   - Has child ID → UPDATE: PATCH /suppliers/{id}/child/sites/{siteId}
     */
    private SyncResponse createOrUpdateREST(SyncRequest request, String entityName, boolean isUpdate) {
        OracleERPProcurementRestClient restClient = getRESTClient();
        String baseUrl = request.getConnector().getAuthConfig().getEndpoint();
        AuthConfig auth = request.getConnector().getAuthConfig();
        String endpoint = restClient.getEndpointForEntity(entityName);

        SyncResponse response = new SyncResponse();

        try {
            String connectorId = request.getConnector().getId();
            Map<String, List<EntityData>> dataMap = request.getData();
            List<EntityData> entitiesToWrite = dataMap != null ? dataMap.get(connectorId) : null;

            if (entitiesToWrite == null || entitiesToWrite.isEmpty()) {
                return response;
            }

            for (EntityData entityData : entitiesToWrite) {
                try {
                    Map<String, Object> data = new HashMap<>(entityData.getValues());
                    EntityData resultEntity = null;
                    List<String> childErrors = new ArrayList<>();

                    String parentId = entityData.getId();
                    if (parentId == null) {
                        String idField = SUPPORTED_ID_FIELDS.get(entityName);
                        if (idField != null && data.containsKey(idField)) {
                            parentId = data.get(idField).toString();
                        }
                    }

                    if (isUpdate && parentId == null) {
                        throw new RuntimeException("Entity ID is required for update operation");
                    }

                    Set<String> childCollections = restClient.getChildCollections(entityName);
                    Map<String, Object> parentFields = new HashMap<>();
                    Map<String, List<Map<String, Object>>> childData = new HashMap<>();

                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();

                        if (childCollections.contains(key) && value != null) {
                            List<Map<String, Object>> childItems = extractChildItems(value);
                            childData.put(key, childItems);
                        } else {
                            parentFields.put(key, value);
                        }
                    }

                    // Process child collections using GET + Compare pattern to detect CREATE, UPDATE, DELETE
                    // Optimization: Only do GET if at least one child collection has actual items
                    boolean hasAnyIncomingChildren = childData.values().stream()
                            .anyMatch(list -> list != null && !list.isEmpty());

                    if (isUpdate && hasAnyIncomingChildren) {
                        EntityData currentParent = fetchCurrentParentWithChildren(restClient, baseUrl, endpoint, entityName, parentId, auth);

                        // Get parent schema for child schema lookup
                        EntitySchema parentSchema = request.getEntitySchema();

                        for (Map.Entry<String, List<Map<String, Object>>> childEntry : childData.entrySet()) {
                            String childType = childEntry.getKey();
                            List<Map<String, Object>> incomingChildren = childEntry.getValue();
                            String childIdField = restClient.getChildIdField(childType);

                            // Get child schema for filtering Syncari metadata
                            EntitySchema childSchema = null;
                            if (parentSchema != null) {
                                Optional<AttributeSchema> childAttr = parentSchema.getField(childType);
                                if (childAttr.isPresent() && childAttr.get().getChildSchema() != null) {
                                    childSchema = childAttr.get().getChildSchema();
                                }
                            }

                            Set<String> existingChildIds = getExistingChildIds(currentParent, childType, childIdField);
                            Set<String> incomingChildIds = new HashSet<>();

                            log.debug("Processing '{}' children for parent {}: {} incoming, {} existing",
                                    childType, parentId, incomingChildren.size(), existingChildIds.size());

                            for (Map<String, Object> childItem : incomingChildren) {
                                try {
                                    Map<String, Object> filteredChildItem = filterToSchemaFields(childItem, childSchema);
                                    Object childIdValue = childItem.get(childIdField);

                                    if (childIdValue != null && !childIdValue.toString().isEmpty()) {
                                        String childId = childIdValue.toString();
                                        incomingChildIds.add(childId);

                                        Map<String, Object> existingChildData = getExistingChildData(currentParent, childType, childIdField, childId);

                                        if (hasRealChanges(childItem, existingChildData)) {
                                            log.debug("Updating child {}/{}", childType, childId);
                                            restClient.updateChild(baseUrl, endpoint, parentId, childType, childId, filteredChildItem, auth);
                                        } else {
                                            log.debug("Skipping UPDATE for child {}/{} - no real changes", childType, childId);
                                        }
                                    } else {
                                        log.debug("Creating new child in {}", childType);
                                        restClient.createChild(baseUrl, endpoint, parentId, childType, filteredChildItem, auth);
                                    }
                                } catch (Exception childEx) {
                                    log.error("Child operation failed for {}: {}", childType, childEx.getMessage());
                                    childErrors.add(childType + ": " + childEx.getMessage());
                                }
                            }

                            Set<String> childrenToDelete = new HashSet<>(existingChildIds);
                            childrenToDelete.removeAll(incomingChildIds);

                            for (String childIdToDelete : childrenToDelete) {
                                try {
                                    log.debug("Deleting child {}/{}", childType, childIdToDelete);
                                    restClient.deleteChild(baseUrl, endpoint, parentId, childType, childIdToDelete, auth);
                                } catch (Exception deleteEx) {
                                    log.error("Child delete failed for {}/{}: {}", childType, childIdToDelete, deleteEx.getMessage());
                                    childErrors.add(childType + " delete: " + deleteEx.getMessage());
                                }
                            }
                        }
                    }

                    boolean hasNonIdParentFields = parentFields.entrySet().stream()
                            .anyMatch(e -> !SUPPORTED_ID_FIELDS.containsValue(e.getKey()));

                    if (isUpdate) {
                        if (hasNonIdParentFields) {
                            EntitySchema parentSchemaForUpdate = request.getEntitySchema();
                            Map<String, Object> filteredParentFields = filterToSchemaFields(parentFields, parentSchemaForUpdate);
                            log.debug("Updating parent {} with {} fields", parentId, filteredParentFields.size());
                            resultEntity = restClient.update(baseUrl, endpoint, parentId, filteredParentFields, auth);
                        }
                    } else {
                        EntitySchema parentSchema = request.getEntitySchema();
                        Map<String, Object> filteredData = filterToSchemaFields(data, parentSchema);
                        log.debug("Creating new parent with {} fields", filteredData.size());
                        resultEntity = restClient.create(baseUrl, endpoint, filteredData, auth);
                    }

                    String resultId = resultEntity != null ? resultEntity.getId() : parentId;
                    String syncariEntityId = entityData.getSyncariEntityId();

                    if (childErrors.isEmpty()) {
                        Result result = new Result(true, resultId, syncariEntityId);
                        response.getResults().add(result);
                        log.debug("Successfully {} {} with ID: {}", isUpdate ? "updated" : "created", entityName, resultId);
                    } else {
                        Result result = new Result(false, resultId, syncariEntityId);
                        result.addError("Child operations failed: " + String.join("; ", childErrors));
                        response.getResults().add(result);
                    }

                } catch (Exception e) {
                    log.error("Error processing REST entity {}: {}", entityName, e.getMessage());
                    Result errorResult = new Result(false, entityData.getId(), entityData.getSyncariEntityId());
                    errorResult.addError("Failed to " + (isUpdate ? "update" : "create") + " entity: " + e.getMessage());
                    response.getResults().add(errorResult);
                }
            }

        } catch (Exception e) {
            log.error("Error in createOrUpdateREST for {}: {}\n{}", entityName, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Failed to " + (isUpdate ? "update" : "create") + " " + entityName, e);
        }

        return response;
    }

    /**
     * Extract child items from a value (handles List, EntityData, Map).
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractChildItems(Object value) {
        List<Map<String, Object>> items = new ArrayList<>();

        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map) {
                    items.add(extractFieldValues((Map<String, Object>) item));
                } else if (item instanceof EntityData) {
                    items.add(extractFieldValues(((EntityData) item).getValues()));
                }
            }
        } else if (value instanceof Map) {
            items.add(extractFieldValues((Map<String, Object>) value));
        } else if (value instanceof EntityData) {
            items.add(extractFieldValues(((EntityData) value).getValues()));
        }

        return items;
    }

    /**
     * Extract field values from a child map, recursively handling nested children.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFieldValues(Map<String, Object> data) {
        Map<String, Object> fieldValues = (data.containsKey("values") && data.get("values") instanceof Map)
                ? (Map<String, Object>) data.get("values")
                : data;

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
            Object value = entry.getValue();

            if (value instanceof List) {
                List<Object> processedList = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        processedList.add(extractFieldValues((Map<String, Object>) item));
                    } else {
                        processedList.add(item);
                    }
                }
                result.put(entry.getKey(), processedList);
            } else {
                result.put(entry.getKey(), value);
            }
        }

        return result;
    }

    /**
     * Fetch current parent entity with children for comparison.
     */
    private EntityData fetchCurrentParentWithChildren(OracleERPProcurementRestClient restClient,
                                                       String baseUrl, String endpoint, String entityName,
                                                       String parentId, AuthConfig auth) {
        try {
            String url = restClient.buildGetByIdUrl(baseUrl, endpoint, entityName, parentId);
            return restClient.getById(url, auth);
        } catch (Exception e) {
            log.warn("Could not fetch parent {} for comparison: {}", parentId, e.getMessage());
            return null;
        }
    }

    /**
     * Extract existing child IDs from current parent entity.
     */
    @SuppressWarnings("unchecked")
    private Set<String> getExistingChildIds(EntityData currentParent, String childType, String childIdField) {
        Set<String> existingIds = new HashSet<>();

        if (currentParent == null) {
            return existingIds;
        }

        Object childCollection = currentParent.getValues().get(childType);
        if (childCollection == null) {
            return existingIds;
        }

        List<?> children = null;
        if (childCollection instanceof List) {
            children = (List<?>) childCollection;
        }

        if (children != null) {
            for (Object child : children) {
                String childId = null;
                if (child instanceof EntityData) {
                    Object idValue = ((EntityData) child).getValues().get(childIdField);
                    if (idValue != null) {
                        childId = idValue.toString();
                    }
                } else if (child instanceof Map) {
                    Map<String, Object> childMap = (Map<String, Object>) child;
                    if (childMap.containsKey("values") && childMap.get("values") instanceof Map) {
                        childMap = (Map<String, Object>) childMap.get("values");
                    }
                    Object idValue = childMap.get(childIdField);
                    if (idValue != null) {
                        childId = idValue.toString();
                    }
                }
                if (childId != null && !childId.isEmpty()) {
                    existingIds.add(childId);
                }
            }
        }

        return existingIds;
    }

    /**
     * Get existing child data by ID from current parent entity.
     * Returns the child's field values as a Map for comparison.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getExistingChildData(EntityData currentParent, String childType, String childIdField, String targetChildId) {
        if (currentParent == null || targetChildId == null) {
            return Collections.emptyMap();
        }

        Object childCollection = currentParent.getValues().get(childType);
        if (childCollection == null) {
            return Collections.emptyMap();
        }

        List<?> children = null;
        if (childCollection instanceof List) {
            children = (List<?>) childCollection;
        }

        if (children != null) {
            for (Object child : children) {
                Map<String, Object> childMap = null;
                if (child instanceof EntityData) {
                    childMap = ((EntityData) child).getValues();
                } else if (child instanceof Map) {
                    childMap = (Map<String, Object>) child;
                    if (childMap.containsKey("values") && childMap.get("values") instanceof Map) {
                        childMap = (Map<String, Object>) childMap.get("values");
                    }
                }

                if (childMap != null) {
                    Object idValue = childMap.get(childIdField);
                    if (idValue != null && targetChildId.equals(idValue.toString())) {
                        return childMap;
                    }
                }
            }
        }

        return Collections.emptyMap();
    }

    /**
     * Check if there are real changes between incoming and existing data.
     * Compares only fields that exist in Oracle's data (existing), ignoring Syncari metadata.
     * Returns true if any Oracle field value differs.
     */
    private boolean hasRealChanges(Map<String, Object> incoming, Map<String, Object> existing) {
        if (existing == null || existing.isEmpty()) {
            return true; // No existing data means this is effectively new
        }
        if (incoming == null || incoming.isEmpty()) {
            return false;
        }

        // Iterate over EXISTING fields (Oracle fields only) - ignores Syncari metadata in incoming
        for (String key : existing.keySet()) {
            Object existingValue = existing.get(key);

            // Skip child collections - they're processed separately
            if (existingValue instanceof List || existingValue instanceof Map) {
                continue;
            }

            Object incomingValue = incoming.get(key);

            // Compare as strings to handle type differences (e.g., Long vs String)
            String existingStr = existingValue != null ? existingValue.toString() : null;
            String incomingStr = incomingValue != null ? incomingValue.toString() : null;

            if (!Objects.equals(existingStr, incomingStr)) {
                log.debug("Change detected in field '{}': '{}' -> '{}'", key, existingStr, incomingStr);
                return true;
            }
        }

        return false;
    }

    /**
     * Filter data map to only include fields that exist in the schema and are not system/syncariDefined.
     * Recursively handles nested children (grandchildren, etc.).
     *
     * @param data The data map to filter
     * @param schema The entity schema to filter against
     * @return Filtered map containing only valid Oracle fields
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> filterToSchemaFields(Map<String, Object> data, EntitySchema schema) {
        if (schema == null || data == null) {
            return data;
        }

        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String fieldName = entry.getKey();
            Optional<AttributeSchema> attr = schema.getField(fieldName);

            // Only include if field exists in schema and is NOT system/syncariDefined
            if (attr.isPresent() && !attr.get().isSystem() && !attr.get().isSyncariDefined()) {
                Object value = entry.getValue();

                // If this is a child collection (List), recursively filter grandchildren
                if (value instanceof List && attr.get().getChildSchema() != null) {
                    EntitySchema childSchema = attr.get().getChildSchema();
                    List<Map<String, Object>> filteredList = new ArrayList<>();
                    for (Object item : (List<?>) value) {
                        if (item instanceof Map) {
                            filteredList.add(filterToSchemaFields((Map<String, Object>) item, childSchema));
                        }
                    }
                    filtered.put(fieldName, filteredList);
                } else {
                    filtered.put(fieldName, value);
                }
            }
        }

        return filtered;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        String entityName = request.getEntityName();

        if (NO_DELETE_ENTITIES.contains(entityName)) {
            throw new RuntimeException("Delete not supported for entity: " + entityName +
                    ". Per Oracle docs, this entity does not have a DELETE operation.");
        }

        throw new RuntimeException("Delete not supported for entity: " + entityName);
    }

    // ===========================================
    // METADATA METHODS
    // ===========================================

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        String entityName = request.getEntity();

        if (!REST_ENTITIES.contains(entityName)) {
            log.error("Unknown entity: {}", entityName);
            return Optional.empty();
        }

        return getRESTEntitySchema(entityName, request.getConnector());
    }

    /**
     * Dynamically build entity schema from REST describe endpoint.
     * Includes child objects from CHILD_COLLECTIONS config.
     *
     * IMPORTANT: Only fields explicitly in CHILD_COLLECTIONS are typed as "child".
     * All other complex/object types are treated as "string" to avoid
     * "Cannot convert to child" errors (same issue as SOAP connector).
     *
     * @see <a href="https://docs.oracle.com/en/cloud/saas/procurement/24b/fapra/Quick_Start.html">Oracle Procurement REST API</a>
     */
    @SuppressWarnings("unchecked")
    private Optional<EntitySchema> getRESTEntitySchema(String entityName, ConnectorInfo connectorInfo) {
        try {
            OracleERPProcurementRestClient restClient = getRESTClient();
            String endpoint = ENTITY_TO_REST_ENDPOINT.get(entityName);
            String baseUrl = getBaseUrl(connectorInfo.getAuthConfig());

            // Fetch schema including children
            Map<String, Object> describeResult = restClient.fetchDescribeSchemaWithChildren(
                    baseUrl, endpoint, entityName, connectorInfo.getAuthConfig());

            List<Map<String, Object>> apiAttributes = (List<Map<String, Object>>) describeResult.get("attributes");
            Map<String, Object> childrenSchemas = (Map<String, Object>) describeResult.get("children");

            EntitySchema schema = new EntitySchema(entityName);
            schema.setDisplayName(ENTITY_DISPLAY_NAMES.getOrDefault(entityName, entityName));
            schema.setPluralName(ENTITY_PLURAL_NAMES.getOrDefault(entityName, entityName));
            schema.setReadOnly(false);

            List<AttributeSchema> attributes = new ArrayList<>();
            String idFieldName = SUPPORTED_ID_FIELDS.get(entityName);
            Set<String> childCollections = restClient.getChildCollections(entityName);

            // Parse parent attributes
            for (Map<String, Object> apiAttr : apiAttributes) {
                String attrName = (String) apiAttr.get("name");
                String oracleType = (String) apiAttr.get("type");

                // Apply CHILD_COLLECTIONS rule: only type as "child" if in config
                String attrType;
                if (childCollections.contains(attrName)) {
                    attrType = "child";
                } else {
                    attrType = mapOracleTypeToSyncari(oracleType);
                }

                boolean isUpdatable = Boolean.TRUE.equals(apiAttr.get("updatable"));
                boolean isRequired = Boolean.TRUE.equals(apiAttr.get("mandatory"));

                AttributeSchema attr = new AttributeSchema(attrName, attrType);
                attr.setDisplayName(createDisplayName(attrName));
                attr.setUpdateable(isUpdatable);
                attr.setNillable(!isRequired);

                if (attrName.equalsIgnoreCase(idFieldName)) {
                    attr.setIdField(true);
                    attr.setSystem(true);
                }
                if (attrName.equalsIgnoreCase("CreationDate")) {
                    attr.setCreatedAtField(true);
                    attr.setSystem(true);
                }
                if (attrName.equalsIgnoreCase("LastUpdateDate")) {
                    attr.setWatermarkField(true);
                    attr.setUpdatedAtField(true);
                    attr.setSystem(true);
                }

                attributes.add(attr);
            }

            // Add child schemas from describe response
            for (String childName : childCollections) {
                Map<String, Object> childSchemaData = (Map<String, Object>) childrenSchemas.get(childName);

                AttributeSchema childAttr = new AttributeSchema(childName, "child");
                childAttr.setDisplayName(createDisplayName(childName));
                childAttr.setNillable(true);
                childAttr.setUpdateable(true);
                childAttr.setMultiValueField(true);

                EntitySchema childEntitySchema = buildChildEntitySchema(
                        childName, entityName, childSchemaData, restClient);
                childAttr.setChildSchema(childEntitySchema);

                attributes.add(childAttr);
                log.debug("Added child schema for '{}' with {} attributes",
                        childName, childEntitySchema != null && childEntitySchema.getAttributes() != null ? childEntitySchema.getAttributes().size() : 0);
            }

            schema.setAttributes(attributes);
            log.info("Built REST schema for {} with {} attributes (including children) from describe endpoint",
                    entityName, attributes.size());
            return Optional.of(schema);

        } catch (Exception e) {
            log.error("Error getting REST entity schema for {}: {}", entityName, e.getMessage());
            log.error(ExceptionUtils.getStackTrace(e));
            return Optional.empty();
        }
    }

    /**
     * Build child entity schema from describe data.
     * Applies CHILD_COLLECTIONS rule for grandchildren.
     */
    @SuppressWarnings("unchecked")
    private EntitySchema buildChildEntitySchema(String childName, String parentEntityName,
                                                 Map<String, Object> childSchemaData,
                                                 OracleERPProcurementRestClient restClient) {
        String childIdField = restClient.getChildIdField(childName);
        String parentIdField = SUPPORTED_ID_FIELDS.get(parentEntityName);

        EntitySchema childSchema = new EntitySchema(childName);
        childSchema.setChild(true);
        childSchema.setDisplayName(createDisplayName(childName));
        childSchema.setPluralName(childName);

        if (childSchemaData == null) {
            log.warn("No describe data for child '{}', schema filtering will be skipped", childName);
            return null;
        }

        List<AttributeSchema> childAttributes = new ArrayList<>();
        Set<String> grandchildCollections = restClient.getChildCollections(childName);

        List<Map<String, Object>> apiChildAttrs = (List<Map<String, Object>>) childSchemaData.get("attributes");
        if (apiChildAttrs != null) {
            for (Map<String, Object> apiAttr : apiChildAttrs) {
                String attrName = (String) apiAttr.get("name");
                String oracleType = (String) apiAttr.get("type");

                // Apply CHILD_COLLECTIONS rule for grandchildren
                String attrType;
                if (grandchildCollections.contains(attrName)) {
                    attrType = "child";
                } else {
                    attrType = mapOracleTypeToSyncari(oracleType);
                }

                boolean isUpdatable = Boolean.TRUE.equals(apiAttr.get("updatable"));
                boolean isRequired = Boolean.TRUE.equals(apiAttr.get("mandatory"));

                AttributeSchema attr = new AttributeSchema(attrName, attrType);
                attr.setDisplayName(createDisplayName(attrName));
                attr.setUpdateable(isUpdatable);
                attr.setNillable(!isRequired);

                if (attrName.equalsIgnoreCase(childIdField)) {
                    attr.setIdField(true);
                    attr.setSystem(true);
                }
                if (attrName.equalsIgnoreCase("CreationDate")) {
                    attr.setCreatedAtField(true);
                    attr.setSystem(true);
                }
                if (attrName.equalsIgnoreCase("LastUpdateDate")) {
                    attr.setWatermarkField(true);
                    attr.setUpdatedAtField(true);
                    attr.setSystem(true);
                }
                if (attrName.equalsIgnoreCase(parentIdField)) {
                    attr.setDataType("reference");
                    attr.setReferenceTo(parentEntityName);
                }

                childAttributes.add(attr);
            }
        }

        // Handle grandchildren (e.g., assignments under sites)
        Map<String, Object> grandchildrenData = (Map<String, Object>) childSchemaData.get("children");
        if (grandchildrenData != null) {
            for (String grandchildName : grandchildCollections) {
                Map<String, Object> grandchildSchemaData = (Map<String, Object>) grandchildrenData.get(grandchildName);

                AttributeSchema grandchildAttr = new AttributeSchema(grandchildName, "child");
                grandchildAttr.setDisplayName(createDisplayName(grandchildName));
                grandchildAttr.setNillable(true);
                grandchildAttr.setUpdateable(true);
                grandchildAttr.setMultiValueField(true);

                EntitySchema grandchildSchema = buildGrandchildEntitySchema(
                        grandchildName, childName, childIdField, grandchildSchemaData, restClient);
                grandchildAttr.setChildSchema(grandchildSchema);

                childAttributes.add(grandchildAttr);
                log.debug("Added grandchild schema for '{}' under '{}'", grandchildName, childName);
            }
        }

        childSchema.setAttributes(childAttributes);
        return childSchema;
    }

    /**
     * Build grandchild entity schema from describe data.
     */
    @SuppressWarnings("unchecked")
    private EntitySchema buildGrandchildEntitySchema(String grandchildName, String parentChildName,
                                                      String parentChildIdField,
                                                      Map<String, Object> grandchildSchemaData,
                                                      OracleERPProcurementRestClient restClient) {
        String grandchildIdField = restClient.getChildIdField(grandchildName);

        if (grandchildSchemaData == null) {
            log.warn("No describe data for grandchild '{}', schema filtering will be skipped", grandchildName);
            return null;
        }

        EntitySchema grandchildSchema = new EntitySchema(grandchildName);
        grandchildSchema.setChild(true);
        grandchildSchema.setDisplayName(createDisplayName(grandchildName));
        grandchildSchema.setPluralName(grandchildName);

        List<AttributeSchema> grandchildAttributes = new ArrayList<>();

        List<Map<String, Object>> apiGrandchildAttrs = (List<Map<String, Object>>) grandchildSchemaData.get("attributes");
        if (apiGrandchildAttrs != null) {
            for (Map<String, Object> apiAttr : apiGrandchildAttrs) {
                String attrName = (String) apiAttr.get("name");
                String oracleType = (String) apiAttr.get("type");
                String attrType = mapOracleTypeToSyncari(oracleType);

                boolean isUpdatable = Boolean.TRUE.equals(apiAttr.get("updatable"));
                boolean isRequired = Boolean.TRUE.equals(apiAttr.get("mandatory"));

                AttributeSchema attr = new AttributeSchema(attrName, attrType);
                attr.setDisplayName(createDisplayName(attrName));
                attr.setUpdateable(isUpdatable);
                attr.setNillable(!isRequired);

                if (attrName.equalsIgnoreCase(grandchildIdField)) {
                    attr.setIdField(true);
                    attr.setSystem(true);
                }
                if (attrName.equalsIgnoreCase("CreationDate")) {
                    attr.setCreatedAtField(true);
                    attr.setSystem(true);
                }
                if (attrName.equalsIgnoreCase("LastUpdateDate")) {
                    attr.setWatermarkField(true);
                    attr.setUpdatedAtField(true);
                    attr.setSystem(true);
                }
                if (attrName.equalsIgnoreCase(parentChildIdField)) {
                    attr.setDataType("reference");
                    attr.setReferenceTo(parentChildName);
                }

                grandchildAttributes.add(attr);
            }
        }

        grandchildSchema.setAttributes(grandchildAttributes);
        return grandchildSchema;
    }

    /**
     * Map Oracle REST API data types to Syncari types.
     * Per Oracle docs: https://docs.oracle.com/en/cloud/saas/applications-common/25c/farca/Data_Types.html
     */
    private String mapOracleTypeToSyncari(String oracleType) {
        if (oracleType == null) return "string";

        switch (oracleType.toLowerCase()) {
            // Integer types (32-bit)
            case "integer":
            case "int":
                return "integer";

            // Long types (64-bit)
            case "long":
                return "long";

            // Decimal/floating point types
            case "number":
            case "decimal":
            case "float":
            case "double":
                return "double";

            // Boolean
            case "boolean":
                return "boolean";

            // Date only
            case "date":
                return "date";

            // Date with time
            case "datetime":
            case "timestamp":
                return "datetime";

            // Complex object type
            case "object":
                return "object";

            // String types (including long text)
            case "string":
            case "long text":
            case "null":
            default:
                return "string";
        }
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> entitySchemaList = new ArrayList<>();

        for (String entityName : REST_ENTITIES) {
            try {
                Optional<EntitySchema> entitySchema = describe(
                        new DescribeRequest(request.getConnector(), entityName));
                entitySchema.ifPresent(entitySchemaList::add);
            } catch (Exception e) {
                log.error("Failed to describe entity '{}': {}", entityName, e.getMessage());
                log.error(ExceptionUtils.getStackTrace(e));
            }
        }

        return entitySchemaList;
    }

    private String createDisplayName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }

        StringBuilder displayName = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                displayName.append(" ");
            }
            if (i == 0) {
                displayName.append(Character.toUpperCase(c));
            } else {
                displayName.append(c);
            }
        }

        return displayName.toString();
    }

    // ===========================================
    // UNSUPPORTED OPERATIONS
    // ===========================================

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support create Object field");
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support delete field");
    }

    // ===========================================
    // AUTH & CONFIG
    // ===========================================

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getEndpointField(), ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    public String getName() {
        return Constants.ORACLE_ERP_PROCUREMENT;
    }

    @Override
    public String getCategory() {
        return "Productivity";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/oraclepim.svg")
                .setDisplayName("Oracle ERP Procurement")
                .setBackgroundColor("#F8F8F8")
                .setHelpUrl("");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }

    @Override
    public boolean isSink() {
        return true;
    }
}
