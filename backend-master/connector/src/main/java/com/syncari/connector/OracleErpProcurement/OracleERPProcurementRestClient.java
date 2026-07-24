package com.syncari.connector.OracleErpProcurement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.DataWithOffset;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * REST client for Oracle ERP Procurement connector.
 *
 * Handles REST API calls for Suppliers entity with child objects:
 * - Supplier Sites
 * - Site Assignments
 * - Supplier Contacts
 *
 * @see <a href="https://docs.oracle.com/en/cloud/saas/procurement/25c/fapra/">Oracle Fusion Cloud Procurement REST API</a>
 */
@Slf4j
public class OracleERPProcurementRestClient extends SyncariEntityDataRestClient {

    // ===========================================
    // CONSTANTS
    // ===========================================

    private static final int TIMEOUT_MILLIS = 300000;
    private static final String API_VERSION = "11.13.18.05";
    private static final String API_BASE_PATH = "/fscmRestApi/resources/" + API_VERSION;
    private static final DateTimeFormatter ORACLE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final String FIELD_CREATION_DATE = "CreationDate";
    private static final String FIELD_LAST_UPDATE_DATE = "LastUpdateDate";

    // ===========================================
    // CHILD ENTITY CONFIGURATION
    // Per Oracle REST API documentation
    // ===========================================

    /**
     * Child collection field names per entity.
     * Includes both direct children and grandchildren (nested under direct children).
     *
     * Structure:
     * - sites: Direct child of suppliers
     * - assignments: Grandchild (nested under sites) - fetched via sites.assignments expand
     * - contacts: Direct child of suppliers
     *
     * @see <a href="https://docs.oracle.com/en/cloud/saas/procurement/25c/fapra/op-suppliers-supplierid-child-sites-get.html">Suppliers API</a>
     */
    private static final Map<String, Set<String>> CHILD_COLLECTIONS = Map.of(
            "suppliers", Set.of("sites", "contacts", "addresses"),
            // Grandchildren are handled specially - assignments is nested under sites
            "sites", Set.of("assignments")
    );

    /**
     * ID field names for child entities (all levels).
     */
    private static final Map<String, String> CHILD_ID_FIELDS = Map.of(
            "sites", "SupplierSiteId",
            "contacts", "SupplierContactId",
            "assignments", "AssignmentId",
            "addresses", "SupplierAddressId"
    );

    /**
     * Expand parameter for fetching child collections.
     * Uses dot notation for grandchildren: sites.assignments
     *
     * This fetches:
     * - suppliers (parent)
     * - suppliers/sites (direct child)
     * - suppliers/sites/assignments (grandchild)
     * - suppliers/contacts (direct child)
     * - suppliers/addresses (direct child - needed for site creation)
     */
    private static final Map<String, String> EXPAND_PARAMS = Map.of(
            "suppliers", "sites,sites.assignments,contacts,addresses"
    );

    /**
     * Sort order configuration for deterministic pagination.
     * Format: "sortField:asc,idField:asc"
     * Per Oracle docs: "You must sort using unique attributes for predictable paging results"
     * Note: LastUpdateDate is not queryable (for filtering) but IS sortable with orderBy
     * @see <a href="https://docs.oracle.com/en/cloud/saas/procurement/25c/fapra/op-suppliers-get.html">Oracle Suppliers REST API</a>
     */
    private static final Map<String, String> ENTITY_SORT_ORDER = Map.of(
            "suppliers", "LastUpdateDate:asc,SupplierId:asc"
    );

    // ===========================================
    // CONSTRUCTORS
    // ===========================================

    public OracleERPProcurementRestClient() {
        super(new JsonParserConfig(null, null, null, "Id", true, null), new ObjectMapper());
    }

    // ===========================================
    // PUBLIC API - READ OPERATIONS
    // ===========================================

