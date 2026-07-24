package com.syncari.connector.OracleErpReceivables;

import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.data.iterator.DefaultDataOffsetIterator;
import com.syncari.connector.data.iterator.OracleERPIncrementalIterator;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Component(Constants.ORACLE_ERP_RECEIVABLES)
public class OracleERPReceivablesService implements CommonDataService, MetadataService, SynapseInfoService, AuthenticationService {

    // ===========================================
    // PRIMARY ENTITY NAMES (per Oracle API docs)
    // ===========================================

    public static final String CUSTOMER_ACCOUNT_ENTITY_NAME = "customer_accounts";      // HZ_CUST_ACCOUNTS
    public static final String CUSTOMER_PARTY_ENTITY_NAME = "customer_parties";         // HZ_PARTIES (Organizations)
    public static final String CUSTOMER_PARTY_SITE_ENTITY_NAME = "customer_party_sites"; // HZ_LOCATIONS
    public static final String PAYMENT_TERMS_ENTITY_NAME = "payment_terms";             // RA_TERMS_B (READ-ONLY)

    // ===========================================
    // SOAP SERVICE/RESOURCE NAMES
    // ===========================================

    public static final String CUSTOMER_ACCOUNT = "CustomerAccount";
    public static final String CUSTOMER_PARTY = "Organization";
    public static final String CUSTOMER_PARTY_SITE = "Location";
    public static final String PAYMENT_TERMS_ENDPOINT = "payablesPaymentTerms";
    public static final String REST_API_VERSION = "11.13.18.05";

    // ===========================================
    // PROTOCOL CATEGORIZATION
    // ===========================================

    // SOAP entities with full CRUD support
    public static final Set<String> SOAP_ENTITIES = Set.of(
            CUSTOMER_ACCOUNT_ENTITY_NAME,
            CUSTOMER_PARTY_ENTITY_NAME,
            CUSTOMER_PARTY_SITE_ENTITY_NAME
    );

    // REST entities
    public static final Set<String> REST_ENTITIES = Set.of(
            PAYMENT_TERMS_ENTITY_NAME
    );

    // READ-ONLY entities (no create/update/delete)
    public static final Set<String> READ_ONLY_ENTITIES = Set.of(
            PAYMENT_TERMS_ENTITY_NAME  // Per Oracle docs: "Payment terms are setup/reference data managed via Oracle Fusion UI only"
    );

    // Entities that do NOT support delete
    public static final Set<String> NO_DELETE_ENTITIES = Set.of(
            PAYMENT_TERMS_ENTITY_NAME
    );

    // ===========================================
    // ENTITY MAPPINGS
    // ===========================================

    private static final Map<String, String> ENTITY_TO_SOAP_RESOURCE = Map.of(
            CUSTOMER_ACCOUNT_ENTITY_NAME, CUSTOMER_ACCOUNT,
            CUSTOMER_PARTY_ENTITY_NAME, CUSTOMER_PARTY,
            CUSTOMER_PARTY_SITE_ENTITY_NAME, CUSTOMER_PARTY_SITE
    );

    private static final Map<String, String> ENTITY_TO_REST_ENDPOINT = Map.of(
            PAYMENT_TERMS_ENTITY_NAME, PAYMENT_TERMS_ENDPOINT
    );

    // ===========================================
    // DISPLAY NAMES
    // ===========================================

    public static final Map<String, String> ENTITY_DISPLAY_NAMES = Map.of(
            CUSTOMER_ACCOUNT_ENTITY_NAME, "Customer Account",
            CUSTOMER_PARTY_ENTITY_NAME, "Customer Party",
            CUSTOMER_PARTY_SITE_ENTITY_NAME, "Location",
            PAYMENT_TERMS_ENTITY_NAME, "Payment Term"
    );

    public static final Map<String, String> ENTITY_PLURAL_NAMES = Map.of(
            CUSTOMER_ACCOUNT_ENTITY_NAME, "Customer Accounts",
            CUSTOMER_PARTY_ENTITY_NAME, "Customer Parties",
            CUSTOMER_PARTY_SITE_ENTITY_NAME, "Locations",
            PAYMENT_TERMS_ENTITY_NAME, "Payment Terms"
    );

