package com.syncari.connector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.netsuite.suitetalk.client.v2020_1.WsClient;
import com.netsuite.suitetalk.proxy.v2020_1.lists.accounting.ItemGroup;
import com.netsuite.suitetalk.proxy.v2020_1.lists.accounting.ItemMember;
import com.netsuite.suitetalk.proxy.v2020_1.lists.relationships.Contact;
import com.netsuite.suitetalk.proxy.v2020_1.platform.core.Record;
import com.netsuite.suitetalk.proxy.v2020_1.platform.core.RecordRef;
import com.netsuite.suitetalk.proxy.v2020_1.platform.core.StatusDetail;
import com.netsuite.suitetalk.proxy.v2020_1.platform.core.types.RecordType;
import com.netsuite.suitetalk.proxy.v2020_1.platform.messages.WriteResponse;
import com.netsuite.suitetalk.proxy.v2020_1.transactions.sales.*;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.config.ProxyConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.*;
import com.syncari.connector.exception.*;
import com.syncari.connector.rest.NetSuiteRestClient;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.*;
import com.syncari.connector.service.seed.NetsuiteSeed;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.netsuite.suitetalk.client.v2020_1.utils.Utils.createRecordRef;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component(Constants.NETSUITE)
public class NetSuiteService implements AuthenticationService, CommonDataService, MetadataService, SynapseInfoService, RestClientService {
    private static final Set<String> EMBEDDED_REF_KEYS = Set.of("links", "totalResults", "count", "hasMore", "offset", "items");
    private static final Set<String> REF_KEYS = Set.of("id", "refName", "externalId", "links");
    public static final String UTC_FORMAT = "yyyy-MM-dd'T'HH:mm:ssz";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final long _WATERMARK_INCREMENT = 1 * 24 * 60 * 60 * 1000l; //1 days
    public static final long _WATERMARK_INCREMENT_HALF_DAY = 12 * 60 * 60 * 1000l; //1 days
    public static final long _WATERMARK_INCREMENT_2_HOUR = 2 * 60 * 60 * 1000l; //2 hours
    public static final long _WATERMARK_INCREMENT_1_HOUR = 60 * 60 * 1000l; //1 hour

    private static final List<String> REPLACE_FOR_ENTITY = List.of("salesorder");
    static final String REPLACE_SUBLIST = "replaceSublist";
    private final int WAIT_TIMEOUT_MILLIS = 300000;
    private final int MAX_RECORDS_PER_PAGE_FOR_QUERY_API = 500;

    public static final int MAX_PAGE_SIZE = 1000;

    static final String SAVED_SEARCH_PREFIX = "saved_search_";

    static final String ENABLE_SAVED_SEARCH = "enableSavedSearches";

    static final String ENABLE_SUITEQL_SYNC = "enableSuiteQLSync";

    static final String SAVED_SEARCHES_LIST = "savedSearchesList";
    static final String TIMEZONE_ID = "timeZoneId";


    @Data
    @EqualsAndHashCode
    public static class CacheKey {
        ConnectorInfo info;
        // Transformed name - datastore name
        String entityName;

        public CacheKey(ConnectorInfo info, String entityName) {
            this.info = info;
            this.entityName = entityName;
        }
    }

    LoadingCache<CacheKey, EntitySchema> schemaCache = CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(6l, TimeUnit.HOURS)
            .build(new CacheLoader<>() {
                @Override
                public EntitySchema load(CacheKey key) {
                    // describe and put to cache
                    return describe(new DescribeRequest(key.getInfo(), key.getEntityName())).get();
                }
            });

    /**
     * payment lne item property processor to handle some specific properties
     */
    private BiConsumer<EntityData, Map<String, Object>> additionalPaymentItemProcessor = (e,v)->{
        Map<String, Object> doc = (Map<String, Object>) v.getOrDefault("doc", Map.of());
        if(!doc.isEmpty()) {
            e.addValue("invoiceId", doc.get("id").toString());
        }
    };
    ObjectMapper mapper =  JsonMapper.builder() // or different mapper for other format
            .addModule(new ParameterNamesModule())
            .addModule(new Jdk8Module())
            .addModule(new JavaTimeModule())
            .defaultDateFormat(new SimpleDateFormat(DATE_FORMAT))
            // and possibly other configuration, modules, then:
            .build();


    @Autowired
    NetSuiteSOAPService netSuiteSOAPService;
    @Autowired
    LocalStorageService localStorageService;

    private static final String VERSION = "v1";
    private static final String API_PREFIX = "%s/services/rest";
    private static final String DESCRIBE_URL = "%s/record/%s/metadata-catalog/%s";

    private static final String RECORD_URL = "%s/services/rest/record/%s/%s";
    private static final String SINGLE_RECORD_URL = "%s/services/rest/record/%s/%s/%s";

    private static final String SUITE_QUERY_URL = "%s/query/%s/suiteql?offset=%s&limit=%s";

    private static final String GET_CUSTOM_RECORD_TYPE_SUITESQL = "SELECT lower(scriptid) as apiname, name  FROM CustomRecordType where isinactive='F' ORDER BY name";
    private static final String GET_FIELD_REFERNECE_TYPE_SUITESQL = "SELECT lower(scriptid) as apiname, name, BUILTIN.DF( fieldvaluetyperecord ) AS referredentityname FROM CustomField WHERE fieldvaluetyperecord IS NOT NULL ORDER BY name ";
    private static final String GET_CUSTOM_RECORD_LAST_MODIFIED_SUITESQL = "SELECT id, TO_CHAR(SYS_EXTRACT_UTC(lastModified), 'YYYY-mm-dd\"T\"hh24:Mi:ss\"Z\"') AS lastModified FROM %s WHERE id in (%s)";

    private static final String ITEM_URL = "%s/services/rest/record/v1/%s/%s?expandSubResources=true";

    private static final String ITEMS_OFFSET_URL = "%s/services/rest/record/v1/%s?limit=%s&offset=%s";

    private static final String LOCATION_PATTERN = "%s\\/([^\\/]+)$";

    private static final String SCHEMA_JSON = "application/schema+json";
    private static final String APPLICATION_JSON = "application/json";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String ACCEPT = "Accept";

    private static final Set<String> SYSTEM_FIELDS = Set.of("lastModifiedDate", "createdDate");
    private final static Set<String> JOURNAL_FIELDS =Set.of("id","memo","trandate","subsidiary","account","createdDate","lastModifiedDate");
    private final static Set<String> JOURNAL_LINE_FIELDS =Set.of("account","credit","debit");

    @Data
    @AllArgsConstructor
    static class Reference {
        private String referredEntityName;
        private String referenceFieldName;
        private String referenceFieldLabel;
    }

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
                    //new Reference("customerStatus", "entityStatus", "Customer Status"),
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
                    //new Reference("customerStatus", "entityStatus", "Customer Status"),
                    //new Reference("term", "terms", "Terms")
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

    private static final Map<String, Set<String>> NULL_SUPPORTED_ENTITY_FIELDS = Map.ofEntries(
            Map.entry("customer", Set.of("salesRep"))
    );

    private static final Map<String, Reference> JOURNAL_LINE_REFERENCES = Map.of(
                    "entity",new Reference("customer", "entity", "Customer")
    );
    private static final Map<String, Set<String>> REQUIRED_FIELDS = Map.of(
            "opportunity", Set.of("entity")
    );

    private static final Set<String> supportedEntities = NetsuiteSeed.supportedEntitiesMap.keySet();

    protected static final Set<String> supportedChildEntities = NetsuiteSeed.supportedChildEntities.keySet();

    private static final Map<String, String> CHILD_PARENT_ENTITY_MAP = Map.ofEntries(
            Map.entry("salesorderlineitem", "salesorder"),
            Map.entry("purchaseorderlineitem", "purchaseorder"),
            Map.entry("cashsalelineitem", "cashsale"),
            Map.entry("creditmemolineitem", "creditmemo"),
            Map.entry("invoicelineitem","invoice"),
            Map.entry("estimatelineitem", "estimate"),
            Map.entry("customerpaymentlineitem","customerpayment"),
            Map.entry("cashrefundlineitem", "cashrefund"),
            Map.entry("subscriptionline","subscription"),
            Map.entry("priceinterval","subscription"),
            Map.entry("subscriptionchangeorderline", "subscriptionchangeorder"),
            Map.entry("subscriptionplanline", "subscriptionplan"),
            Map.entry("pricetier", "priceplan"),
            Map.entry("kititemmember", "kititem")
    );
    //parent object schema -> map of (child objet schema -> child field name on parent record)
    private static final Map<String, Map<String,String>> CHILD_API_NAMES = Map.ofEntries(
            Map.entry("salesorder", Map.of("salesorderlineitem","salesorderlineitems")),
            Map.entry("purchaseorder", Map.of("purchaseorderlineitem","purchaseorderlineitems")),
            Map.entry("cashsale", Map.of("cashsalelineitem","cashsalelineitems")),
            Map.entry("creditmemo", Map.of("creditmemolineitem","creditmemolineitems")),
            Map.entry("estimate", Map.of("estimatelineitem","estimatelineitems")),
            Map.entry("invoice", Map.of("invoicelineitem","invoicelineitems")),
            Map.entry("customerpayment", Map.of("v","apply")),
            Map.entry("cashrefund", Map.of("cashrefundlineitem", "cashrefundlineitems")),
            Map.entry("subscription", Map.of("subscriptionline","subscriptionlines", "priceinterval","priceintervals")),
            Map.entry("subscriptionchangeorder", Map.of("subscriptionchangeorderline", "subscriptionchangeorderlines")),
            Map.entry("subscriptionplan", Map.of("subscriptionplanline", "subscriptionplanlines")),
            Map.entry("priceplan", Map.of("pricetier","pricetiers")),
            Map.entry("kititem", Map.of("kititemmember", "kititemmembers"))
    );

    private static final Set<String> TRANSACTION_ENTITIES = Set.of("salesorder", "invoice", "customerpayment", "opportunity", "estimate", "customerdeposit", "cashrefund");

    private static final Set<String> NO_WM_ENTITIES = Set.of("account","billingaccount", "billingschedule", "subsidiary", "priceinterval", "campaign",
            "subscription", "subscriptionline", "subscriptionchangeorder", "subscriptionchangeorderline", "currency", "subscriptionplan", "subscriptionplanline",
            "subscriptionterm", "pricebook", "priceplan", "pricetier", "pricelevel", "location", "term", "customerstatus", "classification", "department");

    public static final Set<String> READ_ONLY_ENTITIES = Set.of("currency","billingschedule", "subsidiary","pricebook", "location", "subscriptionterm", "term", "estimatelineitem",
            "customerrefund", "classification", "department", "assemblybuild", "assemblyunbuild", "bintransfer",
            "binworksheet", "check", "deposit", "depositapplication", "expensereport", "inventoryadjustment", "inventorycostrevaluation",
            "inventorytransfer", "itemfulfillment", "itemreceipt", "returnauthorization", "transferorder", "vendorbill",
            "vendorcredit", "vendorpayment", "vendorreturnauthorization", "workorder", "workorderclose", "workordercompletion", "workorderissue", "statisticaljournalentry",
            "paycheckjournal","intercompanyjournalentry");

    public static final Map<String, String> INVALID_RESULT_TYPES = Map.of(
            "lotnumberedinventoryitem", "inventoryitem"
    );

    private static class ItemQueryInfo {
        final String tableName;
        final String whereClause;

        ItemQueryInfo(String tableName, String whereClause) {
            this.tableName = tableName;
            this.whereClause = whereClause;
        }
    }

    private static final Map<String, ItemQueryInfo> ITEM_ENTITY_TO_SUITEQL_MAP = Map.of(
        "noninventorysaleitem", new ItemQueryInfo("item", "itemtype = 'NonInvtPart' AND subtype = 'Sale'"),
        "inventoryitem", new ItemQueryInfo("item", "itemtype = 'InvtPart'"),
        "campaign", new ItemQueryInfo("searchcampaign", null),
        "journalentry", new ItemQueryInfo("transaction", "type = 'Journal'")
        // Can add other item types as needed in the future
    );

    private static final Set<String> LEGACY_TAX_SUPPORTED_ENTITIES = Set.of("invoicelineitem", "estimatelineitem", "salesorderlineitem");
    private static final Set<String> LEGACY_TAX_SUPPORTED_PARENT_ENTITIES = Set.of("invoice", "estimate", "salesorder");
    private static final Set<String> LEGACY_TAX_FIELDS = Set.of("taxCode", "taxRate1", "taxRate2", "tax1Amt", "taxAmount");

    private final AtomicInteger threadCountTracker = new AtomicInteger(0);

    private static final int MAX_THREAD_LIMIT = 12;

    @Autowired
    DateUtil dateUtil;

    JsonParserConfig parserConfig;

