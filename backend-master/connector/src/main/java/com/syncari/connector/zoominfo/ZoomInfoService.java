package com.syncari.connector.zoominfo;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.syncari.connector.data.*;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.def.*;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.Response;
import com.syncari.connector.ValueHolder;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.ProxyConfig;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.data.iterator.LocalStorageService;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.QuotaExceededException;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(Constants.ZOOMINFO_SYNAPSE)
public class ZoomInfoService implements CommonDataService, MetadataService, OauthAuthenticationService, SynapseInfoService, RestClientService {
    public static final String MAX_API_CALLS_PER_DAY = "maxApiCallsPerDay";
    private static final String API_HOST = "https://api.zoominfo.com";
    private static final String AUTHENTICATION_ENDPOINT = "/authenticate";
    private static final long TOKEN_EXPIRY_SECONDS = 3600;
    private static final int WAIT_TIMEOUT_MILLIS = 60000;
    private static final String SEARCH_INTENT = API_HOST + "/search/intent";
    private static final List<String> SUPPORTED_ENTITIES = List.of("intent");
    private static final List<String> SYSTEM_CONFIG = List.of(MAX_API_CALLS_PER_DAY, "authType");
    @Autowired
    LocalStorageService localStorageService;
    Cache<Object, Object> apiUsage = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.DAYS).maximumSize(100000).build();

    @Autowired
    ObjectMapper mapper;
    @Autowired
    DateUtil utils;
    
    @Override
    public SyncariEntityDataRestClient getRestClient() {
        return new ZoomInfoRestClient();
    }

    @Override
    public SyncariEntityDataRestClient getRestClient(ProxyConfig proxy) {
        return new ZoomInfoRestClient(proxy);
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        throw new RuntimeException("OAuth Implicit Flow not supported by ZoomInfo");
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig currentConfig = connector.getAuthConfig();
        RestTemplate restTemplate = getTemplate();
        String getTokenUrl = API_HOST + AUTHENTICATION_ENDPOINT;

        return ConnectorHelper.withHttpErrorHandling(() -> {
            var requestBody = Map.of("username", currentConfig.getUserName(), "password", currentConfig.getPassword());
            String payloadString = null;
            try {
                payloadString = mapper.writeValueAsString(requestBody);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            log.info("Retrieving Token using HTTP POST at {}",getTokenUrl);
            ResponseEntity<String> response = this.getRestClient().postRaw(new HttpHeaders(), getTokenUrl, payloadString, connector.getAuthConfig());
            log.info("POST: HTTP Status {}",response.getStatusCode());

            ReadContext ctx = JsonPath.parse(response.getBody());
            Boolean isSuccess = ctx.read("success");
            AuthConfig authConfig = currentConfig.clone();
            if(BooleanUtils.isTrue(isSuccess)){
                authConfig.setAccessToken(ctx.read("jwt").toString());
                authConfig.setRefreshToken(ctx.read("jwt").toString());
                authConfig.setExpiresIn(String.valueOf(TOKEN_EXPIRY_SECONDS));
                authConfig.setLastRefreshed(Instant.now());
                log.info(format("Successfully refreshed token for ZoomInfo"));
            }else {
                log.warn(format("Unable to retrieve Access token for ZoomInfo"));
            }
            return authConfig;
        });
    }

    @Override
    public String getAuthHost(AuthConfig config) {
        return API_HOST;
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        throw new RuntimeException("OAuth Implicit Flow not supported by ZoomInfo");
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo connector, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try{
            AuthConfig updatedConfig = refreshToken(connector);
            response.setAuthConfig(updatedConfig);
            log.info(format("Successfully authenticated ZoomInfo connection for %s", connector.getName()));
            return response;
        } catch (Exception e) {
            log.error("ZoomInfo Authentication failed {}", e.getMessage());
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        return Optional.of(ZoomInfoSeed.getEntity(request.getEntity()));
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField maxApiCallsPerDay = new AuthField().setName(MAX_API_CALLS_PER_DAY).setLabel(i18n(MAX_API_CALLS_PER_DAY))
                .setDataType("text").setHelpSummary(i18n("maxApiCallsPerDay_summary")).setRequired(false);
        AuthField topics = new AuthField().setName("topics").setLabel(i18n("topics"))
                .setDataType("text").setHelpSummary(i18n("topics_summary"));
        AuthField signalScoreMin = new AuthField().setName("signalScoreMin").setLabel(i18n("signalScoreMin"))
                .setDataType("integer").setHelpSummary(i18n("signalScoreMin_summary")).setRequired(false);
        AuthField signalScoreMax = new AuthField().setName("signalScoreMax").setLabel(i18n("signalScoreMax"))
                .setDataType("integer").setHelpSummary(i18n("signalScoreMax_summary")).setRequired(false);
        AuthField audienceStrengthMin = new AuthField().setName("audienceStrengthMin").setLabel(i18n("audienceStrengthMin"))
                .setDataType("text").setHelpSummary(i18n("audienceStrengthMin_summary")).setRequired(false);
        AuthField audienceStrengthMax = new AuthField().setName("audienceStrengthMax").setLabel(i18n("audienceStrengthMax"))
                .setDataType("text").setHelpSummary(i18n("audienceStrengthMax_summary")).setRequired(false);
        AuthField country = new AuthField().setName("country").setLabel(i18n("country"))
                .setDataType("text").setHelpSummary(i18n("country_summary")).setRequired(false);
        AuthField continent = new AuthField().setName("continent").setLabel(i18n("continent"))
                .setDataType("text").setHelpSummary(i18n("continent_summary")).setRequired(false);
        AuthField techAttributeTagList = new AuthField().setName("techAttributeTagList").setLabel(i18n("techAttributeTagList"))
                .setDataType("text").setHelpSummary(i18n("techAttributeTagList_summary")).setRequired(false);
        AuthField primaryIndustriesOnly = new AuthField().setName("primaryIndustriesOnly").setLabel(i18n("primaryIndustriesOnly"))
                .setDataType("text").setHelpSummary(i18n("primaryIndustriesOnly_summary")).setRequired(false);
        AuthField industryKeywords = new AuthField().setName("industryKeywords").setLabel(i18n("industryKeywords"))
                .setDataType("text").setHelpSummary(i18n("industryKeywords_summary")).setRequired(false);
        AuthField sicCodes = new AuthField().setName("sicCodes").setLabel(i18n("sicCodes"))
                .setDataType("text").setHelpSummary(i18n("sicCodes_summary")).setRequired(false);
        AuthField revenue = new AuthField().setName("revenue").setLabel(i18n("revenue"))
                .setDataType("text").setHelpSummary(i18n("revenue_summary")).setRequired(false);
        AuthField fundingAmountMin = new AuthField().setName("fundingAmountMin").setLabel(i18n("fundingAmountMin"))
                .setDataType("text").setHelpSummary(i18n("fundingAmountMin_summary")).setRequired(false);
        return List.of(maxApiCallsPerDay, topics, signalScoreMin,
                signalScoreMax, audienceStrengthMin, audienceStrengthMax, country,
                continent, techAttributeTagList, primaryIndustriesOnly, industryKeywords,
                sicCodes, revenue, fundingAmountMin, ConnectorHelper.getSupportedAuthPicker());
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
        return Constants.ZOOMINFO_SYNAPSE;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/zoominfo.svg")
                .setDisplayName("ZoomInfo")
                .setBackgroundColor("#F8F8F8")
                .setHelpUrl(helpArticlesBaseUrl + SYNAPSE_COMING_SOON_ARTICLE);
    }

    @Override
    public String getCategory() {
        return "EnrichService";
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        ValueHolder<Integer> lastOffset = new ValueHolder<>();
        lastOffset.set(0);
        String connectorId = request.getConnector().getId();
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            if ((offset != 0 && lastOffset.get() == null) || lastOffset.get() == -1)
                return Pair.of(0L, new ArrayList<EntityData>().stream());
            switch (request.getEntityName()) {
                case "intent":
                    List<EntityData> result = new ArrayList<>();
                    try {
                        HttpHeaders headers = this.getRestClient().getHeaders(request.getConnector().getAuthConfig());
                        String intentParams = getIntentParams(request, lastOffset.get());
                        log.info(intentParams);
                        ResponseEntity<String> res = this.getRestClient().postRaw(headers, SEARCH_INTENT, intentParams, request.getConnector().getAuthConfig());
                        incrementApiUsage(request);
                        log.info("POST: HTTP Status {}",res.getStatusCode());
                        log.debug("Response body: {}", res.getBody());

                        ReadContext ctx = JsonPath.parse(res.getBody());
                        List rows = ctx.read("data");
                        if (rows != null && rows.size() > 0) {
                            for (int i = 0; i < rows.size(); i++) {
                                result.addAll(extractData(request, connectorId, (Map) rows.get(i)));
                            }
                        }
                        Response response = new Response(String.valueOf(offset+result.size()), result);
                        int total = ctx.read("totalResults");
                        int currentPage = ctx.read("currentPage");
                        // default page size is 25. If we have exhausted all pages, exit
                        lastOffset.set(25 * currentPage >= total ? -1 : ctx.read("currentPage"));
                        return Pair.of(Long.valueOf(response.getRecords().size()), response.getRecords().stream());
                    } catch (QuotaExceededException e) {
                        throw e;
                    } catch (Exception e) {
                        if(e instanceof HttpClientErrorException) log.error(((HttpClientErrorException)e).getResponseBodyAsString());
                        throw new RuntimeException(e.getMessage());
                    }

                default:
                    throw new RuntimeException("Unsupported get for " + request.getEntityName());
            }
        };
        return getCompositeFetchResponse(request, generator);

    }

    private FetchResponse getCompositeFetchResponse(SyncRequest request,
                                                    Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator) {
        localStorageService.provisionIfNotExists(request, request.getEntityName());
        long maxLocalWatermark = localStorageService.maxWatermark(request.getConnector(), request.getEntityName());
        WatermarkInfo watermark = request.getWatermark();
        long startWatermark = watermark.getStart();
        long endWatermark = watermark.getEnd();
        WatermarkInfo wmForZoomInfo = new WatermarkInfo(startWatermark, endWatermark, watermark.isInitial(), watermark.getOffset());
        if(maxLocalWatermark > startWatermark) {
            wmForZoomInfo.setStart(maxLocalWatermark).setEnd(Math.max(endWatermark, maxLocalWatermark));
        }
        if(maxLocalWatermark < endWatermark) {
            DefaultDataIterator iterator = new DefaultDataIterator(wmForZoomInfo,
                    wmForZoomInfo.getOffset(), generator, new ArrayList<>(),
                    request.getEntitySchema().getWatermarkField(), 50, wmForZoomInfo.getLimit());
            localStorageService.fetch(request, iterator);
        }
        return localStorageService.getByWatermark(request);
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        // Start from the synapse configured signalStartDate
        Integer numDays = (Integer) request.getConnector().getMetaConfig().get("signalStartDate");
        Date start = utils.subtractDaysFromToday(numDays);
        return start.toInstant().toEpochMilli();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> entities = new ArrayList<>();
        SUPPORTED_ENTITIES.forEach(e -> {
            Optional<EntitySchema> entity = describe(new DescribeRequest(request.getConnector(), e));
            entity.map(e1 -> entities.add(e1));
        });
        return entities;
    }

    @Override
    public boolean isSink() {
        return false;
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19208829480468";
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        throw new RuntimeException("getByIds not supported for zoominfo");
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        throw new RuntimeException("create not supported for zoominfo");
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        throw new RuntimeException("update not supported for zoominfo");
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        throw new RuntimeException("delete not supported for zoominfo");
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported for zoominfo");
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("createField not supported for zoominfo");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("deleteField not supported for zoominfo");
    }

    public RestTemplate getTemplate() {
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(WAIT_TIMEOUT_MILLIS);
        clientHttpRequestFactory.setReadTimeout(WAIT_TIMEOUT_MILLIS);
        return new RestTemplate(clientHttpRequestFactory);
    }

    private String getIntentParams(SyncRequest request, int pageNumber) throws JsonProcessingException {
        Map params = new HashMap();
        List<String> allowedKeys = getConfigureFields().stream().map(a -> a.getName()).collect(Collectors.toList());
        request.getConnector().getMetaConfig().forEach((k, v) -> {
            if(v != null && !SYSTEM_CONFIG.contains(k) && allowedKeys.contains(k)) {
                if("topics".equalsIgnoreCase(k)) {
                    String[] parts = v.toString().split(",");
                    String[] topics = Arrays.stream(parts).map(String::trim).toArray(String[]::new);
                    params.put(k, topics);
                } else if(List.of("signalScoreMin", "signalScoreMax").contains(k)){
                    params.put(k, Integer.parseInt(v.toString()));
                } else {
                    params.put(k, v);
                }
            }
        });
        String start = utils.format(Date.from(Instant.ofEpochMilli(request.getWatermark().getStart())), DateUtil.dateOnlyFormat);
        String end = utils.format(Date.from(Instant.ofEpochMilli(request.getWatermark().getEnd())), DateUtil.dateOnlyFormat);
        params.put("signalStartDate", start);
        params.put("signalEndDate", end);
        params.put("sortBy", "signalDate");
        params.put("sortOrder", "asc");
        params.put("page", pageNumber);
        return mapper.writeValueAsString(params);
    }

    private List<EntityData> extractData(SyncRequest request, String connectorId, Map row) {
        List<EntityData> result = new ArrayList<>();
        if(!row.containsKey("recommendedContacts") && !row.containsKey("company")) return result;
        Date signalDate = utils.parse(row.get("signalDate").toString(), DateUtil.dateFormat5);
        long lastModified = signalDate.toInstant().toEpochMilli();

        List recommendedContacts = (List) row.get("recommendedContacts");
        for (Object r : recommendedContacts) {
            Map contact = (Map) r;
            EntityData data = new EntityData(request.getEntityName());
            Long id = getLongValue(contact, "id");
            data.setId("contact_"+id);
            data.setLastModified(lastModified);
            data.setConnectorId(connectorId);
            data.addValue("recordId", id);
            data.addValue("firstName", contact.get("firstName"));
            data.addValue("lastName", contact.get("lastName"));
            data.addValue("jobTitle", contact.get("jobTitle"));
            String jobFunctionName = getValue(row, "jobFunction", "name");
            if(jobFunctionName != null) {
                if(data.has("jobFunctionName") && !StringUtils.isBlank(data.getValueAsString("jobFunctionName"))) {
                    data.addValue("jobFunctionName", data.getValueAsString("jobFunctionName").concat(",").concat(jobFunctionName));
                } else {
                    data.addValue("jobFunctionName", jobFunctionName);
                }
            }
            String jobFunctionDepartment = getValue(row, "jobFunction", "department");
            if(jobFunctionDepartment != null) {
                if(data.has("jobFunctionDepartment") && !StringUtils.isBlank(data.getValueAsString("jobFunctionDepartment"))) {
                    data.addValue("jobFunctionDepartment", data.getValueAsString("jobFunctionDepartment").concat(",").concat(jobFunctionName));
                } else {
                    data.addValue("jobFunctionDepartment", jobFunctionDepartment);
                }
            }
            data = populate(row, data);
            data.addValue("type", "contact");
            result.add(data);
        }
        Map company = (Map) row.get("company");
        EntityData data = new EntityData(request.getEntityName());
        Long id = getLongValue(company, "id");
        data.setId("company_"+id);
        data.setConnectorId(connectorId);
        data.addValue("recordId", id);
        data.addValue("companyName", company.get("name"));
        data.addValue("companyWebsite", company.get("website"));
        data.addValue("hasOtherTopicConsumption", company.get("hasOtherTopicConsumption"));
        data = populate(row, data);
        data.addValue("type", "company");
        data.setLastModified(lastModified);
        result.add(data);
        return result;
    }

    private String getValue(Map row, String parent, String child) {
        if(row.containsKey(parent) && ((Map)row.get(parent)).containsKey(child)) {
            return ((Map)row.get(parent)).get(child).toString();
        }
        return null;
    }

    private EntityData populate(Map row, EntityData data) {
        String topic = row.getOrDefault("topic", "").toString();
        String category = row.getOrDefault("category", "").toString();
        String audienceStrength = row.getOrDefault("audienceStrength", "").toString();
        Boolean newSignal = getBoolValue(row, "newSignal");
        Integer signalScore = getIntValue(row, "signalScore");
        Integer trend = getIntValue(row, "trend");
        Date signalDate = utils.parse(row.get("signalDate").toString(), DateUtil.dateFormat5);

        data.addValue("category", category);
        data.addValue("audienceStrength", audienceStrength);
        data.addValue("signalScore", signalScore);
        data.addValue("topic", topic);
        data.addValue("signalDate", signalDate);
        data.addValue("newSignal", newSignal);
        data.addValue("trend", trend);
        return data;
    }

    private Integer getIntValue(Map row, String key) {
        if(row.containsKey(key) && row.get(key) != null) {
            return Integer.parseInt(row.get(key).toString());
        }
        return null;
    }

    private Long getLongValue(Map row, String key) {
        if(row.containsKey(key) && row.get(key) != null) {
            return Long.parseLong(row.get(key).toString());
        }
        return null;
    }

    private Boolean getBoolValue(Map row, String key) {
        if(row.containsKey(key) && row.get(key) != null) {
            return Boolean.parseBoolean(row.get(key).toString());
        }
        return null;
    }

    private void incrementApiUsage(SyncRequest request) {
        if(!request.getConnector().getMetaConfig().containsKey(MAX_API_CALLS_PER_DAY)) return;
        int max = 0;
        try {
            max = (int) request.getConnector().getMetaConfig().getOrDefault(MAX_API_CALLS_PER_DAY, 1000000);
        } catch (Exception e) {
            return;
        }
        if(max < 0) return;
        Object entry = apiUsage.getIfPresent(request.getConnector().getId());
        if(entry == null) {
            entry = 1;
        } else {
            entry = (Integer)entry + 1;
            if((Integer)entry >= max) {
                long tryAfterSeconds = DateUtil.getTodayEndWithTimezone(ZoneId.of(DateUtil.CENTRAL_TIME_ZONE)).getEpochSecond()
                        - Instant.now().getEpochSecond();
                throw new QuotaExceededException(ErrorCodes.TOO_MANY_REQUESTS.name(),
                        ErrorCodes.TOO_MANY_REQUESTS.name(), ErrorCodes.TOO_MANY_REQUESTS.name(),
                        request.getConnector().getId(), tryAfterSeconds);
            }
        }
        apiUsage.put(request.getConnector().getId(), entry);
    }
    public void clearCache() {
        apiUsage.invalidateAll();
    }

}