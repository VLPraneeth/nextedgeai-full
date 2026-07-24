package com.syncari.connector.OracleErpReceivables;

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
 * REST client for Oracle ERP Receivables connector.
 *
 * Handles REST API calls for entities like Payment Terms and Suppliers.
 * Supports child object handling - children are parsed as List<EntityData> in parent's values map.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/saas/procurement/25c/fapra/">Oracle Fusion Cloud Procurement REST API</a>
 */
@Slf4j
public class OracleERPReceivablesRestClient extends SyncariEntityDataRestClient {

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
     * @see <a href="https://docs.oracle.com/en/cloud/saas/procurement/25c/fapra/op-suppliers-supplierid-child-sites-get.html">Suppliers API</a>
     */
    private static final Map<String, Set<String>> CHILD_COLLECTIONS = Map.of(
            "suppliers", Set.of("sites", "contacts", "addresses"),
            "payment_terms", Set.of()
    );

    /**
     * ID field names for child entities.
     * @see <a href="https://docs.oracle.com/en/cloud/saas/procurement/24b/fapra/op-suppliers-supplierid-child-contacts-suppliercontactid-child-addresses-get.html">Contacts API</a>
     */
    private static final Map<String, String> CHILD_ID_FIELDS = Map.of(
            "sites", "SupplierSiteId",
            "contacts", "SupplierContactId",
            "addresses", "SupplierContactAddressId"
    );

    /**
     * Expand parameter for fetching child collections.
     */
    private static final Map<String, String> EXPAND_PARAMS = Map.of(
            "suppliers", "sites,contacts,addresses"
    );

    /**
     * Sort order configuration for deterministic pagination.
     * Format: "watermarkField:asc,idField:asc"
     * Per Oracle docs: "You must sort using unique attributes for predictable paging results"
     * Note: Field names are CASE-SENSITIVE per Oracle REST API documentation.
     * @see <a href="https://docs.oracle.com/en/cloud/saas/financials/25b/farfa/op-payablespaymentterms-get.html">Payment Terms API</a>
     * @see <a href="https://docs.oracle.com/en/cloud/saas/procurement/25c/fapra/op-suppliers-get.html">Suppliers API</a>
     */
    private static final Map<String, String> ENTITY_SORT_ORDER = Map.of(
            "suppliers", "LastUpdateDate:asc,SupplierId:asc",
            "payment_terms", "lastUpdateDate:asc,termsId:asc"
    );

    // ===========================================
    // CONSTRUCTORS
    // ===========================================

    public OracleERPReceivablesRestClient() {
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
            log.info("REST CREATE successful, ID: {}", result != null ? result.getId() : "null");
            return result;
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
    // URL BUILDERS
    // ===========================================

    /**
     * Build URL for paginated full sync.
     * Includes orderBy for deterministic pagination.
     * @see <a href="https://docs.oracle.com/en/cloud/saas/financials/25b/farfa/op-receivablesinvoices-get.html">Oracle REST API</a>
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
     * Build URL for incremental sync with watermark filtering.
     * Uses same pattern as OracleErpSales: q=lastUpdateDate>startDate and <=endDate (no quotes, no encoding)
     * Includes orderBy for deterministic pagination.
     * @see <a href="https://docs.oracle.com/en/cloud/saas/financials/25b/farfa/op-receivablesinvoices-get.html">Oracle REST API</a>
     */
    public String buildWatermarkUrl(String baseUrl, String endpoint, String entityName,
                                     long startDate, long endDate, int pageSize, long offset) {
        // Use same date format as OracleErpSales: ZonedDateTime.toString() produces ISO format
        String startDateStr = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startDate), ZoneOffset.UTC).toString();
        String endDateStr = ZonedDateTime.ofInstant(Instant.ofEpochMilli(endDate), ZoneOffset.UTC).toString();

