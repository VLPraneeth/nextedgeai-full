package com.syncari.connector.zuora;

import static com.syncari.connector.ConnectorHelper.withRateLimitHandling;
import static java.lang.String.format;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.DefaultAuthTokenHandler;
import com.syncari.connector.EntityData;
import com.syncari.connector.Status;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.data.BatchJob;
import com.syncari.connector.data.CreateFieldRequest;
import com.syncari.connector.data.CreateObjectRequest;
import com.syncari.connector.data.DataWithCursor;
import com.syncari.connector.data.DeleteFieldRequest;
import com.syncari.connector.data.DescribeAllRequest;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.OAuthRequest;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.UIMetadata;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.CSVStorageIterator;
import com.syncari.connector.data.iterator.CompositeEntityDataIterator;
import com.syncari.connector.data.iterator.DefaultCursorBasedIterator;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.data.iterator.LocalStorageService;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.Transformer;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(Constants.ZUORA)
public class ZuoraService implements OauthAuthenticationService, CommonDataService, MetadataService, SynapseInfoService {
    private static final String ID = "Id";
    private static final String WATERMARK_FLD = "UpdatedDate";

    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;
    @Autowired
    LocalStorageService localStorageService;
    @Autowired
    DateUtil dateUtil;

    public static final long _WATERMARK_INCREMENT = 1 * 24 * 60 * 60 * 1000l; //1 days

    // Zuora query end point does not let us change this value.
    private static final int DEFAULT_PAGE_SIZE = 2000;

    private static final String GET_OBJECT = "/v1/"+ZuoraRestClient.ZUORA_WSDL_VERSION_REQ_HEADER_VALUE+"/describe/%s";
    private static final String GET_ALL_OBJECTS = "/v1/"+ZuoraRestClient.ZUORA_WSDL_VERSION_REQ_HEADER_VALUE+"/describe";
    private static final String QUERY_OBJECT = "/v1/action/query";
    private static final String QUERY_MORE_OBJECT = "/v1/action/queryMore";
    private static final String CRUD_DATA = "/v1/%s";
    private static final String BILL_PREVIEW_RUN_ENDPOINT = "/v1/billing-preview-runs";
    private static final String UNIQUE_KEY = "UniqueKey";