    // ===========================================
    // FIELD MAPPINGS (per Oracle API docs)
    // ===========================================

    private static final Map<String, String> SUPPORTED_ID_FIELDS = Map.of(
            // SOAP entities
            CUSTOMER_ACCOUNT_ENTITY_NAME, "CustomerAccountId",
            CUSTOMER_PARTY_ENTITY_NAME, "PartyId",
            CUSTOMER_PARTY_SITE_ENTITY_NAME, "LocationId",
            // REST entities
            PAYMENT_TERMS_ENTITY_NAME, "termsId"
    );

    // Watermark fields - for entities that support incremental sync
    private static final Map<String, String> SUPPORTED_WM_FIELDS = Map.of(
            // SOAP entities
            CUSTOMER_ACCOUNT_ENTITY_NAME, "LastUpdateDate",
            CUSTOMER_PARTY_ENTITY_NAME, "LastUpdateDate",
            CUSTOMER_PARTY_SITE_ENTITY_NAME, "LastUpdateDate",
            // REST entities
            PAYMENT_TERMS_ENTITY_NAME, "LastUpdateDate"
    );

    public static final int API_MAX_PAGESIZE = 200;
    public static final int REST_API_MAX_PAGESIZE = 500;

    protected OracleERPGenericSOAPClient getSOAPClient(AuthConfig authConfig) {
        return new OracleERPGenericSOAPClient(authConfig);
    }

    protected OracleERPReceivablesRestClient getRESTClient() {
        return new OracleERPReceivablesRestClient();
    }

    private String getBaseUrl(AuthConfig authConfig) {
        return authConfig.getEndpoint();
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();

        try {
            OracleERPGenericSOAPClient soapClient = getSOAPClient(config.getAuthConfig());
            soapClient.findByWatermark(CUSTOMER_ACCOUNT_ENTITY_NAME, 0L, System.currentTimeMillis(), 1, 0);
        } catch (Exception e) {
            handleAuthenticationErrorMessage(result, e);
        }

        return result;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        String entityName = request.getEntityName();

        if (REST_ENTITIES.contains(entityName)) {
            return getByWatermarkREST(request, entityName);
        }

        switch (entityName) {
            case CUSTOMER_ACCOUNT_ENTITY_NAME:
                // Use OracleERPIncrementalIterator to handle duplicate watermarks properly
                return getByWatermarkWithOffsetIterator(request, CUSTOMER_ACCOUNT_ENTITY_NAME, "customer accounts");
            case CUSTOMER_PARTY_ENTITY_NAME:
                // Use OracleERPIncrementalIterator to handle duplicate watermarks properly
                return getByWatermarkWithOffsetIterator(request, CUSTOMER_PARTY_ENTITY_NAME, "customer parties");
            case CUSTOMER_PARTY_SITE_ENTITY_NAME:
                return getByWatermarkWithOffsetIterator(request, CUSTOMER_PARTY_SITE_ENTITY_NAME, "locations");
            default:
                throw new IllegalArgumentException(String.format("Entity %s is not supported by Oracle ERP Receivables", entityName));
        }
    }

    /**
     * Fetch REST entity data with incremental sync using LastUpdateDate filtering.
     */
    private FetchResponse getByWatermarkREST(SyncRequest request, String entityName) {
        String displayName = ENTITY_DISPLAY_NAMES.getOrDefault(entityName, entityName);
        OracleERPReceivablesRestClient restClient = getRESTClient();

        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            try {
                String endpoint = ENTITY_TO_REST_ENDPOINT.get(entityName);
                String url = restClient.buildWatermarkUrl(
                        getBaseUrl(request.getConnector().getAuthConfig()),
                        endpoint,
                        entityName,
                        wm.getStart(),
                        wm.getEnd(),
                        pageSize,
                        offset
                );

                log.debug("REST incremental sync URL: {}", url);
                DataWithOffset result = restClient.getDataWithOffset(url, offset, request);
                List<EntityData> data = result.getData();
                return Pair.of((long) data.size(), data.stream());
            } catch (Exception e) {
                log.error("Error fetching {} : {}\n{}", displayName, e.getMessage(), ExceptionUtils.getStackTrace(e));
                throw new RuntimeException("Failed to fetch " + displayName, e);
            }
        };