        // Match OracleErpSales pattern: q=field>start and <=end (no quotes around dates)
        StringBuilder url = new StringBuilder(normalizeUrl(baseUrl))
                .append(API_BASE_PATH).append("/").append(endpoint)
                .append("?q=lastUpdateDate>").append(startDateStr)
                .append(" and <=").append(endDateStr)
                .append("&limit=").append(pageSize)
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
     */
    public List<Map<String, Object>> fetchDescribeSchema(String baseUrl, String endpoint, AuthConfig auth) {
        String url = buildDescribeUrl(baseUrl, endpoint);
        log.debug("Fetching REST schema from: {}", url);

        try {
            ResponseEntity<String> response = getResponse(url, auth);
            log.debug("Describe response: {}", response.getBody());

            ReadContext ctx = JsonPath.parse(response.getBody());

            // Oracle REST API describe returns attributes under "Resources.<endpoint>.attributes"
            // e.g., $.Resources.payablesPaymentTerms.attributes[*]
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
     * Get REST endpoint for entity name.
     */
    public String getEndpointForEntity(String entityName) {
        switch (entityName) {
            case "suppliers":
                return "suppliers";
            case "payment_terms":
                return "payablesPaymentTerms";
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
                    entity.setLastModified(timestamp);
                }
            } else if (FIELD_CREATION_DATE.equals(key)) {
                Long timestamp = parseTimestamp(value);
                if (timestamp != null) {
                    entity.setCreatedAt(timestamp);
                }
            }

            if (childFields.contains(key)) {
                if (value != null) {
                    List<EntityData> children = parseChildCollection(key, value, parentId);
                    if (!children.isEmpty()) {
                        entity.addValue(key, children);
                    }
                }
            } else {
                entity.addValue(key, value);
            }
        }

        return entity;
    }

    @SuppressWarnings("unchecked")
    private List<EntityData> parseChildCollection(String collectionName, Object value, String parentId) {
        List<EntityData> children = new ArrayList<>();
        if (value == null) {
            return children;
        }

        try {
            List<Map<String, Object>> items = extractItemsList(value);
            if (items == null) {
                return children;
            }

            String childIdField = CHILD_ID_FIELDS.getOrDefault(collectionName, collectionName + "Id");
            int index = 0;
            for (Map<String, Object> item : items) {
                children.add(parseChildItem(collectionName, item, parentId, childIdField, index++));
            }
        } catch (Exception e) {
            log.warn("Could not parse child collection {}: {}", collectionName, e.getMessage());
        }

        return children;
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

    private EntityData parseChildItem(String childName, Map<String, Object> attributes,
                                      String parentId, String idField, int index) {
        EntityData child = new EntityData(childName);
        child.setChild(true);
        child.setParentId(parentId);

        String childId = extractStringValue(attributes, idField);
        child.setId(childId != null ? childId : parentId + "#" + index);

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            child.addValue(key, value);

            if (FIELD_LAST_UPDATE_DATE.equals(key)) {
                Long ts = parseTimestamp(value);
                if (ts != null) child.setLastModified(ts);
            } else if (FIELD_CREATION_DATE.equals(key)) {
                Long ts = parseTimestamp(value);
                if (ts != null) child.setCreatedAt(ts);
            }
        }

        return child;
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

    private void appendExpandParam(StringBuilder url, String entityName) {
        String expandParam = EXPAND_PARAMS.get(entityName);
        if (expandParam != null && !expandParam.isEmpty()) {
            url.append("&expand=").append(expandParam);
        }
    }

    /**
     * Append orderBy parameter for deterministic pagination.
     * Per Oracle docs: "You must sort using unique attributes for predictable paging results"
     * @see <a href="https://docs.oracle.com/en/cloud/saas/financials/25b/farfa/op-receivablesinvoices-get.html">Oracle REST API</a>
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
     * Converts Java Date, Instant, ZonedDateTime, and epoch millis to Oracle's expected format.
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

    /**
     * Normalize URL - remove trailing slashes.
     */
    private String normalizeUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("/+$", "");
    }
}
