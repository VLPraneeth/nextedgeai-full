package com.syncari.connector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.syncari.connector.Capability;
import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.ListBasedIterator;
import com.syncari.connector.data.iterator.NetsuiteIncrementalIterator;
import com.syncari.utils.Pair;
import org.jooq.lambda.function.Function3;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.ProxyConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.rest.NetSuiteRestClient;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.RestClientService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.seed.NetsuiteSeed;
import com.syncari.connector.service.seed.NetsuiteSuiteQLSeed;
import com.syncari.utils.DateUtil;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Date;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

/**
 * NetSuite SuiteQL-only synapse implementation.
 *
 * This service uses only NetSuite REST API with SuiteQL queries.
 * It does NOT use SOAP API.
 *
 * Supported Operations:
 * - READ: Via SuiteQL SELECT queries
 * - CREATE/UPDATE/DELETE: Via REST Record API
 * - Custom records: Fully supported via SuiteQL
 * - Standard entities: All queryable via SuiteQL
 * - Custom fields: Auto-discovered via REST metadata-catalog
 *
 * NOT Supported:
 * - Saved searches (SOAP-only)
 * - File operations (SOAP-only)
 * - Picklist enumeration via SOAP (manual configuration required)
 *
 * Known Limitations:
 * - Subsidiary entity may have incomplete field data (old SOAP-based service augmented REST data with additional SOAP fields)
 */