        int pgSize = (request.getPageSize() <= 0) ? REST_API_MAX_PAGESIZE : Math.min(request.getPageSize(), REST_API_MAX_PAGESIZE);
        WatermarkInfo watermark = request.getWatermark();

        DefaultDataIterator iterator = new DefaultDataIterator(
                watermark,
                watermark.getOffset(),
                generator,
                new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(),
                pgSize,
                watermark.getLimit()
        );

        return new FetchResponse(watermark, iterator);
    }

    private FetchResponse getByWatermarkWithDefaultIterator(SyncRequest request, String entityName, String displayName) {
        OracleERPGenericSOAPClient soapClient = getSOAPClient(request.getConnector().getAuthConfig());

        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            try {
                List<EntityData> data = soapClient.findByWatermark(
                        entityName,
                        wm.getStart(),
                        wm.getEnd(),
                        Math.max(1, pageSize),
                        offset
                );
                return Pair.of((long) data.size(), data.stream());
            } catch (Exception e) {
                log.error("Error fetching {} by watermark: {}\n{}", displayName, e.getMessage(), ExceptionUtils.getStackTrace(e));
                throw new RuntimeException("Failed to fetch " + displayName, e);
            }
        };

        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : Math.min(request.getPageSize(), API_MAX_PAGESIZE);

        DefaultDataIterator iterator = new DefaultDataIterator(
                request.getWatermark(),
                request.getWatermark().getOffset(),
                generator,
                new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(),
                pgSize,
                request.getWatermark().getLimit()
        );

        return new FetchResponse(request.getWatermark(), iterator);
    }

    private FetchResponse getByWatermarkWithOffsetIterator(SyncRequest request, String entityName, String displayName) {
        OracleERPGenericSOAPClient soapClient = getSOAPClient(request.getConnector().getAuthConfig());

        Function3<WatermarkInfo, Integer, Long, DataWithOffset> generator = (wm, pageSize, offset) -> {
            try {
                List<EntityData> data = soapClient.findByWatermark(
                        entityName,
                        wm.getStart(),
                        wm.getEnd(),
                        Math.max(1, pageSize),
                        offset
                );
                long nextOffset = data.isEmpty() ? 0 : offset + pageSize;
                return new DataWithOffset(offset, nextOffset, data, new ArrayList<>());
            } catch (Exception e) {
                log.error("Error fetching {} by watermark: {}\n{}", displayName, e.getMessage(), ExceptionUtils.getStackTrace(e));
                throw new RuntimeException("Failed to fetch " + displayName, e);
            }
        };

        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : Math.min(request.getPageSize(), API_MAX_PAGESIZE);
        WatermarkInfo wm = request.getWatermark();
        log.info("Creating OracleERPIncrementalIterator with watermark: start={}, end={}, offset={}, isResync={}",
                wm.getStart(), wm.getEnd(), wm.getOffset(), wm.isResync());
        // Use OracleERPIncrementalIterator to handle duplicate watermarks properly
        // When all records have the same LastUpdateDate, the iterator advances watermark by 1ms
        // to prevent infinite resync loops
        OracleERPIncrementalIterator iterator = new OracleERPIncrementalIterator(wm,
                wm.getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pgSize, wm.getLimit());
        return new FetchResponse(wm, iterator);
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

        if (REST_ENTITIES.contains(entityName)) {
            return getByIdsREST(request, entityName);
        }

        List<EntityData> data = new ArrayList<>();
        OracleERPGenericSOAPClient soapClient = getSOAPClient(request.getConnector().getAuthConfig());

        try {
            for (String id : request.getIds()) {
                EntityData entity = soapClient.getById(entityName, id);
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

    /**
     * Fetch REST entity data by IDs
     */
    private List<EntityData> getByIdsREST(SyncRequest request, String entityName) {
        List<EntityData> data = new ArrayList<>();
        OracleERPReceivablesRestClient restClient = getRESTClient();
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

        if (READ_ONLY_ENTITIES.contains(entityName)) {
            throw new RuntimeException("Create not supported for read-only entity: " + entityName +
                    ". Per Oracle docs, this entity is managed via Oracle Fusion UI only.");
        }

        if (SOAP_ENTITIES.contains(entityName)) {
            return createOrUpdateSOAP(request, entityName, false);
        }

        if (REST_ENTITIES.contains(entityName)) {
            return createOrUpdateREST(request, entityName, false);
        }

        throw new RuntimeException("Create not supported for entity: " + entityName);
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        String entityName = request.getEntityName();

        if (READ_ONLY_ENTITIES.contains(entityName)) {
            throw new RuntimeException("Update not supported for read-only entity: " + entityName +
                    ". Per Oracle docs, this entity is managed via Oracle Fusion UI only.");
        }

        if (SOAP_ENTITIES.contains(entityName)) {
            return createOrUpdateSOAP(request, entityName, true);
        }

        if (REST_ENTITIES.contains(entityName)) {
            return createOrUpdateREST(request, entityName, true);
        }

        throw new RuntimeException("Update not supported for entity: " + entityName);
    }

    private SyncResponse createOrUpdateREST(SyncRequest request, String entityName, boolean isUpdate) {
        if (READ_ONLY_ENTITIES.contains(entityName)) {
            throw new RuntimeException("Write operations not supported for read-only entity: " + entityName);
        }

        OracleERPReceivablesRestClient restClient = getRESTClient();
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
                    Map<String, Object> data = entityData.getValues();
                    EntityData resultEntity;

                    if (isUpdate) {
                        String id = entityData.getId();
                        if (id == null) {
                            String idField = SUPPORTED_ID_FIELDS.get(entityName);
                            if (idField != null && data.containsKey(idField) && data.get(idField) != null) {
                                id = data.get(idField).toString();
                            }
                        }
                        if (id == null) {
                            throw new RuntimeException("Entity ID is required for update operation");
                        }
                        resultEntity = restClient.update(baseUrl, endpoint, id, data, auth);
                    } else {
                        resultEntity = restClient.create(baseUrl, endpoint, data, auth);
                    }

                    String resultId = resultEntity != null ? resultEntity.getId() : entityData.getId();
                    String syncariEntityId = entityData.getSyncariEntityId();
                    Result result = new Result(true, resultId, syncariEntityId);
                    response.getResults().add(result);
                    log.info("Successfully {} {} with ID: {}", isUpdate ? "updated" : "created", entityName, resultId);

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

    private SyncResponse createOrUpdateSOAP(SyncRequest request, String entityName, boolean isUpdate) {
        OracleERPGenericSOAPClient soapClient = getSOAPClient(request.getConnector().getAuthConfig());

        if (!soapClient.isWriteSupported(entityName)) {
            throw new RuntimeException("Write operations not supported for entity: " + entityName);
        }

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
                    Map<String, Object> data = entityData.getValues();

                    if (isUpdate && entityData.getId() != null) {
                        String idField = SUPPORTED_ID_FIELDS.get(entityName);
                        if (idField != null && !data.containsKey(idField)) {
                            data.put(idField, entityData.getId());
                        }
                    }

                    EntityData resultEntity = soapClient.merge(entityName, data);
                    String resultId = resultEntity != null ? resultEntity.getId() : entityData.getId();
                    String syncariEntityId = entityData.getSyncariEntityId();
                    Result result = new Result(true, resultId, syncariEntityId);
                    response.getResults().add(result);
                    log.info("Successfully {} {} with ID: {}", isUpdate ? "updated" : "created", entityName, resultId);

                } catch (Exception e) {
                    log.error("Error processing entity {}: {}", entityName, e.getMessage());
                    Result errorResult = new Result(false, entityData.getId(), entityData.getSyncariEntityId());
                    errorResult.addError("Failed to " + (isUpdate ? "update" : "create") + " entity: " + e.getMessage());
                    response.getResults().add(errorResult);
                }
            }

        } catch (Exception e) {
            log.error("Error in createOrUpdateSOAP for {}: {}\n{}", entityName, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Failed to " + (isUpdate ? "update" : "create") + " " + entityName, e);
        }

        return response;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        String entityName = request.getEntityName();

        if (NO_DELETE_ENTITIES.contains(entityName)) {
            throw new RuntimeException("Delete not supported for entity: " + entityName +
                    ". Per Oracle docs, this entity does not have a DELETE operation.");
        }

        if (SOAP_ENTITIES.contains(entityName)) {
            return deleteSOAP(request, entityName);
        }

        throw new RuntimeException("Delete not supported for entity: " + entityName);
    }

    private SyncResponse deleteSOAP(SyncRequest request, String entityName) {
        OracleERPGenericSOAPClient soapClient = getSOAPClient(request.getConnector().getAuthConfig());

        if (!soapClient.isDeleteSupported(entityName)) {
            throw new RuntimeException("Delete operations not supported for entity: " + entityName +
                    ". Consider using update with Status='I' to deactivate records.");
        }

        SyncResponse response = new SyncResponse();

        try {
            String connectorId = request.getConnector().getId();
            Map<String, List<EntityData>> dataMap = request.getData();
            List<EntityData> entitiesToDelete = dataMap != null ? dataMap.get(connectorId) : null;

            if (entitiesToDelete == null || entitiesToDelete.isEmpty()) {
                return response;
            }

            for (EntityData entityData : entitiesToDelete) {
                try {
                    String id = entityData.getId();
                    if (id == null) {
                        String idField = SUPPORTED_ID_FIELDS.get(entityName);
                        Map<String, Object> values = entityData.getValues();
                        if (idField != null && values != null && values.containsKey(idField) && values.get(idField) != null) {
                            id = values.get(idField).toString();
                        }
                    }

                    if (id == null) {
                        throw new RuntimeException("Entity ID is required for delete operation");
                    }

                    boolean success = soapClient.delete(entityName, id);
                    String syncariEntityId = entityData.getSyncariEntityId();
                    Result result = new Result(success, id, syncariEntityId);
                    if (!success) {
                        result.addError("Delete operation returned unsuccessful status");
                    }
                    response.getResults().add(result);

                    log.info("Delete operation for {} with ID {}: {}", entityName, id, success ? "SUCCESS" : "FAILED");

                } catch (Exception e) {
                    log.error("Error deleting entity {}: {}", entityName, e.getMessage());
                    Result errorResult = new Result(false, entityData.getId(), entityData.getSyncariEntityId());
                    errorResult.addError("Failed to delete entity: " + e.getMessage());
                    response.getResults().add(errorResult);
                }
            }

        } catch (Exception e) {
            log.error("Error in deleteSOAP for {}: {}\n{}", entityName, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Failed to delete " + entityName, e);
        }

        return response;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        String entityName = request.getEntity();

        if (REST_ENTITIES.contains(entityName)) {
            return getRESTEntitySchema(entityName, request.getConnector());
        }

        return getSOAPEntitySchema(entityName, request.getConnector());
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        ConnectorInfo connectorInfo = request.getConnector();
        List<EntitySchema> entitySchemaList = new ArrayList<>();

        ENTITY_TO_SOAP_RESOURCE.keySet().forEach(entityName -> {
            try {
                Optional<EntitySchema> entitySchema = getSOAPEntitySchema(entityName, connectorInfo);
                entitySchema.ifPresent(entitySchemaList::add);
            } catch (Exception e) {
                log.error("Failed to describe SOAP entity '{}'. Continuing with remaining entities: {}",
                        entityName, e.getMessage());
                log.error(ExceptionUtils.getStackTrace(e));
            }
        });

        REST_ENTITIES.forEach(entityName -> {
            try {
                Optional<EntitySchema> entitySchema = getRESTEntitySchema(entityName, connectorInfo);
                entitySchema.ifPresent(entitySchemaList::add);
            } catch (Exception e) {
                log.error("Failed to describe REST entity '{}'. Continuing with remaining entities: {}",
                        entityName, e.getMessage());
                log.error(ExceptionUtils.getStackTrace(e));
            }
        });

        return entitySchemaList;
    }

    private Optional<EntitySchema> getSOAPEntitySchema(String entityName, ConnectorInfo connectorInfo) {
        try {
            return createEntitySchema(entityName, connectorInfo);
        } catch (Exception e) {
            log.error("Error getting SOAP entity schema for {}: {}", entityName, e.getMessage());
            log.error(ExceptionUtils.getStackTrace(e));
            return Optional.empty();
        }
    }

    private Optional<EntitySchema> createEntitySchema(String entityName, ConnectorInfo connectorInfo) {
        EntitySchema entitySchema = new EntitySchema(entityName);
        entitySchema.setDisplayName(ENTITY_DISPLAY_NAMES.getOrDefault(entityName, entityName));
        entitySchema.setPluralName(ENTITY_PLURAL_NAMES.getOrDefault(entityName, entityName));

        OracleERPGenericSOAPClient soapClient = getSOAPClient(connectorInfo.getAuthConfig());
        boolean isWriteSupported = soapClient.isWriteSupported(entityName);
        entitySchema.setReadOnly(!isWriteSupported);

        try {
            String soapResource = ENTITY_TO_SOAP_RESOURCE.get(entityName);
            if (soapResource == null) {
                log.error("No SOAP resource mapping found for entity: {}", entityName);
                return Optional.empty();
            }

            List<Map<String, Object>> entityList = soapClient.getEntitySchema(entityName);

            boolean requiresValidation = CUSTOMER_PARTY_ENTITY_NAME.equals(entityName) || CUSTOMER_PARTY_SITE_ENTITY_NAME.equals(entityName);
            if (requiresValidation) {
                log.debug("Schema fetch result: {} fields found", entityList != null ? entityList.size() : 0);

                if (entityList == null || entityList.isEmpty()) {
                    log.error("No fields returned from SOAP schema for {} entity", soapResource);
                    return Optional.empty();
                }
            }

            List<AttributeSchema> attributes = convertSOAPSchemaToAttributes(entityList, entityName);
            entitySchema.setAttributes(attributes);

            return Optional.of(entitySchema);

        } catch (Exception e) {
            String displayName = ENTITY_DISPLAY_NAMES.getOrDefault(entityName, entityName).toLowerCase();
            log.error("Could not get dynamic schema for {} from SOAP: {}", displayName, e.getMessage());
            log.error(ExceptionUtils.getStackTrace(e));
            return Optional.empty();
        }
    }

    /**
     * Convert SOAP schema (from XSD parsing) to Syncari AttributeSchema list.
     *
     * Handles child/nested objects by:
     * 1. Setting dataType="child" for complex types
     * 2. Setting isMultiValueField=true for arrays (maxOccurs > 1)
     * 3. Creating nested EntitySchema for child objects via childSchema property
     *
     * @param entityList List of field definitions from XSD parsing
     * @param entityName The parent entity name
     * @return List of AttributeSchema including child schemas
     */
    private List<AttributeSchema> convertSOAPSchemaToAttributes(List<Map<String, Object>> entityList,
                                                                String entityName) {
        List<AttributeSchema> attributes = new ArrayList<>();

        String supportedIdField = SUPPORTED_ID_FIELDS.get(entityName);

        if (entityList == null || entityList.isEmpty()) {
            String errorMsg = String.format("SOAP schema discovery failed for entity %s - no fields found from WSDL", entityName);
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        for (Map<String, Object> fieldDef : entityList) {
            try {
                String fieldName = (String) fieldDef.get("name");
                String fieldType = (String) fieldDef.get("type");
                Boolean required = (Boolean) fieldDef.getOrDefault("required", false);
                Boolean isArray = (Boolean) fieldDef.getOrDefault("isArray", false);
                Boolean isComplexType = (Boolean) fieldDef.getOrDefault("isComplexType", false);
                Boolean isChildEntity = (Boolean) fieldDef.getOrDefault("isChildEntity", false);
                String syncariType = (String) fieldDef.getOrDefault("syncariType", "STRING");
                String childEntityName = (String) fieldDef.get("childEntityName");

                if (fieldName == null || fieldName.isEmpty()) {
                    continue;
                }

                // Only true child entities get "child" type
                // Complex types that are NOT child entities (flexfields, DFFs) get "string" type
                String dataType;
                if (isChildEntity) {
                    dataType = "child";
                } else if (isComplexType) {
                    // Flexfields/DFFs are complex in WSDL but return simple values - treat as string
                    dataType = "string";
                    log.debug("Complex type {} is not a child entity, treating as string", fieldName);
                } else {
                    dataType = mapSyncariTypeToDataType(syncariType);
                }

                AttributeSchema attribute = new AttributeSchema(fieldName, dataType);
                attribute.setDisplayName(createDisplayName(fieldName));
                attribute.setNillable(!required);
                attribute.setUpdateable(false);

                if (isArray && !isChildEntity) {
                    attribute.setMultiValueField(true);
                }

                if (fieldName.equalsIgnoreCase(supportedIdField)) {
                    attribute.setIdField(true).setSystem(true);
                } else if (fieldName.equals("LastUpdateDate")) {
                    attribute.setWatermarkField(true).setUpdatedAtField(true).setSystem(true);
                } else if (fieldName.equals("CreationDate")) {
                    attribute.setCreatedAtField(true).setSystem(true);
                } else if (!isComplexType && fieldName.contains("Id") && !fieldName.equals("RequestId")) {
                    attribute.setDataType("reference");
                }

                if (isChildEntity && childEntityName != null) {
                    EntitySchema childSchema = createChildEntitySchema(childEntityName, entityName);
                    attribute.setChildSchema(childSchema);
                    log.debug("Created child schema for field {} -> child entity {}", fieldName, childEntityName);
                }

                attributes.add(attribute);

            } catch (Exception e) {
                log.error("Error processing SOAP field {}: {}\n{}", fieldDef, e.getMessage(), ExceptionUtils.getStackTrace(e));
            }
        }

        boolean hasIdField = attributes.stream().anyMatch(attr -> attr.isIdField());
        boolean hasWatermarkField = attributes.stream().anyMatch(attr -> attr.isWatermarkField());

        if (!hasIdField) {
            String errorMsg = String.format("SOAP schema discovery failed for entity %s - missing required ID field %s", entityName, supportedIdField);
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        if (!hasWatermarkField) {
            String errorMsg = String.format("SOAP schema discovery failed for entity %s - missing required watermark field LastUpdateDate", entityName);
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        log.debug("Successfully processed {} total attributes for SOAP entity {}", attributes.size(), entityName);

        return attributes;
    }

    /**
     * Create a child EntitySchema for nested objects.
     * Child schemas are marked with child=true and have their own attributes.
     *
     * @param childEntityName The child entity type name (e.g., "CustomerAccountSite")
     * @param parentEntityName The parent entity name for context
     * @return EntitySchema for the child entity
     */
    private EntitySchema createChildEntitySchema(String childEntityName, String parentEntityName) {
        EntitySchema childSchema = new EntitySchema(childEntityName);
        childSchema.setChild(true);
        childSchema.setDisplayName(createDisplayName(childEntityName));
        childSchema.setPluralName(childEntityName + "s");

        // Try to get child schema from SOAP client (XSD parsing)
        try {
            String soapResource = ENTITY_TO_SOAP_RESOURCE.get(parentEntityName);
            if (soapResource != null) {
                // Child schemas use basic fields - full schema populated during WSDL parsing
                List<AttributeSchema> childAttributes = new ArrayList<>();
                String childIdField = childEntityName + "Id";
                childAttributes.add(new AttributeSchema(childIdField, "long")
                        .setIdField(true)
                        .setSystem(true)
                        .setDisplayName(createDisplayName(childIdField)));

                childAttributes.add(new AttributeSchema("LastUpdateDate", "datetime")
                        .setWatermarkField(true)
                        .setUpdatedAtField(true)
                        .setSystem(true)
                        .setDisplayName("Last Update Date"));

                childAttributes.add(new AttributeSchema("CreationDate", "datetime")
                        .setCreatedAtField(true)
                        .setSystem(true)
                        .setDisplayName("Creation Date"));

                String parentIdField = SUPPORTED_ID_FIELDS.get(parentEntityName);
                if (parentIdField != null) {
                    childAttributes.add(new AttributeSchema(parentIdField, "reference")
                            .setDisplayName(createDisplayName(parentIdField))
                            .setReferenceTo(parentEntityName));
                }

                childSchema.setAttributes(childAttributes);
            }
        } catch (Exception e) {
            log.warn("Could not fully populate child schema for {}: {}", childEntityName, e.getMessage());
        }

        return childSchema;
    }

    private String mapSyncariTypeToDataType(String syncariType) {
        if (syncariType == null) {
            return "string";
        }
        String baseType = syncariType.replace("_ARRAY", "");

        switch (baseType.toUpperCase()) {
            case "STRING":
                return "string";
            case "INTEGER":
            case "INT":
                return "integer";
            case "LONG":
                return "long";
            case "DOUBLE":
            case "FLOAT":
            case "DECIMAL":
                return "double";
            case "BOOLEAN":
                return "boolean";
            case "DATE":
                return "date";
            case "DATETIME":
                return "datetime";
            case "OBJECT":
            case "OBJECT_ARRAY":
                return "child";
            default:
                return "string";
        }
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

    private Optional<EntitySchema> getRESTEntitySchema(String entityName, ConnectorInfo connectorInfo) {
        try {
            OracleERPReceivablesRestClient restClient = getRESTClient();
            String endpoint = ENTITY_TO_REST_ENDPOINT.get(entityName);
            String baseUrl = getBaseUrl(connectorInfo.getAuthConfig());
            List<Map<String, Object>> apiAttributes = restClient.fetchDescribeSchema(baseUrl, endpoint, connectorInfo.getAuthConfig());

            EntitySchema schema = new EntitySchema(entityName);
            schema.setDisplayName(ENTITY_DISPLAY_NAMES.getOrDefault(entityName, entityName));
            schema.setPluralName(ENTITY_PLURAL_NAMES.getOrDefault(entityName, entityName));
            schema.setReadOnly(READ_ONLY_ENTITIES.contains(entityName));

            List<AttributeSchema> attributes = new ArrayList<>();
            String idFieldName = SUPPORTED_ID_FIELDS.get(entityName);

            for (Map<String, Object> apiAttr : apiAttributes) {
                String attrName = (String) apiAttr.get("name");
                String attrType = mapOracleTypeToSyncari((String) apiAttr.get("type"));
                boolean isUpdatable = Boolean.TRUE.equals(apiAttr.get("updatable"));
                boolean isRequired = Boolean.TRUE.equals(apiAttr.get("required"));

                AttributeSchema attr = new AttributeSchema(attrName, attrType);
                attr.setDisplayName(attrName);
                attr.setUpdateable(isUpdatable);
                attr.setNillable(!isRequired);

                if (attrName.equalsIgnoreCase(idFieldName)) {
                    attr.setIdField(true);
                    attr.setSystem(true);
                }
                if (attrName.equalsIgnoreCase("creationDate")) {
                    attr.setCreatedAtField(true);
                    attr.setSystem(true);
                }
                if (attrName.equalsIgnoreCase("lastUpdateDate")) {
                    attr.setWatermarkField(true);
                    attr.setUpdatedAtField(true);
                    attr.setSystem(true);
                }

                attributes.add(attr);
            }

            schema.setAttributes(attributes);
            log.info("Built REST schema for {} with {} attributes from describe endpoint", entityName, attributes.size());
            return Optional.of(schema);

        } catch (Exception e) {
            log.error("Error getting REST entity schema for {}: {}", entityName, e.getMessage());
            log.error(ExceptionUtils.getStackTrace(e));
            return Optional.empty();
        }
    }

    /**
     * Map Oracle REST API data types to Syncari types.
     * Per Oracle docs: https://docs.oracle.com/en/cloud/saas/applications-common/25c/farca/Data_Types.html
     *
     * Oracle types: string, date, datetime, boolean, number, integer, null, object, array, long text
     * Note: For arrays, use base type with setMultiValueField(true) flag instead of a separate type.
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

            // String types (including long text - UI handles display)
            case "string":
            case "long text":
            case "null":
            default:
                return "string";
        }
    }

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
        return Constants.ORACLE_ERP_RECEIVABLES;
    }

    @Override
    public String getCategory() {
        return "Productivity";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/oraclepim.svg")
                .setDisplayName("Oracle ERP Receivables")
                .setBackgroundColor("#F8F8F8")
                .setHelpUrl("");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }

    @Override
    public boolean isSink() { return true; }
}