    public Set<String> getSupportedEntities() {
        return supportedEntities;
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getTokenBasedOAuthType());
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
    public List<AuthField> getConfigureFields() {
        AuthField endpointField = ConnectorHelper.getEndpointField();
        endpointField.setHelpSummary("Web Services URL. E.g. https://ACCOUNT_ID.suitetalk.api.netsuite.com");
        AuthField enableSavedSearches = new AuthField().setDataType("checkbox").setName(ENABLE_SAVED_SEARCH)
                .setLabel("Enable Saved Searches").setRequired(false);
        AuthField savedSearchesList = new AuthField().setName(SAVED_SEARCHES_LIST).setLabel("Saved Search IDs").setRequired(false)
                .setDataType("text").setHelpSummary(i18n("saved_search_ids"));
        AuthField enableSuiteQLSync = new AuthField().setDataType("checkbox").setName(ENABLE_SUITEQL_SYNC)
                .setLabel("Enable Sync using SuiteQL").setRequired(false);
        AuthField timeZone = new AuthField();
        timeZone.setDataType("text");
        timeZone.setName(TIMEZONE_ID);
        timeZone.setLabel(i18n("timezone_label"));
        timeZone.setHelpSummary(i18n("timezone_help"));
        timeZone.setRequired(false);
        return List.of(endpointField, enableSavedSearches, savedSearchesList, enableSuiteQLSync, timeZone, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCategory() {
        return "CRM";
    }

    @Override
    public String getName() {
        return Constants.NETSUITE;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/netsuite.svg")
                .setDisplayName("Netsuite")
                .setBackgroundColor("#F2F9FF")
                .setHelpUrl(helpArticlesBaseUrl + "/360052656591-Netsuite-Setup");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19206400663828";
    }

	private RestTemplate getTemplate(Optional<ProxyConfig> proxy) {
		HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
		if (proxy.isPresent() && StringUtils.isNotEmpty(proxy.get().getHost())) {
			HttpHost httpProxy = new HttpHost(proxy.get().getHost(), proxy.get().getPort());
			httpClientBuilder.setProxy(httpProxy);
			log.debug("Setting proxy with {} {}", proxy.get().getHost(), proxy.get().getPort());
			CloseableHttpClient client = httpClientBuilder.build();
			clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(client);
		}
		clientHttpRequestFactory.setConnectTimeout(WAIT_TIMEOUT_MILLIS);
		clientHttpRequestFactory.setReadTimeout(WAIT_TIMEOUT_MILLIS);
		return new RestTemplate(clientHttpRequestFactory);
	}

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
                restClient.addHeader(ACCEPT, SCHEMA_JSON);
                ResponseEntity<String> responseEntity = restClient.getResponse(authenticateEndpoint, authConfig);

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

    private BiMap<String, String> populateCustomRecordTypeEntities(ConnectorInfo connector){
        NetSuiteRestClient restClient = getNetSuiteRestClient();
        restClient.addHeader("Prefer", "transient");
        boolean hasMore = false;

        int offset = 0;
        int limit = 100;

        String json = "";
        try {
            Map<String, String> payload = Map.of("q",GET_CUSTOM_RECORD_TYPE_SUITESQL);
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed parsing json with message: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
        Map<String, String> customRecordTypesMap = new HashMap();
        try {
            do {
                String url = format(SUITE_QUERY_URL, getApiUrlPrefix(connector), VERSION, offset, limit);
                ResponseEntity<String> response = restClient.postRaw(url, json, connector.getAuthConfig());
                String body = response.getBody();
                DocumentContext ctx = JsonPath.parse(body);
                List<HashMap<String, String>> customObjectList = ctx.read("items");
                customObjectList.forEach(v -> {
                    customRecordTypesMap.put(v.get("apiname"), v.get("name"));
                });
                hasMore = BooleanUtils.isTrue(ctx.read("hasMore"));
                offset = offset + limit;
            } while (hasMore);
        } catch (NonRetriableException nre){
            // If Non Retriable exception occurs we dont have access to suiteSQL. Ignore and process only standard objects
            return HashBiMap.create(new HashMap());
        }

        return HashBiMap.create(customRecordTypesMap);
    }

    private Map<String, Reference> buildCustomFieldReferences(ConnectorInfo connector, Map<String, String> customRecordTypesMap){
        Map<String, Reference> customFieldReferenceMap = new HashMap();
        if(customRecordTypesMap.isEmpty()){
            // No custom objects implying no field mapping
            return new HashMap();
        }

        NetSuiteRestClient restClient = getNetSuiteRestClient();
        restClient.addHeader("Prefer", "transient");
        boolean hasMore = false;

        int offset = 0;
        int limit = 100;

        String json = "";
        try {
            Map<String, String> payload = Map.of("q",GET_FIELD_REFERNECE_TYPE_SUITESQL);
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed parsing json with message: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
        try {
            do {
                String url = format(SUITE_QUERY_URL, getApiUrlPrefix(connector), VERSION, offset, limit);
                ResponseEntity<String> response = restClient.postRaw(url, json, connector.getAuthConfig());
                String body = response.getBody();
                DocumentContext ctx = JsonPath.parse(body);
                List<HashMap<String, String>> customObjectList = ctx.read("items");
                customObjectList.forEach(v -> {
                    String referenceName = v.get("referredentityname");
                    String referredEntityName = NetsuiteSeed.supportedEntitiesBiMap.inverse().containsKey(referenceName)
                            ? NetsuiteSeed.supportedEntitiesBiMap.inverse().get(referenceName)
                            : customRecordTypesMap.get(referenceName);
                    String referenceFieldName = v.get("apiname");
                    String referenceFieldLabel = v.get("name");
                    if (StringUtils.isNotEmpty(referredEntityName) && StringUtils.isNotEmpty(referenceFieldName)) {
                        customFieldReferenceMap.put(referenceFieldName, new Reference(referredEntityName, referenceFieldName, referenceFieldLabel));
                    }
                });
                hasMore = BooleanUtils.isTrue(ctx.read("hasMore"));
                offset = offset + limit;
            } while (hasMore);
        } catch (NonRetriableException nre){
            // If Non Retriable exception occurs we dont have access to suiteSQL. Ignore and process only standard objects
            return new HashMap();
        }

        return customFieldReferenceMap;

    }

    public List<EntitySchema> describeAllSavedSearches(DescribeAllRequest describeAllRequest, Set<String> savedSearchesList) {
        List<EntitySchema> result = new ArrayList<>();
        WsClient client = netSuiteSOAPService.getClient(describeAllRequest.getConnector());
        List<RecordRef> savedSearches = netSuiteSOAPService.getTransactionSavedSearches(client);
        for (RecordRef recordRef : savedSearches) {
            String savedSearchApiName = SAVED_SEARCH_PREFIX + recordRef.getInternalId();
            if((describeAllRequest.getEntities().isEmpty() || describeAllRequest.getEntities().contains(savedSearchApiName)) && (savedSearchesList.isEmpty() || savedSearchesList.contains(recordRef.getInternalId()))) {
                EntitySchema entitySchema = new EntitySchema(savedSearchApiName);
                entitySchema.setDisplayName("Saved Search: " + recordRef.getName());
                entitySchema.setReadOnly(true);
                List<AttributeSchema> attributeSchemas = netSuiteSOAPService.getSavedSearchAttributes(client, recordRef.getInternalId(), false);
                entitySchema.setAttributes(attributeSchemas);
                result.add(entitySchema);
            }
        }
        savedSearches = netSuiteSOAPService.getCustomSavedSearches(client);
        for (RecordRef recordRef : savedSearches) {
            String savedSearchApiName = SAVED_SEARCH_PREFIX + recordRef.getInternalId();
            if((describeAllRequest.getEntities().isEmpty() || describeAllRequest.getEntities().contains(savedSearchApiName)) && (savedSearchesList.isEmpty() || savedSearchesList.contains(recordRef.getInternalId()))) {
                EntitySchema entitySchema = new EntitySchema(savedSearchApiName);
                entitySchema.setDisplayName("Saved Search: " + recordRef.getName());
                entitySchema.setReadOnly(true);
                entitySchema.setCustom(true);
                List<AttributeSchema> attributeSchemas = netSuiteSOAPService.getSavedSearchAttributes(client, recordRef.getInternalId(), true);
                entitySchema.setAttributes(attributeSchemas);
                result.add(entitySchema);
            }
        }
        return result;
    }

    public Optional<EntitySchema> describeSavedSearch(DescribeRequest describeRequest) {
        WsClient client = netSuiteSOAPService.getClient(describeRequest.getConnector());
        List<RecordRef> savedSearches = netSuiteSOAPService.getTransactionSavedSearches(client);
        for (RecordRef recordRef : savedSearches) {
            if(describeRequest.getEntity().equalsIgnoreCase(SAVED_SEARCH_PREFIX + recordRef.getInternalId())) {
                EntitySchema entitySchema = new EntitySchema(describeRequest.getEntity());
                entitySchema.setDisplayName("Saved Search: " + recordRef.getName());
                entitySchema.setReadOnly(true);
                List<AttributeSchema> attributeSchemas = netSuiteSOAPService.getSavedSearchAttributes(client, describeRequest.getEntity().substring(SAVED_SEARCH_PREFIX.length()), false);
                entitySchema.setAttributes(attributeSchemas);
                return Optional.of(entitySchema);
            }
        }
        savedSearches = netSuiteSOAPService.getCustomSavedSearches(client);
        for (RecordRef recordRef : savedSearches) {
            if(describeRequest.getEntity().equalsIgnoreCase(SAVED_SEARCH_PREFIX + recordRef.getInternalId())) {
                EntitySchema entitySchema = new EntitySchema(describeRequest.getEntity());
                entitySchema.setDisplayName("Saved Search: " + recordRef.getName());
                entitySchema.setReadOnly(true);
                entitySchema.setCustom(true);
                List<AttributeSchema> attributeSchemas = netSuiteSOAPService.getSavedSearchAttributes(client, describeRequest.getEntity().substring(SAVED_SEARCH_PREFIX.length()), true);
                entitySchema.setAttributes(attributeSchemas);
                return Optional.of(entitySchema);
            }
        }
        return Optional.empty();
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

    private String escape(String value) {
        return value.replace("'", "\\'").replace("\"", "\\\"").replace("\\", "\\\\").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\t", "\\t").replace("$", "\\$");
    }

    private List<Map<String, String>> executeQuery(String query, ConnectorInfo connector){
        NetSuiteRestClient restClient = getNetSuiteRestClient();
        restClient.addHeader("Prefer", "transient");

        String body = "";
        try {
            Map<String, String> payload = Map.of("q", query);
            body = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed parsing json with message: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }

        boolean hasMore;
        int offset = 0;
        int limit=MAX_RECORDS_PER_PAGE_FOR_QUERY_API;
        List<Map<String, String>> items = new ArrayList<>();
        do {
            String url = format(SUITE_QUERY_URL, getApiUrlPrefix(connector), VERSION, offset, limit);
            ResponseEntity<String> response = restClient.postRaw(url, body, connector.getAuthConfig());
            DocumentContext ctx = JsonPath.parse(response.getBody());
            List<Map<String, String>> currItems = ctx.read("items");
            items.addAll(currItems);
            hasMore = ctx.read("hasMore");
            offset += limit;
        } while (hasMore);
        log.info("Fetched {} records",items.size());
        return items;
    }

    private String getIDForSearchResults(String entity, Map<String, String> item){
        return item.getOrDefault("id", "");
    }

    private List<EntityData> toEntityData(String connectorId, List<Map<String, String>> results, String entity) {
        List<EntityData> response = new ArrayList<>();
        for (Map<String, String> item: results) {
            EntityData data = new EntityData(entity);
            data.setId(getIDForSearchResults(entity, item));
            if(item.containsKey("datecreated") && !StringUtils.isBlank(item.get("datecreated"))){
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");;
                LocalDate date = LocalDate.parse(item.get("datecreated"), formatter);
                ZonedDateTime resultado = date.atStartOfDay(ZoneId.systemDefault());
                data.setCreatedAt(
                        resultado.toInstant().toEpochMilli());
            }
            data.setConnectorId(connectorId);
            for (Map.Entry<String, String> entry : item.entrySet()) {
                data.addValue(entry.getKey(), entry.getValue());
            }
            response.add(data);
        }
        return response;
    }

    @Override
    public List<EntityData> search(SearchRequest request) {
        log.debug(request.getQuery());
        String requestQuery = request.getQuery();
        if(StringUtils.isBlank(request.getQuery())) return List.of();
        if(!CollectionUtils.isEmpty(request.getParams()) && request.getQuery().contains("?")) {
            int placeholderCount = StringUtils.countMatches(request.getQuery(), "?");
            if(placeholderCount != request.getParams().size()) {
                log.debug("invalid query {} and params", requestQuery);
                return List.of();
            }
            for(Object param : request.getParams()) {
                // escape special characters first
                String escapedParam = escape(param.toString());
                requestQuery = requestQuery.replaceFirst("\\?", escapedParam);
            }
        }
        String[] parts = StringUtils.normalizeSpace(requestQuery).split(" ");
        if(parts.length < 4) {
            log.error("Not able to identify entity for query : "+requestQuery);
            return List.of();
        }

        String entity = parts[3];
        List<Map<String, String>> items = executeQuery(requestQuery, request.getConnector());
        return toEntityData(request.getConnector().getId(), items, entity);
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        boolean enableSavedSearch = request.getConnector().getMetaConfig().containsKey(ENABLE_SAVED_SEARCH) ?
                (boolean) request.getConnector().getMetaConfig().get(ENABLE_SAVED_SEARCH) : false;
        if(request.getEntity().startsWith(SAVED_SEARCH_PREFIX) && enableSavedSearch) {
            return describeSavedSearch(request);
        }

        if(request.getEntity().equalsIgnoreCase("transactionline")) {
            EntitySchema entity = NetsuiteSeed.getTransactionLineSchema();
            return Optional.of(entity);
        }
        if (request.getEntity().equalsIgnoreCase(NetsuiteSeed.PICKLIST_VALUES_ENTITY)) {
            EntitySchema entity = NetsuiteSeed.getPicklistEntitySchema();
            return Optional.of(entity);
        }
        // initialize the custom record map
        BiMap<String, String> customRecordTypeEntities = populateCustomRecordTypeEntities(request.getConnector());
        Map<String, Reference> customFieldReferenceMap = buildCustomFieldReferences(request.getConnector(), customRecordTypeEntities.inverse());

        return describe(request, customRecordTypeEntities, customFieldReferenceMap);
    }

    private Optional<EntitySchema> describe(DescribeRequest request,
                                           BiMap<String, String> customRecordTypeEntities,
                                           Map<String, Reference> customFieldReferenceMap) {
        String entityName = request.getEntity();

        final AttributeSchema replaceSublistAttribute = new AttributeSchema(REPLACE_SUBLIST, "string").setDisplayName("Replace sublists")
                .setDescription("Replaces specified sublist fields during updates.You can add multiple fields separated by commas.  The \"items\" sublist on salesorders are always replaced, regardless of this setting.");
        if (supportedChildEntities.contains(entityName)) {
            final EntitySchema entitySchema = retrieveChildEntityByParent(request, entityName);
            entitySchema.addDestinationParam(replaceSublistAttribute);
            return Optional.of(entitySchema);
        }

        if ("file".equalsIgnoreCase(entityName)) {
            EntitySchema entity = new EntitySchema(request.getEntity(), StringUtils.capitalize(request.getEntity()));
            entity.addDestinationParam(replaceSublistAttribute);
            entity.addField(new AttributeSchema("attachFrom", "string").setDisplayName("Attach From"));
            entity.addField(new AttributeSchema("folder", "string").setDisplayName("Folder Id"));
            entity.addField(new AttributeSchema("fileType", "string").setDisplayName("File Type"));
            entity.addField(new AttributeSchema("fileSize", "double").setDisplayName("File Size"));
            entity.addField(new AttributeSchema("url", "string").setDisplayName("URL"));
            entity.addField(new AttributeSchema("description", "string").setDisplayName("Description"));
            entity.addField(new AttributeSchema("name", "string").setDisplayName("Name"));
            entity.addField(new AttributeSchema("ownerId", "string").setDisplayName("Owner"));
            entity.addField(new AttributeSchema("internalid", "string").setDisplayName("Id").setIdField(true));
            entity.addField(new AttributeSchema("lastModifiedDate", "datetime").setDisplayName("Last Modified Date").setWatermarkField(true));
            entity.addField(new AttributeSchema("createdDate", "datetime").setDisplayName("Created Date"));
            entity.addField(new AttributeSchema(EntityData.SYNCARI_FILE_LINK_FIELD_NAME, "filelink").setDisplayName("Syncari File Link"));
            entity.setReadOnly(true);
            return Optional.of(entity);
        } else if("paycheckjournal".equalsIgnoreCase(entityName)) {
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
        } else if("binworksheet".equalsIgnoreCase(entityName)) {
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

        String describeUrl = format(DESCRIBE_URL, getApiUrlPrefix(request.getConnector()), VERSION, entityName);

        EntitySchema entity = new EntitySchema(request.getEntity());
        entity.addDestinationParam(replaceSublistAttribute);
        try {
            log.info("Describe url: " + describeUrl);
            ConnectorInfo connector = request.getConnector();
            Map<String, HashMap<String, Object>> fields = getFieldsMap(entityName, describeUrl, connector);
            String entityDisplayName = customRecordTypeEntities.containsKey(request.getEntity().toLowerCase()) ?
                    customRecordTypeEntities.get(request.getEntity().toLowerCase()) :
                    StringUtils.capitalize(request.getEntity());

            entity.setDisplayName(entityDisplayName);
            entity.setCustom(customRecordTypeEntities.containsKey(request.getEntity().toLowerCase()));

            Map<String, Reference> fieldToReferenceMap = getFieldToReferenceMap(entityName);

            fields.forEach((apiName, v) -> {
                addFieldToEntity(entity, entityName, apiName, v, fieldToReferenceMap, customFieldReferenceMap);
            });
            if("opportunity".equals(entityName)){
                entity.addField(new AttributeSchema("contacts","reference")
                        .setStatus(Status.ACTIVE)
                        .setMultiValueField(true)
                        .setDisplayName("Contacts")
                        .setNillable(true)
                        .setReferenceTo("contact")
                        .setReferenceTargetField("id")
                );
            } else
            if("subsidiary".equals(entityName)){
                entity.addField(new AttributeSchema("fiscalCalendar","string")
                        .setStatus(Status.ACTIVE)
                        .setDisplayName("Fiscal Calendar")
                        .setNillable(true)
                );
            } else
            if ("salesorder".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("salesorderlineitems", "child").setDisplayName("Sales Order Line Items")
                    .setMultiValueField(true).setReferenceTo("salesorderlineitem"));
            } else
            if ("purchaseorder".equalsIgnoreCase(entity.getApiName())) {
                entity.setReadOnly(true);
                entity.addField(new AttributeSchema("purchaseorderlineitems", "child").setDisplayName("Purchase Order Line Items")
                        .setMultiValueField(true).setReferenceTo("purchaseorderlineitem"));
            } else
            if ("cashsale".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("cashsalelineitems", "child").setDisplayName("Cash Sale Line Items")
                        .setMultiValueField(true).setReferenceTo("cashsalelineitem"));
            } else
            if ("cashrefund".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("cashrefundlineitems", "child").setDisplayName("Cash Refund Line Items")
                        .setMultiValueField(true).setReferenceTo("cashrefundlineitem"));
            } else
            if ("creditmemo".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("creditmemolineitems", "child").setDisplayName("Credit Memo Line Items")
                        .setMultiValueField(true).setReferenceTo("creditmemolineitem"));
            } else
            if ("estimate".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("estimatelineitems", "child").setDisplayName("Estimate Line Items")
                        .setMultiValueField(true).setReferenceTo("estimatelineitem"));
            } else
            if ("invoice".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("invoicelineitems", "child").setDisplayName("Invoice Order Line Items")
                        .setMultiValueField(true).setReferenceTo("invoicelineitem"));
            } else
            if ("kititem".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("kititemmembers", "child").setDisplayName("Kit Item Members")
                        .setMultiValueField(true).setReferenceTo("kititemmember"));
                entity.removeField("member");
            } else
            if ("subscription".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("subscriptionlines", "child").setDisplayName("Subscription Line Items")
                        .setMultiValueField(true).setReferenceTo("subscriptionline"));
                entity.removeField("subscriptionLine");
                entity.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
                entity.addField(new AttributeSchema("priceintervals", "child").setDisplayName("Price Intervals")
                        .setMultiValueField(true).setReferenceTo("priceinterval"));
                entity.removeField("priceInterval");
            } else
            if ("subscriptionchangeorder".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("subscriptionchangeorderlines", "child").setDisplayName("Subscription Change Order Line Items")
                        .setMultiValueField(true).setReferenceTo("subscriptionchangeorderline"));
                entity.removeField("subLine");
                entity.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
            } else
            if ("subscriptionplan".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("subscriptionplanlines", "child").setDisplayName("Subscription Plan Line Items")
                        .setMultiValueField(true).setReferenceTo("subscriptionplanline"));
                entity.removeField("member");
            } else
            if ("billingschedule".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
            } else
            if ("pricebook".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
            } else
                // TODO: refactor and make this a list instead of repeating for each object.
            if ("priceplan".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
                entity.removeField("priceTiers");
                entity.addField(new AttributeSchema("pricetiers", "child").setDisplayName("Price Tiers")
                        .setMultiValueField(true).setReferenceTo("pricetier"));
            } else
            if ("subscriptionterm".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
            } else
            if ("campaign".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
            } else
            if ("contact".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("addr1","string").setDisplayName("Address 1"));
                entity.addField(new AttributeSchema("addr2","string").setDisplayName("Address 2"));
                entity.addField(new AttributeSchema("addr3","string").setDisplayName("Address 3"));
                entity.addField(new AttributeSchema("city","string").setDisplayName("City"));
                entity.addField(new AttributeSchema("state","string").setDisplayName("State"));
                entity.addField(new AttributeSchema("zip","string").setDisplayName("Zip"));
                entity.addField(new AttributeSchema("country","string").setDisplayName("Country"));
            } else
            if ("customer".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("altName", "string").setDisplayName("AltName"));
            } else if ("customerStatus".equalsIgnoreCase(entity.getApiName())) {
                entity.addField(new AttributeSchema("syncariUpdatedAt", "timestamp").setDisplayName("Syncari Updated At")
                        .setWatermarkField(true));
            }
            if (READ_ONLY_ENTITIES.contains(entityName)) {
                entity.setReadOnly(true);
            }
            if (TRANSACTION_ENTITIES.contains(entityName)) {
                entity.addField(new AttributeSchema(EntityData.SYNCARI_FILE_REFERENCE_FIELD_NAME,"reference")
                        .setStatus(Status.ACTIVE)
                        .setMultiValueField(true)
                        .setDisplayName("Files")
                        .setNillable(true)
                        .setReferenceTo("file")
                        .setReferenceTargetField("id")
                        .setSyncariDefined(true)
                );
            }
            addAddressFields(entity,fields);
        }  catch(NonRetriableException | RetriableException nex){
            throw nex;
        } catch(Exception e) {
            ExceptionUtils.printRootCauseStackTrace(e);
            log.error(format("Error message on describe: %s", e.getMessage()));
            ConnectorHelper.handleException(e);
        }
        AttributeSchema suiteQL = new AttributeSchema("suiteql_" + entity.getApiName(), "boolean");
        suiteQL.setDisplayName("Enable SuiteQL sync for source");
        entity.addSourceParam(suiteQL);
        return Optional.of(entity);
    }

    private Map<String, HashMap<String, Object>> getFieldsMap(String entityName, String describeUrl, ConnectorInfo connector) {
        AuthConfig auth = connector.getAuthConfig();
        NetSuiteRestClient restClient = getNetSuiteRestClient();
        restClient.addHeader("Accept", SCHEMA_JSON);
        ResponseEntity<String> reponseEntity = restClient.getResponse(describeUrl, auth);
        String body = reponseEntity.getBody();
        log.debug("Netsuite describe response for entity {} is {}", entityName, reponseEntity);
        DocumentContext ctx = JsonPath.parse(body);
        Map<String, HashMap<String, Object>> fields = ctx.read("properties");
        log.debug(fields.toString());
        return fields;
    }

    private void addFieldToEntity(EntitySchema entity, String entityName, String apiName, HashMap<String, Object> v,
                                  Map<String, Reference> fieldToReferenceMap, Map<String, Reference> customFieldReferenceMap) {
        AttributeSchema attr = null;
        boolean isReadonly = (Boolean) v.getOrDefault("readOnly", false);
        boolean isExplicitlyRequired = REQUIRED_FIELDS.getOrDefault(entityName, Set.of()).contains(apiName);
        Boolean isNullableInMetadata = (Boolean) v.getOrDefault("nullable", true);
        boolean isNillable = !isExplicitlyRequired && isNullableInMetadata;
        boolean isCustomField = v.containsKey("x-ns-custom-field") && (Boolean) v.get("x-ns-custom-field");
        /*
        "status": {
            "type": "object",
            "properties": {
                "id": {
                    "title": "Internal identifier",
                    "type": "string",
                    "enum": [
                        "A",
                        "B",
                        "D",
                        "E",
                        "V",
                        "Y"
                    ]
                },
                "refName": {
                    "title": "Reference Name",
                    "type": "string"
                }
            }
        },
         */

        if ((v.containsKey("title") || fieldToReferenceMap.containsKey(apiName)) && !"links".equalsIgnoreCase(apiName)) {
            //Reference fields don't have labels set in metatdata, so we'll use our internal reference lookup table for these
            String displayName = fieldToReferenceMap.containsKey(apiName) ? fieldToReferenceMap.get(apiName).getReferenceFieldLabel() :
                    v.getOrDefault("title", readableName(apiName)).toString();
            String dataType = resolveDataType(fieldToReferenceMap, apiName, v);

            List<String> picklistValues = List.of();
            if (v.containsKey("enum")) {
                dataType = "enumeration";
                picklistValues = (List<String>) v.get("enum");
            }
            boolean isUpdatedAt = entity.isCustom() ? "lastModified".equalsIgnoreCase(apiName) : "lastModifiedDate".equalsIgnoreCase(apiName);
            boolean isCreatedAt = "createdDate".equalsIgnoreCase(apiName);
            boolean isWatermarkField = isUpdatedAt;

            if (entity.isCustom() && (isCreatedAt || isUpdatedAt || isWatermarkField )){
                isReadonly = true;
            }

            Optional<Reference> reference = Optional.ofNullable(fieldToReferenceMap.get(apiName));
            log.debug("Adding field display name: " + displayName + ", type: " + dataType);
            attr = createAttr(entity, apiName, displayName, dataType, picklistValues, isCustomField, isNillable,
                    reference, isReadonly, isUpdatedAt, isCreatedAt, isWatermarkField);
        } else if ("object".equalsIgnoreCase(v.get("type").toString())) {
            // It could be reference or embedded. All non standard references are marked polymorphic
            Map<String, Map<String, Object>> properties = (Map) v.get("properties");
            if ("journalentry".equalsIgnoreCase(entityName) && "line".equalsIgnoreCase(apiName)) {
                addJournalLineFields(entity, apiName, properties);

            }else if(isPolymorphic(properties)) {
                if (entity.isCustom() && customFieldReferenceMap.containsKey(apiName)){
                    // This is a custom object reference. All fields in custom objects will be custom fields
                    // Get the object reference name doing a custom field lookup
                    // This logic dont work for standard object
                    Reference reference = customFieldReferenceMap.get(apiName);
                    attr = createAttr(entity, apiName, reference.referenceFieldLabel, "reference", null, isCustomField, isNillable,
                            Optional.ofNullable(reference), isReadonly, false, false, false);
                } else {
                    // it is a reference, else its an embedded object. This is ugly :( but there is no better way
                    // to detect this from the Netsuite api at the moment
                    attr = createAttr(entity, apiName, apiName, "polymorphicreference", null, isCustomField, isNillable,
                            Optional.empty(), isReadonly, false, false, false);
                }
            } else {
                if(properties.containsKey("id")){
                    Map<String, Map<String, Object>> idProperties = (Map) properties.get("id");
                    //handle enum types
                    if(idProperties.containsKey("enum")){
                        List<String> enumValues = (List<String>) idProperties.get("enum");
                        attr = createAttr(entity, apiName, apiName, "picklist", enumValues, isCustomField, isNillable,
                                Optional.empty(), isReadonly, false, false, false);
                    }else{
                        // TODO handle this later (when we support OCR)
                    }
                }
            }
        }
        if(attr != null) {
            // determine if the field type is a list
            if(v.containsKey("properties")) {
                Map props = (Map)v.get("properties");
                if(props.containsKey("hasMore") && props.containsKey("items")) {
                    attr.setMultiValueField(true);
                }
            }
            entity.addField(attr);
        }
    }

    private void addAddressFields(EntitySchema entity, Map<String, HashMap<String, Object>> fields) {
        if (fields.containsKey("billingAddress") || fields.containsKey("addressBook") || fields.containsKey("billAddress")) {
            entity.addField(new AttributeSchema("billingAddress_attention","string").setDisplayName("Billing Address: Attention"));
            entity.addField(new AttributeSchema("billingAddress_addressee","string").setDisplayName("Billing Address: Addressee"));
            entity.addField(new AttributeSchema("billingAddress_addr1","string").setDisplayName("Billing Address: Address 1"));
            entity.addField(new AttributeSchema("billingAddress_addr2","string").setDisplayName("Billing Address: Address 2"));
            entity.addField(new AttributeSchema("billingAddress_addr3","string").setDisplayName("Billing Address: Address 3"));
            entity.addField(new AttributeSchema("billingAddress_addrText","string").setDisplayName("Billing Address Text"));
            entity.addField(new AttributeSchema("billingAddress_addrphone","string").setDisplayName("Billing Address: Phone"));
            entity.addField(new AttributeSchema("billingAddress_city","string").setDisplayName("Billing Address: City"));
            entity.addField(new AttributeSchema("billingAddress_state","string").setDisplayName("Billing Address: State"));
            entity.addField(new AttributeSchema("billingAddress_zip","string").setDisplayName("Billing Address: Zip"));
            entity.addField(new AttributeSchema("billingAddress_country","string").setDisplayName("Billing Address: Country"));
            entity.addField(new AttributeSchema("billingAddress_id","string").setDisplayName("Billing Address: ID"));
        }
        if (fields.containsKey("shippingAddress") || fields.containsKey("addressBook") || fields.containsKey("shipAddress")) {
            entity.addField(new AttributeSchema("shippingAddress_attention","string").setDisplayName("Shipping Address: Attention"));
            entity.addField(new AttributeSchema("shippingAddress_addressee","string").setDisplayName("Shipping Address: Addressee"));
            entity.addField(new AttributeSchema("shippingAddress_addr1","string").setDisplayName("Shipping Address: Address 1"));
            entity.addField(new AttributeSchema("shippingAddress_addr2","string").setDisplayName("Shipping Address: Address 2"));
            entity.addField(new AttributeSchema("shippingAddress_addr3","string").setDisplayName("Shipping Address: Address 3"));
            entity.addField(new AttributeSchema("shippingAddress_addrText","string").setDisplayName("Shipping Address Text"));
            entity.addField(new AttributeSchema("shippingAddress_addrphone","string").setDisplayName("Shipping Address: Phone"));
            entity.addField(new AttributeSchema("shippingAddress_city","string").setDisplayName("Shipping Address: City"));
            entity.addField(new AttributeSchema("shippingAddress_state","string").setDisplayName("Shipping Address: State"));
            entity.addField(new AttributeSchema("shippingAddress_zip","string").setDisplayName("Shipping Address: Zip"));
            entity.addField(new AttributeSchema("shippingAddress_country","string").setDisplayName("Shipping Address: Country"));
            entity.addField(new AttributeSchema("shippingAddress_id","string").setDisplayName("Shipping Address: ID"));
        }
        if (fields.containsKey("addressBook")) {
            entity.addField(new AttributeSchema("billingAddress_label","string").setDisplayName("Billing Address: Label"));
            entity.addField(new AttributeSchema("billingAddress_isResidential","boolean").setDisplayName("Billing Address: Is Residential"));
            entity.addField(new AttributeSchema("shippingAddress_label","string").setDisplayName("Shipping Address: Label"));
            entity.addField(new AttributeSchema("shippingAddress_isResidential","boolean").setDisplayName("Shipping Address: Is Residential"));
        }
    }

    protected Map<String, Object> toAddressBook(Map<String,Object> entityData){
        Map<String, Object> addressBook = new HashMap<>();
        List<Map<String, Object>> addresses = new ArrayList<>();
        Map<String, Object> billingAddress = createAddress(entityData, "billingAddress",Optional.empty());
        if(!billingAddress.isEmpty()) {
            billingAddress.put("defaultBilling",true);
            addresses.add(billingAddress);
        }
        Map<String, Object> shippingAddress = createAddress(entityData, "shippingAddress",Optional.empty());
        if(!shippingAddress.isEmpty()) {
            shippingAddress.put("defaultShipping",true);
            addresses.add(shippingAddress);
        } else if (!billingAddress.isEmpty()) {
            // TODO: If shipping address is not provided, we mark the billing address as `defaultShipping:true`.
            // Ideally this should be a config/another flag that represents this.
            billingAddress.put("defaultShipping", true);
        }
        if(!addresses.isEmpty()) {
            addressBook.put("items",addresses);
        }
        return addressBook;
    }

    private Map<String, Object> createAddress(Map<String, Object> entityData, String addressTypePrefix,Optional<String> id) {
        Map<String, Object> address = new HashMap<>();
        Map<String, Object> addressDetails = new HashMap<>();
        if(entityData.get(addressTypePrefix+"_attention")!=null) addressDetails.put("attention", entityData.get(addressTypePrefix+"_attention"));
        if(entityData.get(addressTypePrefix+"_addressee")!=null) addressDetails.put("addressee", entityData.get(addressTypePrefix+"_addressee"));
        if(entityData.get(addressTypePrefix+"_addr1")!=null) addressDetails.put("addr1", entityData.get(addressTypePrefix+"_addr1"));
        if(entityData.get(addressTypePrefix+"_addr2")!=null) addressDetails.put("addr2", entityData.get(addressTypePrefix+"_addr2"));
        if(entityData.get(addressTypePrefix+"_addr3")!=null) addressDetails.put("addr3", entityData.get(addressTypePrefix+"_addr3"));
        if(entityData.get(addressTypePrefix+"_addrText")!=null) addressDetails.put("addrText", entityData.get(addressTypePrefix+"_addrText"));
        if(entityData.get(addressTypePrefix+"_addrphone")!=null) addressDetails.put("addrphone", entityData.get(addressTypePrefix+"_addrphone"));
        if(entityData.get(addressTypePrefix+"_city")!=null) addressDetails.put("city", entityData.get(addressTypePrefix+"_city"));
        if(entityData.get(addressTypePrefix+"_state")!=null) addressDetails.put("state", entityData.get(addressTypePrefix+"_state"));
        if(entityData.get(addressTypePrefix+"_country")!=null) addressDetails.put("country", entityData.get(addressTypePrefix+"_country"));
        if(entityData.get(addressTypePrefix+"_zip")!=null) addressDetails.put("zip", entityData.get(addressTypePrefix+"_zip"));
        if(entityData.get(addressTypePrefix+"_isResidential")!=null) address.put("isResidential", entityData.get(addressTypePrefix+"_isResidential"));
        if(entityData.get(addressTypePrefix+"_label")!=null) address.put("label", entityData.get(addressTypePrefix+"_label"));
        if(!addressDetails.isEmpty()) address.put("addressBookAddress",addressDetails);
        id.ifPresent(idString-> address.put("internalId",Long.valueOf(idString)));
        return address;
    }

    public EntitySchema retrieveChildEntityByParent(DescribeRequest request, String entityName) {
        if (!CHILD_PARENT_ENTITY_MAP.containsKey(entityName))
            throw new RuntimeException(String.format("Unsupported child entity %s, no parent found.", entityName));
        String parentEntityName = CHILD_PARENT_ENTITY_MAP.get(entityName);
        String describeUrl = format(DESCRIBE_URL, getApiUrlPrefix(request.getConnector()), VERSION, parentEntityName);
        Map<String, HashMap<String, Object>> parentFields = getFieldsMap(entityName, describeUrl, request.getConnector());

        switch (entityName) {
            case "salesorderlineitem":
                Map<String, Object> itemSalesOrderField = parentFields.get("item");
                Map<String, Map<String, Object>> properties = (Map) itemSalesOrderField.get("properties");
                return addLineFields(properties, "salesorderlineitem", "Sales Order Line Item", "salesorderid", "Sales Order ID", "salesorder");
            case "purchaseorderlineitem":
                Map<String, Object> itemPurchaseOrderField = parentFields.get("item");
                properties = (Map) itemPurchaseOrderField.get("properties");
                return addLineFields(properties, "purchaseorderlineitem", "Purchase Order Line Item", "purchaseorderid", "Purchase Order ID", "purchaseorder");
            case "cashsalelineitem":
                Map<String, Object> itemCashSalerField = parentFields.get("item");
                properties = (Map) itemCashSalerField.get("properties");
                return addLineFields(properties, "cashsalelineitem", "Cash Sale Line Item", "cashsaleid", "Cash Sale ID", "cashsale");
            case "cashrefundlineitem":
                Map<String, Object> itemCashRefunderField = parentFields.get("item");
                properties = (Map) itemCashRefunderField.get("properties");
                return addLineFields(properties, "cashrefundlineitem", "Cash Refund Line Item", "cashrefundid", "Cash Refund ID", "cashrefund");
            case "creditmemolineitem":
                Map<String, Object> itemCreditMemoField = parentFields.get("item");
                properties = (Map) itemCreditMemoField.get("properties");
                return addLineFields(properties, "creditmemolineitem", "Credit MemoLine Item", "creditmemoid", "Credit Memo ID", "creditmemo");
            case "estimatelineitem": 
                Map<String, Object> itemEstimateField = parentFields.get("item");
                Map<String, Map<String, Object>> estimateProps = (Map) itemEstimateField.get("properties");
                return addEstimateLineFields(estimateProps);
            case "invoicelineitem":
                Map<String, Object> itemInvoiceField = parentFields.get("item");
                Map<String, Map<String, Object>> itemProps = (Map) itemInvoiceField.get("properties");
                return addInvoiceLineFields(itemProps);
            case "customerpaymentlineitem":
                Map<String, Object> itemPayField = parentFields.get("apply");
                Map<String, Map<String, Object>> payProps = (Map) itemPayField.get("properties");
                return addPaymentLineItems(payProps);
            case "subscriptionline":
                Map<String, Object> itemSubscriptionField = parentFields.get("subscriptionLine");
                Map<String, Map<String, Object>> itemSubscriptionProps = (Map) itemSubscriptionField.get("properties");
                return addSubscriptionLineFields(itemSubscriptionProps);
            case "priceinterval":
                Map<String, Object> priceIntervalField = parentFields.get("priceInterval");
                Map<String, Map<String, Object>> priceIntervalProps = (Map) priceIntervalField.get("properties");
                return addPriceIntervalFields(priceIntervalProps);
            case "pricetier":
                Map<String, Object> priceTierField = parentFields.get("priceTiers");
                Map<String, Map<String, Object>> priceTierProps = (Map) priceTierField.get("properties");
                return addPriceTierFields(priceTierProps);
            case "subscriptionchangeorderline":
                Map<String, Object> itemSubLineField = parentFields.get("subLine");
                Map<String, Map<String, Object>> itemSubLineProps = (Map) itemSubLineField.get("properties");
                return addSubscriptionChangeOrderLineFields(itemSubLineProps);
            case "subscriptionplanline":
                Map<String, Object> itemMemberField = parentFields.get("member");
                Map<String, Map<String, Object>> itemMemberProps = (Map) itemMemberField.get("properties");
                return addSubscriptionPlanLineFields(itemMemberProps);
            case "kititemmember":
                Map<String, Object> kititemMemberField = parentFields.get("member");
                Map<String, Map<String, Object>> kititemMemberProps = (Map) kititemMemberField.get("properties");
                return addKititemMemberFields(kititemMemberProps);
            default:
                throw new RuntimeException(String.format("Unsupported child entity %s, no handler found.", entityName));
        }
    }

    public EntitySchema addEstimateLineFields(Map<String, Map<String, Object>> properties) {
        EntitySchema schema = new EntitySchema("estimatelineitem", "Estimate Line Item");
        schema.setChild(true);
        schema.setReadOnly(true);
        if (properties.containsKey("items")) {
            Map<String, Object> lineProperties = ConnectorHelper.getNestedMap(properties.get("items"),"items.properties");
            Map<String, Reference> fieldToReferenceMap = getFieldToReferenceMap("estimatelineitem");
            lineProperties.forEach((lineApiName,lineAttributes)-> {
                HashMap<String, Object> props = (HashMap<String, Object>) lineAttributes;
                log.debug("Adding Estimate Field {} with properties {} " , lineApiName, props );
                addFieldToEntity(schema, "estimatelineitem", lineApiName, props, fieldToReferenceMap, null);
            });
            schema.addField(new AttributeSchema("estimateid","reference").setStatus(Status.ACTIVE).setReferenceTo("estimate").setReferenceTargetField("id").setDisplayName("Estimate ID").setCreateOnly(true));
            schema.addField(new AttributeSchema("id","string").setSystem(true).setIdField(true).setStatus(Status.ACTIVE).setDisplayName("Internal ID"));
            schema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
        }
        addLegacyTaxFields(schema);
        return schema;
    }

    public EntitySchema addLineFields(Map<String, Map<String, Object>> properties, String apiName, String displayName,
                                              String idApiName, String idDisplayName, String entityApiName) {
        EntitySchema schema = new EntitySchema(apiName, displayName);
        schema.setChild(true);
        schema.setReadOnly(true);
        if (properties.containsKey("items")) {
            Map<String, Object> lineProperties = ConnectorHelper.getNestedMap(properties.get("items"),"items.properties");
            Map<String, Reference> fieldToReferenceMap = getFieldToReferenceMap(apiName);
            lineProperties.forEach((lineApiName,lineAttributes)-> {
                HashMap<String, Object> props = (HashMap<String, Object>) lineAttributes;
                log.debug("Adding {} Field {} with properties {} " , apiName, lineApiName, props );
                addFieldToEntity(schema, apiName, lineApiName, props, fieldToReferenceMap, null);
            });
            schema.addField(new AttributeSchema(idApiName,"reference").setStatus(Status.ACTIVE).setReferenceTo(entityApiName).setReferenceTargetField("id").setDisplayName(idDisplayName).setCreateOnly(true));
            schema.addField(new AttributeSchema("id","string").setSystem(true).setIdField(true).setStatus(Status.ACTIVE).setDisplayName("Internal ID"));
            schema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
        }

        if(LEGACY_TAX_SUPPORTED_ENTITIES.contains(schema.getApiName())) {
            addLegacyTaxFields(schema);
        }
        return schema;
    }
    public EntitySchema addInvoiceLineFields(Map<String, Map<String, Object>> properties) {
        EntitySchema schema = new EntitySchema("invoicelineitem", "Invoice Line Item");
        schema.setChild(true);
        if (properties.containsKey("items")) {
            Map<String, Object> lineProperties = ConnectorHelper.getNestedMap(properties.get("items"),"items.properties");
            Map<String, Reference> fieldToReferenceMap = getFieldToReferenceMap("invoicelineitem");
            lineProperties.forEach((lineApiName,lineAttributes)-> {
                HashMap<String, Object> props = (HashMap<String, Object>) lineAttributes;
                log.info("Adding Invoice Field {} with properties {} " , lineApiName, props );
                //schema.addField(field);
                addFieldToEntity(schema, "invoicelineitem", lineApiName, props, fieldToReferenceMap, null);
            });
            //add the invoice line item field
            schema.addField(new AttributeSchema("invoiceid","reference")
                    .setStatus(Status.ACTIVE)
                    .setReferenceTo("invoice").setReferenceTargetField("id").setDisplayName("Invoice ID").setCreateOnly(true));
            schema.addField(new AttributeSchema("id","string").setSystem(true).setIdField(true).setStatus(Status.ACTIVE).setDisplayName("Internal ID"));
            schema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
        }
        addLegacyTaxFields(schema);
        return schema;
    }

    public EntitySchema addPaymentLineItems(Map<String, Map<String, Object>> properties) {
        EntitySchema schema = new EntitySchema("customerpaymentlineitem", "Pay Line Item");
        schema.setChild(true);
        if (properties.containsKey("items")) {
            Map<String, Object> lineProperties = ConnectorHelper.getNestedMap(properties.get("items"),"items.properties");
            Map<String, Reference> fieldToReferenceMap = getFieldToReferenceMap("customerpaymentlineitem");
            lineProperties.forEach((lineApiName,lineAttributes)-> {
                HashMap<String, Object> props = (HashMap<String, Object>) lineAttributes;
                log.info("Adding Payment Field {} with properties {} " , lineApiName, props );
                //schema.addField(field);
                addFieldToEntity(schema, "customerpaymentlineitem", lineApiName, props, fieldToReferenceMap, null);
            });
            //add the invoice line item field
            schema.addField(new AttributeSchema("customerpaymenttid","reference")
                    .setStatus(Status.ACTIVE)
                    .setReferenceTo("customerpayment").setReferenceTargetField("id").setDisplayName("Payment ID").setCreateOnly(true));
            schema.addField(new AttributeSchema("id","string").setSystem(true).setIdField(true).setStatus(Status.ACTIVE).setDisplayName("Internal ID"));
            schema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
            schema.addField(new AttributeSchema("invoiceId", "reference").setReferenceTargetField("id").setDisplayName("Invoice ID").setReferenceTo("invoice"));
        }
        return schema;
    }
    public void addJournalLineFields(EntitySchema entity, String apiName, Map<String, Map<String, Object>> properties) {
        Map<String, Object> lineProperties = ConnectorHelper.getNestedMap(properties.get("items"),"items.properties");
        lineProperties.forEach((lineApiName,lineAttributes)->{
            //Skip debit field, because we are adding an "amount" field to both credit & debit lines explicitly
          if(!"links".equalsIgnoreCase(lineApiName) && !"debit".equalsIgnoreCase(lineApiName)){
              Map<String, Object> props = (Map<String, Object>) lineAttributes;
              boolean isReference = "object".equalsIgnoreCase(props.get("type").toString());
              Reference ref = null;
              String derivedApiName = "credit".equalsIgnoreCase(lineApiName) ? "amount" : lineApiName;

              log.info("Adding Journal Field {} ",derivedApiName);
              if(isReference){
                  ref = JOURNAL_LINE_REFERENCES.get(lineApiName);
                  String dataType = ref != null ? "reference" : "polymorphicreference";
                  String title = ref != null ? ref.getReferenceFieldLabel() : StringUtils.capitalize(lineApiName);
                  entity.addField(createAttr(entity, "__credit_" + derivedApiName, "Credit Line :" + title, dataType, List.of(), Boolean.class.cast(props.getOrDefault("x-ns-custom-field", false)),
                              Boolean.class.cast(props.getOrDefault("nullable",true)),
                              Optional.ofNullable(ref), Boolean.class.cast(props.getOrDefault("readOnly",false)), false, false, false));
                  entity.addField(createAttr(entity, "__debit_" + derivedApiName, "Debit Line :" + title, dataType, List.of(), Boolean.class.cast(props.getOrDefault("x-ns-custom-field", false)),
                              Boolean.class.cast(props.getOrDefault("nullable",true)),
                              Optional.ofNullable(ref), Boolean.class.cast(props.getOrDefault("readOnly",false)), false, false, false));
              }else{
                  String title = "credit".equalsIgnoreCase(lineApiName) ? "Amount" : props.getOrDefault("title",StringUtils.capitalize(lineApiName)).toString();
                  log.info("Adding Simple Journal Field with title {} ",title);
                  var creditLineAttr = createAttr(entity, "__credit_" + derivedApiName, "Credit Line :" + title, props.get("type").toString(), List.of(), Boolean.class.cast(props.getOrDefault("x-ns-custom-field", false)),
                          Boolean.class.cast(props.getOrDefault("nullable",true)),
                          Optional.ofNullable(ref), Boolean.class.cast(props.getOrDefault("readOnly",false)), false, false, false);
                  var debitLineAttr = createAttr(entity, "__debit_" + derivedApiName, "Debit Line :" + title, props.get("type").toString(), List.of(), Boolean.class.cast(props.getOrDefault("x-ns-custom-field", false)),
                          Boolean.class.cast(props.getOrDefault("nullable",true)),
                          Optional.ofNullable(ref), Boolean.class.cast(props.getOrDefault("readOnly",false)), false, false, false);

                  entity.addField(creditLineAttr);
                  entity.addField(debitLineAttr);
              }
          }
        });
    }

    public EntitySchema addPriceIntervalFields(Map<String, Map<String, Object>> properties) {
        EntitySchema schema = new EntitySchema("priceinterval", "Price Interval");
        schema.setChild(true);
        schema.setReadOnly(true);
        if (properties.containsKey("items")) {
            Map<String, Object> lineProperties = ConnectorHelper.getNestedMap(properties.get("items"),"items.properties");
            Map<String, Reference> fieldToReferenceMap = getFieldToReferenceMap("priceinterval");
            lineProperties.forEach((lineApiName,lineAttributes)-> {
                HashMap<String, Object> props = (HashMap<String, Object>) lineAttributes;
                log.debug("Adding Price Interval Field {} with properties {} " , lineApiName, props );
                //schema.addField(field);
                addFieldToEntity(schema, "priceinterval", lineApiName, props, fieldToReferenceMap, null);
            });
            schema.addField(new AttributeSchema("subscriptionid","reference").setStatus(Status.ACTIVE).setReferenceTo("subscription").setReferenceTargetField("id").setDisplayName("Subscription ID").setCreateOnly(true));
            schema.addField(new AttributeSchema("id","string").setSystem(true).setIdField(true).setStatus(Status.ACTIVE).setDisplayName("Internal ID"));
            schema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
            schema.addField(new AttributeSchema("subscriptionPlanLineNumber", "integer").setDisplayName("Subscription Plan Line Number").setStatus(Status.ACTIVE));
        }

        return schema;
    }

    public EntitySchema addPriceTierFields(Map<String, Map<String, Object>> properties) {
        EntitySchema schema = new EntitySchema("pricetier", "Price Tier");
        schema.setChild(true);
        schema.setReadOnly(true);
        if (properties.containsKey("items")) {
            Map<String, Object> lineProperties = ConnectorHelper.getNestedMap(properties.get("items"),"items.properties");
            Map<String, Reference> fieldToReferenceMap = getFieldToReferenceMap("pricetier");
            lineProperties.forEach((lineApiName,lineAttributes)-> {
                HashMap<String, Object> props = (HashMap<String, Object>) lineAttributes;
                log.debug("Adding Price Tier Field {} with properties {} " , lineApiName, props );
                //schema.addField(field);
                addFieldToEntity(schema, "pricetier", lineApiName, props, fieldToReferenceMap, null);
            });
            schema.addField(new AttributeSchema("priceplanid","reference").setStatus(Status.ACTIVE).setReferenceTo("priceplan").setReferenceTargetField("id").setDisplayName("Price Plan ID").setCreateOnly(true));
            schema.addField(new AttributeSchema("id","string").setSystem(true).setIdField(true).setStatus(Status.ACTIVE).setDisplayName("Internal ID"));
            schema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
        }

        return schema;
    }

    public EntitySchema addSubscriptionLineFields(Map<String, Map<String, Object>> properties) {
        EntitySchema schema = new EntitySchema("subscriptionline", "Subscription Line Item");
        schema.setChild(true);
        schema.setReadOnly(true);
        if (properties.containsKey("items")) {
            Map<String, Object> lineProperties = ConnectorHelper.getNestedMap(properties.get("items"),"items.properties");
            Map<String, Reference> fieldToReferenceMap = getFieldToReferenceMap("subscriptionline");
            lineProperties.forEach((lineApiName,lineAttributes)-> {
                HashMap<String, Object> props = (HashMap<String, Object>) lineAttributes;
                log.debug("Adding Subscription Field {} with properties {} " , lineApiName, props );
                //schema.addField(field);
                addFieldToEntity(schema, "subscriptionline", lineApiName, props, fieldToReferenceMap, null);
            });
            schema.addField(new AttributeSchema("subscriptionid","reference").setStatus(Status.ACTIVE).setReferenceTo("subscription").setReferenceTargetField("id").setDisplayName("Subscription ID").setCreateOnly(true));
            schema.addField(new AttributeSchema("id","string").setSystem(true).setIdField(true).setStatus(Status.ACTIVE).setDisplayName("Internal ID"));
            schema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
        }

        return schema;
    }

    public EntitySchema addSubscriptionChangeOrderLineFields(Map<String, Map<String, Object>> properties) {
        EntitySchema schema = new EntitySchema("subscriptionchangeorderline", "Subscription Change Order Line Item");
        schema.setChild(true);
        schema.setReadOnly(true);
        if (properties.containsKey("items")) {
            Map<String, Object> lineProperties = ConnectorHelper.getNestedMap(properties.get("items"),"items.properties");
            Map<String, Reference> fieldToReferenceMap = getFieldToReferenceMap("subscriptionchangeorderline");
            lineProperties.forEach((lineApiName,lineAttributes)-> {
                HashMap<String, Object> props = (HashMap<String, Object>) lineAttributes;
                log.debug("Adding Sub Field {} with properties {} " , lineApiName, props );
                //schema.addField(field);
                addFieldToEntity(schema, "subscriptionchangeorderline", lineApiName, props, fieldToReferenceMap, null);
            });
            schema.addField(new AttributeSchema("subscriptionchangeorderid","reference").setStatus(Status.ACTIVE).setReferenceTo("subscriptionchangeorder").setReferenceTargetField("id").setDisplayName("Subscription Change Order ID").setCreateOnly(true));
            schema.addField(new AttributeSchema("id","string").setSystem(true).setIdField(true).setStatus(Status.ACTIVE).setDisplayName("Internal ID"));
            schema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
        }

        return schema;
    }

    private EntitySchema addSubscriptionPlanLineFields(Map<String, Map<String, Object>> properties) {
        EntitySchema schema = new EntitySchema("subscriptionplanline", "Subscription Plan Line Item");
        schema.setChild(true);
        schema.setReadOnly(true);
        if (properties.containsKey("items")) {
            Map<String, Object> lineProperties = ConnectorHelper.getNestedMap(properties.get("items"),"items.properties");
            Map<String, Reference> fieldToReferenceMap = getFieldToReferenceMap("subscriptionplanline");
            lineProperties.forEach((lineApiName,lineAttributes)-> {
                HashMap<String, Object> props = (HashMap<String, Object>) lineAttributes;
                log.debug("Adding Sub Field {} with properties {} " , lineApiName, props );
                //schema.addField(field);
                addFieldToEntity(schema, "subscriptionplanline", lineApiName, props, fieldToReferenceMap, null);
            });
            schema.addField(new AttributeSchema("subscriptionplanid","reference").setStatus(Status.ACTIVE).setReferenceTo("subscriptionplan").setReferenceTargetField("id").setDisplayName("Subscription Plan ID").setCreateOnly(true));
            schema.addField(new AttributeSchema("id","string").setSystem(true).setIdField(true).setStatus(Status.ACTIVE).setDisplayName("Internal ID"));
            schema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
        }

        return schema;
    }

    private EntitySchema addKititemMemberFields(Map<String, Map<String, Object>> properties) {
        EntitySchema schema = new EntitySchema("kititemmember", "Kit Item Member");
        schema.setChild(true);
        schema.setReadOnly(true);
        if (properties.containsKey("items")) {
            Map<String, Object> lineProperties = ConnectorHelper.getNestedMap(properties.get("items"),"items.properties");
            Map<String, Reference> fieldToReferenceMap = getFieldToReferenceMap("kititem");
            lineProperties.forEach((lineApiName,lineAttributes)-> {
                HashMap<String, Object> props = (HashMap<String, Object>) lineAttributes;
                log.debug("Adding Sub Field {} with properties {} " , lineApiName, props );
                //schema.addField(field);
                addFieldToEntity(schema, "kititemmember", lineApiName, props, fieldToReferenceMap, null);
            });
            schema.addField(new AttributeSchema("kititemid","reference").setStatus(Status.ACTIVE).setReferenceTo("kititem").setReferenceTargetField("id").setDisplayName("Kititem ID").setCreateOnly(true));
            schema.addField(new AttributeSchema("id","string").setSystem(true).setIdField(true).setStatus(Status.ACTIVE).setDisplayName("Internal ID"));
            schema.addField(new AttributeSchema("lastModifiedDate", "datetime").setWatermarkField(true).setSystem(true).setStatus(Status.ACTIVE).setDisplayName("Last Modified Date"));
        }

        return schema;
    }

    private void addLegacyTaxFields(EntitySchema entitySchema) {
        if(LEGACY_TAX_SUPPORTED_ENTITIES.contains(entitySchema.getApiName())) {
            entitySchema.addField(new AttributeSchema("taxCode","string").setStatus(Status.ACTIVE).setDisplayName("Tax Code (Legacy Tax)"));
            entitySchema.addField(new AttributeSchema("taxRate1","double").setStatus(Status.ACTIVE).setDisplayName("Tax Rate 1 (Legacy Tax)"));
            entitySchema.addField(new AttributeSchema("taxRate2","double").setStatus(Status.ACTIVE).setDisplayName("Tax Rate 2 (Legacy Tax)"));
            entitySchema.addField(new AttributeSchema("tax1Amt","double").setStatus(Status.ACTIVE).setDisplayName("Tax 1 Amount (Legacy Tax)"));
            entitySchema.addField(new AttributeSchema("taxAmount","double").setStatus(Status.ACTIVE).setDisplayName("Tax Amount (Legacy Tax)"));
        }
    }

    private boolean isPolymorphic(Map<String, Map<String, Object>> properties) {
        return (properties.size() == 4 && properties.keySet().containsAll(REF_KEYS))
                || (properties.size() == 6 && properties.keySet().containsAll(EMBEDDED_REF_KEYS));
    }

    private String resolveDataType(Map<String, Reference> fieldToReferenceMap, String apiName, HashMap<String, Object> v) {
        if (fieldToReferenceMap.containsKey(apiName)) {
            return "reference";
        } else {
            //JSON Schema allows 'format' fields to narrow down string datatypes
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

    private String readableName(String apiName) {
        String[] splits = StringUtils.splitByCharacterTypeCamelCase(apiName);
        List<String> capitalizedSplits = Arrays.asList(splits).stream().map(s -> StringUtils.capitalize(s)).collect(Collectors.toList());
        return StringUtils.join(capitalizedSplits, " ");
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        boolean enableSavedSearch = request.getConnector().getMetaConfig().containsKey(ENABLE_SAVED_SEARCH) ?
                (boolean) request.getConnector().getMetaConfig().get(ENABLE_SAVED_SEARCH) : false;
        List<EntitySchema> objects = new ArrayList<>();

        // initialize the custom record map
        BiMap<String, String> customRecordTypeEntities = populateCustomRecordTypeEntities(request.getConnector());
        Map<String, Reference> customFieldReferenceMap = buildCustomFieldReferences(request.getConnector(), customRecordTypeEntities.inverse());

        Set<String> requestedEntities = getRequestedEntities(request, customRecordTypeEntities);
        String savedSearchesConfig = request.getConnector().getMetaConfig().getOrDefault(SAVED_SEARCHES_LIST, "").toString();
        Set<String> savedSearchesList = StringUtils.isNotBlank(savedSearchesConfig) ? Arrays.stream(savedSearchesConfig.split(",")).map(String::trim).collect(Collectors.toSet()) : Set.of();
        requestedEntities.forEach(e -> {
            if (supportedEntities.contains(e) || supportedChildEntities.contains(e) || customRecordTypeEntities.keySet().contains(e)) {
                DescribeRequest req = new DescribeRequest(request.getConnector(), e);
                objects.add(describe(req, customRecordTypeEntities, customFieldReferenceMap).get());
            }
            if(e.startsWith(SAVED_SEARCH_PREFIX) && enableSavedSearch && (savedSearchesList.isEmpty() || savedSearchesList.contains(e.substring(SAVED_SEARCH_PREFIX.length())))) {
                DescribeAllRequest describeAllRequest = new DescribeAllRequest(request.getConnector(), List.of(e));
                List<EntitySchema> result = describeAllSavedSearches(describeAllRequest, savedSearchesList);
                objects.add(result.stream().findAny().get());
            }
        });
        if (CollectionUtils.isEmpty(request.getEntities()) && enableSavedSearch) {
            objects.addAll(describeAllSavedSearches(request, savedSearchesList));
        }
        if (CollectionUtils.isEmpty(request.getEntities())) {
            EntitySchema transactionLine = NetsuiteSeed.getTransactionLineSchema();
            objects.add(transactionLine);
            objects.add(NetsuiteSeed.getPicklistEntitySchema());
        }
        return objects;
    }

    private Set<String> getRequestedEntities(DescribeAllRequest request, BiMap<String, String> customRecordTypeEntities) {
        if (request.getEntities() == null || request.getEntities().isEmpty()) {
            Set<String> allEntities = new HashSet<String>();
            allEntities.addAll(supportedEntities);
            allEntities.addAll(supportedChildEntities);
            allEntities.addAll(customRecordTypeEntities.keySet());
            return allEntities;
        } else {
            return new LinkedHashSet(request.getEntities());
        }
    }

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
            //retain original datatype in additionProps
            entity.getAdditionalProperties().putIfAbsent("attributeProperties", new HashMap<>());
            final Map<Object, Object> attributeProperties = (Map<Object, Object>) entity.getAdditionalProperties().get("attributeProperties");
            attributeProperties.put(attr.getApiName(), Map.of("originalType", "polymorphicreference"));
        }
        return attr;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        boolean enableSuiteQLSync = request.getConnector().getMetaConfig().containsKey(ENABLE_SUITEQL_SYNC) ?
                (boolean) request.getConnector().getMetaConfig().get(ENABLE_SUITEQL_SYNC) : false;
        boolean enableSuiteQLSyncPerEntity = Boolean.parseBoolean(
                String.valueOf(request.getAdditionalParams().getOrDefault("suiteql_" + request.getEntityName(), "false"))
        );
        if (request.getEntityName().equalsIgnoreCase(NetsuiteSeed.PICKLIST_VALUES_ENTITY)) {
            return fetchPicklistValues(request);
        }

        if(enableSuiteQLSync || enableSuiteQLSyncPerEntity) {
            return getFetchResponse(request);
        }
        if(request.getEntityName().equalsIgnoreCase("transactionline")) {
            return new FetchResponse(request.getWatermark(), new ListBasedIterator(List.of(), request.getWatermark()));
        }

        if(NO_WM_ENTITIES.contains(request.getEntityName())) {
            return new FetchResponse(request.getWatermark(), new NetsuiteListBasedIterator(getDataWithOffset(request, 1000), request.getWatermark()));
        }
        if(request.getEntityName().startsWith(SAVED_SEARCH_PREFIX)) {
            WsClient client = netSuiteSOAPService.getClient(request.getConnector());
            Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize, cursor) -> {
                log.info("Fetching Netsuite page for cursor {}", cursor);
                DataWithCursor results = netSuiteSOAPService.getSavedSearchRecords(client, request.getEntityName(), request, cursor);
                log.debug("Netsuite page fetched with size of {}", results.getData().size());
                return results;
            };
            int pgSize = (request.getPageSize() <= 0) ? 1000 : request.getPageSize();
            DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(request.getWatermark(),
                    request.getWatermark().getChangeStream(), 0, generator, new ArrayList<>(), pgSize, request.getWatermark().getLimit());
            return new FetchResponse(request.getWatermark(), iterator);
        }
        WsClient client = netSuiteSOAPService.getClient(request.getConnector());
        WatermarkInfo watermark = request.getWatermark();
        return getSortedFetchResponse(request, client, watermark);
    }

    private FetchResponse fetchPicklistValues(SyncRequest request) {
        EntityParams params = new EntityParams()
                .setConnector(request.getConnector())
                .setSchema(request.getEntitySchema())
                .setSourceParams(request.getSourceParams());
        validateEntityConfig(params);
        List<Pair<String, String>> picklistParams = getPicklistParams(Objects.toString(request.getSourceParam("picklistParams"), null));

        WsClient client = netSuiteSOAPService.getClient(request.getConnector());
        final WatermarkInfo nextWM = request.getWatermark().copy();
        //we use the same lastModified for all picklist entries, so no records are dropped in f/w
        long lastModified = System.currentTimeMillis();
        List<EntityData> allSelectValues = new ArrayList<>();
        picklistParams.forEach(p -> {
            int pageNumber = 1;//starts at 1
            String entityName = p.getX();
            String fieldName = p.getY();
            NetSuiteSOAPService.SelectValues selectValues = netSuiteSOAPService.getSelectValues(client, entityName, fieldName, pageNumber, lastModified);
            allSelectValues.addAll(selectValues.getRecordsInCurrentPage());
            while (selectValues.totalPages > pageNumber) {
                selectValues = netSuiteSOAPService.getSelectValues(client, entityName, fieldName, pageNumber, lastModified);
                allSelectValues.addAll(selectValues.getRecordsInCurrentPage());
                pageNumber++;
            }
        });

        return new FetchResponse(nextWM, new ListBasedIterator(allSelectValues, nextWM) {
            //we dont want to filter records by WM, because picklist values dont have created/updated dates
            @Override
            protected void filterRecords(List<EntityData> records, WatermarkInfo watermark) {
                this.filteredRecords = records;
            }
        });
    }

    private FetchResponse getFetchResponse(SyncRequest request) {

        Function3<WatermarkInfo, Integer, Long, Pair<Boolean, DataWithOffset>> generator = (wm, pageSize, offset) -> {
            try {
                log.debug("[SUITEQL_BATCH] Entity: {}, offset: {}, pageSize: {}", request.getEntityName(), offset, pageSize);
                NetSuiteRestClient restClient = getNetSuiteRestClient();
                restClient.addHeader("Prefer", "transient");
                restClient.addHeader(CONTENT_TYPE, APPLICATION_JSON);
                String QUERY_URL = "%s/services/rest/query/v1/suiteql?limit=%s";
                DateTimeFormatter formatter = DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss");

                List<EntityData> result = new ArrayList<>();
                FetchResult currResult;
                long prevOffset = offset;
                // Exhaust all records if page start lastModified == end lastModified
                do {
                    currResult = getSuiteQLEntityData(request, wm, pageSize, offset, formatter, QUERY_URL, restClient);
                    // when computing offset also consider failed record size
                    offset = offset + pageSize;
                    result.addAll(currResult.success.getY());
                } while (!currResult.success.getY().isEmpty() && currResult.success.getY().size() > 1 && (currResult.success.getY().size()+ currResult.failedCount == pageSize)
                        && currResult.success.getY().get(0).getLastModified() == currResult.success.getY().get(currResult.success.getY().size()-1).getLastModified());
                return Pair.of(currResult.success.getX(), new DataWithOffset(prevOffset, offset, result, List.of()));
            } catch (JsonMappingException e) {
                throw new RuntimeException(e);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };

        int pgSize = (request.getPageSize() <= 0) ? MAX_PAGE_SIZE : request.getPageSize();

        boolean isNoWMEntity = NO_WM_ENTITIES.contains(request.getEntityName().toLowerCase());
        NetsuiteIncrementalIterator iterator = new NetsuiteIncrementalIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pgSize, request.getWatermark().getLimit(), isNoWMEntity);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    public String getTransactionTypeForEntity(String entityName) {
        Map<String, String> entityToTransactionTypeMap = new HashMap<>();
        entityToTransactionTypeMap.put("assemblybuild", "Build");
        entityToTransactionTypeMap.put("assemblyunbuild", "Unbuild");
        entityToTransactionTypeMap.put("bill", "VendBill");
        entityToTransactionTypeMap.put("billccard", "VendCard");
        entityToTransactionTypeMap.put("billcredit", "VendCred");
        entityToTransactionTypeMap.put("binputawayworksheet", "BinWksht");
        entityToTransactionTypeMap.put("bintransfer", "BinTrnfr");
        entityToTransactionTypeMap.put("billpayment", "VendPymt");
        entityToTransactionTypeMap.put("cashrefund", "CashRfnd");
        entityToTransactionTypeMap.put("cashsale", "CashSale");
        entityToTransactionTypeMap.put("ccardrefund", "CardRfnd");
        entityToTransactionTypeMap.put("check", "Check");
        entityToTransactionTypeMap.put("commission", "Commissn");
        entityToTransactionTypeMap.put("creditcard", "CardChrg");
        entityToTransactionTypeMap.put("creditmemo", "CustCred");
        entityToTransactionTypeMap.put("currencyrevaluation", "FxReval");
        entityToTransactionTypeMap.put("customerdeposit", "CustDep");
        entityToTransactionTypeMap.put("customerrefund", "CustRfnd");
        entityToTransactionTypeMap.put("deposit", "Deposit");
        entityToTransactionTypeMap.put("depositapplication", "DepAppl");
        entityToTransactionTypeMap.put("expensereport", "ExpRept");
        entityToTransactionTypeMap.put("inventoryadjustment", "InvAdjst");
        entityToTransactionTypeMap.put("inventorycount", "InvCount");
        entityToTransactionTypeMap.put("inventorydistribution", "InvDistr");
        entityToTransactionTypeMap.put("inventorytransfer", "InvTrnfr");
        entityToTransactionTypeMap.put("inventoryworksheet", "InvWksht");
        entityToTransactionTypeMap.put("invoice", "CustInvc");
        entityToTransactionTypeMap.put("itemfulfillment", "ItemShip");
        entityToTransactionTypeMap.put("itemreceipt", "ItemRcpt");
        entityToTransactionTypeMap.put("journal", "Journal");
        entityToTransactionTypeMap.put("opportunitym", "Opprtnty");
        entityToTransactionTypeMap.put("payment", "CustPymt");
        entityToTransactionTypeMap.put("purchaseorder", "PurchOrd");
        entityToTransactionTypeMap.put("quote", "Estimate");
        entityToTransactionTypeMap.put("returnauthorization", "RtnAuth");
        entityToTransactionTypeMap.put("salesorder", "SalesOrd");
        entityToTransactionTypeMap.put("salestaxpayment", "TaxPymt");
        entityToTransactionTypeMap.put("statementcharge", "CustChrg");
        entityToTransactionTypeMap.put("transfer", "Transfer");
        entityToTransactionTypeMap.put("transferorder", "TrnfrOrd");
        entityToTransactionTypeMap.put("vendorreturnauthorization", "VendAuth");
        entityToTransactionTypeMap.put("workorder", "WorkOrd");

        return entityToTransactionTypeMap.get(entityName.toLowerCase());
    }

    private ItemQueryInfo getItemQueryInfo(String entityName) {
        ItemQueryInfo itemInfo = ITEM_ENTITY_TO_SUITEQL_MAP.get(entityName.toLowerCase());
        if (itemInfo != null) {
            return itemInfo;
        }
        // Default for all other entities
        return new ItemQueryInfo(entityName, null);
    }

    private FetchResult getSuiteQLEntityData(SyncRequest request, WatermarkInfo wm, Integer pageSize, Long offset, DateTimeFormatter formatter, String QUERY_URL, NetSuiteRestClient restClient) throws JsonProcessingException {
        FetchResult fetchResult = new FetchResult();
        String zoneId = request.getConnector().getMetaConfig().getOrDefault(TIMEZONE_ID, "UTC").toString();
        String start = formatTimestamp(wm.getStart(), zoneId, formatter);
        String end = formatTimestamp(wm.getEnd(), zoneId, formatter);

        String entityName = request.getEntityName();
        EntitySchema schema = request.getEntitySchema();
        String watermarkFieldName = "lastmodifieddate";
        if (null != schema){
            AttributeSchema attributeSchema = schema.getWatermarkField();
            if (schema.isCustom()){
                watermarkFieldName =  attributeSchema.getApiName();
            }
        }
        String transactionType = getTransactionTypeForEntity(entityName);
        String transactionTypeConfig = String.valueOf(request.getAdditionalParams().getOrDefault("transactiontype", ""));
        boolean child = CHILD_PARENT_ENTITY_MAP.containsKey(request.getEntityName());

        ItemQueryInfo itemQueryInfo = getItemQueryInfo(entityName);
        String whereClause = itemQueryInfo.whereClause != null 
            ? itemQueryInfo.whereClause + " AND " 
            : "";
        String suiteQL = String.format(
            "SELECT id, TO_CHAR(%s, 'YYYY-MM-DD HH24:MI:SS') AS %s " +
            "FROM %s " +
            "WHERE %s%s BETWEEN TO_TIMESTAMP('%s', 'YYYY-MM-DD HH24:MI:SS') " +
            "AND TO_TIMESTAMP('%s', 'YYYY-MM-DD HH24:MI:SS') " +
            "ORDER BY %s, id",
            watermarkFieldName, watermarkFieldName, itemQueryInfo.tableName,
            whereClause, watermarkFieldName, start, end, watermarkFieldName);

        if (transactionType != null) {
            suiteQL = String.format(
                    "SELECT id, TO_CHAR(%s, 'YYYY-MM-DD HH24:MI:SS') AS %s FROM transaction WHERE type = '%s' AND %s BETWEEN TO_TIMESTAMP('%s', 'YYYY-MM-DD HH24:MI:SS') " +
                            "AND TO_TIMESTAMP('%s', 'YYYY-MM-DD HH24:MI:SS') ORDER BY %s, id",
                    watermarkFieldName, watermarkFieldName, transactionType,watermarkFieldName,
                    start,
                    end,watermarkFieldName
            );
        } else if(child) {
            transactionType = getTransactionTypeForEntity(CHILD_PARENT_ENTITY_MAP.get(request.getEntityName()));
            suiteQL = String.format(
                "SELECT t.id AS id, TO_CHAR(%s, 'YYYY-MM-DD HH24:MI:SS') AS %s " +
                "FROM transaction t " +
                "WHERE t.type = '%s' " +
                "AND t.%s BETWEEN TO_TIMESTAMP('%s', 'YYYY-MM-DD HH24:MI:SS') " +
                "AND TO_TIMESTAMP('%s', 'YYYY-MM-DD HH24:MI:SS') " +
                "ORDER BY t.%s, t.id",watermarkFieldName, watermarkFieldName,
                transactionType, watermarkFieldName,start, end,watermarkFieldName
            );
        } else if(entityName.equalsIgnoreCase("transactionline")) {
            suiteQL = String.format(
                    "SELECT tl.*, TO_CHAR(t.%s, 'YYYY-MM-DD HH24:MI:SS') AS %s, t.type as transactiontype" +
                            " FROM transactionline tl JOIN transaction t ON tl.transaction = t.id" +
                            //joining with item brings more transactionline fields magically,even if no additional select clauses are there
                            " LEFT JOIN item on tl.item=item.id " +
                            " WHERE t.type = '%s' " +
                            " AND t.%s BETWEEN TO_TIMESTAMP('%s', 'YYYY-MM-DD HH24:MI:SS') " +
                            " AND TO_TIMESTAMP('%s', 'YYYY-MM-DD HH24:MI:SS') " +
                            " ORDER BY t.%s, t.id",watermarkFieldName,watermarkFieldName,
                    transactionTypeConfig, watermarkFieldName,start, end,watermarkFieldName
            );
        } else if (NO_WM_ENTITIES.contains(entityName.toLowerCase())) {
            suiteQL = String.format("SELECT * FROM %s ORDER BY id", itemQueryInfo.tableName);
        }
        log.debug("SuiteQL used : {}", suiteQL);

        Map<String, Object> payload = Map.of(
                "q", suiteQL
        );

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(payload);

        String queryUrl = String.format(QUERY_URL, request.getConnector().getEndpoint(), pageSize);

        String paginatedUrl = queryUrl + "&offset=" + offset;
        ResponseEntity<String> response = restClient.postRaw(paginatedUrl, json, request.getConnector().getAuthConfig());
        // Parse the response
        JsonNode rootNode = mapper.readTree(response.getBody());
        JsonNode itemsNode = rootNode.get("items");
        long currentTime = Instant.now().toEpochMilli();
        List<EntityData> result = new ArrayList<>();
        // Extract Ids and convert lastModified to epoch millis
        Map<String, Long> idToLastModifiedMap = new LinkedHashMap<>();
        DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of(zoneId));
        List<String> lastModifiedList = new ArrayList<>();
        if (itemsNode != null && itemsNode.isArray()) {
            for (JsonNode item : itemsNode) {
                String id = item.get("id").asText();
                String lastModified = item.has("lastmodifieddate") ? item.get("lastmodifieddate").asText() : null;
                if (schema.isCustom()){
                    lastModified = item.has("lastmodified") ? item.get("lastmodified").asText() : null;
                }
                lastModifiedList.add(lastModified);
                //For no watermark entities we are updating lastmodified time to the current sync end time
                //done as part of defect fix SYN-19633
                if (NO_WM_ENTITIES.contains(entityName.toLowerCase())) {
                    log.debug("Last modified updated to request end time");
                    idToLastModifiedMap.put(id, request.getWatermark().getEnd());
                }
                else if (lastModified != null) {
                    try {
                        long epochMillis = 0l;
                        // Added try catch because test was failing
                        try{
                            epochMillis = ZonedDateTime.parse(lastModified, timestampFormatter).toInstant().toEpochMilli();
                        }catch (DateTimeParseException e){
                            epochMillis = LocalDate.parse(lastModified, DateTimeFormatter.ofPattern("M/d/yyyy")).atStartOfDay(ZoneId.of(zoneId)).toInstant().toEpochMilli();
                        }
                        if(entityName.equalsIgnoreCase("transactionline")) {
                            String parent = item.get("transaction").asText();
                            id = parent + "#" + id;
                            EntityData entityData = new EntityData(entityName);
                            entityData.setId(id);
                            entityData.setLastModified(epochMillis);
                            Map<String, Object> valuesMap = mapper.convertValue(item, new TypeReference<>() {});
                            entityData.setValues(valuesMap);
                            entityData.addValue("line", item.get("id").asText());
                            result.add(entityData);
                            continue;
                        }
                        idToLastModifiedMap.put(id, epochMillis);
                    } catch (DateTimeParseException e) {
                        log.error("Failed to parse lastModified date: {}", lastModified, e);
                    }
                } else {
                    //do nothing for now?
                    log.debug("Last modified does not exist for entity {}", entityName);
                }
            }
        }

        boolean hasMore = rootNode.get("hasMore").asBoolean();

        if(entityName.equalsIgnoreCase("transactionline")) {
            return fetchResult.setSuccess(Pair.of(hasMore, result));
        }

        // Fetch using REST
        if(child) {
            EntitySchema entitySchema = transformSchema(request);
            request.setEntitySchema(entitySchema);
        }
        // Use idToLastModifiedMap.keySet() instead of ids, and make idToLastModifiedMap available as needed.
        result = getEntityData(request, new ArrayList<>(idToLastModifiedMap.keySet()), SearchResults.emptyResults());
        if(!idToLastModifiedMap.isEmpty() && result.size() < idToLastModifiedMap.size()) {
            fetchResult.setFailedCount(idToLastModifiedMap.size() - result.size());
        }

        if (child) {
            result = result.stream()
                    .flatMap(entityData -> entityData.getChildrenRecords(resolveChildAPIName(request.getEntityName(), entityName)).stream())
                    .collect(Collectors.toList());
        }

        // Update lastModified field for each EntityData using idToLastModifiedMap
        for (EntityData entity : result) {
            if (child) {
                String parentId = entity.getParentId();
                if (parentId != null) {
                    Long epochMillis = idToLastModifiedMap.get(parentId);
                    if (epochMillis != null) {
                        entity.setLastModified(epochMillis);
                    }
                }
            } else {
                Long epochMillis = idToLastModifiedMap.get(entity.getId());
                if (epochMillis != null) {
                    entity.setLastModified(epochMillis);
                }
            }
        }

//        // Check the lastModifiedDate of the first and last records in the batch
//        if (result.size() > 1) {
//            long firstModified = result.get(0).getLastModified();
//            long lastModified = result.get(result.size() - 1).getLastModified();
//
//            // If first and last modified dates are not the same, stop fetching
//            if (firstModified != lastModified) {
//                return result;
//            } else if(hasMore){
//                // If the same, keep fetching additional batches (increment the offset)
//                result.addAll(getSuiteQLEntityData(request, wm, pageSize, offset + pageSize, formatter, QUERY_URL, restClient));
//            }
//        }
        return fetchResult.setSuccess(Pair.of(hasMore, result));
    }

    private static String formatTimestamp(long epochMillis, String zoneId, DateTimeFormatter formatter) {
        ZoneId zone = ZoneId.of(zoneId);
        Instant instant = Instant.ofEpochMilli(epochMillis);
        ZonedDateTime zonedDateTime = instant.atZone(zone);
        return formatter.format(zonedDateTime);
    }

    protected FetchResponse getSortedFetchResponse(SyncRequest request, WsClient client, WatermarkInfo watermark) {
        //if full sync, find the nearest watermark for the earliest modified record
        long start = Instant.ofEpochMilli(watermark.getStart()).truncatedTo(ChronoUnit.MINUTES).toEpochMilli();
        EntitySchema entitySchema = transformSchema(request);
        SyncRequest initialSyncRequest = request.withEntitySchema(entitySchema);
        long startWatermark =( start <= getFirstCreatedTime(request) && !NO_WM_ENTITIES.contains(request.getEntityName())) ? netSuiteSOAPService.findFirst(client,initialSyncRequest,watermark) : start;
        WatermarkInfo initialWatermark = new WatermarkInfo(startWatermark, watermark.getEnd(), watermark.isInitial(), watermark.getOffset());
        WatermarkInfo windowedWatermark = new WatermarkInfo(startWatermark, watermark.getEnd(), watermark.isInitial(), watermark.getOffset());
        //Current wm end or 30 days from end of watermark
        log.info("Windowed watermarks is {}", windowedWatermark);
        windowedWatermark.setEnd(Math.min(watermark.getEnd(),startWatermark + _WATERMARK_INCREMENT));

        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            log.info("Using Start Watermark {}, end {} offset {}",windowedWatermark.getStart(),windowedWatermark.getEnd(), offset);
            //if we are trying to retrieve sublists, we switch the schema to parent first
            SyncRequest syncRequest = request.withEntitySchema(entitySchema);

            // Implement retries in case of read timeout
            int retries = 5;
            SearchResults results = null;

            while (retries > 0) {
                try {
                    results = netSuiteSOAPService.list(client, syncRequest, windowedWatermark, pageSize, offset.intValue());
                    break;
                } catch (Exception e) {
                    if (e.getMessage().contains("Read timed out")) {
                        long startWm = windowedWatermark.getStart();
                        long endWatermark = windowedWatermark.getEnd();
                        long diffInHours = TimeUnit.MILLISECONDS.toHours(endWatermark - startWm);
                        log.info("Difference in start {} and {} in hours is  {}", startWm, endWatermark, diffInHours);
                        long increment = (diffInHours >= 24) ? _WATERMARK_INCREMENT_HALF_DAY : (diffInHours >= 12) ?  _WATERMARK_INCREMENT_2_HOUR : _WATERMARK_INCREMENT_1_HOUR;
                        windowedWatermark.setEnd(Math.min(windowedWatermark.getEnd(),windowedWatermark.getStart() + increment));
                        log.error("Retrying because of read timeout for watermark - {}. Retries left: " + (retries - 1), windowedWatermark);
                        retries--;
                    } else {
                        throw e;
                    }
                }
            }

            if(results == null) {
                throw new RuntimeException("Failed to fetch results for watermark - " + windowedWatermark);
            }

            List<String> ids = results.getInternalIds();
            log.info("Got {} recordIds from SOAP",ids.size());

            List<EntityData> result = List.of();
            if("file".equalsIgnoreCase(syncRequest.getEntityName())) {
              result = getFileEntityDatas(client, results);
            } else if("paycheckjournal".equalsIgnoreCase(syncRequest.getEntityName())) {
              result = netSuiteSOAPService.toPaycheckJournalData(client, results);
            }  else if("binworksheet".equalsIgnoreCase(request.getEntityName())) {
              result = netSuiteSOAPService.toBinWorksheetData(client, results);
            } else {
              result = getEntityData(syncRequest, ids, results);
            }
            if("contact".equalsIgnoreCase(syncRequest.getEntityName())) {
                netSuiteSOAPService.updateContactWithAddressDetails(result, results);
            }
            if("customer".equalsIgnoreCase(syncRequest.getEntityName())) {
                netSuiteSOAPService.updateCustomerWithAltName(result, results);
            }
            log.info("Got {} records from REST",result.size());
            return Pair.of(Long.valueOf(result.size()), result.stream());
        };

        if (watermark.isResync() && start <= getFirstCreatedTime(request)){
            localStorageService.provisionIfNotExists(request, request.getEntityName());
            localStorageService.cleanupDB(request);
        }
        //Store against db name
        localStorageService.provisionIfNotExists(request, request.getEntityName());
        long maxLocalWatermark = localStorageService.maxWatermark(request.getConnector(), request.getEntityName());
        if(maxLocalWatermark > startWatermark) {
            windowedWatermark.setStart(maxLocalWatermark).setEnd(Math.min(Math.max(initialWatermark.getEnd(),maxLocalWatermark),maxLocalWatermark+_WATERMARK_INCREMENT));
        }
        log.info("After checking maxLocalWatermark from localstorage, Windowed watermarks is {} and maxLocalWatermark is {} ", windowedWatermark,maxLocalWatermark);

        int pSize = request.getPageSize()==0 ? MAX_PAGE_SIZE : Math.min(Math.max(request.getPageSize(), 5), MAX_PAGE_SIZE);
        DefaultDataIterator iterator = new NetSuiteIterator(initialWatermark,
                0, generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pSize, watermark.getLimit(), request.getEntitySchema(), entitySchema, this::extractChildRecords);
        localStorageService.fetch(request,iterator);
        long count = localStorageService.count(request.getConnector(), request.getEntityName(), startWatermark);
        while (count < iterator.getMaxRecordsPerEntitySyncCycle() && windowedWatermark.getEnd() < watermark.getEnd()){
            //if counts are less than our internal page size, expand query window linearly

            long increment = count < (request.getPageSize()==0? MAX_PAGE_SIZE : request.getPageSize()) ? windowedWatermark.getDurationMs() + _WATERMARK_INCREMENT  : _WATERMARK_INCREMENT;
            long x = watermark.getEnd() - windowedWatermark.getEnd();
            windowedWatermark.moveBy(Math.min(increment, x));
            localStorageService.fetch(request,new NetSuiteIterator(windowedWatermark,
                    0, generator, new ArrayList<>(),
                    request.getEntitySchema().getWatermarkField(), pSize, watermark.getLimit(), request.getEntitySchema(), entitySchema, this::extractChildRecords));
            count = localStorageService.count(request.getConnector(), request.getEntityName(),startWatermark);
            log.info("Local Storage Count {} Max Records {} window watermark {} initial watermark {}", count, iterator.getMaxRecordsPerEntitySyncCycle(), windowedWatermark, startWatermark);
        }

        FetchResponse fetchResponse = localStorageService.getByWatermark(entitySchema.isCustom() ? request : request.setWatermark(initialWatermark));
        AbstractEntityDataBatchIterator localStorageIterator = (AbstractEntityDataBatchIterator)fetchResponse.getIterator();
        localStorageIterator.setMaxRecordsPerEntitySyncCycle(iterator.getMaxRecordsPerEntitySyncCycle());
        return fetchResponse;
    }

    private List<EntityData> getFileEntityDatas(WsClient wsClient, SearchResults results) {
        return netSuiteSOAPService.toFileEntityData(wsClient, results);
    }

    private List<EntityData> getEntityData(SyncRequest request, List<String> itemIds, SearchResults results) {
        Integer threadCount = getThreadCount(request);
        List<List<String>> partitions = partitionIntoNParts(itemIds, threadCount);

        if (threadCountTracker.get() < MAX_THREAD_LIMIT && request.getConnector().getInternalConfig().containsKey("threadCount")) {
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

            threadCountTracker.addAndGet(threadCount);
            try {
                List<CompletableFuture<List<EntityData>>> futures = partitions.stream()
                        .map(partition -> CompletableFuture.supplyAsync(
                                () -> partition.stream()
                                        .flatMap(id -> getItem(request, id, results, getCustomRecordLastModifiedMap(request, itemIds)).stream())
                                        .collect(Collectors.toList()),
                                executorService))
                        .collect(Collectors.toList());

                return awaitFuturesAndCombineResults(futures, executorService);
            } finally {
                threadCountTracker.addAndGet(-threadCount);
                executorService.shutdown();
            }
        } else {
            log.error("Max thread count reached {}. Falling back to single thread mode", threadCountTracker.get());
            return itemIds.stream()
                    .flatMap(id -> getItem(request, id, results, getCustomRecordLastModifiedMap(request, itemIds)).stream())
                    .collect(Collectors.toList());
        }
    }

    private static Integer getThreadCount(SyncRequest request) {
        return (Integer) request.getConnector().getInternalConfig().getOrDefault("threadCount", 3);
    }

    private List<EntityData> awaitFuturesAndCombineResults(List<CompletableFuture<List<EntityData>>> futures, ExecutorService executorService) {
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        allFutures.join();

        List<EntityData> combinedResults = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        executorService.shutdown();

        return combinedResults;
    }

    private Map<String, String> getCustomRecordLastModifiedMap(SyncRequest request, List<String> itemIds) {
        return request.getEntitySchema().isCustom()
                ? generateCustomRecordLastModifiedMap(request, itemIds)
                : Collections.emptyMap();
    }

    public List<List<String>> partitionIntoNParts(List<String> list, int n) {
        int totalSize = list.size();
        int partitionSize = totalSize / n;
        int remainder = totalSize % n;

        return IntStream.range(0, n)
                .mapToObj(i -> {
                    int start = i * partitionSize + Math.min(i, remainder);
                    int end = start + partitionSize + (i < remainder ? 1 : 0);
                    return new ArrayList<>(list.subList(start, end));
                })
                .collect(Collectors.toList());
    }

    public List<EntityData> getDataWithOffset(SyncRequest request, long pageSize) {
        EntitySchema entitySchema = transformSchema(request);
        SyncRequest syncRequest = request.withEntitySchema(entitySchema);
        NetSuiteRestClient restClient = getNetSuiteRestClient();
        AuthConfig authConfig = request.getConnector().getAuthConfig();
        List<String> ids;
        String body = "";
        long prevOffset = 0;
        List<EntityData> entityData = new ArrayList<>();
        Map<String, Map<String, Object>> soapDataMap = new HashMap<>();
        if (syncRequest.getEntityName().equals("subsidiary") || syncRequest.getEntityName().equals("campaign")) {
            WsClient client = netSuiteSOAPService.getClient(request.getConnector());
            updateWithSOAPResponse(request, client, entityData, Optional.empty());
            return entityData;
        }
        while(prevOffset != -1) {
            log.info("Fetching {} data for offset {}", request.getEntityName(), prevOffset);
            if (syncRequest.getEntityName().equals("subscriptionplan")) {
                String url = format(ITEMS_OFFSET_URL, request.getConnector().getEndpoint(), "subscription", pageSize, prevOffset);
                ResponseEntity<String> response = restClient.getResponse(url, authConfig);
                restClient.checkResponse(response);
                body = response.getBody();
                List<String> subscriptionIds = extractIds(body);
                ids = extractSubscriptionPlanIds(syncRequest, subscriptionIds);
            } else {
                String url = format(ITEMS_OFFSET_URL, request.getConnector().getEndpoint(), syncRequest.getEntityName(), pageSize, prevOffset);
                ResponseEntity<String> response = restClient.getResponse(url, authConfig);
                restClient.checkResponse(response);
                body = response.getBody();
                ids = extractIds(body);
            }
            getDataForIds(request, ids, syncRequest, entityData);
            prevOffset = extractOffset(body);
        }
        if(supportedChildEntities.contains(request.getEntityName())){
            log.info("Fetching child records for parent {}", request.getEntityName());
            List<EntityData> childRecords = extractChildRecords(syncRequest.getEntitySchema(), request.getEntitySchema(), entityData);
            log.info("Got {} child records child records from REST",childRecords.size());
            return childRecords;
        }
        return entityData;
    }

    private void updateWithSOAPResponse(SyncRequest request, WsClient client, List<EntityData> entityData, Optional<List<String>> idsToFetch) {
        List<String> ids;
        Map<String, Map<String, Object>> soapDataMap;
        soapDataMap = netSuiteSOAPService.fetchAllSubsidiary(client, idsToFetch);
        ids = new ArrayList<>(soapDataMap.keySet());
        getDataForIds(request, ids, request, entityData);
        if(!soapDataMap.isEmpty()) {
            for (EntityData result : entityData) {
                if(soapDataMap.containsKey(result.getId())) {
                    Map<String, Object> map = soapDataMap.get(result.getId());
                    map.forEach(result::addValue);
                }
            }
        }
    }

    private void getDataForIds(SyncRequest request, List<String> ids, SyncRequest syncRequest, List<EntityData> entityData) {
        List<EntityData> results = getDataForIds(ids, syncRequest);

        if(request.getWatermark() != null) {
            // Netsuite Rest APIs for the NO_WM_ENTITIES do not support proper filtering on the lastModifiedDate (range query).
            // We end up pulling all records. In this block we try to optimize by discarding records
            // that are not within the WM window (or at least not less than the wm begin)
            final String wmField = getWatermarkField(syncRequest);
            results = results.stream().filter(x -> {
                if (!x.has(wmField) || ZonedDateTime.parse(x.getValueAsString(wmField)).toEpochSecond() * 1000 >= request.getWatermark().getStart()) {
                    return true;
                }
                // no WM field or outside wm window, discard it.
                return false;
            }).collect(Collectors.toList());
        }

        entityData.addAll(results);
    }

    private List<String> extractSubscriptionPlanIds(SyncRequest request, List<String> ids) {
        List<String> subscriptionPlanIds = new ArrayList<>();
        ids.forEach(id -> {
            ConnectorInfo connectorInfo = request.getConnector();
            AuthConfig auth = connectorInfo.getAuthConfig();
            String entityName = request.getEntityName();
            String itemUrl = format(ITEM_URL, connectorInfo.getEndpoint(), "subscription", id);
            log.info("Fetching associated subscription - {}", itemUrl);
            NetSuiteRestClient restClient = getNetSuiteRestClient();
            restClient.addHeader(CONTENT_TYPE, APPLICATION_JSON);
            try {
                ResponseEntity<String> reponseEntity = restClient.getResponse(itemUrl, auth);
                log.debug("getItem responseEntity {} ", reponseEntity);
                String body = reponseEntity.getBody();
                try {
                    Map<String, Object> parsedMap = mapper.readValue(body, Map.class);
                    Map<String, Object> subscriptionPlanMap = (Map<String, Object>) parsedMap.get("subscriptionPlan");
                    subscriptionPlanIds.add((String) subscriptionPlanMap.get("id"));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e.getMessage());
                }
            } catch(NonRetriableException e){
                if(ErrorCodes.BAD_REQUEST.equals(e.getErrorCode())){
                    log.info("Skipping GET record for id {}, entity name {}",id,entityName);
                }

                log.error("Could not get record for id {}.Error {}",id,e);
            }
        });
        return subscriptionPlanIds;
    }

    private long extractOffset(String body) {
        long offset = -1;
        try {
            Map<String, Object> parsedMap = mapper.readValue(body, Map.class);
            List<Object> links = (List<Object>) parsedMap.get("links");
            for(int i = 0; i < links.size(); i++) {
                Map<String, String> linkMap = (Map<String, String>) links.get(i);
                if(linkMap.get("rel").equalsIgnoreCase("next")) {
                    String href = linkMap.get("href");
                    int pos = href.indexOf("offset=");
                    offset = Long.valueOf(href.substring(pos + 7));
                    break;
                }
            };
            return offset;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private List<EntityData> getDataForIds(List<String> ids, SyncRequest request) {
        SearchResults searchResults = new SearchResults(ids, 0, 0, new ArrayList<>());
        List<EntityData> results = getItems(request, ids, searchResults);
        return results;
    }

    private List<String> extractIds(String body) {
        try {
            List<String> ids = new ArrayList<>();
            Map<String, Object> parsedMap = mapper.readValue(body, Map.class);
            List<Object> items = (List<Object>) parsedMap.get("items");
            items.forEach(item -> {
                Map<String, String> itemMap = (Map<String, String>) item;
                if(itemMap.containsKey("id")) ids.add(itemMap.get("id"));
            });
            return ids;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not parse response");
        }
    }

    private List<EntityData> getItems(SyncRequest request, List<String> ids, SearchResults searchResults) {
        int maxLimit = (request.getWatermark() != null && request.getWatermark().getLimit() > 0) ? request.getWatermark().getLimit() : ids.size();
        Map<String, String> customObjectLastModifiedMap = request.getEntitySchema().isCustom() ? generateCustomRecordLastModifiedMap(request, ids) : Collections.EMPTY_MAP;
        return ids.stream().flatMap(id -> getItem(request, id, searchResults, customObjectLastModifiedMap).stream()).limit(maxLimit).collect(Collectors.toList());
    }

    private Optional<EntityData> getItem(SyncRequest request, String id, SearchResults results, Map<String, String> customObjectLastModifiedMap) {
        ConnectorInfo connectorInfo = request.getConnector();
        AuthConfig auth = connectorInfo.getAuthConfig();
        String entityName = request.getEntityName();
        String urlEntityName = getURLEntityName(entityName, results);
        String itemUrl = format(ITEM_URL, connectorInfo.getEndpoint(), urlEntityName, id);
        log.info("Fetching {}", itemUrl);
        NetSuiteRestClient restClient = getNetSuiteRestClient();
        restClient.addHeader(CONTENT_TYPE, APPLICATION_JSON);
        try {
            ResponseEntity<String> reponseEntity = restClient.getResponse(itemUrl, auth);
            log.debug("getItem responseEntity {} ", reponseEntity);
            String body = reponseEntity.getBody();
            DocumentContext ctx = JsonPath.parse(body);
            HashMap<String, Object> item = ctx.read("$");
            tranformJournalEntries(entityName, item);
            EntitySchema entitySchema = request.getEntitySchema();
            EntityData d = new EntityData();
            d.setName(entityName);
            d.setId(item.get("id").toString());
            d.setConnectorId(request.getConnector().getId());
            d.setValues(new HashMap<>());
            if (NO_WM_ENTITIES.contains(request.getEntityName().toLowerCase())) {
                if (request.getWatermark() != null) {
                    d.setLastModified(request.getWatermark().getEnd());
                }
            }
            else {
                String lastModifiedStr = null;
                if (item.get(getWatermarkField(request)) != null){
                    lastModifiedStr =item.get(getWatermarkField(request)).toString();
                }
                if (!customObjectLastModifiedMap.isEmpty() && request.getEntitySchema().isCustom()){
                    lastModifiedStr = customObjectLastModifiedMap.get(d.getId());
                    d.addValue(getWatermarkField(request), customObjectLastModifiedMap.get(d.getId()));
                }
                d.setLastModified(ZonedDateTime.parse(lastModifiedStr).toEpochSecond() * 1000);
            }
            // First process child objects
            transformLineItems(request, entityName, item, d, ctx, results);
            log.debug(item.toString());
            transformValues(request, entitySchema, entityName, d, item, ctx, false);
            addOpptyContacts(results, entityName, d);
            Optional<AttributeSchema> filesAttr = request.getEntitySchema().getAttributes().stream()
                .filter(x -> EntityData.SYNCARI_FILE_REFERENCE_FIELD_NAME.equalsIgnoreCase(x.getApiName())).findFirst();
            if (TRANSACTION_ENTITIES.contains(entityName) && filesAttr.isPresent()) {
                netSuiteSOAPService.getDocumentFiles(request, List.of(d));
            }
            return Optional.ofNullable(d);
        }catch (RetriableException retriableException){
            if (StringUtils.isNotEmpty(retriableException.getStatusCode()) && retriableException.getStatusCode().equalsIgnoreCase("500") && StringUtils.isNotEmpty(retriableException.getMessage()) &&
            retriableException.getMessage().contains("An unexpected error occurred. Error ID")){
                log.error("RetriableException occurred,not retrying in this case, Could not get record for id {}.Error is {}",id,retriableException);
                return Optional.empty();
            }
            throw retriableException;
        }catch(NonRetriableException e){
            if(ErrorCodes.BAD_REQUEST.equals(e.getErrorCode())){
                log.info("Skipping GET record for id {}, entity name {}",id,entityName);
            }

            log.error("Could not get record for id {}.Error {}",id,e);
            return Optional.empty();
        }
    }

    private Map<String, String> generateCustomRecordLastModifiedMap(SyncRequest request, List<String> ids){
        if (ids.isEmpty()){
            return Collections.EMPTY_MAP;
        }
        NetSuiteRestClient restClient = getNetSuiteRestClient();
        restClient.addHeader("Prefer", "transient");

        String json = "";
        try {
            Map<String, String> payload = Map.of("q", format(GET_CUSTOM_RECORD_LAST_MODIFIED_SUITESQL,
                    request.getEntityName(),
                    String.join(",", ids)));
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed parsing json with message: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }

        String url = format(SUITE_QUERY_URL, getApiUrlPrefix(request.getConnector()), VERSION, 0,1000);
        ResponseEntity<String> response = restClient.postRaw(url, json, request.getConnector().getAuthConfig());
        String body = response.getBody();
        DocumentContext ctx = JsonPath.parse(body);
        List<HashMap<String, String>> itemList = ctx.read("items");
        return itemList.stream().collect(Collectors.toMap(item->item.get("id"), item->item.get("lastmodified")));
    }

    private HashMap<String, Object> getRawItemById(AuthConfig auth, String endpoint, String urlEntityName, String id) {
        String itemUrl = format(ITEM_URL, endpoint, urlEntityName, id);
        log.info("Fetching {}", itemUrl);
        NetSuiteRestClient restClient = getNetSuiteRestClient();
        restClient.addHeader(CONTENT_TYPE, APPLICATION_JSON);

        ResponseEntity<String> reponseEntity = restClient.getResponse(itemUrl, auth);
        log.debug("getRawItemById responseEntity {} ", reponseEntity);
        String body = reponseEntity.getBody();
        DocumentContext ctx = JsonPath.parse(body);
        return ctx.read("$");
    }

    private void transformValues(SyncRequest request, EntitySchema entitySchema, String entityName, EntityData d,
                                 HashMap<String, Object> item, DocumentContext ctx, boolean useRefsFromMapValue) {
        Map<String, Reference> references = getFieldToReferenceMap(entityName);
        List<Pair<String,Object>> skippedFields = new ArrayList<>();
        item.forEach((k, v) -> {
            if (references.containsKey(k)) {
                // TODO: This and the else block has similar code. One is to handle standard defined references. Need to consolidate.
                // The other is to extract free form references from "items" map.
                Map<String, Object> refs = Map.class.cast(v);
                Object value = "";
                if (refs.containsKey("id")) {
                    value = refs.get("id");
                } else {
                    // Handle multivalued field references.
                    Optional<AttributeSchema> field = entitySchema.getField(k);
                    value = field.isPresent() && field.get().isMultiValueField() ? read(ctx, format("$.%s.items[*].id",k)) :  read(ctx,format("$.%s.id", k));
                }
                d.addValue(k, value);
            } else if (!Map.class.isAssignableFrom(v.getClass())) {
                d.addValue(k, v);
            } else if (Map.class.isAssignableFrom(v.getClass()) && "addressBook".equalsIgnoreCase(k)) {
                addAddressBookValues(d, Map.class.cast(v));
            } else {
                Optional<AttributeSchema> field = entitySchema.getCachedField(k);
                field.ifPresentOrElse(f ->{
                    Map<String, Object> refs = Map.class.cast(v);
                    Object value = "";
                    if (useRefsFromMapValue) {
                        if (refs.containsKey("id")) value = refs.get("id");
                    } else {

                        //just ids for picklists
                        if ("picklist".equals(f.getDataType())) {
                            value = f.isMultiValueField() ? read(ctx, format("$.%s.items[*].id", k)) : read(ctx, format("$.%s.id", k));
                        } else {
                            //all values otherwise
                            ctx.delete(format("$.%s..links", k));
                            if (f.isMultiValueField()) {
                                final List<Object> itemKeys = ctx.read(format("$.%s.items[*].keys()", k));
                                boolean hasItems = itemKeys != null && !itemKeys.isEmpty();

                                if (hasItems) {
                                    //we check if the fields only have id & refName (or just id),
                                    //if so, we simply picklup the ids as a list , for backward compat
                                    Set<Object> keys = Set.class.cast(itemKeys.get(0));
                                    if (keys.contains("id") && keys.size() <= 2) {
                                        value = read(ctx, format("$.%s.items[*].id", k));
                                    } else {
                                        //otherwise, we read all keys and present it it as list of objects
                                        value = read(ctx, format("$.%s.items[*]", k));
                                    }
                                }
                            } else {
                                final Set<Object> keys = ctx.read(format("$.%s.keys()", k));
                                if (keys != null && (keys.contains("id")) && keys.size() <= 2) {
                                    value = read(ctx, format("$.%s.id", k));
                                } else {
                                    value = read(ctx, format("$.%s", k));
                                }
                            }
                        }
                    }
                    d.addValue(k, value);
                },()->skippedFields.add(Pair.of(k,v)));
            }
        });
        if ("salesorder".equalsIgnoreCase(entityName) || "estimate".equalsIgnoreCase(entityName)
        || "cashsale".equalsIgnoreCase(entityName)) {
            addSalesOrderAddresses(d, item);
        }
        if ("billingaccount".equalsIgnoreCase(entityName)) {
            Map<String, Object> customerVal = (Map<String, Object>) item.get("customer");
            Map<String, Object> customer = getRawItemById(request.getConnector().getAuthConfig(), request.getConnector().getEndpoint(),
                "customer", customerVal.get("id").toString());
            if (customer.containsKey("addressBook")) {
                List<Object> addressItems = (List<Object>) Map.class.cast(customer.get("addressBook")).getOrDefault("items",List.of());
                Map<String, Object> addressById = new HashMap<>();
                addressItems.forEach(addressItem -> {
                    Map<String, Object> address = (Map<String, Object>) addressItem;
                    addressById.put(address.get("id").toString(), address);
                });
                // It really comes as a single string value even though the name is List!
                if (item.containsKey("billAddressList")) {
                    addAddressItem(d, addressById.get(item.get("billAddressList").toString()));
                }
                if (item.containsKey("shipAddressList")) {
                    addAddressItem(d, addressById.get(item.get("shipAddressList").toString()));
                }
            }
        }
    }

    protected void removeRefNamesIfIdPresent(Object object) {
        if (object != null && Map.class.isAssignableFrom(object.getClass())) {
            Map map = (Map) object;
            if (map.containsKey("id")) {
                map.remove("refName");
            }
            map.forEach((k, v) -> {
                removeRefNamesIfIdPresent(v);
            });
        }
    }

    protected Object read(DocumentContext ctx, String path) {
        try {
            return ctx.read(path);
        } catch (com.jayway.jsonpath.PathNotFoundException e) {
            return null;
        }
    }

    private String getURLEntityName(String entityName, SearchResults results) {
        String urlEntityName = entityName;
        if (results.getRecords().size() == 0) return urlEntityName;
        String resultsType = results.getRecords().get(0).getClass().getSimpleName().toLowerCase();
        if (!resultsType.equalsIgnoreCase("customrecord") && !entityName.equalsIgnoreCase(resultsType)) {
            if(!INVALID_RESULT_TYPES.containsKey(resultsType)) {
                urlEntityName = resultsType;
            } else  {
                urlEntityName = INVALID_RESULT_TYPES.get(resultsType);
            }
        }
        return urlEntityName;
    }

    private void addAddressBookValues(EntityData d, Map<String,Object> values) {
        List<Object> addressItems = (List<Object>) values.getOrDefault("items",List.of());
        addressItems.forEach(addressItem ->{
            addAddressItem(d, addressItem);
        });
    }

    private void addAddressItem(EntityData d, Object addressItem) {
        if(Map.class.isAssignableFrom(addressItem.getClass())) {
            Map<String, Object> address = (Map<String, Object>) addressItem;
            Map<String, Object> addressDetails = (Map<String, Object>) address.getOrDefault("addressBookAddress",Map.of());
            String addressLabel = address.getOrDefault("label","").toString();
            Boolean isResidential = Boolean.valueOf((address.getOrDefault("isResidential", "false").toString()));
            Boolean isSyncedFromSubsidiary = Boolean.valueOf((address.getOrDefault("isSyncedFromSubsidiary", "false").toString()));
            Boolean isBilling = Boolean.valueOf((address.getOrDefault("defaultBilling", "false").toString()));
            String id = address.getOrDefault("internalId", "").toString();
            Boolean isShipping = Boolean.valueOf((address.getOrDefault("defaultShipping", "false").toString()));
            if(isBilling) {
                addAddressValues("billingAddress", addressDetails, d);
                d.addValue("billingAddress_isResidential", isResidential);
                d.addValue("billingAddress_label", addressLabel);
                d.addValue("billingAddress_isSyncedFromSubsidiary", isSyncedFromSubsidiary);
                d.addValue("billingAddress_id", id);
            }
            if(isShipping){
                addAddressValues("shippingAddress", addressDetails, d);
                d.addValue("shippingAddress_isResidential", isResidential);
                d.addValue("shippingAddress_label", addressLabel);
                d.addValue("shippingAddress_isSyncedFromSubsidiary", isSyncedFromSubsidiary);
                d.addValue("shippingAddress_id", id);
            }
        }
    }

    private void addAddressValues(String prefix, Map<String, Object> addressDetails, EntityData data) {
        data.addValue(prefix + "_attention", addressDetails.getOrDefault("attention", ""));
        data.addValue(prefix + "_addressee", addressDetails.getOrDefault("addressee", ""));
        data.addValue(prefix + "_addr1", addressDetails.getOrDefault("addr1", ""));
        data.addValue(prefix + "_addr2", addressDetails.getOrDefault("addr2", ""));
        data.addValue(prefix + "_addr3", addressDetails.getOrDefault("addr3", ""));
        data.addValue(prefix + "_addrText", addressDetails.getOrDefault("addrText", ""));
        data.addValue(prefix + "_addrphone", addressDetails.getOrDefault("addrPhone", "")); // when retrieved the key for phone in address is addrPhone (Note P uppercase)
        data.addValue(prefix + "_city", addressDetails.getOrDefault("city", ""));
        data.addValue(prefix + "_state", addressDetails.getOrDefault("state", ""));
        data.addValue(prefix + "_zip", addressDetails.getOrDefault("zip", ""));
        Map<String, String> country = (Map<String, String>)addressDetails.get("country");
        data.addValue(prefix + "_country", country==null ? null:country.get("id"));
    }

    private void tranformJournalEntries( String entityName, Map<String,Object> values) {
        if("journalEntry".equalsIgnoreCase(entityName)){
            Optional<List<Map<String,Object>>> optionalLineEntries = (Optional<List<Map<String, Object>>>) ConnectorHelper.get(values,"line.items");
            optionalLineEntries.ifPresent(lineEntries->{
                for(Map<String, Object> lineEntry :lineEntries){
                    if(lineEntry.get("line") != null && lineEntry.get("credit") != null && Integer.valueOf(lineEntry.get("line").toString())==0 && Double.valueOf(lineEntry.get("credit").toString())>0){
                        lineEntry.forEach((lineApiName, value)->{
                            if(!"links".equalsIgnoreCase(lineApiName)){
                                String derivedApiName = "credit".equalsIgnoreCase(lineApiName) ?"amount" : lineApiName;
                                values.put("__credit_"+derivedApiName,value);
                            }
                        });
                    }else{
                        lineEntry.forEach((lineApiName, value)->{
                            if(!"links".equalsIgnoreCase(lineApiName)){
                                String derivedApiName = "debit".equalsIgnoreCase(lineApiName) ?"amount" : lineApiName;
                                values.put("__debit_"+derivedApiName,value);
                            }
                        });
                    }
                }
                values.remove("line");
            });

        }
    }

    private void transformLineItems(SyncRequest request, String entityName, Map<String,Object> values,
                                    EntityData parent, DocumentContext ctx, SearchResults results) {
        if ("salesorder".equalsIgnoreCase(entityName)) {
            List<EntityData> lineItems = extractLineItems(request, values, parent, ctx, "salesorderlineitem", "item.items", results);
            values.remove("item");
            values.put("salesorderlineitems", lineItems);
        }if ("purchaseorder".equalsIgnoreCase(entityName)) {
            List<EntityData> lineItems = extractLineItems(request, values, parent, ctx, "purchaseorderlineitem", "item.items", results);
            values.remove("item");
            values.put("purchaseorderlineitems", lineItems);
        }if ("cashsale".equalsIgnoreCase(entityName)) {
            List<EntityData> lineItems = extractLineItems(request, values, parent, ctx, "cashsalelineitem", "item.items", results);
            values.remove("item");
            values.put("cashsalelineitems", lineItems);
        }if ("creditmemo".equalsIgnoreCase(entityName)) {
            List<EntityData> lineItems = extractLineItems(request, values, parent, ctx, "creditmemolineitem", "item.items", results);
            values.remove("item");
            values.put("creditmemolineitems", lineItems);
        }if ("estimate".equalsIgnoreCase(entityName)) {
            List<EntityData> lineItems = extractLineItems(request, values, parent, ctx, "estimatelineitem", "item.items", results);
            values.remove("item");
            values.put("estimatelineitems", lineItems);
        }else if ("invoice".equalsIgnoreCase(entityName)) {
            List<EntityData> lineItems = extractLineItems(request, values, parent, ctx, "invoicelineitem", "item.items", results);
            values.remove("item");
            values.put("invoicelineitems", lineItems);
        }else if ("subscription".equalsIgnoreCase(entityName)) {
            List<EntityData> lineItems = extractLineItems(request, values, parent, ctx, "subscriptionline", "subscriptionLine.items", results);
            values.remove("subscriptionLine");
            values.put("subscriptionlines", lineItems);
            List<EntityData> priceIntervalItems = extractLineItems(request, values, parent, ctx, "priceinterval", "priceInterval.items", results);
            values.remove("priceInterval");
            values.put("priceintervals", priceIntervalItems);
        }else if ("subscriptionchangeorder".equalsIgnoreCase(entityName)) {
            List<EntityData> lineItems = extractLineItems(request, values, parent, ctx, "subscriptionchangeorderline", "subLine.items", results);
            values.remove("subLine");
            values.put("subscriptionchangeorderlines", lineItems);
        }else if ("subscriptionplan".equalsIgnoreCase(entityName)) {
            List<EntityData> lineItems = extractLineItems(request, values, parent, ctx, "subscriptionplanline", "member.items", results);
            values.remove("member");
            values.put("subscriptionplanlines", lineItems);
        }else if ("customerpayment".equalsIgnoreCase(entityName)) {
            List<EntityData> lineItems = extractLineItems(request, values, parent, ctx,
                    "customerpaymentlineitem", "apply.items",additionalPaymentItemProcessor, results);
            values.remove("item");
            values.put("customerpaymentlineitems", lineItems);
        }else if ("priceplan".equalsIgnoreCase(entityName)) {
            List<EntityData> priceTierItems = extractLineItems(request, values, parent, ctx, "pricetier", "priceTiers.items", results);
            values.remove("priceTiers");
            values.put("pricetiers", priceTierItems);
        }
        else if ("kititem".equalsIgnoreCase(entityName)) {
            List<EntityData> lineItems = extractLineItems(request, values, parent, ctx, "kititemmember", "member.items", results);
            values.remove("member");
            values.put("kititemmembers", lineItems);
        } else if ("cashrefund".equalsIgnoreCase(entityName)) {
            List<EntityData> lineItems = extractLineItems(request, values, parent, ctx, "cashrefundlineitem", "item.items", results);
            values.remove("item");
            values.put("cashrefundlineitems", lineItems);
        }
    }
    private List<EntityData> extractLineItems(SyncRequest request, Map<String, Object> values, EntityData parent,
                                              DocumentContext ctx, String lineEntityName, String path, SearchResults results) {
        return extractLineItems(request, values, parent,ctx, lineEntityName, path,(e,v)->{}, results);
    }

    private List<EntityData> extractLineItems(SyncRequest request, Map<String, Object> values, EntityData parent,
                                              DocumentContext ctx, String lineEntityName, String path, BiConsumer<EntityData, Map<String, Object>> additionalProcessor, SearchResults results) {
        CacheKey key = new CacheKey(request.getConnector(), lineEntityName);
        EntitySchema lineItemSchema = schemaCache.getUnchecked(key);
        Optional<List<HashMap<String,Object>>> optionalItemEntries =
            (Optional<List<HashMap<String, Object>>>) ConnectorHelper.get(values,path);
        return optionalItemEntries.map(lineEntries -> {
            Optional<Record> lineItemRecord = Optional.empty();
            Map<String, Map<String, Object>> legacyTaxFieldValueMap = new HashMap<>();
            if(LEGACY_TAX_SUPPORTED_ENTITIES.contains(lineEntityName)) {
                lineItemRecord = lookupRecord(parent, results);
                if(lineItemRecord.isPresent()) {
                    Record record = lineItemRecord.get();
                    if(record instanceof Estimate) {
                        EstimateItemList itemList = ((Estimate) record).getItemList();
                        if(itemList != null) {
                            for (EstimateItem item : itemList.getItem()) {
                                Map<String, Object> valueMap = new HashMap<>();
                                valueMap.put("taxCode", item.getTaxCode() != null ? item.getTaxCode().getInternalId() : null);
                                valueMap.put("taxRate1", item.getTaxRate1());
                                valueMap.put("taxRate2", item.getTaxRate2());
                                valueMap.put("tax1Amt", item.getTax1Amt());
                                valueMap.put("taxAmount", item.getTaxAmount());
                                legacyTaxFieldValueMap.put(String.valueOf(item.getLine()), valueMap);
                            }
                        }
                    } else if(record instanceof SalesOrder) {
                        SalesOrderItemList itemList = ((SalesOrder) record).getItemList();
                        if(itemList != null) {
                            for (SalesOrderItem item : itemList.getItem()) {
                                Map<String, Object> valueMap = new HashMap<>();
                                valueMap.put("taxCode", item.getTaxCode() != null ? item.getTaxCode().getInternalId() : null);
                                valueMap.put("taxRate1", item.getTaxRate1());
                                valueMap.put("taxRate2", item.getTaxRate2());
                                valueMap.put("tax1Amt", item.getTax1Amt());
                                valueMap.put("taxAmount", item.getTaxAmount());
                                legacyTaxFieldValueMap.put(String.valueOf(item.getLine()), valueMap);
                            }
                        }
                    } else if(record instanceof Invoice) {
                        InvoiceItemList itemList = ((Invoice) record).getItemList();
                        if(itemList != null) {
                            for (InvoiceItem item : itemList.getItem()) {
                                Map<String, Object> valueMap = new HashMap<>();
                                valueMap.put("taxCode", item.getTaxCode() != null ? item.getTaxCode().getInternalId() : null);
                                valueMap.put("taxRate1", item.getTaxRate1());
                                valueMap.put("taxRate2", item.getTaxRate2());
                                valueMap.put("tax1Amt", item.getTax1Amt());
                                valueMap.put("taxAmount", item.getTaxAmount());
                                legacyTaxFieldValueMap.put(String.valueOf(item.getLine()), valueMap);
                            }
                        }
                    }
                }
            }
            List<EntityData> lineItems = new ArrayList<>();
            int line = 1;
            for (HashMap<String, Object> lineEntry : lineEntries) {
                EntityData d = new EntityData();
                d.setName(lineEntityName);
                lineEntry.remove("links");
                d.setValues(new HashMap<>());
                d.setChild(true);
                d.setParentId(parent.getId());
                transformValues(request, lineItemSchema, lineEntityName, d, lineEntry, ctx, true);
                d.setId(parent.getId()+"#"+lineEntry.getOrDefault("line", String.valueOf(line++)).toString());
                d.addValue(parent.getName()+"id",parent.getId());
                d.addValue("id",d.getId());
                d.setLastModified(parent.getLastModified());
                additionalProcessor.accept(d,lineEntry);
                if(lineEntry.containsKey("line")) {
                    Map<String, Object> legacyTaxValues = legacyTaxFieldValueMap.get(String.valueOf(lineEntry.get("line")));
                    if (legacyTaxValues != null && !legacyTaxValues.isEmpty()) {
                        legacyTaxValues.forEach((k, v) -> d.addValue(k, v));
                    }
                }
                lineItems.add(d);
            }
            return lineItems;
        }).orElse(List.of());
    }

    private Optional<Record> lookupRecord(EntityData parent, SearchResults results) {
        String id = parent.getId();
        return results.getRecords().stream().filter(record -> NetSuiteSOAPService.getInternalId(record).equalsIgnoreCase(id)).findAny();
    }

    private void addSalesOrderAddresses(EntityData data, Map<String, Object> item) {
        Map<String, Object> billAddressDetails = (Map<String, Object>) item.get("billingAddress");
        addAddressValues("billingAddress", billAddressDetails, data);
        Map<String, Object> shipAddressDetails = (Map<String, Object>) item.get("shippingAddress");
        addAddressValues("shippingAddress", shipAddressDetails, data);
    }

    private void addOpptyContacts(SearchResults results, String entityName, EntityData d) {
        if("opportunity".equals(entityName)){
            List<Record> contacts = results.getReferences(d.getId(), "contact");
            List<String> contactIds = contacts.stream().map(c -> Contact.class.cast(c).getInternalId()).collect(Collectors.toList());
            if(!contactIds.isEmpty()) {
                d.addValue("contacts",contactIds);
            }
        }
    }

    private Map<String, Reference> getFieldToReferenceMap(String entityName) {
        return STANDARD_REFERENCES.getOrDefault(entityName, Set.of()).stream().collect(Collectors.toMap(r -> r.getReferenceFieldName(), r -> r));
    }

    private String getWatermarkField(SyncRequest request) {
        AttributeSchema waterMarkField = request.getEntitySchema().getWatermarkField();
        String waterMarkFieldName = "lastModifiedDate";
        if (waterMarkField != null) {
            waterMarkFieldName = waterMarkField.getApiName();
        }
        return waterMarkFieldName;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return ZonedDateTime.of(1990,1,1,0,0,0,0,ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    protected EntitySchema transformSchema(SyncRequest request){
        if(supportedChildEntities.contains(request.getEntityName())){
            return describe(new DescribeRequest(request.getConnector(), CHILD_PARENT_ENTITY_MAP.get(request.getEntityName()))).get();
        }else {
            return request.getEntitySchema();
        }
    }

    protected List<EntityData> extractChildRecords(EntitySchema parent,EntitySchema child, List<EntityData> parentRecords){
        List<EntityData> childItems = new ArrayList<>();
            parentRecords.forEach(item->{
                List<EntityData> childrenRecords = item.getChildrenRecords(resolveChildAPIName(parent.getApiName(), child.getApiName()));
                childrenRecords.forEach(r->{
                    childItems.add(r);
                });
            });
            return childItems;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        if (request.getEntitySchema().getApiName().equalsIgnoreCase("picklistValues")) {
            throw new NotSupportedException("Getbyids is not supported  for " + request.getEntitySchema().getDisplayName() + " (picklistValues)");
        }
        WsClient client = netSuiteSOAPService.getClient(request.getConnector());
        List<EntityData> records = request.getData().getOrDefault(request.getConnector().getId(),List.of());
        if(request.getEntityName().startsWith(SAVED_SEARCH_PREFIX)) {
            if(request.getEntitySchema().getIdField().getApiName().equalsIgnoreCase("id")) {
                throw new NotSupportedException("Getbyids is not supported when using default id field");
            }
            Set<String> ids = records.stream()
                    .map(entityData -> entityData.getId()).collect(Collectors.toSet());
            List<EntityData> response = netSuiteSOAPService.getAllSavedSearchRecords(client, request.getEntityName(), request);
            List<EntityData> results = new ArrayList<>();
            response.forEach(ed -> {
                if(ids.contains(ed.getId())) {
                    results.add(ed);
                }
            });
            return results;
        }
        List<List<EntityData>> partitions = ListUtils.partition(records, 1000);
        List<EntityData> results = new ArrayList<>();
        partitions.forEach(partition ->{
            List<String> ids = partition.stream()
                    .map(entityData -> entityData.getId()).collect(Collectors.toList());

            if(request.getEntityName().equalsIgnoreCase("subsidiary")) {
                updateWithSOAPResponse(request, client, results, Optional.of(ids));
                return;
            }

            //if parent ids are present, use parent ids
            Set<String> parentIds = new HashSet<>();
            partition.forEach(p -> {
                        p.setId(p.getId().split("#")[0]);
                        parentIds.add(p.getId());
                    }
            );
            SyncRequest partitionedRequest = request.withData(Map.of(request.getConnector().getId(),partition))
                    .withEntitySchema(transformSchema(request));
            log.debug("Partitioned request is : {} and ", partitionedRequest);
            SearchResults searchResults = NO_WM_ENTITIES.contains(request.getEntityName()) ? new SearchResults(new ArrayList<String>(parentIds), 0, parentIds.size(), new ArrayList<>()) :
                    netSuiteSOAPService.listByIds(client, partitionedRequest);
            List<EntityData> items = List.of();
            if("paycheckjournal".equalsIgnoreCase(request.getEntityName())) {
              items = netSuiteSOAPService.toPaycheckJournalData(client, searchResults);
            } else if("binworksheet".equalsIgnoreCase(request.getEntityName())) {
              items = netSuiteSOAPService.toBinWorksheetData(client, searchResults);
            } else {
              items = getItems(partitionedRequest, new ArrayList<>(parentIds), searchResults);
            }
            if("contact".equalsIgnoreCase(request.getEntityName())) {
                netSuiteSOAPService.updateContactWithAddressDetails(items, searchResults);
            }
            if("customer".equalsIgnoreCase(request.getEntityName())) {
                netSuiteSOAPService.updateCustomerWithAltName(items, searchResults);
            }
            if(supportedChildEntities.contains(request.getEntityName())){
                List<EntityData> childItems = extractChildRecords(partitionedRequest.getEntitySchema(),request.getEntitySchema(),items);
                results.addAll(childItems.stream().filter(c->ids.contains(c.getId())).collect(Collectors.toList()));
            }else {
                results.addAll(items);
            }
        });
        return results;
    }

    private String resolveChildAPIName(String parentEntityName, String childEntityName) {
        return CHILD_API_NAMES.getOrDefault(parentEntityName,Map.of()).get(childEntityName);
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        if (request.getEntityName().equalsIgnoreCase(Constants.CONTACT)) {
            return insertContact(request);
        } else {
            return insert(request);
        }
    }

    private String getIdFromLocation(String url, String entityName) {
        String id = "";
        Pattern p = Pattern.compile(format(LOCATION_PATTERN, entityName), Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(url);
        if (m.find()) {
            try {
                id = m.group(1);
            } catch (IndexOutOfBoundsException e) {
                log.error("url does not have id: " + url + " with exception message " + e.getMessage());
                throw new RuntimeException("Invalid endpoint url: " + url + ". Format should be https://ACCOUNT_ID.suitetalk.api.netsuite.com");
            }
        }
        return id;
    }

    private SyncResponse insertContact(SyncRequest request) {
        return insert(request);
    }

    private Result insertSingleRecord(SyncRequest request, NetSuiteRestClient restClient, EntityData entityData){
        ConnectorInfo connector = request.getConnector();
        AuthConfig auth = connector.getAuthConfig();

        String json = "";
        try {
            // Add syncariID as an externalId for the request so that retry wont create duplicates
            Map<String, Object> payload = preprocess(request, addAddresses(fixDateAndTime(request, entityData)));
            if(StringUtils.isBlank((String)payload.get("externalId")) && StringUtils.isNotBlank(entityData.getSyncariEntityId()) ){
                payload.put("externalId", entityData.getSyncariEntityId());
            }
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.debug("Failed parsing json with message: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
        log.debug(json);

        try{
            String recordCreateUrl = format(RECORD_URL, connector.getEndpoint(), VERSION, request.getEntityName());
            ResponseEntity<String> restResponse = restClient.postRaw(recordCreateUrl, json, auth);
            List<String> location = restResponse.getHeaders().get("Location");
            String id = "";
            if (!location.isEmpty()) {
                id = getIdFromLocation(location.get(0), request.getEntityName());
            } else {
                throw new RuntimeException("Expecting a location in the header, found none.");
            }
            if(restResponse.getStatusCode().is2xxSuccessful() && LEGACY_TAX_SUPPORTED_PARENT_ENTITIES.contains(request.getEntityName())) {
                Pair<Boolean, String> response = updateLinesWithLegacyTaxDetails(request, entityData, id);
                if(!response.x) {
                    Result result = new Result(false, id, entityData.getSyncariEntityId());
                    result.addError(response.y);
                    return result;
                }
            }
            Result result = new Result(true, id, entityData.getSyncariEntityId());
            return result;
        } catch (NonRetriableException ex) {
            String externalId = entityData.getValueAsString("externalId");
            if (StringUtils.isBlank(externalId)){
                externalId =entityData.getSyncariEntityId();
            }
            // If the externalId already exists, get the id by externalID and associate with the record
            if (ErrorCodes.BAD_REQUEST.name().equals(ex.getErrorCode())
                    && StringUtils.isNotBlank(ex.getMessage())
                    && ex.getMessage().contains("Error while accessing a resource. This record already exists")
                    && StringUtils.isNotBlank(externalId)
            ) {
                String recordGetUrl = format(SINGLE_RECORD_URL, connector.getEndpoint(), VERSION, request.getEntityName(), "eid:"+externalId);
                ResponseEntity<String> restResponse = restClient.getResponse(recordGetUrl, auth);
                restClient.checkResponse(restResponse);
                String body = restResponse.getBody();
                DocumentContext ctx = JsonPath.parse(body);
                String id = ctx.read("id").toString();
                Result result = new Result(true, id, entityData.getSyncariEntityId());
                return result;
            } else {
                throw ex;
            }
        }
    }

    private Pair<Boolean, String> updateLinesWithLegacyTaxDetails(SyncRequest request, EntityData entityData, String id) {
        List<Pair<Boolean, String>> responses = new ArrayList<>();
        if(checkChildRecords(entityData)) {
            WsClient client = netSuiteSOAPService.getClient(request.getConnector());
            SearchResults searchResults = getSearchResults(request, id, client);
            if (searchResults.getRecords().size() == 1) {
                CHILD_API_NAMES.get(request.getEntityName()).forEach((k, v) -> {
                    if (LEGACY_TAX_SUPPORTED_ENTITIES.contains(k)) {
                        responses.add(updateLinesWithLegacyTaxDetails(entityData, v, searchResults.getRecords().get(0), client));
                    }
                });
            } else {
                log.error("Invalid number of {} for id -  {}. Not updating legacy tax fields", request.getEntityName(), id);
            }
        }
        if(!responses.isEmpty()) {
            List<String> failedResponses = responses.stream().filter(resp -> !resp.x).map(resp -> resp.y).collect(Collectors.toList());
            if(!failedResponses.isEmpty()) {
                String errors = String.join(",", failedResponses);
                return Pair.of(false, errors);
            } else {
                return Pair.of(true, "Legacy tax details updated");
            }
        } else {
            return Pair.of(true, "Nothing to update");
        }
    }

    private SearchResults getSearchResults(SyncRequest request, String id, WsClient client) {
        SyncRequest getByIdRequest = new SyncRequest()
                .setConnector(request.getConnector())
                .setEntitySchema(request.getEntitySchema())
                .setData(Map.of(request.getConnector().getId(), List.of(new EntityData(request.getEntityName()).setId(id))));

        SearchResults searchResults = netSuiteSOAPService.listByIds(client, getByIdRequest);
        return searchResults;
    }

    private boolean checkChildRecords(EntityData entityData) {
        for(Map.Entry<String, String> childEntity: CHILD_API_NAMES.get(entityData.getName()).entrySet()) {
            if(LEGACY_TAX_SUPPORTED_ENTITIES.contains(childEntity.getKey())) {
                for (EntityData child : entityData.getChildrenRecords(childEntity.getValue())) {
                    if (child.getValues().entrySet().stream()
                            .anyMatch(entry -> LEGACY_TAX_FIELDS.contains(entry.getKey()) && entry.getValue() != null))
                        return true;
                }
            }
        };
        return false;
    }

    private Pair<Boolean, String> updateLinesWithLegacyTaxDetails(EntityData entityData, String childEntityName, Record record, WsClient client) {
        if(record instanceof Estimate) {
            Estimate estimate = (Estimate) record;
            List<EstimateItem> updatedItems = new ArrayList<>();
            for(EntityData childEntityData: entityData.getChildrenRecords(childEntityName)) {
                EstimateItemList estimateItemList = estimate.getItemList();
                Long line = (Long) childEntityData.getValue("line");
                // Check if this is an item group
                String itemInternalId = childEntityData.getValueAsString("item");
                Set<String> itemGroupMembers = new HashSet<>();
                try {
                    getItemGroupMembers(client, itemInternalId, itemGroupMembers);
                } catch (Exception e) {
                    return Pair.of(false, "Failed to fetch item group id - " + itemInternalId + ", " + e.getMessage());
                }
                for(EstimateItem estimateItem : estimateItemList.getItem()) {
                    // Check if line numbers are equal, the line item is not a subscription and the item ids also match.
                    if(estimateItem.getSubscription() == null && ((Objects.equals(estimateItem.getLine(), line) && itemInternalId.equalsIgnoreCase(estimateItem.getItem().getInternalId())) ||
                            itemGroupMembers.contains(estimateItem.getItem().getInternalId()))) {
                        updatedItems.add(updateEstimateLineItem(childEntityData, estimateItem));
                    }
                }
            }
            if(updatedItems.isEmpty()) {
                return Pair.of(true, "Nothing to update");
            }
            Estimate updatedRecord = new Estimate();
            updatedRecord.setInternalId(estimate.getInternalId());
            EstimateItemList estimateItemList = new EstimateItemList();
            estimateItemList.setItem(updatedItems.toArray(new EstimateItem[updatedItems.size()]));
            estimateItemList.setReplaceAll(false);
            updatedRecord.setItemList(estimateItemList);
            return writeToNS(entityData, updatedRecord, client);
        }
        if(record instanceof Invoice) {
            Invoice invoice = (Invoice) record;
            List<InvoiceItem> updatedItems = new ArrayList<>();
            for(EntityData childEntityData: entityData.getChildrenRecords(childEntityName)) {
                InvoiceItemList invoiceItemList = invoice.getItemList();
                Long line = (Long) childEntityData.getValue("line");
                // Check if this is an item group
                String itemInternalId = childEntityData.getValueAsString("item");
                Set<String> itemGroupMembers = new HashSet<>();
                try {
                    getItemGroupMembers(client, itemInternalId, itemGroupMembers);
                } catch (Exception e) {
                    return Pair.of(false, "Failed to fetch item group id - " + itemInternalId + ", " + e.getMessage());
                }
                for(InvoiceItem invoiceItem : invoiceItemList.getItem()) {
                    if((Objects.equals(invoiceItem.getLine(), line) && itemInternalId.equalsIgnoreCase(invoiceItem.getItem().getInternalId())) ||
                            itemGroupMembers.contains(invoiceItem.getItem().getInternalId())) {
                        updatedItems.add(updateInvoiceLineItem(childEntityData, invoiceItem));
                    }
                }
            }
            if(updatedItems.isEmpty()) {
                return Pair.of(true, "Nothing to update");
            }
            Invoice updatedRecord = new Invoice();
            updatedRecord.setInternalId(invoice.getInternalId());
            InvoiceItemList invoiceItemList = new InvoiceItemList();
            invoiceItemList.setItem(updatedItems.toArray(new InvoiceItem[updatedItems.size()]));
            invoiceItemList.setReplaceAll(false);
            updatedRecord.setItemList(invoiceItemList);
            return writeToNS(entityData, updatedRecord, client);
        }
        if(record instanceof SalesOrder) {
            SalesOrder salesOrder = (SalesOrder) record;
            List<SalesOrderItem> updatedItems = new ArrayList<>();
            for(EntityData childEntityData: entityData.getChildrenRecords(childEntityName)) {
                SalesOrderItemList salesOrderItemList = salesOrder.getItemList();
                Long line = (Long) childEntityData.getValue("line");
                // Check if this is an item group
                String itemInternalId = childEntityData.getValueAsString("item");
                Set<String> itemGroupMembers = new HashSet<>();
                try {
                    getItemGroupMembers(client, itemInternalId, itemGroupMembers);
                } catch (Exception e) {
                    return Pair.of(false, "Failed to fetch item group id - " + itemInternalId + ", " + e.getMessage());
                }
                for(SalesOrderItem salesOrderItem : salesOrderItemList.getItem()) {
                    if(salesOrderItem.getSubscription() == null && ((Objects.equals(salesOrderItem.getLine(), line) && itemInternalId.equalsIgnoreCase(salesOrderItem.getItem().getInternalId())) ||
                            (itemGroupMembers.contains(salesOrderItem.getItem().getInternalId())))) {
                        updatedItems.add(updateSalesOrderLineItem(childEntityData, salesOrderItem));
                    }
                }
            }
            if(updatedItems.isEmpty()) {
                return Pair.of(true, "Nothing to update");
            }
            SalesOrder updatedRecord = new SalesOrder();
            updatedRecord.setInternalId(salesOrder.getInternalId());
            SalesOrderItemList salesOrderItemList = new SalesOrderItemList();
            salesOrderItemList.setItem(updatedItems.toArray(new SalesOrderItem[updatedItems.size()]));
            salesOrderItemList.setReplaceAll(false);
            updatedRecord.setItemList(salesOrderItemList);
            return writeToNS(entityData, updatedRecord, client);
        }
        return Pair.of(false, "Update to Legacy tax field not support for this entity");
    }

    private void getItemGroupMembers(WsClient client, String itemInternalId, Set<String> itemGroupMembers) throws RemoteException {
        if(itemInternalId == null) return;
        Record itemGroupRecord = client.getRecord(itemInternalId, RecordType.itemGroup);
        if(itemGroupRecord != null) {
            ItemGroup itemGroup = (ItemGroup) itemGroupRecord;
            for (ItemMember itemMember: itemGroup.getMemberList().getItemMember()) {
                itemGroupMembers.add(itemMember.getItem().getInternalId());
            }
        }
    }

    private Pair<Boolean, String> writeToNS(EntityData entityData, Record record, WsClient client) {
        try {
            WriteResponse response = client.callUpdateRecord(record);
            if(!response.getStatus().isIsSuccess()) {
                String errorMessage = "";
                if(response.getStatus() != null && response.getStatus().getStatusDetail() != null) {
                    for(StatusDetail statusDetail: response.getStatus().getStatusDetail()) {
                        errorMessage = errorMessage + statusDetail.getMessage() + ", ";
                    }
                }
                String finalErrorMessage = String.format("Error updating legacy tax details for entity %s for id %s with status - %s", entityData.getName(), entityData.getId(), errorMessage);
                log.error(finalErrorMessage);
                return Pair.of(false, finalErrorMessage);
            }
        } catch (RemoteException e) {
            throw new RuntimeException(String.format("Error updating legacy tax details for entity %s for id %s with error - %s",
                    entityData.getName(), entityData.getId(), e.getMessage()));
        }
        return Pair.of(true, "Legacy tax details updated");
    }

    private EstimateItem updateEstimateLineItem(EntityData childEntityData, EstimateItem estimateItem) {
        EstimateItem updatedEstimateItem = new EstimateItem();
        updatedEstimateItem.setItem(estimateItem.getItem());
        updatedEstimateItem.setLocation(estimateItem.getLocation());
        updatedEstimateItem.setLine(estimateItem.getLine());
        updatedEstimateItem.setTaxCode(createRecordRef(childEntityData.getValueAsString("taxCode")));
        updatedEstimateItem.setTax1Amt(convertToDouble(childEntityData.getValue("tax1Amt")));
        updatedEstimateItem.setTaxAmount(convertToDouble(childEntityData.getValue("taxAmount")));
        updatedEstimateItem.setTaxRate1(convertToDouble(childEntityData.getValue("taxRate1")));
        updatedEstimateItem.setTaxRate2(convertToDouble(childEntityData.getValue("taxRate2")));
        return updatedEstimateItem;
    }

    private InvoiceItem updateInvoiceLineItem(EntityData childEntityData, InvoiceItem invoiceItem) {
        InvoiceItem updatedInvoiceItem = new InvoiceItem();
        updatedInvoiceItem.setItem(invoiceItem.getItem());
        updatedInvoiceItem.setLocation(invoiceItem.getLocation());
        updatedInvoiceItem.setLine(invoiceItem.getLine());
        updatedInvoiceItem.setTaxCode(createRecordRef(childEntityData.getValueAsString("taxCode")));
        updatedInvoiceItem.setTax1Amt(convertToDouble(childEntityData.getValue("tax1Amt")));
        updatedInvoiceItem.setTaxAmount(convertToDouble(childEntityData.getValue("taxAmount")));
        updatedInvoiceItem.setTaxRate1(convertToDouble(childEntityData.getValue("taxRate1")));
        updatedInvoiceItem.setTaxRate2(convertToDouble(childEntityData.getValue("taxRate2")));
        return updatedInvoiceItem;
    }

    private SalesOrderItem updateSalesOrderLineItem(EntityData childEntityData, SalesOrderItem salesOrderItem) {
        SalesOrderItem updatedSalesOrderItem = new SalesOrderItem();
        updatedSalesOrderItem.setItem(salesOrderItem.getItem());
        updatedSalesOrderItem.setLocation(salesOrderItem.getLocation());
        updatedSalesOrderItem.setLine(salesOrderItem.getLine());
        updatedSalesOrderItem.setTaxCode(createRecordRef(childEntityData.getValueAsString("taxCode")));
        updatedSalesOrderItem.setTax1Amt(convertToDouble(childEntityData.getValue("tax1Amt")));
        updatedSalesOrderItem.setTaxAmount(convertToDouble(childEntityData.getValue("taxAmount")));
        updatedSalesOrderItem.setTaxRate1(convertToDouble(childEntityData.getValue("taxRate1")));
        updatedSalesOrderItem.setTaxRate2(convertToDouble(childEntityData.getValue("taxRate2")));
        return updatedSalesOrderItem;
    }

    private Double convertToDouble(Object value) {
        if(value instanceof Long) {
            return ((Long)value).doubleValue();
        }
        if(value instanceof Integer) {
            return ((Integer)value).doubleValue();
        }
        return null;
    }

    private SyncResponse insert(SyncRequest request) {
        List<EntityData> toBeCreated = request.getData().get(request.getConnector().getId());
        log.debug(toBeCreated.toString());
        List<Result> results = new ArrayList<>();
        SyncResponse response = new SyncResponse();
        NetSuiteRestClient restClient = getNetSuiteRestClient();
        restClient.addHeader(CONTENT_TYPE, APPLICATION_JSON);
        boolean isSuccess=true;
        for (EntityData create : toBeCreated) {
            //remove oppty contacts, because they needspecial handling
            List<String> contacts =  "opportunity".equals(create.getName()) ? (List<String>) create.getValues().remove("contacts") : List.of();
            try {
                Result result = insertSingleRecord(request, restClient, create);
                results.add(result);
                addLineItemResults(result, create, "salesorderlineitems");
                addLineItemResults(result, create, "purchaseorderlineitems");
                addLineItemResults(result, create, "cashsalelineitems");
                addLineItemResults(result, create, "creditmemolineitems");
                addLineItemResults(result, create, "estimatelineitems");
                addLineItemResults(result, create, "invoicelineitems");
                addLineItemResults(result, create, "customerpaymentlineitems");
                addLineItemResults(result, create, "cashrefundlineitems");
                addLineItemResults(result, create, "priceintervals");
                addLineItemResults(result, create, "subscriptionlines");
                addLineItemResults(result, create, "subscriptionchangeorderlines");
                addLineItemResults(result, create, "pricetiers");
                addLineItemResults(result, create, "kititemmembers");
                attachContactsToOppties(request, create, result.getId(), contacts);
            } catch (NonRetriableException ex) {
                isSuccess = false;
                ex.getStatusCode();
                results.add(new Result(false, null, create.getSyncariEntityId()).addError(ex.getMessage()));
            }catch(UnknownException ex){
                isSuccess = false;
                results.add(new Result(false, null, create.getSyncariEntityId()).addError(ex.getMessage()));
            }
        }
        response.setSuccess(isSuccess);
        response.setResults(results);
        log.info("Created {} records in {} in {} connector, status {}, errors {}",
                response.getResults().size(), request.getEntityName(),
                request.getConnector().getName(), isSuccess, response.getErrors());
        return response;
    }
    private void addLineItemResults(Result result, EntityData parentRecord, String childApiName) {
        List<EntityData> childrenRecords = parentRecord.getChildrenRecords(childApiName);
        for(int i=0;i<childrenRecords.size();i++){
            //Line items start at index 1 and are in the insert order
            //this is a short cut to avoid having to read the record back and match using values
            result.addChildResult(childApiName,new Result(true,result.getId()+"#"+(i+1),childrenRecords.get(i).getSyncariEntityId()));
        }
    }

    private Map<String, Object> addAddresses(Map<String, Object> entityData) {
        Map<String, Object> addressItems = toAddressBook(entityData);
        if(!addressItems.isEmpty()){
            entityData.put("addressBook",addressItems);
        }
        return entityData;
    }

    private boolean isNullSupportedAttribute(String entityName, String attributeName){
        return NULL_SUPPORTED_ENTITY_FIELDS.getOrDefault(entityName.toLowerCase(), new HashSet<String>()).contains(attributeName.toString());
    }

    private Map<String, Object> preprocess(SyncRequest request, Map<String, Object> payload) {
        Set<String> nullKeysToRemove = new HashSet<>();
        final Map<String, Object> attributeProperties = request.getEntitySchema().getAdditionalProperty("attributeProperties");
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String apiName = entry.getKey();
            Object value = entry.getValue();
            if (value == null && !isNullSupportedAttribute(request.getEntityName(), apiName)) {
                nullKeysToRemove.add(apiName);
            }

            //we now handle multivalued "object" types. These are reference fields, and we don't know what they refer to.
            // The pipeline typically handles the array/object creation
            // The pipeline sends them as an array of objects
            // [{"id":12345},{"id":34567}] or [{"refName":"Name1"},{"refName":"Name2"}]
            // and we need to send
            //  {
            //      "items":[{"id":12345},{"id":34567}]
            //  }
            //This logic is valid for all "object" types, but we'll relax this later, for the fear of regressions
            // and only deal with multivalued polymorphic ref fields here
            request.getEntitySchema().getField(apiName).ifPresent(field -> {
                if (value != null && "object".equalsIgnoreCase(field.getDataType()) && field.isMultiValueField() && isPolymorphicReference(attributeProperties, apiName)) {
                    // only do it for fields that have not been "processed" elsewhere, like addressBook
                    if (List.class.isAssignableFrom(entry.getValue().getClass())) {
                        List<Map<String, Object>> items = (List<Map<String, Object>>) List.class.cast(entry.getValue()).stream()
                                .filter(Objects::nonNull)
                                .map(e -> {
                                    if (Map.class.isAssignableFrom(e.getClass())) {
                                        return e;
                                    }
                                    return Map.of("id", e);
                                })
                                .collect(Collectors.toList());
                        entry.setValue(Map.of("items", items));
                    }
                }
            });
        }
        nullKeysToRemove.forEach(apiName -> payload.remove(apiName));
        // If we are updating with empty list values, we get an exception. Remove such entries
        removeEmptyListValues(payload);
        if (request.getEntitySchema().getApiName().equalsIgnoreCase("journalEntry")) {
            return preprocessJournalEntries(request, payload);
        } else if (request.getEntitySchema().getApiName().equalsIgnoreCase("salesorder")) {
            return preprocessLine(request, payload, "salesorderlineitems");
        } else if (request.getEntitySchema().getApiName().equalsIgnoreCase("purchaseorder")) {
            return preprocessLine(request, payload, "purchaseorderlineitems");
        } else if (request.getEntitySchema().getApiName().equalsIgnoreCase("cashsale")) {
            return preprocessLine(request, payload, "cashsalelineitems");
        } else if (request.getEntitySchema().getApiName().equalsIgnoreCase("creditmemo")) {
            return preprocessLine(request, payload, "creditmemolineitems");
        } else if (request.getEntitySchema().getApiName().equalsIgnoreCase("estimate")) {
            return preprocessEstimate(request, payload);
        } else if (request.getEntitySchema().getApiName().equalsIgnoreCase("invoice")) {
            return preprocessInvoice(request, payload);
        } else if (request.getEntitySchema().getApiName().equalsIgnoreCase("customerpayment")) {
            return preprocessCustomerPayment(request, payload);
        } else if (request.getEntitySchema().getApiName().equalsIgnoreCase("cashrefund")) {
            return preprocessCashRefund(request, payload);
        } else if (request.getEntitySchema().getApiName().equalsIgnoreCase("subscription")) {
            return preprocessSubscription(request, payload);
        } else if (request.getEntitySchema().getApiName().equalsIgnoreCase("subscriptionchangeorder")) {
            return preprocessSubscriptionChangeOrder(request, payload);
        } else if (request.getEntitySchema().getApiName().equalsIgnoreCase("subscriptionplan")) {
            return preprocessSubscriptionPlan(request, payload);
        } else if (request.getEntitySchema().getApiName().equalsIgnoreCase("priceplan")) {
            return preprocessPriceplan(request, payload);
        } else if (request.getEntitySchema().getApiName().equalsIgnoreCase("kititem")) {
            return preprocessKitItem(request, payload);
        } else {
            return payload;
        }
    }

    private static boolean isPolymorphicReference(Map<String, Object> attributeProperties, String apiName) {
        if (attributeProperties != null && attributeProperties.get(apiName) != null) {
            Map<String, Object> props = (Map<String, Object>) attributeProperties.get(apiName);
            if (props != null && !props.isEmpty() && props.get("originalType") != null &&
                    "polymorphicreference".equalsIgnoreCase(props.get("originalType").toString())) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> preprocessJournalEntries(SyncRequest request, Map<String, Object> payload) {
        Map<String, Object> creditLine = new HashMap<>();
        Map<String, Object> debitLine = new HashMap<>();
        Set<String> keysToDelete = new HashSet<>();
        String creditLineFieldPrefix = "__credit_";
        String debitLineFieldPrefix = "__debit_";
        payload.forEach((apiName,value)->{
            boolean isCreditLineField = apiName.startsWith(creditLineFieldPrefix);
            boolean isDebitLineField = apiName.startsWith(debitLineFieldPrefix);
            if(isCreditLineField || isDebitLineField){
                Map<String, Object> line = isCreditLineField ? creditLine : debitLine;
                Optional<AttributeSchema> optionalField = request.getEntitySchema().getField(apiName);
                String originalApiName = apiName.replace(creditLineFieldPrefix,"").replace(debitLineFieldPrefix,"");
                optionalField.ifPresent(field->{
                    if ("amount".equalsIgnoreCase(originalApiName)){
                        if(isCreditLineField) {
                            line.put("credit", value);
                        }else{
                            line.put("debit", value);
                        }
                    }else{
                        line.put(originalApiName, value);
                    }
                });
                keysToDelete.add(apiName);
            }
        });
        keysToDelete.forEach(key -> payload.remove(key));
        payload.put("line",Map.of("items",List.of(creditLine,debitLine)));
        return payload;
    }

    private Map<String, Object> preprocessLine(SyncRequest request, Map<String, Object> payload, String name) {
        payload.put("billingAddress", preprocessAddress("billingAddress", payload));
        payload.put("shippingAddress", preprocessAddress("shippingAddress", payload));
        // This will throw an API exception if there are no SO line items.
        if (!payload.containsKey(name)) return payload;
        List<Map<String, Object>> lineItems = new ArrayList<>();
        AtomicLong line = new AtomicLong(1);
        ((List) payload.get(name)).forEach(lineItem -> {
            EntityData lineItemRecord = (EntityData) lineItem;
            var l = lineItemRecord.getValues();
            //add line item as id
            if(lineItemRecord.getId()!=null) {
                String lineItemId = lineItemRecord.getId();
                String[] parts = lineItemId.split("#");
                l.put("line", Integer.valueOf(parts.length>1?parts[1]:lineItemId));
            } else {
                l.put("line", line.getAndIncrement());
            }
            if (!lineItemRecord.isDeleted()) {
                lineItems.add(l);
            }
        });
        payload.remove(name);
        payload.put("item", Map.of("items", lineItems));
        return payload;
    }

    private Map<String, Object> preprocessCashRefund(SyncRequest request, Map<String, Object> payload) {
        // This will throw an API exception if there are no SO line items.
        String name = "cashrefundlineitems";
        if (!payload.containsKey(name)) return payload;
        List<Map<String, Object>> lineItems = new ArrayList<>();
        ((List) payload.get(name)).forEach(lineItem -> {
            EntityData lineItemRecord = (EntityData) lineItem;
            var l = lineItemRecord.getValues();
            //add line item as id
            l.put("item", Map.of("id", Integer.valueOf(lineItemRecord.getValueAsString("item"))));
            if (l.containsKey("line")) {
                l.put("line", l.get("line"));
            }
            if (!lineItemRecord.isDeleted()) {
                lineItems.add(l);
            }
        });
        payload.remove(name);
        payload.put("item", Map.of("items", lineItems));
        return payload;
    }

    private Map<String, Object> preprocessEstimate(SyncRequest request, Map<String, Object> payload) {
        Map<String, Object> billingAddress = preprocessEstimateAddress("billingAddress", payload);
        if(!billingAddress.isEmpty()) {
            payload.put("billingAddress", billingAddress);
        }
        Map<String, Object> shippingAddress = preprocessEstimateAddress("shippingAddress", payload);
        if(!shippingAddress.isEmpty()) {
            payload.put("shippingAddress", shippingAddress);
        }
        // This will throw an API exception if there are no Estimate line items.
        if (!payload.containsKey("estimatelineitems")) return payload;
        List<Map<String, Object>> lineItems = new ArrayList<>();
        AtomicLong line = new AtomicLong(1);
        ((List) payload.get("estimatelineitems")).forEach(lineItem -> {
            EntityData lineItemRecord = (EntityData) lineItem;
            var l = lineItemRecord.getValues();
            //add line item as id
            if(lineItemRecord.getId()!=null) {
                String lineItemId = lineItemRecord.getId();
                String[] parts = lineItemId.split("#");
                l.put("line", Integer.valueOf(parts.length>1?parts[1]:lineItemId));
            } else {
                l.put("line", line.getAndIncrement());
            }
            if (!lineItemRecord.isDeleted()) {
                lineItems.add(l);
            }
        });
        payload.remove("estimatelineitems");
        payload.put("item", Map.of("items", lineItems));
        return payload;
    }

    private Map<String, Object> preprocessInvoice(SyncRequest request, Map<String, Object> payload) {
        // This will throw an API exception if there are no SO line items.
        if (!payload.containsKey("invoicelineitems")) return payload;
        List<Map<String, Object>> lineItems = new ArrayList<>();
        AtomicLong line = new AtomicLong(1);
        ((List) payload.get("invoicelineitems")).forEach(lineItem -> {
            EntityData lineItemRecord = (EntityData) lineItem;
            var l = lineItemRecord.getValues();
            //add line item as id
            if(lineItemRecord.getId()!=null) {
                String lineItemId = lineItemRecord.getId();
                String[] parts = lineItemId.split("#");
                l.put("line", Integer.valueOf(parts.length>1?parts[1]:lineItemId));
            } else {
                l.put("line", line.getAndIncrement());
            }
            if (!lineItemRecord.isDeleted()) {
                lineItems.add(l);
            }
        });
        payload.remove("invoicelineitems");
        payload.put("item", Map.of("items", lineItems));
        return payload;
    }
    private Map<String, Object> preprocessCustomerPayment(SyncRequest request, Map<String, Object> payload) {
        // This will throw an API exception if there are no SO line items.
        if (!payload.containsKey("customerpaymentlineitems")) return payload;
        List<Map<String, Object>> lineItems = new ArrayList<>();
        ((List) payload.get("customerpaymentlineitems")).forEach(lineItem -> {
            EntityData lineItemRecord = (EntityData) lineItem;
            var l = lineItemRecord.getValues();

            //add line item as id
            if(lineItemRecord.getId()!=null) {
                String lineItemId = lineItemRecord.getId();
                String[] parts = lineItemId.split("#");
                l.put("line", Integer.valueOf(parts.length>1?parts[1]:lineItemId));
            }
            String invoiceId = lineItemRecord.getValueAsString("invoiceId");
            l.put("doc",Map.of("id",invoiceId));
            l.remove("invoiceId");
            if (!lineItemRecord.isDeleted()) {
                lineItems.add(l);
            }
        });
        payload.remove("customerpaymentlineitems");
        payload.put("apply", Map.of("items", lineItems));
        return payload;
    }

    private Map<String, Object> preprocessSubscription(SyncRequest request, Map<String, Object> payload) {
        // This will throw an API exception if there are no SO line items.
        if (!payload.containsKey("subscriptionlines") && !payload.containsKey("priceintervals")) return payload;
        if (payload.containsKey("subscriptionlines")) {
            List<Map<String, Object>> lineItems = new ArrayList<>();
            final Optional<EntitySchema> subscriptionlinesSchema = request.getEntitySchema().getField("subscriptionlines").map(a -> a.getChildSchema());
            ((List) payload.get("subscriptionlines")).forEach(lineItem -> {
                EntityData lineItemRecord = (EntityData) lineItem;
                var l = lineItemRecord.getValues();
                subscriptionlinesSchema.ifPresent(schema -> fixReferenceFormats(schema, l));
                //add line item as id
                if (lineItemRecord.getId() != null) {
                    String lineItemId = lineItemRecord.getId();
                    String[] parts = lineItemId.split("#");
                    l.put("line", Integer.valueOf(parts.length > 1 ? parts[1] : lineItemId));
                }
                if(l.get("item") != null) {
                    l.put("item", Map.of("id", l.get("item")));
                }
                if (!lineItemRecord.isDeleted()) {
                    lineItems.add(l);
                }
            });
            payload.remove("subscriptionlines");
            payload.put("subscriptionLine", Map.of("items", lineItems));
        }

        if (payload.containsKey("priceintervals")) {
            final Optional<EntitySchema> priceintervalsSchema = request.getEntitySchema().getField("priceintervals").map(a -> a.getChildSchema());
            List<Map<String, Object>> priceIntervals = new ArrayList<>();
            ((List) payload.get("priceintervals")).forEach(lineItem -> {
                EntityData lineItemRecord = (EntityData) lineItem;
                var l = lineItemRecord.getValues();
                priceintervalsSchema.ifPresent(schema -> fixReferenceFormats(schema, l));
                //add line item as id
                if (lineItemRecord.getId() != null) {
                    String lineItemId = lineItemRecord.getId();
                    String[] parts = lineItemId.split("#");
                    l.put("line", Integer.valueOf(parts.length > 1 ? parts[1] : lineItemId));
                    l.remove("id");
                }
                if (!lineItemRecord.isDeleted()) {
                    priceIntervals.add(l);
                }
            });
            payload.remove("priceintervals");
            payload.put("priceInterval", Map.of("items", priceIntervals));
        }

        return payload;
    }

    private void fixReferenceFormats(EntitySchema schema, Map<String, Object> values) {
        schema.getReferenceFields().forEach(refField -> {
            if (values.get(refField.getApiName()) != null) {
                values.put(refField.getApiName(), new NSReference(values.get(refField.getApiName()).toString()));
            }
        });
    }

    private Map<String, Object> preprocessSubscriptionChangeOrder(SyncRequest request, Map<String, Object> payload) {
        // This will throw an API exception if there are no SO line items.
        if (!payload.containsKey("subscriptionchangeorderlines")) return payload;
        List<Map<String, Object>> lineItems = new ArrayList<>();
        ((List) payload.get("subscriptionchangeorderlines")).forEach(lineItem -> {
            EntityData lineItemRecord = (EntityData) lineItem;
            var l = lineItemRecord.getValues();
            //add line item as id
            if(lineItemRecord.getId()!=null) {
                String lineItemId = lineItemRecord.getId();
                String[] parts = lineItemId.split("#");
                l.put("line", Integer.valueOf(parts.length>1?parts[1]:lineItemId));
            }
            if (!lineItemRecord.isDeleted()) {
                lineItems.add(l);
            }
        });
        payload.remove("subscriptionchangeorderlines");
        payload.put("item", Map.of("items", lineItems));
        return payload;
    }

    private Map<String, Object> preprocessSubscriptionPlan(SyncRequest request, Map<String, Object> payload) {
        // This will throw an API exception if there are no SO line items.
        if (!payload.containsKey("subscriptionplanlines")) return payload;
        List<Map<String, Object>> lineItems = new ArrayList<>();
        ((List) payload.get("subscriptionplanlines")).forEach(lineItem -> {
            EntityData lineItemRecord = (EntityData) lineItem;
            var l = lineItemRecord.getValues();
            //add line item as id
            if(lineItemRecord.getId()!=null) {
                String lineItemId = lineItemRecord.getId();
                String[] parts = lineItemId.split("#");
                l.put("line", Integer.valueOf(parts.length>1?parts[1]:lineItemId));
            }
            if (!lineItemRecord.isDeleted()) {
                lineItems.add(l);
            }
        });
        payload.remove("subscriptionplanlines");
        payload.put("member", Map.of("items", lineItems));
        return payload;
    }

    private Map<String, Object> preprocessKitItem(SyncRequest request, Map<String, Object> payload) {
        // This will throw an API exception if there are no SO line items.
        if (!payload.containsKey("kititemmembers")) return payload;
        List<Map<String, Object>> lineItems = new ArrayList<>();
        ((List) payload.get("kititemmembers")).forEach(lineItem -> {
            EntityData lineItemRecord = (EntityData) lineItem;
            var l = lineItemRecord.getValues();
            //add line item as id
            if(lineItemRecord.getId()!=null) {
                String lineItemId = lineItemRecord.getId();
                String[] parts = lineItemId.split("#");
                l.put("line", Integer.valueOf(parts.length>1?parts[1]:lineItemId));
            }
            if (!lineItemRecord.isDeleted()) {
                lineItems.add(l);
            }
        });
        payload.remove("kititemmembers");
        payload.put("member", Map.of("items", lineItems));
        return payload;
    }

    private Map<String, Object> preprocessPriceplan(SyncRequest request, Map<String, Object> payload) {
        // This will throw an API exception if there are no SO line items.
        if (!payload.containsKey("pricetiers")) return payload;
        List<Map<String, Object>> lineItems = new ArrayList<>();
        ((List) payload.get("pricetiers")).forEach(lineItem -> {
            EntityData lineItemRecord = (EntityData) lineItem;
            var l = lineItemRecord.getValues();
            //add line item as id
            if(lineItemRecord.getId()!=null) {
                String lineItemId = lineItemRecord.getId();
                String[] parts = lineItemId.split("#");
                l.put("line", Integer.valueOf(parts.length>1?parts[1]:lineItemId));
            }
            if (l.containsKey("fromVal") && l.get("fromVal") != null) {
                l.put("fromVal", Integer.valueOf(l.get("fromVal").toString()));
            }
            if (!lineItemRecord.isDeleted()) {
                lineItems.add(l);
            }
        });
        payload.remove("pricetiers");
        payload.put("pricetiers", Map.of("items", lineItems));

        return payload;
    }

    private Map<String, Object> preprocessAddress(String prefix, Map<String, Object> payload) {
        Map<String, Object> address = new HashMap<>();
        address.put("attention", payload.getOrDefault(prefix + "_attention", ""));
        address.put("addressee", payload.getOrDefault(prefix + "_addressee", ""));
        address.put("addr1", payload.getOrDefault(prefix + "_addr1", ""));
        address.put("addr2", payload.getOrDefault(prefix + "_addr2", ""));
        address.put("city", payload.getOrDefault(prefix + "_city", ""));
        address.put("state", payload.getOrDefault(prefix + "_state", ""));
        address.put("zip", payload.getOrDefault(prefix + "_zip", ""));
        address.put("country", payload.getOrDefault(prefix + "_country", ""));
        address.put("addrphone", payload.getOrDefault(prefix + "_addrphone", ""));
        return address;
    }

    private Map<String, Object> preprocessEstimateAddress(String prefix, Map<String, Object> payload) {
        Map<String, Object> address = new HashMap<>();
        if (payload.containsKey(prefix + "_attention")) {
            address.put("attention", payload.get(prefix + "_attention"));
        }
        if (payload.containsKey(prefix + "_addressee")) {
            address.put("addressee", payload.get(prefix + "_addressee"));
        }
        if (payload.containsKey(prefix + "_addr1")) {
            address.put("addr1", payload.get(prefix + "_addr1"));
        }
        if (payload.containsKey(prefix + "_addr2")) {
            address.put("addr2", payload.get(prefix + "_addr2"));
        }
        if (payload.containsKey(prefix + "_city")) {
            address.put("city", payload.get(prefix + "_city"));
        }
        if (payload.containsKey(prefix + "_state")) {
            address.put("state", payload.getOrDefault(prefix + "_state", ""));
        }
        if (payload.containsKey(prefix + "_zip")) {
            address.put("zip", payload.get(prefix + "_zip"));
        }
        if(payload.containsKey(prefix + "_country")) {
            address.put("country", payload.get(prefix + "_country"));
        }
        if(payload.containsKey(prefix + "_addrphone")) {
            address.put("addrphone", payload.get(prefix + "_addrphone"));
        }
        return address;
    }

    private void attachContactsToOppties(SyncRequest request, EntityData create, String id, List<String> contacts) {
        if ("opportunity".equals(create.getName()) && contacts != null && !contacts.isEmpty()) {
            WsClient client = netSuiteSOAPService.getClient(request.getConnector());
            for (String contact : contacts) {
                netSuiteSOAPService.attachContactToOppty(client, contact, id);
            }
        }
    }

    boolean ofType(Object value, Class cls){
        return value!=null && cls.isAssignableFrom(value.getClass());
    }

    private Map<String, Object> fixDateAndTime(SyncRequest request, EntityData create) {
        request.getEntitySchema().getAttributes().forEach(attribute -> {
            Object value = create.getValue(attribute.getApiName());
            if (create.has(attribute.getApiName()) && value!=null) {
                if ("date".equalsIgnoreCase(attribute.getDataType())) {
                    if(value instanceof  Date) {
                        String formattedDate = dateUtil.format((Date) value, DateUtil.dateOnlyFormat);
                        create.addValue(attribute.getApiName(), formattedDate);
                    }else{
                        log.error("Expecting Date for attribute {} but got {} ({})", attribute.getApiName(),value,value.getClass().getName());
                    }
                } else if ("datetime".equalsIgnoreCase(attribute.getDataType())&& value!=null) {
                    if(value instanceof  ZonedDateTime) {
                        ZonedDateTime typedValue = (ZonedDateTime)value;
                        ZonedDateTime utcDateTime = typedValue.withZoneSameInstant(ZoneOffset.UTC);
                        String formattedDateTime = dateUtil.format(utcDateTime, UTC_FORMAT);
                        create.addValue(attribute.getApiName(), formattedDateTime);
                    }else{
                        log.error("Expecting ZonedDateTime for attribute {} but got {} ({})", attribute.getApiName(),value,value.getClass().getName());
                    }
                }
                handlePicklistValues(create, attribute, value);
                //Trandate is special in Netsuite and cannot contain nulls, even though metadata says its nullable.
                //TODO: Are there other fields like this?
                if ("trandate".equalsIgnoreCase(attribute.getApiName()) && create.getValue("trandate") == null) {
                    create.remove("trandate");
                }
            }
        });
        return new HashMap<>(create.getValues());
    }

    private void handlePicklistValues(EntityData create, AttributeSchema attribute, Object value) {
        if(value!=null && ("picklist".equals(attribute.getDataType())||"reference".equals(attribute.getDataType())||"polymorphicreference".equals(attribute.getDataType()) )){
            if(attribute.isMultiValueField()) {
                List<Object> selectedValues = ofType(value, List.class) ? List.class.cast(value) : List.of(value);
                List<Map<String, Object>> items = selectedValues.stream()
                        .filter(o-> o!=null && !StringUtils.isBlank(o.toString()))
                        .map(o -> Map.of("id", o))
                        .collect(Collectors.toList());
                if(!items.isEmpty()) {
                    create.addValue(attribute.getApiName(), Map.of("items", items));
                }else{
                    create.remove(attribute.getApiName());
                }
            }else{
                if(!StringUtils.isBlank(value.toString())) {
                    create.addValue(attribute.getApiName(), Map.of("id", value));
                }else{
                    create.remove(attribute.getApiName());
                }
            }
        }
    }

    private boolean isOpptyContact(EntityData create, AttributeSchema attribute) {
        return "contacts".equals(attribute.getApiName()) && "opportunity".equals(create.getName());
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        ConnectorInfo connector = request.getConnector();
        AuthConfig auth = connector.getAuthConfig();
        List<EntityData> toBeUpdated = request.getData().get(request.getConnector().getId());
        final String replaceSublists = request.getTypedDestParam(REPLACE_SUBLIST);
        SyncResponse response = new SyncResponse();
        List<Result> results = new ArrayList<>();
        NetSuiteRestClient restClient = getNetSuiteRestClient();
        restClient.addHeader(CONTENT_TYPE, APPLICATION_JSON);
        AtomicBoolean isSuccess = new AtomicBoolean(true);
        Set<String> replaceSublistSet = new HashSet<>();
        if (REPLACE_FOR_ENTITY.contains(request.getEntityName())) {
            replaceSublistSet.add("item");
        }
        if (!StringUtils.isBlank(replaceSublists)) {
            replaceSublistSet.addAll(
                    Arrays.stream(replaceSublists.split(","))
                            .map(String::strip)
                            .filter(s -> !StringUtils.isBlank(s))
                            .collect(Collectors.toList())
            );
        }
        String replaceSublistAll = String.join(",", replaceSublistSet);
        toBeUpdated.forEach((update) -> {
            String id = update.getId();
            String updateURL = format(SINGLE_RECORD_URL, connector.getEndpoint(), VERSION, update.getName(), id);
            String json = "";
            try {
                Map<String, Object> payload = preprocess(request, updateAddressFields(fixDateAndTime(request, update),request, id));
                final String availableReplacements = replaceSublistSet.stream()
                        .filter(s -> payload.containsKey(s))
                        .collect(Collectors.joining(","));
                if(payload.isEmpty()){
                    log.info("Skipping update to record {} on object {} because no nonnull values found",id,request.getEntityName());
                }else {
                    json = mapper.writeValueAsString(payload);
                    log.debug(json);
                    //TODO: Skip journal entry updates for now
                    if (!"journalEntry".equalsIgnoreCase(request.getEntityName())) {
                        // For salesorder line items, we always want to replace them to update/delete items in one shot.
                        if (!availableReplacements.isEmpty()) {
                            updateURL += "?replace=" + availableReplacements;
                        }
                        restClient.patchRaw(updateURL, json, auth);
                    }
                    List<String> contacts =  "opportunity".equals(update.getName()) ? 
                        (List<String>) update.getValues().remove("contacts") : List.of();
                    attachContactsToOppties(request, update, id, contacts);
                }
                results.add(new Result(true, id, update.getSyncariEntityId()));

            }catch (NonRetriableException | UnknownException |JsonProcessingException ex){
                Result result = new Result(false, id, update.getSyncariEntityId()).addError(ex.getMessage());
                if(ex instanceof NonRetriableException) {
                    NonRetriableException nonRetriableException = (NonRetriableException) ex;
                    if(StringUtils.isNotBlank(nonRetriableException.getStatusCode()) && nonRetriableException.getStatusCode().contains("The record instance does not exist.")) {
                        result.setErrorCode(ErrorCodes.DATA_NOT_FOUND.name());
                    }
                }
                results.add(result);
                isSuccess.set(false);
            }
        });
        response.setSuccess(isSuccess.get());
        response.setResults(results);
        return response;
    }

    private Map<String, Object> updateAddressFields(Map<String, Object> values, SyncRequest request, String id) {
        if("customer".equalsIgnoreCase(request.getEntityName())){
            boolean hasAddress = values.keySet().stream().anyMatch(k -> k.startsWith("billingAddress_") || k.startsWith("shippingAddress_"));
            if(hasAddress) {
                Optional<EntityData> item = getItem(request, id, SearchResults.emptyResults(), Collections.EMPTY_MAP);
                item.ifPresent(customer->{
                    String billingAddressId = customer.getValueAsString("billingAddress_id");
                    Map<String, Object> incomingBillingAddress = createAddress(values, "billingAddress", Optional.ofNullable(billingAddressId));
                    String shippingAddressId = customer.getValueAsString("shippingAddress_id");
                    Map<String, Object> incomingShippingAddress = createAddress(values, "shippingAddress",Optional.ofNullable(shippingAddressId));
                    //same address for both
                    if(Objects.equals(billingAddressId, shippingAddressId) && (!incomingBillingAddress.isEmpty() || !incomingShippingAddress.isEmpty())){
                        Map<String, Object> merged = new HashMap<>(incomingBillingAddress);
                        merged.putAll(incomingShippingAddress);
                        merged.put("defaultBilling",true);
                        merged.put("defaultShipping",true);
                        values.put("addressBook", Map.of("items",List.of(merged)));
                    }
                });
            }
        }
        return values;
    }

    private void removeEmptyListValues(Map<String, Object> values) {
        Set<String> emptyListsToRemove = new HashSet<>();
        values.forEach((apiName,value)->{
            if(values.get(apiName) instanceof List && ((List) values.get(apiName)).isEmpty()) emptyListsToRemove.add(apiName);
        });
        emptyListsToRemove.forEach(apiName -> values.remove(apiName));
    }

    protected NetSuiteRestClient getNetSuiteRestClient(ProxyConfig proxy) {
        return new NetSuiteRestClient(getTemplate(Optional.ofNullable(proxy)), mapper, proxy);
    }

    protected NetSuiteRestClient getNetSuiteRestClient() {
        return new NetSuiteRestClient(getTemplate(Optional.empty()), mapper);
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
            String contactUrl = format(SINGLE_RECORD_URL, connector.getEndpoint(), VERSION, delete.getName(), id);
            try {
                NetSuiteRestClient restClient = getNetSuiteRestClient();
                restClient.addHeader(CONTENT_TYPE, APPLICATION_JSON);
                restClient.delete(contactUrl, auth);
                results.add(new Result(true, id, delete.getSyncariEntityId()));
            } catch (Exception e) {
                log.error("Failed delete: " + e.getMessage());
                throw e;
            }
        });
        response.setSuccess(true);
        response.setResults(results);
        return response;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        return null;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        return null;
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {

    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of(Constants.ACCOUNT.toLowerCase(), "customer", Constants.CONTACT.toLowerCase(), Constants.CONTACT.toLowerCase(),
                Constants.OPPORTUNITY.toLowerCase(), Constants.OPPORTUNITY.toLowerCase(), Constants.DOCUMENT.toLowerCase(), "file");
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return NetsuiteSeed.getAttributeMappings(entityApiName);
    }

    @Override
    public DocumentResponse getFileContents(DocumentRequest request) {
        return new DocumentResponse(
            netSuiteSOAPService.getFileContents(netSuiteSOAPService.getClient(request.getConnector()), request.getFileMetadata()),
            request.getFileMetadata()
        );
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
}


class NetSuiteIterator extends DefaultDataIterator {

    private EntitySchema entitySchema;
    private EntitySchema parentSchema;
    private Function3<EntitySchema, EntitySchema, List<EntityData>, List<EntityData>> extractChildItems;

    public NetSuiteIterator(WatermarkInfo baseWatermark, long offset, Function3<WatermarkInfo, Integer, Long,
            Pair<Long, Stream<EntityData>>> generator, List<EntityData> data, AttributeSchema watermarkField,int pageSize, int maxRecords, EntitySchema entitySchema, EntitySchema parentSchema,
                            Function3<EntitySchema, EntitySchema, List<EntityData>, List<EntityData>> extractChildItems) {
        super(baseWatermark, offset, generator, data, watermarkField,pageSize,maxRecords);
        this.entitySchema = entitySchema;
        this.parentSchema = parentSchema;
        this.extractChildItems = extractChildItems;
    }

    @Override
    protected int getEffectivePageSize() {
        ///Netsuite has a minimum 5 record page size
        return Math.max(super.getEffectivePageSize(),5);
    }

    @Override
    protected long nextOffset(Pair<Long, Stream<EntityData>> results, List<EntityData> data) {
        //offset is used as pageNuber in netsuite
        return offset + 1;
    }

    @Override
    public int getMaxRecordsPerEntitySyncCycle() {
        return this.pageSize == 0 ? super.getMaxRecordsPerEntitySyncCycle() : this.pageSize;
    }

    @Override
    public List<EntityData> next() {
        List<EntityData> data = super.next();
        if (NetSuiteService.supportedChildEntities.contains(entitySchema.getApiName())) {
            return extractChildItems.apply(parentSchema, entitySchema, data);
        }
        return data;
    }
}

@Data
@AllArgsConstructor
class NSReference implements Serializable {
    private String id;
}

@Data
@Accessors(chain = true)
class FetchResult {
    Pair<Boolean,List<EntityData>> success;
    int failedCount;
}