@Slf4j
@Component(Constants.NETSUITE_SUITEQL)
public class NetsuiteSuiteQLService implements AuthenticationService, CommonDataService, MetadataService,
        SynapseInfoService, RestClientService {

    private static final String VERSION = "v1";
    private static final String SUITE_QUERY_URL = "%s/services/rest/query/%s/suiteql";
    private static final String SUITE_QUERY_URL_WITH_PAGINATION = "%s/services/rest/query/%s/suiteql?offset=%s&limit=%s";
    private static final String RECORD_URL = "%s/services/rest/record/%s/%s";
    private static final String SINGLE_RECORD_URL = "%s/services/rest/record/%s/%s/%s";
    private static final String ITEM_URL = "%s/services/rest/record/v1/%s/%s?expandSubResources=true";
    private static final String DESCRIBE_URL = "%s/record/%s/metadata-catalog/%s";
    private static final String API_PREFIX = "%s/services/rest";
    private static final String SCHEMA_JSON = "application/schema+json";
    private static final String ACCEPT = "Accept";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String UTC_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    static final String TIMEZONE_ID = "timeZoneId";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String REPLACE_SUBLIST = "replaceSublist";

    // SuiteQL queries for dynamic custom field/record discovery
    private static final String GET_CUSTOM_RECORD_TYPE_SUITESQL = "SELECT lower(scriptid) as apiname, name FROM CustomRecordType where isinactive='F' ORDER BY name";
    private static final String GET_FIELD_REFERENCE_TYPE_SUITESQL = "SELECT lower(scriptid) as apiname, name, BUILTIN.DF(fieldvaluetyperecord) AS referredentityname FROM CustomField WHERE fieldvaluetyperecord IS NOT NULL ORDER BY name";

    private static final int MAX_PAGE_SIZE = 1000;
    private static final int GETBYIDS_BATCH_SIZE = 1000; // Oracle IN clause limit
    private static final int MAX_RECORDS_PER_PAGE_FOR_QUERY_API = 500; // SuiteQL query API page size (matches old SOAP synapse)
    private static final long WATERMARK_INCREMENT = 24 * 60 * 60 * 1000L; // 1 day
    private static final int WAIT_TIMEOUT_MILLIS = 300000; // 5 minutes

    // Entities that don't support watermark-based WHERE clause filtering in queries
    // These entities must be fetched in full every sync cycle without WHERE clause filtering
    private static final Set<String> NO_WM_ENTITIES = Set.of("account", "billingaccount", "billingschedule",
            "subsidiary", "campaign", "subscription", "subscriptionline",
            "subscriptionchangeorder", "subscriptionchangeorderline", "currency", "subscriptionplan",
            "subscriptionplanline", "subscriptionterm",
            "pricelevel", "location", "customerstatus", "classification", "department");

    private static final Set<String> EMBEDDED_REF_KEYS = Set.of("links", "totalResults", "count", "hasMore", "offset", "items");
    private static final Set<String> REF_KEYS = Set.of("id", "refName", "externalId", "links");

    private static final Map<String, Reference> JOURNAL_LINE_REFERENCES = Map.of(
            "entity", new Reference("customer", "entity", "Customer")
    );

    private static final Set<String> LEGACY_TAX_SUPPORTED_ENTITIES = Set.of("invoicelineitem", "estimatelineitem", "salesorderlineitem");

    private static final Set<String> SYSTEM_FIELDS = Set.of("lastModifiedDate", "createdDate");

    // Helper class for entity to SuiteQL table name mapping
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ItemQueryInfo {
        final String tableName;
        final String whereClause;
    }

    // Maps entity names to actual SuiteQL table names with optional WHERE clauses
    // Many item types don't have their own tables - they're all in "item" table with filters
    private static final Map<String, ItemQueryInfo> ITEM_ENTITY_TO_SUITEQL_MAP = Map.ofEntries(
        // Item types - all map to "item" table with itemtype filters
        Map.entry("paymentitem", new ItemQueryInfo("item", "itemtype = 'Payment'")),
        Map.entry("noninventorysaleitem", new ItemQueryInfo("item", "itemtype = 'NonInvtPart' AND subtype = 'Sale'")),
        Map.entry("noninventorypurchaseitem", new ItemQueryInfo("item", "itemtype = 'NonInvtPart' AND subtype = 'Purchase'")),
        Map.entry("noninventoryresaleitem", new ItemQueryInfo("item", "itemtype = 'NonInvtPart' AND subtype = 'Resale'")),
        Map.entry("inventoryitem", new ItemQueryInfo("item", "itemtype = 'InvtPart'")),
        Map.entry("serviceitem", new ItemQueryInfo("item", "itemtype = 'Service'")),
        Map.entry("servicesaleitem", new ItemQueryInfo("item", "itemtype = 'Service' AND subtype = 'Sale'")),
        Map.entry("servicepurchaseitem", new ItemQueryInfo("item", "itemtype = 'Service' AND subtype = 'Purchase'")),
        Map.entry("serviceresaleitem", new ItemQueryInfo("item", "itemtype = 'Service' AND subtype = 'Resale'")),
        Map.entry("kititem", new ItemQueryInfo("item", "itemtype = 'Kit'")),
        Map.entry("assemblyitem", new ItemQueryInfo("item", "itemtype = 'Assembly'")),
        Map.entry("otherchargesaleitem", new ItemQueryInfo("item", "itemtype = 'OthCharge' AND subtype = 'Sale'")),
        Map.entry("otherchargepurchaseitem", new ItemQueryInfo("item", "itemtype = 'OthCharge' AND subtype = 'Purchase'")),
        Map.entry("otherchargeresaleitem", new ItemQueryInfo("item", "itemtype = 'OthCharge' AND subtype = 'Resale'")),

        // Additional item types from old SOAP service
        Map.entry("descriptionitem", new ItemQueryInfo("item", "itemtype = 'Description'")),
        Map.entry("discountitem", new ItemQueryInfo("item", "itemtype = 'Discount'")),
        Map.entry("downloaditem", new ItemQueryInfo("item", "itemtype = 'Download'")),
        Map.entry("giftcertificateitem", new ItemQueryInfo("item", "itemtype = 'GiftCert'")),
        Map.entry("itemgroup", new ItemQueryInfo("item", "itemtype = 'Group'")),
        Map.entry("markupitem", new ItemQueryInfo("item", "itemtype = 'Markup'")),
        Map.entry("subtotalitem", new ItemQueryInfo("item", "itemtype = 'Subtotal'")),
        Map.entry("lotnumberedassemblyitem", new ItemQueryInfo("item", "itemtype = 'Assembly' AND islotnumbered = 'T'")),
        Map.entry("lotnumberedinventoryitem", new ItemQueryInfo("item", "itemtype = 'InvtPart' AND islotnumbered = 'T'")),
        Map.entry("serializedassemblyitem", new ItemQueryInfo("item", "itemtype = 'Assembly' AND isserialized = 'T'")),
        Map.entry("serializedinventoryitem", new ItemQueryInfo("item", "itemtype = 'InvtPart' AND isserialized = 'T'")),

        // Other entity mappings
        Map.entry("campaign", new ItemQueryInfo("searchcampaign", null)),

        // Transaction types - all map to "transaction" table with type filters
        Map.entry("journalentry", new ItemQueryInfo("transaction", "type = 'Journal'")),
        Map.entry("salesorder", new ItemQueryInfo("transaction", "type = 'SalesOrd'")),
        Map.entry("invoice", new ItemQueryInfo("transaction", "type = 'CustInvc'")),
        Map.entry("estimate", new ItemQueryInfo("transaction", "type = 'Estimate'")),
        Map.entry("cashsale", new ItemQueryInfo("transaction", "type = 'CashSale'")),
        Map.entry("customerpayment", new ItemQueryInfo("transaction", "type = 'CustPymt'")),
        Map.entry("customerdeposit", new ItemQueryInfo("transaction", "type = 'CustDep'")),
        Map.entry("cashrefund", new ItemQueryInfo("transaction", "type = 'CashRfnd'")),
        Map.entry("customerrefund", new ItemQueryInfo("transaction", "type = 'CustRfnd'")),
        Map.entry("creditmemo", new ItemQueryInfo("transaction", "type = 'CustCred'")),
        Map.entry("purchaseorder", new ItemQueryInfo("transaction", "type = 'PurchOrd'")),

        // Vendor transaction types
        Map.entry("vendorbill", new ItemQueryInfo("transaction", "type = 'VendBill'")),
        Map.entry("vendorcredit", new ItemQueryInfo("transaction", "type = 'VendCred'")),
        Map.entry("vendorpayment", new ItemQueryInfo("transaction", "type = 'VendPymt'")),
        Map.entry("vendorreturnauthorization", new ItemQueryInfo("transaction", "type = 'VendAuth'")),

        // Inventory and warehouse transaction types
        Map.entry("itemreceipt", new ItemQueryInfo("transaction", "type = 'ItemRcpt'")),
        Map.entry("itemfulfillment", new ItemQueryInfo("transaction", "type = 'ItemShip'")),
        Map.entry("inventorytransfer", new ItemQueryInfo("transaction", "type = 'InvTrnfr'")),
        Map.entry("inventoryadjustment", new ItemQueryInfo("transaction", "type = 'InvAdjst'")),
        Map.entry("bintransfer", new ItemQueryInfo("transaction", "type = 'BinTrnfr'")),
        Map.entry("assemblybuild", new ItemQueryInfo("transaction", "type = 'Build'")),
        Map.entry("assemblyunbuild", new ItemQueryInfo("transaction", "type = 'Unbuild'")),
        Map.entry("transferorder", new ItemQueryInfo("transaction", "type = 'TrnfrOrd'")),
        Map.entry("workorder", new ItemQueryInfo("transaction", "type = 'WorkOrd'")),

        Map.entry("opportunity", new ItemQueryInfo("transaction", "type = 'Opprtnty'")),
        Map.entry("check", new ItemQueryInfo("transaction", "type = 'Check'")),
        Map.entry("deposit", new ItemQueryInfo("transaction", "type = 'Deposit'")),
        Map.entry("depositapplication", new ItemQueryInfo("transaction", "type = 'DepAppl'")),
        Map.entry("expensereport", new ItemQueryInfo("transaction", "type = 'ExpRept'")),
        Map.entry("returnauthorization", new ItemQueryInfo("transaction", "type = 'RtnAuth'"))
    );

    // Reference field helper class
    @lombok.Data
    @lombok.AllArgsConstructor
    static class Reference {
        private String referredEntityName;
        private String referenceFieldName;
        private String referenceFieldLabel;
    }

    // Standard reference fields that should be added even if not in metadata
    // Copied from NetsuiteService.STANDARD_REFERENCES (lines 201-313)
    private static final Map<String, Set<Reference>> STANDARD_REFERENCES = Map.ofEntries(
            Map.entry("opportunity", Set.of(
                    new Reference("customer", "entity", "Customer"),
                    new Reference("currency", "currency", "Currency"),
                    new Reference("location", "location", "Location"),
                    new Reference("partner", "partner", "Partner"),
                    new Reference("employee", "salesRep", "Sales Rep"),
                    new Reference("department", "department", "Department"),
                    new Reference("nexus", "nexus", "Nexus"),
                    new Reference("nexus", "entityNexus", "Customer Nexus"),
                    new Reference("contact", "contacts", "Contacts")
            )),
            Map.entry("estimate", Set.of(
                    new Reference("customer", "entity", "Customer")
            )),
            Map.entry("estimatelineitem", Set.of(
                    new Reference("pricelevel", "price", "Price")
            )),
            Map.entry("contact", Set.of(
                    new Reference("customer", "company", "Company")
            )),
            Map.entry("supportcase", Set.of(
                    new Reference("company", "company", "Company")
            )),
            Map.entry("salesorder", Set.of(
                    new Reference("customer", "entity", "Customer")
            )),
            Map.entry("invoice", Set.of(
                    new Reference("customer", "entity", "Customer")
            )),
            Map.entry("customerpayment", Set.of(
                    new Reference("customer", "customer", "Customer"),
                    new Reference("location", "location", "Location"),
                    new Reference("department", "department", "Department"),
                    new Reference("currency", "currency", "Currency")
            )),
            Map.entry("cashrefund", Set.of(
                    new Reference("customer", "entity", "Customer"),
                    new Reference("location", "location", "Location"),
                    new Reference("department", "department", "Department"),
                    new Reference("account", "account", "Account"),
                    new Reference("currency", "currency", "Currency")
            )),
            Map.entry("customerdeposit", Set.of(
                    new Reference("customer", "customer", "Customer"),
                    new Reference("location", "location", "Location"),
                    new Reference("department", "department", "Department"),
                    new Reference("currency", "currency", "Currency"),
                    new Reference("salesorder", "salesOrder", "Salesorder")
            )),
            Map.entry("salesorderlineitem", Set.of(
                    new Reference("pricelevel", "price", "Price")
            )),
            Map.entry("subscription", Set.of(
                    new Reference("billingaccount", "billingAccount", "Billing Account"),
                    new Reference("billingschedule", "billingSchedule", "Billing Schedule"),
                    new Reference("currency", "currency", "Currency"),
                    new Reference("location", "location", "Location"),
                    new Reference("customer", "customer", "Customer"),
                    new Reference("subscriptionplan", "defaultRenewalPlan", "Default Renewal Plan"),
                    new Reference("pricebook", "pricebook", "Price Book"),
                    new Reference("subscriptionplan", "subscriptionPlan", "Subscription Plan"),
                    new Reference("subsidiary", "subsidiary", "Subsidiary")
            )),
            Map.entry("priceinterval", Set.of(
                    new Reference("priceplan", "pricePlan", "Price Plan")
            )),
            Map.entry("subscriptionchangeorder", Set.of(
                    new Reference("billingaccount", "billingAccount", "Billing Account"),
                    new Reference("employee", "requestor", "Requestor"),
                    new Reference("customer", "customer", "Customer"),
                    new Reference("subscription", "subscription", "Subscription"),
                    new Reference("subscriptionplan", "subscriptionPlan", "Subscription Plan"),
                    new Reference("subsidiary", "subsidiary", "Subsidiary")
            )),
            Map.entry("subscriptionplan", Set.of(
                    new Reference("subscriptionplan", "defaultRenewalPlan", "Default Renewal Plan"),
                    new Reference("account", "incomeAccount", "Income Account"),
                    new Reference("subsidiary", "subsidiary", "Subsidiary")
            )),
            Map.entry("billingaccount", Set.of(
                    new Reference("billingschedule", "billingSchedule", "Billing Schedule"),
                    new Reference("currency", "currency", "Currency"),
                    new Reference("customer", "customer", "Customer"),
                    new Reference("location", "location", "Location"),
                    new Reference("subsidiary", "subsidiary", "Subsidiary")
            )),
            Map.entry("subsidiary", Set.of(
                    new Reference("subsidiary", "parent", "Parent"),
                    new Reference("currency", "currency", "Currency")
            )),
            Map.entry("customer", Set.of(
                    new Reference("customer", "parent", "Parent"),
                    new Reference("subsidiary", "subsidiary", "Subsidiary"),
                    new Reference("employee", "salesRep", "Sales Rep")
            )),
            Map.entry("location", Set.of(
                    new Reference("location", "parent", "Parent"),
                    new Reference("subsidiary", "subsidiary", "Subsidiary")
            )),
            Map.entry("classification", Set.of(
                    new Reference("classification", "parent", "Parent"),
                    new Reference("subsidiary", "subsidiary", "Subsidiary")
            )),
            Map.entry("department", Set.of(
                    new Reference("department", "parent", "Parent"),
                    new Reference("subsidiary", "subsidiary", "Subsidiary")
            ))
    );

    public static final Set<String> READ_ONLY_ENTITIES = Set.of(
            "currency", "billingschedule", "subsidiary", "location", "subscriptionterm", "estimatelineitem",
            "customerrefund", "classification", "department", "assemblybuild", "assemblyunbuild", "bintransfer",
            "check", "deposit", "depositapplication", "expensereport", "inventoryadjustment",
            "inventorytransfer", "itemfulfillment", "itemreceipt", "returnauthorization", "transferorder", "vendorbill",
            "vendorcredit", "vendorpayment", "vendorreturnauthorization", "workorder"
    );

    // Entities that are NOT supported in SuiteQL REST API
    // These entities were available in SOAP API but do not exist as queryable tables in SuiteQL
    // They are hidden from the UI to prevent sync errors
    private static final Set<String> UNSUPPORTED_SUITEQL_ENTITIES = Set.of(
            "item",  // Generic item table - actual items are inventoryitem, serviceitem, etc.

            // These entities do NOT exist as queryable tables in SuiteQL REST API
            // They are listed in seed file but fail with "Record not found" or "Invalid search type" errors
            "binworksheet",
            "inventorycostrevaluation",
            "workorderissue",
            "workordercompletion",
            "workorderclose",
            "paycheckjournal",
            "intercompanyjournalentry",
            "statisticaljournalentry",

            // Pricing entities - parent entities don't exist in SuiteQL
            "priceplan",
            "pricebook",
            "pricetier",       // Child entity - parent priceplan doesn't exist
            "priceinterval",   // Child entity - parent priceplan doesn't exist

            // Subscription entities - not supported in SuiteQL REST API
            "subscription",
            "subscriptionline",
            "subscriptionchangeorder",
            "subscriptionchangeorderline",
            "subscriptionplan",
            "subscriptionplanline",

            // Transaction line entity - doesn't have lastModifiedDate field in SuiteQL
            // Must be queried via JOIN with transaction table, not standalone
            "transactionline"
    );

    // Child entity support - maps from NetsuiteSuiteQLSeed
    private static final Set<String> supportedChildEntities = NetsuiteSuiteQLSeed.supportedChildEntities.keySet();

    // Map child entities to their parent entities
    // Note: pricetier and priceinterval are NOT included because their parent
    // entities (priceplan) don't exist in SuiteQL REST API
    private static final Map<String, String> CHILD_PARENT_ENTITY_MAP = Map.ofEntries(
            Map.entry("salesorderlineitem", "salesorder"),
            Map.entry("purchaseorderlineitem", "purchaseorder"),
            Map.entry("cashsalelineitem", "cashsale"),
            Map.entry("creditmemolineitem", "creditmemo"),
            Map.entry("invoicelineitem", "invoice"),
            Map.entry("estimatelineitem", "estimate"),
            Map.entry("customerpaymentlineitem", "customerpayment"),
            Map.entry("cashrefundlineitem", "cashrefund"),
            Map.entry("subscriptionline", "subscription"),
            Map.entry("subscriptionchangeorderline", "subscriptionchangeorder"),
            Map.entry("subscriptionplanline", "subscriptionplan"),
            Map.entry("kititemmember", "kititem")
    );

    // Map parent entity to child field name in the parent record
    private static final Map<String, Map<String, String>> CHILD_API_NAMES = Map.ofEntries(
            Map.entry("salesorder", Map.of("salesorderlineitem", "item")),
            Map.entry("purchaseorder", Map.of("purchaseorderlineitem", "item")),
            Map.entry("cashsale", Map.of("cashsalelineitem", "item")),
            Map.entry("creditmemo", Map.of("creditmemolineitem", "item")),
            Map.entry("estimate", Map.of("estimatelineitem", "item")),
            Map.entry("invoice", Map.of("invoicelineitem", "item")),
            Map.entry("customerpayment", Map.of("customerpaymentlineitem", "apply")),
            Map.entry("cashrefund", Map.of("cashrefundlineitem", "item")),
            Map.entry("subscription", Map.of("subscriptionline", "subscriptionLine", "priceinterval", "priceinterval")),
            Map.entry("subscriptionchangeorder", Map.of("subscriptionchangeorderline", "subLine")),
            Map.entry("subscriptionplan", Map.of("subscriptionplanline", "member")),
            Map.entry("priceplan", Map.of("pricetier", "priceTiers")),
            Map.entry("kititem", Map.of("kititemmember", "member"))
    );

    @Autowired
    DateUtil dateUtil;

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new ParameterNamesModule())
            .addModule(new Jdk8Module())
            .addModule(new JavaTimeModule())
            .defaultDateFormat(new SimpleDateFormat(DATE_FORMAT))
            .build();

    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    static class SuiteQLResponse {
        private List<Map<String, Object>> items;
        private int offset;
        private int count;
        private boolean hasMore;
        private int totalResults;
    }

    // ============================================================================
    // AuthenticationService Implementation
    // ============================================================================

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            AuthConfig authConfig = config.getAuthConfig();
            authConfig.setEndpoint(config.getEndpoint());
            ArrayList<String> errorMessages = new ArrayList<>();
            entityNames.forEach(entityName -> {
                // file is fabricated entity, do not try to pull metadata from netsuite.
                if ("file".equalsIgnoreCase(entityName)) return;
                String authenticateEndpoint = format(DESCRIBE_URL, getApiUrlPrefix(config), VERSION, entityName);
                NetSuiteRestClient restClient = getNetSuiteRestClient();
                AuthConfig authConfigWithHeaders = authConfig.addHeader(ACCEPT, SCHEMA_JSON);
                ResponseEntity<String> responseEntity = restClient.getResponse(authenticateEndpoint, authConfigWithHeaders);

                log.info(format("Testing entity name %s, got response code %s", entityName, responseEntity.getStatusCodeValue()));
                String responseBody = responseEntity.getBody();
                if (responseEntity.getStatusCodeValue() != 200 || !isValidTestResponse(responseBody)) {
                    errorMessages.add(format(i18n("invalid_response_record"), entityName));
                    log.error(format("NetSuite authentication error with response body : %s", responseBody));
                }
            });

            if (!errorMessages.isEmpty()) {
                response = new TestConnectionResponse(TestConnectionResponse.AUTH_FAILED_MESSAGE, ConnectorErrorCodes.CONNECTION_ERROR, errorMessages);
            } else {
                log.info(format("Successfully authenticated NetSuite connection for %s", config.getName()));
                // Leave response with null message and empty errors to indicate success (isSuccess() will return true)
            }
            return response;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                response = new TestConnectionResponse(TestConnectionResponse.AUTH_FAILED_MESSAGE, ConnectorErrorCodes.CONNECTION_ERROR,
                    Arrays.asList(i18n("authentication_failed")));
            } else {
                response = new TestConnectionResponse(TestConnectionResponse.AUTH_FAILED_MESSAGE, ConnectorErrorCodes.CONNECTION_ERROR,
                        Arrays.asList(e.getMessage()));
            }
            log.error("NetSuite authentication error with message: " + e.getMessage());
            return response;
        } catch (NonRetriableException e) {
            log.error("NetSuite authentication error with message: " + e.getMessage());
            if (ErrorCodes.BAD_ENDPOINT.name().equals(e.getErrorCode())) {
                response = new TestConnectionResponse(TestConnectionResponse.AUTH_FAILED_MESSAGE, ConnectorErrorCodes.CONNECTION_ERROR,
                    Arrays.asList(i18n("invalid_endpoint")));
                return response;
            }
            return getErrorResponse(e);
        } catch (Exception e) {
            // Catch all other exceptions (network errors, timeouts, unknown hosts, etc.)
            log.error("NetSuite connection test failed with exception: " + e.getMessage(), e);
            response = new TestConnectionResponse(TestConnectionResponse.AUTH_FAILED_MESSAGE, ConnectorErrorCodes.CONNECTION_ERROR,
                    Arrays.asList(i18n("invalid_endpoint")));
            return response;
        }
    }

    private TestConnectionResponse getErrorResponse(NonRetriableException e) {
        try {
            TestConnectionResponse response = new TestConnectionResponse("Invalid login", ConnectorErrorCodes.CONNECTION_ERROR,
                    Arrays.asList(e.getMessage()));
            handleAuthenticationErrorMessage(response, e);
            return response;
        } catch (Exception msgE) {
            log.error(String.format("Invalid response: %s", msgE.getMessage()));
            return new TestConnectionResponse(TestConnectionResponse.AUTH_FAILED_MESSAGE, ConnectorErrorCodes.CONNECTION_ERROR,
                    Arrays.asList(i18n("invalid_endpoint")));
        }
    }

    private boolean isValidTestResponse(String body) throws PathNotFoundException {
        DocumentContext ctx = JsonPath.parse(body);
        if (ctx.read("type").equals("object")) {
            return true;
        }
        ;
        return false;
    }

    private String getApiUrlPrefix(ConnectorInfo currentConfig) {
        return format(API_PREFIX, currentConfig.getEndpoint());
    }

    // ============================================================================
    // DataService Implementation
    // ============================================================================

    @Override
    /**
     * NOTE: Performance Optimizations Available (Not Yet Implemented)
     *
     * The following optimizations from the old NetSuiteService can be added to improve
     * sync performance for large datasets:
     *
     * 1. Local Storage Caching (Checkpoint Recovery)
     *    - HSQL-based disk caching for resume capability
     *    - Stores fetched records with watermark for checkpoint recovery
     *    - Enables resuming from last position on sync failures
     *    - Reference: NetSuiteService.getSortedFetchResponse() lines 2079-2182
     *
     * 2. Multi-threaded Fetching
     *    - Parallel REST API calls with configurable thread pools (default: 3 threads)
     *    - 3-6x speedup for large record sets
     *    - Thread pool management with global limit (12 threads max)
     *    - Reference: NetSuiteService.getEntityData() lines 2188-2216
     *
     * Detailed implementation plan available in:
     * /Users/richard/.claude/plans/abstract-humming-sparrow.md
     *
     * Estimated implementation time: 16-24 hours (2-3 days)
     */
    public FetchResponse getByWatermark(SyncRequest request) {
        String requestedEntityName = request.getEntityName();

        // Special handling for picklist_values entity
        if (requestedEntityName.equalsIgnoreCase(NetsuiteSeed.PICKLIST_VALUES_ENTITY)) {
            return fetchPicklistValues(request);
        }

        boolean isChildEntity = supportedChildEntities.contains(requestedEntityName);
        EntitySchema schema = request.getEntitySchema();

        if (isChildEntity) {
            return fetchChildEntitiesByWatermark(request, requestedEntityName, schema);
        } else {
            return fetchStandardEntitiesByWatermark(request, schema);
        }
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        // Return hardcoded date (January 1, 1990) to avoid expensive MIN() queries on large tables
        // This matches the approach used in the old NetSuite service
        return ZonedDateTime.of(1990, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        try {
            String requestedEntityName = request.getEntityName();

            // Special handling: picklist_values doesn't support getByIds
            if (requestedEntityName.equalsIgnoreCase(NetsuiteSeed.PICKLIST_VALUES_ENTITY)) {
                throw new NotSupportedException("GetByIds is not supported for " +
                    request.getEntitySchema().getDisplayName() + " (picklist_values)");
            }

            // Note: Saved searches are not supported in SuiteQL-only implementation due to API limitations.
            // Note: subsidiary - Special SOAP+REST hybrid fetch (old service line 2955-2958)
            // paycheckjournal, binworksheet changes not implemented as it is not currently working

            // TODO: Implement entity-specific handling from old service:
            // 4. contact - Enrichment with address details via updateContactWithAddressDetails() (old service line 2980-2982)
            // 5. customer - Enrichment with alt name via updateCustomerWithAltName() (old service line 2983-2985)

            List<EntityData> requestData = request.getData().get(request.getConnector().getId());
            if (requestData == null || requestData.isEmpty()) {
                return List.of();
            }

            boolean isChildEntity = supportedChildEntities.contains(requestedEntityName);

            if (isChildEntity) {
                return getChildEntitiesByIds(request, requestedEntityName, requestData);
            } else {
                return getStandardEntitiesByIds(request, requestedEntityName, requestData);
            }

        } catch (NonRetriableException | RetriableException e) {
            // Re-throw connector exceptions as-is
            log.error("Failed to get records by IDs: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // Wrap unexpected exceptions in NonRetriableException (matches old SOAP service behavior)
            log.error("Failed to get records by IDs", e);
            throw new NonRetriableException(ErrorCodes.API_ERROR.name(),
                "Get by IDs failed: " + e.getMessage(), "GETBYIDS_ERROR");
        }
    }

    /**
     * Fetch child entities by IDs via their parent entities.
     * Child entities (like line items) are embedded in parent records and must be fetched
     * by retrieving parents with expandSubResources=true, then extracting children.
     */
    private List<EntityData> getChildEntitiesByIds(SyncRequest request, String requestedEntityName,
                                                    List<EntityData> requestData) {
        log.debug("Fetching child entity by IDs: {} via parent entity", requestedEntityName);

        String parentEntityName = CHILD_PARENT_ENTITY_MAP.get(requestedEntityName);
        EntitySchema parentSchema = transformSchema(request);
        EntitySchema schema = request.getEntitySchema();

        // Extract parent IDs and requested child IDs in a single pass (optimization)
        ParentChildIds ids = extractParentAndChildIds(requestData);

        if (ids.parentIds.isEmpty()) {
            return List.of();
        }

        // Partition parent IDs into batches to respect Oracle IN clause limit (1000 values)
        List<List<String>> parentIdPartitions = ListUtils.partition(new ArrayList<>(ids.parentIds), GETBYIDS_BATCH_SIZE);
        log.debug("Partitioned {} parent IDs into {} batches for child entity {}",
            ids.parentIds.size(), parentIdPartitions.size(), requestedEntityName);

        List<EntityData> allChildren = new ArrayList<>();

        for (List<String> parentIdBatch : parentIdPartitions) {
            // Fetch parent records with nested children for this batch
            List<EntityData> parentRecords = fetchParentRecordsWithChildren(
                request.getConnector(), parentEntityName, parentIdBatch, parentSchema);

            // Extract child records from this batch of parents
            List<EntityData> batchChildren = extractChildRecords(
                parentSchema, schema, parentRecords, requestedEntityName);

            allChildren.addAll(batchChildren);
        }

        // Filter to only return requested child IDs (using HashSet for O(1) filtering)
        if (!ids.childIds.isEmpty()) {
            return allChildren.stream()
                .filter(child -> ids.childIds.contains(child.getId()))
                .collect(Collectors.toList());
        }

        return allChildren;
    }

    /**
     * Fetch standard entities by IDs using SuiteQL queries.
     *
     * ARCHITECTURAL DECISION: Returns parent entity data only, NO child records
     * - Uses SuiteQL SELECT queries which return flat result sets
     * - Child records (e.g., salesorderlineitems, purchaseorderlineitems) are NOT included
     * - To sync child records, query them separately as independent entities
     * - This differs from old SOAP service which returned nested children in parent records
     */
    private List<EntityData> getStandardEntitiesByIds(SyncRequest request, String requestedEntityName,
                                                       List<EntityData> requestData) {
        // Note: Old SOAP implementation had special enrichment for these entities:
        // - subsidiary: Hybrid SOAP+REST fetch with fetchAllSubsidiary() for additional fields
        // - contact: SOAP enrichment with address details via updateContactWithAddressDetails()
        // - customer: SOAP enrichment with alt name via updateCustomerWithAltName()
        // - paycheckjournal: SOAP-specific converter toPaycheckJournalData()
        // - binworksheet: SOAP-specific converter toBinWorksheetData()
        // These enrichments are not implemented in SuiteQL version as they were SOAP-specific.
        //
        // LIMITATION: SuiteQL SELECT * queries do not return:
        // - Nested child records (salesorderlineitems, purchaseorderlineitems, etc.)
        // - Nested address fields (billingAddress_*, shippingAddress_*) for some entities
        // To get these, would need REST Record API with expandSubResources=true (not currently used here).

        EntitySchema schema = request.getEntitySchema();

        List<String> ids = requestData.stream()
            .map(EntityData::getId)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());

        if (ids.isEmpty()) {
            return List.of();
        }

        // Partition IDs into batches to respect Oracle IN clause limit (1000 values)
        List<List<String>> idPartitions = ListUtils.partition(ids, GETBYIDS_BATCH_SIZE);
        log.debug("Partitioned {} IDs into {} batches for entity {}",
            ids.size(), idPartitions.size(), requestedEntityName);

        List<EntityData> allResults = new ArrayList<>();

        // TODO: Implement NO_WM_ENTITIES special handling (from old NetSuiteService line 2970)
        // Some entities don't support watermark queries and need synthetic SearchResults
        // instead of making actual API calls. Check if requestedEntityName is in NO_WM_ENTITIES
        // and handle accordingly.

        // Check if entity needs table name mapping (e.g., assemblybuild -> transaction table)
        ItemQueryInfo queryInfo = ITEM_ENTITY_TO_SUITEQL_MAP.get(requestedEntityName.toLowerCase());
        String actualTableName = queryInfo != null ? queryInfo.getTableName() : schema.getApiName();
        String additionalWhereClause = queryInfo != null ? queryInfo.getWhereClause() : null;

        for (List<String> idBatch : idPartitions) {
            String idList = idBatch.stream()
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(","));

            // Build query with table mapping and additional where clause if needed
            String query;
            if (additionalWhereClause != null) {
                query = String.format("SELECT * FROM %s WHERE id IN (%s) AND %s",
                    actualTableName, idList, additionalWhereClause);
            } else {
                query = String.format("SELECT * FROM %s WHERE id IN (%s)",
                    actualTableName, idList);
            }

            List<EntityData> batchResults = executeSuiteQLQuery(
                request.getConnector(), query, schema, idBatch.size());

            allResults.addAll(batchResults);
        }

        return allResults;
    }

    /**
     * Extract parent and child IDs from request data in a single pass.
     * Optimization: Instead of iterating twice, we collect both sets of IDs in one loop.
     */
    private ParentChildIds extractParentAndChildIds(List<EntityData> requestData) {
        Set<String> parentIds = new LinkedHashSet<>();  // Maintains insertion order + deduplication
        Set<String> childIds = new HashSet<>();  // For O(1) filtering later

        for (EntityData ed : requestData) {
            // Extract requested child ID
            String childId = ed.getId();
            if (StringUtils.isNotBlank(childId)) {
                childIds.add(childId);
            }

            // Extract parent ID from parentId field or composite ID
            String parentId;
            if (StringUtils.isNotBlank(ed.getParentId())) {
                parentId = ed.getParentId();
            } else if (StringUtils.isNotBlank(childId) && childId.contains("#")) {
                // Extract parent from composite ID: "502#1" → "502"
                parentId = childId.split("#")[0];
            } else {
                parentId = childId;
            }

            if (StringUtils.isNotBlank(parentId)) {
                parentIds.add(parentId);
            }
        }

        return new ParentChildIds(parentIds, childIds);
    }

    /**
     * Helper class to hold parent and child IDs extracted from request data.
     */
    private static class ParentChildIds {
        final Set<String> parentIds;
        final Set<String> childIds;

        ParentChildIds(Set<String> parentIds, Set<String> childIds) {
            this.parentIds = parentIds;
            this.childIds = childIds;
        }
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        SyncResponse response = new SyncResponse(true);
        List<Result> results = new ArrayList<>();

        // TODO: Missing features from old NetSuiteService.insert() and related methods:
        // 1. Opportunity contacts special handling (old service lines 3334, 3351, 3836-3838)
        //    - Remove "contacts" field before create, then attach via SOAP using attachContactsToOppties()
        //    - Requires SOAP API integration
        // 2. Contact entity special routing (old service lines 3002-3003, 3024-3026)
        //    - Currently just delegates to insert(), may need special handling in future

        try {
            NetSuiteRestClient client = getNetSuiteRestClient();

            EntitySchema schema = request.getEntitySchema();
            List<EntityData> dataList = request.getData().get(request.getConnector().getId());

            if (dataList == null || dataList.isEmpty()) {
                response.setResults(results);
                return response;
            }

            for (EntityData data : dataList) {
                try {
                    Result result = createSingleRecord(request, client, data, schema);
                    results.add(result);

                    // Add child record results (composite IDs like "502#1", "502#2")
                    // Matches old NetSuiteService.addLineItemResults (lines 3368-3375)
                    addChildRecordResults(result, data);

                } catch (NonRetriableException ex) {
                    log.error("Create failed for record (non-retriable)", ex);
                    results.add(new Result(false, null, data.getSyncariEntityId())
                            .addError(ex.getMessage()));
                } catch (Exception e) {
                    log.error("Create failed for record", e);
                    results.add(new Result(false, data.getId(), data.getSyncariEntityId())
                            .addError(e.getMessage()));
                }
            }

            response.setResults(results);
        } catch (Exception e) {
            log.error("Create operation failed", e);
            response.setSuccess(false);
            response.setErrors(List.of(e.getMessage()));
        }
        return response;
    }

    /**
     * Create a single record with externalId duplicate prevention and fetch-by-externalId fallback
     * Matches old NetSuiteService.insertSingleRecord (lines 3028-3089)
     */
    private Result createSingleRecord(SyncRequest request, NetSuiteRestClient client,
                                      EntityData data, EntitySchema schema) {
        try {
            Map<String, Object> payload = entityDataToPayload(data, schema);

            // Add syncariEntityId as externalId for duplicate prevention on retry
            // Matches old NetSuiteService lines 3036-3038
            if (!payload.containsKey("externalid") && StringUtils.isNotBlank(data.getSyncariEntityId())) {
                payload.put("externalid", data.getSyncariEntityId());
            }

            String url = String.format(RECORD_URL,
                    request.getConnector().getAuthConfig().getEndpoint(),
                    VERSION,
                    schema.getApiName());

            String body;
            try {
                body = mapper.writeValueAsString(payload);
                log.debug("Creating {} with payload: {}", schema.getApiName(), body);
            } catch (Exception e) {
                log.error("Failed to serialize payload", e);
                throw new RuntimeException("Failed to serialize payload: " + e.getMessage());
            }

            ResponseEntity<String> response = client.postRaw(url, body, request.getConnector().getAuthConfig());

            // Log response for debugging
            log.debug("Create response status: {}, body: {}", response.getStatusCode(), response.getBody());

            if (response.getStatusCode() == HttpStatus.CREATED ||
                    response.getStatusCode() == HttpStatus.OK ||
                    response.getStatusCode() == HttpStatus.NO_CONTENT) {
                log.debug("Create successful with status {}", response.getStatusCode());

                // For 204 NO_CONTENT, we need to extract ID from the Location header or externalId
                if (response.getStatusCode() == HttpStatus.NO_CONTENT) {
                    String newId = extractIdFromLocationHeader(response);
                    if (newId == null && StringUtils.isNotBlank(data.getSyncariEntityId())) {
                        // Use externalId as fallback - we'll need to query by externalId to get the actual ID
                        newId = data.getSyncariEntityId();
                    }
                    return new Result(true, newId, data.getSyncariEntityId());
                }

                JsonNode responseNode;
                try {
                    responseNode = mapper.readTree(response.getBody());
                } catch (Exception e) {
                    log.error("Failed to parse response", e);
                    throw new RuntimeException("Failed to parse response: " + e.getMessage());
                }
                String newId = extractIdFromResponse(responseNode);

                // TODO: Implement legacy tax field updates if needed (see old NetSuiteService lines 3056-3063)

                return new Result(true, newId, data.getSyncariEntityId());
            } else {
                log.error("Create failed with status {}, body: {}", response.getStatusCode(), response.getBody());
                return new Result(false, data.getId(), data.getSyncariEntityId())
                        .addError(response.getBody());
            }

        } catch (NonRetriableException ex) {
            // If record already exists with this externalId, fetch the existing record by externalId
            // Matches old NetSuiteService lines 3066-3087
            String externalId = data.getValueAsString("externalId");
            if (StringUtils.isBlank(externalId)) {
                externalId = data.getSyncariEntityId();
            }

            if (ErrorCodes.BAD_REQUEST.name().equals(ex.getErrorCode())
                    && StringUtils.isNotBlank(ex.getMessage())
                    && ex.getMessage().contains("This record already exists")
                    && StringUtils.isNotBlank(externalId)) {

                log.debug("Record already exists with externalId {}, fetching existing record", externalId);
                try {
                    String recordGetUrl = String.format(SINGLE_RECORD_URL,
                            request.getConnector().getAuthConfig().getEndpoint(),
                            VERSION,
                            schema.getApiName(),
                            "eid:" + externalId);

                    ResponseEntity<String> getResponse = client.getResponse(recordGetUrl,
                            request.getConnector().getAuthConfig());
                    client.checkResponse(getResponse);

                    String body = getResponse.getBody();
                    DocumentContext ctx = JsonPath.parse(body);
                    String id = ctx.read("id").toString();

                    log.debug("Found existing record with id {} for externalId {}", id, externalId);
                    return new Result(true, id, data.getSyncariEntityId());
                } catch (Exception e) {
                    log.error("Failed to fetch existing record by externalId {}", externalId, e);
                    throw ex; // Re-throw original exception
                }
            } else {
                throw ex; // Re-throw if not a duplicate error
            }
        }
    }

    /**
     * Add child record results with composite IDs (e.g., "502#1", "502#2")
     * Matches old NetSuiteService.addLineItemResults (lines 3368-3375)
     */
    private void addChildRecordResults(Result result, EntityData parentRecord) {
        if (!result.isSuccess()) {
            return; // Only add child results for successful parent creation
        }

        String parentId = result.getId();
        String parentEntityName = parentRecord.getName();

        // Skip if entity name is null or entity doesn't have child records
        if (parentEntityName == null || !CHILD_API_NAMES.containsKey(parentEntityName)) {
            return;
        }

        // For each supported child entity type, create composite IDs
        CHILD_API_NAMES.get(parentEntityName).keySet().forEach(childApiName -> {
            List<EntityData> childRecords = parentRecord.getChildrenRecords(childApiName);
            if (childRecords != null && !childRecords.isEmpty()) {
                for (int i = 0; i < childRecords.size(); i++) {
                    // Line items start at index 1 and are in insertion order
                    // Composite ID format: "parentId#childIndex"
                    String compositeId = parentId + "#" + (i + 1);
                    result.addChildResult(childApiName,
                            new Result(true, compositeId, childRecords.get(i).getSyncariEntityId()));
                }
            }
        });
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        SyncResponse response = new SyncResponse(true);
        List<Result> results = new ArrayList<>();

        // TODO: Missing features from old NetSuiteService.update() (lines 3908-3975):
        //
        // 1. REPLACE_SUBLIST parameter support (CRITICAL - old service lines 3913, 3919-3950)
        //    - Parse request.getTypedDestParam(REPLACE_SUBLIST) for comma-separated sublist names
        //    - Build replaceSublistSet from:
        //      a) REPLACE_FOR_ENTITY list (e.g., salesorder always replaces "item")
        //      b) Custom sublists from destination parameter
        //    - Filter to only sublists present in payload: availableReplacements
        //    - Add ?replace=field1,field2 query parameter to update URL
        //    - Example: /record/v1/salesorder/123?replace=item
        //    - This enables NetSuite sublist replacement mode (replace all items, not merge)
        //
        // 2. Customer address update special handling (CRITICAL - old service lines 3977-3999)
        //    - Call updateAddressFields() before update for customer entity
        //    - Fetches existing customer to get billingAddress_id and shippingAddress_id
        //    - Handles shared address case: if billing and shipping use same address ID
        //    - Merges incoming changes with defaultBilling=true, defaultShipping=true
        //    - Prevents duplicate address creation
        //
        // 3. Journal entry update skip (old service lines 3946-3947)
        //    - Skip PATCH call if entity is journalEntry
        //    - Old service has TODO: "Skip journal entry updates for now"
        //    - May need to investigate if journalEntry updates are now supported
        //
        // 4. Opportunity contacts SOAP handling (old service lines 3954-3956)
        //    - Remove "contacts" field from payload for opportunity entity
        //    - Call attachContactsToOppties() to update via SOAP
        //    - Requires SOAP API integration
        //
        // 5. Error code mapping for "record does not exist" (old service lines 3960-3967)
        //    - Catch NonRetriableException and check for "The record instance does not exist."
        //    - Set result.setErrorCode(ErrorCodes.DATA_NOT_FOUND.name())
        //    - Enables downstream systems to handle missing records properly
        //
        // 6. Success tracking behavior (old service line 3918, 3969, 3972)
        //    - Use AtomicBoolean isSuccess to track if ANY record fails
        //    - response.setSuccess(isSuccess.get()) at end
        //    - Current implementation only sets false on outer exception

        try {
            NetSuiteRestClient client = getNetSuiteRestClient();

            EntitySchema schema = request.getEntitySchema();
            List<EntityData> dataList = request.getData().get(request.getConnector().getId());

            if (dataList == null || dataList.isEmpty()) {
                response.setResults(results);
                return response;
            }

            for (EntityData data : dataList) {
                try {
                    if (StringUtils.isBlank(data.getId())) {
                        results.add(new Result(false, data.getId(), data.getSyncariEntityId())
                                .addError("ID is required for update"));
                        continue;
                    }

                    Map<String, Object> payload = entityDataToPayload(data, schema);

                    // Skip update if payload is empty (matches old connector behavior)
                    if (payload.isEmpty()) {
                        log.info("Skipping update to record {} on object {} because no nonnull values found",
                                data.getId(), schema.getApiName());
                        results.add(new Result(true, data.getId(), data.getSyncariEntityId()));
                    } else {
                        String url = String.format(SINGLE_RECORD_URL,
                                request.getConnector().getAuthConfig().getEndpoint(),
                                VERSION,
                                schema.getApiName(),
                                data.getId());

                        String body = mapper.writeValueAsString(payload);

                        ResponseEntity<String> result = client.patchRaw(url, body, request.getConnector().getAuthConfig());

                        if (result.getStatusCode() == HttpStatus.OK ||
                            result.getStatusCode() == HttpStatus.NO_CONTENT) {
                            results.add(new Result(true, data.getId(), data.getSyncariEntityId()));
                        } else {
                            results.add(new Result(false, data.getId(), data.getSyncariEntityId())
                                    .addError(result.getBody()));
                        }
                    }
                } catch (org.springframework.web.client.HttpClientErrorException e) {
                    String responseBody = e.getResponseBodyAsString();
                    log.error("Update failed for record - Status: {}, Response: {}", e.getStatusCode(), responseBody, e);
                    results.add(new Result(false, data.getId(), data.getSyncariEntityId())
                            .addError(responseBody != null && !responseBody.isEmpty() ? responseBody : e.getMessage()));
                } catch (Exception e) {
                    log.error("Update failed for record", e);
                    results.add(new Result(false, data.getId(), data.getSyncariEntityId())
                            .addError(e.getMessage()));
                }
            }

            response.setResults(results);
        } catch (Exception e) {
            log.error("Update operation failed", e);
            response.setSuccess(false);
            response.setErrors(List.of(e.getMessage()));
        }
        return response;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        ConnectorInfo connector = request.getConnector();
        AuthConfig auth = connector.getAuthConfig();
        List<EntityData> toBeDeleted = request.getData().get(request.getConnector().getId());
        SyncResponse response = new SyncResponse();
        List<Result> results = new ArrayList<>();
        toBeDeleted.forEach((delete) -> {
            String id = delete.getId();
            String url = String.format(SINGLE_RECORD_URL, connector.getEndpoint(), VERSION, delete.getName(), id);
            try {
                log.debug("Deleting record - Entity: {}, ID: {}, URL: {}", delete.getName(), id, url);
                NetSuiteRestClient restClient = getNetSuiteRestClient();
                restClient.delete(url, auth);
                log.debug("Successfully deleted record - Entity: {}, ID: {}", delete.getName(), id);
                results.add(new Result(true, id, delete.getSyncariEntityId()));
            } catch (Exception e) {
                log.error("Failed to delete record - Entity: {}, ID: {}, Error: {}",
                    delete.getName(), id, e.getMessage(), e);
                Result errorResult = new Result(false, id, delete.getSyncariEntityId());
                errorResult.addError("Failed to delete: " + e.getMessage());
                results.add(errorResult);
            }
        });

        // Set overall success based on whether all individual deletions succeeded
        boolean allSucceeded = results.stream().allMatch(Result::isSuccess);
        response.setSuccess(allSucceeded);
        response.setResults(results);
        return response;
    }

    @Override
    public List<EntityData> search(SearchRequest request) {
        log.debug(request.getQuery());
        String requestQuery = request.getQuery();
        if (StringUtils.isBlank(request.getQuery())) return List.of();
        if (!CollectionUtils.isEmpty(request.getParams()) && request.getQuery().contains("?")) {
            int placeholderCount = StringUtils.countMatches(request.getQuery(), "?");
            if (placeholderCount != request.getParams().size()) {
                log.debug("invalid query {} and params", requestQuery);
                return List.of();
            }
            for (Object param : request.getParams()) {
                // escape special characters first
                String escapedParam = escape(param.toString());
                requestQuery = requestQuery.replaceFirst("\\?", escapedParam);
            }
        }
        String[] parts = StringUtils.normalizeSpace(requestQuery).split(" ");
        if (parts.length < 4) {
            log.error("Not able to identify entity for query : " + requestQuery);
            return List.of();
        }

        // Find entity name after FROM keyword
        // Query format: SELECT <columns> FROM <entity> WHERE <conditions>
        String entityName = null;
        for (int i = 0; i < parts.length - 1; i++) {
            if ("FROM".equalsIgnoreCase(parts[i])) {
                entityName = parts[i + 1];
                break;
            }
        }

        if (entityName == null) {
            log.error("Not able to identify entity (no FROM clause) for query : " + requestQuery);
            return List.of();
        }

        final String entity = entityName;

        // Implement pagination loop like old NetSuite SOAP synapse
        // Fetches all pages of results and accumulates them
        boolean hasMore;
        int offset = 0;
        int limit = MAX_RECORDS_PER_PAGE_FOR_QUERY_API;
        List<EntityData> allItems = new ArrayList<>();

        do {
            List<EntityData> pageItems = executeSuiteQLQueryWithOffset(
                request.getConnector(),
                requestQuery,
                null,
                limit,
                offset
            );

            // Set entity name on each returned item (since schema is null, mapToEntityData can't set it)
            pageItems.forEach(item -> item.setName(entity));

            allItems.addAll(pageItems);

            // If we got fewer items than the limit, we've reached the last page
            hasMore = pageItems.size() >= limit;
            offset += limit;

        } while (hasMore);

        log.info("Fetched {} records", allItems.size());
        return allItems;
    }

    // ============================================================================
    // MetadataService Implementation
    // ============================================================================

    /**
     * NOTE: Missing Features Compared to Old SOAP-based NetSuiteService
     *
     * The following entities/features from the old NetSuiteService are NOT supported
     * in this SuiteQL-based implementation:
     *
     * 1. TransactionLine Entity (Architectural Limitation)
     *    - The 'transactionline' entity is in UNSUPPORTED_SUITEQL_ENTITIES
     *    - Doesn't have lastModifiedDate field in SuiteQL
     *    - Must be queried via JOIN with transaction table, not standalone
     *    - Attempting to sync will fail with "Unknown identifier 'lastModifiedDate'" error
     *    - Old service: NetSuiteService.describe() lines 835-838
     *
     * 2. File Entity (Not Implemented)
     *    - The 'file' entity with attachFrom, folder, fileType fields not supported
     *    - Old service included full schema with SYNCARI_FILE_LINK_FIELD_NAME
     *    - Old service: NetSuiteService.describe() lines 863-879
     *
     * 3. Parent Entity Schemas Don't Expose Child Relationships
     *    - Parent entities (salesorder, purchaseorder, invoice, etc.) don't have child entity
     *      fields in their schemas (e.g., salesorder schema doesn't have "salesorderlineitems" field)
     *    - Child records must be synced as separate independent entities
     *    - This is an architectural decision documented in:
     *      /Users/richard/syncari/backend/connector/NETSUITE_SUITEQL_ARCHITECTURE.md
     *    - Old service: NetSuiteService.describe() lines 958-1024 (adds child fields to parent schemas)
     *
     * Other entity-specific fields that may be missing:
     * - Opportunity: 'contacts' multi-value reference field
     * - Contact: addr1, addr2, addr3, city, state, zip, country fields
     * - Transaction entities: SYNCARI_FILE_REFERENCE_FIELD_NAME for file attachments
     */
    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        // Check for special entities first (matches old connector behavior)
        // These entities don't need custom record discovery

        if (request.getEntity().equalsIgnoreCase(NetsuiteSeed.PICKLIST_VALUES_ENTITY)) {
            EntitySchema entity = NetsuiteSeed.getPicklistEntitySchema();
            return Optional.of(entity);
        }

        // Run discovery for custom record types and field references (like old NetSuite connector)
        // This ensures custom record display names and field references are properly resolved
        BiMap<String, String> customRecordTypeEntities = populateCustomRecordTypeEntities(request.getConnector());
        Map<String, Reference> customFieldReferenceMap = buildCustomFieldReferences(request.getConnector(), customRecordTypeEntities.inverse());

        return describe(request, customRecordTypeEntities, customFieldReferenceMap);
    }

    /**
     * Describe entity with pre-computed custom record types and field references.
     * This is used by describeAll() to avoid running discovery for each entity.
     *
     * @param request The describe request
     * @param customRecordTypeEntities BiMap of custom record type API names to display names
     * @param customFieldReferenceMap Map of custom field API names to Reference objects
     * @return Optional EntitySchema
     */
    private Optional<EntitySchema> describe(DescribeRequest request,
                                            BiMap<String, String> customRecordTypeEntities,
                                            Map<String, Reference> customFieldReferenceMap) {
        try {
            String entityName = request.getEntity().toLowerCase();

            // Check for special entities first (must be before child entity check)
            // Note: transactionline is NOT supported (see public describe method comment)

            if (entityName.equalsIgnoreCase(NetsuiteSeed.PICKLIST_VALUES_ENTITY)) {
                EntitySchema entity = NetsuiteSeed.getPicklistEntitySchema();
                return Optional.of(entity);
            }

            // Define replaceSublist attribute for entities that support it
            final AttributeSchema replaceSublistAttribute = new AttributeSchema(REPLACE_SUBLIST, "string")
                    .setDisplayName("Replace sublists")
                    .setDescription("Replaces specified sublist fields during updates. You can add multiple fields separated by commas. The \"items\" sublist on salesorders are always replaced, regardless of this setting.");

            // Check for special entities (paycheckjournal, binworksheet)
            if (entityName.equalsIgnoreCase("paycheckjournal")) {
                EntitySchema entity = new EntitySchema(request.getEntity(), StringUtils.capitalize(request.getEntity()));
                entity.addDestinationParam(replaceSublistAttribute);
                entity.addField(new AttributeSchema("account", "string").setDisplayName("Account"));
                entity.addField(new AttributeSchema("class", "string").setDisplayName("Class"));
                entity.addField(new AttributeSchema("companyContributionList", "string").setDisplayName("Company Contribution List"));
                entity.addField(new AttributeSchema("companyTaxList", "string").setDisplayName("Company Tax List"));
                entity.addField(new AttributeSchema("createdDate", "datetime").setDisplayName("Created Date"));
                entity.addField(new AttributeSchema("customFieldList", "string").setDisplayName("Custom Field List"));
                entity.addField(new AttributeSchema("customForm", "string").setDisplayName("Custom Form"));
                entity.addField(new AttributeSchema("deductionList", "string").setDisplayName("Deduction List"));
                entity.addField(new AttributeSchema("department", "string").setDisplayName("Department"));
                entity.addField(new AttributeSchema("earningList", "string").setDisplayName("Earning List"));
                entity.addField(new AttributeSchema("employee", "string").setDisplayName("Employee"));
                entity.addField(new AttributeSchema("employeeTaxList", "string").setDisplayName("Employee Tax List"));
                entity.addField(new AttributeSchema("exchangeRate", "double").setDisplayName("Exchange Rate"));
                entity.addField(new AttributeSchema("location", "string").setDisplayName("Location"));
                entity.addField(new AttributeSchema("postingPeriod", "string").setDisplayName("Posting Period"));
                entity.addField(new AttributeSchema("subsidiary", "string").setDisplayName("Subsidiary"));
                entity.addField(new AttributeSchema("tranDate", "datetime").setDisplayName("Date"));
                entity.addField(new AttributeSchema("tranId", "string").setDisplayName("Entry No"));
                entity.addField(new AttributeSchema("internalid", "string").setDisplayName("Id").setIdField(true));
                entity.addField(new AttributeSchema("lastModifiedDate", "datetime").setDisplayName("Last Modified Date").setWatermarkField(true));
                entity.setReadOnly(true);
                return Optional.of(entity);
            }
            if (entityName.equalsIgnoreCase("binworksheet")) {
                EntitySchema entity = new EntitySchema(request.getEntity(), StringUtils.capitalize(request.getEntity()));
                entity.addDestinationParam(replaceSublistAttribute);
                entity.addField(new AttributeSchema("createdDate", "datetime").setDisplayName("Created Date"));
                entity.addField(new AttributeSchema("customFieldList", "string").setDisplayName("Custom Field List"));
                entity.addField(new AttributeSchema("itemList", "string").setDisplayName("Item List"));
                entity.addField(new AttributeSchema("lastModifiedDate", "datetime").setDisplayName("Last Modified Date").setWatermarkField(true));
                entity.addField(new AttributeSchema("location", "string").setDisplayName("Location"));
                entity.addField(new AttributeSchema("memo", "string").setDisplayName("Memo"));
                entity.addField(new AttributeSchema("tranDate", "datetime").setDisplayName("Date"));
                entity.addField(new AttributeSchema("tranId", "string").setDisplayName("Bin Worksheet No"));
                entity.addField(new AttributeSchema("internalid", "string").setDisplayName("Id").setIdField(true));
                entity.setReadOnly(true);
                return Optional.of(entity);
            }

            // Line item entities now use fully dynamic discovery (handled by supportedChildEntities check below)

            // Check if it's a child entity FIRST (before checking standard entities)
            if (supportedChildEntities.contains(entityName)) {
                log.debug("Describing child entity: {}", entityName);
                EntitySchema childSchema = describeChildEntity(request, customFieldReferenceMap);
                return Optional.of(childSchema);
            }

            // Check if it's a supported standard entity
            if (NetsuiteSuiteQLSeed.supportedEntitiesBiMap.containsKey(entityName)) {
                return Optional.of(describeStandardEntity(request, customFieldReferenceMap));
            }

            // Check if it's a custom record (starts with customrecord_)
            if (entityName.startsWith("customrecord_")) {
                return Optional.of(describeCustomRecord(request, customRecordTypeEntities, customFieldReferenceMap));
            }

            return Optional.empty();
        } catch (Exception e) {
            log.error("Describe failed for entity: " + request.getEntity(), e);
            return Optional.empty();
        }
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of(Constants.ACCOUNT.toLowerCase(), "customer", Constants.CONTACT.toLowerCase(), Constants.CONTACT.toLowerCase(),
                Constants.OPPORTUNITY.toLowerCase(), Constants.OPPORTUNITY.toLowerCase(), Constants.DOCUMENT.toLowerCase(), "file");
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> schemas = new ArrayList<>();

        // Run discovery once at the beginning (like old NetSuite implementation)
        log.debug("Running custom record type and field reference discovery for schema refresh");
        BiMap<String, String> customRecordTypeEntities = populateCustomRecordTypeEntities(request.getConnector());
        Map<String, Reference> customFieldReferenceMap = buildCustomFieldReferences(request.getConnector(), customRecordTypeEntities.inverse());

        // Add requested standard entities
        if (request.getEntities() != null && !request.getEntities().isEmpty()) {
            for (String entityName : request.getEntities()) {
                Optional<EntitySchema> schema = describe(new DescribeRequest(request.getConnector(), entityName),
                                                          customRecordTypeEntities, customFieldReferenceMap);
                schema.ifPresent(schemas::add);
            }
        } else {

            // 1. Add all standard entities (skip SuiteQL-only entities that don't exist in REST API)
            NetsuiteSuiteQLSeed.supportedEntitiesMap.forEach((key, value) -> {
                if (!UNSUPPORTED_SUITEQL_ENTITIES.contains(key)) {
                    describe(new DescribeRequest(request.getConnector(), key),
                             customRecordTypeEntities, customFieldReferenceMap)
                            .ifPresent(schemas::add);
                } else {
                    log.debug("Skipping SuiteQL-only entity: {} (not available in REST API)", key);
                }
            });

            // 2. Add all child entities (line items)
            NetsuiteSuiteQLSeed.supportedChildEntitiesBiMap.forEach((key, value) -> {
                describe(new DescribeRequest(request.getConnector(), key),
                         customRecordTypeEntities, customFieldReferenceMap)
                        .ifPresent(schemas::add);
            });

            // 3. Add all custom record types (discovered dynamically)
            customRecordTypeEntities.forEach((customRecordApiName, displayName) -> {
                log.debug("Describing custom record type: {}", customRecordApiName);
                Optional<EntitySchema> schema = describe(new DescribeRequest(request.getConnector(), customRecordApiName),
                                                         customRecordTypeEntities, customFieldReferenceMap);
                if (schema.isPresent()) {
                    schemas.add(schema.get());
                } else {
                    log.warn("Failed to describe custom record type: {} ({})", customRecordApiName, displayName);
                }
            });

            // 4. Add special entities (Picklist only - transactionline is not supported)
            // Note: transactionline doesn't have lastModifiedDate field and must be queried via JOIN
            schemas.add(NetsuiteSeed.getPicklistEntitySchema());
            log.debug("Added special entity: picklistValues");

            log.debug("Loaded total {} entities: {} standard, {} child, {} custom records, 1 special",
                    schemas.size(),
                    NetsuiteSuiteQLSeed.supportedEntitiesMap.size(),
                    NetsuiteSuiteQLSeed.supportedChildEntitiesBiMap.size(),
                    customRecordTypeEntities.size());
        }

        return schemas;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        return null;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        return null;
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {

    }

    // ============================================================================
    // SynapseInfoService Implementation
    // ============================================================================

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getTokenBasedOAuthType());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        // NetSuite Account URL field
        AuthField endpointField = ConnectorHelper.getEndpointField();
        endpointField.setHelpSummary("Your NetSuite account URL (e.g., https://1234567.suitetalk.api.netsuite.com)");
        endpointField.setRequired(true);

        // Add timezone configuration field
        AuthField timeZone = new AuthField();
        timeZone.setDataType("text");
        timeZone.setName(TIMEZONE_ID);
        timeZone.setLabel("Time Zone");
        timeZone.setHelpSummary("NetSuite account timezone (e.g., America/Los_Angeles, America/New_York, UTC). " +
                "Must match the timezone configured in NetSuite: Setup → Company → Company Information → Time Zone");
        timeZone.setRequired(false);

        // Authentication method picker - the actual OAuth fields (consumerKey, consumerSecret, tokenId, tokenSecret)
        // are defined in getSupportedAuthTypes() and will be shown when user selects the auth method
        return List.of(endpointField, timeZone, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public boolean validateEntityConfig(EntityParams params) {
        switch (params.getSchema().getApiName()) {
            case NetsuiteSeed.PICKLIST_VALUES_ENTITY:
                String picklistParamString = Objects.toString(params.getSourceParam("picklistParams"), null);
                if (StringUtils.isEmpty(picklistParamString)) {
                    throw new NonRetriableException(ErrorCodes.BAD_REQUEST, "At least one valid picklist parameter is required", "BAD_REQUEST");
                }
                final List<Pair<String, String>> picklistParams = getPicklistParams(picklistParamString);
                if (picklistParams == null || picklistParams.isEmpty()) {
                    throw new NonRetriableException(ErrorCodes.BAD_REQUEST, "At least one valid picklist parameter is required", "BAD_REQUEST");
                }
        }
        return true;
    }

    private static List<Pair<String, String>> getPicklistParams(String picklistParams) {
        try {
            //sorting is important for watermark management
            return Arrays.stream(picklistParams.split(",")).sorted().map(p -> p.trim().split("\\."))
                    .map(s -> Pair.of(s[0], s[1])).collect(Collectors.toList());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new NonRetriableException(ErrorCodes.BAD_REQUEST, "Cannot parse picklist parameters '" + picklistParams + "'. Please follow the format entityName.apiName", "BAD_REQUEST");
        }
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return NetsuiteSuiteQLSeed.getAttributeMappings(entityApiName);
    }

    @Override
    public String getName() {
        return Constants.NETSUITE_SUITEQL;
    }

    @Override
    public String getCategory() {
        return "CRM";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata()
            .setDisplayName("NetSuite SuiteQL")
            .setIconPath("/assets/icons/logos/netsuite.svg")
            .setBackgroundColor("#F2F9FF")
            .setHelpUrl(helpArticlesBaseUrl + "/44504571577876-Netsuite-SuiteQL");
    }

    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<Capability>();
        capabilities.add(Capability.schemaEditInSyncari);
        capabilities.add(Capability.schemaCreateField);
        capabilities.add(Capability.userEditableId);
        capabilities.add(Capability.update);
        capabilities.add(Capability.search);
        return capabilities;
    }

    //TODO
    @Override
    public String getCapabilitiesArticleId() {
        return "44973525761684";
    }

    @Override
    public SyncariEntityDataRestClient getRestClient() {
        var restClient = getNetSuiteRestClient();
        restClient.addHeader(CONTENT_TYPE, APPLICATION_JSON);
        return restClient;
    }

    @Override
    public SyncariEntityDataRestClient getRestClient(ProxyConfig proxy) {
        var restClient = getNetSuiteRestClient(proxy);
        restClient.addHeader(CONTENT_TYPE, APPLICATION_JSON);
        return restClient;
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Create NetSuiteRestClient for making API calls
     */
    protected NetSuiteRestClient getNetSuiteRestClient(ProxyConfig proxy) {
        return new NetSuiteRestClient(getTemplate(Optional.ofNullable(proxy)), mapper, proxy);
    }

    protected NetSuiteRestClient getNetSuiteRestClient() {
        return new NetSuiteRestClient(getTemplate(Optional.empty()), mapper);
    }

    private org.springframework.web.client.RestTemplate getTemplate(Optional<ProxyConfig> proxy) {
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        if (proxy.isPresent()) {
            ProxyConfig proxyConfig = proxy.get();
            if (StringUtils.isNotEmpty(proxyConfig.getHost())) {
                HttpHost httpProxy = new HttpHost(proxyConfig.getHost(), proxyConfig.getPort());
                httpClientBuilder.setProxy(httpProxy);
                log.debug("Setting proxy with {} {}", proxyConfig.getHost(), proxyConfig.getPort());
                CloseableHttpClient client = httpClientBuilder.build();
                clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(client);
            }
        }
        clientHttpRequestFactory.setConnectTimeout(WAIT_TIMEOUT_MILLIS);
        clientHttpRequestFactory.setReadTimeout(WAIT_TIMEOUT_MILLIS);
        return new org.springframework.web.client.RestTemplate(clientHttpRequestFactory);
    }

    /**
     * Execute SuiteQL query and return results
     */
    private List<EntityData> executeSuiteQLQuery(ConnectorInfo connectorInfo, String query, EntitySchema schema, int limit) {
        try {
            NetSuiteRestClient client = getNetSuiteRestClient();

            // Add required Prefer header for SuiteQL
            addSuiteQLHeaders(connectorInfo.getAuthConfig());

            // Build URL with limit and offset as query parameters (NetSuite SuiteQL requirement)
            String baseUrl = String.format(SUITE_QUERY_URL,
                    connectorInfo.getAuthConfig().getEndpoint(),
                    VERSION);
            String url = baseUrl + "?limit=" + limit + "&offset=0";

            // Request body contains only the query
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("q", query);

            String body = mapper.writeValueAsString(requestBody);
            log.debug("Executing SuiteQL query with limit {}: {}", limit, query);
            ResponseEntity<String> response = client.postRaw(url, body, connectorInfo.getAuthConfig());

            if (response.getStatusCode() != HttpStatus.OK) {
                String errorMsg = String.format("SuiteQL query failed with status: %s for query: %s",
                    response.getStatusCode(), query);
                log.error(errorMsg);
                throw new NonRetriableException(ErrorCodes.API_ERROR.name(), errorMsg,
                    response.getStatusCode().toString());
            }

            SuiteQLResponse suiteQLResponse = mapper.readValue(response.getBody(), SuiteQLResponse.class);

            if (suiteQLResponse.getItems() == null) {
                log.debug("Query returned null items for query: {}", query);
                return List.of();
            }

            List<EntityData> results = suiteQLResponse.getItems().stream()
                    .map(item -> mapToEntityData(item, schema, connectorInfo))
                    .collect(Collectors.toList());

            log.debug("Query returned {} records (totalResults: {}, hasMore: {}) for query: {}",
                    results.size(), suiteQLResponse.getTotalResults(), suiteQLResponse.isHasMore(), query);

            return results;

        } catch (JsonProcessingException e) {
            String errorMsg = "JSON processing error while executing SuiteQL query: " + query;
            log.error(errorMsg, e);
            throw new NonRetriableException(ErrorCodes.API_ERROR.name(), errorMsg + ": " + e.getMessage(), "JSON_ERROR");
        } catch (NonRetriableException | RetriableException e) {
            // Re-throw connector exceptions as-is (from client.postRaw)
            log.error("Error executing SuiteQL query: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // Wrap unexpected exceptions in NonRetriableException (matches old SOAP service behavior)
            String errorMsg = "Unexpected error executing SuiteQL query: " + query;
            log.error(errorMsg, e);
            throw new NonRetriableException(ErrorCodes.API_ERROR.name(), errorMsg + ": " + e.getMessage(), "UNEXPECTED_ERROR");
        }
    }

    /**
     * Execute SuiteQL query with pagination support
     */
    private List<EntityData> executeSuiteQLQueryWithOffset(ConnectorInfo connectorInfo, String query, EntitySchema schema, int limit, int offset) {
        try {
            NetSuiteRestClient client = getNetSuiteRestClient();

            // Add required Prefer header for SuiteQL
            addSuiteQLHeaders(connectorInfo.getAuthConfig());

            // Build URL with limit and offset as query parameters (NetSuite SuiteQL requirement)
            String baseUrl = String.format(SUITE_QUERY_URL,
                    connectorInfo.getAuthConfig().getEndpoint(),
                    VERSION);
            String url = baseUrl + "?limit=" + limit + "&offset=" + offset;

            // Request body contains only the query
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("q", query);

            String body = mapper.writeValueAsString(requestBody);
            log.debug("Executing SuiteQL query with limit {} and offset {}: {}", limit, offset, query);
            ResponseEntity<String> response = client.postRaw(url, body, connectorInfo.getAuthConfig());

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RetriableException(ErrorCodes.API_ERROR.name(),
                    "SuiteQL query failed with status: " + response.getStatusCode(),
                    response.getStatusCode().toString());
            }

            SuiteQLResponse suiteQLResponse = mapper.readValue(response.getBody(), SuiteQLResponse.class);

            if (suiteQLResponse.getItems() == null) {
                log.debug("Query returned null items for query: {}", query);
                return List.of();
            }

            List<EntityData> results = suiteQLResponse.getItems().stream()
                    .map(item -> mapToEntityData(item, schema, connectorInfo))
                    .collect(Collectors.toList());

            log.debug("Query returned {} records (totalResults: {}, hasMore: {}) for query with offset {}: {}",
                    results.size(), suiteQLResponse.getTotalResults(), suiteQLResponse.isHasMore(), offset, query);

            return results;

        } catch (Exception e) {
            log.error("SuiteQL query execution failed: " + query, e);
            throw new RetriableException(ErrorCodes.API_ERROR.name(),
                "Query execution failed: " + e.getMessage(), "500");
        }
    }

    /**
     * Map SuiteQL result row to EntityData
     */
    private EntityData mapToEntityData(Map<String, Object> row, EntitySchema schema, ConnectorInfo connectorInfo) {
        // IMPORTANT: Must pass entity name to constructor for pipeline to process records correctly
        String entityName = schema != null ? schema.getApiName() : null;
        EntityData data = new EntityData(entityName);

        // Set connectorId - matches old NetSuite service behavior (line 790)
        data.setConnectorId(connectorInfo.getId());

        // Get timezone from connector config (matches old synapse behavior)
        String zoneId = connectorInfo.getMetaConfig().getOrDefault(TIMEZONE_ID, "UTC").toString();

        // Create a map of lowercase field names to actual schema field names
        Map<String, String> lowercaseToActualName = new HashMap<>();
        if (schema != null) {
            schema.getAttributes().forEach(attr ->
                lowercaseToActualName.put(attr.getApiName().toLowerCase(), attr.getApiName())
            );
        }

        row.forEach((key, value) -> {
            if (value != null) {
                if ("id".equalsIgnoreCase(key)) {
                    data.setId(value.toString());
                }
                // Set lastModified timestamp - critical for pipeline processing
                if ("lastmodifieddate".equalsIgnoreCase(key) || "lastmodified".equalsIgnoreCase(key)) {
                    try {
                        long epochMillis = parseDateToEpochMillis(value, zoneId);
                        data.setLastModified(epochMillis);
                    } catch (Exception e) {
                        log.warn("Failed to parse lastModified value: {}", value, e);
                    }
                }
                // Set createdAt timestamp - matches old NetSuite service behavior (line 783-789)
                // Old service parsed "datecreated" field with format "dd/MM/yyyy" and set as createdAt
                if ("datecreated".equalsIgnoreCase(key)) {
                    try {
                        long epochMillis = parseDateToEpochMillis(value, zoneId);
                        data.setCreatedAt(epochMillis);
                    } catch (Exception e) {
                        log.warn("Failed to parse datecreated value: {}", value, e);
                    }
                }
                // Map lowercase response key to actual schema field name
                String actualFieldName = lowercaseToActualName.getOrDefault(key.toLowerCase(), key);
                data.addValue(actualFieldName, value);
            }
        });

        return data;
    }

    /**
     * Parse date value to epoch milliseconds
     * Handles both string dates and numeric timestamps
     * Supports multiple date formats: ISO-8601, SuiteQL format, and NetSuite default format
     */
    private long parseDateToEpochMillis(Object value, String zoneId) throws Exception {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            String dateStr = (String) value;

            // Try ISO-8601 format first (REST API returns this: "2024-10-30T13:47:00Z")
            try {
                return java.time.Instant.parse(dateStr).toEpochMilli();
            } catch (Exception e1) {
                // Try SuiteQL format: "yyyy-MM-dd HH:mm:ss"
                // Use configured timezone (matches old synapse behavior)
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    sdf.setTimeZone(TimeZone.getTimeZone(zoneId));
                    return sdf.parse(dateStr).getTime();
                } catch (Exception e2) {
                    // Try NetSuite default format: "M/d/yyyy"
                    SimpleDateFormat nsFormat = new SimpleDateFormat("M/d/yyyy");
                    nsFormat.setTimeZone(TimeZone.getTimeZone(zoneId));
                    return nsFormat.parse(dateStr).getTime();
                }
            }
        }
        throw new Exception("Unable to parse date value: " + value);
    }

    // ============================================================================
    // Child Entity Support Methods
    // ============================================================================

    /**
     * Transform schema to parent entity schema for child entities
     */
    protected EntitySchema transformSchema(SyncRequest request) {
        String entityName = request.getEntityName();
        if (supportedChildEntities.contains(entityName)) {
            String parentEntityName = CHILD_PARENT_ENTITY_MAP.get(entityName);
            DescribeRequest describeRequest = new DescribeRequest(request.getConnector(), parentEntityName);
            return describe(describeRequest).orElseThrow(() ->
                new RuntimeException("Cannot find parent entity schema: " + parentEntityName));
        }
        return request.getEntitySchema();
    }

    /**
     * Build SuiteQL query for parent entity IDs when fetching child entities
     */
    private String buildFetchQueryForParent(SyncRequest request, String parentEntityName, EntitySchema parentSchema) {
        String watermarkField = parentSchema.hasWatermarkField() ?
            parentSchema.getWatermarkField().getApiName().toLowerCase() : "lastmodifieddate";

        WatermarkInfo watermark = request.getWatermark();
        if (watermark == null) {
            watermark = new WatermarkInfo(0L, System.currentTimeMillis(), false, 0);
        }

        // Get timezone from connector config (must match standard entity queries)
        String zoneId = request.getConnector().getMetaConfig().getOrDefault(TIMEZONE_ID, "UTC").toString();

        String startDate = dateUtil.formatDate(Instant.ofEpochMilli(watermark.getStart()),
            DATE_FORMAT + " HH:mm:ss", java.time.ZoneId.of(zoneId));
        String endDate = dateUtil.formatDate(Instant.ofEpochMilli(watermark.getEnd()),
            DATE_FORMAT + " HH:mm:ss", java.time.ZoneId.of(zoneId));

        // Check if parent entity needs table name mapping
        ItemQueryInfo queryInfo = ITEM_ENTITY_TO_SUITEQL_MAP.get(parentEntityName.toLowerCase());
        String actualTableName = queryInfo != null ? queryInfo.getTableName() : parentEntityName;
        String additionalFilter = queryInfo != null ? queryInfo.getWhereClause() : null;

        // Build query to get parent IDs
        StringBuilder query = new StringBuilder();
        query.append(String.format(
            "SELECT id, TO_CHAR(%s, 'YYYY-MM-DD HH24:MI:SS') AS %s FROM %s WHERE ",
            watermarkField, watermarkField, actualTableName
        ));

        // Add type filter if needed (for transaction entities)
        if (additionalFilter != null) {
            query.append(additionalFilter).append(" AND ");
        }

        query.append(String.format(
            "%s BETWEEN TO_TIMESTAMP('%s', 'YYYY-MM-DD HH24:MI:SS') " +
            "AND TO_TIMESTAMP('%s', 'YYYY-MM-DD HH24:MI:SS') ORDER BY %s, id",
            watermarkField, startDate, endDate, watermarkField
        ));

        return query.toString();
    }

    /**
     * Fetch IDs using SuiteQL with watermark filtering
     * This ensures only modified records are fetched, not all records
     * For NO_WM_ENTITIES, fetches all records without watermark filtering
     */
    private List<String> fetchIdsFromRestAPI(ConnectorInfo connector, String entityName,
                                             int limit, int offset, WatermarkInfo watermark, SyncRequest request) {
        try {
            // Check if this is a NO_WM_ENTITY (no watermark filtering)
            boolean isNoWMEntity = NO_WM_ENTITIES.contains(entityName.toLowerCase());

            String query;
            if (isNoWMEntity) {
                // NO_WM_ENTITIES: Fetch all records without watermark filtering (matches old connector)
                // Check if entity needs table name mapping
                ItemQueryInfo queryInfo = ITEM_ENTITY_TO_SUITEQL_MAP.get(entityName.toLowerCase());
                String actualTableName = queryInfo != null ? queryInfo.getTableName() : entityName;
                String additionalFilter = queryInfo != null ? queryInfo.getWhereClause() : null;

                if (additionalFilter != null) {
                    query = String.format("SELECT id FROM %s WHERE %s ORDER BY id", actualTableName, additionalFilter);
                } else {
                    query = String.format("SELECT id FROM %s ORDER BY id", actualTableName);
                }
                log.debug("Fetching ALL IDs for NO_WM entity {}: {} (limit={}, offset={})",
                        entityName, query, limit, offset);
            } else {
                // Standard entities: Use watermark filtering
                String watermarkField = "lastModifiedDate";

                // Get timezone from connector config (matches old connector behavior)
                String zoneId = connector.getMetaConfig().getOrDefault(TIMEZONE_ID, "UTC").toString();

                // Log watermark values for debugging
                log.debug("Watermark epoch values - start: {}, end: {}, isInitial: {}",
                        watermark.getStart(), watermark.getEnd(), watermark.isInitial());

                // Format watermark dates using configured timezone
                String startDate = dateUtil.formatDate(Instant.ofEpochMilli(watermark.getStart()),
                        DATE_FORMAT + " HH:mm:ss", java.time.ZoneId.of(zoneId));
                String endDate = dateUtil.formatDate(Instant.ofEpochMilli(watermark.getEnd()),
                        DATE_FORMAT + " HH:mm:ss", java.time.ZoneId.of(zoneId));

                // Check if entity needs table name mapping (e.g., transaction entities)
                ItemQueryInfo queryInfo = ITEM_ENTITY_TO_SUITEQL_MAP.get(entityName.toLowerCase());
                String actualTableName = queryInfo != null ? queryInfo.getTableName() : entityName;
                String additionalFilter = queryInfo != null ? queryInfo.getWhereClause() : null;

                // Build SuiteQL query to get IDs filtered by watermark
                // Note: LIMIT and OFFSET are passed as URL params, not in the SQL query
                StringBuilder queryBuilder = new StringBuilder();
                queryBuilder.append(String.format("SELECT id FROM %s WHERE ", actualTableName));

                // Add type filter if needed (for transaction entities)
                if (additionalFilter != null) {
                    queryBuilder.append(additionalFilter).append(" AND ");
                }

                queryBuilder.append(String.format(
                    "%s BETWEEN TO_TIMESTAMP('%s', 'YYYY-MM-DD HH24:MI:SS') " +
                    "AND TO_TIMESTAMP('%s', 'YYYY-MM-DD HH24:MI:SS') " +
                    "ORDER BY %s, id",
                    watermarkField, startDate, endDate, watermarkField
                ));

                query = queryBuilder.toString();

                log.debug("Fetching IDs with SuiteQL query for entity {}: {} (limit={}, offset={}, timezone={})",
                        entityName, query, limit, offset, zoneId);
            }

            // Execute SuiteQL query with limit and offset as URL parameters
            String suiteQLUrl = String.format("%s/services/rest/query/v1/suiteql?limit=%d&offset=%d",
                    connector.getEndpoint(), limit, offset);
            NetSuiteRestClient client = getNetSuiteRestClient();

            // Use addSuiteQLHeaders to avoid header duplication
            addSuiteQLHeaders(connector.getAuthConfig());

            Map<String, String> queryPayload = new HashMap<>();
            queryPayload.put("q", query);
            String requestBody = mapper.writeValueAsString(queryPayload);

            ResponseEntity<String> response = client.postRaw(suiteQLUrl, requestBody, connector.getAuthConfig());

            if (response.getStatusCode() == HttpStatus.OK) {
                String body = response.getBody();
                JsonNode jsonNode = mapper.readTree(body);

                List<String> ids = new ArrayList<>();
                if (jsonNode.has("items") && jsonNode.get("items").isArray()) {
                    jsonNode.get("items").forEach(item -> {
                        if (item.has("id")) {
                            ids.add(item.get("id").asText());
                        }
                    });
                }
                return ids;
            } else {
                String errorMsg = String.format("Failed to fetch IDs for %s: %s", entityName, response.getStatusCode());
                log.error(errorMsg);
                throw new NonRetriableException(ErrorCodes.API_ERROR.name(), errorMsg, response.getStatusCode().toString());
            }
        } catch (JsonProcessingException e) {
            String errorMsg = "JSON processing error while fetching IDs for " + entityName;
            log.error(errorMsg, e);
            throw new NonRetriableException(ErrorCodes.API_ERROR.name(), errorMsg + ": " + e.getMessage(), "JSON_ERROR");
        } catch (NonRetriableException | RetriableException e) {
            // Re-throw connector exceptions as-is (from client.postRaw)
            log.error("Error fetching IDs for {}: {}", entityName, e.getMessage());
            throw e;
        } catch (Exception e) {
            // Wrap unexpected exceptions in NonRetriableException
            String errorMsg = "Unexpected error fetching IDs for " + entityName;
            log.error(errorMsg, e);
            throw new NonRetriableException(ErrorCodes.API_ERROR.name(), errorMsg + ": " + e.getMessage(), "UNEXPECTED_ERROR");
        }
    }

    /**
     * Fetch individual records by ID with expandSubResources (from old NetSuite connector pattern)
     * Uses /services/rest/record/v1/{entity}/{id}?expandSubResources=true
     */
    private List<EntityData> fetchRecordsIndividually(
            ConnectorInfo connector, String entityName, List<String> ids, EntitySchema schema) {

        List<EntityData> records = new ArrayList<>();

        for (String id : ids) {
            try {
                // Create fresh client for each request to avoid header accumulation
                // Note: Content-Type header comes from authConfig.additionalHeaders in getHeaders()
                NetSuiteRestClient client = getNetSuiteRestClient();
                client.addHeader(ACCEPT, APPLICATION_JSON);

                String url = String.format(ITEM_URL, connector.getEndpoint(), entityName, id);
                log.info("Fetching {}", url);

                ResponseEntity<String> response = client.getResponse(url, connector.getAuthConfig());

                if (response.getStatusCode() == HttpStatus.OK) {
                    String body = response.getBody();
                    JsonNode jsonNode = mapper.readTree(body);

                    // Parse record with transformations
                    EntityData data = parseJsonToEntityData(jsonNode, entityName, schema, connector);
                    records.add(data);
                } else {
                    log.warn("Failed to fetch record {} for entity {}: {}", id, entityName, response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("Error fetching record " + id + " for entity " + entityName, e);
            }
        }

        return records;
    }

    /**
     * Fetch parent records with expandSubResources to get nested children
     */
    private List<EntityData> fetchParentRecordsWithChildren(
            ConnectorInfo connector, String parentEntityName, List<String> parentIds, EntitySchema parentSchema) {

        List<EntityData> parentRecords = new ArrayList<>();

        for (String parentId : parentIds) {
            try {
                // Create fresh client for each request to avoid header accumulation
                // Note: Content-Type header comes from authConfig.additionalHeaders in getHeaders()
                NetSuiteRestClient client = getNetSuiteRestClient();
                client.addHeader(ACCEPT, APPLICATION_JSON);

                String url = String.format(ITEM_URL, connector.getEndpoint(), parentEntityName, parentId);
                log.info("Fetching parent record with children: {}", url);

                ResponseEntity<String> response = client.getResponse(url, connector.getAuthConfig());

                if (response.getStatusCode() == HttpStatus.OK) {
                    String body = response.getBody();
                    JsonNode jsonNode = mapper.readTree(body);

                    // Parse parent record
                    EntityData parentData = parseJsonToEntityData(jsonNode, parentEntityName, parentSchema, connector);
                    parentRecords.add(parentData);
                } else {
                    log.warn("Failed to fetch parent record {}: {}", parentId, response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("Error fetching parent record " + parentId, e);
            }
        }

        return parentRecords;
    }

    /**
     * Parse JSON response including nested children arrays
     * Enhanced with transformations from old NetSuite connector for backward compatibility
     */
    private EntityData parseJsonToEntityData(JsonNode jsonNode, String entityName, EntitySchema schema, ConnectorInfo connector) throws Exception {
        EntityData data = new EntityData();
        data.setName(entityName);
        data.setConnectorId(schema != null ? entityName : "");

        // Get timezone from connector config (matches old synapse behavior)
        String zoneId = connector.getMetaConfig().getOrDefault(TIMEZONE_ID, "UTC").toString();

        // Convert JsonNode to Map for transformation methods
        Map<String, Object> itemMap = mapper.convertValue(jsonNode, Map.class);

        // Parse all fields including nested arrays
        jsonNode.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            JsonNode fieldValue = entry.getValue();

            // Handle addressBook - convert nested structure to flat fields (from old connector)
            if ("addressBook".equalsIgnoreCase(fieldName) && fieldValue.isObject()) {
                Map<String, Object> addressBook = mapper.convertValue(fieldValue, Map.class);
                addAddressBookValues(data, addressBook);
                return; // Don't add addressBook as-is
            }

            if (fieldValue.isObject() && fieldValue.has("items")) {
                // This is a nested array (child records)
                List<EntityData> childRecords = new ArrayList<>();
                JsonNode items = fieldValue.get("items");
                if (items.isArray()) {
                    items.forEach(item -> {
                        try {
                            // Set child entity name for proper pipeline processing
                            EntityData childData = new EntityData(fieldName);
                            item.fields().forEachRemaining(childEntry -> {
                                childData.addValue(childEntry.getKey(), extractJsonValue(childEntry.getValue()));
                            });

                            // Set ID explicitly (required for pipeline processing)
                            // NetSuite line items don't have 'id' field - they use 'line' (line number)
                            String itemId = null;
                            if (item.has("id")) {
                                itemId = item.get("id").asText();
                            } else if (item.has("line")) {
                                // Use 'line' field for line items (e.g., salesorderlineitem)
                                itemId = item.get("line").asText();
                            }

                            if (itemId != null) {
                                childData.setId(itemId);
                            }

                            childRecords.add(childData);
                        } catch (Exception e) {
                            log.warn("Failed to parse child item", e);
                        }
                    });
                }
                // Store child records in values map
                data.addValue(fieldName, childRecords);
            } else if (fieldValue.isObject()) {
                // Reference field - extract ID
                if (fieldValue.has("id")) {
                    data.addValue(fieldName, fieldValue.get("id").asText());
                }
            } else {
                // Simple value
                data.addValue(fieldName, extractJsonValue(fieldValue));
            }
        });

        // Set ID
        if (jsonNode.has("id")) {
            data.setId(jsonNode.get("id").asText());
        }

        // Set lastModified for watermark tracking
        if (jsonNode.has("lastModifiedDate")) {
            try {
                long epochMillis = parseDateToEpochMillis(jsonNode.get("lastModifiedDate").asText(), zoneId);
                data.setLastModified(epochMillis);
            } catch (Exception e) {
                log.warn("Failed to parse lastModifiedDate", e);
            }
        }

        // Apply entity-specific transformations (from old NetSuite connector)
        if ("salesorder".equalsIgnoreCase(entityName) || "estimate".equalsIgnoreCase(entityName)
                || "cashsale".equalsIgnoreCase(entityName)) {
            addSalesOrderAddresses(data, itemMap);
        }

        return data;
    }

    /**
     * Extract child records from parent records
     */
    protected List<EntityData> extractChildRecords(
            EntitySchema parentSchema, EntitySchema childSchema,
            List<EntityData> parentRecords, String childEntityName) {

        List<EntityData> childItems = new ArrayList<>();
        String childFieldName = resolveChildAPIName(parentSchema.getApiName(), childEntityName);

        log.debug("Extracting child records: parentAPI={}, childEntity={}, resolvedFieldName={}",
            parentSchema.getApiName(), childEntityName, childFieldName);

        if (childFieldName == null) {
            log.warn("Cannot resolve child field name for parent: {} child: {}",
                parentSchema.getApiName(), childEntityName);
            return childItems;
        }

        parentRecords.forEach(parentRecord -> {
            log.debug("Parent record ID={}, available fields: {}",
                parentRecord.getId(), parentRecord.getValues().keySet());
            List<EntityData> childrenRecords = parentRecord.getChildrenRecords(childFieldName);
            log.debug("Retrieved {} child records from field '{}' in parent {}",
                childrenRecords != null ? childrenRecords.size() : 0, childFieldName, parentRecord.getId());
            if (childrenRecords != null && !childrenRecords.isEmpty()) {
                childrenRecords.forEach(childRecord -> {
                    String childId = childRecord.getId();
                    log.debug("Child record before setting name/parent: id={}, name={}, hasValues={}",
                        childId, childRecord.getName(), childRecord.getValues() != null && !childRecord.getValues().isEmpty());

                    childRecord.setName(childEntityName);
                    childRecord.setParentId(parentRecord.getId());
                    childRecord.setChild(true);  // Mark as child entity for proper pipeline processing

                    // Create composite ID: parentId#childId (e.g., "502#1", "502#2")
                    // This ensures global uniqueness across all line items
                    if (childId != null && parentRecord.getId() != null) {
                        String compositeId = parentRecord.getId() + "#" + childId;
                        childRecord.setId(compositeId);
                        log.debug("Set composite ID: {} (parent: {}, line: {})",
                            compositeId, parentRecord.getId(), childId);
                    }

                    // Copy watermark from parent - this ensures all line items from modified
                    // invoices are treated as updated, matching old synapse behavior
                    if (parentRecord.getLastModified() > 0) {
                        childRecord.setLastModified(parentRecord.getLastModified());
                    }

                    log.debug("Child record after setting name/parent: id={}, name={}, parentId={}",
                        childRecord.getId(), childRecord.getName(), childRecord.getParentId());

                    childItems.add(childRecord);
                });
            }
        });

        log.debug("Extracted {} child records of type {} from {} parent records",
            childItems.size(), childEntityName, parentRecords.size());

        return childItems;
    }

    /**
     * Resolve child API name from parent entity and child entity names
     */
    private String resolveChildAPIName(String parentEntityName, String childEntityName) {
        Map<String, String> childFields = CHILD_API_NAMES.get(parentEntityName.toLowerCase());
        if (childFields != null) {
            return childFields.get(childEntityName.toLowerCase());
        }
        return null;
    }

    /**
     * Extract child field properties from parent metadata.
     * Navigates: field -> properties -> items -> items -> properties
     * Returns null if navigation fails at any step (logs warning once).
     */
    private Map<String, Object> extractChildFieldProperties(
            Map<String, Map<String, Object>> parentFields,
            String childFieldName,
            String parentEntityName,
            String childEntityName) {

        // Validate child field name resolved
        if (childFieldName == null) {
            log.warn("Could not resolve child field name for {} in parent {}", childEntityName, parentEntityName);
            return null;
        }

        // Validate parent contains child field
        if (!parentFields.containsKey(childFieldName)) {
            log.warn("Parent {} does not contain child field '{}' for {}", parentEntityName, childFieldName, childEntityName);
            return null;
        }

        // Navigate through nested structure
        try {
            Map<String, Object> childFieldMetadata = parentFields.get(childFieldName);
            Map<String, Object> properties = (Map<String, Object>) childFieldMetadata.get("properties");
            Map<String, Object> items = (Map<String, Object>) properties.get("items");
            Map<String, Object> nestedItems = (Map<String, Object>) items.get("items");
            Map<String, Object> itemProperties = (Map<String, Object>) nestedItems.get("properties");
            return itemProperties;
        } catch (ClassCastException | NullPointerException e) {
            log.warn("Could not extract child fields for {} from parent {}: unexpected metadata structure",
                childEntityName, parentEntityName);
            return null;
        }
    }

    /**
     * Calculate next watermark from parent ID results
     */
    private Long calculateNextWatermarkFromParents(List<EntityData> parentResults, SyncRequest request) {
        if (parentResults == null || parentResults.isEmpty()) {
            return request.getWatermark() != null ? request.getWatermark().getEnd() : 0L;
        }

        // Get timezone from connector config (matches old synapse behavior)
        String zoneId = request.getConnector().getMetaConfig().getOrDefault(TIMEZONE_ID, "UTC").toString();

        EntitySchema parentSchema = transformSchema(request);
        String watermarkField = parentSchema.hasWatermarkField() ?
            parentSchema.getWatermarkField().getApiName().toLowerCase() : "lastmodifieddate";

        // Find the maximum watermark value from parent results
        Long maxWatermark = 0L;
        for (EntityData result : parentResults) {
            Object watermarkValue = result.getValue(watermarkField);
            if (watermarkValue != null) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    sdf.setTimeZone(TimeZone.getTimeZone(zoneId));
                    Date date = sdf.parse((String) watermarkValue);
                    maxWatermark = Math.max(maxWatermark, date.getTime());
                } catch (Exception e) {
                    log.warn("Failed to parse watermark value: " + watermarkValue, e);
                }
            }
        }

        return maxWatermark > 0 ? maxWatermark : request.getWatermark().getEnd();
    }

    /**
     * Extract value from JSON node
     */
    private Object extractJsonValue(JsonNode node) {
        if (node.isNull()) {
            return null;
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isInt()) {
            return node.asInt();
        } else if (node.isLong()) {
            return node.asLong();
        } else if (node.isDouble()) {
            return node.asDouble();
        } else if (node.isTextual()) {
            return node.asText();
        } else {
            return node.toString();
        }
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Convert EntityData to REST API payload
     */
    private Map<String, Object> entityDataToPayload(EntityData data, EntitySchema schema) {
        // Step 1: Fix date/datetime and picklist/reference fields (matches old NetSuiteService.fixDateAndTime)
        fixDateTimeAndPicklists(data, schema);

        final Map<String, Object> originalPayload = new HashMap<>();
        String entityName = schema.getApiName().toLowerCase();

        // Step 2: Add all field values (keep original case for now - will lowercase later)
        schema.getAttributes().forEach(field -> {
            if (field.isUpdateable() || field.isInitializable()) {
                Object value = data.getValue(field.getApiName());
                if (value != null) {
                    // Keep original case for address transformation to work
                    originalPayload.put(field.getApiName(), value);
                }
            }
        });

        // Step 2.5: Convert List<EntityData> line items to proper format
        // Check if there are any List<EntityData> values (line items passed as EntityData objects)
        for (Map.Entry<String, Object> entry : new HashMap<>(originalPayload).entrySet()) {
            Object value = entry.getValue();
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                if (!list.isEmpty() && list.get(0) instanceof EntityData) {
                    // Convert List<EntityData> to List<Map<String, Object>>
                    List<Map<String, Object>> convertedItems = new ArrayList<>();
                    for (Object item : list) {
                        EntityData itemData = (EntityData) item;
                        Map<String, Object> itemMap = new HashMap<>();
                        // Add all values from the EntityData to the map (lowercase keys for NetSuite API)
                        itemData.getValues().forEach((key, val) -> {
                            if (val != null) {
                                // Transform reference fields to {"id": value} format
                                // Common reference fields in line items
                                String lowerKey = key.toLowerCase();
                                if (isLikelyReferenceField(lowerKey) && isSimpleValue(val)) {
                                    itemMap.put(lowerKey, Map.of("id", val));
                                } else {
                                    itemMap.put(lowerKey, val);
                                }
                            }
                        });
                        convertedItems.add(itemMap);
                    }
                    originalPayload.put(entry.getKey(), convertedItems);
                }
            }
        }

        // Step 3: Apply generic transformations (from old NetSuite connector for backward compatibility)

        // Transform addresses (billingAddress_* → addressBook structure)
        // This must happen BEFORE lowercasing field names
        Map<String, Object> payload = transformAddresses(originalPayload);

        // Debug log after address transformation
        log.debug("After address transformation for {} entity: {} fields", entityName, payload.keySet());

        // Now lowercase all remaining field names (NetSuite API requires lowercase)
        Map<String, Object> lowercasePayload = new HashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            lowercasePayload.put(entry.getKey().toLowerCase(), entry.getValue());
        }

        // Fix reference field formats
        fixReferenceFormats(lowercasePayload);
        removeRefNamesIfIdPresent(lowercasePayload);

        // Step 4: Apply entity-specific transformations

        // Transform line items for transaction entities
        if (requiresLineItemHandling(entityName)) {
            transformLineItems(lowercasePayload, entityName, data);
        }

        // TODO: Missing entity-specific preprocessing from old NetSuiteService.preprocess() (lines 3389-3461):
        // 1. Null value handling for specific entity fields (NULL_SUPPORTED_ENTITY_FIELDS check - line 3395-3397)
        // 2. Polymorphic reference handling (isPolymorphicReference check - lines 3409-3425)
        //    - Wraps array of values into {items: [...]} format for multi-valued polymorphic refs
        // 3. Empty list removal (removeEmptyListValues - line 3429)
        // 4. Entity-specific preprocessing methods:
        //    - preprocessJournalEntries() for journalEntry (line 3431)
        //    - preprocessLine() for salesorder, purchaseorder, cashsale, creditmemo (lines 3432-3438)
        //    - preprocessEstimate() for estimate (line 3441)
        //    - preprocessInvoice() for invoice (line 3443)
        //    - preprocessCustomerPayment() for customerpayment (line 3445)
        //    - preprocessCashRefund() for cashrefund (line 3447)
        //    - preprocessSubscription() for subscription (line 3449)
        //    - preprocessSubscriptionChangeOrder() for subscriptionchangeorder (line 3451)
        //    - preprocessSubscriptionPlan() for subscriptionplan (line 3453)
        //    - preprocessPriceplan() for priceplan (line 3455)
        //    - preprocessKitItem() for kititem (line 3457)
        // These methods handle complex entity-specific validation and transformation logic

        // Remove null and empty string values (NetSuite may reject them)
        lowercasePayload.entrySet().removeIf(entry -> {
            Object value = entry.getValue();
            return value == null || (value instanceof String && ((String) value).isEmpty());
        });

        // Log the final payload for debugging
        log.debug("Final payload for {} entity has {} fields: {}", entityName, lowercasePayload.size(), lowercasePayload.keySet());

        return lowercasePayload;
    }

    /**
     * Fix date/datetime formatting and picklist/reference transformations
     * Matches old NetSuiteService.fixDateAndTime and handlePicklistValues (lines 3849-3902)
     */
    private void fixDateTimeAndPicklists(EntityData data, EntitySchema schema) {
        schema.getAttributes().forEach(attribute -> {
            Object value = data.getValue(attribute.getApiName());
            if (data.has(attribute.getApiName()) && value != null) {
                String dataType = attribute.getDataType();

                // Format Date fields
                if ("date".equalsIgnoreCase(dataType)) {
                    if (value instanceof Date) {
                        String formattedDate = dateUtil.format((Date) value, DateUtil.dateOnlyFormat);
                        data.addValue(attribute.getApiName(), formattedDate);
                    } else {
                        log.error("Expecting Date for attribute {} but got {} ({})",
                                attribute.getApiName(), value, value.getClass().getName());
                    }
                }
                // Format DateTime fields
                else if ("datetime".equalsIgnoreCase(dataType)) {
                    if (value instanceof ZonedDateTime) {
                        ZonedDateTime typedValue = (ZonedDateTime) value;
                        ZonedDateTime utcDateTime = typedValue.withZoneSameInstant(ZoneOffset.UTC);
                        String formattedDateTime = dateUtil.format(utcDateTime, UTC_FORMAT);
                        data.addValue(attribute.getApiName(), formattedDateTime);
                    } else {
                        log.error("Expecting ZonedDateTime for attribute {} but got {} ({})",
                                attribute.getApiName(), value, value.getClass().getName());
                    }
                }
                // Transform picklist/reference fields to {id: value} or {items: [{id: val1}, ...]}
                else if ("picklist".equals(dataType) || "reference".equals(dataType) || "polymorphicreference".equals(dataType)) {
                    handlePicklistValues(data, attribute, value);
                }

                // Special handling: trandate cannot contain nulls (even though metadata says nullable)
                if ("trandate".equalsIgnoreCase(attribute.getApiName()) && data.getValue("trandate") == null) {
                    data.remove("trandate");
                }
            }
        });
    }

    /**
     * Transform picklist/reference values to NetSuite API format
     * Matches old NetSuiteService.handlePicklistValues (lines 3881-3902)
     */
    private void handlePicklistValues(EntityData data, AttributeSchema attribute, Object value) {
        if (value == null) {
            return;
        }

        if (attribute.isMultiValueField()) {
            // Multi-value field: transform to {items: [{id: val1}, {id: val2}, ...]}
            List<Object> selectedValues = (value instanceof List) ? (List<Object>) value : List.of(value);
            List<Map<String, Object>> items = selectedValues.stream()
                    .filter(o -> o != null && !StringUtils.isBlank(o.toString()))
                    .map(o -> Map.of("id", o))
                    .collect(Collectors.toList());

            if (!items.isEmpty()) {
                data.addValue(attribute.getApiName(), Map.of("items", items));
            } else {
                data.remove(attribute.getApiName());
            }
        } else {
            // Single-value field: transform to {id: value}
            if (!StringUtils.isBlank(value.toString())) {
                // Check if already wrapped (to avoid double-wrapping on reused EntityData)
                if (value instanceof Map && ((Map<?, ?>) value).containsKey("id")) {
                    // Already wrapped, don't wrap again
                    return;
                }
                data.addValue(attribute.getApiName(), Map.of("id", value));
            } else {
                data.remove(attribute.getApiName());
            }
        }
    }

    /**
     * Check if a field name is likely a reference field (entity, account, item, etc.)
     */
    private boolean isLikelyReferenceField(String fieldName) {
        // Common reference fields in NetSuite transactions and line items
        String lower = fieldName.toLowerCase();
        return lower.equals("item") ||
               lower.equals("account") ||
               lower.equals("entity") ||
               lower.equals("customer") ||
               lower.equals("vendor") ||
               lower.equals("employee") ||
               lower.equals("department") ||
               lower.equals("class") ||
               lower.equals("location") ||
               lower.equals("subsidiary") ||
               lower.equals("currency") ||
               lower.equals("taxcode") ||
               lower.equals("terms") ||
               lower.equals("paymentmethod") ||
               lower.equals("postingperiod") ||
               lower.equals("partner") ||
               lower.equals("salesrep") ||
               lower.equals("leadsource") ||
               lower.equals("opportunity") ||
               lower.equals("contact") ||
               lower.equals("parent") ||
               lower.equals("pricelevel") ||
               lower.equals("shipmethod") ||
               lower.equals("shippingaddress") ||
               lower.equals("billingaddress") ||
               lower.equals("customform") ||
               lower.equals("job") ||
               lower.equals("units") ||
               lower.equals("inventorylocation");
    }

    /**
     * Check if a value is a simple value (string or number) that should be wrapped in {"id": value}
     */
    private boolean isSimpleValue(Object value) {
        return value instanceof String || value instanceof Number;
    }

    /**
     * Extract ID from create/update response
     */
    /**
     * Extract ID from Location header in HTTP response
     * NetSuite returns Location header with format: /record/v1/customer/12345
     */
    private String extractIdFromLocationHeader(ResponseEntity<String> response) {
        if (response.getHeaders().getLocation() != null) {
            String location = response.getHeaders().getLocation().toString();
            log.debug("Extracting ID from Location header: {}", location);
            String[] parts = location.split("/");
            if (parts.length > 0) {
                return parts[parts.length - 1];
            }
        }
        return null;
    }

    private String extractIdFromResponse(JsonNode response) {
        if (response.has("id")) {
            return response.get("id").asText();
        }
        // Parse from Location header format: /record/v1/customer/12345
        if (response.has("links")) {
            JsonNode links = response.get("links");
            if (links.isArray() && links.size() > 0) {
                JsonNode firstLink = links.get(0);
                if (firstLink.has("href")) {
                    String href = firstLink.get("href").asText();
                    String[] parts = href.split("/");
                    return parts[parts.length - 1];
                }
            }
        }
        return null;
    }

    /**
     * Calculate next watermark from results
     */
    private Long calculateNextWatermark(List<EntityData> results, SyncRequest request) {
        WatermarkInfo watermark = request.getWatermark();
        long baseTime = watermark != null ? watermark.getStart() : 0L;

        if (results.isEmpty()) {
            return baseTime + WATERMARK_INCREMENT;
        }

        EntitySchema schema = request.getEntitySchema();
        if (!schema.hasWatermarkField()) {
            return System.currentTimeMillis();
        }

        // Get timezone from connector config (matches old synapse behavior)
        String zoneId = request.getConnector().getMetaConfig().getOrDefault(TIMEZONE_ID, "UTC").toString();

        String watermarkFieldName = schema.getWatermarkField().getApiName();
        // When using SELECT *, we get a formatted version with _formatted suffix
        String formattedFieldName = watermarkFieldName.toLowerCase() + "_formatted";

        EntityData lastRecord = results.get(results.size() - 1);

        // Try to get the formatted field first (from our TO_CHAR alias)
        Object watermarkValue = lastRecord.getValue(formattedFieldName);

        // Fall back to original field if formatted not available
        if (watermarkValue == null) {
            watermarkValue = lastRecord.getValue(watermarkFieldName);
        }

        if (watermarkValue != null) {
            try {
                if (watermarkValue instanceof Long) {
                    return (Long) watermarkValue;
                } else if (watermarkValue instanceof String) {
                    String dateStr = (String) watermarkValue;

                    // Try parsing as formatted date first: 'YYYY-MM-DD HH24:MI:SS'
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        sdf.setTimeZone(TimeZone.getTimeZone(zoneId));
                        Date date = sdf.parse(dateStr);
                        return date.getTime();
                    } catch (Exception e1) {
                        // Try NetSuite's default format: M/d/yyyy
                        try {
                            SimpleDateFormat nsFormat = new SimpleDateFormat("M/d/yyyy");
                            nsFormat.setTimeZone(TimeZone.getTimeZone(zoneId));
                            Date date = nsFormat.parse(dateStr);
                            return date.getTime();
                        } catch (Exception e2) {
                            log.warn("Failed to parse watermark value '{}' with both formats", dateStr);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse watermark value: " + watermarkValue, e);
            }
        }

        return System.currentTimeMillis();
    }

    /**
     * Describe standard entity using REST metadata-catalog API
     */
    private EntitySchema describeStandardEntity(DescribeRequest request, Map<String, Reference> customFieldReferenceMap) {
        String entityName = request.getEntity().toLowerCase();
        String displayName = NetsuiteSuiteQLSeed.supportedEntitiesBiMap.getOrDefault(entityName,
            StringUtils.capitalize(entityName));

        // Build metadata-catalog URL
        String apiPrefix = request.getConnector().getAuthConfig().getEndpoint() + "/services/rest";
        String describeUrl = String.format(DESCRIBE_URL, apiPrefix, VERSION, entityName);

        EntitySchema schema = new EntitySchema(entityName, displayName);

        // Check if this is a SuiteQL-only entity that doesn't exist in REST API
        // Note: These entities are skipped during schema refresh but this check handles direct describe calls
        if (UNSUPPORTED_SUITEQL_ENTITIES.contains(entityName)) {
            log.warn("Entity {} is not supported - it's not available in REST API or lacks required fields for syncing. " +
                     "For 'item', use specific entity types instead (e.g., inventoryitem, serviceitem, assemblyitem). " +
                     "For 'transactionline', it must be queried via JOIN with transaction table.", entityName);

            // Return null to indicate entity is not supported
            // This will cause describe() to return Optional.empty()
            return null;
        }

        try {
            // Fetch field metadata from REST API
            Map<String, Map<String, Object>> fields = getFieldsMap(entityName, describeUrl, request.getConnector());

            // Get reference field mappings for this entity (merges standard + custom references)
            Map<String, Reference> fieldToReferenceMap = getFieldToReferenceMap(entityName, customFieldReferenceMap);
            log.debug("Entity {} has {} reference fields configured", entityName, fieldToReferenceMap.size());

            // Add each field to schema
            for (Map.Entry<String, Map<String, Object>> entry : fields.entrySet()) {
                String apiName = entry.getKey();
                Map<String, Object> fieldMetadata = entry.getValue();

                // Special handling for journal entry line field (from old NetSuite connector)
                if ("journalentry".equalsIgnoreCase(entityName) && "line".equalsIgnoreCase(apiName)) {
                    if (fieldMetadata.get("properties") instanceof Map) {
                        Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) fieldMetadata.get("properties");
                        addJournalLineFields(schema, apiName, properties);
                    }
                    continue; // Skip normal field processing for this field
                }

                addFieldToSchema(schema, apiName, fieldMetadata, fieldToReferenceMap);
            }

            // Add address fields if entity has addressBook, billingAddress, or shippingAddress
            addAddressFields(schema, fields);

            // Add entity-specific custom fields
            addEntitySpecificFields(schema, entityName);

        } catch (Exception e) {
            log.warn("Failed to fetch metadata for entity: " + entityName + ", using basic schema", e);

            // Fallback to basic schema
            schema.addField(new AttributeSchema("id", "id")
                    .setDisplayName("Internal ID")
                    .setIdField(true)
                    .setUpdateable(false)
                    .setSystem(true)
                    .setUnique(true)
                    .setNillable(false));

            schema.addField(new AttributeSchema("lastModifiedDate", "datetime")
                    .setDisplayName("Last Modified Date")
                    .setWatermarkField(true)
                    .setUpdateable(false)
                    .setSystem(true));
        }

        // Mark entity as read-only if it's in the READ_ONLY_ENTITIES list
        if (READ_ONLY_ENTITIES.contains(entityName)) {
            schema.setReadOnly(true);
        }

        return schema;
    }

    /**
     * Describe child entity by extracting fields from parent entity metadata
     */
    private EntitySchema describeChildEntity(DescribeRequest request, Map<String, Reference> customFieldReferenceMap) {
        String childEntityName = request.getEntity().toLowerCase();

        if (!CHILD_PARENT_ENTITY_MAP.containsKey(childEntityName)) {
            throw new RuntimeException("Unsupported child entity: " + childEntityName + ", no parent found.");
        }

        String parentEntityName = CHILD_PARENT_ENTITY_MAP.get(childEntityName);
        String childDisplayName = NetsuiteSuiteQLSeed.supportedChildEntitiesBiMap.getOrDefault(
            childEntityName, StringUtils.capitalize(childEntityName));

        log.debug("Describing child entity {} via parent entity {}", childEntityName, parentEntityName);

        try {
            // Fetch parent entity metadata
            String apiPrefix = request.getConnector().getAuthConfig().getEndpoint() + "/services/rest";
            String describeUrl = String.format(DESCRIBE_URL, apiPrefix, VERSION, parentEntityName);

            Map<String, Map<String, Object>> parentFields = getFieldsMap(parentEntityName, describeUrl, request.getConnector());

            // Create child entity schema
            EntitySchema childSchema = new EntitySchema(childEntityName, childDisplayName);
            childSchema.setChild(true);

            // Match old synapse behavior: invoicelineitem and customerpaymentlineitem are writable
            // (via parent records), other child entities are read-only
            // Old synapse: NetSuiteService.java lines 1383, 1406 (no setReadOnly) vs 1341, 1362, etc. (with setReadOnly)
            Set<String> writableChildEntities = Set.of("invoicelineitem", "customerpaymentlineitem");
            if (!writableChildEntities.contains(childEntityName)) {
                childSchema.setReadOnly(true);
            }

            // Add basic system fields
            childSchema.addField(new AttributeSchema("id", "id")
                    .setDisplayName("Internal ID")
                    .setIdField(true)
                    .setUpdateable(false)
                    .setSystem(true)
                    .setUnique(true)
                    .setNillable(false));

            // Extract child field properties from parent metadata
            // NetSuite REST API structure: field -> properties -> items -> items -> properties
            String childFieldName = resolveChildAPIName(parentEntityName, childEntityName);
            Map<String, Object> itemProperties = extractChildFieldProperties(parentFields, childFieldName, parentEntityName, childEntityName);

            if (itemProperties != null) {
                // Successfully extracted child field properties - add them to schema
                Map<String, Reference> fieldToReferenceMap = getFieldToReferenceMap(parentEntityName, customFieldReferenceMap);
                for (Map.Entry<String, Object> entry : itemProperties.entrySet()) {
                    String fieldName = entry.getKey();
                    Object fieldMetadata = entry.getValue();
                    if (fieldMetadata instanceof Map) {
                        addFieldToSchema(childSchema, fieldName, (Map<String, Object>) fieldMetadata, fieldToReferenceMap);
                    }
                }
            }

            // Add reference to parent
            childSchema.addField(new AttributeSchema(parentEntityName + "Id", "reference")
                    .setDisplayName(StringUtils.capitalize(parentEntityName) + " ID")
                    .setReferenceTo(parentEntityName)
                    .setReferenceTargetField("id")
                    .setSystem(true));

            // Add lastModifiedDate field (watermark field for child entities)
            childSchema.addField(new AttributeSchema("lastModifiedDate", "datetime")
                    .setDisplayName("Last Modified Date")
                    .setWatermarkField(true)
                    .setSystem(true)
                    .setUpdateable(false));

            // Add legacy tax fields for supported entities (from old NetSuite connector)
            if (LEGACY_TAX_SUPPORTED_ENTITIES.contains(childEntityName)) {
                childSchema.addField(new AttributeSchema("taxCode", "string")
                        .setDisplayName("Tax Code")
                        .setUpdateable(true));
                childSchema.addField(new AttributeSchema("taxRate1", "double")
                        .setDisplayName("Tax Rate 1")
                        .setUpdateable(true));
                childSchema.addField(new AttributeSchema("taxRate2", "double")
                        .setDisplayName("Tax Rate 2")
                        .setUpdateable(true));
                childSchema.addField(new AttributeSchema("tax1Amt", "double")
                        .setDisplayName("Tax 1 Amount")
                        .setUpdateable(true));
                childSchema.addField(new AttributeSchema("taxAmount", "double")
                        .setDisplayName("Tax Amount")
                        .setUpdateable(true));
            }

            return childSchema;

        } catch (Exception e) {
            log.error("Failed to describe child entity: " + childEntityName, e);

            // Return minimal schema as fallback
            EntitySchema fallbackSchema = new EntitySchema(childEntityName, childDisplayName);
            fallbackSchema.addField(new AttributeSchema("id", "id")
                    .setDisplayName("Internal ID")
                    .setIdField(true)
                    .setSystem(true));

            // Mark fallback schema as read-only if it's in the READ_ONLY_ENTITIES list
            if (READ_ONLY_ENTITIES.contains(childEntityName)) {
                fallbackSchema.setReadOnly(true);
            }

            return fallbackSchema;
        }
    }

    /**
     * Fetch field metadata from REST metadata-catalog endpoint
     */
    private Map<String, Map<String, Object>> getFieldsMap(String entityName, String describeUrl, ConnectorInfo connector) {
        NetSuiteRestClient restClient = getNetSuiteRestClient();
        restClient.addHeader("Accept", SCHEMA_JSON);

        ResponseEntity<String> response = restClient.getResponse(describeUrl, connector.getAuthConfig());
        String body = response.getBody();

        log.debug("NetSuite describe response for entity {}: status={}", entityName, response.getStatusCode());

        try {
            // Parse JSON schema response
            JsonNode rootNode = mapper.readTree(body);
            JsonNode propertiesNode = rootNode.get("properties");

            if (propertiesNode == null) {
                log.warn("No properties found in schema response for {}", entityName);
                return Map.of();
            }

            Map<String, Map<String, Object>> fields = new HashMap<>();
            propertiesNode.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode fieldNode = entry.getValue();

                Map<String, Object> fieldMap = new HashMap<>();
                fieldNode.fields().forEachRemaining(prop -> {
                    JsonNode propValue = prop.getValue();
                    // Preserve nested objects (like "items") as Maps instead of flattening to strings
                    if (propValue.isObject()) {
                        fieldMap.put(prop.getKey(), mapper.convertValue(propValue, Map.class));
                    } else {
                        fieldMap.put(prop.getKey(), extractJsonValue(propValue));
                    }
                });

                fields.put(fieldName, fieldMap);
            });

            return fields;

        } catch (Exception e) {
            log.error("Failed to parse metadata response for " + entityName, e);
            return Map.of();
        }
    }

    /**
     * Check if a field is known to be non-queryable in SuiteQL
     * These fields may appear in metadata but fail when included in SuiteQL queries
     * Note: This is only used for schema building - actual queries use SELECT * to avoid field issues
     */
    private boolean isFieldNonQueryable(String apiName) {
        String lowerApiName = apiName.toLowerCase();

        // Banking fields - not queryable via SuiteQL
        if (lowerApiName.startsWith("sbank")) {
            return true;
        }

        // Known problematic fields that appear in metadata but aren't in SuiteQL
        Set<String> nonQueryableFields = Set.of(
            "altname",      // Customer alternate name - not in SuiteQL
            "trandate",     // Transaction date - only valid on transaction entities
            "unit"          // Unit field - inconsistently queryable
        );

        return nonQueryableFields.contains(lowerApiName);
    }

    /**
     * Add field to entity schema from metadata
     * Updated to include all fields regardless of title, generating readable names when needed
     * With SELECT *, NetSuite will only return queryable fields, so we include all in schema
     */
    private void addFieldToSchema(EntitySchema schema, String apiName, Map<String, Object> fieldMetadata, Map<String, Reference> fieldToReferenceMap) {
        try {
            // Only skip the "links" field as it's metadata, not actual data
            if ("links".equalsIgnoreCase(apiName)) {
                log.debug("Skipping field {} (links field)", apiName);
                return;
            }

            // Note: We don't filter out fields here anymore because:
            // 1. SELECT * will only return queryable fields from NetSuite
            // 2. We want schema to match whatever NetSuite returns
            // 3. Non-queryable fields will simply not appear in query results

            boolean isReferenceField = fieldToReferenceMap.containsKey(apiName);

            // Extract field properties
            boolean isReadOnly = (Boolean) fieldMetadata.getOrDefault("readOnly", false);
            Boolean isNullable = (Boolean) fieldMetadata.getOrDefault("nullable", true);

            // Get title - use reference field label if it's a reference field, otherwise use title from metadata or generate readable name
            String title;
            if (isReferenceField) {
                Reference ref = fieldToReferenceMap.get(apiName);
                title = ref.getReferenceFieldLabel();
            } else if (fieldMetadata.containsKey("title")) {
                title = (String) fieldMetadata.get("title");
            } else {
                // Generate readable name from API name (e.g., "addressBook" -> "Address Book")
                title = readableName(apiName);
            }

            // Determine data type - use "reference" if it's in the reference map
            String dataType;
            if (isReferenceField) {
                dataType = "reference";
            } else {
                dataType = determineDataType(fieldMetadata);
                // If determineDataType returns "reference" but it's not in STANDARD_REFERENCES,
                // treat it as a string field instead of skipping
                // This handles fields like addressBook which are objects but not entity references
                if ("reference".equals(dataType) && !isReferenceField) {
                    log.debug("Field '{}' detected as object/reference but not in STANDARD_REFERENCES, treating as string", apiName);
                    dataType = "string"; // Treat as string to preserve the field
                }
            }

            // Check for enum values (picklist)
            if (fieldMetadata.containsKey("enum")) {
                dataType = "enumeration";
            }

            // Handle nested object types with enum in id field (status, etc)
            if ("object".equalsIgnoreCase(String.valueOf(fieldMetadata.get("type")))) {
                Object propertiesObj = fieldMetadata.get("properties");
                if (propertiesObj instanceof Map) {
                    Map<String, Object> properties = (Map<String, Object>) propertiesObj;
                    if (properties.containsKey("id")) {
                        Object idObj = properties.get("id");
                        if (idObj instanceof Map) {
                            Map<String, Object> idProps = (Map<String, Object>) idObj;
                            if (idProps.containsKey("enum")) {
                                dataType = "picklist";
                            }
                        }
                    }
                }
            }

            // Create attribute schema
            AttributeSchema attr = new AttributeSchema(apiName, dataType);
            attr.setDisplayName(title);
            attr.setNillable(isNullable);
            attr.setUpdateable(!isReadOnly && !isNetSuiteSystemField(apiName));
            attr.setInitializable(!isNetSuiteSystemField(apiName));

            // Set reference target if it's a reference field
            if (isReferenceField) {
                Reference ref = fieldToReferenceMap.get(apiName);
                attr.setReferenceTo(ref.getReferredEntityName());
                attr.setReferenceTargetField("id");
            }

            // Mark system fields
            if (isNetSuiteSystemField(apiName)) {
                attr.setSystem(true);
            }

            // Mark ID field
            if ("id".equalsIgnoreCase(apiName) || "internalid".equalsIgnoreCase(apiName)) {
                attr.setIdField(true);
                attr.setUnique(true);
                attr.setNillable(false);  // ID fields cannot be null
            }

            // Mark watermark field
            if ("lastmodifieddate".equalsIgnoreCase(apiName) || "lastmodified".equalsIgnoreCase(apiName)) {
                attr.setWatermarkField(true);
            }

            // Check if it's a multi-value field (has items and hasMore properties)
            if (fieldMetadata.containsKey("properties")) {
                Object propsObj = fieldMetadata.get("properties");
                if (propsObj instanceof Map) {
                    Map<String, Object> props = (Map<String, Object>) propsObj;
                    if (props.containsKey("hasMore") && props.containsKey("items")) {
                        attr.setMultiValueField(true);
                    }
                }
            }

            schema.addField(attr);
            log.debug("Added field: {} ({})", apiName, dataType);

        } catch (Exception e) {
            log.warn("Failed to add field {} to schema: {}", apiName, e.getMessage());
        }
    }

    /**
     * Add address fields for entities with billing/shipping addresses
     * This matches the behavior in NetsuiteService.addAddressFields()
     */
    private void addAddressFields(EntitySchema schema, Map<String, Map<String, Object>> fields) {
        // Check if entity has address fields
        boolean hasBillingAddress = fields.containsKey("billingAddress") ||
                                   fields.containsKey("addressBook") ||
                                   fields.containsKey("billAddress");

        boolean hasShippingAddress = fields.containsKey("shippingAddress") ||
                                    fields.containsKey("addressBook") ||
                                    fields.containsKey("shipAddress");

        if (hasBillingAddress) {
            schema.addField(new AttributeSchema("billingAddress_attention", "string").setDisplayName("Billing Address: Attention"));
            schema.addField(new AttributeSchema("billingAddress_addressee", "string").setDisplayName("Billing Address: Addressee"));
            schema.addField(new AttributeSchema("billingAddress_addr1", "string").setDisplayName("Billing Address: Address 1"));
            schema.addField(new AttributeSchema("billingAddress_addr2", "string").setDisplayName("Billing Address: Address 2"));
            schema.addField(new AttributeSchema("billingAddress_addr3", "string").setDisplayName("Billing Address: Address 3"));
            schema.addField(new AttributeSchema("billingAddress_addrText", "string").setDisplayName("Billing Address Text"));
            schema.addField(new AttributeSchema("billingAddress_addrphone", "string").setDisplayName("Billing Address: Phone"));
            schema.addField(new AttributeSchema("billingAddress_city", "string").setDisplayName("Billing Address: City"));
            schema.addField(new AttributeSchema("billingAddress_state", "string").setDisplayName("Billing Address: State"));
            schema.addField(new AttributeSchema("billingAddress_zip", "string").setDisplayName("Billing Address: Zip"));
            schema.addField(new AttributeSchema("billingAddress_country", "string").setDisplayName("Billing Address: Country"));
            schema.addField(new AttributeSchema("billingAddress_id", "string").setDisplayName("Billing Address: ID"));
        }

        if (hasShippingAddress) {
            schema.addField(new AttributeSchema("shippingAddress_attention", "string").setDisplayName("Shipping Address: Attention"));
            schema.addField(new AttributeSchema("shippingAddress_addressee", "string").setDisplayName("Shipping Address: Addressee"));
            schema.addField(new AttributeSchema("shippingAddress_addr1", "string").setDisplayName("Shipping Address: Address 1"));
            schema.addField(new AttributeSchema("shippingAddress_addr2", "string").setDisplayName("Shipping Address: Address 2"));
            schema.addField(new AttributeSchema("shippingAddress_addr3", "string").setDisplayName("Shipping Address: Address 3"));
            schema.addField(new AttributeSchema("shippingAddress_addrText", "string").setDisplayName("Shipping Address Text"));
            schema.addField(new AttributeSchema("shippingAddress_addrphone", "string").setDisplayName("Shipping Address: Phone"));
            schema.addField(new AttributeSchema("shippingAddress_city", "string").setDisplayName("Shipping Address: City"));
            schema.addField(new AttributeSchema("shippingAddress_state", "string").setDisplayName("Shipping Address: State"));
            schema.addField(new AttributeSchema("shippingAddress_zip", "string").setDisplayName("Shipping Address: Zip"));
            schema.addField(new AttributeSchema("shippingAddress_country", "string").setDisplayName("Shipping Address: Country"));
            schema.addField(new AttributeSchema("shippingAddress_id", "string").setDisplayName("Shipping Address: ID"));
        }

        if (fields.containsKey("addressBook")) {
            schema.addField(new AttributeSchema("billingAddress_label", "string").setDisplayName("Billing Address: Label"));
            schema.addField(new AttributeSchema("billingAddress_isResidential", "boolean").setDisplayName("Billing Address: Is Residential"));
            schema.addField(new AttributeSchema("shippingAddress_label", "string").setDisplayName("Shipping Address: Label"));
            schema.addField(new AttributeSchema("shippingAddress_isResidential", "boolean").setDisplayName("Shipping Address: Is Residential"));
        }

        log.debug("Added {} address fields to schema",
            (hasBillingAddress ? 12 : 0) + (hasShippingAddress ? 12 : 0) + (fields.containsKey("addressBook") ? 4 : 0));
    }

    /**
     * Add entity-specific custom fields that are not in metadata
     * This matches the behavior in NetsuiteService describe() method
     */
    private void addEntitySpecificFields(EntitySchema schema, String entityName) {
        switch (entityName.toLowerCase()) {
            case "contact":
                // Contact address fields (separate from addressBook)
                schema.addField(new AttributeSchema("addr1", "string").setDisplayName("Address 1"));
                schema.addField(new AttributeSchema("addr2", "string").setDisplayName("Address 2"));
                schema.addField(new AttributeSchema("addr3", "string").setDisplayName("Address 3"));
                schema.addField(new AttributeSchema("city", "string").setDisplayName("City"));
                schema.addField(new AttributeSchema("state", "string").setDisplayName("State"));
                schema.addField(new AttributeSchema("zip", "string").setDisplayName("Zip"));
                schema.addField(new AttributeSchema("country", "string").setDisplayName("Country"));
                break;
            case "opportunity":
                schema.addField(new AttributeSchema("contacts", "reference")
                        .setDisplayName("Contacts")
                        .setMultiValueField(true)
                        .setNillable(true)
                        .setReferenceTo("contact")
                        .setReferenceTargetField("id"));
                break;
            case "subsidiary":
                // Add fiscalCalendar field for subsidiary (from old NetSuite connector)
                schema.addField(new AttributeSchema("fiscalCalendar", "string")
                        .setDisplayName("Fiscal Calendar"));
                break;
            case "billingschedule":
            case "subscriptionterm":
            case "campaign":
            case "customerstatus":
                // These NO_WM_ENTITIES need lastModifiedDate watermark field manually added
                // Note: pricebook and priceplan are NOT included because they don't exist in SuiteQL REST API
                if (!schema.hasWatermarkField()) {
                    schema.addField(new AttributeSchema("lastModifiedDate", "datetime")
                            .setDisplayName("Last Modified Date")
                            .setWatermarkField(true)
                            .setSystem(true));
                }
                break;
        }
    }

    /**
     * Determine Syncari data type from NetSuite field metadata
     */
    private String determineDataType(Map<String, Object> fieldMetadata) {
        Object typeObj = fieldMetadata.get("type");

        if (typeObj == null) {
            // Check if it's a reference (object with id/refName)
            Object properties = fieldMetadata.get("properties");
            if (properties instanceof Map) {
                Map<String, Object> props = (Map<String, Object>) properties;
                if (props.containsKey("id") || props.containsKey("refName")) {
                    return "reference";
                }
            }
            return "string";
        }

        String type = typeObj.toString().toLowerCase();

        switch (type) {
            case "string":
                // Check for date/datetime formats
                Object format = fieldMetadata.get("format");
                if (format != null) {
                    String formatStr = format.toString().toLowerCase();
                    if (formatStr.contains("date-time")) {
                        return "datetime";
                    } else if (formatStr.contains("date")) {
                        return "date";
                    }
                }
                return "string";
            case "integer":
                return "int";
            case "number":
                return "double";
            case "boolean":
                return "boolean";
            case "object":
                // Object types are usually references
                return "reference";
            case "array":
                return "string"; // Multi-value fields
            default:
                return "string";
        }
    }

    /**
     * Check if field is a NetSuite system field
     */
    private boolean isNetSuiteSystemField(String fieldName) {
        String lower = fieldName.toLowerCase();
        return lower.equals("id") ||
               lower.equals("internalid") ||
               lower.equals("lastmodifieddate") ||
               lower.equals("lastmodified") ||
               lower.equals("createddate") ||
               lower.equals("created");
    }

    /**
     * Populate custom record type entities by querying NetSuite CustomRecordType table via SuiteQL.
     * This discovers all custom record types (customrecord_*) available in the NetSuite account.
     *
     * Ported from NetsuiteService.populateCustomRecordTypeEntities() at line 579
     *
     * @param connector The connector configuration
     * @return BiMap of custom record type API names to display names (e.g., "customrecord_foo" -> "Custom Foo Record")
     */
    private BiMap<String, String> populateCustomRecordTypeEntities(ConnectorInfo connector) {
        NetSuiteRestClient restClient = getNetSuiteRestClient();
        restClient.addHeader("Prefer", "transient");

        int offset = 0;
        int limit = 100;

        Map<String, String> customRecordTypesMap = new HashMap<>();

        try {
            // Build SuiteQL query payload
            Map<String, String> payload = Map.of("q", GET_CUSTOM_RECORD_TYPE_SUITESQL);
            String json = mapper.writeValueAsString(payload);

            boolean hasMore;
            do {
                // Execute paginated SuiteQL query
                String url = String.format(SUITE_QUERY_URL_WITH_PAGINATION,
                        connector.getAuthConfig().getEndpoint(), VERSION, offset, limit);

                ResponseEntity<String> response = restClient.postRaw(url, json, connector.getAuthConfig());
                String body = response.getBody();

                // Parse response using Jackson
                SuiteQLResponse suiteQLResponse = mapper.readValue(body, SuiteQLResponse.class);

                // Extract custom record types
                if (suiteQLResponse.getItems() != null) {
                    suiteQLResponse.getItems().forEach(item -> {
                        String apiName = (String) item.get("apiname");
                        String name = (String) item.get("name");
                        if (apiName != null && name != null) {
                            customRecordTypesMap.put(apiName, name);
                        }
                    });
                }

                hasMore = suiteQLResponse.isHasMore();
                offset += limit;

                log.debug("Discovered {} custom record types (hasMore={})", customRecordTypesMap.size(), hasMore);

            } while (hasMore);

            log.debug("Successfully discovered {} custom record types", customRecordTypesMap.size());

        } catch (org.springframework.web.client.HttpServerErrorException e) {
            // CustomRecordType table query failed - likely permissions issue or table not available
            // Don't retry (avoid 5 retry attempts with backoff), just log and continue with standard entities
            log.warn("CustomRecordType table not accessible ({}), proceeding with standard entities only", e.getStatusCode());
            return HashBiMap.create(new HashMap<>());
        } catch (NonRetriableException nre) {
            // If Non Retriable exception occurs we don't have access to SuiteQL metadata tables
            log.warn("No access to CustomRecordType table, proceeding with standard entities only");
            return HashBiMap.create(new HashMap<>());
        } catch (RetriableException re) {
            // If retriable exception occurs (e.g., after retries exhausted), CustomRecordType query is not supported
            log.warn("CustomRecordType query failed after retries, proceeding with standard entities only: {}", re.getMessage());
            return HashBiMap.create(new HashMap<>());
        } catch (Exception e) {
            log.warn("Failed to discover custom record types: {}, proceeding with standard entities only", e.getMessage());
            return HashBiMap.create(new HashMap<>());
        }

        return HashBiMap.create(customRecordTypesMap);
    }

    /**
     * Build custom field reference mappings by querying NetSuite CustomField table via SuiteQL.
     * This discovers which custom fields reference other entities (both standard and custom).
     * Results are cached with 1 hour TTL to minimize API calls.
     *
     * Ported from NetsuiteService.buildCustomFieldReferences() at line 617
     *
     * @param connector The connector configuration
     * @param customRecordTypesMap Map of custom record types for resolving custom entity references
     * @return Map of custom field API names to Reference objects
     */
    private Map<String, Reference> buildCustomFieldReferences(ConnectorInfo connector, Map<String, String> customRecordTypesMap) {
        Map<String, Reference> customFieldReferenceMap = new HashMap<>();

        if (customRecordTypesMap.isEmpty()) {
            log.debug("No custom record types found, skipping custom field reference discovery");
            return customFieldReferenceMap;
        }

        NetSuiteRestClient restClient = getNetSuiteRestClient();
        restClient.addHeader("Prefer", "transient");

        int offset = 0;
        int limit = 100;

        try {
            // Build SuiteQL query payload
            Map<String, String> payload = Map.of("q", GET_FIELD_REFERENCE_TYPE_SUITESQL);
            String json = mapper.writeValueAsString(payload);

            boolean hasMore;
            do {
                // Execute paginated SuiteQL query
                String url = String.format(SUITE_QUERY_URL_WITH_PAGINATION,
                        connector.getAuthConfig().getEndpoint(), VERSION, offset, limit);

                ResponseEntity<String> response = restClient.postRaw(url, json, connector.getAuthConfig());
                String body = response.getBody();

                // Parse response using Jackson
                SuiteQLResponse suiteQLResponse = mapper.readValue(body, SuiteQLResponse.class);

                // Extract custom field references
                if (suiteQLResponse.getItems() != null) {
                    suiteQLResponse.getItems().forEach(item -> {
                        String referenceName = (String) item.get("referredentityname");
                        String referenceFieldName = (String) item.get("apiname");
                        String referenceFieldLabel = (String) item.get("name");

                        // Resolve the referred entity name (could be standard or custom entity)
                        String referredEntityName = null;

                        // Check if it's a standard entity
                        if (NetsuiteSuiteQLSeed.supportedEntitiesBiMap.inverse().containsKey(referenceName)) {
                            referredEntityName = NetsuiteSuiteQLSeed.supportedEntitiesBiMap.inverse().get(referenceName);
                        }
                        // Check if it's a custom record type
                        else if (customRecordTypesMap.containsKey(referenceName)) {
                            referredEntityName = referenceName; // Use custom record API name directly
                        }

                        // Add to reference map if valid
                        if (StringUtils.isNotEmpty(referredEntityName) && StringUtils.isNotEmpty(referenceFieldName)) {
                            customFieldReferenceMap.put(referenceFieldName,
                                    new Reference(referredEntityName, referenceFieldName, referenceFieldLabel));
                        }
                    });
                }

                hasMore = suiteQLResponse.isHasMore();
                offset += limit;

            } while (hasMore);

        } catch (NonRetriableException nre) {
            // If Non Retriable exception occurs we don't have access to SuiteQL metadata tables
            log.debug("No access to CustomField table, proceeding without custom field references", nre);
            return new HashMap<>();
        } catch (Exception e) {
            log.debug("Failed to discover custom field references", e);
            return new HashMap<>();
        }

        return customFieldReferenceMap;
    }

    /**
     * Get reference field mappings for an entity, merging standard hard-coded references
     * with dynamically discovered custom field references.
     *
     * @param entityName The entity name
     * @param customFieldReferenceMap Map of custom field references (from discovery)
     * @return Map of field names to Reference objects
     */
    private Map<String, Reference> getFieldToReferenceMap(String entityName, Map<String, Reference> customFieldReferenceMap) {
        // Start with hard-coded standard references for this entity
        Map<String, Reference> references = new HashMap<>(
                STANDARD_REFERENCES.getOrDefault(entityName, Set.of()).stream()
                        .collect(Collectors.toMap(Reference::getReferenceFieldName, r -> r))
        );

        // Add dynamically discovered custom field references
        if (customFieldReferenceMap != null && !customFieldReferenceMap.isEmpty()) {
            references.putAll(customFieldReferenceMap);
            log.debug("Entity {} has {} total references ({} standard, {} custom)",
                    entityName,
                    references.size(),
                    STANDARD_REFERENCES.getOrDefault(entityName, Set.of()).size(),
                    customFieldReferenceMap.size());
        } else {
            log.debug("Entity {} has {} standard references (no custom field discovery)",
                    entityName,
                    references.size());
        }

        return references;
    }

    /**
     * Convert API name to readable display name (e.g., "addressBook" -> "Address Book")
     * Matches the logic in NetsuiteService.readableName() at line 1625
     */
    private String readableName(String apiName) {
        String[] splits = StringUtils.splitByCharacterTypeCamelCase(apiName);
        List<String> capitalizedSplits = Arrays.stream(splits)
                .map(StringUtils::capitalize)
                .collect(Collectors.toList());
        return StringUtils.join(capitalizedSplits, " ");
    }

    //TODO - verify
    /**
     * Describe custom record by querying metadata
     */
    private EntitySchema describeCustomRecord(DescribeRequest request,
                                               BiMap<String, String> customRecordTypeEntities,
                                               Map<String, Reference> customFieldReferenceMap) {
        String entityName = request.getEntity().toLowerCase();

        // Get display name from discovered custom record types
        String displayName = customRecordTypeEntities.getOrDefault(entityName,
            StringUtils.capitalize(entityName));

        // Build metadata-catalog URL
        String apiPrefix = request.getConnector().getAuthConfig().getEndpoint() + "/services/rest";
        String describeUrl = String.format(DESCRIBE_URL, apiPrefix, VERSION, entityName);

        EntitySchema schema = new EntitySchema(entityName, displayName);
        schema.setCustom(true); // Mark as custom entity

        try {
            // Fetch field metadata from REST API (same as standard entities)
            Map<String, Map<String, Object>> fields = getFieldsMap(entityName, describeUrl, request.getConnector());

            // Get reference field mappings for this entity (merges standard + custom references)
            Map<String, Reference> fieldToReferenceMap = getFieldToReferenceMap(entityName, customFieldReferenceMap);
            log.debug("Custom record {} has {} reference fields configured", entityName, fieldToReferenceMap.size());

            // Add each field to schema
            for (Map.Entry<String, Map<String, Object>> entry : fields.entrySet()) {
                String apiName = entry.getKey();
                Map<String, Object> fieldMetadata = entry.getValue();
                addFieldToSchema(schema, apiName, fieldMetadata, fieldToReferenceMap);
            }

            // Add address fields if entity has addressBook, billingAddress, or shippingAddress
            addAddressFields(schema, fields);

        } catch (Exception e) {
            log.warn("Failed to fetch metadata for custom record: " + entityName + ", using basic schema", e);

            // Fallback to basic schema
            schema.addField(new AttributeSchema("id", "id")
                    .setDisplayName("Internal ID")
                    .setIdField(true)
                    .setUpdateable(false)
                    .setSystem(true)
                    .setUnique(true)
                    .setNillable(false));

            schema.addField(new AttributeSchema("name", "string")
                    .setDisplayName("Name")
                    .setInitializable(true)
                    .setUpdateable(true));

            schema.addField(new AttributeSchema("lastModifiedDate", "datetime")
                    .setDisplayName("Last Modified Date")
                    .setWatermarkField(true)
                    .setUpdateable(false)
                    .setSystem(true));
        }

        return schema;
    }

    /**
     * Add required headers for SuiteQL queries
     * NetSuite SuiteQL requires the Prefer header with "transient" value
     */
    private void addSuiteQLHeaders(AuthConfig authConfig) {
        if (authConfig.getAdditionalHeaders() == null) {
            authConfig.setAdditionalHeaders(new HashMap<>());
        }
        // Required header for SuiteQL queries
        authConfig.getAdditionalHeaders().put("Prefer", "transient");
        // Note: Content-Type is already added by NetSuiteRestClient.getHeaders() for POST operations
    }

    /**
     * Escape special characters in SQL query parameters
     */
    private String escape(String value) {
        return value.replace("'", "\\'").replace("\"", "\\\"").replace("\\", "\\\\").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\t", "\\t").replace("$", "\\$");
    }

    /**
     * Fetch picklist values by extracting enum values from field metadata
     * This replaces the SOAP-based getSelectValues() approach with REST metadata-catalog API
     */
    private FetchResponse fetchPicklistValues(SyncRequest request) {
        EntityParams params = new EntityParams()
                .setConnector(request.getConnector())
                .setSchema(request.getEntitySchema())
                .setSourceParams(request.getSourceParams());
        validateEntityConfig(params);

        String picklistParamString = Objects.toString(request.getSourceParam("picklistParams"), null);
        List<Pair<String, String>> picklistParams = getPicklistParams(picklistParamString);

        final WatermarkInfo nextWM = request.getWatermark().copy();
        // Use the same lastModified for all picklist entries, so no records are dropped in framework
        long lastModified = System.currentTimeMillis();
        List<EntityData> allPicklistValues = new ArrayList<>();

        // For each entity.field pair, fetch enum values from metadata-catalog API
        picklistParams.forEach(pair -> {
            String entityName = pair.getX();
            String fieldName = pair.getY();

            try {
                log.debug("Fetching picklist values for {}.{}", entityName, fieldName);

                // Build metadata-catalog URL
                String apiPrefix = request.getConnector().getAuthConfig().getEndpoint() + "/services/rest";
                String describeUrl = String.format(DESCRIBE_URL, apiPrefix, VERSION, entityName);

                // Fetch field metadata
                Map<String, Map<String, Object>> fields = getFieldsMap(entityName, describeUrl, request.getConnector());

                if (!fields.containsKey(fieldName)) {
                    log.warn("Field {} not found in entity {}. Available fields: {}", fieldName, entityName, fields.keySet());
                    return;
                }

                Map<String, Object> fieldMetadata = fields.get(fieldName);

                // Extract enum values
                List<String> enumValues = extractEnumValues(fieldMetadata);

                if (enumValues.isEmpty()) {
                    log.warn("No enum values found for field {}.{}", entityName, fieldName);
                    return;
                }

                // Convert each enum value to EntityData
                for (String enumValue : enumValues) {
                    EntityData picklistRecord = createPicklistEntityData(
                            entityName, fieldName, enumValue, lastModified);
                    allPicklistValues.add(picklistRecord);
                }

            } catch (Exception e) {
                log.error("Failed to fetch picklist values for {}.{}", entityName, fieldName, e);
                // Continue with other picklist params even if one fails
            }
        });

        log.debug("Fetched total {} picklist values across {} field(s)", allPicklistValues.size(), picklistParams.size());

        return new FetchResponse(nextWM, new ListBasedIterator(allPicklistValues, nextWM) {
            // We don't want to filter records by watermark, because picklist values don't have created/updated dates
            @Override
            protected void filterRecords(List<EntityData> records, WatermarkInfo watermark) {
                this.filteredRecords = records;
            }
        });
    }

    /**
     * Extract enum values from field metadata
     * Handles both direct enum fields and object fields with nested enum in "id" property
     */
    private List<String> extractEnumValues(Map<String, Object> fieldMetadata) {
        List<String> enumValues = new ArrayList<>();

        // Case 1: Direct enum field (e.g., "enum": ["A", "B", "C"])
        if (fieldMetadata.containsKey("enum")) {
            Object enumObj = fieldMetadata.get("enum");
            if (enumObj instanceof List) {
                List<?> enumList = (List<?>) enumObj;
                for (Object val : enumList) {
                    if (val != null) {
                        enumValues.add(val.toString());
                    }
                }
            }
        }
        // Case 2: Object field with enum in nested "id" property (e.g., status field)
        else if ("object".equalsIgnoreCase(String.valueOf(fieldMetadata.get("type")))) {
            Object propertiesObj = fieldMetadata.get("properties");
            if (propertiesObj instanceof Map) {
                Map<String, Object> properties = (Map<String, Object>) propertiesObj;
                if (properties.containsKey("id")) {
                    Object idObj = properties.get("id");
                    if (idObj instanceof Map) {
                        Map<String, Object> idProps = (Map<String, Object>) idObj;
                        if (idProps.containsKey("enum")) {
                            Object enumObj = idProps.get("enum");
                            if (enumObj instanceof List) {
                                List<?> enumList = (List<?>) enumObj;
                                for (Object val : enumList) {
                                    if (val != null) {
                                        enumValues.add(val.toString());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return enumValues;
    }

    /**
     * Check if properties indicate a polymorphic reference
     * (from old NetSuite connector for backward compatibility)
     */
    private boolean isPolymorphic(Map<String, Map<String, Object>> properties) {
        return (properties.size() == 4 && properties.keySet().containsAll(REF_KEYS))
                || (properties.size() == 6 && properties.keySet().containsAll(EMBEDDED_REF_KEYS));
    }

    /**
     * Resolve data type from field metadata
     * (from old NetSuite connector for backward compatibility)
     */
    private String resolveDataType(Map<String, Reference> fieldToReferenceMap, String apiName, HashMap<String, Object> v) {
        if (fieldToReferenceMap.containsKey(apiName)) {
            return "reference";
        } else {
            // JSON Schema allows 'format' fields to narrow down string datatypes
            String datatype = v.get("type").toString();
            String format = v.getOrDefault("format", "").toString();
            if ("string".equals(datatype)) {
                if ("date".equals(format)) {
                    return "date";
                }
                if ("date-time".equals(format)) {
                    return "datetime";
                }
            }
            return datatype;
        }
    }

    /**
     * Create AttributeSchema with all properties
     * (from old NetSuite connector for backward compatibility)
     */
    private AttributeSchema createAttr(EntitySchema entity, String apiName, String displayName, String type, List<String> picklistValues,
                                       boolean isCustomField, boolean isNillable, Optional<Reference> reference,
                                       boolean isReadonly, boolean isUpdatedAtField, boolean isCreatedAtField, boolean isWatermarkField) {
        boolean isId = "id".equalsIgnoreCase(apiName);
        AttributeSchema attr = new AttributeSchema()
                .setApiName(apiName)
                .setDisplayName(displayName)
                .setDataType(type)
                .setPicklistValues(picklistValues)
                .setNillable(!isId && isNillable)
                .setUpdateable(!isReadonly)
                .setUpdatedAtField(isUpdatedAtField)
                .setCreatedAtField(isCreatedAtField)
                .setIdField(isId)
                .setSystem(SYSTEM_FIELDS.contains(apiName))
                .setWatermarkField(isWatermarkField)
                .setCustom(isCustomField)
                .setUnique(isId);

        if (reference.isPresent()) {
            attr.setReferenceTargetField("id").setReferenceTo(reference.get().getReferredEntityName());
        } else if ("polymorphicreference".equalsIgnoreCase(attr.getDataType())) {
            // If no reference info found, we do not want to set the references to 'null', it messes up the pipeline.
            attr.setDataType("object");
            // Retain original datatype in additionalProps
            entity.getAdditionalProperties().putIfAbsent("attributeProperties", new HashMap<>());
            final Map<Object, Object> attributeProperties = (Map<Object, Object>) entity.getAdditionalProperties().get("attributeProperties");
            attributeProperties.put(attr.getApiName(), Map.of("originalType", "polymorphicreference"));
        }
        return attr;
    }

    /**
     * Add journal entry line fields - creates credit and debit prefixed fields
     * (from old NetSuite connector for backward compatibility)
     */
    private void addJournalLineFields(EntitySchema entity, String apiName, Map<String, Map<String, Object>> properties) {
        Map<String, Object> lineProperties = ConnectorHelper.getNestedMap(properties.get("items"), "items.properties");
        lineProperties.forEach((lineApiName, lineAttributes) -> {
            // Skip debit field, because we are adding an "amount" field to both credit & debit lines explicitly
            if (!"links".equalsIgnoreCase(lineApiName) && !"debit".equalsIgnoreCase(lineApiName)) {
                Map<String, Object> props = (Map<String, Object>) lineAttributes;
                boolean isReference = "object".equalsIgnoreCase(props.get("type").toString());
                Reference ref = null;
                String derivedApiName = "credit".equalsIgnoreCase(lineApiName) ? "amount" : lineApiName;

                log.info("Adding Journal Field {}", derivedApiName);
                if (isReference) {
                    ref = JOURNAL_LINE_REFERENCES.get(lineApiName);
                    String dataType = ref != null ? "reference" : "polymorphicreference";
                    String title = ref != null ? ref.getReferenceFieldLabel() : StringUtils.capitalize(lineApiName);
                    entity.addField(createAttr(entity, "__credit_" + derivedApiName, "Credit Line :" + title, dataType, List.of(),
                            Boolean.class.cast(props.getOrDefault("x-ns-custom-field", false)),
                            Boolean.class.cast(props.getOrDefault("nullable", true)),
                            Optional.ofNullable(ref), Boolean.class.cast(props.getOrDefault("readOnly", false)), false, false, false));
                    entity.addField(createAttr(entity, "__debit_" + derivedApiName, "Debit Line :" + title, dataType, List.of(),
                            Boolean.class.cast(props.getOrDefault("x-ns-custom-field", false)),
                            Boolean.class.cast(props.getOrDefault("nullable", true)),
                            Optional.ofNullable(ref), Boolean.class.cast(props.getOrDefault("readOnly", false)), false, false, false));
                } else {
                    String title = "credit".equalsIgnoreCase(lineApiName) ? "Amount" : props.getOrDefault("title", StringUtils.capitalize(lineApiName)).toString();
                    log.info("Adding Simple Journal Field with title {}", title);
                    var creditLineAttr = createAttr(entity, "__credit_" + derivedApiName, "Credit Line :" + title, props.get("type").toString(), List.of(),
                            Boolean.class.cast(props.getOrDefault("x-ns-custom-field", false)),
                            Boolean.class.cast(props.getOrDefault("nullable", true)),
                            Optional.ofNullable(ref), Boolean.class.cast(props.getOrDefault("readOnly", false)), false, false, false);
                    var debitLineAttr = createAttr(entity, "__debit_" + derivedApiName, "Debit Line :" + title, props.get("type").toString(), List.of(),
                            Boolean.class.cast(props.getOrDefault("x-ns-custom-field", false)),
                            Boolean.class.cast(props.getOrDefault("nullable", true)),
                            Optional.ofNullable(ref), Boolean.class.cast(props.getOrDefault("readOnly", false)), false, false, false);

                    entity.addField(creditLineAttr);
                    entity.addField(debitLineAttr);
                }
            }
        });
    }

    /**
     * Transform flat address fields to addressBook structure for NetSuite
     * (from old NetSuite connector for backward compatibility)
     */
    private Map<String, Object> toAddressBook(Map<String, Object> entityData) {
        Map<String, Object> addressBook = new HashMap<>();
        List<Map<String, Object>> addresses = new ArrayList<>();

        Map<String, Object> billingAddress = createAddress(entityData, "billingAddress", Optional.empty());
        if (!billingAddress.isEmpty()) {
            billingAddress.put("defaultBilling", true);
            addresses.add(billingAddress);
        }

        Map<String, Object> shippingAddress = createAddress(entityData, "shippingAddress", Optional.empty());
        if (!shippingAddress.isEmpty()) {
            shippingAddress.put("defaultShipping", true);
            addresses.add(shippingAddress);
        } else if (!billingAddress.isEmpty()) {
            // If shipping address is not provided, mark billing address as defaultShipping
            billingAddress.put("defaultShipping", true);
        }

        if (!addresses.isEmpty()) {
            addressBook.put("items", addresses);
        }
        return addressBook;
    }

    /**
     * Create address object from flat address fields
     * (from old NetSuite connector for backward compatibility)
     */
    private Map<String, Object> createAddress(Map<String, Object> entityData, String prefix, Optional<String> id) {
        Map<String, Object> address = new HashMap<>();
        Map<String, Object> addressDetails = new HashMap<>();

        if (entityData.get(prefix + "_attention") != null) addressDetails.put("attention", entityData.get(prefix + "_attention"));
        if (entityData.get(prefix + "_addressee") != null) addressDetails.put("addressee", entityData.get(prefix + "_addressee"));
        if (entityData.get(prefix + "_addr1") != null) addressDetails.put("addr1", entityData.get(prefix + "_addr1"));
        if (entityData.get(prefix + "_addr2") != null) addressDetails.put("addr2", entityData.get(prefix + "_addr2"));
        if (entityData.get(prefix + "_addr3") != null) addressDetails.put("addr3", entityData.get(prefix + "_addr3"));
        if (entityData.get(prefix + "_addrText") != null) addressDetails.put("addrText", entityData.get(prefix + "_addrText"));
        if (entityData.get(prefix + "_city") != null) addressDetails.put("city", entityData.get(prefix + "_city"));
        if (entityData.get(prefix + "_state") != null) addressDetails.put("state", entityData.getOrDefault(prefix + "_state", ""));
        if (entityData.get(prefix + "_zip") != null) addressDetails.put("zip", entityData.get(prefix + "_zip"));
        if (entityData.get(prefix + "_country") != null) addressDetails.put("country", entityData.get(prefix + "_country"));
        if (entityData.get(prefix + "_addrphone") != null) addressDetails.put("addrphone", entityData.get(prefix + "_addrphone"));
        if (entityData.get(prefix + "_label") != null) address.put("label", entityData.get(prefix + "_label"));
        if (entityData.get(prefix + "_isResidential") != null) address.put("isResidential", entityData.get(prefix + "_isResidential"));

        // Wrap address details in "addressBookAddress" key (matches old NetSuiteService.createAddress line 1264)
        if (!addressDetails.isEmpty()) {
            address.put("addressBookAddress", addressDetails);
        }

        id.ifPresent(idString -> address.put("internalId", Long.valueOf(idString)));
        return address;
    }

    /**
     * Transform address fields and remove flat address fields from payload
     * (from old NetSuite connector for backward compatibility)
     */
    private Map<String, Object> transformAddresses(Map<String, Object> payload) {
        Map<String, Object> addressItems = toAddressBook(payload);
        if (!addressItems.isEmpty()) {
            payload.put("addressBook", addressItems);

            // Remove flat address fields from payload
            payload.keySet().removeIf(key ->
                key.startsWith("billingAddress_") || key.startsWith("shippingAddress_")
            );
        }
        return payload;
    }

    /**
     * Fix reference field formats - ensures reference fields have proper structure
     * (from old NetSuite connector for backward compatibility)
     */
    private void fixReferenceFormats(Map<String, Object> values) {
        values.entrySet().forEach(entry -> {
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> refMap = (Map<String, Object>) value;
                // If it has an id field, it's a reference
                if (refMap.containsKey("id")) {
                    // Ensure it's in the correct format for NetSuite
                    Object idValue = refMap.get("id");
                    if (idValue != null) {
                        // If idValue is itself a Map with an id (double-wrapped), extract the inner id
                        if (idValue instanceof Map && ((Map<String, Object>) idValue).containsKey("id")) {
                            idValue = ((Map<String, Object>) idValue).get("id");
                        }
                        entry.setValue(Map.of("id", idValue.toString()));
                    }
                }
            }
        });
    }

    /**
     * Remove refName field if id is present (NetSuite doesn't need both)
     * (from old NetSuite connector for backward compatibility)
     */
    private void removeRefNamesIfIdPresent(Map<String, Object> values) {
        values.entrySet().forEach(entry -> {
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> refMap = (Map<String, Object>) value;
                // If both id and refName exist, remove refName
                if (refMap.containsKey("id") && refMap.containsKey("refName")) {
                    refMap.remove("refName");
                }
            }
        });
    }

    /**
     * Check if entity requires line item transformation
     */
    private boolean requiresLineItemHandling(String entityName) {
        return Set.of("salesorder", "purchaseorder", "invoice", "estimate",
                     "cashsale", "creditmemo", "cashrefund").contains(entityName.toLowerCase());
    }

    /**
     * Transform line items from flat structure to NetSuite nested structure
     * (from old NetSuite connector for backward compatibility)
     */
    private void transformLineItems(Map<String, Object> payload, String entityName, EntityData originalData) {
        // Child entity field name mapping
        // Journal entries use "line" field, most other transactions use "item"
        String childFieldName = "journalentry".equals(entityName.toLowerCase()) ? "line" : "item";
        String childEntityName = entityName.toLowerCase() + "lineitem";

        // Check if line items are already provided as a List (from EntityData line items)
        Object existingItems = payload.get(childFieldName);
        if (existingItems instanceof List) {
            List<?> itemsList = (List<?>) existingItems;
            if (!itemsList.isEmpty() && itemsList.get(0) instanceof Map) {
                // Line items already in correct format, just wrap them
                Map<String, Object> itemsWrapper = new HashMap<>();
                itemsWrapper.put("items", itemsList);
                payload.put(childFieldName, itemsWrapper);
                return;
            }
        }

        // Remove line item fields from main payload and collect them
        List<Map<String, Object>> lineItems = new ArrayList<>();
        Map<String, Object> lineItemData = new HashMap<>();

        // Find all fields that belong to line items (they'll have index suffix like _0, _1, etc.)
        payload.keySet().removeIf(key -> {
            // Check if this is a line item field (has numeric suffix)
            if (key.matches(".*_\\d+$")) {
                String baseFieldName = key.replaceAll("_\\d+$", "");
                String indexStr = key.replaceAll(".*_(\\d+)$", "$1");
                int index = Integer.parseInt(indexStr);

                // Ensure we have a map for this line item index
                while (lineItems.size() <= index) {
                    lineItems.add(new HashMap<>());
                }

                // Add this field to the appropriate line item
                lineItems.get(index).put(baseFieldName, payload.get(key));
                return true; // Remove from main payload
            }
            return false;
        });

        // If we found line items, add them to payload in NetSuite format
        if (!lineItems.isEmpty()) {
            Map<String, Object> itemsWrapper = new HashMap<>();
            itemsWrapper.put("items", lineItems);
            payload.put(childFieldName, itemsWrapper);
        }
    }

    // ==================== READ TRANSFORMATIONS (REST API → EntityData) ====================

    /**
     * Convert nested addressBook structure to flat address fields (from old NetSuite connector)
     * This is the inverse of toAddressBook() - used during READ operations
     */
    private void addAddressBookValues(EntityData data, Map<String, Object> addressBook) {
        List<Object> addressItems = (List<Object>) addressBook.getOrDefault("items", List.of());
        addressItems.forEach(addressItem -> {
            addAddressItem(data, addressItem);
        });
    }

    /**
     * Process individual address item and add flat fields to EntityData (from old NetSuite connector)
     */
    private void addAddressItem(EntityData data, Object addressItem) {
        if (addressItem instanceof Map) {
            Map<String, Object> address = (Map<String, Object>) addressItem;
            Map<String, Object> addressDetails = (Map<String, Object>) address.getOrDefault("addressBookAddress", Map.of());
            String addressLabel = address.getOrDefault("label", "").toString();
            Boolean isResidential = Boolean.valueOf(address.getOrDefault("isResidential", "false").toString());
            Boolean isSyncedFromSubsidiary = Boolean.valueOf(address.getOrDefault("isSyncedFromSubsidiary", "false").toString());
            Boolean isBilling = Boolean.valueOf(address.getOrDefault("defaultBilling", "false").toString());
            String id = address.getOrDefault("internalId", "").toString();
            Boolean isShipping = Boolean.valueOf(address.getOrDefault("defaultShipping", "false").toString());

            if (isBilling) {
                addAddressValues("billingAddress", addressDetails, data);
                data.addValue("billingAddress_isResidential", isResidential);
                data.addValue("billingAddress_label", addressLabel);
                data.addValue("billingAddress_isSyncedFromSubsidiary", isSyncedFromSubsidiary);
                data.addValue("billingAddress_id", id);
            }
            if (isShipping) {
                addAddressValues("shippingAddress", addressDetails, data);
                data.addValue("shippingAddress_isResidential", isResidential);
                data.addValue("shippingAddress_label", addressLabel);
                data.addValue("shippingAddress_isSyncedFromSubsidiary", isSyncedFromSubsidiary);
                data.addValue("shippingAddress_id", id);
            }
        }
    }

    /**
     * Add flat address field values with given prefix (from old NetSuite connector)
     * Used during READ to flatten nested address structures
     */
    private void addAddressValues(String prefix, Map<String, Object> addressDetails, EntityData data) {
        if (addressDetails == null || addressDetails.isEmpty()) {
            return;
        }
        data.addValue(prefix + "_attention", addressDetails.getOrDefault("attention", ""));
        data.addValue(prefix + "_addressee", addressDetails.getOrDefault("addressee", ""));
        data.addValue(prefix + "_addr1", addressDetails.getOrDefault("addr1", ""));
        data.addValue(prefix + "_addr2", addressDetails.getOrDefault("addr2", ""));
        data.addValue(prefix + "_addr3", addressDetails.getOrDefault("addr3", ""));
        data.addValue(prefix + "_addrText", addressDetails.getOrDefault("addrText", ""));
        // Note: when retrieved, the key for phone in address is "addrPhone" (uppercase P)
        data.addValue(prefix + "_addrphone", addressDetails.getOrDefault("addrPhone", ""));
        data.addValue(prefix + "_city", addressDetails.getOrDefault("city", ""));
        data.addValue(prefix + "_state", addressDetails.getOrDefault("state", ""));
        data.addValue(prefix + "_zip", addressDetails.getOrDefault("zip", ""));
        // Country is a reference object, extract ID
        if (addressDetails.get("country") instanceof Map) {
            Map<String, Object> country = (Map<String, Object>) addressDetails.get("country");
            data.addValue(prefix + "_country", country.get("id"));
        } else {
            data.addValue(prefix + "_country", addressDetails.get("country"));
        }
    }

    /**
     * Add sales order specific address fields (from old NetSuite connector)
     * For entities like salesorder, estimate, cashsale that have direct billingAddress/shippingAddress
     */
    private void addSalesOrderAddresses(EntityData data, Map<String, Object> item) {
        Map<String, Object> billAddressDetails = (Map<String, Object>) item.get("billingAddress");
        addAddressValues("billingAddress", billAddressDetails, data);
        Map<String, Object> shipAddressDetails = (Map<String, Object>) item.get("shippingAddress");
        addAddressValues("shippingAddress", shipAddressDetails, data);
    }

    /**
     * Create EntityData for a single picklist value
     * Format matches the old SOAP-based implementation
     */
    private EntityData createPicklistEntityData(String entityName, String fieldName, String enumValue, long lastModified) {
        // Create unique ID in format: entityName_fieldName_internalId
        String picklistId = String.format("%s_%s_%s", entityName, fieldName, enumValue);

        EntityData entityData = new EntityData(NetsuiteSeed.PICKLIST_VALUES_ENTITY);
        entityData.setId(picklistId);
        entityData.setLastModified(lastModified);
        entityData.setCreatedAt(lastModified);

        // Add field values matching the schema
        entityData.addValue("id", picklistId);
        entityData.addValue("internalId", enumValue);
        entityData.addValue("externalId", null); // Not available in REST API metadata
        entityData.addValue("name", enumValue); // Use enum value as name (REST API doesn't provide separate display name)
        entityData.addValue("entityName", entityName);
        entityData.addValue("fieldName", fieldName);
        entityData.addValue("lastModified", lastModified);

        return entityData;
    }

    /**
     * Fetch child entities by watermark.
     * Child entities are fetched by querying parent records with expandSubResources=true,
     * then extracting the nested child records.
     */
    private FetchResponse fetchChildEntitiesByWatermark(SyncRequest request, String requestedEntityName,
                                                        EntitySchema originalSchema) {
        log.info("Fetching child records for parent {}", requestedEntityName);

        // Transform to parent entity
        String parentEntityName = CHILD_PARENT_ENTITY_MAP.get(requestedEntityName);
        EntitySchema parentSchema = transformSchema(request);

        WatermarkInfo watermark = request.getWatermark();
        int pageSize = (request.getPageSize() <= 0) ? MAX_PAGE_SIZE : request.getPageSize();
        // Note: watermark.getLimit() represents number of pages/batches, not total records
        // So multiply by pageSize to get actual maxRecords
        int maxRecords = (watermark != null && watermark.getLimit() > 0) ? watermark.getLimit() * pageSize : 0;

        // Create page fetcher for child entities
        PageFetcher pageFetcher = (wm, limit, offset) -> {
            // Build query for parent IDs
            String query = buildFetchQueryForParent(request, parentEntityName, parentSchema);

            // Execute SuiteQL query to get parent IDs with pagination
            List<EntityData> parentIdResults = executeSuiteQLQueryWithOffset(
                request.getConnector(), query, null, limit, (int) offset);

            // Extract parent IDs
            List<String> parentIds = parentIdResults.stream()
                .map(EntityData::getId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());

            if (parentIds.isEmpty()) {
                return Pair.of(0, List.of());
            }

            // Fetch parent records with expandSubResources=true to get nested children
            List<EntityData> parentRecords = fetchParentRecordsWithChildren(
                request.getConnector(), parentEntityName, parentIds, parentSchema);

            // Extract child records from parent records
            List<EntityData> childRecords = extractChildRecords(
                parentSchema, originalSchema, parentRecords, requestedEntityName);

            log.info("Got {} child records from REST", childRecords.size());

            return Pair.of(parentIdResults.size(), childRecords);
        };

        // Create generator using common logic
        Function3<WatermarkInfo, Integer, Long, Pair<Boolean, DataWithOffset>> generator =
            createPaginationGenerator(request, pageFetcher, requestedEntityName);

        return createIteratorResponse(watermark, generator, parentSchema, pageSize, maxRecords, false);
    }

    /**
     * Fetch standard entities by watermark using SuiteQL queries.
     */
    private FetchResponse fetchStandardEntitiesByWatermark(SyncRequest request, EntitySchema schema) {
        WatermarkInfo watermark = request.getWatermark();
        int pageSize = (request.getPageSize() <= 0) ? MAX_PAGE_SIZE : request.getPageSize();
        // Note: watermark.getLimit() represents number of pages/batches, not total records
        // So multiply by pageSize to get actual maxRecords
        int maxRecords = (watermark != null && watermark.getLimit() > 0) ? watermark.getLimit() * pageSize : 0;

        // Check if this is a NO_WM_ENTITY
        boolean isNoWMEntity = NO_WM_ENTITIES.contains(request.getEntityName().toLowerCase());

        // Create page fetcher for standard entities
        PageFetcher pageFetcher = (wm, limit, offset) -> {
            // Step 1: Fetch IDs using SuiteQL with watermark filtering
            List<String> ids = fetchIdsFromRestAPI(
                request.getConnector(),
                request.getEntityName(),
                limit,
                (int) offset,
                wm,
                request
            );

            if (ids.isEmpty()) {
                return Pair.of(0, List.of());
            }

            // Step 2: Fetch full records individually using REST record API
            List<EntityData> pageResults = fetchRecordsIndividually(
                request.getConnector(),
                request.getEntityName(),
                ids,
                schema
            );

            // Log sample record for debugging on first page
            if (offset == 0 && !pageResults.isEmpty()) {
                EntityData sample = pageResults.get(0);
                log.debug("Sample record - ID: {}, Name: {}, ConnectorId: {}, LastModified: {}, Fields: {}, Values: {}",
                        sample.getId(),
                        sample.getName(),
                        sample.getConnectorId(),
                        sample.getLastModified(),
                        sample.getValues().keySet(),
                        sample.getValues().size());
            }

            return Pair.of(ids.size(), pageResults);
        };

        // Create generator using common logic
        Function3<WatermarkInfo, Integer, Long, Pair<Boolean, DataWithOffset>> generator =
            createPaginationGenerator(request, pageFetcher, request.getEntityName());

        return createIteratorResponse(watermark, generator, schema, pageSize, maxRecords, isNoWMEntity);
    }

    /**
     * Functional interface for fetching a page of data.
     * Allows different implementations for child vs standard entities.
     */
    @FunctionalInterface
    private interface PageFetcher {
        /**
         * Fetch a page of data.
         *
         * @param watermark Current watermark
         * @param limit Max records to fetch
         * @param offset Offset for pagination
         * @return Pair of (records fetched count, list of EntityData)
         */
        Pair<Integer, List<EntityData>> fetchPage(WatermarkInfo watermark, int limit, long offset);
    }

    /**
     * Create a generator function for pagination.
     * Common logic shared by both child and standard entity fetchers.
     */
    private Function3<WatermarkInfo, Integer, Long, Pair<Boolean, DataWithOffset>> createPaginationGenerator(
            SyncRequest request,
            PageFetcher pageFetcher,
            String entityName) {

        return (wm, pgSize, offset) -> {
            try {
                // Calculate limit for this batch
                int limit = Math.min(MAX_PAGE_SIZE, pgSize);

                // Fetch page using provided fetcher
                Pair<Integer, List<EntityData>> fetchResult = pageFetcher.fetchPage(wm, limit, offset);
                int fetchedCount = fetchResult.getX();
                List<EntityData> pageResults = fetchResult.getY();

                if (pageResults.isEmpty()) {
                    return Pair.of(false, new DataWithOffset(offset, 0, List.of(), List.of()));
                }

                // Set connectorId on all records - required for pipeline processing
                String connectorId = request.getConnector().getId();
                pageResults.forEach(data -> data.setConnectorId(connectorId));

                // Determine if there are more pages
                boolean hasMore = fetchedCount >= limit;
                long nextOffset = hasMore ? offset + fetchedCount : 0;

                log.debug("Fetched {} records for entity {} at offset {}, hasMore: {}",
                        pageResults.size(), entityName, offset, hasMore);

                return Pair.of(hasMore, new DataWithOffset(offset, nextOffset, pageResults, List.of()));

            } catch (NonRetriableException | RetriableException e) {
                // Re-throw connector exceptions as-is (including authentication errors)
                log.error("Failed to fetch page at offset {}: {}", offset, e.getMessage());
                throw e;
            } catch (Exception e) {
                log.error("Failed to fetch page at offset {}", offset, e);
                throw new RuntimeException("Failed to fetch page: " + e.getMessage(), e);
            }
        };
    }

    /**
     * Create FetchResponse with NetsuiteIncrementalIterator.
     * Common logic shared by both child and standard entity fetchers.
     */
    private FetchResponse createIteratorResponse(WatermarkInfo watermark,
                                                  Function3<WatermarkInfo, Integer, Long, Pair<Boolean, DataWithOffset>> generator,
                                                  EntitySchema schema,
                                                  int pageSize,
                                                  int maxRecords,
                                                  boolean ignoreWMMode) {
        NetsuiteIncrementalIterator iterator = new NetsuiteIncrementalIterator(
            watermark,
            0, // initial offset
            generator,
            new ArrayList<>(), // initial empty data
            schema.hasWatermarkField() ? schema.getWatermarkField() : null,
            pageSize,
            maxRecords,
            ignoreWMMode
        );

        return new FetchResponse(watermark, iterator);
    }
}