    public static final String dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    public static final List<String> SUPPORTED_OBJECTS = List.of("Account","AccountingCode","AccountingPeriod","Amendment",
        "BillingRun","BillingPreviewRun","Contact","CommunicationProfile","Product","ProductRatePlan","RatePlan","Subscription","Usage",
        "Invoice","InvoiceItem");

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of(Constants.ACCOUNT.toLowerCase(), Constants.ACCOUNT.toLowerCase(), Constants.CONTACT.toLowerCase(), Constants.CONTACT.toLowerCase());
    }
    
    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }
    
    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(new AuthMetadata(AuthType.SimpleOAuth,
            List.of(ConnectorHelper.getClientIdField(), ConnectorHelper.getClientSecretField()), "Simple OAuth", ""));
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of(ConnectorHelper.getEndpointField(), ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCategory() {
        return "Accounting";
    }
    
    @Override
    public String getName() {
        return Constants.ZUORA;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/zuora.svg")
                .setDisplayName("Zuora")
                .setBackgroundColor("#EFF2F6")
                .setHelpUrl(helpArticlesBaseUrl + "/4408576189716");
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19169156854676";
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if (ZuoraSeed.BILLING_PREVIEW_RUN.equalsIgnoreCase(request.getEntityName())) {
            return fetchBillingPreviewRun(request);
        }
        return getSortedDataIterator(request, request.getWatermark());
    }

    protected FetchResponse getSortedDataIterator(SyncRequest request, WatermarkInfo requestWm) {
        long start = Instant.ofEpochMilli(requestWm.getStart()).toEpochMilli();
        long startWatermark =( start <= getFirstCreatedTime(request) ) ? getFirstCreatedTime(request) : start;
        
        //Store against db name
        localStorageService.provisionIfNotExists(request, request.getEntityName());

        WatermarkInfo zuoraWatermark = new WatermarkInfo(startWatermark, requestWm.getEnd(), requestWm.isInitial(), requestWm.getOffset());
        zuoraWatermark.setChangeStream(requestWm.getChangeStream());
        zuoraWatermark.setLimit(requestWm.getLimit());
        zuoraWatermark.setResync(requestWm.isResync());
        zuoraWatermark.setTest(requestWm.isTest());

        long maxLocalWatermark = (requestWm.isResync() && startWatermark == 0) ? 0 
            : localStorageService.maxWatermark(request.getConnector(), request.getEntityName());
        log.info("The maxLocalWatermark to consider is: {} ", maxLocalWatermark);
        if(maxLocalWatermark > startWatermark) {
            zuoraWatermark.setStart(maxLocalWatermark);
        }

        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize,
                changeStream) -> {
            String url = request.getConnector().getEndpoint() + QUERY_OBJECT;
            String postBody = "";
            // If for first page, changeStream will be empty, in which case, begin the cursor iteration.
            if (StringUtils.isEmpty(changeStream)) {
                String entityName = ("BillingRun".equalsIgnoreCase(request.getEntitySchema().getApiName())) ? "BillRun" : 
                    request.getEntitySchema().getApiName();
                postBody = String.format("{\"queryString\": \"select %s from %s WHERE UpdatedDate >= '%s'\"}", 
                    getFields(request.getEntitySchema()), entityName, 
                    dateUtil.formatDate(Instant.ofEpochMilli(wm.getStart()), dateFormat));
            } else {
                postBody = String.format("{\"queryLocator\": \"%s\"}", changeStream);
                url = request.getConnector().getEndpoint() + QUERY_MORE_OBJECT;
            }
            try {
                return post(request, url, postBody, changeStream);
            } catch (NonRetriableException e) {
                if("Usage".equalsIgnoreCase(request.getEntitySchema().getApiName()) && e.getMessage().contains("INVALID_FIELD") && e.getMessage().contains("Usage.uniquekey")) {
                    if (StringUtils.isEmpty(changeStream)) {
                        postBody = String.format("{\"queryString\": \"select %s from %s WHERE UpdatedDate >= '%s'\"}",
                                getFieldsExcept(request.getEntitySchema(), UNIQUE_KEY), request.getEntitySchema().getApiName(),
                                dateUtil.formatDate(Instant.ofEpochMilli(wm.getStart()), dateFormat));
                    } else {
                        postBody = String.format("{\"queryLocator\": \"%s\"}", changeStream);
                        url = request.getConnector().getEndpoint() + QUERY_MORE_OBJECT;
                    }
                    return post(request, url, postBody, changeStream);
                }
                throw e;
            }
        };

        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(zuoraWatermark,
            zuoraWatermark.getChangeStream(),
            zuoraWatermark.getOffset(), generator, new ArrayList<>(), DEFAULT_PAGE_SIZE, zuoraWatermark.getLimit());

        localStorageService.fetch(request, iterator);

        return localStorageService.getByWatermark(request);
    }

    private String getFields(EntitySchema entity) {
        return String.join(", ", entity.getAttributes().stream()
            .filter(a -> a.getStatus() == Status.ACTIVE).map(a -> a.getApiName()).collect(Collectors.toList()));
    }

    private String getFieldsExcept(EntitySchema entity, String field) {
        return String.join(", ", entity.getAttributes().stream()
                .filter(a -> a.getStatus() == Status.ACTIVE && !a.getApiName().equalsIgnoreCase(field)).map(a -> a.getApiName()).collect(Collectors.toList()));
    }

    private FetchResponse fetchBillingPreviewRun(SyncRequest request) {
        ZuoraRestClient restClient = getClient(getSingleJsonConfig(request.getEntityName()));
        
        final String billingPreviewRunURL = request.getConnector().getEndpoint() + BILL_PREVIEW_RUN_ENDPOINT;
        String targetDate = dateUtil.formatDate(Instant.ofEpochMilli(request.getWatermark().getStart()), dateFormat);

        List<BatchJob> pendingJobs = request.getBatchJobs();
        List<BatchJob> batchJobs = new ArrayList<>();
        if (CollectionUtils.isEmpty(pendingJobs) || pendingJobs.size() == 0) {
            log.info("Pending Billing Preview Run ids {}", pendingJobs);
            batchJobs = List.of(restClient.submitAsyncJob(request, billingPreviewRunURL, targetDate));
        }

        List<BatchJob> newJobStatuses = pendingJobs.stream()
                .map(pending -> {
                    BatchJob newJob = pending;
                    if (pending.isError()) {
                        //retry failed download;
                        newJob = restClient.submitAsyncJob(request, billingPreviewRunURL, targetDate);
                    } else if (pending.isPending()) {
                        newJob = restClient.queryJobStatus(request, billingPreviewRunURL, pending);
                        if (newJob.isCompleted()) {
                            newJob = restClient.downloadAsyncJobResults(request, newJob);
                        }
                    }
                    newJob.setInternalId(pending.getInternalId());
                    return newJob;
                }).collect(Collectors.toList());

        //TODO: Build an iterator for downloaded files
        int pageSize = request.getPageSize() ==0? 1000 : request.getPageSize();
        List<CSVStorageIterator> csvStorageIteratorStream = newJobStatuses.stream()
                .filter(j -> j.isCompleted())
                .map(job -> new ZuoraCSVIterator(job.getJobId(), request.getStorage(), job, pageSize, request, true))
                .collect(Collectors.toList());
        FetchResponse fetchResponse = new FetchResponse(request.getWatermark(), new CompositeEntityDataIterator(csvStorageIteratorStream, 0));
        List<BatchJob> allJobs = new ArrayList<>();
        allJobs.addAll(batchJobs);
        allJobs.addAll(newJobStatuses);
        fetchResponse.setBatchJobs(allJobs);
        return fetchResponse;
    }

    private DataWithCursor post(SyncRequest request, String url, String postBody, String prevChangeStream) {
        ZuoraRestClient restClient = new ZuoraRestClient(getBatchJsonConfig(request.getEntityName()), mapper);
        return restClient.getDataWithCursor(request, url, postBody, prevChangeStream);
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        return withRateLimitHandling(request.getConnector().getId(), () -> {
            ZuoraRestClient restClient = getClient(getSingleJsonConfig(request.getEntityName()));
            return restClient.getByIds(request.getConnector().getEndpoint() + String.format(CRUD_DATA, 
                ZuoraSeed.getCRUDObjectName(request.getEntityName().toLowerCase())),
                request);
        });
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        return ConnectorHelper.withRateLimitHandling(request.getConnector().getId(), () -> {
            ZuoraRestClient restClient = getClient(getSingleJsonConfig(request.getEntityName()));
            return restClient.createOrUpdate(request.getConnector().getEndpoint() + String.format(CRUD_DATA, 
                ZuoraSeed.getCRUDObjectName(request.getEntityName().toLowerCase())), 
                request, true);
        });
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return ConnectorHelper.withRateLimitHandling(request.getConnector().getId(), () -> {
            ZuoraRestClient restClient = getClient(getSingleJsonConfig(request.getEntityName()));
            return restClient.createOrUpdate(request.getConnector().getEndpoint() + String.format(CRUD_DATA, 
                ZuoraSeed.getCRUDObjectName(request.getEntityName().toLowerCase())), 
                request, false);
        });
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return ConnectorHelper.withRateLimitHandling(request.getConnector().getId(), () -> {
            ZuoraRestClient restClient = getClient(getSingleJsonConfig(request.getEntityName()));
            return restClient.delete(request.getConnector().getEndpoint() + String.format(CRUD_DATA, 
                ZuoraSeed.getCRUDObjectName(request.getEntityName().toLowerCase())), request);
        });
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        String entityName = request.getEntity();
        log.info("Starting describe for {}", entityName);

        if (ZuoraSeed.BILLING_PREVIEW_RUN.equalsIgnoreCase(entityName)) {
            return Optional.of(ZuoraSeed.getBillingPreviewRunSchema());
        }

        ZuoraRestClient restClient = getClient(getBatchJsonConfig(request.getEntity() + "_fields"));
        EntitySchema entity = new EntitySchema(request.getEntity(), request.getEntity());
            
        ResponseEntity<String> response = ConnectorHelper.withRateLimitHandling(request.getConnector().getId(), () -> {
            return getResponse(String.format(request.getConnector().getEndpoint() + GET_OBJECT, entityName),
                request.getConnector().getAuthConfig(), restClient);
        });
        restClient.checkResponse(response);

        entity = toEntityAndFieldSchemas(Jsoup.parse(response.getBody()), entity);
        return Optional.of(entity);
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> entities = new ArrayList<>();
        ZuoraRestClient restClient = getClient(getBatchJsonConfig("" + "_fields"));

        ResponseEntity<String> allObjectsResponse = ConnectorHelper.withRateLimitHandling(request.getConnector().getId(), () -> {
            return getResponse(request.getConnector().getEndpoint() + GET_ALL_OBJECTS,
                    request.getConnector().getAuthConfig(), restClient);
        });
        restClient.checkResponse(allObjectsResponse);
        Document parse = Jsoup.parse(allObjectsResponse.getBody());
        Elements fields = parse.getElementsByTag("object");

        for (Element f : fields) {
            String entityName = f.selectFirst("name").text();
            if (!SUPPORTED_OBJECTS.contains(entityName)) {
                log.debug("{} is not supported yet for Zuora Synapse.", entityName);
                continue;
            }
            DescribeRequest req = new DescribeRequest(request.getConnector(), entityName);
            entities.add(describe(req).get());
        }

        // Add BillingPreviewRun as explicit object.
        DescribeRequest req = new DescribeRequest(request.getConnector(), ZuoraSeed.BILLING_PREVIEW_RUN);
        entities.add(describe(req).get());

        return entities;
    }

    private EntitySchema toEntityAndFieldSchemas(Document parse, EntitySchema entity) {
        log.debug("Parsed Entity Response: {} ", parse);
        Elements fields = parse.getElementsByTag("field");
        for (Element f : fields) {
            AttributeSchema attr = new AttributeSchema();
            attr.setApiName(ZuoraSeed.getFieldAPIName(f.selectFirst("name").text()));
            attr.setDisplayName(f.selectFirst("label").text());
            attr.setDataType(f.selectFirst("type").text());
            attr.setNillable(Boolean.valueOf(f.selectFirst("required").text()));
            attr.setInitializable(Boolean.valueOf(f.selectFirst("createable").text()));
            attr.setUpdateable(attr.isInitializable() || Boolean.valueOf(f.selectFirst("updateable").text()));
            if (attr.isInitializable() && !Boolean.valueOf(f.selectFirst("updateable").text())) {
                attr.setCreateOnly(true);
                attr.setUpdateable(true);
            }
            
            attr.setCustom(Boolean.valueOf(f.selectFirst("custom").text()));
            if (ZuoraSeed.skipFieldForEntity(entity.getApiName(), attr)) {
                continue;
            }

            if ("picklist".equalsIgnoreCase(attr.getDataType())) {
                for (Element opt: f.selectFirst("options").getElementsByTag("option")) {
                    attr.getPicklistValues().add(opt.selectFirst("option").text());
                }
            }
            if (WATERMARK_FLD.equalsIgnoreCase(attr.getApiName())) {
                attr.setWatermarkField(true);
            }
            if (ID.equalsIgnoreCase(attr.getApiName())) {
                attr.setIdField(true);
            }
            ZuoraSeed.augmentRefDetail(entity.getApiName(), attr);
            entity.addField(attr);
        }
        return entity;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("Zuora does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("Zuora does not support delete field");
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "/oauth/token?client_id={{client_id}}&redirect_uri={{redirect_uri}}&response_type=code";
    }
    
    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        Map<String, String> map = Map.of("grant_type", "client_credentials",
                "client_id", config.getClientId(), "client_secret", config.getClientSecret());
        AuthConfig withToken = tokenHandler.refreshToken(config, connector.getEndpoint() + "/oauth/token", map);
        withToken.setClientId(config.getClientId());
        withToken.setClientSecret(config.getClientSecret());
        // The framework does not reauth if refreshtoekn is not set.
        withToken.setRefreshToken(withToken.getAccessToken());
        return withToken;
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        Map<String, String> map = Map.of("grant_type", "client_credentials", "client_id",
                oAuthRequest.getConfig().getClientId(), "client_secret", oAuthRequest.getConfig().getClientSecret());
        AuthConfig withToken = tokenHandler.getAccessToken(oAuthRequest.getEndpoint() + "/oauth/token", map);
        withToken.setClientId(oAuthRequest.getConfig().getClientId());
        withToken.setClientSecret(oAuthRequest.getConfig().getClientSecret());
        // The framework does not reauth if refreshtoekn is not set.
        withToken.setRefreshToken(withToken.getAccessToken());
        return withToken;
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            if (StringUtils.isEmpty(config.getAuthConfig().getAccessToken())) {
                config.setAuthConfig(refreshToken(config));
                response.setAuthConfig(config.getAuthConfig());
            }
            describe(new DescribeRequest(config, "account"));
            log.info(format("Successfully authenticated zuora connection for %s", config.getName()));
        } catch (Exception e) {
            handleAuthenticationErrorMessage(response, e);
        }
        return response;
    }

    private JsonParserConfig getBatchJsonConfig(String plural) {
        return new JsonParserConfig(plural, plural + "[{i}]", null, ID, true, plural + "[{i}].__key__");
    }

    ZuoraRestClient getClient(JsonParserConfig config) {
        return new ZuoraRestClient(config,mapper);
    }

    protected ResponseEntity<String> getResponse(String url, AuthConfig auth, ZuoraRestClient client) {
        RestTemplate restTemplate = client.getTemplate();
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(getHeaders(auth)), String.class);
    }

    private JsonParserConfig getSingleJsonConfig(String plural) {
        return new JsonParserConfig(null, null, null, ID, true, null);
    }

    private HttpHeaders getHeaders(AuthConfig authConf) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        // TODO remove this when oauth is supported
        headers.set("Authorization", "Bearer " + authConf.getAccessToken());
        return headers;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in zuora yet");
    }
}

class ZuoraIterator extends DefaultDataIterator {
    
    public ZuoraIterator(WatermarkInfo baseWatermark, long offset, Function3<WatermarkInfo, Integer, Long,
            Pair<Long, Stream<EntityData>>> generator, List<EntityData> data, AttributeSchema watermarkField,int pageSize, int maxRecords) {
        super(baseWatermark, offset, generator, data, watermarkField,pageSize,maxRecords);
    }

    @Override
    protected long nextOffset(Pair<Long, Stream<EntityData>> results, List<EntityData> data) {
        return offset + 1;
    }
}