package com.syncari.connector.service;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.data.iterator.HubspotIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.data.iterator.Offset.OffsetType;
import com.syncari.connector.data.iterator.hubspot.HubspotEmailEventIterator;
import com.syncari.connector.data.iterator.hubspot.HubspotFormSubmissionIterator;
import com.syncari.connector.data.iterator.hubspot.HubspotIncrementalIterator;
import com.syncari.connector.exception.*;
import com.syncari.connector.rest.HubspotRestClient;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.*;
import com.syncari.connector.service.seed.HubspotSeed;
import com.syncari.utils.KeyValue;
import com.syncari.utils.Pair;
import com.syncari.utils.TextUtil;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.syncari.connector.data.iterator.hubspot.HubspotIncrementalIterator.fixMultivaluedFields;
import static com.syncari.connector.service.seed.HubspotSeed.HS_OBJECT_ID;
import static com.syncari.utils.ExceptionUtils.rethrow;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component(Constants.HUBSPOT)
public class HubspotService
		implements OauthAuthenticationService, CommonDataService, MetadataService, SynapseInfoService, WebhookService {
    private static final String DOT = "\\.";
	private static final String X_HUBSPOT_SIGNATURE = "x-hubspot-signature";
	private static final String ENUMERATION = "enumeration";
    private static final int DEAL_TO_COMPANY_DEF_ID = 5;
    private static final int DEAL_TO_CONTACT_ASSOC_ID = 3;
    private static final int ENGAGEMENT_TO_COMPANY_DEF_ID = 8;
    private static final int ENGAGEMENT_TO_CONTACT_ASSOC_ID = 10;
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int ENGAGEMENT_PAGE_SIZE = 250;
    public static final int MAX_CONTACTS_LIMIT = 5000;
    public static final int MAX_ACTIVITIES_LIMIT = 2000;
    private static final int MAX_OFFSET = 10000;
    public static final String DEAL = "deal";
    public static final String ENGAGEMENT = "engagement";
    public static final String LINE_ITEM = "line_item";
    public static final String ASSOCIATION = "association";
    public static final String QUOTE = "quote";
    public static final String ASSOCIATION_SUFFIX = "_association";
    private static final String SPACE = " ";
    public static final String API_HOST = "https://api.hubapi.com";
    private static String OAUTH_HOST = "https://app.hubspot.com";
    private static String OAUTH_URL = "https://api.hubapi.com/oauth/v1/token";
    private static final Map<String, String> objPluralMap = new HashMap<>();
    static {
    	objPluralMap.put("company", "companies");
    	objPluralMap.put("contact", "contacts");
    	objPluralMap.put(DEAL, "deals");
    	objPluralMap.put("ticket", "tickets");
    	objPluralMap.put("lead", "leads");
    	objPluralMap.put(Constants.OWNER, "owners");
    	objPluralMap.put(Constants.EVENT, "events");
    	objPluralMap.put("engagement", "engagements");
    	objPluralMap.put("activity", "activities");
    	objPluralMap.put("product", "products");
    	objPluralMap.put("form", "forms");
    	objPluralMap.put("formsubmission", "formsubmissions");
    	objPluralMap.put(Constants.EMAIL_EVENT, "emailevents");
    	objPluralMap.put("line_item", "line_items");
    	objPluralMap.put("note", "notes");
        objPluralMap.put("invoice", "invoices");
        objPluralMap.put("subscription", "subscriptions");
        objPluralMap.put("quote", "quotes");
    }

    private static final Map<String, List<Pair<String, String>>> scopeToObjectMap = new HashMap<>();
    static {
        scopeToObjectMap.put("crm.objects.companies.read", List.of(Pair.of("company", "read")));
        scopeToObjectMap.put("crm.objects.companies.write", List.of(Pair.of("company", "write")));
        scopeToObjectMap.put("crm.objects.leads.read", List.of(Pair.of("lead", "read")));
        scopeToObjectMap.put("crm.objects.leads.write", List.of(Pair.of("lead", "write")));
        scopeToObjectMap.put("crm.objects.contacts.read", List.of(Pair.of("contact", "read"), Pair.of("note", "read")));
        scopeToObjectMap.put("crm.objects.contacts.write", List.of(Pair.of("contact", "write"), Pair.of("note", "write")));
        scopeToObjectMap.put("crm.objects.deals.read", List.of(Pair.of("deal", "read")));
        scopeToObjectMap.put("crm.objects.deals.write", List.of(Pair.of("deal", "write")));
        scopeToObjectMap.put("crm.objects.owners.read", List.of(Pair.of("owner", "read")));
        scopeToObjectMap.put("tickets", List.of(Pair.of("ticket", "write")));
        scopeToObjectMap.put("e-commerce", List.of(Pair.of("product", "write"), Pair.of("line_item", "write")));
        scopeToObjectMap.put("forms", List.of(Pair.of("form", "read"), Pair.of("formsubmission", "read")));
        scopeToObjectMap.put("content", List.of(Pair.of("emailevent", "read")));
        scopeToObjectMap.put("business-intelligence", List.of(Pair.of("activity", "read"), Pair.of("event", "read")));
        scopeToObjectMap.put("crm.objects.subscriptions.read", List.of(Pair.of("subscription", "read")));
        scopeToObjectMap.put("crm.objects.subscriptions.write", List.of(Pair.of("subscription", "write")));
        scopeToObjectMap.put("crm.objects.invoices.read", List.of(Pair.of("invoice", "read")));
        scopeToObjectMap.put("crm.objects.invoices.write", List.of(Pair.of("invoice", "write")));
        scopeToObjectMap.put("crm.objects.quotes.read", List.of(Pair.of("quote", "read")));
        scopeToObjectMap.put("crm.objects.quotes.write", List.of(Pair.of("quote", "write")));
    }

    private static final Set<String> engagementScopesReadMap = Set.of("crm.objects.companies.read", "crm.objects.contacts.read", "crm.objects.deals.read" , "tickets", "e-commerce");
    private static final Set<String> engagementScopesWriteMap = Set.of("crm.objects.companies.write", "crm.objects.contacts.write", "crm.objects.deals.write" , "tickets", "e-commerce");

    private static final Set<String> FIEDS_FOR_INTEGER_DATATYPE = Set.of(HS_OBJECT_ID, "hs_created_by_user_id", "deal_id_first_created_deal_id", "most_recent_deal_id", "subscriber_company_id", "hs_internal_subscription_id", "associated_subscription_id","primary_company_id", "hs_primary_associated_company");
    private static final Map<String, String> createFieldScopeMap = Map.of(
            "contact", "crm.schemas.contacts.write",
            "company", "crm.schemas.companies.write",
            "deal", "crm.schemas.deals.write"
    );

    private static final Map<String, String> objIdMap = Map.of(Constants.OWNER, "ownerId");
	private static final List<String> SEED_ENTITIES = List.of(Constants.OWNER, Constants.EVENT, Constants.ACTIVITY,
			"engagement", "note",  "form", Constants.FORM_SUBMISSION, Constants.EMAIL_EVENT);

    private static final List<String> READ_ONLY_ENTITIES = List.of("subscription");

    private static final Map<String, Set<String>> ASSOCIATION_ENTITIES_MAP = Map.of(
            "contact", Set.of("company", "deal", "ticket", "product"),
            "company", Set.of("company", "contact", "deal", "ticket", "product"),
            "deal", Set.of("contact", "company", "ticket", "call", "meeting"),
            "ticket", Set.of("contact", "company", "deal", "product"),
                "quote", Set.of("contact", "company", "deal")
    );

    private static final Set<String> ASSOCIATION_PRIMARY_ENTITIES = Set.of("contact", "company", "deal", "ticket", "quote");

    private static Set<String> REQUIRED_SCOPES = Set.of(
            "oauth",
            "crm.objects.companies.read",
            "crm.objects.companies.write",
            "crm.objects.contacts.read",
            "crm.objects.contacts.write",
            "crm.objects.deals.read",
            "crm.objects.deals.write",
            "crm.objects.owners.read",
            "crm.schemas.companies.read",
            "crm.schemas.contacts.read",
            "crm.schemas.deals.read"
    );

    private static Set<String> OPTIONAL_SCOPES = Set.of(
            "crm.objects.marketing_events.read",
            "crm.objects.marketing_events.write",
            "crm.lists.read",
            "crm.lists.write",
            "e-commerce",
            "sales-email-read",
            "content",
            "crm.objects.custom.read",
            "crm.objects.custom.write",
            "crm.schemas.custom.read",
            "tickets",
            "business-intelligence",
            "forms",
            "files",
            "crm.schemas.companies.write",
            "crm.schemas.contacts.write",
            "crm.schemas.deals.write",
            "crm.objects.line_items.write",
            "crm.objects.line_items.read",
            "crm.objects.leads.read",
            "crm.objects.leads.write",
            "crm.objects.subscriptions.read",
            "crm.schemas.subscriptions.read",
            "crm.objects.invoices.write",
            "crm.objects.invoices.read",
            "crm.schemas.invoices.read",
            "crm.objects.quotes.read",
            "crm.objects.quotes.write",
            "crm.schemas.quotes.read"
    );

    private static final String ADD_TO_LIST = "/contacts/v1/lists/%s/add";
    private static final String CREATE_CONTACT_BATCH = "/crm/v3/objects/contacts/batch/create";
    private static final String UPDATE_CONTACT_BATCH = "/crm/v3/objects/contacts/batch/update";

    private static final String UPDATE_LINE_ITEM_BATCH = "/crm/v3/objects/line_items/batch/update";
    private static final String UPDATE_DEAL_BATCH = "/crm/v3/objects/deals/batch/update";
    private static final String UPDATE_INVOICE_BATCH = "/crm/v3/objects/invoices/batch/update";
    private static final String UPDATE_QUOTE_BATCH = "/crm/v3/objects/quotes/batch/update";
    private static final Map<String, String> BATCH_UPDATE_URLS = Map.of(
            "contact", UPDATE_CONTACT_BATCH,
            "deal", UPDATE_DEAL_BATCH,
            "line_item", UPDATE_LINE_ITEM_BATCH,
            "invoice", UPDATE_INVOICE_BATCH,
            "quote", UPDATE_QUOTE_BATCH
    );
    private static final String CREATE_CUSTOM_BATCH = "/crm/v3/objects/%s/batch/create";
    private static final String UPDATE_CUSTOM_BATCH = "/crm/v3/objects/%s/batch/update";
    private static final String CREATE = "create";
    private static final String UPDATE = "update";
    // Ticket api does not support incremental GET
    private static final Map<String, String> incrementalGetAPIMap = Map.of(
            "company", "/companies/v2/companies/recent/modified?since=%s&count=%s&offset=%s",
            "contact", "/contacts/v1/lists/recently_updated/contacts/recent?timeOffset=%s&count=100",
            DEAL, "/deals/v1/deal/recent/modified?since=%s&count=%s&offset=%s",
            Constants.EMAIL_EVENT, "/email/public/v1/events",
            "engagement", "/engagements/v1/engagements/recent/modified?since=%s&count=%s&offset=%s",
            "activity", "/events/v3/events?objectType=contact&sort=occurredAt&limit=%s&objectId=%s&occurredAfter=%s&occurredBefore=%s",
            "quote", "/crm/v3/objects/quotes?limit=%s&after=%s&properties=hs_lastmodifieddate&hs_lastmodifieddate__gte=%s");

    private static final Map<String, String> initialGetAPIMap = Map.of(
            "company", "/companies/v2/companies/paged?offset=%s&limit=%s",
            "contact", "/contacts/v1/lists/all/contacts/all?count=%s&vidOffset=%s",
            DEAL, "/deals/v1/deal/paged?offset=%s&limit=%s",
            Constants.OWNER, "/crm/v3/owners/",
            Constants.EVENT, "/reports/v2/events",
            Constants.EMAIL_EVENT, "/email/public/v1/events",
            "engagement", "/engagements/v1/engagements/paged?offset=%s&limit=%s",
            "activity", "/events/v3/events?objectType=contact&sort=occurredAt&limit=%s&objectId=%s&occurredAfter=%s&occurredBefore=%s",
            "quote", "/crm/v3/objects/quotes?limit=%s&after=%s");

    private static final Map<String, String> getByIds = Map.ofEntries(
            Map.entry("company", "/crm/v3/objects/companies/batch/read"),
            Map.entry("deal", "/crm/v3/objects/deals/batch/read"),
            Map.entry("ticket", "/crm/v3/objects/tickets/batch/read"),
            Map.entry("lead", "/crm/v3/objects/leads/batch/read"),
            Map.entry("contact", "/contacts/v1/contact/vids/batch?&vid="),
            Map.entry("form", "/forms/v2/forms/"),
            Map.entry(Constants.OWNER, "/crm/v3/owners/"),
            Map.entry(Constants.EVENT, "/reports/v2/events/"),
            Map.entry("engagement", "/engagements/v1/engagements/"),
            Map.entry("custom", "/crm/v3/objects/%s/batch/read"),
            Map.entry("line_item", "/crm/v3/objects/line_items/batch/read"),
            Map.entry("product", "/crm/v3/objects/products/batch/read"),
            Map.entry("invoice", "/crm/v3/objects/invoices/batch/read"),
            Map.entry("subscription", "/crm/v3/objects/subscriptions/batch/read"),
            Map.entry("quote", "/crm/v3/objects/quotes/batch/read"));

    private static final Map<String, String> createUrlMap = Map.ofEntries(
            Map.entry("company", "/companies/v2/companies"),
            Map.entry("contact", "/contacts/v1/contact"),
            Map.entry("ticket", "/crm/v3/objects/tickets"),
            Map.entry("lead", "/crm/v3/objects/leads"),
            Map.entry(DEAL, "/deals/v1/deal"),
            Map.entry("property", "/crm/v3/properties/%s"),
            Map.entry("associations", "/crm-associations/v1/associations/create-batch"),
            Map.entry("engagement", "/engagements/v1/engagements/"),
            Map.entry("note", "/crm/v3/objects/notes"),
            Map.entry("product", "/crm/v3/objects/products"),
            Map.entry("custom", "/crm/v3/objects/%s"),
            Map.entry("line_item", "/crm/v3/objects/line_item"),
            Map.entry("property_group", "/crm/v3/properties/%s/groups"),
            Map.entry("invoice", "/crm/v3/objects/invoices"),
            Map.entry("subscription", "/crm/v3/objects/subscription"),
            Map.entry("quote", "/crm/v3/objects/quotes")
    );

    private static final Map<String, String> updateUrlMap = Map.ofEntries(
            Map.entry("company", "/companies/v2/companies/%s"),
            Map.entry("contact", "/contacts/v1/contact/vid/%s/profile"),
            Map.entry("ticket", "/crm/v3/objects/tickets/%s"),
            Map.entry("lead", "/crm/v3/objects/leads/%s"),
            Map.entry("deal", "/deals/v1/deal/%s"), 
            Map.entry("engagement", "/engagements/v1/engagements/%s"),
            Map.entry("custom", "/crm/v3/objects/%s/%s"),
            Map.entry("line_item", "/crm/v3/objects/line_item/%s"),
            Map.entry("product", "/crm/v3/objects/products/%s"),
            Map.entry("invoice", "/crm/v3/objects/invoices/%s"),
            Map.entry("subscription", "/crm/v3/objects/subscription/%s"),
            Map.entry("quote", "/crm/v3/objects/quotes/%s")
            );

    private static final Map<String, String> httpUpdateMethodMap = Map.ofEntries(
            Map.entry("company", "PUT"),
            Map.entry("contact", "POST"),
            Map.entry("lead", "PATCH"),
            Map.entry("engagement", "PUT"),
            Map.entry("line_item", "PATCH"),
            Map.entry("product", "PATCH"),
            Map.entry("invoice", "PATCH"),
            Map.entry("subscription", "PATCH"),
            Map.entry("quote", "PATCH"));

    private static final Map<String, String> deleteUrlMap = Map.ofEntries(
            Map.entry("company", "/companies/v2/companies/%s"),
            Map.entry("contact", "/contacts/v1/contact/vid/%s"),
            Map.entry("ticket", "/crm/v3/objects/tickets/%s"),
            Map.entry("lead", "/crm/v3/objects/leads/%s"),
            Map.entry("property", "/crm/v3/properties/%s/%s"),
            Map.entry("deal", "/deals/v1/deal/%s"),
            Map.entry("engagement", "/engagements/v1/engagements/%s"),
            Map.entry("custom", "/crm/v3/objects/%s/%s"),
            Map.entry("line_item", "/crm/v3/objects/line_item/%s"),
            Map.entry("product", "/crm/v3/objects/products/%s"),
            Map.entry("invoice", "/crm/v3/objects/invoices/%s"),
            Map.entry("subscription", "/crm/v3/objects/subscription/%s"),
            Map.entry("quote", "/crm/v3/objects/quotes/%s")
    );

    private static final Map<String, String> createPropertyNameMap = Map.of("company", "name",
            "contact", "property",
            "deal", "name");
    private static final Map<String, String> updatePropertyNameMap = Map.of("company", "name",
            "contact", "property",
            "lead", "property",
            "deal", "property" /* deal updates use v3 batch APIs*/);
    private static String DESCRIBE_URL_TEMPLATE = "/crm/v3/properties/%s";

    private static String CONTACT_MERGE = "/crm/v3/objects/contact/merge";

    private final static Set<String> systemFields = Set.of("hs_lastmodifieddate", "lastmodifieddate", "createdate", "hs_createdate", HS_OBJECT_ID);
    private final static String WATERMARK_FIELD = "hs_lastmodifieddate";
    private final static String CONTACT_WATERMARK_FIELD = "lastmodifieddate";
    private final static Map<String, List<String>> mandatoryFieldsOfEntity = Map.of(
            "contact", List.of("email"),
            "company", List.of("name"),
            "deal", List.of("dealname", "pipeline", "dealstage"),
            "ticket", List.of("subject", "hs_pipeline", "hs_pipeline_stage"));

    private final static Set<String> SUPPORTED_ACTIVITIES = Set.of("e_visited_page", "e_submitted_form");

    private final static Set<String> VALID_ENGAGEMENT_STATUSES = Set.of("COMPLETED", "CANCELED", "BUSY", "FAILED", "NO_ANSWER",
            "RINGING", "QUEUED", "IN_PROGRESS", "CALLING_CRM_USER", "CONNECTING");

    private final static String HUBSPOT_CO_ENDPOINT = "/crm/v3/schemas/";

    private final static String CALL_DISPOSITIONS = "/calling/v1/dispositions";

    private final static String ASSOCIATIONS_BATCH_READ = "/crm/v4/associations/%s/%s/batch/read";
    private final static int HUBSPOT_BATCH_LIMIT = 1000; // HubSpot API limit for batch operations
    private final static String ASSOCIATIONS_CREATE_DEFAULT = "/crm/v4/objects/%s/%s/associations/default/%s/%s";
    private final static String ASSOCIATIONS_CREATE = "/crm/v4/objects/%s/%s/associations/%s/%s";

    private final static String ASSOCIATION_DELETE = "/crm/v4/associations/%s/%s/batch/labels/archive";

    // Thread-safe counter to track the total number of threads
    private final AtomicInteger threadCountTracker = new AtomicInteger(0);

    private static final int MAX_THREAD_LIMIT = 12;

    private final static Set<String> PARALLEL_WRITE_SUPPORT = Set.of("line_item");

    @Autowired
    ObjectMapper mapper;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;

    @Data
    @EqualsAndHashCode
    public static class CacheKey {
        ConnectorInfo info;

        public CacheKey(ConnectorInfo info) {
            this.info = info;
        }
    }

    LoadingCache<CacheKey, Map<String, String>> dispositionCache = CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(1l, TimeUnit.HOURS)
            .build(new CacheLoader<>() {
                @Override
                public Map<String, String> load(CacheKey key) {
                    return refreshCallDispositions(key.getInfo());
                }
            });

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        var auth = new AuthMetadata(AuthType.Oauth, Lists.newArrayList(), "OAuth", "");
        auth.setOptions(KeyValue.of("oneClickOauth", true));
        return List.of(auth, ConnectorHelper.getApiKey());
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19107544490260";
    }

    @Override
    public List<AuthField> getConfigureFields() {
    	AuthField portalId = new AuthField().setName("portalId").setLabel(i18n("portal_id"))
    			.setRequired(true)
                .setDataType("text").setHelpSummary(i18n("portal_summary"));
        return List.of(portalId, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public int lookBehindDuration(ConnectorInfo connectorInfo) {
        return Constants.TWO_MIN_IN_MILLI;
    }

    @Override
    public String getCategory() {
        return "Marketing";
    }
    
    @Override
    public String getName() {
        return Constants.HUBSPOT;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/hubspot.svg")
                .setDisplayName("HubSpot")
                .setBackgroundColor("#FFF5EE")
                .setHelpUrl(helpArticlesBaseUrl + "/360052157552-Hubspot-Setup");
    }

    // Method implementations for test override purpose.
    public int getMaxContactsLimit() {
        return MAX_CONTACTS_LIMIT;
    }
    public int getMaxActivitiesLimit() {
        return MAX_ACTIVITIES_LIMIT;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        // return schema is entity is one of the seeded etities
        if(SEED_ENTITIES.contains(request.getEntity())){
            EntitySchema es = HubspotSeed.getSeedEntitySchema(request.getEntity());
            updateSchema(es, request);
            return Optional.of(es);
        }

        if(request.getEntity().contains(ASSOCIATION_SUFFIX)) {
            String entity = request.getEntity();
            EntitySchema es = HubspotSeed.getSeedEntitySchema(ASSOCIATION);
            String primaryEntity = entity.substring(0, entity.indexOf(ASSOCIATION_SUFFIX));
            es.setApiName(entity);
            es.setDisplayName(StringUtils.capitalize(primaryEntity) + " Associations");
            return Optional.of(es);
        }

        String entityName = objPluralMap.get(request.getEntity());

        // possibly custom object?
        if (StringUtils.isEmpty(entityName)) {
            return describeCustomObject(request);
        }

        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        String path = String.format(DESCRIBE_URL_TEMPLATE, entityName);
        String url = getUrl("", request.getConnector().getAuthConfig(), path);
        EntitySchema entityDefinition = new EntitySchema();
        entityDefinition.setApiName(request.getEntity());
        entityDefinition.setDisplayName(StringUtils.capitalize(request.getEntity()));
//        entityDefinition.setReadOnly(READ_ONLY_ENTITIES.contains(request.getEntity()));
        List<String> mandatoryFields = mandatoryFieldsOfEntity.getOrDefault(request.getEntity(), List.of());
        try {
            log.debug("Starting hubspot schema refresh for {}", entityName);
            ResponseEntity<String> responseEntity = restClient.getResponse(url,request.getConnector().getAuthConfig());
            Map<String,List> responseObject = mapper.readValue(responseEntity.getBody(),Map.class);
            List properties = responseObject.getOrDefault("results", Collections.emptyList());
            for (int i = 0; i < properties.size(); ++i) {
                Map o = (Map) properties.get(i);
                Map modificationMetadataMap = (Map) o.get("modificationMetadata");
                AttributeSchema f = new AttributeSchema();
                f.setApiName(this.getValue(o, "name"));
                String label = this.getValue(o, "label");
                f.setDisplayName(label == null ? f.getApiName() : label);
                f.setDataType(toSyncariDatatype(this.getValue(o, "type"), o));
                f.setInitializable(true);
                f.setCalculated(this.getBoolValue(o, "calculated"));
                setReadonly(request, f, modificationMetadataMap, o);

                Boolean isHubspotDefined = this.getBoolValue(o, "hubspotDefined");
                f.setCustom(!(isHubspotDefined == null ? false : isHubspotDefined));
                f.setSystem(systemFields.contains(f.getApiName()));
                f.setIdField(HS_OBJECT_ID.equals(f.getApiName()));
                f.setUnique(f.isIdField() || this.getBoolValue(o, "hasUniqueValue"));
                f.setNillable(!f.isIdField() && !mandatoryFields.contains(f.getApiName()));
                if ("contacts".equalsIgnoreCase(entityName)) {
                    f.setWatermarkField(CONTACT_WATERMARK_FIELD.equalsIgnoreCase(f.getApiName()));
                } else{
                    f.setWatermarkField(WATERMARK_FIELD.equalsIgnoreCase(f.getApiName()));
                }
                // Enforce watermark fields as required
                if (f.isWatermarkField()) {
                    f.setNillable(false);
                }
                boolean isLineItemProductReference = request.getEntity().equalsIgnoreCase("line_item") && this.getValue(o, "name").equalsIgnoreCase("hs_product_id");
                if(o.get("referencedObjectType")!=null || isLineItemProductReference) {
                    String idFieldName = HS_OBJECT_ID;
                    String referenceTo = isLineItemProductReference ? "product" : o.get("referencedObjectType").toString().toLowerCase();
                    if(objIdMap.containsKey(referenceTo)) {
                        idFieldName = objIdMap.get(referenceTo);
                    }
                    f.setReferenceTargetField(idFieldName);
                    f.setReferenceTo(referenceTo);
                    if(isLineItemProductReference) {
                        f.setDataType("reference");
                    }
                }
                if(ENUMERATION.equalsIgnoreCase(f.getDataType()) && "checkbox".equalsIgnoreCase(this.getValue(o, "fieldType"))) {
                    f.setMultiValueField(true);
                }

                entityDefinition.addField(f);
            }
            updateSchema(entityDefinition, request);
            log.debug("Successfully completed hubspot schema refresh for {}", entityName);
        } catch(NonRetriableException | RetriableException nex){
          throw nex;
        } catch(Exception e) {
            ConnectorHelper.handleException(e);
        }
        return Optional.of(entityDefinition);
    }

    private void setReadonly(DescribeRequest request, AttributeSchema f, Map modificationMetadataMap, Map o) {
        f.setUpdateable(!this.getBoolValue((null == modificationMetadataMap) ? o : modificationMetadataMap, "readOnlyValue"));

        // Special handling for lead entity: make hs_primary_company_id and hs_primary_contact_id updatable
        if ("lead".equalsIgnoreCase(request.getEntity()) &&
            ("hs_primary_company_id".equals(f.getApiName()) || "hs_primary_contact_id".equals(f.getApiName()))) {
            f.setUpdateable(true);
            f.setDataType("string");
        }
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> objects = new ArrayList<>();

        ConnectorInfo connectorInfo = request.getConnector();
        Map<String, Object> metaConfig = connectorInfo.getMetaConfig();
        // Provarity passes the oauth scopes through oAuthScopes config key and Impartner through additionalScopes in ConnectorInfo
        if((metaConfig.containsKey("oAuthScopes") && StringUtils.isNotBlank(String.valueOf(metaConfig.get("oAuthScopes")))) ||
                (connectorInfo.getRequiredScopes() != null && !connectorInfo.getRequiredScopes().isEmpty())) {
            Set<String> scopes = (connectorInfo.getRequiredScopes() != null && !connectorInfo.getRequiredScopes().isEmpty()) ? new HashSet<>(connectorInfo.getRequiredScopes()) :
                    Arrays.stream(String.valueOf(metaConfig.get("oAuthScopes")).split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
            Set<String> optionalScopes = (connectorInfo.getOptionalScopes() != null && !connectorInfo.getOptionalScopes().isEmpty()) ? new HashSet<>(connectorInfo.getOptionalScopes()) :
                    new HashSet<>();
            scopes.addAll(optionalScopes);
            if(!scopes.isEmpty()) {
                Map<String, Pair<String, EntitySchema>> existingObjects = new HashMap<>();
                scopes.forEach(scope -> {
                    if (scopeToObjectMap.containsKey(scope)) {
                        List<Pair<String, String>> supportedObjects = scopeToObjectMap.get(scope);
                        supportedObjects.forEach(supportedObject -> {
                            String entity = supportedObject.x;
                            String permission = supportedObject.y;
                            if (existingObjects.containsKey(entity) && existingObjects.get(entity).x.equalsIgnoreCase("read") && permission.equalsIgnoreCase("write")) {
                                Pair<String, EntitySchema> pair = existingObjects.get(entity);
                                EntitySchema schema = pair.y;
                                schema.setReadOnly(false);
                                existingObjects.put(entity, Pair.of(permission, schema));
                            } else if (!existingObjects.containsKey(entity)) {
                                try {
                                    DescribeRequest req = new DescribeRequest(request.getConnector(), entity);
                                    EntitySchema schema = describe(req).get();
                                    schema.setReadOnly(permission.equalsIgnoreCase("read"));
                                    existingObjects.put(entity, Pair.of(schema.isReadOnly() ? "read" : "write", schema));
                                } catch (Exception e) {
                                    log.error("Exception describing {}", entity);
                                    log.error(ExceptionUtils.getStackTrace(e));
                                }
                            }
                        });
                    }
                });
                DescribeRequest req = new DescribeRequest(request.getConnector(), "engagement");
                EntitySchema engagementSchema = describe(req).get();
                engagementScopesReadMap.forEach(engagementScope -> {
                    if (scopes.contains(engagementScope)) {
                        engagementSchema.setReadOnly(true);
                        existingObjects.put("engagement", Pair.of("read", engagementSchema));
                    }
                });
                engagementScopesWriteMap.forEach(engagementScope -> {
                    if (scopes.contains(engagementScope)) {
                        engagementSchema.setReadOnly(false);
                        existingObjects.put("engagement", Pair.of("write", engagementSchema));
                    }
                });
                if(scopes.contains("crm.objects.custom.read") || scopes.contains("crm.objects.custom.write")) {
                    List<EntitySchema> customObjects = describeCustomObjects(request.getConnector());
                    if (scopes.contains("crm.objects.custom.read")) {
                        customObjects.forEach(customObject -> customObject.setReadOnly(true));
                    }
                    if (scopes.contains("crm.objects.custom.write")) {
                        customObjects.forEach(customObject -> customObject.setReadOnly(false));
                    }
                    objects.addAll(customObjects);
                }
                objects.addAll(existingObjects.keySet().stream().map(key -> existingObjects.get(key).y).collect(Collectors.toList()));

                // Add ASSOCIATION_PRIMARY_ENTITIES
                addAssociationObjects(request, objects);

                return objects;
            }
        }

        objPluralMap.keySet().forEach(e -> {
            try {
                DescribeRequest req = new DescribeRequest(request.getConnector(), e);
                objects.add(describe(req).get());
            } catch (Exception ex) {
                log.error("Exception describing {}", e);
                log.error(ExceptionUtils.getStackTrace(ex));
            }
        });

        addAssociationObjects(request, objects);

        List<EntitySchema> customObjectList = describeCustomObjects(request.getConnector());
        objects.addAll(customObjectList);

//        customObjectList.forEach(customObject -> {
//            DescribeRequest req = new DescribeRequest(request.getConnector(), customObject.getApiName() + "_association_custom");
//            objects.add(describe(req).get());
//        });

        return objects;
    }

    private void addAssociationObjects(DescribeAllRequest request, List<EntitySchema> objects) {
        ASSOCIATION_PRIMARY_ENTITIES.forEach(entity -> {
            DescribeRequest associationReq = new DescribeRequest(request.getConnector(), entity + ASSOCIATION_SUFFIX);
            objects.add(describe(associationReq).get());
        });
    }

    private Optional<EntitySchema> describeCustomObject(DescribeRequest request) {
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        String url = getUrl("", request.getConnector().getAuthConfig(), HUBSPOT_CO_ENDPOINT + request.getEntity());
        try{
            ResponseEntity<String> responseEntity = restClient.getResponse(url, request.getConnector().getAuthConfig());
            Map customObject = mapper.readValue(responseEntity.getBody(), Map.class);
            Optional<EntitySchema> customEntity =  prepareCustomEntitySchema(customObject);
            if (customEntity.isPresent() && CollectionUtils.isEmpty(customEntity.get().getAttributes())){
                String msg = String.format("Unable to retrieve the attributes for entity: %s", request.getEntity());
                log.error(msg);
                log.error("Error Payload response without attributes: {}", responseEntity);
                throw new RetriableException(ErrorCodes.IO_ERROR, msg, ErrorCodes.IO_ERROR.toString());
            }
            return customEntity;
        } catch (NonRetriableException e){
            if(ErrorCodes.BAD_REQUEST.name().equals(e.getErrorCode()) && StringUtils.contains(e.getMessage(), "Unable to infer object type from:")) {
                return Optional.empty();
            }
            throw e;
        } catch(IOException e){
            ConnectorHelper.handleException(e);
        }
        return Optional.empty();
    }

    private List<EntitySchema> describeCustomObjects(ConnectorInfo connector) {
        List<EntitySchema> objects = new ArrayList<>();
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        String url = getUrl("", connector.getAuthConfig(), HUBSPOT_CO_ENDPOINT);
        try {
            ResponseEntity<String> responseEntity = restClient.getResponse(url, connector.getAuthConfig());
            Map results = mapper.readValue(responseEntity.getBody(), Map.class);
            List customObjects = (List) results.get("results");
            for (int i = 0; i < customObjects.size(); ++i) {
                Map customObject = (Map) customObjects.get(i);
                objects.add(prepareCustomEntitySchema(customObject).get());
            }
        } catch (Exception e) {
            ConnectorHelper.handleException(e);
        }
        return objects;
    }

    // TODO: consolidate this and the regular object describe schema.
    private Optional<EntitySchema> prepareCustomEntitySchema(Map customObject) {
        EntitySchema entityDefinition = new EntitySchema();
        entityDefinition.setApiName(this.getValue(customObject, "fullyQualifiedName"));
        Map labels = (Map) customObject.get("labels");
        if (!MapUtils.isEmpty(labels)) {
            entityDefinition.setDisplayName(labels.get("plural").toString());
        } else {
            entityDefinition.setDisplayName(this.getValue(customObject, "name"));
        }
        entityDefinition.setCustom(true);
        List<String> mandatoryFields = (List<String>) customObject.get("requiredProperties");
        try {
            List properties = (List) customObject.get("properties");
            for (int i = 0; i < properties.size(); ++i) {
                Map o = (Map) properties.get(i);    
                AttributeSchema f = new AttributeSchema();
                f.setApiName(this.getValue(o, "name"));
                String label = this.getValue(o, "label");
                f.setDisplayName(label == null ? f.getApiName() : label);
                f.setDataType(toSyncariDatatype(this.getValue(o, "type"), o));
                f.setInitializable(true);
                f.setCalculated(this.getBoolValue(o, "calculated"));
                Map modificationMetadata = (Map) o.get("modificationMetadata");
                f.setUpdateable(!this.getBoolValue(modificationMetadata, "readOnlyValue"));
                Boolean isHubspotDefined = this.getBoolValue(o, "hubspotDefined");
                f.setCustom(!(isHubspotDefined == null ? false : isHubspotDefined));
                f.setSystem(systemFields.contains(f.getApiName()));
                f.setIdField(HS_OBJECT_ID.equals(f.getApiName()));
                f.setUnique(f.isIdField() || this.getBoolValue(o, "hasUniqueValue"));
                f.setNillable(!f.isIdField() && !mandatoryFields.contains(f.getApiName()));
                f.setWatermarkField(WATERMARK_FIELD.equalsIgnoreCase(f.getApiName()));
                // Enforce watermark fields as required
                if (f.isWatermarkField()) {
                    f.setNillable(false);
                }
                if(o.get("referencedObjectType")!=null) {
                    String idFieldName = HS_OBJECT_ID;
                    String referenceTo = o.get("referencedObjectType").toString().toLowerCase();
                    if(objIdMap.containsKey(referenceTo)) {
                        idFieldName = objIdMap.get(referenceTo);
                    }
                    f.setReferenceTargetField(idFieldName);
                    f.setReferenceTo(referenceTo);
                }
                if(ENUMERATION.equalsIgnoreCase(f.getDataType()) && "checkbox".equalsIgnoreCase(this.getValue(o, "fieldType"))) {
                    f.setMultiValueField(true);
                }

                entityDefinition.addField(f);
            }
            log.debug("Successfully completed custom hubspot schema refresh for {}", entityDefinition.getApiName());
        } catch(NonRetriableException | RetriableException nex){
          throw nex;
        } catch(Exception e) {
            ConnectorHelper.handleException(e);
        }
        return Optional.of(entityDefinition);
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        checkCreateScope(request);
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);

        String groupName = request.getEntityName() + "information";
        try {
            String path = String.format(createUrlMap.get("property_group"), request.getEntityName());
            String url = getUrl("", request.getConnector().getAuthConfig(), path);
            Map<String, Object> data = new HashMap<>();
            data.put("name", groupName);
            data.put("label", groupName);
            String valueAsString = mapper.writeValueAsString(data);
            log.debug("Creating group {} in hubspot", groupName);
            restClient.getTemplate().exchange(url, HttpMethod.POST,
                    new HttpEntity(valueAsString, restClient.getHeaders(request.getConnector().getAuthConfig())),
                    String.class);
            log.info("Successfully created hubspot property group {} on {}", groupName,
                    request.getEntityName());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                // Group already exists. Nothing to do
            } else {
                throw e;
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to process JSON for property group creation: {}", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }

        String path = String.format(createUrlMap.get("property"), request.getEntityName());
        String url = getUrl("", request.getConnector().getAuthConfig(), path);
        String apiName = formattedName(request.getSchema().getApiName());
        request.getSchema().setApiName(apiName);
        log.info("Creating hubspot field {} on {}", request.getSchema().getApiName(), request.getEntityName());
        Map<String, Object> data = new HashMap<>();
        request.setSchema(transform(request.getSchema()));
        data.put("name", apiName);
        data.put("label", request.getSchema().getDisplayName());
        data.put("groupName", groupName);
        String datatype = toHubspotDatatype(request.getSchema().getDataType());
        data.put("type", datatype);
        if (ENUMERATION.equalsIgnoreCase(datatype)) {
            data.put("options", request.getSchema().getPicklistValues().stream()
                    .map(p -> FluentMap.of("value", p).add( "label", p)).collect(Collectors.toList()));
        }
        data.put("fieldType", "text");

        try {
            String valueAsString = mapper.writeValueAsString(data);
            log.debug("Creating field {} in hubspot", valueAsString);
            restClient.getTemplate().exchange(url, HttpMethod.POST,
                    new HttpEntity(valueAsString, restClient.getHeaders(request.getConnector().getAuthConfig())),
                    String.class);
            log.info("Successfully created hubspot field {} on {}", request.getSchema().getApiName(),
                    request.getEntityName());
        } catch (HttpClientErrorException e) {
            log.error(e.getResponseBodyAsString());
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.warn("Field {} on entity {} already exists in hubspot", request.getSchema().getApiName(),
                        request.getEntityName());
            } else {
                ConnectorHelper.handleException(e);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to process JSON for field creation: {}", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }

        return request.getSchema();
    }

    public void checkCreateScope(CreateFieldRequest request) {
        ConnectorInfo connectorInfo = request.getConnector();
        if(connectorInfo.getMetaConfig().containsKey("oAuthScopes") &&
                StringUtils.isNotBlank(String.valueOf(connectorInfo.getMetaConfig().get("oAuthScopes"))) &&
                createFieldScopeMap.containsKey(request.getEntityName())) {
            String providedScopes = String.valueOf(connectorInfo.getMetaConfig().get("oAuthScopes"));
            String scope = createFieldScopeMap.get(request.getEntityName());
            if(!providedScopes.contains(scope)) {
                throw new RuntimeException("Access token does not have scope " + scope + " for entity " + request.getEntityName());
            }
        }
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        String path = String.format(deleteUrlMap.get("property"), request.getEntityName(),
                request.getFieldName());
        String url = getUrl("", request.getConnector().getAuthConfig(), path);

        restClient.getTemplate().exchange(url, HttpMethod.DELETE,
                new HttpEntity(restClient.getHeaders(request.getConnector().getAuthConfig())), String.class);
        log.info("Successfully deleted field {} in hubspot for {}", request.getFieldName(),
                request.getConnector().getName());
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        return getByWatermark(request, false);
    }

    public FetchResponse getByWatermark(SyncRequest request, boolean applyUpperBoundWM) {
        try {
            if (Constants.OWNER.equalsIgnoreCase(request.getEntityName())) {
                return getOwnersByWatermark(request);
            } else if (Constants.EVENT.equalsIgnoreCase(request.getEntityName())) {
                return getEventsByWatermark(request);
            } else if ("engagement".equalsIgnoreCase(request.getEntityName())) {
                return getEngagementsByWatermarkIterator(request);
                //return new FetchResponse(request.getWatermark(), iterator);
            } else if (Constants.ACTIVITY.equalsIgnoreCase(request.getEntityName())) {
                return getActivitiesByWatermark(request);
            } else if (Constants.FORM.equalsIgnoreCase(request.getEntityName())) {
                return getFormsByWatermark(request);
            } else if (Constants.FORM_SUBMISSION.equalsIgnoreCase(request.getEntityName())) {
                return getFormSubmissionByWatermark(request);
            } else if (Constants.EMAIL_EVENT.equalsIgnoreCase(request.getEntityName())) {
                return getEmailEventsByWatermark(request);
            } else if (request.getEntityName().contains(ASSOCIATION_SUFFIX)) {
                return getAssociationsByWatermark(request);
            }else {
                return getDataByWatermark(request, applyUpperBoundWM);
            }
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            ConnectorHelper.handleException(e);
            return null;
        }
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        List<EntityData> result = new ArrayList<>();
        String entity = request.getEntityName();
        HubspotRestClient restClient = new HubspotRestClient(new JsonParserConfig(null, null, null, "vid", true, null), mapper);
        List<EntityData> data = request.getData().get(request.getConnector().getId());
        var partitioned = Lists.partition(data, 100);

        if (Constants.ACTIVITY.equalsIgnoreCase(request.getEntityName())) {
            String msg = String.format("Hubspot getByIds operation not supported for entity %s", request.getEntityName());
            log.warn(msg);
            throw new RuntimeException(msg);
        }

        if(request.getEntityName().contains(ASSOCIATION_SUFFIX)) {
            partitioned.forEach(partition -> {
                List<String> ids = partition.stream().map(e -> e.getId()).filter(id -> !StringUtils.isBlank(id)).collect(Collectors.toList());
                if (CollectionUtils.isEmpty(ids)) return;

                String fromEntity = request.getEntityName().substring(0, request.getEntityName().indexOf(ASSOCIATION_SUFFIX));
                Map<String, EntityData> entityDataMap = new HashMap<>();
                Map<String, Set<String>> idMap = ids.stream().map(id -> id.split("-")).filter(arr -> arr.length == 5).collect(Collectors.groupingBy(arr -> arr[2],  Collectors.mapping(arr -> arr[0], Collectors.toSet())));
                idMap.keySet().forEach(toEntity -> {
                    request.setWatermark(new WatermarkInfo(0, Instant.now().toEpochMilli(), false, 0));
                    Pair<List<EntityData>, Long> associations = fetchAssociations(fromEntity, toEntity, idMap.get(toEntity), 0, request, new HashMap<>(), -1);
                    entityDataMap.putAll(associations.x.stream().collect(Collectors.toMap(association -> association.getId(), association -> association)));
                });
                ids.forEach(id -> {
                    if (entityDataMap.containsKey(id)) {
                        result.add(entityDataMap.get(id));
                    }
                });
            });
            return result;
        }
        
        switch (entity) {
            case "contact":
                partitioned.forEach(partition -> {
                    List<String> ids = partition.stream().map(e -> e.getId()).filter(id->!StringUtils.isBlank(id)).collect(Collectors.toList());
                    if (CollectionUtils.isEmpty(ids)) return;

                    String path = request.getEntitySchema().isCustom() ? 
                        String.format(getByIds.get("custom"), entity) : getByIds.get(entity);

                    String url = API_HOST + path + ids.stream().collect(Collectors.joining("&vid="));
                    List<EntityData> contactsByIds = restClient.getContactsByIds(
                            url, ids,
                            request.getConnector(), getTokenHandler(request.getConnector()));

                    List<EntityData> filteredContacts = contactsByIds.stream()
                            .filter(c -> c.getId().equalsIgnoreCase(c.getValueAsString(HS_OBJECT_ID)))
                            .collect(Collectors.toList());

                    filteredContacts.forEach(r->{
                        r.setName("contact");
                        r.setConnectorId(request.getConnector().getId());
                        if (r.getValue("lastmodifieddate") != null) {
                            r.setLastModified(Long.parseLong(r.getValue("lastmodifieddate").toString()));
                        }
                        if (r.getValue("createdate") != null) {
                            r.setCreatedAt(Long.parseLong(r.getValue("createdate").toString()));
                        }

                    });
                    result.addAll(filteredContacts);
                });
                break;
            case Constants.OWNER:
                partitioned.forEach(partition -> {
                    List<String> ids = partition.stream().map(e -> e.getId()).filter(id->!StringUtils.isBlank(id)).collect(Collectors.toList());
                    if (CollectionUtils.isEmpty(ids)) return;
                    ids.stream().forEach(id -> {
                        String url = getByIds.get(Constants.OWNER) + id;
                        Optional<EntityData> ownerById = restClient.getHubspotObjectById(
                                getUrl(Constants.OWNER, request.getConnector().getAuthConfig(), url),
                                request.getConnector(), getTokenHandler(request.getConnector()));
                        ownerById.ifPresent(owner -> {
                            owner.setName(Constants.OWNER);
                            owner.setId(owner.getValueAsString("id"));
                            owner.setConnectorId(request.getConnector().getId());
                            if (owner.getValue("updatedAt") != null) {
                                owner.setLastModified(HubspotIncrementalIterator.toTimestamp(owner.getValueAsString("updatedAt")));
                            }
                            if (owner.getValue("createdAt") != null) {
                                owner.setCreatedAt(HubspotIncrementalIterator.toTimestamp(owner.getValueAsString("createdAt")));
                            }
                            result.add(owner);
                        });
                    });
                });
                break;
            
            case Constants.EVENT:
                partitioned.forEach(partition -> {
                    List<String> ids = partition.stream().map(e -> e.getId()).filter(id->!StringUtils.isBlank(id)).collect(Collectors.toList());
                    if (CollectionUtils.isEmpty(ids)) return;
                    ids.stream().forEach(id -> {
                        String url = getByIds.get(Constants.EVENT) + id;
                        Optional<EntityData> eventById = restClient.getHubspotObjectById(
                                appendIncludeDeletes(getUrl(Constants.EVENT, request.getConnector().getAuthConfig(), url)),
                                request.getConnector(), getTokenHandler(request.getConnector()));
                        eventById.ifPresent(event -> {
                            event.setName(Constants.EVENT);
                            event.setId(event.getValueAsString("id"));
                            event.setConnectorId(request.getConnector().getId());
                            event.setLastModified(Instant.now().toEpochMilli());
                            event.setDeleted("DELETED".equalsIgnoreCase(event.getValueAsString("status").toString()));
                            result.add(event);
                        });
                    });
                });
                break;
            case "form":
                partitioned.forEach(partition -> {
                    List<String> ids = partition.stream().map(e -> e.getId()).filter(id->!StringUtils.isBlank(id)).collect(Collectors.toList());
                    if (CollectionUtils.isEmpty(ids)) return;
                    ids.stream().forEach(id -> {
                        String url = API_HOST + getByIds.get("form") + id;
                        Optional<EntityData> form = restClient.getHubspotObjectById(url, request.getConnector(), getTokenHandler(request.getConnector()));
                        form.ifPresent(f -> {
                            f.setId(f.getValueAsString("guid"));
                            f.setName("form");
                            f.setConnectorId(request.getConnector().getId());
                            result.add(f);
                        });
                    });
                });
                break;

            case "engagement":
                partitioned.forEach(partition -> {
                    List<String> ids = partition.stream().map(e -> e.getId()).filter(id->!StringUtils.isBlank(id)).collect(Collectors.toList());
                    if (CollectionUtils.isEmpty(ids)) return;
                    ids.stream().forEach(id -> {
                        String url = getByIds.get("engagement") + id;
                        Optional<EntityData> engagementById = restClient.getHubspotEngagementObject(
                                appendIncludeDeletes(getUrl("engagement", request.getConnector().getAuthConfig(), url)),
                                request.getConnector(), getTokenHandler(request.getConnector()));
                        engagementById.ifPresent(engagement -> {
                            Map engMap = (Map) engagement.getValue("engagement");
                            engagement.setName("engagement");
                            engagement.setId(engMap.get("id").toString());
                            engagement.setConnectorId(request.getConnector().getId());
                            if (engMap.containsKey("lastUpdated")) {
                                engagement.setLastModified((Long) engMap.get("lastUpdated"));
                            }
                            if (engMap.containsKey("createdAt")) {
                                engagement.setCreatedAt((Long) engMap.get("createdAt"));
                            }
                            // Augment with associations.
                            engagement.setValues(
                                HubspotRestClient.flattenEngagementResults(engMap.get("type").toString(), engagement.getValues())
                            );
                            if(engagement.getValue("hs_engagement_type") != null && engagement.getValueAsString("hs_engagement_type").equalsIgnoreCase("CALL")) {
                                setCallDispositionLabel(request, engagement);
                            }
                            result.add(engagement);
                        });
                    });
                });
                break;

            default:
                if(!request.getEntitySchema().isCustom() && !getByIds.containsKey(entity)) {
                    throw new RuntimeException("Get by id not supported for entity - " + entity);
                }
                partitioned.forEach(partition -> {
                    List<Map<String,String>> ids = partition.stream().filter(e -> !StringUtils.isBlank(e.getId())).map(e -> Map.of("id",e.getId())).collect(Collectors.toList());
                    if (CollectionUtils.isEmpty(ids)) return;
                    Map<String, String> idMap = new HashMap();
                    partition.forEach(e ->{
                        if(!StringUtils.isBlank(e.getId())){
                            idMap.put(e.getId(),e.getSyncariEntityId());
                        }
                    });
                    List<String> properties = getPropertyList(request);

                    Map<String, Object> payload = new HashMap();
                    payload.put("properties", properties);
                    payload.put("inputs",ids);

                    String path = request.getEntitySchema().isCustom() ? String.format(getByIds.get("custom"), entity) : getByIds.get(entity);

                    String url = API_HOST + path;
                    ResponseEntity<String> responseEntity = restClient.postRaw(url, rethrow(() -> mapper.writeValueAsString(payload)), request.getConnector(), getTokenHandler(request.getConnector()));
                    List<EntityData> entityData = HubspotIncrementalIterator.toEntityData(responseEntity.getBody(), entity, request.getConnector().getId(), mapper);
                    if("deal".equalsIgnoreCase(entity)) {
                        HubspotIncrementalIterator.addDealAssociations(entityData, request.getConnector(), restClient, mapper, getTokenHandler(request.getConnector()));
                    }
                    if("line_item".equalsIgnoreCase(entity)) {
                        HubspotIncrementalIterator.addLineItemAssociations(entityData,  request.getConnector(), restClient, mapper, getTokenHandler(request.getConnector()));
                    }
                    if (HubspotService.QUOTE.equalsIgnoreCase(entity)) {
                        HubspotIncrementalIterator.addQuoteAssociations(entityData,  request.getConnector(), restClient, mapper, getTokenHandler(request.getConnector()));
                    }
                    entityData.forEach(e -> e.setSyncariEntityId(idMap.get(e.getId())));
                    result.addAll(entityData);
                });
                break;
        }
        final EntitySchema entitySchema = request.getEntitySchema();
        fixMultivaluedFields(result, entitySchema);

        return result;
    }

    private void setCallDispositionLabel(SyncRequest request, EntityData engagement) {
        CacheKey key = new CacheKey(request.getConnector());
        Map<String, String> dispositionMap = dispositionCache.getUnchecked(key);
        String dispositionId = engagement.getValueAsString("hs_call_disposition");
        if(StringUtils.isNotEmpty(dispositionId) && dispositionMap.containsKey(dispositionId)) {
            engagement.addValue("hs_call_disposition", dispositionMap.get(dispositionId));
        } else {
            dispositionMap = refreshCallDispositions(request.getConnector());
            if(dispositionMap.containsKey(dispositionId)) {
                engagement.addValue("hs_call_disposition", dispositionMap.get(dispositionId));
            } else {
                engagement.addValue("hs_call_disposition", "UNKNOWN_DISPOSITION");
            }
            dispositionCache.put(key, dispositionMap);
        }
    }

    private void setCallDispositionId(SyncRequest request, EntityData engagement) {
        String dispositionLabel = engagement.getValueAsString("hs_call_disposition");
        if(StringUtils.isNotEmpty(dispositionLabel) && !dispositionLabel.equalsIgnoreCase("UNKNOWN_DISPOSITION")) {
            CacheKey key = new CacheKey(request.getConnector());
            Map<String, String> dispositions = dispositionCache.getUnchecked(key);
            Set<String> dispositionIds = getDispositionIds(dispositionLabel, dispositions);
            if(dispositionIds.size() == 1) {
                engagement.addValue("hs_call_disposition", dispositionIds.iterator().next());
            } else if(dispositionIds.size() == 0) {
                dispositions = refreshCallDispositions(request.getConnector());
                dispositionCache.put(key, dispositions);
                dispositionIds = getDispositionIds(dispositionLabel, dispositions);
                if (dispositionIds.size() == 1) {
                    engagement.addValue("hs_call_disposition", dispositionIds.iterator().next());
                }
            }
            if(dispositionIds.size() == 0) {
                throw new RuntimeException(format("Disposition %s does not exist in Hubspot. Please create, refresh the schema and try again.", dispositionLabel));
            } else if(dispositionIds.size() > 1){
                throw new RuntimeException(format("Multiple ids found for disposition %s in Hubspot. Please delete duplicates", dispositionLabel));
            }
        }
    }

    private Set<String> getDispositionIds(String disposition, Map<String, String> dispositions) {
        Set<String> dispositionIds = dispositions.entrySet()
                .stream()
                .filter(entry -> Objects.equals(entry.getValue(), disposition))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (dispositionIds.isEmpty()){
            dispositionIds = dispositions.entrySet()
                    .stream()
                    .filter(entry -> Objects.equals(entry.getKey(), disposition))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }
        return dispositionIds;
    }

    public Map<String, String> refreshCallDispositions(ConnectorInfo connector) {
        HubspotRestClient restClient = new HubspotRestClient();
        return restClient.getCallDispositionMap(getUrl("dispositions",
                connector.getAuthConfig(), CALL_DISPOSITIONS), connector, getTokenHandler(connector));
    }

    private List<String> getPropertyList(SyncRequest request) {
        List<String> properties = new ArrayList<>();
        for (AttributeSchema a : request.getEntitySchema().getAttributes()) {
            if ("deal".equalsIgnoreCase(request.getEntitySchema().getApiName()) && (
                    "accountId".equalsIgnoreCase(a.getApiName()) || "associatedVids".equalsIgnoreCase(a.getApiName())
            )) continue;
            properties.add(a.getApiName());
        }
        return properties;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        EntityDataBatchIterator iterator = getByWatermark(request).getIterator();
        while (iterator.hasNext()) {
            List<EntityData> data = iterator.next();
            if (data == null || data.isEmpty())
                break;
            return data.get(0).getLastModified();
//            return Long.parseLong(
//                    data.get(0).getValue(request.getEntitySchema().getWatermarkField().getApiName()).toString());
        }
        return 0;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        SyncResponse response = new SyncResponse();

        if ((!createUrlMap.containsKey(request.getEntityName()) && !request.getEntitySchema().isCustom()) && !request.getEntityName().contains(ASSOCIATION_SUFFIX)) {
            String msg = String.format("Hubspot Create operation not supported for entity %s", request.getEntityName());
            log.warn(msg);
            response.appendError(msg);
            return response;
        }

        HubspotRestClient restClient = new HubspotRestClient(
                getSingleJsonConfig(request.getEntityName()),mapper);
        List<EntityData> toBeCreated = request.getData().get(request.getConnector().getId());
        Map<String, AttributeSchema> attribMap = request.getEntitySchema().getAttributes().stream().collect(
                Collectors.toMap(AttributeSchema::getApiName, x -> x));
        List<String> syncariIds = new ArrayList<>();
        List<Map<String,Object>> batchPayload = new ArrayList<>();
        List<Pair<EntityData, Map<String, Object>>> parallelCreateList = new ArrayList<>();
        for (EntityData data : toBeCreated) {
            String path = "";
            if (createUrlMap.containsKey(request.getEntityName())) {
                path = createUrlMap.get(request.getEntityName());
            } else if (request.getEntitySchema().isCustom()) {
                path = String.format(CREATE_CUSTOM_BATCH, request.getEntityName());
            } else if (request.getEntityName().contains(ASSOCIATION_SUFFIX)) {
                String fromObjectType = data.getValueAsString("fromObjectType");
                String fromObjectId = data.getValueAsString("fromObjectId");
                String toObjectType = data.getValueAsString("toObjectType");
                String toObjectId = data.getValueAsString("toObjectId");
                String category = data.getValueAsString("category");
                String typeId = data.getValueAsString("typeId");
                if(category != null && typeId != null) {
                    path = String.format(ASSOCIATIONS_CREATE, fromObjectType, fromObjectId, toObjectType, toObjectId);
                } else {
                    path = String.format(ASSOCIATIONS_CREATE_DEFAULT, fromObjectType, fromObjectId, toObjectType, toObjectId);
                }
            } else {
                // This condition is already captured as a validation in beginning of this method.
            }

            String url = getUrl(request.getEntityName(), request.getConnector().getAuthConfig(), path);
            // TODO: User objectmapper instead of string concat

            Map<String, Object> payload =new HashMap<>();
            if ("engagement".equalsIgnoreCase(request.getEntityName())) {
                if(data.getValue("hs_engagement_type") != null && data.getValueAsString("hs_engagement_type").equalsIgnoreCase("CALL")) {
                    setCallDispositionId(request, data);
                }
                String engagementType = data.getValueAsString("hs_engagement_type");
                payload = HubspotRestClient.unFlattenEngagementObject(engagementType, data.getValues());
                validateEngagementPayload(engagementType, payload);
            } else if ("ticket".equalsIgnoreCase(request.getEntityName())) {

                Map<String,Object> properties = new HashMap<>();
                data.getValues().forEach((k, v) -> {
                    Object value = getTransformedValue(attribMap, k, v);
                    properties.put(k, value);
                });
                payload.put("properties", properties);
            } else if("line_item".equalsIgnoreCase(request.getEntityName())){
                Map<String,Object> properties = new HashMap<>();
                if(data.getValues().containsKey("recurringbillingfrequency") && data.getValues().get("recurringbillingfrequency") != null){
                    data.getValues().put("recurringbillingfrequency",HubspotSeed.getRecurringBillingApiField((String)data.getValues().get("recurringbillingfrequency")));
                }
                data.getValues().forEach((k, v) -> {
                    Object value = getTransformedValue(attribMap, k, v);
                    properties.put(k, value);
                });
                payload.put("properties",properties);
                if(data.getValues().get("hs_deal_id") != null) {
                    Map assoc = Map.of("to", data.getValues().get("hs_deal_id"), "types", List.of(Map.of("associationCategory", "HUBSPOT_DEFINED", "associationTypeId", 20)));
                    payload.put("associations", List.of(assoc));
                }

                if(data.getValues().get("hs_quote_id") != null) {
                    Map assoc = Map.of("to", data.getValues().get("hs_quote_id"), "types", List.of(Map.of("associationCategory", "HUBSPOT_DEFINED", "associationTypeId", 68)));
                    payload.put("associations", List.of(assoc));
                }
            } else if ("product".equalsIgnoreCase(request.getEntityName())
                    ||"invoice".equalsIgnoreCase(request.getEntityName())
                    || "subscription".equalsIgnoreCase(request.getEntityName())) {
                Map<String,Object> properties = new HashMap<>();
                data.getValues().forEach((k, v) -> {
                    Object value = getTransformedValue(attribMap, k, v);
                    properties.put(k, value);
                });
                payload.put("properties", properties);
            } else if ("lead".equalsIgnoreCase(request.getEntityName())) {
                addLeadAssociations(data, attribMap, payload);
            } else if ("quote".equalsIgnoreCase(request.getEntityName())) {
                Map<String,Object> properties = new HashMap<>();
                data.getValues().forEach((k, v) -> {
                    if(!"associatedcompanyid".equalsIgnoreCase(k) && !"associatedVids".equalsIgnoreCase(k) && !"associateddealid".equalsIgnoreCase(k)) {
                        Object value = getTransformedValue(attribMap, k, v);
                        properties.put(k, value);
                    }
                });
                payload.put("properties", properties);
                List<Map> assocList = new ArrayList<>();
                if(data.getValues().get("associatedcompanyid") != null) {
                    Map assoc = Map.of("to", data.getValues().get("associatedcompanyid"), "types", List.of(Map.of("associationCategory", "HUBSPOT_DEFINED", "associationTypeId", 71)));
                    assocList.add(assoc);
                }
                if(data.getValues().get("associateddealid") != null) {
                    Map assoc = Map.of("to", data.getValues().get("associateddealid"), "types", List.of(Map.of("associationCategory", "HUBSPOT_DEFINED", "associationTypeId", 64)));
                    assocList.add(assoc);
                }
                if(data.getValues().get("associatedVids") != null) {

                    List<Object> values = (List) data.getValues().get("associatedVids");
                    for (var v : values) {
                        Map assoc = Map.of("to",v, "types", List.of(Map.of("associationCategory", "HUBSPOT_DEFINED", "associationTypeId", 69)));
                        assocList.add(assoc);
                    }
                }

                if (!assocList.isEmpty()) {
                    payload.put("associations", assocList);
                }

            } else {
                List<Map<String,Object>> properties = new ArrayList<>();
                data.getValues().forEach((k, v) -> {
                    Object value = getTransformedValue(attribMap, k, v);
    //                // The field accountId for deal is transformed as a separate property. Hence remove it from property list
                    if(isSupportedAssociation(request, k)) {
                        return;
                    }
                    String propKey = request.getEntitySchema().isCustom() ? "property" : createPropertyNameMap.get(request.getEntityName());
                    properties.add(FluentMap.of("value",value).add(propKey, k));
                });

                Map<String, Object> additionalProps = getAdditionalProperties(request, data);
                payload =new HashMap<>(additionalProps);
                payload.put("properties", properties);
            }
            if(isBatchModeRequest(request, CREATE)) {
            	batchPayload.add(payload);
                syncariIds.add(data.getSyncariEntityId());
            	continue;
            }
            if(PARALLEL_WRITE_SUPPORT.contains(request.getEntityName())) {
                parallelCreateList.add(Pair.of(data, payload));
                continue;
            }
            try {
                EntityData d = null;
                if ("engagement".equalsIgnoreCase(request.getEntityName())) {
                    d = restClient.postEngagement(url, payload, HttpMethod.POST, request.getConnector(), getTokenHandler(request.getConnector()));
                    response.getResults().add(new Result(true, d.getId(), data.getSyncariEntityId()));
                } else if (request.getEntitySchema().isCustom()) {
                    doBatchOperation(request, response, restClient, "create", List.of(payload), List.of(data.getSyncariEntityId()), List.of(), Map.of());
                } else if (request.getEntityName().contains(ASSOCIATION_SUFFIX)) {
                    List<Map<String, Object>> properties = new ArrayList<>();
                    String category = data.getValueAsString("category");
                    String typeId = data.getValueAsString("typeId");
                    if(category != null && typeId != null) {
                        properties.add(Map.of("associationCategory", category, "associationTypeId", typeId));
                    }
                    try {
                        ResponseEntity<String> responseEntity = restClient.put(url, mapper.writeValueAsString(properties), request.getConnector(), getTokenHandler(request.getConnector()));
                        String fromObjectId = data.getValueAsString("fromObjectId");
                        String toObjectId = data.getValueAsString("toObjectId");
                        String toObjectType = data.getValueAsString("toObjectType");
                        String id = getId(fromObjectId, toObjectId, toObjectType, category, typeId);
                        if(responseEntity.getStatusCode().is2xxSuccessful()) {
                            if(category == null && typeId == null) {
                                String responseBody = responseEntity.getBody();
                                try {
                                    Map<String, Object> map = mapper.readValue(responseBody, Map.class);
                                    List<Map<String, Object>> results = (List<Map<String, Object>>) map.get("results");
                                    for(Map<String, Object> responseResult : results) {
                                        String fromId = ((Map<String, String>) responseResult.get("from")).get("id");
                                        String toId = ((Map<String, String>) responseResult.get("to")).get("id");
                                        if(fromId.equalsIgnoreCase(fromObjectId) && toId.equalsIgnoreCase(toObjectId)) {
                                            Map<String, Object> associationSpec = (Map<String, Object>) responseResult.get("associationSpec");
                                            id = getId(fromObjectId, toObjectId, toObjectType, String.valueOf(associationSpec.get("associationCategory")), String.valueOf(associationSpec.get("associationTypeId")));
                                            response.getResults().add(new Result(true, id, data.getSyncariEntityId()));
                                        }
                                    }
                                } catch (Exception e) {
                                    Result result = new Result(false, null, data.getSyncariEntityId());
                                    result.setErrors(List.of(e.getMessage()));
                                    response.getResults().add(result);
                                }
                            } else {
                                response.getResults().add(new Result(true, id, data.getSyncariEntityId()));
                            }
                        } else {
                            Result result = new Result(false, id, data.getSyncariEntityId());
                            result.setErrors(List.of(responseEntity.getBody()));
                            response.getResults().add(result);
                        }
                    } catch (JsonProcessingException e) {
                        log.error(ExceptionUtils.getStackTrace(e));
                    }
                } else {
                    d = restClient.post(url, payload, request.getConnector(), getTokenHandler(request.getConnector()));
                    if(d.getId() == null && d.getValues().containsKey(HS_OBJECT_ID) && d.getValues().get(HS_OBJECT_ID) != null) {
                        d.setId(d.getValues().get(HS_OBJECT_ID).toString());
                    }
                    response.getResults().add(new Result(true, d.getId(), data.getSyncariEntityId()));
                }
            } catch (NonRetriableException e) {
                log.error(e.getMessage(), e);
                Result error = new Result(false, null, data.getSyncariEntityId());
                error.getErrors().add(e.getMessage());
                response.getResults().add(error);
            } catch (RestClientException e) {
                log.error(e.getMessage(), e);
                Result error = new Result(false, null, data.getSyncariEntityId());
                error.getErrors().add(e.getMessage());
                response.getResults().add(error);
            }
        }
        if(isBatchModeRequest(request, CREATE)) {
        	doBatchOperation(request, response, restClient, "create", batchPayload, syncariIds, List.of(), Map.of());
        }
        if(PARALLEL_WRITE_SUPPORT.contains(request.getEntityName())) {
            doParallelWrites(request, response, restClient, "create", parallelCreateList);
        }
        return response;
    }

    private void addLeadAssociations(EntityData data, Map<String, AttributeSchema> attribMap, Map<String, Object> payload) {
        Map<String,Object> properties = new HashMap<>();
        data.getValues().forEach((k, v) -> {
            if(!"hs_primary_contact_id".equalsIgnoreCase(k)) {
                Object value = getTransformedValue(attribMap, k, v);
                properties.put(k, value);
            }
        });
        payload.put("properties", properties);
        List<Map> assocList = new ArrayList<>();
        if(data.getValues().get("hs_primary_contact_id") != null) {
            Map assoc = Map.of("to", data.getValues().get("hs_primary_contact_id"), "types", List.of(Map.of("associationCategory", "HUBSPOT_DEFINED", "associationTypeId", 578)));
            assocList.add(assoc);
            properties.remove("hs_primary_contact_id");
        }
        if(data.getValues().get("hs_primary_company_id") != null) {
            Map assoc = Map.of("to", data.getValues().get("hs_primary_company_id"), "types", List.of(Map.of("associationCategory", "HUBSPOT_DEFINED", "associationTypeId", 580)));
            assocList.add(assoc);
            properties.remove("hs_primary_company_id");
        }
        if (!assocList.isEmpty()) {
            payload.put("associations", assocList);
        }
    }

    private void create(SyncRequest request, EntityData data, HubspotRestClient restClient, String url, Map<String, Object> payload, ConcurrentLinkedQueue<Result> results) {
        try {
            EntityData d = restClient.post(url, payload, request.getConnector(), getTokenHandler(request.getConnector()));
            if (d.getId() == null && d.getValues().containsKey(HS_OBJECT_ID) && d.getValues().get(HS_OBJECT_ID) != null) {
                d.setId(d.getValues().get(HS_OBJECT_ID).toString());
            }
            results.add(new Result(true, d.getId(), data.getSyncariEntityId()));
        } catch (NonRetriableException e) {
            log.error(e.getMessage(), e);
            Result error = new Result(false, null, data.getSyncariEntityId());
            error.getErrors().add(e.getMessage());
            results.add(error);
        } catch (RestClientException e) {
            log.error(e.getMessage(), e);
            Result error = new Result(false, null, data.getSyncariEntityId());
            error.getErrors().add(e.getMessage());
            results.add(error);
        }
    }

    private void update(SyncRequest request, EntityData data, HubspotRestClient restClient, String url, Map<String, Object> payload, ConcurrentLinkedQueue<Result> results) {
        try {
            restClient.patch(String.format(url, data.getId()), payload, request.getConnector(), getTokenHandler(request.getConnector()));
            results.add(new Result(true, data.getId(), data.getSyncariEntityId()));
        } catch (NonRetriableException e) {
            log.error(e.getMessage(), e);
            Result error = new Result(false, data.getId(), data.getSyncariEntityId());
            error.getErrors().add(e.getMessage());
            results.add(error);
        } catch (RestClientException e) {
            log.error(e.getMessage(), e);
            Result error = new Result(false, data.getId(), data.getSyncariEntityId());
            error.getErrors().add(e.getMessage());
            results.add(error);
        }
    }

    private void doParallelWrites(SyncRequest request, SyncResponse response, HubspotRestClient restClient, String operation, List<Pair<EntityData, Map<String, Object>>> parallelWriteList) {
        String path = operation.equalsIgnoreCase("create") ? createUrlMap.get(request.getEntityName()) : updateUrlMap.get(request.getEntityName());
        String url = getUrl(request.getEntityName(), request.getConnector().getAuthConfig(), path);
        ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
        Integer threadCount = getThreadCount(request);
        List<List<Pair<EntityData, Map<String, Object>>>> partitions = partitionIntoNParts(parallelWriteList, threadCount);
        if(threadCountTracker.get() < MAX_THREAD_LIMIT && request.getConnector().getInternalConfig().containsKey("threadCount")) {
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

            // Increment the thread count tracker by the number of threads created
            threadCountTracker.addAndGet(threadCount);

            // Create a list of CompletableFuture tasks
            List<CompletableFuture<Void>> futures = partitions.stream()
                    .map(partition -> CompletableFuture.runAsync(() -> writePartition(request, partition, restClient, results, response, url, operation), executorService))
                    .collect(Collectors.toList());

            awaitFuturesAndFinalizeResponse(futures, executorService, response, results);

            // Decrement the thread count tracker by the number of threads created
            threadCountTracker.addAndGet(-threadCount);
        } else {
            log.error("Max thread count reached {}. Falling back to single thread mode", threadCountTracker.get());
            writePartition(request, parallelWriteList, restClient, results, response, url, operation);
            response.setResults(new ArrayList<>(results));
        }
    }

    private void writePartition(SyncRequest request, List<Pair<EntityData, Map<String, Object>>> partition, HubspotRestClient restClient, ConcurrentLinkedQueue<Result> results, SyncResponse response, String url, String operation) {
        partition.forEach(pair -> {
            if(operation.equalsIgnoreCase("create")) {
                create(request, pair.x, restClient, url, pair.y, results);
            } else {
                update(request, pair.x, restClient, url, pair.y, results);
            }
        });
    }

    private void awaitFuturesAndFinalizeResponse(List<CompletableFuture<Void>> futures, ExecutorService executorService, SyncResponse response, ConcurrentLinkedQueue<Result> results) {
        // Combine all futures and wait for all of them to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        // Join the combined future to wait for completion
        allFutures.join();

        // Shutdown the executor service
        executorService.shutdown();

        response.setResults(new ArrayList<>(results));
    }

    private static Integer getThreadCount(SyncRequest request) {
        return (Integer) request.getConnector().getInternalConfig().getOrDefault("threadCount", 3);
    }

    public List<List<Pair<EntityData, Map<String, Object>>>> partitionIntoNParts(List<Pair<EntityData, Map<String, Object>>> list, int n) {
        int totalSize = list.size();
        int partitionSize = totalSize / n;
        int remainder = totalSize % n;

        List<List<Pair<EntityData, Map<String, Object>>>> partitions = IntStream.range(0, n)
                .mapToObj(i -> {
                    int start = i * partitionSize + Math.min(i, remainder);
                    int end = start + partitionSize + (i < remainder ? 1 : 0);
                    return new ArrayList<>(list.subList(start, end));
                })
                .collect(Collectors.toList());

        return partitions;
    }

    private boolean isBatchModeRequest(SyncRequest request, String operation) {

        if (("contact".equalsIgnoreCase(request.getEntityName()) || request.getEntitySchema().isCustom()
                || "deal".equalsIgnoreCase(request.getEntityName()) || "line_item".equalsIgnoreCase(request.getEntityName())
                || "invoice".equalsIgnoreCase(request.getEntityName()))
                && UPDATE.equalsIgnoreCase(operation)) {
            return true;
        }
        return false;
    }

    private String getBatchCreateURL(SyncRequest request) {
        // Only Contacts and custom entities supported for batch. when we support additional entities, generalize.
        String path = request.getEntitySchema().isCustom() ? String.format(CREATE_CUSTOM_BATCH, request.getEntityName()) : CREATE_CONTACT_BATCH;
        return getUrl(request.getEntityName(), request.getConnector().getAuthConfig(), path);
    }

    private String getBatchUpdateURL(SyncRequest request) {
        String path = "";
        if (request.getEntitySchema().isCustom()) {
            path = String.format(UPDATE_CUSTOM_BATCH, request.getEntityName());
        } else {
            path = BATCH_UPDATE_URLS.getOrDefault(request.getEntityName(), "");
        }
        return getUrl(request.getEntityName(), request.getConnector().getAuthConfig(), path);
    }

    private void doBatchOperation(SyncRequest request, SyncResponse response, HubspotRestClient restClient,
            String operation, List<Map<String,Object>> batchPayload, List<String> syncariIds, List<String> externalIds, Map<String, String> externalIdToSyncariIdMap) {
        try {
            String url = "";
            if ("create".equals(operation.toLowerCase())) {
                url = getBatchCreateURL(request);
            } else {
                url = getBatchUpdateURL(request);
            }
            
            List<Map<String, Object>> batchFormat = batchPayload.stream()
                    .map(record-> toBatchFormat(record, "update".equals(operation.toLowerCase()), request.getEntityName()))
                    .collect(Collectors.toList());
            List<List<Map<String, Object>>> partitions = Lists.partition(batchFormat, 10);
            List<List<String>> idPartitions = Lists.partition(syncariIds, 10);
            List<List<String>> externalIdPartitions = Lists.partition(externalIds, 10);
            int partitionIndex = 0;
            for (List<Map<String, Object>> partition : partitions) {
                try {
                    postOperation(request, response, restClient, operation, url, idPartitions, partitionIndex, partition, externalIdToSyncariIdMap);
                    partitionIndex++;
                } catch (NonRetriableException e1) {
                    if(operation.equalsIgnoreCase("update")) {
                        log.error("Error in batch operation - {}. Trying to update records one by one", e1.getMessage());
                        for(int idx = 0; idx < partition.size(); idx++) {
                            var record = partition.get(idx);
                            try {
                                postOperation(request, response, restClient, operation, url, idPartitions, partitionIndex, List.of(record), externalIdToSyncariIdMap);
                            } catch (NonRetriableException e2) {
                                log.error(e2.getMessage(), e2);
                                Result result = new Result(false, externalIdPartitions.get(partitionIndex).get(idx), idPartitions.get(partitionIndex).get(idx));
                                result.addError(e2.getMessage());
                                response.getResults().add(result);
                            }
                        }
                        partitionIndex++;
                    } else {
                        throw e1;
                    }
                }
            }
        } catch (RestClientException e) {
            log.error(e.getMessage(), e);
            response.setErrors(List.of(e.getMessage()));
        }
    }

    private void postOperation(SyncRequest request, SyncResponse response, HubspotRestClient restClient, String operation, String url, List<List<String>> idPartitions, int partitionIndex,
                               List<Map<String, Object>> recordList, Map<String, String> externalIdToSyncariIdMap) {
        String payload = rethrow(() -> mapper.writeValueAsString(Map.of("inputs", recordList)));
        log.debug("Hubspot request payload {}", payload);
        ResponseEntity<String> responseEntity = restClient.postRaw(url,
                payload, request.getConnector(), getTokenHandler(request.getConnector()));
        log.debug("Hubspot batch {} result code {}", operation, responseEntity.getStatusCode());

        String entityName = request.getEntitySchema() != null ?
                           request.getEntitySchema().getApiName() : "unknownEntity";

        log.debug("Hubspot batch {} result {}", operation, responseEntity);
        ReadContext ctx = JsonPath.parse(responseEntity.getBody());
        List<Map<String, Object>> results = getSafeJsonPath(ctx, "results");
        List<Map<String, Object>> errors = getSafeJsonPath(ctx, "errors");

        // Process results and collect HubSpot IDs
        List<String> createdHubspotIds = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            Map row = (Map) results.get(i);
            if (row.get("id") == null) {
                log.error("HubSpot batch {} {} - Result at index {} has null ID", operation, entityName, i);
            } else {
                createdHubspotIds.add(row.get("id").toString());
            }
            String externalId = row.get("id").toString();
            String syncariId = externalIdToSyncariIdMap.getOrDefault(externalId, idPartitions.get(partitionIndex).get(i));
            response.getResults().add(new Result(true, externalId, syncariId));
        }

        // Log batch summary with created IDs
        int batchSize = idPartitions.get(partitionIndex).size();
        log.info("HubSpot batch {} {} - Status: {}, Batch size: {}, Results: {}, Errors: {}, Created IDs: [{}]",
            operation, entityName, responseEntity.getStatusCode(), batchSize,
            results.size(), errors.size(), String.join(", ", createdHubspotIds));

        // Process errors
        for (Map<String, Object> error : errors) {
            if (error == null || error.get("context") == null || error.get("message") == null) {
                log.warn("Invalid error entry encountered: {}", error);
                continue;
            }

            String errorStatus = (String) error.get("status");
            String errorCategory = (String) error.get("category");
            String errorMessage = (String) error.get("message");
            Map<String, Object> context = (Map<String, Object>) error.get("context");
            List<String> ids = context != null ? (List<String>) context.get("ids") : null;

            if (ids != null) {
                for (String id : ids) {
                    if (id == null) {
                        log.warn("Invalid ID encountered in error context.");
                        continue;
                    }
                    String syncariId = externalIdToSyncariIdMap.get(id);
                    log.error("Error for ID {}: Status={}, Category={}, Message={}", id, errorStatus, errorCategory, errorMessage);
                    response.getResults().add(new Result(false, id, syncariId).addError(errorMessage));
                }
            } else {
                log.error("Error without associated IDs: Status={}, Category={}, Message={}", errorStatus, errorCategory, errorMessage);
            }
        }
    }

    private List<Map<String, Object>> getSafeJsonPath(ReadContext ctx, String path) {
        try {
            return ctx.read(path, List.class);
        } catch (PathNotFoundException e) {
            log.debug("Path '{}' not found in the JSON response.", path);
            return Collections.emptyList();
        }
    }

    protected Map<String, Object> toBatchFormat(Map<String,Object> record, boolean isUpdate, String entityName){
    	Map<String, Object> batchRecord = new HashMap<>();
        Map<String, Object> batchMap = new HashMap<>();
        if(entityName.equalsIgnoreCase("line_item") || entityName.equalsIgnoreCase("invoice")
                || entityName.equalsIgnoreCase("subscription")) {
            batchRecord = (Map<String, Object>) record.getOrDefault("properties", List.of());
            batchMap.put("associations", record.getOrDefault("associations", List.of()));
        } else {
            List<Map<String, Object>> fields = (List<Map<String, Object>>) record.getOrDefault("properties", List.of());
            for (Map<String, Object> field : fields) {
                batchRecord.put(field.get("property").toString().toLowerCase(), field.get("value"));
            }
        }
        batchMap.put("properties",batchRecord);
        if (isUpdate) {
            batchMap.put("id", (String) record.getOrDefault("id", ""));
        }
        return batchMap;
    }
    private void validateEngagementPayload(String engagementType, Map<String, Object> payload) {
        Map<String, String> metadata = (Map<String, String>) payload.get("metadata");
        if("CALL".equalsIgnoreCase(engagementType)) {
            String status = metadata.get("status");
            if (!VALID_ENGAGEMENT_STATUSES.contains(status)) {
                throw new NonRetriableException(ErrorCodes.BAD_REQUEST,
                        String.format("Invalid hubspot engagement call status - %s", status), "400");
            }
        }
    }

    private boolean isSupportedAssociation(SyncRequest request, String k) {
        return List.of(DEAL, ENGAGEMENT).contains(request.getEntityName().toLowerCase()) && 
            ("associatedcompanyid".equalsIgnoreCase(k) || "associatedvids".equalsIgnoreCase(k));
    }

    private FluentMap<String,Object> asMap(String k1, Object v1){
        return FluentMap.of(k1,v1);
    }


    @Override
    public SyncResponse delete(SyncRequest request) {
        SyncResponse response = new SyncResponse(true);

        if (!deleteUrlMap.containsKey(request.getEntityName()) && !request.getEntitySchema().isCustom() && !request.getEntityName().contains(ASSOCIATION_SUFFIX)) {
            String msg = String.format("Hubspot Delete operation not supported for entity %s", request.getEntityName());
            log.warn(msg);
            response.appendError(msg);
            return response;
        }

        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(
                getSingleJsonConfig(request.getEntityName()),mapper);
        List<EntityData> toBeDeleted = request.getData().get(request.getConnector().getId());
        String syncariId=null;
        for (EntityData data : toBeDeleted) {
            try{
                syncariId = data.getSyncariEntityId();
                if (request.getEntityName().contains(ASSOCIATION_SUFFIX)) {
                    String fromObjectType = request.getEntityName().substring(0, request.getEntityName().indexOf(ASSOCIATION_SUFFIX));
                    Map<String, String> parsedId = parseId(data.getId());
                    if(parsedId.size() == 5) {
                        String fromObjectId = parsedId.get("fromObjectId");
                        String toObjectType = parsedId.get("toObjectType");
                        String toObjectId = parsedId.get("toObjectId");
                        String category = parsedId.get("category");
                        String typeId = parsedId.get("typeId");
                        String path = String.format(ASSOCIATION_DELETE, fromObjectType, toObjectType);
                        String url = getUrl(request.getEntityName(), request.getConnector().getAuthConfig(), path);
                        Map<String, Object> inputPayload = new HashMap<>();

                        inputPayload.put("inputs", List.of(
                                Map.of("from", Map.of("id", fromObjectId),
                                        "to", Map.of("id", toObjectId),
                                        "types", List.of(Map.of("associationCategory", category, "associationTypeId", typeId)))
                        ));
                        ResponseEntity<String> responseEntity = restClient.postRaw(url, mapper.writeValueAsString(inputPayload), request.getConnector().getAuthConfig());
                        if (responseEntity.getStatusCode() != HttpStatus.NO_CONTENT) {
                            Result error = new Result(false, null, syncariId);
                            error.setErrors(List.of(responseEntity.getBody()));
                            response.getResults().add(error);
                        }
                    } else {
                        Result error = new Result(false, null, syncariId);
                        error.setErrors(List.of("Malformed id for association - " + data.getId()));
                        response.getResults().add(error);
                    }
                } else {
                    String path = request.getEntitySchema().isCustom() ?
                            String.format(deleteUrlMap.get("custom"), request.getEntityName(), data.getId()) :
                            String.format(deleteUrlMap.get(request.getEntityName()), data.getId());
                    String url = getUrl(request.getEntityName(), request.getConnector().getAuthConfig(), path);

                    restClient.delete(url, request.getConnector().getAuthConfig());
                    response.getResults().add(new Result(true, data.getId(), data.getSyncariEntityId()));
                    log.debug("Successfully deleted {} {}", request.getEntityName(), data.getId());
                }
            }catch (NonRetriableException e) {
                log.error(e.getMessage(), e);
                Result error = new Result(false, null, syncariId);
                error.getErrors().add(e.getMessage());
                response.getResults().add(error);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                Result error = new Result(false, null, syncariId);
                error.getErrors().add(e.getMessage());
                response.getResults().add(error);
            }
        }

        return response;
    }

    @Override
    public MergeResponse merge(MergeRequest request) {
        try {
            MergeResponse mergeResult = new MergeResponse();
            SyncResponse winnerResult = new SyncResponse();
            SyncResponse loserResult = new SyncResponse();
            String winnerId = request.getWinner().getId();
            String url = format(API_HOST + CONTACT_MERGE);
            SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(
                    getSingleJsonConfig(request.getEntityName()), mapper);
            try {
                if ("contact".equalsIgnoreCase(request.getEntityName())) {
                    postMerge(request, mergeResult, winnerResult, loserResult, winnerId, url, restClient, request.getConnector().getAuthConfig());
                } else {
                    return CommonDataService.super.merge(request);
                }
            } catch (NonRetriableException e) {
                if(e.getMessage().contains("This oauth-token is expired")) {
                    ConnectorInfo connectorInfo = request.getConnector();
                    forceRefreshToken(connectorInfo);
                    if ("contact".equalsIgnoreCase(request.getEntityName())) {
                        postMerge(request, mergeResult, winnerResult, loserResult, winnerId, url, restClient, connectorInfo.getAuthConfig());
                    } else {
                        return CommonDataService.super.merge(request);
                    }
                } else {
                    throw e;
                }
            }
            return mergeResult;
        } catch (Exception e) {
            ConnectorHelper.handleException(e);
            return null;
        }
    }

    private void postMerge(MergeRequest request, MergeResponse mergeResult, SyncResponse winnerResult, SyncResponse loserResult,
                           String winnerId, String url, SyncariEntityDataRestClient restClient, AuthConfig authConfig) {
        AtomicBoolean isSuccessForWinner = new AtomicBoolean(true);
        Result resultForWinner = new Result(false, null, request.getWinner().getSyncariEntityId());
        request.getLosers().forEach(l -> {
            HttpHeaders headers = restClient.getHeaders(authConfig);
            MergeBody mergeBody = new MergeBody(l.getId(), winnerId);
            log.debug("Merging contacts with URL {} and Merge Body {} ", url, mergeBody);
            final ResponseEntity<String> resp;
            Result resultForCurrentLooser = new Result(false, null, l.getSyncariEntityId());
            try {
                resp = restClient.getTemplate().exchange(url, HttpMethod.POST,
                        new HttpEntity(mergeBody, headers), String.class);

                if (resp.getStatusCode() != HttpStatus.OK) {
                    String msg = format("Failed merge for winner %s and looser %s reason %s", winnerId, l.getId(), resp.getBody());
                    loserResult.getResults().add(resultForCurrentLooser);
                    loserResult.getErrors().add(msg);
                    log.error(msg);
                } else {
                    Map resultMap = rethrow(() -> mapper.readValue(resp.getBody(), Map.class));
                    if (resultMap.containsKey("id")) {
                        resultForWinner.setId(resultMap.get("id").toString());
                    }
                    resultForCurrentLooser.setSuccess(true);
                    loserResult.getResults().add(resultForCurrentLooser);
                    log.debug("Successfully merged winner {} and looser {}", winnerId, l.getId());
                }
                loserResult.getResults().add(resultForCurrentLooser);
            } catch (HttpClientErrorException e) {
                if (e.getResponseBodyAsString().contains("vid to merge is not a valid contact or is already in the process of being merged")) {
                    resultForCurrentLooser.setSuccess(true);
                    loserResult.getResults().add(resultForCurrentLooser);
                    log.debug("Winner {} and loser {} are already merged", winnerId, l.getId());
                } else {
                    isSuccessForWinner.set(false);
                    String errorOfResult = e.getMessage() != null ? e.getMessage() : "Error while merging contacts," + e.getStatusCode();
                    handleExceptionWhileMergingContacts(loserResult, resultForWinner, resultForCurrentLooser, errorOfResult);
                }
            } catch (Exception e) {
                isSuccessForWinner.set(false);
                String errorOfResult = e.getMessage();
                handleExceptionWhileMergingContacts(loserResult, resultForWinner, resultForCurrentLooser, errorOfResult);
            }
        });
        resultForWinner.setSuccess(isSuccessForWinner.get());
        winnerResult.getResults().add(resultForWinner);
        winnerResult.setSuccess(isSuccessForWinner.get());
        mergeResult.setWinnerResult(winnerResult);
        mergeResult.setLoserResult(loserResult);
        if (isSuccessForWinner.get()) {
            // use the new id to upsert and set original request winner id back once the call is done, as caller/platform needs the original winner id to remove id mapping.
            String requestWinnerId = request.getWinner().getId();
            if (!StringUtils.isBlank(resultForWinner.getId())) {
                request.getWinner().setId(resultForWinner.getId());
            }
            upsertWinner(request);
            request.getWinner().setId(requestWinnerId);
        }
    }

    private static void handleExceptionWhileMergingContacts(SyncResponse loserResult,
                                                            Result resultForWinner,
                                                            Result resultForCurrentLooser,
                                                            String errorOfResult) {
        log.error("Failed merge for winner {} and looser {} reason {}", resultForWinner.getId(), resultForCurrentLooser.getId(), errorOfResult);
        resultForWinner.addError(errorOfResult);
        resultForCurrentLooser.addError(errorOfResult);

        loserResult.getResults().add(resultForCurrentLooser);
        loserResult.setSuccess(false);
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        // Predefined list of required scopes
        Set<String> required_scopes = new HashSet<>(REQUIRED_SCOPES);

        // Predefined list of optional scopes
        Set<String> optional_scopes = new HashSet<>(OPTIONAL_SCOPES);

        // Additional scopes to be obtained from the config
        List<String> additional_scopes = connector.getRequiredScopes();

        // if additional scopes is not empty add it to required_scope and remove all of it from optional_scopes
        if (additional_scopes != null && !CollectionUtils.isEmpty(additional_scopes)){
            required_scopes = new HashSet<>(additional_scopes);
            // Use the optional scope from connector
            optional_scopes = CollectionUtils.isEmpty(connector.getOptionalScopes()) ? new HashSet<>() : new HashSet<>(connector.getOptionalScopes());
        }

        String scope = StringUtils.join(required_scopes, SPACE);
        String optional_scope = StringUtils.join(optional_scopes, SPACE);

        log.debug("Required scopes - {}", scope);
        log.debug("Optional scopes - {}", optional_scope);
        return "/oauth/authorize?client_id={{client_id}}" +
                // Required scopes
                (StringUtils.isBlank(scope) ? "" : "&scope=" + scope) +
                // Optional scopes
                (StringUtils.isBlank(optional_scope) ? "" : "&optional_scope=" + optional_scope) +
                "&redirect_uri={{redirect_uri}}&state={{state}}";
    }
    
    @Override
    public String getAuthHost(AuthConfig config) {
        return OAUTH_HOST;
    }

    @Override
    public Supplier<AuthConfig> getTokenHandler(ConnectorInfo connector){
        return () -> refreshToken(connector);
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.REFRESH_TOKEN,
                DefaultAuthTokenHandler.REFRESH_TOKEN, config.getRefreshToken(),
                DefaultAuthTokenHandler.CLIENT_ID, config.getClientId(), 
                DefaultAuthTokenHandler.CLIENT_SECRET, config.getClientSecret());

        return tokenHandler.refreshToken(config, OAUTH_URL, map);
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, "authorization_code", 
                DefaultAuthTokenHandler.CODE, oAuthRequest.getCode(), 
                DefaultAuthTokenHandler.CLIENT_ID, oAuthRequest.getConfig().getClientId(),
                DefaultAuthTokenHandler.CLIENT_SECRET, oAuthRequest.getConfig().getClientSecret(), 
                DefaultAuthTokenHandler.REDIRECT_URI, oAuthRequest.getRedirectUri());

        return tokenHandler.getAccessToken(OAUTH_URL, map);
    }
    
    @Override
    public SyncResponse update(SyncRequest request) {
        String url = null;
        SyncResponse response = new SyncResponse();

        if (!updateUrlMap.containsKey(request.getEntityName()) && !request.getEntitySchema().isCustom()) {
            String msg = String.format("Hubspot Update operation not supported for entity %s", request.getEntityName());
            log.warn(msg);
            response.appendError(msg);
            return response;
        }

        HubspotRestClient restClient = new HubspotRestClient(
                getSingleJsonConfig(request.getEntityName()),mapper);
        List<EntityData> toBeCreated = request.getData().get(request.getConnector().getId());
        Map<String, AttributeSchema> attribMap = request.getEntitySchema().getAttributes().stream().collect(
                Collectors.toMap(AttributeSchema::getApiName, x -> x));
        Map<String, List<Pair<String, Object>>> associationMap = new HashMap<>();
        List<Map<String,Object>> batchPayload = new ArrayList<>();
        Map<String, String> externalIdToSyncariIdMap = new HashMap<>();
        for (EntityData data : toBeCreated) {
            String path = request.getEntitySchema().isCustom() ? 
                String.format(updateUrlMap.get("custom"), request.getEntityName(), data.getId()) :
                String.format(updateUrlMap.get(request.getEntityName()), data.getId());

            url = getUrl(request.getEntityName(), request.getConnector().getAuthConfig(), path);

            Map<String, Object> payload =new HashMap<>();
            if ("engagement".equalsIgnoreCase(request.getEntityName())) {
                if(data.getValue("hs_engagement_type") != null && data.getValueAsString("hs_engagement_type").equalsIgnoreCase("CALL")) {
                    setCallDispositionId(request, data);
                }
                data.getValues().forEach((k, v) -> {
                    Object value = getTransformedValue(attribMap, k, v);
                    if(isSupportedAssociation(request, k)) {
                        if (Objects.isNull(value)) return;
                        List<Pair<String, Object>> associations = associationMap.getOrDefault(k, new ArrayList<>());
                        associations.add(Pair.of(data.getId(), value));
                        associationMap.put(k, associations);
                        return;
                    }
                });
                payload = HubspotRestClient.unFlattenEngagementObject(data.getValueAsString("hs_engagement_type"), data.getValues());
            }  else if ("ticket".equalsIgnoreCase(request.getEntityName())) {
                Map<String,Object> properties = new HashMap<>();
                data.getValues().forEach((k, v) -> {
                    Object value = getTransformedValue(attribMap, k, v);
                    properties.put(k, value);
                });
                payload.put("properties", properties);
            } else if("line_item".equalsIgnoreCase(request.getEntityName())){
                Map<String,Object> properties = new HashMap<>();
                if(data.getValues().containsKey("recurringbillingfrequency") && data.getValues().get("recurringbillingfrequency") != null){
                    data.getValues().put("recurringbillingfrequency",HubspotSeed.getRecurringBillingApiField((String)data.getValues().get("recurringbillingfrequency")));
                }
                data.getValues().forEach((k, v) -> {
                    Object value = getTransformedValue(attribMap, k, v);
                    properties.put(k, value);
                });
                payload.put("properties", properties);
            } else if("invoice".equalsIgnoreCase(request.getEntityName())){
                Map<String,Object> properties = new HashMap<>();
                data.getValues().forEach((k, v) -> {
                    Object value = getTransformedValue(attribMap, k, v);
                    properties.put(k, value);
                });
                payload.put("properties", properties);
            } else if("subscription".equalsIgnoreCase(request.getEntityName())){
                Map<String,Object> properties = new HashMap<>();
                data.getValues().forEach((k, v) -> {
                    Object value = getTransformedValue(attribMap, k, v);
                    properties.put(k, value);
                });
                payload.put("properties", properties);
            } else if("quote".equalsIgnoreCase(request.getEntityName())){
                Map<String,Object> properties = new HashMap<>();
                data.getValues().forEach((k, v) -> {
                    Object value = getTransformedValue(attribMap, k, v);
                    properties.put(k, value);
                });
                payload.put("properties", properties);
            } else if("product".equalsIgnoreCase(request.getEntityName()) ||
                    "lead".equalsIgnoreCase(request.getEntityName())){
                Map<String,Object> properties = new HashMap<>();
                data.getValues().forEach((k, v) -> {
                    Object value = getTransformedValue(attribMap, k, v);
                    properties.put(k, value);
                });
                payload.put("properties", properties);
            } else {
                List<Map<String, Object>> properties = new ArrayList<>();
                data.removeSystemFields();
                data.getValues().forEach((k, v) -> {
                    Object value = getTransformedValue(attribMap, k, v);
                    if(isSupportedAssociation(request, k)) {
                        if (Objects.isNull(value)) return;

                        List<Pair<String, Object>> associations = associationMap.getOrDefault(k, new ArrayList<>());
                        associations.add(Pair.of(data.getId(),value));
                        associationMap.put(k, associations);
                        return;
                    }
                    String propKey = request.getEntitySchema().isCustom() ? "property" : updatePropertyNameMap.get(request.getEntityName());
                    properties.add(FluentMap.of("value",value).add(propKey,k));
                });
                if(properties.isEmpty() && associationMap.isEmpty()) {
                    response.getResults().add(new Result(true, data.getId(), data.getSyncariEntityId()));
                    continue;
                }
                log.debug("Updating hubspot at {} with {}", url, properties);
                payload.put("properties", properties);
            }

            if(isBatchModeRequest(request, UPDATE)) {
                payload.put("id", data.getId());
            	batchPayload.add(payload);
                externalIdToSyncariIdMap.put(data.getId(), data.getSyncariEntityId());
            	continue;
            }

            Result result = new Result(true, data.getId(), data.getSyncariEntityId());

            EntityData d = null;
            if ("engagement".equalsIgnoreCase(request.getEntityName())) {
                try {
                    d = restClient.postEngagement(url, payload, HttpMethod.PATCH, request.getConnector(), getTokenHandler(request.getConnector()));
                } catch(NonRetriableException e){
                    log.error(e.getMessage(), e);
                    result.setSuccess(false).addError(e.getStatusCode()+e.getMessage());
                }
            } else if (Constants.TICKET.toLowerCase().equalsIgnoreCase(request.getEntityName()) || request.getEntitySchema().isCustom()) {
                try {
                    d = restClient.postEntityObject(request.getEntitySchema(), url, payload, HttpMethod.PATCH, request.getConnector(), getTokenHandler(request.getConnector()));
                } catch(NonRetriableException e){
                    log.error(e.getMessage(), e);
                    result.setSuccess(false).addError(e.getStatusCode()+e.getMessage());
                }
            } else if ("POST".equalsIgnoreCase(httpUpdateMethodMap.get(request.getEntityName()))) {
                try {
                    d = restClient.post(url, payload, request.getConnector(), getTokenHandler(request.getConnector()));
                }catch(NonRetriableException e){
                    log.error(e.getMessage(), e);
                    result.setSuccess(false).addError(e.getStatusCode()+e.getMessage());
                }
            } else if ("PATCH".equalsIgnoreCase(httpUpdateMethodMap.get(request.getEntityName()))) {
                try {
                    d = restClient.patch(url, payload, request.getConnector(), getTokenHandler(request.getConnector()));
                }catch(NonRetriableException e){
                    log.error(e.getMessage(), e);
                    result.setSuccess(false).addError(e.getStatusCode()+e.getMessage());
                }
            }else {

                try {
                    restClient.put(url, payload, request.getConnector(), getTokenHandler(request.getConnector()));
                } catch (ConnectorException e) {
                    if ("404 NOT_FOUND".equalsIgnoreCase(e.getStatusCode()) && ErrorCodes.BAD_ENDPOINT.name().equalsIgnoreCase(e.getErrorCode())) {
                        //ignore the object that is not found in hubspot
                        log.warn(e.getMessage(), e);
                        result.setSuccess(false).addError(e.getStatusCode()+e.getMessage());
                    } else {
                        log.error(e.getMessage(), e);
                        result.setSuccess(false).addError(e.getStatusCode() + e.getMessage());
                    }
                }
            }

            if (!result.isSuccess()) {
                log.error("Update failed for entity:{},result:{}", result.getId(), request.getEntityName(), result);
                if(!result.getErrors().isEmpty() && result.getErrors().get(0).contains("404 NOT_FOUND")) {
                    result.setErrorCode(ErrorCodes.DATA_NOT_FOUND.name());
                }
            }
            response.getResults().add(result);
        }

        if (isBatchModeRequest(request, UPDATE)) {
            doBatchOperation(request, response, restClient, "update", batchPayload, request.getSyncariIds(), request.getIds(), externalIdToSyncariIdMap);
        }

        if (!associationMap.isEmpty()) {

            Map<String, Result> resultsByExternalId = response.getResults().stream().collect(Collectors.toMap(Result::getId, r -> r));
            List<Association> associations = new ArrayList<>();
            List<Association> deleteAssociations = new ArrayList<>();

            associationMap.forEach((k, v) -> {
                Stream<Pair<String, Object>> currentAssociations = v.stream().filter(a -> resultsByExternalId.get(a.x).isSuccess());

                if ("associatedVids".equalsIgnoreCase(k) && request.getEntityName().toLowerCase().equalsIgnoreCase(DEAL)) {
                    handleContactAssociations(request, currentAssociations, associations, deleteAssociations);

                    if (!deleteAssociations.isEmpty()) {
                        deleteAssociations(request.getEntityName().toLowerCase(), "contact", deleteAssociations, request.getConnector().getAuthConfig());
                        log.debug("Deleted {} associations", deleteAssociations.size());
                    }
                } else if (request.getEntityName().toLowerCase().equalsIgnoreCase(ENGAGEMENT) && "associatedcompanyid".equalsIgnoreCase(k)) {
                    int compAssocId = ENGAGEMENT_TO_COMPANY_DEF_ID;
                    List<Association> companyAssociations = currentAssociations.flatMap(assoc -> {
                        List<String> companyIds = List.class.cast(assoc.y);
                        return companyIds.stream().filter(i -> i != null).map(c -> new Association(Long.valueOf(assoc.x), Long.valueOf(c), compAssocId));
                    }).collect(Collectors.toList());
                    associations.addAll(companyAssociations);
                } else if ("associatedcompanyid".equalsIgnoreCase(k)) {
                    int compAssocId = DEAL_TO_COMPANY_DEF_ID;
                    associations.addAll(currentAssociations.map(a -> new Association(Long.valueOf(a.x), Long.valueOf(a.y.toString()), compAssocId)).collect(Collectors.toList()));
                }
            });

            if (!associations.isEmpty()) {
                String assocUrl = getUrl(request.getEntityName(), request.getConnector().getAuthConfig(),
                        createUrlMap.get("associations"));
                var partitioned = Lists.partition(associations, 100);
                partitioned.forEach(partition -> {
                    List<Result> results = partition.stream()
                            .map(p -> resultsByExternalId.get(String.valueOf(p.getFromObjectId())))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    try {
                        String valueAsString = mapper.writeValueAsString(partition);
                        final ResponseEntity<String> putResults = restClient.put(assocUrl, valueAsString, request.getConnector(), getTokenHandler(request.getConnector()));
                        log.debug("Put Results for URL:{}, Status Code:{}, Body:{}", assocUrl, putResults.getStatusCode(), putResults.getBody());
                    } catch (ConnectorException e) {
                        log.error(e.getMessage(), e);
                        results.forEach(result -> result.setSuccess(false).addError(e.getStatusCode() + e.getMessage()));
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                        results.forEach(result -> result.setSuccess(false).addError(e.getMessage()));
                    }
                });
                log.debug("Created {} new associations", associations.size());
            }
        }
        return response;
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            if(config.getAuthType() != AuthType.ApiKey) {
                // Force refresh token when testing connection
                forceRefreshToken(config);
            }
            response.setAuthConfig(config.getAuthConfig());
            DescribeAllRequest request = new DescribeAllRequest(config,
                    objPluralMap.keySet().stream().collect(Collectors.toList()));
           describeAll(request);
            log.info(format("Successfully authenticated hubspot connection for %s", config.getName()));
        } catch (Exception e) {
            try {
                if (e.getMessage() == null) {
                    throw new Exception();
                }

                try {
                    JsonNode node = mapper.readTree(e.getMessage());
                    handleAuthenticationErrorMessage(response, new Exception(node.get("message").asText()));
                } catch (JacksonException je) {
                    handleAuthenticationErrorMessage(response, e);
                }
            } catch (Exception e2) {
                log.error(ExceptionUtils.getStackTrace(e2));
                response.setMessage("Unknown Error");
                response.setCode(HttpStatus.UNAUTHORIZED.name());
            }
        }
        return response;
    }

    private boolean checkForInvoiceAndSubscriptionDiff(List<String> standardObjects, Map<String, String> objPluralMap) {
        Set<String> obs = new HashSet<>(objPluralMap.keySet());
        obs.removeAll(standardObjects);
        return obs.equals(Set.of("invoice", "subscription"));
    }

    private void forceRefreshToken(ConnectorInfo config) {
        AuthConfig authConfig = refreshToken(config);
        config.getAuthConfig().setAccessToken(authConfig.getAccessToken());
        config.getAuthConfig().setRefreshToken(authConfig.getRefreshToken());
        config.getAuthConfig().setExpiresIn(authConfig.getExpiresIn());
    }

    @Override
    public List<EventData> parseEventData(WebhookRequest request) {
    	validateCaller(request);
    	List<EventData> response = new ArrayList<>();
    	try {
			List entities = mapper.readValue(request.getBody(), List.class);
			for (Object object : entities) {
				Map entity = (Map)object;
				String[] parts = entity.get("subscriptionType").toString().split(DOT);
				String eventType = parts[1]; // "creation", "deletion", "associationChange"

				// Handle association change events
				if ("associationChange".equalsIgnoreCase(eventType)) {
					EventData associationEvent = parseAssociationChangeEvent(entity, parts[0], request.getConfig().getId());
					if (associationEvent != null) {
						response.add(associationEvent);
					}
				} else {
					// Handle regular entity events (creation, deletion)
					EntityData ed = new EntityData(parts[0]);
					// For now we capture only deletes, so just the ID is sufficient
					ed.setId(entity.get("objectId").toString());
					ed.setConnectorId(request.getConfig().getId());
					Operation operation = getOp(eventType);
					if(operation == Operation.delete) {
						ed.setDeleted(true);
					}

					Long updatedAt = (Long)entity.get("occurredAt");
					if (null != updatedAt){
						ed.setLastModified(updatedAt);
					}else{
						ed.setLastModified(ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).toInstant().toEpochMilli());
					}

					response.add(new EventData().setData(ed).setOperation(operation));
				}
			}
		} catch (JsonProcessingException e) {
			log.error(ExceptionUtils.getStackTrace(e));
			throw new RuntimeException("Invalid request. The eventdata json is invalid");
		}
    	log.debug("Parsed {} records for hubspot", response.size());
    	return response;
	}

	private EventData parseAssociationChangeEvent(Map webhookEvent, String fromObjectType, String connectorId) {
		try {
			// Log raw webhook payload for debugging
			log.debug("Processing association change webhook. Payload keys: {}", webhookEvent.keySet());

			// Extract association details from webhook
			// HubSpot sends: fromObjectId, toObjectId, associationType (String), associationRemoved (boolean)
			String fromObjectId = String.valueOf(webhookEvent.get("fromObjectId"));
			String toObjectId = String.valueOf(webhookEvent.get("toObjectId"));
			Boolean associationRemoved = (Boolean) webhookEvent.get("associationRemoved");
			String associationTypeString = (String) webhookEvent.get("associationType"); // e.g., "CONTACT_TO_COMPANY"

			// Only process association removals - creations are handled by normal sync
			if (!Boolean.TRUE.equals(associationRemoved)) {
				log.debug("Ignoring association creation webhook (sync will handle it): {} {} -> {}",
					fromObjectType, fromObjectId, toObjectId);
				return null;
			}

			// Parse associationType string to extract toObjectType
			// Format: "FROM_TO_TO" e.g., "CONTACT_TO_COMPANY" means contact -> company
			String toObjectType = null;
			if (associationTypeString != null && associationTypeString.contains("_TO_")) {
				String[] parts = associationTypeString.split("_TO_");
				if (parts.length == 2) {
					toObjectType = parts[1].toLowerCase(); // "COMPANY" -> "company"
				}
			}

			if (toObjectType == null) {
				log.warn("Could not parse toObjectType from associationType: {}", associationTypeString);
				return null;
			}

			// Create association entity name (e.g., "deal_association")
			String associationEntityName = fromObjectType + ASSOCIATION_SUFFIX;

			// LIMITATION: HubSpot webhook doesn't provide the exact typeId needed for the association ID
			// The actual association ID format is: {fromId}-{toId}-{toType}-{category}-{typeId}
			// But webhook only gives us: fromId, toId, and associationType string
			//
			// WORKAROUND: Create EntityData with available fields. The Core layer EventData processor
			// should query existing associations matching fromObjectId + toObjectId + toObjectType
			// and mark ALL matching associations as deleted (there may be multiple typeIds)

			// Create a placeholder ID for logging (this won't match the real association ID)
			String placeholderId = String.format("%s-%s-%s-UNKNOWN-UNKNOWN", fromObjectId, toObjectId, toObjectType);

			// Create EntityData for webhook deletion event
			// Note: The ID won't match existing associations, so Core layer must handle field-based matching
			EntityData ed = new EntityData(associationEntityName);
			ed.setId(placeholderId);
			ed.setConnectorId(connectorId);
			ed.addValue("fromObjectType", fromObjectType);
			ed.addValue("toObjectType", toObjectType);
			ed.addValue("fromObjectId", fromObjectId);
			ed.addValue("toObjectId", toObjectId);
			ed.addValue("label", associationTypeString);

			// Set last modified time
			Long occurredAt = webhookEvent.get("occurredAt") != null ?
				Long.parseLong(String.valueOf(webhookEvent.get("occurredAt"))) :
				ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).toInstant().toEpochMilli();
			ed.setLastModified(occurredAt);

			// Mark association as deleted
			ed.setDeleted(true);
			Operation operation = Operation.delete;

			log.debug("Successfully created association deletion event: entity={}, operation={}, placeholderId={}",
				associationEntityName, operation, placeholderId);
			return new EventData().setData(ed).setOperation(operation);

		} catch (Exception e) {
			log.error("Error parsing association change event. Webhook payload: {}. Error: {}",
				webhookEvent, ExceptionUtils.getStackTrace(e));
			return null;
		}
	}

	public Map addToList(ConnectorInfo info, int listId, List<Integer> contactIds, List<String> emails) {
		Map result = new HashMap();
		HubspotRestClient restClient = new HubspotRestClient(getSingleJsonConfig(""), mapper);
		Map<String, Object> payload = new HashMap();
		String url = String.format(getUrl("contact", info.getAuthConfig(), ADD_TO_LIST), listId);
		log.debug("Starting hubspot addToList for contactIds {} emails {}", contactIds.size(), emails.size());

		if (!contactIds.isEmpty()) {
			List<List<Integer>> partitions = Lists.partition(contactIds, 500);
			for (List<Integer> partition : partitions) {
				payload.put("vids", partition);
				ResponseEntity<String> responseEntity = restClient.postRaw(url,
						rethrow(() -> mapper.writeValueAsString(payload)), info, getTokenHandler(info));
				result.putAll(rethrow(() -> mapper.readValue(responseEntity.getBody(), Map.class)));
			}
		}

		if (!emails.isEmpty()) {
			List<List<String>> partitions = Lists.partition(emails, 500);
			for (List<String> partition : partitions) {
				payload.put("emails", partition);
				ResponseEntity<String> responseEntity = restClient.postRaw(url,
						rethrow(() -> mapper.writeValueAsString(payload)),info, getTokenHandler(info));
				result.putAll(rethrow(() -> mapper.readValue(responseEntity.getBody(), Map.class)));
			}
		}
		log.debug("Completed hubspot addToList result {}", result.size());
		return result;
	}

    private void validateCaller(WebhookRequest request) {
    	String hash = Hex.encodeHexString(TextUtil.getSha(request.getConfig().getAuthConfig().getClientSecret().concat(request.getBody())));
    	if(!request.getHeaders().containsKey(X_HUBSPOT_SIGNATURE) || !request.getHeaders().get(X_HUBSPOT_SIGNATURE).toString().equalsIgnoreCase(hash)) {
    		throw new RuntimeException("Invalid request. The signatures do not match.");
    	}
	}

	private Operation getOp(String op) {
    	switch (op) {
		case "creation":
			return Operation.create;
		case "deletion":
			return Operation.delete;
		default:
			throw new RuntimeException(String.format("Operation %s not supported", op));
		}
    }

    private JsonParserConfig getSingleJsonConfig(String entity) {
        String idPath = ("contact".equalsIgnoreCase(entity) ? "vid" : entity + "Id");
        return new JsonParserConfig("properties", "properties", idPath, idPath, true, "properties.__key__.value");
    }

    private String getUrl(String entityName, AuthConfig auth, String path) {
        if (path == null)
            throw new RuntimeException("Path empty for " + entityName);
        String url = API_HOST + path;
        return url;
    }

    protected Object getTransformedValue(Map<String, AttributeSchema> attribMap, String k, Object v) {
        Object result = v;
        if(attribMap.containsKey(k)) {
            String dataType = attribMap.get(k).getDataType();
            //flatten multivalued picklists
            if(!attribMap.get(k).isReference() && attribMap.get(k).isMultiValueField()){
                if(result!=null && List.class.isAssignableFrom(result.getClass())){
                    List values = (List) (List.class.cast(result)).stream()
                            .filter(value -> value != null).map(val -> val.toString()).collect(Collectors.toList());
                    result = String.join(";", values);
                }
            }
            // If the incoming datetime is in Java object format, convert to epoch milli
            if(dataType != null) {
                if(isDateTime(v, dataType)) {
                    result = ((ZonedDateTime) v).toInstant().toEpochMilli();
                }else if(isTimestamp(v, dataType)){
                    result = ((Instant)v).toEpochMilli();
                }else if (isDate(v, dataType)) {
                    Date dateValue = ((Date) v);
                    result = LocalDate.ofInstant(dateValue.toInstant(), ZoneOffset.UTC.normalized())
                            .atStartOfDay(ZoneOffset.UTC.normalized())
                            .toInstant().toEpochMilli();
                    //Workaround because whole number types cannot be discovered by Hubspot property API
                } else if (isNumeric(v, dataType) && canCastToWholeNumber(Number.class.cast(v))) {
                    result = Number.class.cast(v).longValue();
                }
            }
        }
        return result;
    }

    private boolean canCastToWholeNumber(Number value) {
        return value.longValue() == value.doubleValue();
    }

    private boolean isDate(Object v, String dataType) {
        return "date".equalsIgnoreCase(dataType) && v instanceof Date;
    }

    private boolean isNumeric(Object v, String dataType) {
        return ("number".equalsIgnoreCase(dataType) || "double".equalsIgnoreCase(dataType)) && (v instanceof Number);
    }

    private boolean isTimestamp(Object v, String dataType) {
        return "timestamp".equalsIgnoreCase(dataType) && v instanceof Instant;
    }

    private boolean isDateTime(Object v, String dataType) {
        return "datetime".equalsIgnoreCase(dataType) && v instanceof ZonedDateTime;
    }

    private String toHubspotDatatype(String inputDatatype) {
        // string, number, bool, datetime, enumeration, date, phone_number,
        // currency_number
        switch (inputDatatype.toLowerCase()) {
            case "picklist":
            case "list":
            case ENUMERATION:
                return ENUMERATION;
            case "double":
            case "id":
            case "integer":
                return "number";
            case "boolean":
                return "bool";
            case "date":
            case "datetime":
            case "timestamp":
                return "datetime";
            default:
                return "string";
        }
    }

    private String toSyncariDatatype(String inputDatatype, Map fields) {
        // https://knowledge.hubspot.com/contacts/property-field-types-in-hubspot
        // https://developers.hubspot.com/docs/methods/crm-extensions/property-types
        String apiName = this.getValue(fields, "name");
        //Hubspot does not differentiate b/w whole numbers and decimals. Everything is a "number"
        if(FIEDS_FOR_INTEGER_DATATYPE.contains(apiName)){
            return "integer";
        }
        String fieldType = fields == null ? "" : this.getValue(fields, "fieldType");
        String referencedObjectType = fields == null ? "" : this.getValue(fields, "referencedObjectType");
        if(!StringUtils.isBlank(referencedObjectType)) {
            return "reference";
        }
        switch (inputDatatype.toLowerCase()) {
            case "text":
                return "string";
            case ENUMERATION:
                if(!StringUtils.isBlank(fieldType) && "booleancheckbox".equalsIgnoreCase(fieldType)) {
                    return "boolean";
                }
                return ENUMERATION;
            case "bool":
                return "boolean";
            case "datetime":
                return "datetime";
            case "date":
                return "date";
            default:
                return inputDatatype.toLowerCase();
        }
    }

    private AttributeSchema transform(AttributeSchema schema) {
        schema.setApiName(schema.getApiName().toLowerCase());
        return schema;
    }

    private String formattedName(String name) {
        return name.toLowerCase();
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in hubspot yet");
    }

    private FetchResponse getDataByWatermark(SyncRequest request, boolean applyUpperBoundWM) {
        WatermarkInfo watermark = request.getWatermark();

        HubspotIncrementalIterator iterator = new HubspotIncrementalIterator(watermark, watermark.getOffset(),request.getWatermark().getLimit(),request.getPageSize(),request.getConnector(),
                request.getEntitySchema(), applyUpperBoundWM, getTokenHandler(request.getConnector()));
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private FetchResponse getEngagementsByWatermarkIterator(SyncRequest request) {
        HubspotRestClient restClient = new HubspotRestClient(getSingleJsonConfig(""), mapper);
        Function3<WatermarkInfo, Integer, Long, EntityPage> generator = (wm, pageSize, offset) -> {
            String url = null;
            pageSize = pageSize == 0 ? ENGAGEMENT_PAGE_SIZE : Math.min(pageSize, ENGAGEMENT_PAGE_SIZE);
            // The recent engagement API supports navigating records for just recent 30 days. So if its a resync,
            // we would want to fallback to "get all API" with offset based iteration between sync cycles.
            if (request.getWatermark().isResync() || request.getWatermark().isInitial() || request.getWatermark().isTest()) {
                int count = request.getPageSize() <=0 ? pageSize : request.getPageSize();
                url = String.format(API_HOST + initialGetAPIMap.get(request.getEntityName()), offset, count);
            } else {
                offset = resetIncrementalWatermarkAndOffsetIfNeeded(wm, offset, pageSize);
                url = String.format(API_HOST + incrementalGetAPIMap.get(request.getEntityName()), wm.getEnd(), pageSize, offset);
            }

            List<EntityData> results = new ArrayList<>();
            log.debug("Fetching engagements for watermark {}", request.getWatermark().toString());
            ResponseEntity<String> responseEntity = restClient.getResponse(
                url, request.getConnector(), getTokenHandler(request.getConnector()));
            Map<String, Object> resp = rethrow(() -> mapper.readValue(responseEntity.getBody(), Map.class));
            String pageOffset = "0";
            boolean hasMore = false;
            if (resp.containsKey("results")) {
                List engagements = mapper.convertValue(resp.get("results"), new TypeReference<List<Map<String, Object>>>(){});
                for (int i = 0; i < engagements.size(); i++) {
                    Map o = (Map) ((Map) engagements.get(i)).get("engagement");
                    String engagementType = o.get("type").toString();
                    var data = new EntityData();
                    data.setId(o.get("id").toString());
                    data.setName(request.getEntityName());
                    data.setConnectorId(request.getConnector().getId());
                    data.setLastModified(Long.parseLong(o.get("lastUpdated").toString()));
                    data.setCreatedAt(Long.parseLong(o.get("createdAt").toString()));
                    data.setDeleted(Boolean.parseBoolean(o.getOrDefault("gdprDeleted", "false").toString()));
                    data.setValues(HubspotRestClient.flattenEngagementResults(engagementType, (Map) engagements.get(i)));
                    if(data.getValue("hs_engagement_type") != null && data.getValueAsString("hs_engagement_type").equalsIgnoreCase("CALL")) {
                        setCallDispositionLabel(request, data);
                    }
                    results.add(data);
                }
                hasMore = mapper.convertValue(resp.get("hasMore"), Boolean.class);
                if (hasMore) {
                    pageOffset = mapper.convertValue(resp.get("offset"), String.class);
                } else {
                    pageOffset = "0";
                }
            }
            Set<String> ids = results.stream().map(EntityData::getId).collect(Collectors.toSet());
            log.debug("Ids fetched - {}", ids);
            return new EntityPage().setData(results).setHasMore(hasMore).setOffset(Long.valueOf(pageOffset));
        };

        HubspotIterator iterator = new HubspotIterator(request.getWatermark(), request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), request.getWatermark().getLimit(), ENGAGEMENT_PAGE_SIZE);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private long resetIncrementalWatermarkAndOffsetIfNeeded(WatermarkInfo watermark, long offset, int pageSizeToCheck) {
        //Hubspot recent cursor can only return 10k records at most
        if (offset + pageSizeToCheck >= MAX_OFFSET) {
            offset = 0;
            // The framework automatically sets the previous page's last watermark as its upperbound.
            // Here we simply use that as the start and reset the offset to 0, we also fake the end to be current,
            // but framework will again consider the last record's watermark in the batch as end.
            long prevUpperboundWM = watermark.getEnd();
            watermark.setStart(prevUpperboundWM);
            //watermark.setEnd(Instant.now().toEpochMilli());
            log.info("Incremental pull offset was reset to 0 because the offset went over 10k. The new watermark {}", watermark);
        }
        return offset;
    }

    private Set<AttributeSchema> getMultivaluedFieldNames(SyncRequest request) {
        return request.getEntitySchema().getAttributes().stream().filter(a->a.isMultiValueField()).collect(Collectors.toSet());
    }

    private void setMultivaluedFields(Set<AttributeSchema> multivaluedFields, EntityData r) {
        multivaluedFields.forEach(multivaluedField ->{
            if(!multivaluedField.isReference()) {
                //multivalued picllists are semicolon separated
                List<String> values = StringUtils.isBlank(r.getValueAsString(multivaluedField.getApiName())) ? List.of() :
                        Arrays.asList(r.getValueAsString(multivaluedField.getApiName()).split(";"));
                r.addValue(multivaluedField.getApiName(), values);
            }
        });
    }

    private FetchResponse getOwnersByWatermark(SyncRequest request) {
        WatermarkInfo watermark = request.getWatermark();
        HubspotRestClient restClient = new HubspotRestClient();
        String url = getUrl("", request.getConnector().getAuthConfig(), initialGetAPIMap.get(Constants.OWNER));

        Function3<WatermarkInfo, Integer, Long, EntityPage> generator = (wm, pageSize, offset) -> {
            List<EntityData> results = new ArrayList<>();
            boolean hasMore = true;
            String cursor = "";
            log.debug("Fetching owners for watermark {}", request.getWatermark().toString());
            while(hasMore) {
                String after = StringUtils.isNotBlank(cursor) ? "?after=" + cursor : "";
                ResponseEntity<String> responseEntity = restClient.getResponse(url + after, request.getConnector(), getTokenHandler(request.getConnector()));
                Map<String, Object> response = rethrow(() -> mapper.readValue(responseEntity.getBody(), Map.class));
                Map<String, Object> paging = response.containsKey("paging") ? (Map<String, Object>) response.get("paging") : Map.of();
                Map<String, String> next = paging.containsKey("next") ? (Map<String, String>) paging.get("next") : Map.of();
                hasMore = next.containsKey("after");
                cursor = hasMore ? next.get("after") : "";

                List owners = response.containsKey("results") ? (List) response.get("results") : List.of();
                for (int i = 0; i < owners.size(); i++) {
                    Map o = (Map) owners.get(i);
                    var data = new EntityData();
                    data.setId(o.get("id").toString());
                    data.setName(request.getEntityName());
                    data.setConnectorId(request.getConnector().getId());
                    data.setLastModified(HubspotIncrementalIterator.toTimestamp((o.get("updatedAt").toString())));
                    data.setCreatedAt(HubspotIncrementalIterator.toTimestamp((o.get("createdAt").toString())));
                    data.setValues(o);
                    data.addValue("ownerId", data.getId());
                    boolean isActive = o.get("archived") == null ? true : !(boolean) o.get("archived");
                    data.addValue("isActive",  isActive);
                    data.setDeleted(!isActive);
                    data.addValue("activeUserId", o.get("userId"));
                    results.add(data);
                }
            }
            return new EntityPage().setData(results).setHasMore(false).setOffset(0);
        };

        HubspotIterator iterator = new HubspotIterator(watermark, watermark.getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(),request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    @Deprecated
    //This is using legacy API. We use events/v3/events for pulling activities
    private FetchResponse getEventsByWatermark(SyncRequest request) {
        WatermarkInfo watermark = request.getWatermark();
        HubspotRestClient restClient = new HubspotRestClient();
        String url = appendIncludeDeletes(
            getUrl("", request.getConnector().getAuthConfig(), initialGetAPIMap.get(Constants.EVENT)));

        long updatedAt = watermark.getEnd();
        Function3<WatermarkInfo, Integer, Long, EntityPage> generator = (wm, pageSize, offset) -> {
            List<EntityData> results = new ArrayList<>();
            log.debug("Fetching events for watermark {}", request.getWatermark().toString());
            ResponseEntity<String> responseEntity = restClient.getResponse(url, request.getConnector(), getTokenHandler(request.getConnector()));
            List events = rethrow(() -> mapper.readValue(responseEntity.getBody(), List.class));

            for (int i = 0; i < events.size(); i++) {
                Map o = (Map) events.get(i);
                var data = new EntityData();
                data.setId(o.get("id").toString());
                data.setName(request.getEntityName());
                data.setConnectorId(request.getConnector().getId());
                data.setLastModified(updatedAt);
                data.setCreatedAt(Long.parseLong(o.get("createdAt").toString()));
                data.setDeleted("DELETED".equalsIgnoreCase(o.get("status").toString()));
                data.setValues(o);

                results.add(data);
            }
            return new EntityPage().setData(results).setHasMore(false).setOffset(0);
        };

        HubspotIterator iterator = new HubspotIterator(watermark, watermark.getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(),request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private FetchResponse getActivitiesByWatermark(SyncRequest request) {
        EntitySchema schema = describe(new DescribeRequest(request.getConnector(), Constants.CONTACT.toLowerCase())).get();
        SyncRequest contactsRequest = new SyncRequest().Builder(request.getConnector(), schema);
        request.getWatermark().setEnd(Instant.now().toEpochMilli());
        long contactsWMStart = (request.getWatermark().getOffset() > 0) ? request.getWatermark().getOffset() : request.getWatermark().getStart();
        WatermarkInfo contactsWM = new WatermarkInfo().setStart(contactsWMStart).setEnd(request.getWatermark().getEnd());
        // The limit should only apply on the activities we lookup. see comments above.
        contactsWM.setLimit(0);
        contactsRequest.setWatermark(contactsWM);
        FetchResponse recentContacts = getByWatermark(contactsRequest, true);
        HubspotActivitySyncContactsIterator activitySyncContactsIterator =
            new HubspotActivitySyncContactsIterator("", recentContacts.getIterator(), request.getWatermark().getLimit(),
                getMaxContactsLimit(), getMaxActivitiesLimit());

        HubspotRestClient restClient = new HubspotRestClient(getSingleJsonConfig(""), mapper);
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {

            if (!activitySyncContactsIterator.hasNext() || activitySyncContactsIterator.hasReachedLimit())
                return Pair.of(0L, new ArrayList<EntityData>().stream());

            String pageOffset = "";
            List<EntityData> results = new ArrayList<>();

            while (activitySyncContactsIterator.hasNext() && !activitySyncContactsIterator.hasReachedLimit()) {

                EntityData contact = activitySyncContactsIterator.getCurrentContact();
                String offsetPart = "";
                // When we know we drained all of the activities for the contact, we move on to next contact.
                if (contact == null || StringUtils.isEmpty(activitySyncContactsIterator.getActivityPageOffset())) {
                    contact = activitySyncContactsIterator.next();
                } else {
                    offsetPart = "&after=" + activitySyncContactsIterator.getActivityPageOffset();
                }

                // We want to get all activities for the contacts for the initial,resync and test mode.
                long begin = (wm.isResync() || wm.isTest() || wm.isInitial()) ? Instant.EPOCH.toEpochMilli() : wm.getStart();
                String url = String.format(API_HOST +
                    incrementalGetAPIMap.get(request.getEntityName()), pageSize, contact.getId(), begin, wm.getEnd()) + offsetPart;

                log.debug("Fetching activities for watermark {}", request.getWatermark().toString());
                ResponseEntity<String> responseEntity = null;

                responseEntity = restClient.getResponse(
                        url, request.getConnector(), getTokenHandler(request.getConnector()));

                final var tempRespEntity = responseEntity;
                Map<String, Object> resp = new HashMap<>();
                if (tempRespEntity != null) {
                    resp = rethrow(() -> mapper.readValue(tempRespEntity.getBody(), Map.class));
                }

                int skippedActivityCount = 0;
                if (resp.containsKey("results")) {
                    List activities = mapper.convertValue(resp.get("results"), new TypeReference<List<Map<String, Object>>>(){});
                    for (int i = 0; i < activities.size(); i++) {
                        Map act = (Map) ((Map) activities.get(i));
                        // Hubspot events API seem to support a wide variety of event types. We dont not want to pull them all yet.
                        if (!SUPPORTED_ACTIVITIES.contains(act.get("eventType").toString())) {
                            skippedActivityCount++;
                            continue;
                        }
                        var data = new EntityData();
                        data.setId(act.get("id").toString());
                        data.setName(request.getEntityName());
                        data.setConnectorId(request.getConnector().getId());
                        data.setLastModified(contact.getLastModified());
                        data.setCreatedAt(HubspotIncrementalIterator.toTimestamp(act.get("occurredAt").toString()));
                        data.setValues((Map) act.get("properties"));
                        data.addValue("activityType", act.get("eventType"));
                        data.addValue("objectId", act.get("objectId"));
                        data.addValue("objectType", act.get("objectType"));
                        if ("contact".equalsIgnoreCase(act.get("objectType").toString())) {
                            data.addValue("contactId", act.get("objectId"));
                        }
                        // set as top level values.
                        data.addValue("updatedAt", HubspotIncrementalIterator.toTimestamp(act.get("occurredAt").toString()));
                        data.addValue("occurredAt", HubspotIncrementalIterator.toTimestamp(act.get("occurredAt").toString()));
                        results.add(data);
                    }
                    if (skippedActivityCount > 0) {
                        log.debug("Skipped processing unsupported {} activities out of total {} activities", skippedActivityCount, activities.size());
                    }
                    if (resp.containsKey("paging")) {
                        pageOffset = ((Map)((Map) resp.get("paging")).get("next")).get("after").toString();
                    } else {
                        // End of pagination.
                        pageOffset = mapper.convertValue(null, String.class);
                    }
                    activitySyncContactsIterator.incrementActivitiesConsumedBy(activities.size() - skippedActivityCount);
                    activitySyncContactsIterator.setActivityPageOffset(pageOffset);
                }
            }
            Response response = new Response(pageOffset, results);
            return Pair.of(Long.valueOf(response.getRecords().size()), response.getRecords().stream());
        };
        int pageSize = request.getPageSize() == 0 ? DEFAULT_PAGE_SIZE : Math.min(request.getPageSize(), DEFAULT_PAGE_SIZE);

        // For this iterator, we always start with 0 as offset, the previous offset is for contacts iteration
        // and we should clear for each sync cycle.
        HubspotActivitiesIterator iterator = new HubspotActivitiesIterator(request.getWatermark(), 0, generator, new ArrayList<>(),
            request.getEntitySchema().getWatermarkField(), pageSize, request.getWatermark().getLimit(), activitySyncContactsIterator);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private FetchResponse getEmailEventsByWatermark(SyncRequest request) {
        HubspotRestClient restClient = new HubspotRestClient(getSingleJsonConfig(""), mapper);
        int pageSize = request.getPageSize() == 0 ? DEFAULT_PAGE_SIZE : Math.min(request.getPageSize(), DEFAULT_PAGE_SIZE);

        HubspotEmailEventIterator iterator = new HubspotEmailEventIterator(pageSize, request.getConnector(), restClient, getTokenHandler(request.getConnector()));
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private FetchResponse getFormsByWatermark(SyncRequest request) {
        HubspotRestClient restClient = new HubspotRestClient(getSingleJsonConfig(""), mapper);
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            List<EntityData> results = new ArrayList<>();
            String url = String.format(API_HOST + "/forms/v2/forms?offset=%s&limit=%s", offset, pageSize);

            log.debug("Fetching forms for watermark {}", request.getWatermark().toString());
            ResponseEntity<String> responseEntity = restClient.getResponse(
                    url, request.getConnector(), getTokenHandler(request.getConnector()));

            final var tempRespEntity = responseEntity;
            List<Map<String, Object>> forms = new ArrayList<>();
            if (tempRespEntity != null) {
                forms = rethrow(() -> mapper.readValue(tempRespEntity.getBody(), List.class));
            }
            for (int i = 0; i < forms.size(); i++) {
                Map form = (Map) ((Map) forms.get(i));
                results.add(extractForm(form, request.getConnector().getId()));
            }
            Response response = new Response("0", results);
            return Pair.of(Long.valueOf(response.getRecords().size()), response.getRecords().stream());
        };
        int pageSize = request.getPageSize() == 0 ? DEFAULT_PAGE_SIZE : Math.min(request.getPageSize(), DEFAULT_PAGE_SIZE);
        DefaultDataIterator iterator = new DefaultDataIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pageSize, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private EntityData extractForm(Map form, String connectorId) {
        var data = new EntityData();
        data.setId(form.get("guid").toString());
        data.setName(Constants.FORM);
        data.setConnectorId(connectorId);
        data.addValue("name", form.get("name"));
        data.addValue("method", form.get("method"));
        data.addValue("action", form.get("action"));
        data.addValue("formType", form.get("formType"));
        data.setLastModified(Long.parseLong(form.get("updatedAt").toString()));
        data.setCreatedAt(Long.parseLong(form.get("createdAt").toString()));
        data.addValue("updatedAt", data.getLastModified());
        data.addValue("createdAt", data.getCreatedAt());
        return data;
    }

    private FetchResponse getFormSubmissionByWatermark(SyncRequest request) {
        HubspotRestClient restClient = new HubspotRestClient(getSingleJsonConfig(""), mapper);
        SyncRequest formReq = new SyncRequest().setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), true, 0));
        formReq.setEntitySchema(HubspotSeed.getSeedEntitySchema(Constants.FORM));
        formReq.setConnector(request.getConnector());
        // for each form get submission
        List<EntityData> forms = new ArrayList<>();
        FetchResponse formsByWatermark = getFormsByWatermark(formReq);
        while(formsByWatermark.getIterator().hasNext()) {
            forms.addAll(formsByWatermark.getIterator().next());
        }

        int pageSize = request.getPageSize() == 0 ? DEFAULT_PAGE_SIZE : Math.min(request.getPageSize(), DEFAULT_PAGE_SIZE);
        HubspotFormSubmissionIterator iterator = new HubspotFormSubmissionIterator(request.getWatermark(), pageSize, request.getConnector(), restClient, forms, getTokenHandler(request.getConnector()));
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private FetchResponse getAssociationsByWatermark(SyncRequest request) {
        /**
         * Add custom objects to the entity map
         * Custom objects should be added to the set of objects to fetch associations for each standard object
         * "contact", "company", "deal", "ticket" as well as other custom objects should be added for each custom object
         */

        Map<String, Set<String>> associationEntitiesMap = new HashMap<>();
        ASSOCIATION_ENTITIES_MAP.keySet().forEach(key -> {
            Set<String> entities = new HashSet<>(ASSOCIATION_ENTITIES_MAP.get(key));
            associationEntitiesMap.put(key, entities);
        });

        List<EntitySchema> customObjects = describeCustomObjects(request.getConnector());
        List<String> customObjectsApiNames = customObjects.stream().map(obj -> obj.getApiName()).collect(Collectors.toList());
        if(!customObjects.isEmpty()) {
            associationEntitiesMap.keySet().forEach(entity -> associationEntitiesMap.get(entity).addAll(customObjectsApiNames));
//            customObjectsApiNames.forEach(apiName -> {
//                Set<String> apiNameSet = new HashSet<>(customObjectsApiNames);
//                apiNameSet.remove(apiName);
//                apiNameSet.addAll(List.of("contact", "company", "deal", "ticket"));
//                associationEntitiesMap.put(apiName, apiNameSet);
//            });
        }

        /**
         * Set entity response limit to 1000
         * If the response has more, encode the offset per object to watermark changestream
         */

        int entityPageSize = 2000;
        HubspotAssociationChangeStream changeStream = new HubspotAssociationChangeStream();
        if(StringUtils.isNotBlank(request.getWatermark().getChangeStream())) {
            try {
                changeStream = mapper.readValue(request.getWatermark().getChangeStream(), HubspotAssociationChangeStream.class);
            } catch (JsonMappingException e) {
                log.error("Failed to parse change stream for associations: {}", ExceptionUtils.getStackTrace(e));
            } catch (JsonProcessingException e) {
                log.error("Failed to process change stream JSON for associations: {}", ExceptionUtils.getStackTrace(e));
            }
        }

        /**
         * Check if association changestream is present
         * Only move on to entities after we exhaust the associations
         */

        WatermarkInfo originalWatermarkInfo = request.getWatermark();
        List<EntityData> associationsList = new ArrayList<>();
        String fromEntity = request.getEntityName().substring(0, request.getEntityName().indexOf(ASSOCIATION_SUFFIX));
        long lastWatermark = request.getWatermark().getEnd();
        List<EntityData> entityDataList = new ArrayList<>();
        if(!changeStream.hasAssociationChangeStream()) {
            Long entityChangeStream = changeStream.getEntityChangeStream();
            Optional<EntitySchema> schemaOpt;
            DescribeRequest describeRequest = new DescribeRequest(request.getConnector(), fromEntity);
            schemaOpt = describe(describeRequest);
            if(schemaOpt.isPresent()) {
                long offset = entityChangeStream;
                WatermarkInfo watermarkInfo = new WatermarkInfo(originalWatermarkInfo.getStart(),
                        originalWatermarkInfo.getEnd(), originalWatermarkInfo.isInitial(), offset);
                SyncRequest syncRequest = new SyncRequest().setEntitySchema(schemaOpt.get())
                        .setWatermark(watermarkInfo).setPageSize(DEFAULT_PAGE_SIZE).setConnector(request.getConnector());
                FetchResponse response = getByWatermark(syncRequest);
                EntityDataBatchIterator iterator = response.getIterator();
                while(entityDataList.size() < entityPageSize && iterator.hasNext()) {
                    entityDataList.addAll(iterator.next());
                }

                lastWatermark = getAssociations(request, associationEntitiesMap, changeStream, associationsList, fromEntity, entityDataList, iterator);
                while(associationsList.isEmpty() && iterator.hasNext()) {
                    boolean hasNext;
                    List<EntityData> newEntityDataList = new ArrayList<>();
                    do{
                        newEntityDataList.addAll(iterator.next());
                        hasNext = iterator.hasNext();
                    } while (hasNext && newEntityDataList.size() < entityPageSize);
                    if(hasNext && newEntityDataList.size() >= entityPageSize) {
                        newEntityDataList.addAll(iterator.next());
                    }
                    entityDataList.addAll(newEntityDataList);
                    lastWatermark = getAssociations(request, associationEntitiesMap, changeStream, associationsList, fromEntity, newEntityDataList, iterator);
                }
            } else {
                throw new RuntimeException("Failed to fetch schema for primary entity - " + fromEntity);
            }
        }

        /**
         * Fetch association if there is association changestream present
         * Update changestream with association offset
         */

        if(changeStream.hasAssociationChangeStream()) {
            Map<String, Pair<Set<String>, Long>> associationChangeStream = changeStream.getAssociationChangeStream();
            for(String toEntity: associationChangeStream.keySet()) {
                Pair<Set<String>, Long> idMap = associationChangeStream.get(toEntity);
                Pair<List<EntityData>, Long> associations = fetchAssociations(fromEntity, toEntity, idMap.x, idMap.y, request, new HashMap<>(), -1);
                associationsList.addAll(associations.x);
                if(associations.y != 0) {
                    associationChangeStream.put(toEntity, Pair.of(idMap.x, associations.y));
                } else {
                    associationChangeStream.remove(toEntity);
                }
            }
        }

        /**
         * If HubspotAssociationChangeStream is present then convert it to string and store it to iterator changestream
         */

        String changeStreamJson = "";
        if(changeStream.hasChangeStream()) {
            Gson gson = new Gson();
            changeStreamJson = gson.toJson(changeStream);
        }

        HubspotListBasedIterator iterator = new HubspotListBasedIterator(associationsList, entityDataList, request.getWatermark(), changeStreamJson);
        iterator.setLastWatermark(lastWatermark);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private long getAssociations(SyncRequest request, Map<String, Set<String>> associationEntitiesMap, HubspotAssociationChangeStream changeStream, List<EntityData> associationsList, String fromEntity, List<EntityData> entityDataList, EntityDataBatchIterator iterator) {
        long lastWatermark;
        Map<String, Long> idToLastModifiedMap = buildIdToLastModifiedMap(entityDataList);

        changeStream.setEntityChangeStream(iterator.getLastOffset());
        lastWatermark = iterator.getLastWatermark();
        Set<String> entityIds = new HashSet<>();
        if(!entityDataList.isEmpty()) {
            entityIds.addAll(entityDataList.stream().map(entityData -> entityData.getId()).collect(Collectors.toSet()));
        }
        if(!entityIds.isEmpty()) {
            for (String toEntity : associationEntitiesMap.get(fromEntity)) {
                long offset = 0;
                do {
                    Pair<List<EntityData>, Long> associations = fetchAssociations(fromEntity, toEntity, entityIds, offset, request, idToLastModifiedMap, lastWatermark);
                    associationsList.addAll(associations.x);
                    offset = associations.y;
                } while (offset != 0);
            }
        }
        return lastWatermark;
    }

    private Map<String, Long> buildIdToLastModifiedMap(List<EntityData> entityDataList) {
        Map<String, Long> results = new HashMap<>();
        entityDataList.forEach(ed -> {
            results.put(ed.getId(), ed.getLastModified());
        });
        return results;
    }

    Pair<List<EntityData>, Long> fetchAssociations(String fromEntity, String toEntity, Set<String> ids, long offset,
                                                           SyncRequest request, Map<String, Long> idToLastModifiedMap, long lastWatermark) {
        List<EntityData> allAssociations = new ArrayList<>();
        long maxWatermark = lastWatermark;

        // Partition IDs into chunks of 1000
        List<String> idList = new ArrayList<>(ids);
        List<List<String>> idPartitions = Lists.partition(idList, HUBSPOT_BATCH_LIMIT);

        log.debug("Fetching associations for {} IDs in {} batches (limit: {})", ids.size(), idPartitions.size(), HUBSPOT_BATCH_LIMIT);

        for (int i = 0; i < idPartitions.size(); i++) {
            List<String> idBatch = idPartitions.get(i);
            log.debug("Processing batch {}/{} with {} IDs", i + 1, idPartitions.size(), idBatch.size());

            String url = getUrl(request.getEntityName(), request.getConnector().getAuthConfig(), String.format(ASSOCIATIONS_BATCH_READ, fromEntity, toEntity));

            SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient();
            ObjectMapper objectMapper = new ObjectMapper();
            List<Map<String, String>> idMapList = new ArrayList<>();
            idBatch.forEach(id -> idMapList.add(Map.of("id", id)));
            Map<String, List<Map<String, String>>> payload = Map.of("inputs", idMapList);

            try {
                String body = objectMapper.writeValueAsString(payload);
                ResponseEntity<String> response = restClient.postRaw(url, body, request.getConnector().getAuthConfig());
                Pair<List<EntityData>, Long> batchAssociations = parseAssociations(response, objectMapper, fromEntity, toEntity, request, idToLastModifiedMap, lastWatermark);
                allAssociations.addAll(batchAssociations.x);
                maxWatermark = Math.max(maxWatermark, batchAssociations.y);
            } catch (JsonProcessingException e) {
                throw new UnknownException(e.getMessage());
            } catch (Exception e) {
                throw e;
            }
        }

        log.debug("Fetched total of {} associations across {} batches", allAssociations.size(), idPartitions.size());
        // Return 0 as offset since batch read API doesn't support pagination - all results fetched in batches
        return Pair.of(allAssociations, 0L);
    }

    private Pair<List<EntityData>, Long> parseAssociations(ResponseEntity<String> response, ObjectMapper objectMapper,
                                                           String fromEntity, String toEntity, SyncRequest request, Map<String, Long> idToLastModifiedMap, long lastWatermark) {
        try {
            List<EntityData> associations = new ArrayList<>();
            Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);
            if(responseMap.containsKey("results")) {
                List results = (List) responseMap.get("results");
                results.forEach(result -> {
                    Map<String, Object> fromToMap = (Map<String, Object>)result;
                    Map<String, String> fromMap = (Map<String, String>)fromToMap.get("from");
                    String fromId = fromMap.get("id");
                    List toList = (List) fromToMap.get("to");
                    toList.forEach(toObject -> {
                        Map<String, Object> toMap = (Map<String, Object>) toObject;
                        String toId = String.valueOf(toMap.get("toObjectId"));
                        List associationTypes = (List)toMap.get("associationTypes");
                        associationTypes.forEach(associationType -> {
                            Map<String, String> associationTypeMap = (Map<String, String>)associationType;
                            String category = String.valueOf(associationTypeMap.get("category"));
                            String typeId = String.valueOf(associationTypeMap.get("typeId"));
                            String label = associationTypeMap.get("label");
                            EntityData entityData = new EntityData(request.getEntityName());
                            entityData.setId(getId(fromId, toId, toEntity, category, typeId));
                            entityData.addValue("fromObjectType", fromEntity);
                            entityData.addValue("toObjectType", toEntity);
                            entityData.addValue("fromObjectId", fromId);
                            entityData.addValue("toObjectId", toId);
                            entityData.addValue("category", category);
                            entityData.addValue("typeId", typeId);
                            entityData.addValue("label", label);
                            long lastModified = 0;
                            if(lastWatermark != -1) {
                                lastModified = lastWatermark;
                            }
                            else if(idToLastModifiedMap.containsKey(fromId)) {
                                lastModified = idToLastModifiedMap.get(fromId);
                            } else {
                                lastModified = getLastModified(fromEntity, request, fromId);
                            }
                            entityData.setLastModified(lastModified);
                            associations.add(entityData);
                        });
                    });
                });
            }
            long offset = 0;
            if(responseMap.containsKey("hasMore") && (boolean) responseMap.get("hasMore")) {
                offset = (long) responseMap.get("offset");
            }
            return Pair.of(associations, offset);
        } catch (JsonProcessingException e) {
            throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), "500");
        }
    }

    private long getLastModified(String fromEntity, SyncRequest request, String fromId) {
        long lastModified = 0;
        DescribeRequest describeRequest = new DescribeRequest(request.getConnector(), fromEntity);
        Optional<EntitySchema> entitySchemaOptional =  describe(describeRequest);
        if(entitySchemaOptional.isPresent()) {
            EntitySchema entitySchema = entitySchemaOptional.get();
            SyncRequest syncRequest = new SyncRequest();
            syncRequest.setEntitySchema(entitySchema);
            syncRequest.setConnector(request.getConnector());
            EntityData getByData = new EntityData(fromEntity);
            getByData.setId(fromId);
            syncRequest.addData(request.getConnector().getId(), getByData);
            List<EntityData> resultDataList = getByIds(syncRequest);
            for(EntityData ed: resultDataList) {
                if(ed.getId().equalsIgnoreCase(fromId)) {
                    lastModified = ed.getLastModified();
                }
            }
        }
        return lastModified;
    }

    private Set<String> getCurrentAssociations(String fromEntity, String toEntity, String fromObjectId, AuthConfig authConfig) {
        String url = getUrl(fromEntity, authConfig, String.format(ASSOCIATIONS_BATCH_READ, fromEntity, toEntity));
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient();
        ObjectMapper objectMapper = new ObjectMapper();

        List<Map<String, String>> idMapList = List.of(Map.of("id", fromObjectId));
        Map<String, List<Map<String, String>>> payload = Map.of("inputs", idMapList);

        try {
            String body = objectMapper.writeValueAsString(payload);
            ResponseEntity<String> response = restClient.postRaw(url, body, authConfig);

            Set<String> currentAssociations = new HashSet<>();
            Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);

            if (responseMap.containsKey("results")) {
                List results = (List) responseMap.get("results");
                if (!results.isEmpty()) {
                    Map result = (Map) results.get(0);
                    if (result.containsKey("to")) {
                        List toObjects = (List) result.get("to");
                        for (Object toObj : toObjects) {
                            Map toMap = (Map) toObj;
                            Object toIdObj = toMap.get("toObjectId");
                            String toId = toIdObj != null ? toIdObj.toString() : null;
                            if (toId != null) {
                                currentAssociations.add(toId);
                            }
                        }
                    }
                }
            }
            return currentAssociations;
        } catch (Exception e) {
            log.error("Failed to fetch current associations for {} {} -> {}: {}", fromEntity, fromObjectId, toEntity, ExceptionUtils.getStackTrace(e));
            return new HashSet<>();
        }
    }

    private void deleteAssociations(String fromEntity, String toEntity, List<Association> associationsToDelete, AuthConfig authConfig) {
        if (associationsToDelete.isEmpty()) {
            return;
        }

        String url = getUrl(fromEntity, authConfig, String.format(ASSOCIATION_DELETE, fromEntity, toEntity));
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient();
        ObjectMapper objectMapper = new ObjectMapper();

        var partitioned = Lists.partition(associationsToDelete, 100);
        for (List<Association> batch : partitioned) {
            List<Map<String, Object>> inputs = batch.stream().map(assoc -> {
                String category = getAssociationCategory(fromEntity, toEntity, assoc.getDefinitionId());
                Map<String, Object> input = new HashMap<>();
                input.put("from", Map.of("id", String.valueOf(assoc.getFromObjectId())));
                input.put("to", Map.of("id", String.valueOf(assoc.getToObjectId())));
                input.put("types", List.of(Map.of(
                    "associationCategory", category,
                    "associationTypeId", assoc.getDefinitionId()
                )));
                return input;
            }).collect(Collectors.toList());

            Map<String, Object> payload = Map.of("inputs", inputs);

            try {
                String body = objectMapper.writeValueAsString(payload);
                ResponseEntity<String> response = restClient.postRaw(url, body, authConfig);

                if (response.getStatusCode() != HttpStatus.NO_CONTENT && response.getStatusCode() != HttpStatus.OK) {
                    log.error("Failed to delete associations: HTTP {}", response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("Failed to delete associations for {} -> {}: {}", fromEntity, toEntity, ExceptionUtils.getStackTrace(e));
            }
        }
    }

    private String getAssociationCategory(String fromEntity, String toEntity, int associationTypeId) {
        return "HUBSPOT_DEFINED";
    }

    private void handleContactAssociations(SyncRequest request,
                                         Stream<Pair<String, Object>> currentAssociations,
                                         List<Association> associationsToAdd,
                                         List<Association> associationsToDelete) {

        int contactAssocId = request.getEntityName().toLowerCase().equalsIgnoreCase(DEAL) ? DEAL_TO_CONTACT_ASSOC_ID : ENGAGEMENT_TO_CONTACT_ASSOC_ID;

        currentAssociations.forEach(assoc -> {
            String fromObjectId = assoc.x;

            log.debug("handleContactAssociations - Object {}: Received value type: {}, value: {}",
                     fromObjectId, assoc.y != null ? assoc.y.getClass().getName() : "null", assoc.y);

            List<String> newContactIds = new ArrayList<>();

            if (assoc.y == null) {
                log.debug("Object {}: Contact associations is null (empty list)", fromObjectId);
            } else if (assoc.y instanceof List) {
                List<?> rawList = (List<?>) assoc.y;

                // Handle malformed nested list [[]]
                if (!rawList.isEmpty() && rawList.get(0) instanceof List) {
                    log.error("Object {}: Received malformed nested list for contact associations: {}. This should be a flat list of contact IDs.",
                              fromObjectId, assoc.y);
                } else {
                    for (Object item : rawList) {
                        if (item != null && !item.toString().isEmpty()) {
                            newContactIds.add(item.toString());
                        }
                    }
                }
            } else {
                log.error("Object {}: Unexpected type for contact associations: {} (expected List or null)",
                         fromObjectId, assoc.y.getClass().getName());
            }

            Set<String> currentContactIds = getCurrentAssociations(
                request.getEntityName().toLowerCase(),
                "contact",
                fromObjectId,
                request.getConnector().getAuthConfig()
            );

            Set<String> newContactIdSet = newContactIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

            Set<String> contactsToAdd = new HashSet<>(newContactIdSet);
            contactsToAdd.removeAll(currentContactIds);

            Set<String> contactsToRemove = new HashSet<>(currentContactIds);
            contactsToRemove.removeAll(newContactIdSet);

            for (String contactId : contactsToAdd) {
                try {
                    if (contactId != null && !contactId.trim().isEmpty() && !contactId.equals("[]")) {
                        associationsToAdd.add(new Association(Long.valueOf(fromObjectId), Long.valueOf(contactId), contactAssocId));
                    }
                } catch (NumberFormatException e) {
                    log.error("Invalid contact ID for association: '{}'. Skipping. Error: {}", contactId, ExceptionUtils.getStackTrace(e));
                }
            }

            for (String contactId : contactsToRemove) {
                try {
                    if (contactId != null && !contactId.trim().isEmpty() && !contactId.equals("[]")) {
                        associationsToDelete.add(new Association(Long.valueOf(fromObjectId), Long.valueOf(contactId), contactAssocId));
                    }
                } catch (NumberFormatException e) {
                    log.error("Invalid contact ID for deletion: '{}'. Skipping. Error: {}", contactId, ExceptionUtils.getStackTrace(e));
                }
            }

            log.debug("Object {}: Adding {} contacts, removing {} contacts",
                fromObjectId, contactsToAdd.size(), contactsToRemove.size());
        });
    }

    private String getId(String fromId, String toId, String toType, String category, String typeId) {
        return fromId + "-" + toId + "-" + toType + "-" + category + "-" + typeId;
    }

    private Map<String, String> parseId(String id) {
        if(StringUtils.countMatches(id, '-') == 4) {
            String[] parts = id.split("-");
            if(parts.length == 5) {
                return Map.of("fromObjectId", parts[0], "toObjectId", parts[1],
                        "toObjectType", parts[2], "category", parts[3], "typeId", parts[4]);
            }
        }
        return Map.of();
    }

    private String appendIncludeDeletes(String url) {
        if (url.contains("?")) {
            return url + "&includeDeletes=true";
        }
        return url + "?includeDeletes=true";
    }

    private Map<String, Object> getAdditionalProperties(SyncRequest request, EntityData data) {
        FluentMap<String,Object> additionalProperties = FluentMap.of();
        var associations = FluentMap.of();

        if(DEAL.equalsIgnoreCase(request.getEntityName()) || ENGAGEMENT.equalsIgnoreCase(request.getEntityName())) {
            // Append associated account if present (there will always be only 1 account as per hubspot doc
            if(data.has("associatedcompanyid") && data.getValue("associatedcompanyid") != null) {
                associations.add("associatedCompanyIds",List.of(data.getValue("associatedcompanyid")));
            }
            if(data.has("associatedVids") && data.getValue("associatedVids") != null) {
                associations.add("associatedVids",data.getValue("associatedVids"));
            }
        }
        if(!associations.isEmpty()){
            additionalProperties.add("associations",associations);
        }
        return additionalProperties;
    }

    private void updateSchema(EntitySchema entityDefinition, DescribeRequest request) {
        switch (entityDefinition.getApiName().toLowerCase()) {
        case DEAL:
        case ENGAGEMENT:
            AttributeSchema accountId = new AttributeSchema("associatedcompanyid", "reference");
            accountId.setDisplayName("Account Id");
            accountId.setReferenceTo("company");
            accountId.setReferenceTargetField(HS_OBJECT_ID);
            if (ENGAGEMENT.equalsIgnoreCase(entityDefinition.getApiName().toLowerCase())) accountId.setMultiValueField(true);
            entityDefinition.addField(accountId);
            AttributeSchema contacts = new AttributeSchema("associatedVids", "reference");
            contacts.setDisplayName("Associated Contacts");
            contacts.setReferenceTo("contact");
            contacts.setReferenceTargetField("vid");
            contacts.setMultiValueField(true);
            entityDefinition.addField(contacts);
            if (entityDefinition.hasField("hs_call_disposition")) {
                addCallDispositionPickListValues(entityDefinition, request);
            }
            break;

        case LINE_ITEM:
            AttributeSchema dealId = new AttributeSchema("hs_deal_id", "reference");
            dealId.setDisplayName("Deal Id");
            dealId.setReferenceTo("deal");
            dealId.setReferenceTargetField(HS_OBJECT_ID);
            entityDefinition.addField(dealId);

            AttributeSchema quoteId = new AttributeSchema("hs_quote_id", "reference");
            quoteId.setDisplayName("Quote Id");
            quoteId.setReferenceTo("quote");
            quoteId.setReferenceTargetField(HS_OBJECT_ID);
            entityDefinition.addField(quoteId);
            break;

        case QUOTE:
            AttributeSchema quoteAccountId = new AttributeSchema("associatedcompanyid", "reference");
            quoteAccountId.setDisplayName("Account Id");
            quoteAccountId.setReferenceTo("company");
            quoteAccountId.setReferenceTargetField(HS_OBJECT_ID);
            entityDefinition.addField(quoteAccountId);
            AttributeSchema quoteContacts = new AttributeSchema("associatedVids", "reference");
            quoteContacts.setDisplayName("Associated Contacts");
            quoteContacts.setReferenceTo("contact");
            quoteContacts.setReferenceTargetField("vid");
            quoteContacts.setMultiValueField(true);
            entityDefinition.addField(quoteContacts);
            AttributeSchema quoteDealId = new AttributeSchema("associateddealid", "reference");
            quoteDealId.setDisplayName("Associated Deal Id");
            quoteDealId.setReferenceTo("deal");
            quoteDealId.setReferenceTargetField(HS_OBJECT_ID);
            entityDefinition.addField(quoteDealId);
            break;

        default:
            break;
        }
    }

    private void addCallDispositionPickListValues(EntitySchema entityDefinition, DescribeRequest request) {
        Map<String, String> dispositions = refreshCallDispositions(request.getConnector());
        entityDefinition.getField("hs_call_disposition").get().setPicklistValues(getCallDispositionLabels(dispositions));
        dispositionCache.put(new CacheKey(request.getConnector()), dispositions);
    }

    public List<String> getCallDispositionLabels(Map<String, String> dispositions) {
        return dispositions.values().stream().map(Object::toString).collect(Collectors.toList());
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of(Constants.ACCOUNT.toLowerCase(), "company",
                Constants.CONTACT.toLowerCase(), "contact",
                Constants.OPPORTUNITY.toLowerCase(), "deal",
                Constants.TICKET.toLowerCase(), "ticket",
                Constants.USER.toLowerCase(), Constants.OWNER.toLowerCase(),
                Constants.EVENT.toLowerCase(), "event",
                Constants.ACTIVITY.toLowerCase(), "activity");
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return HubspotSeed.getAttributeMappings(entityApiName);
    }

    private String getValue(Map o, String key) {
        return o.get(key) != null ? o.get(key).toString() : null;
    }

    private Boolean getBoolValue(Map o, String key) {
        return o.get(key) != null ? Boolean.valueOf(o.get(key).toString()) : null;
    }

	@Override
	public String extractIdentifier(WebhookRequest request) {
    	try {
			List entities = mapper.readValue(request.getBody(), List.class);
			for (Object object : entities) {
				Map entity = (Map)object;
				return entity.get("portalId").toString();
			}
			throw new RuntimeException("Invalid request. The eventdata json is invalid");
		} catch (JsonProcessingException e) {
			log.error(ExceptionUtils.getStackTrace(e));
			throw new RuntimeException("Invalid request. The eventdata json is invalid");
		}
	}

	@Override
	public String getEndpoint() {
		return "/api/v1/webhooks/"+Constants.HUBSPOT;
	}

	@Override
	public String getIdentifier(ConnectorInfo config) {
		return config.getMetaConfig().get("portalId").toString();
	}
}

@Data
@AllArgsConstructor
class Property {
    String name;
    String value;
}

@Data
@AllArgsConstructor
class MergeBody {
    String objectIdToMerge;
    String primaryObjectId;
}

class FluentMap<K,V> extends HashMap<K,V>{
    public  static <K,V> FluentMap<K,V> of(K key, V value){
        return new FluentMap<K,V>().add(key,value);
    }
    public  static <K,V> FluentMap<K,V> of(){
        return new FluentMap<>();
    }

    public FluentMap<K,V> add(K key, V value){
        super.put(key,value);
        return this;
    }
}

@Data
class Association {
    long fromObjectId;
    long toObjectId;
    String category = "HUBSPOT_DEFINED";
    int definitionId;

    public Association(long fromObjectId, long toObjectId, int definitionId) {
        this.fromObjectId = fromObjectId;
        this.toObjectId = toObjectId;
        this.definitionId = definitionId;
    }
}

@Data
@Slf4j
class HubspotActivitySyncContactsIterator {

    String activityPageOffset;
    EntityDataBatchIterator contactsIterator;

    private final int maxContactsLimit;
    private final int maxActivitiesLimit;

    // The watermark of contacts to be used as offset for the activities iterator.
    long currentContactsWatermark = 0;
    boolean hasSameWatermarkAsPrev = false;
    // To keep track of how many contacts were consumed.
    long contactsConsumedCount = 0;
    long activitiesConsumedCount = 0;
    long wmLimit;
    List<EntityData> contactsList = new ArrayList<EntityData>();
    ListIterator<EntityData> contactsListIterator;
    EntityData currentContact = null;

    public HubspotActivitySyncContactsIterator(String activityPageOffset, EntityDataBatchIterator contactsIterator, long wmLimit,
            int maxContactsLimit, int maxActivitiesLimit) {
        this.activityPageOffset = activityPageOffset;
        this.contactsIterator = contactsIterator;
        this.contactsListIterator = contactsList.listIterator();
        this.wmLimit = (wmLimit <= 0) ? 1000000000 : wmLimit;
        this.maxContactsLimit = maxContactsLimit;
        this.maxActivitiesLimit = maxActivitiesLimit;
    }

    public EntityData getCurrentContact() {
        return currentContact;
    }

    // Contacts page iterator.
    public EntityData next() {
        if (!hasNext()) return null;

        currentContact = contactsListIterator.next();
        // The records are ordered so this is the contact's current watermark.
        hasSameWatermarkAsPrev = currentContactsWatermark > 0 && currentContactsWatermark == currentContact.getLastModified() ? true : false;
        currentContactsWatermark = currentContact.getLastModified();
        ++contactsConsumedCount;
        return currentContact;
    }

    public boolean hasNext() {
        if (contactsList.isEmpty() || !contactsListIterator.hasNext()) {
            // refill from source.
            if (contactsIterator.hasNext()) {
                contactsList = contactsIterator.next();
                contactsListIterator = contactsList.listIterator();
            } else {
                // If done, clear up.
                contactsList = new ArrayList<>();
            }
        }
        return contactsListIterator.hasNext();
    }

    public void incrementActivitiesConsumedBy(int count) {
        activitiesConsumedCount += count;
    }

    // Apply some limits to the iterator.
    public boolean hasReachedLimit() {
        // When we reach maximum contacts to consume and the watermark is not same as previous one, exit iteration.
        if (contactsConsumedCount >= maxContactsLimit && !hasSameWatermarkAsPrev && StringUtils.isBlank(activityPageOffset)) {
            log.debug("Reached maxContactsLimit: {} and no activityPageOffset for this contact {}, exiting", maxContactsLimit, currentContact);
            return true;
        }
        // For test scenarios we will be setting the limit, so exit when we reach it. This is sample scenario.
        if (activitiesConsumedCount >= wmLimit) {
            log.debug("Reached wmLimit: {}, exiting", wmLimit);
            return true;
        }
        return false;
    }
}

// A wrapper to send the activitysync contact's watermark as offset.
class HubspotActivitiesIterator extends DefaultDataIterator {

    HubspotActivitySyncContactsIterator activitySyncContactsIterator;

    public HubspotActivitiesIterator(WatermarkInfo baseWatermark, long offset, Function3<WatermarkInfo, Integer, Long,
            Pair<Long, Stream<EntityData>>> generator, List<EntityData> data, AttributeSchema watermarkField,int pageSize, int maxRecords,
            HubspotActivitySyncContactsIterator activitySyncContactsIterator) {
        super(baseWatermark, offset, generator, data, watermarkField,pageSize,maxRecords);
        this.activitySyncContactsIterator = activitySyncContactsIterator;
    }

    @Override
    public long getLastOffset() {
        return activitySyncContactsIterator.getCurrentContactsWatermark();
    }

    @Override
    public Offset getOffsetInfo() {
        return new Offset(OffsetType.RECORD_COUNT, pageSize);
    }
}

@Getter
@Setter
class HubspotAssociationChangeStream {

    Map<String, Pair<Set<String>, Long>> associationChangeStream = new HashMap<>();

    Long entityChangeStream = 0L;

    public boolean hasChangeStream() {
        return !associationChangeStream.isEmpty() || !(entityChangeStream == 0);
    }

    public boolean hasAssociationChangeStream() {
        return !associationChangeStream.isEmpty();
    }

    public boolean hasEntityChangeStream() {
        return !(entityChangeStream == 0);
    }
}