    /**
     * Fetch data with offset-based pagination.
     */
    public DataWithOffset getDataWithOffset(String url, Long prevOffset, SyncRequest request) {
        try {
            ResponseEntity<String> response = getResponse(url, request.getConnector().getAuthConfig());
            log.debug("REST response status: {}, body length: {}", response.getStatusCode(),
                    response.getBody() != null ? response.getBody().length() : 0);
            log.debug("REST response body: {}", response.getBody());
            ReadContext ctx = JsonPath.parse(response.getBody());
            List<Map<String, Object>> items = ctx.read("items");

            if (items.isEmpty()) {
                return DataWithOffset.emptyWithOffsets(prevOffset, prevOffset);
            }

            List<EntityData> entities = parseItems(items, request);
            boolean hasMore = ctx.read("hasMore", Boolean.class);
            long nextOffset = hasMore ? prevOffset + entities.size() : prevOffset;

            return new DataWithOffset(prevOffset, nextOffset, entities, new ArrayList<>());
        } catch (NonRetriableException e) {
            if (ErrorCodes.BAD_REQUEST.name().equals(e.getErrorCode()) && StringUtils.isBlank(e.getMessage())) {
                throw new RetriableException(e.getErrorCode(), "No error message in response", e.getStatusCode());
            }
            throw e;
        }
    }

