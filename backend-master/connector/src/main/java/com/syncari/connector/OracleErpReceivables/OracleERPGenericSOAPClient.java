package com.syncari.connector.OracleErpReceivables;

import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.axis.encoding.Base64;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Generic SOAP client for Oracle ERP Receivables.
 * Handles SOAP operations for entities like CustomerAccount, Organization, and Location.
 *
 * @see <a href="https://docs.oracle.com/en/cloud/saas/sales/oesws/">Oracle Fusion Cloud SOAP Web Services</a>
 */
@Slf4j
public class OracleERPGenericSOAPClient {

    // ===========================================
    // CONSTANTS
    // ===========================================

    private static final DateTimeFormatter ORACLE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final String FIELD_LAST_UPDATE_DATE = "LastUpdateDate";
    private static final String FIELD_CREATION_DATE = "CreationDate";

    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";
    private static final String ADF_TYPES_NS = "http://xmlns.oracle.com/adf/svc/types/";

    private static final String TYP1 = "typ1";
    private static final String FIND_CRITERIA = "findCriteria";
    private static final String FIND_CONTROL = "findControl";
    private static final String FILTER = "filter";
    private static final String GROUP = "group";
    private static final String ITEM = "item";
    private static final String NESTED = "nested";
    private static final String CONJUNCTION = "conjunction";
    private static final String ATTRIBUTE = "attribute";
    private static final String OPERATOR = "operator";
    private static final String VALUE = "value";
    private static final String UPPER_CASE_COMPARE = "upperCaseCompare";

    private static final String AND = "And";
    private static final String OR = "Or";
    private static final String ON_OR_AFTER = "ONORAFTER";
    private static final String ON_OR_BEFORE = "ONORBEFORE";
    private static final String EQUALS = "=";
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    // ===========================================
    // CONFIGURATION CLASSES
    // ===========================================

    @Data
    @AllArgsConstructor
    public static class ServiceConfig {
        private String serviceEndpoint;
        private String wsdlPath;
        private String namespace;
        private String findOperation;
        private String getOperation;
        private String resultElement;
        private String responseElement;
        private String idField;
        private String watermarkField;
        private Map<String, String> fieldMappings;
        private String xsdFileName;        // XSD file name (e.g., "OrganizationParty.xsd") - Oracle XSD names don't always match entity type
        private String xsdComplexTypeName; // ComplexType name in XSD (e.g., "OrganizationParty") - used for schema extraction
    }

    @Data
    @AllArgsConstructor
    public static class MergeConfig {
        private String mergeOperation;
        private String deleteOperation;
        private String entityElementName;
        private String entityNamespace;
        private String entityPrefix;
        private List<String> requiredFields;
        private List<String> childElements;
    }

    @Data
    @AllArgsConstructor
    public static class ChildCollectionConfig {
        private String collectionName;
        private String watermarkField;
    }

    // ===========================================
    // ENTITY CONFIGURATIONS
    // Per Oracle SOAP API documentation
    // ===========================================

    /**
     * Service configurations for SOAP entities.
     * @see <a href="https://docs.oracle.com/en/cloud/saas/sales/oesws/">Oracle CRM SOAP Services</a>
     */
    private static final Map<String, ServiceConfig> SERVICE_CONFIGS = Map.of(
            "customer_accounts", new ServiceConfig(
                    "/crmService/CustomerAccountService",
                    "/crmService/CustomerAccountService?WSDL",
                    "http://xmlns.oracle.com/apps/cdm/foundation/parties/customerAccountService/applicationModule/types/",
                    "findCustomerAccount", "getCustomerAccount",
                    "CustomerAccount", "Value",
                    "CustomerAccountId", FIELD_LAST_UPDATE_DATE,
                    Map.of(),
                    "CustomerAccount.xsd",
                    "CustomerAccount"
            ),
            "customer_parties", new ServiceConfig(
                    "/crmService/FoundationPartiesOrganizationService",
                    "/crmService/FoundationPartiesOrganizationService?WSDL",
                    "http://xmlns.oracle.com/apps/cdm/foundation/parties/organizationService/applicationModule/types/",
                    "findOrganization", "getOrganization",
                    "Organization", "Value",
                    "PartyId", FIELD_LAST_UPDATE_DATE,
                    Map.of(),
                    "OrganizationParty.xsd",
                    "OrganizationParty"
            ),
            "customer_party_sites", new ServiceConfig(
                    "/crmService/FoundationPartiesLocationService",
                    "/crmService/FoundationPartiesLocationService?WSDL",
                    "http://xmlns.oracle.com/apps/cdm/foundation/parties/locationService/applicationModule/types/",
                    "findLocation", "getLocation",
                    "Location", "Value",
                    "LocationId", FIELD_LAST_UPDATE_DATE,
                    Map.of(),
                    "Location.xsd",
                    "Location"
            )
    );

    /**
     * Merge configurations for write operations.
     * @see <a href="https://docs.oracle.com/en/cloud/saas/sales/oesws/Create-CustomerAccount-including-CustomerAccountSite-and-CustomerAccountSiteUse.html">Oracle Merge Operations</a>
     */
    private static final Map<String, MergeConfig> MERGE_CONFIGS = Map.of(
            "customer_accounts", new MergeConfig(
                    "mergeCustomerAccount", "deleteCustomerAccount",
                    "customerAccount",
                    "http://xmlns.oracle.com/apps/cdm/foundation/parties/customerAccountService/",
                    "cus",
                    List.of("PartyId", "CreatedByModule"),  // CreatedByModule required for create
                    List.of("CustomerAccountSite", "CustomerAccountContact")
            ),
            "customer_parties", new MergeConfig(
                    "mergeOrganization", "deleteOrganization",
                    "organizationParty",  // Per Oracle docs: <ns1:organizationParty>
                    "http://xmlns.oracle.com/apps/cdm/foundation/parties/organizationService/",
                    "org",
                    List.of("CreatedByModule"),  // CreatedByModule required for create; OrganizationName goes in OrganizationProfile
                    List.of("OrganizationProfile", "PartyUsageAssignment", "PartySite", "Email", "Phone")
            ),
            "customer_party_sites", new MergeConfig(
                    "mergeLocation", "deleteLocation",
                    "location",
                    "http://xmlns.oracle.com/apps/cdm/foundation/parties/locationService/",
                    "loc",
                    List.of("Address1", "City", "Country", "Status"),
                    List.of()
            )
    );

    /**
     * Child collections for incremental sync filtering.
     * Used to build nested OR filters that capture child-only updates.
     */
    private static final Map<String, List<ChildCollectionConfig>> CHILD_COLLECTIONS = Map.of(
            "customer_accounts", List.of(
                    new ChildCollectionConfig("CustomerAccountSite", FIELD_LAST_UPDATE_DATE),
                    new ChildCollectionConfig("CustomerAccountContact", FIELD_LAST_UPDATE_DATE)
            ),
            "customer_parties", List.of(
                    // Verified working child filters for incremental sync
                    // Note: Email/Phone/ContactPoint are flattened fields on parent (not child collections)
                    //       Updates to them will update parent's LastUpdateDate automatically
                    new ChildCollectionConfig("OrganizationProfile", FIELD_LAST_UPDATE_DATE),
                    new ChildCollectionConfig("PartySite", FIELD_LAST_UPDATE_DATE),
                    new ChildCollectionConfig("Relationship", FIELD_LAST_UPDATE_DATE)
            ),
            "customer_party_sites", List.of(
                    // HZ_LOCATIONS is standalone - no nested child objects
                    // LocationProfile causes HTTP 500 - not a valid child collection
            )
    );

    /**
     * ID field names for child entities.
     * @see <a href="https://docs.oracle.com/en/cloud/saas/sales/oesws/Create-CustomerAccount-including-CustomerAccountSite-and-CustomerAccountSiteUse.html">Oracle Child Entities</a>
     *
     * KNOWN LIMITATION: CustomerAccountSiteUse UPDATE is blocked by Oracle bug JBO-27008.
     * CREATE works (omit SiteUseId), but UPDATE fails with ReadOnlyAttrException.
     * See Oracle Support Doc ID 2192805.1. Tested 2026-01-14.
     */
    private static final Map<String, String> CHILD_ID_FIELDS = Map.of(
            "CustomerAccountSite", "CustomerAccountSiteId",
            "CustomerAccountSiteUse", "SiteUseId",  // CREATE only - UPDATE blocked (JBO-27008)
            "CustomerAccountContact", "CustomerAccountContactId",
            "CustomerAccountRelationship", "CustomerAccountRelationshipId",
            "OrganizationProfile", "OrganizationProfileId",
            "PartySite", "PartySiteId",
            "ContactPoint", "ContactPointId",
            "LocationProfile", "LocationProfileId"
    );

    // ===========================================
    // INSTANCE FIELDS
    // ===========================================

    private final AuthConfig authConfig;

    public OracleERPGenericSOAPClient(AuthConfig authConfig) {
        this.authConfig = authConfig;
        if (this.authConfig.getEndpoint() != null && this.authConfig.getEndpoint().endsWith("/")) {
            this.authConfig.setEndpoint(this.authConfig.getEndpoint().replaceAll("/+$", ""));
        }
    }

    // ===========================================
    // PUBLIC API - READ OPERATIONS
    // ===========================================