    /**
     * Fetch single entity by ID.
     */
    public EntityData getById(String url, SyncRequest request) {
        try {
            ResponseEntity<String> response = getResponse(url, request.getConnector().getAuthConfig());
            ReadContext ctx = JsonPath.parse(response.getBody());
            Map<String, Object> attributes = ctx.json();

            if (attributes == null || attributes.isEmpty()) {
                return null;
            }

            return parseItem(attributes, request.getEntityName(),
                    request.getEntitySchema().getIdField().getApiName(),
                    getWatermarkFieldName(request));
        } catch (Exception e) {
            log.error("Error fetching entity by ID: {}\n{}", e.getMessage(), ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    /**
     * Fetch single entity by ID (simple version for internal use).
     * Used by GET + Compare pattern to fetch current parent with children.
     */
    public EntityData getById(String url, AuthConfig auth) {
        try {
            log.info("REST GET BY ID: {}", url);
            ResponseEntity<String> response = getResponse(url, auth);
            ReadContext ctx = JsonPath.parse(response.getBody());
            Map<String, Object> attributes = ctx.json();

            if (attributes == null || attributes.isEmpty()) {
                return null;
            }

            return parseItem(attributes, "suppliers", "SupplierId", FIELD_LAST_UPDATE_DATE);
        } catch (Exception e) {
            log.error("Error fetching entity by ID: {}\n{}", e.getMessage(), ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    // ===========================================
    // PUBLIC API - WRITE OPERATIONS
    // ===========================================

    /**
     * Create entity via POST.
     */
    public EntityData create(String baseUrl, String endpoint, Map<String, Object> data, AuthConfig auth) {
        String url = normalizeUrl(baseUrl) + API_BASE_PATH + "/" + endpoint;
        log.info("REST CREATE: POST {}", url);

        try {
            Map<String, Object> formattedData = formatDatesForOracle(data);
            log.debug("REST CREATE payload: {}", formattedData);
            EntityData result = post(url, formattedData, auth);

            if (result != null) {
                log.debug("REST CREATE response values: {}", result.getValues());
                if (result.getId() == null && result.getValues().containsKey("SupplierId")) {
                    Object supplierId = result.getValues().get("SupplierId");
                    if (supplierId != null) {
                        result.setId(supplierId.toString());
                    }
                }
            }

            log.info("REST CREATE successful, ID: {}", result != null ? result.getId() : "null");
            return result;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("REST CREATE failed for {}: {} - Response: {}", endpoint, e.getMessage(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to create entity: " + e.getMessage() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("REST CREATE failed for {}: {}", endpoint, e.getMessage());
            throw new RuntimeException("Failed to create entity: " + e.getMessage(), e);
        }
    }

    /**
     * Update entity via PATCH.
     */
    public EntityData update(String baseUrl, String endpoint, String id, Map<String, Object> data, AuthConfig auth) {
        String url = normalizeUrl(baseUrl) + API_BASE_PATH + "/" + endpoint + "/" + id;
        log.info("REST UPDATE: PATCH {}", url);

        try {
            Map<String, Object> formattedData = formatDatesForOracle(data);
            log.debug("REST UPDATE payload: {}", formattedData);
            EntityData result = patch(url, formattedData, auth);
            log.info("REST UPDATE successful for ID: {}", id);
            return result;
        } catch (Exception e) {
            log.error("REST UPDATE failed for {}/{}: {}", endpoint, id, e.getMessage());
            throw new RuntimeException("Failed to update entity: " + e.getMessage(), e);
        }
    }

    // ===========================================
    // CHILD ENTITY OPERATIONS
    // Oracle REST requires separate endpoints for child entities:
    // - POST   /suppliers/{id}/child/sites         (create child)
    // - PATCH  /suppliers/{id}/child/sites/{siteId} (update child)
    // - DELETE /suppliers/{id}/child/sites/{siteId} (delete child)
    // ===========================================

    /**
     * Create a child entity via POST to child endpoint.
     * Endpoint: POST /{parentEndpoint}/{parentId}/child/{childType}
     *
     * @param baseUrl Base URL
     * @param parentEndpoint Parent endpoint (e.g., "suppliers")
     * @param parentId Parent entity ID
     * @param childType Child collection name (e.g., "sites", "contacts")
     * @param data Child entity data
     * @param auth Authentication config
     * @return Created child entity
     */
    public EntityData createChild(String baseUrl, String parentEndpoint, String parentId,
                                   String childType, Map<String, Object> data, AuthConfig auth) {
        String url = normalizeUrl(baseUrl) + API_BASE_PATH + "/" + parentEndpoint + "/" + parentId + "/child/" + childType;
        log.info("REST CHILD CREATE: POST {}", url);

        try {
            Map<String, Object> formattedData = formatDatesForOracle(data);
            log.debug("REST CHILD CREATE payload: {}", formattedData);
            EntityData result = post(url, formattedData, auth);

            if (result != null && result.getId() == null) {
                String childIdField = CHILD_ID_FIELDS.getOrDefault(childType, childType + "Id");
                if (result.getValues().containsKey(childIdField)) {
                    Object childId = result.getValues().get(childIdField);
                    if (childId != null) {
                        result.setId(childId.toString());
                    }
                }
            }

            log.info("REST CHILD CREATE successful, ID: {}", result != null ? result.getId() : "null");
            return result;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("REST CHILD CREATE failed: {} - Response: {}", e.getMessage(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to create child entity: " + e.getMessage() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("REST CHILD CREATE failed: {}", e.getMessage());
            throw new RuntimeException("Failed to create child entity: " + e.getMessage(), e);
        }
    }

    /**
     * Update a child entity via PATCH to child endpoint.
     * Endpoint: PATCH /{parentEndpoint}/{parentId}/child/{childType}/{childId}
     *
     * @param baseUrl Base URL
     * @param parentEndpoint Parent endpoint (e.g., "suppliers")
     * @param parentId Parent entity ID
     * @param childType Child collection name (e.g., "sites", "contacts")
     * @param childId Child entity ID
     * @param data Child entity data to update
     * @param auth Authentication config
     * @return Updated child entity
     */
    public EntityData updateChild(String baseUrl, String parentEndpoint, String parentId,
                                   String childType, String childId, Map<String, Object> data, AuthConfig auth) {
        String url = normalizeUrl(baseUrl) + API_BASE_PATH + "/" + parentEndpoint + "/" + parentId + "/child/" + childType + "/" + childId;
        log.info("REST CHILD UPDATE: PATCH {}", url);

        try {
            Map<String, Object> formattedData = formatDatesForOracle(data);
            log.debug("REST CHILD UPDATE payload: {}", formattedData);
            EntityData result = patch(url, formattedData, auth);
            log.info("REST CHILD UPDATE successful for ID: {}", childId);
            return result;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("REST CHILD UPDATE failed: {} - Response: {}", e.getMessage(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to update child entity: " + e.getMessage() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("REST CHILD UPDATE failed: {}", e.getMessage());
            throw new RuntimeException("Failed to update child entity: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a child entity via DELETE to child endpoint.
     * Endpoint: DELETE /{parentEndpoint}/{parentId}/child/{childType}/{childId}
     *
     * @param baseUrl Base URL
     * @param parentEndpoint Parent endpoint (e.g., "suppliers")
     * @param parentId Parent entity ID
     * @param childType Child collection name (e.g., "sites", "contacts")
     * @param childId Child entity ID
     * @param auth Authentication config
     * @return true if deleted successfully
     */
    public boolean deleteChild(String baseUrl, String parentEndpoint, String parentId,
                                String childType, String childId, AuthConfig auth) {
        String url = normalizeUrl(baseUrl) + API_BASE_PATH + "/" + parentEndpoint + "/" + parentId + "/child/" + childType + "/" + childId;
        log.info("REST CHILD DELETE: DELETE {}", url);

        try {
            delete(url, auth);
            log.info("REST CHILD DELETE successful for ID: {}", childId);
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("REST CHILD DELETE failed: {} - Response: {}", e.getMessage(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to delete child entity: " + e.getMessage() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("REST CHILD DELETE failed: {}", e.getMessage());
            throw new RuntimeException("Failed to delete child entity: " + e.getMessage(), e);
        }
    }

    /**
     * Get child collection names for an entity.
     */
    public Set<String> getChildCollections(String entityName) {
        return CHILD_COLLECTIONS.getOrDefault(entityName, Set.of());
    }

    /**
     * Get ID field name for a child type.
     */
    public String getChildIdField(String childType) {
        return CHILD_ID_FIELDS.getOrDefault(childType, childType + "Id");
    }

    // ===========================================
    // URL BUILDERS
    // ===========================================

    /**
     * Build URL for paginated full sync (no watermark - suppliers don't support incremental).
     * Includes orderBy for deterministic pagination.
     * @see <a href="https://docs.oracle.com/en/cloud/saas/procurement/25c/fapra/">Oracle Procurement REST API</a>
     */
    public String buildFullSyncUrl(String baseUrl, String endpoint, String entityName, int pageSize, long offset) {
        StringBuilder url = new StringBuilder(normalizeUrl(baseUrl))
                .append(API_BASE_PATH).append("/").append(endpoint)
                .append("?limit=").append(pageSize)
                .append("&offset=").append(offset)
                .append("&onlyData=true");

        appendSortOrder(url, entityName);
        appendExpandParam(url, entityName);
        return url.toString();
    }

    /**
     * Build URL for single entity by ID.
     */
    public String buildGetByIdUrl(String baseUrl, String endpoint, String entityName, String id) {
        StringBuilder url = new StringBuilder(normalizeUrl(baseUrl))
                .append(API_BASE_PATH).append("/").append(endpoint).append("/").append(id);

        String expandParam = EXPAND_PARAMS.get(entityName);
        if (expandParam != null) {
            url.append("?expand=").append(expandParam);
        }
        return url.toString();
    }

    /**
     * Build URL for schema describe.
     */
    public String buildDescribeUrl(String baseUrl, String endpoint) {
        return normalizeUrl(baseUrl) + API_BASE_PATH + "/" + endpoint + "/describe";
    }

    /**
     * Fetch and parse schema from REST describe endpoint.
     * Returns a list of attribute maps with name, type, and other metadata.
     *
     * @see <a href="https://docs.oracle.com/en/cloud/saas/procurement/24b/fapra/Quick_Start.html">Oracle Procurement REST API Quick Start</a>
     */
    public List<Map<String, Object>> fetchDescribeSchema(String baseUrl, String endpoint, AuthConfig auth) {
        String url = buildDescribeUrl(baseUrl, endpoint);
        log.debug("Fetching REST schema from: {}", url);

        try {
            ResponseEntity<String> response = getResponse(url, auth);
            log.debug("Describe response: {}", response.getBody());

            ReadContext ctx = JsonPath.parse(response.getBody());

            // Oracle REST API describe returns attributes under "Resources.<endpoint>.attributes"
            // e.g., $.Resources.suppliers.attributes[*]
            String jsonPath = "$.Resources." + endpoint + ".attributes[*]";
            List<Map<String, Object>> attributes = ctx.read(jsonPath);

            log.debug("Fetched {} attributes from describe endpoint using path: {}", attributes.size(), jsonPath);
            return attributes;
        } catch (Exception e) {
            log.error("Error fetching describe schema for {}: {}", endpoint, e.getMessage());
            throw new RuntimeException("Failed to fetch describe schema: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch full describe schema including children.
     * Returns a map containing:
     * - "attributes": List of parent attribute definitions
     * - "children": Map of childName -> child attributes list
     *
     * Only children defined in CHILD_COLLECTIONS are included.
     *
     * @see <a href="https://docs.oracle.com/en/cloud/saas/procurement/24b/fapra/Quick_Start.html">Oracle Procurement REST API Quick Start</a>
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchDescribeSchemaWithChildren(String baseUrl, String endpoint, String entityName, AuthConfig auth) {
        String url = buildDescribeUrl(baseUrl, endpoint);
        log.debug("Fetching REST schema with children from: {}", url);

        Map<String, Object> result = new HashMap<>();

        try {
            ResponseEntity<String> response = getResponse(url, auth);
            ReadContext ctx = JsonPath.parse(response.getBody());

            // Parse parent attributes
            String attrPath = "$.Resources." + endpoint + ".attributes";
            List<Map<String, Object>> attributes = ctx.read(attrPath);
            result.put("attributes", attributes != null ? attributes : new ArrayList<>());
            log.debug("Fetched {} parent attributes", attributes != null ? attributes.size() : 0);

            // Parse children - only those in CHILD_COLLECTIONS
            Map<String, Object> childrenSchemas = new HashMap<>();
            Set<String> allowedChildren = CHILD_COLLECTIONS.getOrDefault(entityName, Set.of());

            for (String childName : allowedChildren) {
                try {
                    String childAttrPath = "$.Resources." + endpoint + ".children." + childName + ".attributes";
                    List<Map<String, Object>> childAttrs = ctx.read(childAttrPath);

                    if (childAttrs != null && !childAttrs.isEmpty()) {
                        Map<String, Object> childSchema = new HashMap<>();
                        childSchema.put("attributes", childAttrs);

                        // Check for grandchildren (e.g., assignments under sites)
                        Set<String> allowedGrandchildren = CHILD_COLLECTIONS.getOrDefault(childName, Set.of());
                        if (!allowedGrandchildren.isEmpty()) {
                            Map<String, Object> grandchildrenSchemas = new HashMap<>();
                            for (String grandchildName : allowedGrandchildren) {
                                try {
                                    String grandchildPath = "$.Resources." + endpoint + ".children." + childName +
                                            ".children." + grandchildName + ".attributes";
                                    List<Map<String, Object>> grandchildAttrs = ctx.read(grandchildPath);
                                    if (grandchildAttrs != null && !grandchildAttrs.isEmpty()) {
                                        Map<String, Object> grandchildSchema = new HashMap<>();
                                        grandchildSchema.put("attributes", grandchildAttrs);
                                        grandchildrenSchemas.put(grandchildName, grandchildSchema);
                                        log.debug("Fetched {} attributes for grandchild '{}'", grandchildAttrs.size(), grandchildName);
                                    }
                                } catch (Exception e) {
                                    log.debug("No grandchild '{}' found under '{}': {}", grandchildName, childName, e.getMessage());
                                }
                            }
                            if (!grandchildrenSchemas.isEmpty()) {
                                childSchema.put("children", grandchildrenSchemas);
                            }
                        }

                        childrenSchemas.put(childName, childSchema);
                        log.debug("Fetched {} attributes for child '{}'", childAttrs.size(), childName);
                    }
                } catch (Exception e) {
                    log.debug("No child '{}' found in describe: {}", childName, e.getMessage());
                }
            }

            result.put("children", childrenSchemas);
            return result;

        } catch (Exception e) {
            log.error("Error fetching describe schema for {}: {}", endpoint, e.getMessage());
            throw new RuntimeException("Failed to fetch describe schema: " + e.getMessage(), e);
        }
    }

    /**
     * Get REST endpoint for entity name.
     */
    public String getEndpointForEntity(String entityName) {
        switch (entityName) {
            case "suppliers":
                return "suppliers";
            default:
                throw new IllegalArgumentException("Unknown REST entity: " + entityName);
        }
    }

    // ===========================================
    // HTTP CONFIGURATION
    // ===========================================

    @Override
    public HttpHeaders getHeaders(AuthConfig authConf) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.addAll(getAuthHeaders(authConf));
        return headers;
    }

    @Override
    public RestTemplate getTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MILLIS);
        factory.setReadTimeout(TIMEOUT_MILLIS);
        return new RestTemplate(factory);
    }

    // ===========================================
    // PRIVATE - RESPONSE PARSING
    // ===========================================

    private List<EntityData> parseItems(List<Map<String, Object>> items, SyncRequest request) {
        String entityName = request.getEntityName();
        String idField = request.getEntitySchema().getIdField().getApiName();
        String wmField = getWatermarkFieldName(request);

        List<EntityData> result = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            result.add(parseItem(item, entityName, idField, wmField));
        }
        return result;
    }

    private EntityData parseItem(Map<String, Object> attributes, String entityName, String idField, String wmField) {
        EntityData entity = new EntityData(entityName);
        Set<String> childFields = CHILD_COLLECTIONS.getOrDefault(entityName, Set.of());

        // Track max LastUpdateDate across parent and ALL child levels for incremental sync
        long maxLastUpdateDate = 0L;

        String parentId = extractStringValue(attributes, idField);
        if (parentId != null) {
            entity.setId(parentId);
        }

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key.equalsIgnoreCase(idField) && value != null) {
                entity.setId(value.toString());
            } else if (wmField != null && key.equalsIgnoreCase(wmField)) {
                Long timestamp = parseTimestamp(value);
                if (timestamp != null) {
                    maxLastUpdateDate = Math.max(maxLastUpdateDate, timestamp);
                }
            } else if (FIELD_LAST_UPDATE_DATE.equals(key)) {
                Long timestamp = parseTimestamp(value);
                if (timestamp != null) {
                    maxLastUpdateDate = Math.max(maxLastUpdateDate, timestamp);
                }
            } else if (FIELD_CREATION_DATE.equals(key)) {
                Long timestamp = parseTimestamp(value);
                if (timestamp != null) {
                    entity.setCreatedAt(timestamp);
                }
            }

            if (childFields.contains(key)) {
                if (value != null) {
                    ParseResult childResult = parseChildCollectionWithMaxDate(key, value, parentId);
                    if (!childResult.children.isEmpty()) {
                        // Convert EntityData to Maps for MongoDB serialization
                        entity.addValue(key, convertEntityDataListToMaps(childResult.children));
                    }
                    maxLastUpdateDate = Math.max(maxLastUpdateDate, childResult.maxLastUpdateDate);
                }
            } else {
                entity.addValue(key, value);
            }
        }

        if (maxLastUpdateDate > 0) {
            entity.setLastModified(maxLastUpdateDate);
        } else {
            log.warn("No LastUpdateDate found for entity {}, using current time", entity.getId());
            entity.setLastModified(System.currentTimeMillis());
        }

        return entity;
    }

    /**
     * Result holder for parsing child collections with max LastUpdateDate tracking.
     */
    private static class ParseResult {
        List<EntityData> children = new ArrayList<>();
        long maxLastUpdateDate = 0L;
    }

    /**
     * Parse child collection with max LastUpdateDate tracking.
     * Also handles grandchildren (e.g., assignments under sites).
     */
    @SuppressWarnings("unchecked")
    private ParseResult parseChildCollectionWithMaxDate(String collectionName, Object value, String parentId) {
        ParseResult result = new ParseResult();
        if (value == null) {
            return result;
        }

        try {
            List<Map<String, Object>> items = extractItemsList(value);
            if (items == null) {
                return result;
            }

            String childIdField = CHILD_ID_FIELDS.getOrDefault(collectionName, collectionName + "Id");
            Set<String> grandchildFields = CHILD_COLLECTIONS.getOrDefault(collectionName, Set.of());
            int index = 0;

            for (Map<String, Object> item : items) {
                ChildParseResult childResult = parseChildItemWithMaxDate(
                        collectionName, item, parentId, childIdField, index++, grandchildFields);
                result.children.add(childResult.child);
                result.maxLastUpdateDate = Math.max(result.maxLastUpdateDate, childResult.maxLastUpdateDate);
            }
        } catch (Exception e) {
            log.warn("Could not parse child collection {}: {}", collectionName, e.getMessage());
        }

        return result;
    }

    /**
     * Result holder for parsing a single child item.
     */
    private static class ChildParseResult {
        EntityData child;
        long maxLastUpdateDate = 0L;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItemsList(Object value) {
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        if (value instanceof Map) {
            Map<String, Object> wrapper = (Map<String, Object>) value;
            if (wrapper.containsKey("items") && wrapper.get("items") instanceof List) {
                return (List<Map<String, Object>>) wrapper.get("items");
            }
            return List.of(wrapper);
        }
        return null;
    }

    private ChildParseResult parseChildItemWithMaxDate(String childName, Map<String, Object> attributes,
                                                        String parentId, String idField, int index,
                                                        Set<String> grandchildFields) {
        ChildParseResult result = new ChildParseResult();
        EntityData child = new EntityData(childName);
        child.setChild(true);
        child.setParentId(parentId);

        String childId = extractStringValue(attributes, idField);
        child.setId(childId != null ? childId : parentId + "#" + index);

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (grandchildFields.contains(key) && value != null) {
                ParseResult grandchildResult = parseChildCollectionWithMaxDate(key, value, child.getId());
                if (!grandchildResult.children.isEmpty()) {
                    // Convert EntityData to Maps for MongoDB serialization
                    child.addValue(key, convertEntityDataListToMaps(grandchildResult.children));
                }
                result.maxLastUpdateDate = Math.max(result.maxLastUpdateDate, grandchildResult.maxLastUpdateDate);
            } else {
                child.addValue(key, value);
            }

            if (FIELD_LAST_UPDATE_DATE.equals(key)) {
                Long ts = parseTimestamp(value);
                if (ts != null) {
                    child.setLastModified(ts);
                    result.maxLastUpdateDate = Math.max(result.maxLastUpdateDate, ts);
                }
            } else if (FIELD_CREATION_DATE.equals(key)) {
                Long ts = parseTimestamp(value);
                if (ts != null) child.setCreatedAt(ts);
            }
        }

        result.child = child;
        return result;
    }

    // ===========================================
    // PRIVATE - UTILITIES
    // ===========================================

    private String getWatermarkFieldName(SyncRequest request) {
        return request.getEntitySchema().getWatermarkField() != null
                ? request.getEntitySchema().getWatermarkField().getApiName()
                : null;
    }

    private String extractStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Long parseTimestamp(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value.toString()).toEpochSecond() * 1000;
        } catch (Exception e) {
            log.warn("Could not parse timestamp: {}", value);
            return null;
        }
    }

    /**
     * Convert a list of EntityData objects to a list of Maps for MongoDB serialization.
     */
    private List<Map<String, Object>> convertEntityDataListToMaps(List<EntityData> entities) {
        List<Map<String, Object>> result = new ArrayList<>(entities.size());
        for (EntityData entity : entities) {
            result.add(convertEntityDataToMap(entity));
        }
        return result;
    }

    /**
     * Convert a single EntityData to a Map with flat field values.
     * Recursively converts nested EntityData objects (grandchildren).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertEntityDataToMap(EntityData entity) {
        Map<String, Object> flatValues = new LinkedHashMap<>();
        if (entity.getValues() != null) {
            for (Map.Entry<String, Object> entry : entity.getValues().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof List && !((List<?>) value).isEmpty()
                        && ((List<?>) value).get(0) instanceof EntityData) {
                    flatValues.put(entry.getKey(), convertEntityDataListToMaps((List<EntityData>) value));
                } else {
                    flatValues.put(entry.getKey(), value);
                }
            }
        }
        return flatValues;
    }

    private void appendExpandParam(StringBuilder url, String entityName) {
        String expandParam = EXPAND_PARAMS.get(entityName);
        if (expandParam != null && !expandParam.isEmpty()) {
            url.append("&expand=").append(expandParam);
        }
    }

    /**
     * Append orderBy parameter for deterministic pagination.
     * Per Oracle docs: "You must sort using unique attributes for predictable paging results"
     * @see <a href="https://docs.oracle.com/en/cloud/saas/procurement/25c/fapra/">Oracle Procurement REST API</a>
     */
    private void appendSortOrder(StringBuilder url, String entityName) {
        String sortOrder = ENTITY_SORT_ORDER.get(entityName);
        if (sortOrder != null && !sortOrder.isEmpty()) {
            url.append("&orderBy=").append(sortOrder);
            log.debug("Adding REST sort order for {}: orderBy={}", entityName, sortOrder);
        }
    }

    /**
     * Format date values in the data map for Oracle REST API.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> formatDatesForOracle(Map<String, Object> data) {
        if (data == null) return null;

        Map<String, Object> formatted = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                formatted.put(key, null);
            } else if (value instanceof Date) {
                formatted.put(key, formatInstant(((Date) value).toInstant()));
            } else if (value instanceof Instant) {
                formatted.put(key, formatInstant((Instant) value));
            } else if (value instanceof ZonedDateTime) {
                formatted.put(key, ((ZonedDateTime) value).format(ORACLE_DATE_FORMAT));
            } else if (value instanceof Long && isDateField(key)) {
                formatted.put(key, formatInstant(Instant.ofEpochMilli((Long) value)));
            } else if (value instanceof Map) {
                formatted.put(key, formatDatesForOracle((Map<String, Object>) value));
            } else if (value instanceof List) {
                List<Object> formattedList = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        formattedList.add(formatDatesForOracle((Map<String, Object>) item));
                    } else {
                        formattedList.add(item);
                    }
                }
                formatted.put(key, formattedList);
            } else {
                formatted.put(key, value);
            }
        }
        return formatted;
    }

    private String formatInstant(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).format(ORACLE_DATE_FORMAT);
    }

    private boolean isDateField(String fieldName) {
        if (fieldName == null) return false;
        String lower = fieldName.toLowerCase();
        return lower.contains("date") || lower.contains("time") || lower.endsWith("at");
    }

    private String normalizeUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("/+$", "");
    }
}