    /**
     * Find entities by watermark range with pagination.
     */
    public List<EntityData> findByWatermark(String entityType, long startWatermark, long endWatermark,
                                            int pageSize, long offset) {
        ServiceConfig config = getServiceConfig(entityType);

        String startDate = formatTimestamp(startWatermark);
        String endDate = formatTimestamp(endWatermark);

        log.info("SOAP findByWatermark - Entity: {}, DateRange: {} to {}, Offset: {}, PageSize: {}",
                entityType, startDate, endDate, offset, pageSize);

        try {
            SOAPMessage payload = createFindPayload(config, entityType, startDate, endDate, pageSize, (int) offset);
            SOAPMessage response = makeRequest(config.getServiceEndpoint(), payload);
            return parseResponse(response, config, entityType);
        } catch (Exception e) {
            log.error("Error fetching {} by watermark: {}\n{}", entityType, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Failed to fetch " + entityType, e);
        }
    }

    /**
     * Get single entity by ID.
     */
    public EntityData getById(String entityType, String id) {
        ServiceConfig config = getServiceConfig(entityType);

        try {
            SOAPMessage payload = createGetByIdPayload(config, id);
            SOAPMessage response = makeRequest(config.getServiceEndpoint(), payload);
            List<EntityData> entities = parseResponse(response, config, entityType);
            return entities.isEmpty() ? null : entities.get(0);
        } catch (Exception e) {
            log.error("Error fetching {} by ID {}: {}\n{}", entityType, id, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Failed to fetch " + entityType + " by ID", e);
        }
    }

    /**
     * Get child collections configuration for an entity.
     */
    public List<ChildCollectionConfig> getChildCollections(String entityType) {
        return CHILD_COLLECTIONS.getOrDefault(entityType, List.of());
    }

    // ===========================================
    // PUBLIC API - WRITE OPERATIONS
    // ===========================================

    /**
     * Create or update entity via merge operation.
     */
    public EntityData merge(String entityType, Map<String, Object> data) {
        ServiceConfig serviceConfig = getServiceConfig(entityType);
        MergeConfig mergeConfig = getMergeConfig(entityType);

        // Check if this is an update (ID field present) or create (no ID field)
        String idField = serviceConfig.getIdField();
        boolean isUpdate = data.containsKey(idField) && data.get(idField) != null;

        // Auto-add CreatedByModule only for CREATE operations (not updates)
        // CreatedByModule is read-only on existing records and causes JBO-27004 error
        if (!isUpdate && !data.containsKey("CreatedByModule")) {
            data.put("CreatedByModule", "HZ_WS");
        }

        try {
            SOAPMessage payload = createMergePayload(serviceConfig, mergeConfig, data);
            log.info("SOAP merge for {}", entityType);
            log.debug("SOAP merge payload:\n{}", soapMessageToString(payload));

            SOAPMessage response = makeRequest(serviceConfig.getServiceEndpoint(), payload);
            EntityData result = parseMergeResponse(response, serviceConfig, entityType);
            if (result != null) {
                log.info("Successfully merged {} with ID: {}", entityType, result.getId());
            }
            return result;
        } catch (Exception e) {
            log.error("Error merging {}: {}\n{}", entityType, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Failed to merge " + entityType, e);
        }
    }

    /**
     * Delete entity by ID.
     */
    public boolean delete(String entityType, String id) {
        ServiceConfig serviceConfig = getServiceConfig(entityType);
        MergeConfig mergeConfig = getMergeConfig(entityType);

        if (mergeConfig.getDeleteOperation() == null) {
            throw new IllegalArgumentException("Delete not supported for: " + entityType);
        }

        try {
            SOAPMessage payload = createDeletePayload(serviceConfig, mergeConfig, id);
            log.info("SOAP delete {} with ID: {}", entityType, id);

            SOAPMessage response = makeRequest(serviceConfig.getServiceEndpoint(), payload);
            boolean success = parseDeleteResponse(response);

            if (success) {
                log.info("Successfully deleted {} with ID: {}", entityType, id);
            }
            return success;
        } catch (Exception e) {
            log.error("Error deleting {} with ID {}: {}\n{}", entityType, id, e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Failed to delete " + entityType, e);
        }
    }

    public boolean isWriteSupported(String entityType) {
        return MERGE_CONFIGS.containsKey(entityType);
    }

    public boolean isDeleteSupported(String entityType) {
        MergeConfig config = MERGE_CONFIGS.get(entityType);
        return config != null && config.getDeleteOperation() != null;
    }

    private String soapMessageToString(SOAPMessage message) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out);
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Error converting SOAP message: " + e.getMessage();
        }
    }

    // ===========================================
    // PUBLIC API - SCHEMA DISCOVERY
    // ===========================================

    /**
     * Get entity schema from WSDL.
     */
    public List<Map<String, Object>> getEntitySchema(String entityType) {
        ServiceConfig config = SERVICE_CONFIGS.get(entityType);
        if (config == null) {
            log.warn("No configuration for entity: {}", entityType);
            return List.of();
        }

        try {
            String wsdlUrl = authConfig.getEndpoint() + config.getWsdlPath();
            return parseSchemaFromWSDL(wsdlUrl, config.getXsdFileName(), config.getXsdComplexTypeName());
        } catch (Exception e) {
            log.error("Error getting schema for {}: {}\n{}", entityType, e.getMessage(), ExceptionUtils.getStackTrace(e));
            return List.of();
        }
    }

    // ===========================================
    // PRIVATE - CONFIGURATION HELPERS
    // ===========================================

    private ServiceConfig getServiceConfig(String entityType) {
        ServiceConfig config = SERVICE_CONFIGS.get(entityType);
        if (config == null) {
            throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }
        return config;
    }

    private MergeConfig getMergeConfig(String entityType) {
        MergeConfig config = MERGE_CONFIGS.get(entityType);
        if (config == null) {
            throw new IllegalArgumentException("Write not supported for: " + entityType);
        }
        return config;
    }

    private String getChildIdField(String childEntityName) {
        return CHILD_ID_FIELDS.getOrDefault(childEntityName, childEntityName + "Id");
    }

    // ===========================================
    // PRIVATE - SOAP PAYLOAD BUILDERS
    // ===========================================

    private SOAPMessage createFindPayload(ServiceConfig config, String entityType,
                                          String startDate, String endDate,
                                          int pageSize, int offset) throws Exception {
        SOAPMessage message = createBasicMessage(config);
        SOAPBody body = message.getSOAPBody();

        SOAPElement findElement = body.addChildElement(config.getFindOperation(), "typ");
        SOAPElement findCriteria = findElement.addChildElement(FIND_CRITERIA, "typ");

        findCriteria.addChildElement("fetchStart", TYP1).addTextNode(String.valueOf(offset));
        findCriteria.addChildElement("fetchSize", TYP1).addTextNode(String.valueOf(pageSize));
        findCriteria.addChildElement("excludeAttribute", TYP1).addTextNode(FALSE);

        if (startDate != null && endDate != null) {
            SOAPElement filter = findCriteria.addChildElement(FILTER, TYP1);

            List<ChildCollectionConfig> childCollections = getChildCollections(entityType);
            // Use OR if we have child filters (parent OR child1 OR child2), otherwise And
            filter.addChildElement(CONJUNCTION, TYP1).addTextNode(childCollections.isEmpty() ? AND : OR);
            addParentWatermarkGroup(filter, config.getWatermarkField(), startDate, endDate);
            for (ChildCollectionConfig child : childCollections) {
                addChildNestedWatermarkGroup(filter, child, startDate);
            }
        }

        // Add sort order to ensure deterministic pagination when records have the same LastUpdateDate
        // Sort by watermark field (LastUpdateDate) ASC, then by ID field ASC
        // This prevents infinite loops when bulk-imported data has identical timestamps
        // @see https://docs.oracle.com/en/cloud/saas/applications-common/24d/cgsac/calling-the-find-method-on-an-oracle-service-interface.html
        addSortOrder(findCriteria, config.getWatermarkField(), config.getIdField());

        SOAPElement findControl = findElement.addChildElement(FIND_CONTROL, "typ");
        findControl.addChildElement("retrieveAllTranslations", TYP1).addTextNode(FALSE);

        addAuthHeaders(message);
        message.saveChanges();
        return message;
    }

    private SOAPMessage createGetByIdPayload(ServiceConfig config, String id) throws Exception {
        SOAPMessage message = createBasicMessage(config);
        SOAPBody body = message.getSOAPBody();

        SOAPElement findElement = body.addChildElement(config.getFindOperation(), "typ");
        SOAPElement findCriteria = findElement.addChildElement(FIND_CRITERIA, "typ");

        findCriteria.addChildElement("fetchStart", TYP1).addTextNode("0");
        findCriteria.addChildElement("fetchSize", TYP1).addTextNode("1");

        SOAPElement filter = findCriteria.addChildElement(FILTER, TYP1);
        filter.addChildElement(CONJUNCTION, TYP1).addTextNode(AND);

        SOAPElement group = filter.addChildElement(GROUP, TYP1);
        group.addChildElement(CONJUNCTION, TYP1).addTextNode(AND);
        group.addChildElement(UPPER_CASE_COMPARE, TYP1).addTextNode(FALSE);

        SOAPElement item = group.addChildElement(ITEM, TYP1);
        item.addChildElement(CONJUNCTION, TYP1).addTextNode(AND);
        item.addChildElement(UPPER_CASE_COMPARE, TYP1).addTextNode(FALSE);
        item.addChildElement(ATTRIBUTE, TYP1).addTextNode(config.getIdField());
        item.addChildElement(OPERATOR, TYP1).addTextNode(EQUALS);
        item.addChildElement(VALUE, TYP1).addTextNode(id);

        SOAPElement findControl = findElement.addChildElement(FIND_CONTROL, "typ");
        findControl.addChildElement("retrieveAllTranslations", TYP1).addTextNode(FALSE);

        addAuthHeaders(message);
        message.saveChanges();
        return message;
    }

    // Additional namespace for party-related elements (PartyUsageAssignment, PartySite, etc.)
    private static final String PARTY_SERVICE_NS = "http://xmlns.oracle.com/apps/cdm/foundation/parties/partyService/";
    private static final String PARTY_PREFIX = "par";

    // Elements whose CHILDREN use the party service namespace (the element itself uses parent's namespace)
    private static final Set<String> PARTY_CHILD_NS_ELEMENTS = Set.of("PartyUsageAssignment", "PartySite", "PartySiteUse");

    private SOAPMessage createMergePayload(ServiceConfig serviceConfig, MergeConfig mergeConfig,
                                           Map<String, Object> data) throws Exception {
        SOAPMessage message = MessageFactory.newInstance().createMessage();
        SOAPEnvelope envelope = message.getSOAPPart().getEnvelope();

        envelope.setPrefix("soapenv");
        envelope.addNamespaceDeclaration("soapenv", SOAP_NS);
        envelope.addNamespaceDeclaration("typ", serviceConfig.getNamespace());
        envelope.addNamespaceDeclaration(mergeConfig.getEntityPrefix(), mergeConfig.getEntityNamespace());
        envelope.addNamespaceDeclaration(PARTY_PREFIX, PARTY_SERVICE_NS);  // Add party namespace

        envelope.getHeader().setPrefix("soapenv");
        SOAPBody body = envelope.getBody();
        body.setPrefix("soapenv");

        SOAPElement mergeElement = body.addChildElement(mergeConfig.getMergeOperation(), "typ");
        SOAPElement entityElement = mergeElement.addChildElement(mergeConfig.getEntityElementName(), "typ");

        String prefix = mergeConfig.getEntityPrefix();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            addFieldToElement(entityElement, entry.getKey(), entry.getValue(), prefix, mergeConfig.getChildElements());
        }

        addAuthHeaders(message);
        message.saveChanges();
        return message;
    }

    private SOAPMessage createDeletePayload(ServiceConfig serviceConfig, MergeConfig mergeConfig,
                                            String id) throws Exception {
        SOAPMessage message = MessageFactory.newInstance().createMessage();
        SOAPEnvelope envelope = message.getSOAPPart().getEnvelope();

        envelope.setPrefix("soapenv");
        envelope.addNamespaceDeclaration("soapenv", SOAP_NS);
        envelope.addNamespaceDeclaration("typ", serviceConfig.getNamespace());
        envelope.addNamespaceDeclaration(mergeConfig.getEntityPrefix(), mergeConfig.getEntityNamespace());

        envelope.getHeader().setPrefix("soapenv");
        SOAPBody body = envelope.getBody();
        body.setPrefix("soapenv");

        SOAPElement deleteElement = body.addChildElement(mergeConfig.getDeleteOperation(), "typ");
        SOAPElement entityElement = deleteElement.addChildElement(mergeConfig.getEntityElementName(), "typ");

        SOAPElement idElement = entityElement.addChildElement(serviceConfig.getIdField(), mergeConfig.getEntityPrefix());
        idElement.addTextNode(id);

        addAuthHeaders(message);
        message.saveChanges();
        return message;
    }

    private SOAPMessage createBasicMessage(ServiceConfig config) throws Exception {
        SOAPMessage message = MessageFactory.newInstance().createMessage();
        SOAPEnvelope envelope = message.getSOAPPart().getEnvelope();

        envelope.setPrefix("soapenv");
        envelope.addNamespaceDeclaration("soapenv", SOAP_NS);
        envelope.addNamespaceDeclaration("typ", config.getNamespace());
        envelope.addNamespaceDeclaration(TYP1, ADF_TYPES_NS);
        envelope.addNamespaceDeclaration("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        envelope.addNamespaceDeclaration("ns0", ADF_TYPES_NS);

        envelope.getHeader().setPrefix("soapenv");
        envelope.getBody().setPrefix("soapenv");

        return message;
    }

    // ===========================================
    // PRIVATE - SORT ORDER BUILDER
    // ===========================================

    /**
     * Add sort order to ensure deterministic pagination.
     * Sort by watermark field ASC, then by ID field ASC.
     * This prevents infinite loops when multiple records have the same LastUpdateDate.
     *
     * @see <a href="https://docs.oracle.com/en/cloud/saas/applications-common/24d/cgsac/calling-the-find-method-on-an-oracle-service-interface.html">Oracle FindCriteria Documentation</a>
     */
    private void addSortOrder(SOAPElement findCriteria, String watermarkField, String idField) throws SOAPException {
        log.debug("Adding sort order: primary={} ASC, secondary={} ASC", watermarkField, idField);
        SOAPElement sortOrder = findCriteria.addChildElement("sortOrder", TYP1);

        // Primary sort: watermark field (LastUpdateDate) ascending
        SOAPElement wmSort = sortOrder.addChildElement("sortAttribute", TYP1);
        wmSort.addChildElement("name", TYP1).addTextNode(watermarkField);
        wmSort.addChildElement("descending", TYP1).addTextNode(FALSE);

        // Secondary sort: ID field ascending (for deterministic order when watermarks are equal)
        SOAPElement idSort = sortOrder.addChildElement("sortAttribute", TYP1);
        idSort.addChildElement("name", TYP1).addTextNode(idField);
        idSort.addChildElement("descending", TYP1).addTextNode(FALSE);
    }

    // ===========================================
    // PRIVATE - FILTER BUILDERS
    // ===========================================

    private void addParentWatermarkGroup(SOAPElement filter, String wmField,
                                         String startDate, String endDate) throws SOAPException {
        SOAPElement group = filter.addChildElement(GROUP, TYP1);
        group.addChildElement(CONJUNCTION, TYP1).addTextNode(AND);
        group.addChildElement(UPPER_CASE_COMPARE, TYP1).addTextNode(FALSE);

        SOAPElement startItem = group.addChildElement(ITEM, TYP1);
        startItem.addChildElement(CONJUNCTION, TYP1).addTextNode(AND);
        startItem.addChildElement(UPPER_CASE_COMPARE, TYP1).addTextNode(FALSE);
        startItem.addChildElement(ATTRIBUTE, TYP1).addTextNode(wmField);
        startItem.addChildElement(OPERATOR, TYP1).addTextNode(ON_OR_AFTER);
        SOAPElement startValue = startItem.addChildElement(VALUE, TYP1);
        startValue.setAttribute("xsi:type", "ns0:dateTime");
        startValue.addTextNode(startDate);

        SOAPElement endItem = group.addChildElement(ITEM, TYP1);
        endItem.addChildElement(CONJUNCTION, TYP1).addTextNode(AND);
        endItem.addChildElement(UPPER_CASE_COMPARE, TYP1).addTextNode(FALSE);
        endItem.addChildElement(ATTRIBUTE, TYP1).addTextNode(wmField);
        endItem.addChildElement(OPERATOR, TYP1).addTextNode(ON_OR_BEFORE);
        SOAPElement endValue = endItem.addChildElement(VALUE, TYP1);
        endValue.setAttribute("xsi:type", "ns0:dateTime");
        endValue.addTextNode(endDate);
    }

    private void addChildNestedWatermarkGroup(SOAPElement filter, ChildCollectionConfig childConfig,
                                              String startDate) throws SOAPException {
        SOAPElement group = filter.addChildElement(GROUP, TYP1);
        SOAPElement item = group.addChildElement(ITEM, TYP1);
        item.addChildElement(ATTRIBUTE, TYP1).addTextNode(childConfig.getCollectionName());

        SOAPElement nested = item.addChildElement(NESTED, TYP1);
        SOAPElement nestedGroup = nested.addChildElement(GROUP, TYP1);
        nestedGroup.addChildElement(CONJUNCTION, TYP1).addTextNode(AND);

        SOAPElement nestedItem = nestedGroup.addChildElement(ITEM, TYP1);
        nestedItem.addChildElement(CONJUNCTION, TYP1).addTextNode(AND);
        nestedItem.addChildElement(UPPER_CASE_COMPARE, TYP1).addTextNode(FALSE);
        nestedItem.addChildElement(ATTRIBUTE, TYP1).addTextNode(childConfig.getWatermarkField());
        nestedItem.addChildElement(OPERATOR, TYP1).addTextNode(ON_OR_AFTER);

        SOAPElement nestedValue = nestedItem.addChildElement(VALUE, TYP1);
        nestedValue.setAttribute("xsi:type", "ns0:dateTime");
        nestedValue.addTextNode(startDate);
    }

    // ===========================================
    // PRIVATE - FIELD SERIALIZATION
    // ===========================================

    @SuppressWarnings("unchecked")
    private void addFieldToElement(SOAPElement parent, String fieldName, Object value,
                                   String prefix, List<String> childElements) throws SOAPException {
        if (value == null) return;

        boolean isChildCollection = childElements.contains(fieldName);
        // For PARTY_CHILD_NS_ELEMENTS: element uses parent's prefix, but its children use party prefix
        String childPrefix = PARTY_CHILD_NS_ELEMENTS.contains(fieldName) ? PARTY_PREFIX : prefix;

        if (isChildCollection && value instanceof List) {
            for (Object child : (List<?>) value) {
                if (child instanceof EntityData) {
                    addEntityDataChild(parent, fieldName, (EntityData) child, prefix, childPrefix);
                } else if (child instanceof Map) {
                    addMapChild(parent, fieldName, (Map<String, Object>) child, prefix, childPrefix);
                }
            }
        } else if (value instanceof EntityData) {
            addEntityDataChild(parent, fieldName, (EntityData) value, prefix, childPrefix);
        } else if (!(value instanceof List) && !(value instanceof Map)) {
            SOAPElement field = parent.addChildElement(fieldName, prefix);
            field.addTextNode(convertToString(value));
        }
    }

    @SuppressWarnings("unchecked")
    private void addEntityDataChild(SOAPElement parent, String name, EntityData data, String elementPrefix, String childrenPrefix) throws SOAPException {
        // Element uses elementPrefix, its children use childrenPrefix
        SOAPElement child = parent.addChildElement(name, elementPrefix);

        for (Map.Entry<String, Object> entry : data.getValues().entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;

            if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    if (item instanceof EntityData) {
                        addEntityDataChild(child, entry.getKey(), (EntityData) item, childrenPrefix, childrenPrefix);
                    } else if (item instanceof Map) {
                        addMapChild(child, entry.getKey(), (Map<String, Object>) item, childrenPrefix, childrenPrefix);
                    }
                }
            } else if (value instanceof EntityData) {
                addEntityDataChild(child, entry.getKey(), (EntityData) value, childrenPrefix, childrenPrefix);
            } else if (value instanceof Map) {
                addMapChild(child, entry.getKey(), (Map<String, Object>) value, childrenPrefix, childrenPrefix);
            } else {
                SOAPElement field = child.addChildElement(entry.getKey(), childrenPrefix);
                field.addTextNode(convertToString(value));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void addMapChild(SOAPElement parent, String name, Map<String, Object> data, String elementPrefix, String childrenPrefix) throws SOAPException {
        SOAPElement child = parent.addChildElement(name, elementPrefix);

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;

            if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        addMapChild(child, entry.getKey(), (Map<String, Object>) item, childrenPrefix, childrenPrefix);
                    }
                }
            } else if (value instanceof Map) {
                addMapChild(child, entry.getKey(), (Map<String, Object>) value, childrenPrefix, childrenPrefix);
            } else {
                SOAPElement field = child.addChildElement(entry.getKey(), childrenPrefix);
                field.addTextNode(convertToString(value));
            }
        }
    }

    // ===========================================
    // PRIVATE - RESPONSE PARSING
    // ===========================================

    private List<EntityData> parseResponse(SOAPMessage response, ServiceConfig config, String entityType) throws Exception {
        List<EntityData> entities = new ArrayList<>();
        Document doc = response.getSOAPPart().getEnvelope().getOwnerDocument();
        NodeList resultNodes = doc.getElementsByTagNameNS("*", config.getResponseElement());

        List<String> childCollectionNames = new ArrayList<>();
        for (ChildCollectionConfig cc : getChildCollections(entityType)) {
            childCollectionNames.add(cc.getCollectionName());
        }

        for (int i = 0; i < resultNodes.getLength(); i++) {
            Node node = resultNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;

            Element element = (Element) node;
            EntityData entity = new EntityData(entityType);
            String parentId = null;

            // First pass: simple fields
            NodeList children = element.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() != Node.ELEMENT_NODE) continue;

                String fieldName = child.getLocalName();
                if (childCollectionNames.contains(fieldName)) continue;

                String fieldValue = child.getTextContent();
                if (config.getFieldMappings().containsKey(fieldName)) {
                    fieldName = config.getFieldMappings().get(fieldName);
                }

                entity.addValue(fieldName, fieldValue);

                if (config.getIdField().equals(fieldName)) {
                    entity.setId(fieldValue);
                    parentId = fieldValue;
                }
                if (config.getWatermarkField().equals(fieldName)) {
                    Long ts = parseTimestamp(fieldValue);
                    if (ts != null) entity.setLastModified(ts);
                }
            }

            // Second pass: child collections
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() != Node.ELEMENT_NODE) continue;

                String fieldName = child.getLocalName();
                if (!childCollectionNames.contains(fieldName)) continue;

                @SuppressWarnings("unchecked")
                List<EntityData> childList = (List<EntityData>) entity.getValue(fieldName);
                if (childList == null) childList = new ArrayList<>();

                EntityData childEntity = parseChildElement((Element) child, fieldName, parentId, childList.size());
                childList.add(childEntity);
                entity.addValue(fieldName, childList);
            }

            entities.add(entity);
        }

        log.debug("Parsed {} {} entities", entities.size(), entityType);
        return entities;
    }

    private EntityData parseChildElement(Element element, String entityName, String parentId, int index) {
        EntityData child = new EntityData(entityName);
        child.setChild(true);
        child.setParentId(parentId);

        String idField = getChildIdField(entityName);
        String childId = parentId + "#" + index;

        NodeList fields = element.getChildNodes();
        for (int i = 0; i < fields.getLength(); i++) {
            Node fieldNode = fields.item(i);
            if (fieldNode.getNodeType() != Node.ELEMENT_NODE) continue;

            String fieldName = fieldNode.getLocalName();
            Element fieldElement = (Element) fieldNode;

            // Check for nested elements (grandchildren)
            if (hasElementChildren(fieldElement)) {
                // Get existing list or create new one (handles multiple elements with same name)
                @SuppressWarnings("unchecked")
                List<EntityData> nestedList = (List<EntityData>) child.getValue(fieldName);
                if (nestedList == null) nestedList = new ArrayList<>();

                // Parse THIS element as the nested entity, not its children as separate entities
                nestedList.add(parseChildElement(fieldElement, fieldName, childId, nestedList.size()));
                child.addValue(fieldName, nestedList);
            } else {
                String fieldValue = fieldElement.getTextContent();
                child.addValue(fieldName, fieldValue);

                if (fieldName.equals(idField) && fieldValue != null && !fieldValue.isEmpty()) {
                    child.setId(fieldValue);
                    childId = fieldValue;
                }
                if (FIELD_LAST_UPDATE_DATE.equals(fieldName)) {
                    Long ts = parseTimestamp(fieldValue);
                    if (ts != null) child.setLastModified(ts);
                }
                if (FIELD_CREATION_DATE.equals(fieldName)) {
                    Long ts = parseTimestamp(fieldValue);
                    if (ts != null) child.setCreatedAt(ts);
                }
            }
        }

        if (child.getId() == null || child.getId().isEmpty()) {
            child.setId(parentId + "#" + index);
        }

        return child;
    }

    private EntityData parseMergeResponse(SOAPMessage response, ServiceConfig config, String entityType) throws Exception {
        Document doc = response.getSOAPPart().getEnvelope().getOwnerDocument();

        // Response structure: <result><Value>...fields...</Value></result>
        // Look for "Value" element inside result
        NodeList valueNodes = doc.getElementsByTagNameNS("*", "Value");

        if (valueNodes.getLength() == 0) {
            // Fallback to result if no Value element
            NodeList resultNodes = doc.getElementsByTagNameNS("*", "result");
            if (resultNodes.getLength() == 0) {
                log.warn("No result/Value element in merge response");
                return null;
            }
            return parseEntityFromNode(resultNodes.item(0), config, entityType);
        }

        return parseEntityFromNode(valueNodes.item(0), config, entityType);
    }

    private EntityData parseEntityFromNode(Node node, ServiceConfig config, String entityType) {
        if (node.getNodeType() != Node.ELEMENT_NODE) return null;

        Element element = (Element) node;
        EntityData entity = new EntityData(entityType);

        // Get child collection names for this entity type
        Set<String> childCollectionNames = getChildCollectionNames(entityType);

        // Map to collect child objects by collection name
        Map<String, List<Map<String, Object>>> childCollections = new HashMap<>();

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;

            String fieldName = child.getLocalName();

            // Check if this is a known child collection element
            if (childCollectionNames.contains(fieldName)) {
                // Parse child object recursively
                Map<String, Object> childObj = parseChildObject(child, fieldName);
                if (childObj != null && !childObj.isEmpty()) {
                    childCollections.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(childObj);
                }
            }
            // Simple text value
            else if (child.getChildNodes().getLength() == 1 && child.getFirstChild().getNodeType() == Node.TEXT_NODE) {
                String fieldValue = child.getTextContent();
                if (fieldValue != null && !fieldValue.trim().isEmpty()) {
                    entity.addValue(fieldName, fieldValue);

                    if (config.getIdField().equals(fieldName)) {
                        entity.setId(fieldValue);
                    }
                    if (config.getWatermarkField().equals(fieldName)) {
                        Long ts = parseTimestamp(fieldValue);
                        if (ts != null) entity.setLastModified(ts);
                    }
                }
            }
        }

        // Add child collections to entity
        for (Map.Entry<String, List<Map<String, Object>>> entry : childCollections.entrySet()) {
            entity.addValue(entry.getKey(), entry.getValue());
        }

        return entity;
    }

    /**
     * Parse a child object element into a map of field values.
     * Recursively parses nested children.
     */
    private Map<String, Object> parseChildObject(Node node, String childType) {
        if (node.getNodeType() != Node.ELEMENT_NODE) return null;

        Map<String, Object> childObj = new HashMap<>();
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;

            String fieldName = child.getLocalName();

            // Check for nested child collections (grandchildren)
            if (CHILD_ID_FIELDS.containsKey(fieldName)) {
                // This is a nested child collection - recursively parse
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nestedList = (List<Map<String, Object>>)
                        childObj.computeIfAbsent(fieldName, k -> new ArrayList<>());
                Map<String, Object> nestedChild = parseChildObject(child, fieldName);
                if (nestedChild != null && !nestedChild.isEmpty()) {
                    nestedList.add(nestedChild);
                }
            }
            // Simple text value
            else if (child.getChildNodes().getLength() == 1 && child.getFirstChild().getNodeType() == Node.TEXT_NODE) {
                String fieldValue = child.getTextContent();
                if (fieldValue != null && !fieldValue.trim().isEmpty()) {
                    childObj.put(fieldName, fieldValue);
                }
            }
            // Complex element with multiple children - recursively parse as object
            else if (child.getChildNodes().getLength() > 0) {
                Map<String, Object> nestedObj = parseChildObject(child, fieldName);
                if (nestedObj != null && !nestedObj.isEmpty()) {
                    childObj.put(fieldName, nestedObj);
                }
            }
        }

        return childObj;
    }

    /**
     * Get the set of child collection names for an entity type.
     */
    private Set<String> getChildCollectionNames(String entityType) {
        List<ChildCollectionConfig> configs = CHILD_COLLECTIONS.getOrDefault(entityType, List.of());
        Set<String> names = new HashSet<>();
        for (ChildCollectionConfig config : configs) {
            names.add(config.getCollectionName());
        }
        return names;
    }

    private boolean parseDeleteResponse(SOAPMessage response) throws Exception {
        SOAPBody body = response.getSOAPBody();
        if (body.hasFault()) {
            SOAPFault fault = body.getFault();
            throw new RuntimeException("Delete failed: " + fault.getFaultString());
        }
        return true;
    }

    // ===========================================
    // PRIVATE - SCHEMA PARSING
    // ===========================================

    private List<Map<String, Object>> parseSchemaFromWSDL(String wsdlUrl, String xsdFileName, String xsdComplexTypeName) {
        try {
            DocumentBuilder builder = createDocumentBuilder();
            Document doc = fetchDocument(builder, wsdlUrl);
            NodeList imports = doc.getElementsByTagNameNS(XSD_NS, "import");

            for (int i = 0; i < imports.getLength(); i++) {
                Element importElement = (Element) imports.item(i);
                String schemaLocation = importElement.getAttribute("schemaLocation");

                if (schemaLocation != null && !schemaLocation.contains("fault") && schemaLocation.contains("Service.xsd")) {
                    Document xsdDoc = fetchDocument(builder, schemaLocation);
                    NodeList nestedImports = xsdDoc.getElementsByTagNameNS(XSD_NS, "import");

                    for (int j = 0; j < nestedImports.getLength(); j++) {
                        Element nested = (Element) nestedImports.item(j);
                        String nestedLocation = nested.getAttribute("schemaLocation");

                        if (nestedLocation != null && nestedLocation.contains(xsdFileName)) {
                            Document entityXsd = fetchDocument(builder, nestedLocation);
                            List<Map<String, Object>> fields = extractFieldsFromXSD(entityXsd, xsdComplexTypeName);
                            if (!fields.isEmpty()) return fields;
                        }
                    }
                }
            }

            log.warn("Could not find schema for complexType: {} in XSD: {}", xsdComplexTypeName, xsdFileName);
            return List.of();
        } catch (Exception e) {
            log.error("Error parsing WSDL: {}\n{}", e.getMessage(), ExceptionUtils.getStackTrace(e));
            return List.of();
        }
    }

    private List<Map<String, Object>> extractFieldsFromXSD(Document xsdDoc, String xsdComplexTypeName) {
        List<Map<String, Object>> fields = new ArrayList<>();

        // Find the entity type key for this XSD complex type name (e.g., "CustomerAccount" -> "customer_accounts")
        String entityTypeKey = findEntityTypeKeyByXsdComplexTypeName(xsdComplexTypeName);
        Set<String> allowedChildCollections = getChildCollectionNames(entityTypeKey);

        NodeList complexTypes = xsdDoc.getElementsByTagNameNS(XSD_NS, "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            String typeName = complexType.getAttribute("name");
            if (xsdComplexTypeName.equals(typeName)) {
                NodeList elements = complexType.getElementsByTagNameNS(XSD_NS, "element");
                for (int j = 0; j < elements.getLength(); j++) {
                    Element element = (Element) elements.item(j);
                    fields.add(extractFieldDefinition(element, allowedChildCollections));
                }
                break;
            }
        }

        return fields;
    }

    /**
     * Find the entity type key (e.g., "customer_accounts") for a given XSD complex type name (e.g., "CustomerAccount").
     */
    private String findEntityTypeKeyByXsdComplexTypeName(String xsdComplexTypeName) {
        for (Map.Entry<String, ServiceConfig> entry : SERVICE_CONFIGS.entrySet()) {
            if (xsdComplexTypeName.equals(entry.getValue().getXsdComplexTypeName())) {
                return entry.getKey();
            }
        }
        return "";
    }

    /**
     * Extract field definition from XSD element.
     *
     * @param element The XSD element
     * @param allowedChildCollections Set of field names that are true child collections (from CHILD_COLLECTIONS config).
     *                                 Only fields in this set will be typed as "child" - all other complex types
     *                                 will be treated as strings (since Oracle WSDL defines many complex types
     *                                 that actually return flattened string values in SOAP responses).
     */
    private Map<String, Object> extractFieldDefinition(Element element, Set<String> allowedChildCollections) {
        String fieldName = element.getAttribute("name");
        String fieldType = element.getAttribute("type");
        String minOccurs = element.getAttribute("minOccurs");
        String maxOccurs = element.getAttribute("maxOccurs");
        String nillable = element.getAttribute("nillable");
        String defaultValue = element.getAttribute("default");

        boolean isArray = "unbounded".equalsIgnoreCase(maxOccurs) ||
                (maxOccurs != null && !maxOccurs.isEmpty() && Integer.parseInt(maxOccurs) > 1);
        boolean isComplex = !isPrimitiveType(fieldType);
        boolean required = !TRUE.equalsIgnoreCase(nillable) &&
                (defaultValue == null || defaultValue.isEmpty()) &&
                (minOccurs == null || minOccurs.isEmpty() || Integer.parseInt(minOccurs) >= 1);

        Map<String, Object> field = new HashMap<>();
        field.put("name", fieldName);
        field.put("type", fieldType);
        field.put("minOccurs", minOccurs);
        field.put("maxOccurs", maxOccurs);
        field.put("nillable", nillable);
        field.put("default", defaultValue);
        field.put("isArray", isArray);
        field.put("isComplexType", isComplex);
        field.put("required", required);

        // For complex types, check if it's a true child entity by using CHILD_COLLECTIONS as source of truth.
        // Only fields explicitly defined in CHILD_COLLECTIONS should be typed as "child".
        // All other complex types (flexfields, DFFs, embedded objects, etc.) return flattened strings
        // in SOAP responses, so they should be typed as "string".
        boolean isChild = false;
        if (isComplex) {
            String typeName = extractTypeName(fieldType);
            // Use allowlist approach: only fields in CHILD_COLLECTIONS are true child entities
            isChild = allowedChildCollections.contains(fieldName);
            field.put("childEntityName", typeName);
            field.put("isChildEntity", isChild);
        }

        field.put("syncariType", mapToSyncariType(fieldType, isArray, isComplex && isChild));

        return field;
    }

    /**
     * Check if the XSD type is a primitive/simple type (not a complex type).
     *
     * XML Schema built-in primitive datatypes (19):
     * string, boolean, decimal, float, double, duration, dateTime, time, date,
     * gYearMonth, gYear, gMonthDay, gDay, gMonth, hexBinary, base64Binary, anyURI, QName, NOTATION
     *
     * XML Schema built-in derived datatypes (25):
     * normalizedString, token, language, NMTOKEN, NMTOKENS, Name, NCName, ID, IDREF, IDREFS,
     * ENTITY, ENTITIES, integer, nonPositiveInteger, negativeInteger, long, int, short, byte,
     * nonNegativeInteger, unsignedLong, unsignedInt, unsignedShort, unsignedByte, positiveInteger
     *
     * @see <a href="https://www.w3.org/TR/xmlschema-2/#built-in-datatypes">W3C XML Schema Part 2: Datatypes</a>
     */
    private static final Set<String> XSD_PRIMITIVE_TYPES = Set.of(
            // 19 built-in primitive datatypes
            "string", "boolean", "decimal", "float", "double", "duration", "datetime", "time", "date",
            "gyearmonth", "gyear", "gmonthday", "gday", "gmonth", "hexbinary", "base64binary", "anyuri", "qname", "notation",
            // 25 built-in derived datatypes
            "normalizedstring", "token", "language", "nmtoken", "nmtokens", "name", "ncname", "id", "idref", "idrefs",
            "entity", "entities", "integer", "nonpositiveinteger", "negativeinteger", "long", "int", "short", "byte",
            "nonnegativeinteger", "unsignedlong", "unsignedint", "unsignedshort", "unsignedbyte", "positiveinteger"
    );

    private boolean isPrimitiveType(String type) {
        if (type == null || type.isEmpty()) return true;

        // Extract the type name without namespace prefix (e.g., "xsd:string" -> "string")
        String typeName = extractTypeName(type).toLowerCase();

        // Oracle uses custom type names like "dateTime-Timestamp" and "date-Date"
        // Extract base type before hyphen (e.g., "datetime-timestamp" -> "datetime")
        if (typeName.contains("-")) {
            typeName = typeName.substring(0, typeName.indexOf("-"));
        }

        // Check against standard XSD primitive and derived types
        return XSD_PRIMITIVE_TYPES.contains(typeName);
    }

    /**
     * Map XSD type to Syncari type.
     *
     * XSD Primitive Types mapped to Syncari Types:
     * - string, normalizedString, token, Name, NCName, language, NMTOKEN, ID, IDREF, ENTITY, anyURI, QName, NOTATION -> STRING
     * - boolean -> BOOLEAN
     * - decimal, float, double -> DOUBLE
     * - integer, int, short, byte, nonPositiveInteger, negativeInteger, nonNegativeInteger, unsignedInt, unsignedShort, unsignedByte, positiveInteger -> INTEGER
     * - long, unsignedLong -> LONG
     * - date, gYear, gYearMonth, gMonth, gMonthDay, gDay -> DATE
     * - dateTime, time, duration -> DATETIME
     * - hexBinary, base64Binary -> STRING (binary data treated as string, same as SAP connector)
     */
    private String mapToSyncariType(String xsdType, boolean isArray, boolean isComplex) {
        if (isComplex) return isArray ? "OBJECT_ARRAY" : "OBJECT";
        if (xsdType == null) return "STRING";

        String type = extractTypeName(xsdType).toLowerCase();

        // Oracle uses custom type names like "dateTime-Timestamp" and "date-Date"
        // Extract base type before hyphen (e.g., "datetime-timestamp" -> "datetime")
        if (type.contains("-")) {
            type = type.substring(0, type.indexOf("-"));
        }

        String baseType;
        switch (type) {
            // Boolean
            case "boolean":
                baseType = "BOOLEAN";
                break;

            // Long (64-bit integer)
            case "long":
            case "unsignedlong":
                baseType = "LONG";
                break;

            // Integer (32-bit and smaller)
            case "integer":
            case "int":
            case "short":
            case "byte":
            case "nonpositiveinteger":
            case "negativeinteger":
            case "nonnegativeinteger":
            case "unsignedint":
            case "unsignedshort":
            case "unsignedbyte":
            case "positiveinteger":
                baseType = "INTEGER";
                break;

            // Double/Decimal
            case "decimal":
            case "float":
            case "double":
                baseType = "DOUBLE";
                break;

            // Date only (no time component)
            case "date":
            case "gyear":
            case "gyearmonth":
            case "gmonth":
            case "gmonthday":
            case "gday":
                baseType = "DATE";
                break;

            // DateTime (with time component)
            case "datetime":
            case "time":
            case "duration":
                baseType = "DATETIME";
                break;

            // Binary data types - map to STRING (same as SAP connector approach)
            case "hexbinary":
            case "base64binary":
                baseType = "STRING";
                break;

            // String (default for all string-like types)
            case "string":
            case "normalizedstring":
            case "token":
            case "name":
            case "ncname":
            case "language":
            case "nmtoken":
            case "nmtokens":
            case "id":
            case "idref":
            case "idrefs":
            case "entity":
            case "entities":
            case "anyuri":
            case "qname":
            case "notation":
            default:
                baseType = "STRING";
                break;
        }

        return isArray ? baseType + "_ARRAY" : baseType;
    }

    private String extractTypeName(String type) {
        if (type == null) return "";
        return type.contains(":") ? type.substring(type.indexOf(":") + 1) : type;
    }

    // ===========================================
    // PRIVATE - HTTP/SOAP
    // ===========================================

    private SOAPMessage makeRequest(String endpoint, SOAPMessage payload) throws Exception {
        String url = authConfig.getEndpoint() + endpoint;
        log.debug("SOAP request to: {}", url);
        logMessage("REQUEST", payload);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
        conn.setRequestProperty("Accept-Encoding", "gzip");
        conn.setRequestProperty("Authorization", getAuthHeader());
        conn.setDoOutput(true);
        conn.setDoInput(true);

        try (OutputStream os = conn.getOutputStream()) {
            payload.writeTo(os);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            String error = readErrorResponse(conn);
            log.error("SOAP failed HTTP {}: {}", responseCode, error);
            throw new RuntimeException("SOAP request failed: HTTP " + responseCode);
        }

        try (InputStream is = getDecodedStream(conn)) {
            SOAPMessage response = MessageFactory.newInstance().createMessage(null, is);
            logMessage("RESPONSE", response);
            return response;
        }
    }

    private void addAuthHeaders(SOAPMessage message) {
        message.getMimeHeaders().addHeader("Authorization", getAuthHeader());
    }

    private String getAuthHeader() {
        String auth = authConfig.getUserName() + ":" + authConfig.getPassword();
        return "Basic " + Base64.encode(auth.getBytes(StandardCharsets.UTF_8));
    }

    private InputStream getDecodedStream(HttpURLConnection conn) throws Exception {
        InputStream stream = conn.getInputStream();
        return "gzip".equalsIgnoreCase(conn.getContentEncoding()) ? new GZIPInputStream(stream) : stream;
    }

    private String readErrorResponse(HttpURLConnection conn) {
        try {
            InputStream errorStream = conn.getErrorStream();
            if (errorStream == null) return "";
            if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) {
                errorStream = new GZIPInputStream(errorStream);
            }
            return new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Error reading response: " + e.getMessage();
        }
    }

    private void logMessage(String prefix, SOAPMessage message) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out);
            log.debug("[SOAP {}]\n{}", prefix, out.toString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Could not log SOAP message: {}", e.getMessage());
        }
    }

    // ===========================================
    // PRIVATE - UTILITIES
    // ===========================================

    private String formatTimestamp(long epochMillis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC)
                .format(ORACLE_DATE_FORMAT);
    }

    private Long parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return null;
        try {
            return Instant.parse(timestamp).toEpochMilli();
        } catch (Exception e1) {
            try {
                return ZonedDateTime.parse(timestamp).toInstant().toEpochMilli();
            } catch (Exception e2) {
                log.warn("Could not parse timestamp: {}", timestamp);
                return null;
            }
        }
    }

    private String convertToString(Object value) {
        if (value == null) return "";
        if (value instanceof Date) {
            return ORACLE_DATE_FORMAT.format(((Date) value).toInstant().atZone(ZoneOffset.UTC));
        }
        if (value instanceof Instant) {
            return ORACLE_DATE_FORMAT.format(((Instant) value).atZone(ZoneOffset.UTC));
        }
        return value.toString();
    }

    private boolean hasElementChildren(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) return true;
        }
        return false;
    }

    private DocumentBuilder createDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder();
    }

    private Document fetchDocument(DocumentBuilder builder, String url) throws Exception {
        URLConnection conn = new URL(url).openConnection();
        conn.setRequestProperty("Authorization", getAuthHeader());
        try (InputStream is = conn.getInputStream()) {
            return builder.parse(is);
        }
    }
}
