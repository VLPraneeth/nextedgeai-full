package com.syncari.connector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.*;
import com.syncari.connector.exception.*;
import com.syncari.connector.rest.MarketoRestClient;
import com.syncari.connector.seed.marketo.MarketoSeed;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.iterator.MarketoCSVIterator;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;
import com.syncari.utils.TextUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function1;
import org.jooq.lambda.function.Function2;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.ExceptionUtils.rethrow;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component(Constants.MARKETO)
public class MarketoService implements OauthAuthenticationService, CommonDataService, MetadataService, SynapseInfoService {

    @Autowired
    LocalStorageService dbStorageIterator;

    private static final String STATIC_LIST_ID = "staticListId";
    private static final String GET_TOKEN_ENDPOINT = "/identity/oauth/token?grant_type=client_credentials&client_id=%s&client_secret=%s";
    private static final String DESCRIBE_ENTITY_ENDPOINT = "/rest/v1/%s/describe.json";
    private static final String LIST_CUSTOM_OBJECTS_ENDPOINT = "/rest/v1/customobjects.json";
    private static final String DESCRIBE_CUSTOM_OBJECT_ENTITY_ENDPOINT = "/rest/v1/customobjects/schema.json?names=%s";
    private static final String DESCRIBE_ACTIVITY_ENDPOINT = "/rest/v1/activities/types.json?nextPageToken=%s";
    private static final String DESCRIBE_CUSTOM_ACTIVITY_ENDPOINT = "/rest/v1/activities/external/types.json?nextPageToken=%s";

    private static final List<String> supportedEntities = List.of("lead", "company", Constants.ACTIVITY);

    private static final String GET_PAGE_TOKEN_ENDPOINT = "/rest/v1/activities/pagingtoken.json?sinceDatetime=%s";
    protected static final String GET_ACTIVITIES_CDV_ENDPOINT = "/rest/v1/activities/leadchanges.json?nextPageToken=%s&fields=%s&listId=%s";
    protected static final String GET_ACTIVITIES_BY_TYPE_ENDPOINT = "/rest/v1/activities.json?nextPageToken=%s&activityTypeIds=%s&listId=%s";
    private static final String GET_LEAD_ACTIVITIES_BY_TYPE_ENDPOINT = "/rest/v1/activities.json?nextPageToken=%s&activityTypeIds=%s&leadIds=%s";
    private static final String GET_ACTIVITIES_DELETED_LEADS_ENDPOINT = "/rest/v1/activities/deletedleads.json?nextPageToken=%s";

    private static final String ENTITY_DATA_ENDPOINT = "/rest/v1/%s.json";
    private static final String CUSTOM_ENTITY_DATA_ENDPOINT = "/rest/v1/customobjects/%s.json";
    private static final String MERGE_ENDPOINT = "/rest/v1/leads/%s/merge.json?leadId=%s&mergeInCRM=%s";

    private static final String PROGRAM_BY_ID_ENDPOINT = "/rest/asset/v1/program/%s.json";
    private static final String PROGRAM_DELETE_ENDPOINT = "/rest/asset/v1/program/%s/delete.json";
    private static final String PROGRAMS_ENDPOINT = "/rest/asset/v1/programs.json";
    private static final String PROGRAM_MEMBERSHIP_ENDPOINT = "/rest/v1/leads/%s/programMembership.json";
    private static final String PROGRAM_MEMBERS_ENDPOINT = "/rest/v1/programs/%s/members.json";
    private static final String STATIC_LIST_ENDPOINT = "/rest/v1/lists/%s/leads.json";
    private static final String LEAD_PUSH_ENDPOINT = "/rest/v1/leads/push.json";
    private static final String VALIDATE_STATIC_LIST_ENDPOINT = "/rest/v1/lists/%s.json";
    private static final String SYNC_PROGRAM_MEMBER_STATUS_ENDPOINT = "/rest/v1/programs/%s/members/status.json";
    private static final String DELETE_PROGRAM_MEMBER_ENDPOINT = "/rest/v1/programs/%s/members/delete.json";
    private static final String CREATE_BATCHJOB_ENDPOINT = "/bulk/v1/customobjects/%s/export/create.json";
    private static final String ENQUEUE_BATCHJOB_ENDPOINT = "/bulk/v1/customobjects/%s/export/%s/enqueue.json";
    private static final String GET_BATCHJOB_STATUS = "/bulk/v1/customobjects/%s/export/%s/status.json";
    private static final String DOWNLOAD_BATCHJOB_RESULT = "/bulk/v1/customobjects/%s/export/%s/file.json";
    private static final String CUSTOM_OBJECT_DELETE = "/rest/v1/customobjects/%s/delete.json";
    private static final Map<String, String> objPluralMap = Map.of("lead", "leads", "company", "companies", "opportunity", "opportunities");

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private static final List<String> LEADS_COMPANY_FIELDS = List.of("annualRevenue", "billingStreet", "billingCity", "billingCountry", "billingPostalCode", "billingState", "company", "mktoCompanyNotes", "externalCompanyId", "industry", "mainPhone", "numberOfEmployees", "sicCode", "site", "website", "externalSalesPersonId", "contactCompany");
    private static final int MAX_QUERY_BATCHSIZE = 300;
    private static final int PROGRAM_MAX_PAGESIZE = 200;
    private static final String MARKETO_FOLDER_NAME = "Syncari";

    /* Activity Types */
    protected static final String PROGRAM_STATUS_CHANGE_ACTIVITY_TYPES = "104";
    private static final String LEAD_CREATE_ACTIVITY_TYPE = "12";
    private static final String LEAD_UPDATE_ACTIVITY_TYPE = "13";
    private static final String LEAD_DELETE_ACTIVITY_TYPE = "37";
    private static final String ADD_TO_LIST_ACTIVITY_TYPE = "24";
    private static final String REMOVE_FROM_LIST_ACTIVITY_TYPE = "25";
    private static final String INTERESTING_MOMENT_ACTIVITY_TYPE = "46";
    private static final String FORM_FILL_ACTIVITY_TYPE = "2";
    private static final String CLICK_LINK_ACTIVITY_TYPE = "3";
    private static final String CLICK_LINK_IN_EMAIL_ACTIVITY_TYPE = "11";
    private static final String CLICK_LINK_IN_SALES_EMAIL_ACTIVITY_TYPE = "41";
    private static final String OPEN_EMAIL_ACTIVITY_TYPE = "10";
    private static final String OPEN_SALES_EMAIL_ACTIVITY_TYPE = "40";
    private static final String RECEIVE_SALES_EMAIL_ACTIVITY_TYPE = "45";
    public static int MAX_URL_LENGTH = 8192;
    protected final static List<String> SUPPORTED_ACTIVITY_TYPES = List.of(
            INTERESTING_MOMENT_ACTIVITY_TYPE,
            FORM_FILL_ACTIVITY_TYPE,
            CLICK_LINK_ACTIVITY_TYPE,
            ADD_TO_LIST_ACTIVITY_TYPE,
            REMOVE_FROM_LIST_ACTIVITY_TYPE,
            CLICK_LINK_IN_EMAIL_ACTIVITY_TYPE,
            CLICK_LINK_IN_SALES_EMAIL_ACTIVITY_TYPE,
            OPEN_EMAIL_ACTIVITY_TYPE,
            OPEN_SALES_EMAIL_ACTIVITY_TYPE,
            RECEIVE_SALES_EMAIL_ACTIVITY_TYPE
    );

    private final static Set<String> systemFields = Set.of("id", "createdAt", "updatedAt", "marketoGUID");
    private final static String WATERMARK_FIELD_NAME = "updatedAt";
    private final static int MAX_LEAD_IDS_FROM_ACTIVITIES = 900;
    private static final Map<String, List<String>> mandatoryFieldsOfEntity = Map.of(
            Constants.LEAD.toLowerCase(), List.of("email"),
            Constants.COMPANY.toLowerCase(), List.of("externalCompanyId"));

    private static final Map<String, Reference> referenceFieldsMetadata = Map.of(
            "acquisitionProgramId", new Reference("acquisitionProgramId", "program", "id"));

    private final static List<String> RECORD_SUCCESS_STATUSES = List.of("created", "updated", "deleted");

    private static final String CENTRAL_TIME_ZONE = "America/Chicago";
    private static final String ACTIVITY_ATTRIBUTE_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private static final String MUNCHKIN = "munchkin";
    private final static int CLOCK_SKEW_TOLERANCE_SECS = 5 * 60;
    private final static Set<String> mergeSupportedEntities = Set.of("lead");

    @Autowired
    ObjectMapper mapper;

    @Autowired
    DateUtil dateUtil;

    @Autowired
    TextUtil textUtil;

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getSimpleOAuthType());
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19201950139028";
    }

    @Override
    public int clockSkewTolerance(ConnectorInfo connectorInfo) {
        final String clockSkewTolerance = connectorInfo.getMetaConfig().getOrDefault("clockSkewTolerance", String.valueOf(CLOCK_SKEW_TOLERANCE_SECS)).toString();
        if(StringUtils.isBlank(clockSkewTolerance.strip())){
            return CLOCK_SKEW_TOLERANCE_SECS;
        }
        return Integer.parseInt(clockSkewTolerance.strip());
    }

    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<Capability>();
        capabilities.add(Capability.schemaEditInSyncari);
        capabilities.add(Capability.schemaCreateField);
        capabilities.add(Capability.update);
        return capabilities;
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField munchkinId = new AuthField().setName(MUNCHKIN).setLabel(i18n("munchkin_id"))
                .setDataType("text").setHelpSummary(i18n("mkto_munchkin_summary"));
        AuthField staticListIds = new AuthField().setName(STATIC_LIST_ID).setLabel(i18n("static_list_id"))
                .setDataType("text").setHelpSummary(i18n("static_list_id_summary"));
        return List.of(munchkinId, staticListIds, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public boolean validate(ConnectorInfo connector) {
        var munchkin = connector.getMetaConfig().get(MUNCHKIN);
        // check if null
        if(munchkin == null){
            throw new RuntimeException(i18n("marketo_invalid_munchkin_id_error"));
        }
        String munchkinId = munchkin.toString();

        // validate munchkin pattern
        String patternString = "\\d{3}-[a-zA-Z]{3}-\\d{3}";
        Pattern pattern = Pattern.compile(patternString);

        if (!pattern.matcher(munchkinId).matches()) {
            throw new RuntimeException(i18n("marketo_invalid_munchkin_id_error"));
        }

        // validate staticListId
        if(isStaticListProvided(connector)){
            var staticListId = connector.getMetaConfig().get(STATIC_LIST_ID).toString();
            if(!StringUtils.isNumeric(staticListId)){
                throw new RuntimeException(i18n("marketo_static_list_id_error"));
            }
            validateListAccess(staticListId, connector);
        }

        return true;
    }

    public void validateListAccess(String staticListId, ConnectorInfo connector) {
        try {
            // refreshAuthentication before making API call to validate static list
            AuthConfig refreshedConfig = refreshToken(connector);
            connector.setAuthConfig(refreshedConfig);
            MarketoRestClient restClient = getRestClient(getJsonConfig(null, "id"), connector.getId());
            String munchkin = getMunchkin(connector);
            String url = getHost(munchkin) + String.format(VALIDATE_STATIC_LIST_ENDPOINT, staticListId);
            List<EntityData> staticLists = restClient.get(url, connector, getTokenHandler(connector));
            if(CollectionUtils.isEmpty(staticLists)){
                throw new RuntimeException(format("Static list with id %s not found", staticListId));
            }
        } catch (RuntimeException e){
            log.error(e.getMessage(), e);
            throw new RuntimeException(i18n("marketo_static_list_validation_error", staticListId), e);
        }
    }

    @Override
    public String getCategory() {
        return "Marketing";
    }

    @Override
    public String getName() {
        return Constants.MARKETO;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/marketo.svg")
                .setDisplayName("Marketo")
                .setBackgroundColor("#FBF9FF")
                .setHelpUrl(helpArticlesBaseUrl + "/360052204592-Marketo-Setup");
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo connector, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try{
            AuthConfig updatedConfig = refreshToken(connector);
            response.setAuthConfig(updatedConfig);
            log.debug(format("Successfully authenticated Marketo REST API credentials for %s", connector.getName()));
            return response;
        } catch (Exception e) {
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            response.setMessage(StringUtils.isBlank(e.getMessage()) ? ConnectorErrorCodes.CONNECTION_ERROR : e.getMessage());
            handleAuthenticationErrorMessage(response, e);
        }
        return response;
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "";
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        throw new RuntimeException("OAuth Implicit Flow not supported by Marketo");
    }

    // Force refreshing the token irrespective of the expires whenever access token is invalid or expired
    public AuthConfig forceRefreshToken(ConnectorInfo connector){
        AuthConfig currentConfig = connector.getAuthConfig();
        currentConfig.setLastRefreshed(Instant.EPOCH);
        return refreshToken(connector);
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig currentConfig = connector.getAuthConfig();
        if(!refreshTokenNeeded(currentConfig)) {
            log.debug("Connector {} token will expire in {} seconds. Skipping refreshToken", connector.getName(),
                    (currentConfig.getLastRefreshed().getEpochSecond() + Long.valueOf(currentConfig.getExpiresIn())) - Instant.now().getEpochSecond());
            return currentConfig;
        }
        String munchkin = getMunchkin(connector);
        HttpClient client = HttpClient.newHttpClient();
        try{
            String getTokenUrl = String.format(getHost(munchkin) + GET_TOKEN_ENDPOINT,
                    currentConfig.getClientId(), currentConfig.getClientSecret());
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getTokenUrl)).timeout(Duration.ofMinutes(1))
                    .GET().build();
            log.debug("Retrieving Token");
            HttpResponse<String> response = ConnectorHelper.withHttpErrorHandling(() -> client.send(request, HttpResponse.BodyHandlers.ofString()));
            log.info(format("Got response code %s", response.statusCode()));

            Map responseValues = mapper.readValue(response.body(), Map.class);
            if (response.statusCode() != HttpStatus.OK.value() || !responseValues.containsKey("access_token")) {
                String msg = format("Error while authorizing Marketo: code: %s, details:%s", response.statusCode(),
                        response.body());
                if(response.statusCode() == HttpStatus.UNAUTHORIZED.value()){
                    msg = "Invalid client credentials";
                }
                log.error(msg);
                throw new RuntimeException(msg);
            }
            AuthConfig authConfig = currentConfig.clone();
            authConfig.setAccessToken(responseValues.get("access_token").toString());
            authConfig.setRefreshToken(responseValues.get("access_token").toString());
            authConfig.setExpiresIn(responseValues.get("expires_in").toString());
            authConfig.setLastRefreshed(Instant.now());

            log.info(format("Successfully refreshed token for Marketo"));
            return authConfig;
        } catch(NonRetriableException | RetriableException nex){
            throw nex;
        } catch (Exception e) {
            log.error(format("Error in refreshToken: %s", e.getMessage()), e);
            throw new RuntimeException(e);
        }
    }

    public boolean refreshTokenNeeded(AuthConfig config){
        if(config.getRefreshToken() == null || config.getExpiresIn() == null) return true;
        long lastRefreshed = (config.getLastRefreshed()==null? Instant.EPOCH : config.getLastRefreshed()).getEpochSecond();
        return (lastRefreshed + Long.valueOf(config.getExpiresIn())) <= Instant.now().getEpochSecond();
    }

    @Override
    public List<MergeResponse> merge(List<MergeRequest> requests) {
        List<MergeResponse> mergeResponses = doMerge(requests);
        return mergeResponses.isEmpty() ? List.of() : mergeResponses;
    }

    @Override
    public MergeResponse merge(com.syncari.connector.data.MergeRequest request) {
        String entityName = request.getEntityName();
        if (!mergeSupportedEntities.contains(entityName.toLowerCase())) {
            return CommonDataService.super.merge(request);
        }

        List<MergeResponse> mergeResponses = doMerge(List.of(request));
        return mergeResponses.isEmpty() ? null : mergeResponses.get(0);
    }

    private List<MergeResponse> doMerge(List<com.syncari.connector.data.MergeRequest> requests) {
        if(requests.isEmpty()){
            return List.of();
        }
        com.syncari.connector.data.MergeRequest example = requests.get(0);
        String entityName = example.getEntityName();
        if(!mergeSupportedEntities.contains(entityName.toLowerCase())) {
            return CommonDataService.super.merge(requests);
        }
        boolean mergeInCRM = (boolean) requests.get(0).getDestParams().getOrDefault("mergeInCRM", false);
        log.debug("The flag mergeInCRM {}", mergeInCRM);

        ConnectorInfo connector = requests.get(0).getConnector();
        String munchkin = getMunchkin(connector);
        MarketoRestClient restClient = getRestClient(getJsonConfig("leads", "leadId"), connector.getId());
        List<MergeResponse> response = new ArrayList<>();
        AuthConfig prevAuthConfig = new AuthConfig();
        for(MergeRequest request: requests) {
            if(request.getConnector().getAuthConfig().hasTokenChanges(prevAuthConfig) && StringUtils.isNotBlank(prevAuthConfig.getAccessToken())) {
                request.getConnector().setAuthConfig(prevAuthConfig);
            }
            // if winner id is null, create winner in Marketo
            if(request.getWinner().getId() == null) {
                SyncRequest createRequest = new SyncRequest().addData(connector.getId(), request.getWinner()).setConnector(connector)
                        .setEntitySchema(request.getEntitySchema());
                SyncResponse createResp = create(createRequest);
                request.getWinner().setId(createResp.getResults().get(0).getId());
                log.info("Successfully created winner lead {}", request.getWinner().getId());
            }
            MergeResponse mr = new MergeResponse();
            boolean failed = false;
            Optional<String> notFoundId = Optional.empty();
            for(EntityData loser : request.getLosers()) {
                Map respMap = mergeLoser(mergeInCRM, connector, munchkin, restClient, request, loser);
                List<Result> loserResults= new ArrayList<>();
                if(respMap.containsKey("success") && (boolean)respMap.get("success") == true) {
                    loserResults.add(new Result(true, loser.getId(), loser.getSyncariEntityId()));
                } else {
                    if(respMap.containsKey("errors")) {
                        failed = true;
                        List errors = (List) respMap.get("errors");
                        Result loserResult = new Result(false, loser.getId(), loser.getSyncariEntityId());
                        for(Object err: errors) {
                            if(err instanceof Map) {
                                Map<String, String> errMap = (Map<String, String>) err;
                                // Invalid access token. Reauth might fix
                                if (errMap.containsKey("code") && errMap.get("code").equalsIgnoreCase("601")) {
                                    AuthConfig updatedAuthConfig = forceRefreshToken(request.getConnector());
                                    request.getConnector().setAuthConfig(updatedAuthConfig);
                                    prevAuthConfig = updatedAuthConfig;
                                    respMap = mergeLoser(mergeInCRM, connector, munchkin, restClient, request, loser);
                                    if (respMap.containsKey("success") && (boolean) respMap.get("success") == true) {
                                        loserResults.add(new Result(true, loser.getId(), loser.getSyncariEntityId()));
                                        failed = false;
                                        break;
                                    }
                                } else if (errMap.containsKey("code") && errMap.get("code").equalsIgnoreCase("1004") && errMap.get("message").contains("not found")) {
                                    // get id that was not found
                                    var pattern = Pattern.compile("'(\\d+)' not found");
                                    Matcher matcher = pattern.matcher(errMap.get("message"));
                                    if (matcher.find()) {
                                        notFoundId = Optional.of(matcher.group(1));
                                    }

                                }
                            }
                            loserResult.addError(err.toString());
                            log.debug("Error on loser {}", err.toString());
                        };
                        if(failed) {
                            loserResults.add(loserResult);
                        }
                    }
                }
                mr.setLoserResult(new SyncResponse().setResults(loserResults));
            }
            List<Result> winnerResults = new ArrayList<>();
            Result result = new Result(!failed, request.getWinner().getId(), request.getWinner().getSyncariEntityId());
            if (notFoundId.isPresent() && request.getWinner().getId().equals(notFoundId.get())) {
                result.setErrorCode(ErrorCodes.DATA_NOT_FOUND.name());
            }
            winnerResults.add(result);
            mr.setWinnerResult(new SyncResponse(!failed).setResults(winnerResults));
            response.add(mr);
            log.info("Merge result winner:{} loser:{}", mr.getWinnerResult(), mr.getLoserResult());
        };
        return response;
    }

    private Map mergeLoser(boolean mergeInCRM, ConnectorInfo connector, String munchkin, MarketoRestClient restClient, MergeRequest request, EntityData loser) {
        String path = String.format(MERGE_ENDPOINT, request.getWinner().getId(), loser.getId(), mergeInCRM);
        ResponseEntity<String> resp = restClient.postRaw(String.format(getHost(munchkin) + path),
                null, connector.getAuthConfig());

        Map respMap = rethrow(()->mapper.readValue(resp.getBody(), Map.class));
        return respMap;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {

        log.info("Using watermark {} for entity {}", request.getWatermark().toString(), request.getEntityName());
        if(Constants.PROGRAM.equalsIgnoreCase(request.getEntityName())){
            return getByWatermarkPrograms(request);
        }else if(Constants.COMPANY.equalsIgnoreCase(request.getEntityName())){
            return getByWatermarkCompanies(request);
        } else if("programMembership".equalsIgnoreCase(request.getEntityName())){
            return getByWatermarkProgramMembership(request);
        } else if(Constants.ACTIVITY.equalsIgnoreCase(request.getEntityName())){
            return getByWatermarkActivities(request);
        } else if(request.getEntitySchema().isCustom()) {
            return getByWatermarkCustomObjects(request);
        } else {
            return getByWatermarkLeads(request);
        }
    }

    private boolean isStaticListProvided(ConnectorInfo connector) {
        Map<String, Object> metaConfig = connector.getMetaConfig();
        return metaConfig.containsKey(STATIC_LIST_ID) && metaConfig.get(STATIC_LIST_ID) != null && !"*".equals(metaConfig.get(STATIC_LIST_ID));
    }

    private FetchResponse getByWatermarkActivities(SyncRequest request) {
        WatermarkInfo watermark = request.getWatermark();
        List<String> activitiesToRetrieve = getSupportedActivityTypes(request.getConnector(), request.getEntitySchema());

        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize, pageToken) -> {

            if(StringUtils.isBlank(pageToken)){
                pageToken = getPageToken(request.getConnector(), watermark.getStart());
            }

            long lastActivityWatermark = -1l;

            var activities = retrieveActivities(request, GET_ACTIVITIES_BY_TYPE_ENDPOINT, activitiesToRetrieve, pageToken, Optional.empty());
            List<EntityData> prunedActivityData = new ArrayList<>();
            for(EntityData activity: activities.getData()){
                var activityWm = dateUtil.toEpochMilli(activity.getValueAsString("activityDate"));
                lastActivityWatermark = Math.max(lastActivityWatermark, activityWm);
                if (activityWm <= watermark.getEnd()){
                    prunedActivityData.add(activity);
                }
            }

            return new DataWithCursor(pageToken,
                    activities.isHasMore() && lastActivityWatermark <= watermark.getEnd() ? activities.getNextPage(): "",
                    prunedActivityData);
        };

        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(watermark,
                watermark.getChangeStream(),
                watermark.getOffset(),
                generator, new ArrayList<>(),
                MAX_QUERY_BATCHSIZE, watermark.getLimit(), true);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private FetchResponse getByWatermarkLeads(SyncRequest request) {
        WatermarkInfo watermark = request.getWatermark();
        if (watermark.isInitial() || (watermark.isResync() && !watermark.isPartialResync())) {
            // case 1: retrieve leads in historic sync using CREATE_LEAD activity (type = 12)
            return fetchLeadsInHistoricSync(request);
        } else {
            // Incremental Sync (fetch all CDV activities)
            if(isStaticListProvided(request.getConnector())) {
                // Case 2: retrieve leadIds using 1.AddToList and DeletedLeads activity 2.cdv activity for listId
                return fetchLeadsInStaticListIncrementalSync(request);
            } else {
                // Case 3: retrieve all leads in incremental sync if specific staticList id is not provided
                return fetchLeadsWithoutStaticListREST(request);
            }
        }
    }

    private FetchResponse fetchLeadsInHistoricSync(SyncRequest request) {


        WatermarkInfo watermark = request.getWatermark();
        String staticListId = getStaticListId(request.getConnector());
        String activitiesPageToken = "";
        if (StringUtils.isNotBlank(request.getWatermark().getChangeStream())) {
            activitiesPageToken = request.getWatermark().getChangeStream();
        } else {
            activitiesPageToken = getPageToken(request.getConnector(),
                    request.getWatermark().getOffset() == 0 ? request.getWatermark().getStart() : request.getWatermark().getOffset());
        }
        Function2<WatermarkInfo, String, MarketoEntityPage> generator = (wm, pageToken) -> {

            if (StringUtils.isBlank(pageToken)) {
                throw new RuntimeException(format("Invalid Page Token: %s", pageToken));
            }
            String tokenForCDVActivities = pageToken;
            Set<String> updatedLeadsToFetch = new HashSet<>();
            boolean hasMoreCDVActivities = tokenForCDVActivities != null;
            long lastWatermark = -1l;
            while (updatedLeadsToFetch.size() < MAX_LEAD_IDS_FROM_ACTIVITIES && hasMoreCDVActivities) {
                // fetch leads cdv for mapped fields only in given staticListId
                if (hasMoreCDVActivities) {
                    MarketoEntityPage updatedLeadsInList = retrieveActivities(request, GET_ACTIVITIES_BY_TYPE_ENDPOINT,
                            List.of(LEAD_CREATE_ACTIVITY_TYPE), tokenForCDVActivities, staticListId, Optional.empty());
                    for(EntityData data: updatedLeadsInList.getData()){
                        var leadId = data.getValueAsString("leadId");
                        updatedLeadsToFetch.add(leadId);
                        //var activityWm = dateUtil.toEpochMilli(data.getValueAsString("activityDate"));
                        lastWatermark = Math.max(lastWatermark, data.getLastModified());
                    }
                    hasMoreCDVActivities = updatedLeadsInList.isHasMore() && lastWatermark <= watermark.getEnd();
                    tokenForCDVActivities = hasMoreCDVActivities ? updatedLeadsInList.getNextPage() : null;
                }
            }

            log.info("Updated Leads Count: {}. Lead Ids: [{}]", updatedLeadsToFetch.size(), String.join(",", updatedLeadsToFetch));
            List<EntityData> result = new ArrayList<>();
            try {
                result = getById(request.getConnector(), request.getEntityName(), new ArrayList<>(updatedLeadsToFetch),
                        getActiveFields(request.getEntitySchemaWithMappedFields()));
            } catch (NonRetriableException e) {
                if (ErrorCodes.TOKEN_EXPIRED.name().equalsIgnoreCase(e.getErrorCode())) {
                    AuthConfig updatedAuthConfig = forceRefreshToken(request.getConnector());
                    request.getConnector().setAuthConfig(updatedAuthConfig);
                    result = getById(request.getConnector(), request.getEntityName(), new ArrayList<>(updatedLeadsToFetch),
                            getActiveFields(request.getEntitySchemaWithMappedFields()));
                } else {
                    throw e;
                }
            }
            MarketoEntityPage leads = new MarketoEntityPage();
            leads.setData(result);
            leads.setHasMore(hasMoreCDVActivities);
            leads.setNextPage(tokenForCDVActivities);
            return leads;
        };
        MarketoHistoricalDataIterator iterator = new MarketoHistoricalDataIterator(watermark, generator);
        iterator.setPageToken(activitiesPageToken);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private FetchResponse fetchLeadsInStaticListIncrementalSync(SyncRequest request) {
        WatermarkInfo watermark = request.getWatermark();
        String staticListId = getStaticListId(request.getConnector());
        Function2<WatermarkInfo, String, MarketoEntityPage> generator = (wm, pageToken) -> {

            if (StringUtils.isBlank(pageToken)) {
                throw new RuntimeException(format("Invalid Page Token: %s", pageToken));
            }
            String[] tokens = pageToken.split(",");
            String prevCDVToken = "INVALID";
            String prevDeleteToken = "INVALID";
            String tokenForCDVActivities =tokens[0];
            String tokenForListAddAndDeletedLeadsActivities = tokens[1];
            if(tokens.length == 4) {
                prevCDVToken = tokens[0];
                prevDeleteToken = tokens[1];
                tokenForCDVActivities = tokens[2];
                tokenForListAddAndDeletedLeadsActivities = tokens[3];
            }
            Set<String> updatedLeadsToFetch = new HashSet<>();
            Map<String, Long> leadToActivityTimestamp = new HashMap<>();
            Set<String> leadIdsToDelete = new HashSet<>();
            boolean hasMoreCDVActivities = !"INVALID".equalsIgnoreCase(tokenForCDVActivities);
            boolean hasMoreAddDeleteActivities = !"INVALID".equalsIgnoreCase(tokenForListAddAndDeletedLeadsActivities) && !wm.isPartialResync();
            long lastWatermarkCDV = -1l;
            long lastWatermarkList = -1l;
            while (updatedLeadsToFetch.size() < MAX_LEAD_IDS_FROM_ACTIVITIES && (hasMoreCDVActivities || hasMoreAddDeleteActivities)) {
                // fetch leads cdv for mapped fields only in given staticListId
                if (hasMoreCDVActivities) {
                    MarketoEntityPage updatedLeadsInList = retrieveActivities(request, GET_ACTIVITIES_CDV_ENDPOINT, getActiveFields(request.getEntitySchemaWithMappedFields()), tokenForCDVActivities, staticListId, Optional.empty());

                    for(EntityData data: updatedLeadsInList.getData()){
                        var leadId = data.getValueAsString("leadId");
                        updatedLeadsToFetch.add(leadId);
                        // set the latestActivityTime and use it to set tje lastModifiedTime of leads
                        // we need to set the lastModifiedTime of lead to activityDate because there is no guarantee that
                        // we will see the corresponding activity of lead in future as we perform CDV on subset of fields (only mapped fields)
                        leadToActivityTimestamp.putIfAbsent(leadId, data.getLastModified());
                        leadToActivityTimestamp.put(leadId, Math.max(leadToActivityTimestamp.get(leadId), data.getLastModified()));
                        var activityWm = dateUtil.toEpochMilli(data.getValueAsString("activityDate"));
                        lastWatermarkCDV = Math.max(lastWatermarkCDV, activityWm);
                    }
                    hasMoreCDVActivities = updatedLeadsInList.isHasMore() && lastWatermarkCDV <= watermark.getEnd();
                    if(hasMoreCDVActivities) {
                        prevCDVToken = tokenForCDVActivities;
                        tokenForCDVActivities = updatedLeadsInList.getNextPage();
                    } else {
                        tokenForCDVActivities = "INVALID";
                    }
                }

                // fetch activities of type Add to list (typeId 24) and Deleted leads (typeId 37) in given staticListId
                if (hasMoreAddDeleteActivities) {

                    MarketoEntityPage addedAndDeletedLeadsInList = request.isExcludeDeleted()
                            ? retrieveActivities(request, GET_ACTIVITIES_BY_TYPE_ENDPOINT, List.of(ADD_TO_LIST_ACTIVITY_TYPE), tokenForListAddAndDeletedLeadsActivities, staticListId, Optional.empty())
                            : retrieveActivities(request, GET_ACTIVITIES_BY_TYPE_ENDPOINT, List.of(ADD_TO_LIST_ACTIVITY_TYPE, LEAD_DELETE_ACTIVITY_TYPE), tokenForListAddAndDeletedLeadsActivities, staticListId, Optional.empty());
                    for(EntityData data: addedAndDeletedLeadsInList.getData()){
                        var leadId = data.getValueAsString("leadId");
                        var activityTypeId = data.getValueAsString("activityTypeId");
                        var listId = data.getValueAsString("primaryAttributeValueId");
                        if(!StringUtils.isBlank(listId) && staticListId.equalsIgnoreCase(listId)) {
                            // check if activity is for specified listId - if not skip it
                            if (LEAD_DELETE_ACTIVITY_TYPE.equalsIgnoreCase(activityTypeId)) {
                                leadIdsToDelete.add(leadId);
                            } else {
                                updatedLeadsToFetch.add(leadId);
                            }
                        }
                        leadToActivityTimestamp.putIfAbsent(leadId, data.getLastModified());
                        leadToActivityTimestamp.put(leadId, Math.max(leadToActivityTimestamp.get(leadId), data.getLastModified()));
                        var activityWm = dateUtil.toEpochMilli(data.getValueAsString("activityDate"));
                        lastWatermarkList = Math.max(lastWatermarkList, activityWm);
                    }
                    hasMoreAddDeleteActivities = addedAndDeletedLeadsInList.isHasMore() && lastWatermarkList <= watermark.getEnd();
                    if(hasMoreAddDeleteActivities) {
                        prevDeleteToken = tokenForListAddAndDeletedLeadsActivities;
                        tokenForListAddAndDeletedLeadsActivities = addedAndDeletedLeadsInList.getNextPage();
                    } else {
                        tokenForListAddAndDeletedLeadsActivities = "INVALID";
                    }
                }
            }
            log.info("Activity Watermarks -> lastWatermarkCDV: {}, lastWatermarkAddToList: {}", lastWatermarkCDV, lastWatermarkList);

            log.info("Updated Leads Count: {}. Lead Ids: [{}]", updatedLeadsToFetch.size(), String.join(",", updatedLeadsToFetch));
            log.info("Deleted Leads Count: {}. Lead Ids: [{}]", leadIdsToDelete.size(), String.join(",", leadIdsToDelete));
            List<EntityData> result = new ArrayList<>();
            try {
                result = getById(request.getConnector(), request.getEntityName(), new ArrayList<>(updatedLeadsToFetch),
                        getActiveFields(request.getEntitySchemaWithMappedFields()));
            } catch (NonRetriableException e) {
                if (ErrorCodes.TOKEN_EXPIRED.name().equalsIgnoreCase(e.getErrorCode())) {
                    AuthConfig updatedAuthConfig = forceRefreshToken(request.getConnector());
                    request.getConnector().setAuthConfig(updatedAuthConfig);
                    result = getById(request.getConnector(), request.getEntityName(), new ArrayList<>(updatedLeadsToFetch),
                            getActiveFields(request.getEntitySchemaWithMappedFields()));
                } else {
                    throw e;
                }
            }
            // set the lastModifiedTime of each Lead to its corresponding latestActivityWm
            // This is needed to avoid unnecessary lead pruning and also guarantee the lead on activity is processed
            result.forEach(l -> l.setLastModified(leadToActivityTimestamp.getOrDefault(l.getId(), l.getLastModified())));

            // Add deleted leads to result
            var deletedLeads = leadIdsToDelete.stream().map(leadId -> {
                EntityData leadToDelete = new EntityData(request.getEntityName());
                leadToDelete.setId(leadId);
                leadToDelete.setConnectorId(request.getConnector().getId());
                leadToDelete.setDeleted(true);
                return leadToDelete;
            }).collect(Collectors.toList());
            result.addAll(deletedLeads);

            boolean hasMoreRecordsToPull = hasMoreCDVActivities || hasMoreAddDeleteActivities;
            MarketoEntityPage leads = new MarketoEntityPage();
            leads.setData(result);
            leads.setHasMore(hasMoreRecordsToPull);
            if(hasMoreRecordsToPull) {
                String token = String.format("%s,%s,%s,%s", prevCDVToken, prevDeleteToken, tokenForCDVActivities, tokenForListAddAndDeletedLeadsActivities);
                leads.setNextPage(token);
            } else {
                leads.setNextPage(null);
            }
            return leads;
        };
        MarketoDataIterator iterator = new MarketoDataIterator(watermark, generator);
        if(request.getWatermark().getPruneState() != null && request.getWatermark().getPruneState().isPruned()) {
            fetchNewToken(request, watermark, iterator, request.getWatermark().getPruneState());
        } else if (StringUtils.isNotBlank(request.getWatermark().getChangeStream())) {
            iterator.setPageToken(request.getWatermark().getChangeStream());
        } else {
            fetchNewToken(request, watermark, iterator);
        }
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private FetchResponse fetchLeadsWithoutStaticListREST(SyncRequest request) {
        WatermarkInfo watermark = request.getWatermark();
        Function2<WatermarkInfo, String, MarketoEntityPage> generator = (wm, pageToken) -> {

            if(StringUtils.isBlank(pageToken)){
                throw new RuntimeException(format("Invalid Page Token: %s", pageToken));
            }
            String[] tokens = pageToken.split(",");
            String prevCDVToken = "INVALID";
            String prevDeleteToken = "INVALID";
            String tokenForCDVActivities =tokens[0];
            String tokenForDeletedLeadsActivities = tokens[1];
            if(tokens.length == 4) {
                prevCDVToken = tokens[0];
                prevDeleteToken = tokens[1];
                tokenForCDVActivities = tokens[2];
                tokenForDeletedLeadsActivities = tokens[3];
            }
            List<EntityData> updatedLeads = new ArrayList<>();
            List<EntityData> deletedLeads = new ArrayList<>();
            Map<String, Long> leadToActivityTimestamp = new HashMap<>();
            boolean hasMoreCDVActivities = !"INVALID".equalsIgnoreCase(tokenForCDVActivities);
            boolean hasMoreDeleteActivities = !"INVALID".equalsIgnoreCase(tokenForDeletedLeadsActivities) && !request.isExcludeDeleted();
            long lastWmCDVActivities = -1l;
            long lastWmDeletedLeadActivities = -1l;
            while (updatedLeads.isEmpty() && deletedLeads.isEmpty() && (hasMoreCDVActivities || hasMoreDeleteActivities)) {
                // fetch leads cdv for mapped fields only
                Set<String> updatedLeadsToFetch = new HashSet<>();
                if (hasMoreCDVActivities) {
                    MarketoEntityPage updatedLeadsInList = retrieveActivities(request, GET_ACTIVITIES_CDV_ENDPOINT, getActiveFields(request.getEntitySchemaWithMappedFields()), tokenForCDVActivities, Optional.empty());
                    for(EntityData data: updatedLeadsInList.getData()){
                        var leadId = data.getValueAsString("leadId");
                        var activityWm = dateUtil.toEpochMilli(data.getValueAsString("activityDate"));
                        // discard activities outside of watermark
                        if(activityWm <= request.getWatermark().getEnd()) {
                            updatedLeadsToFetch.add(leadId);
                            // set the latestActivityTime and use it to set tje lastModifiedTime of leads
                            // we need to set the lastModifiedTime of lead to activityDate because there is no guarantee that
                            // we will see the corresponding activity of lead in future as we perform CDV on subset of fields (only mapped fields)
                            leadToActivityTimestamp.putIfAbsent(leadId, data.getLastModified());
                            leadToActivityTimestamp.put(leadId, Math.max(leadToActivityTimestamp.get(leadId), data.getLastModified()));
                            lastWmCDVActivities = Math.max(lastWmCDVActivities, activityWm);
                        }
                    }
                    // fetch more if
                    // 1. isHasMoreFlag is true
                    // 2. lastWmCDVActivities within the endWm
                    // 3. if records were found in last retrieval - if not this means we have exhausted all records in the given window and records are being filtered out as they are outside of endWm
                    hasMoreCDVActivities = updatedLeadsInList.isHasMore() && lastWmCDVActivities <= watermark.getEnd() && !updatedLeadsToFetch.isEmpty();
                    if(hasMoreCDVActivities) {
                        prevCDVToken = tokenForCDVActivities;
                        tokenForCDVActivities = updatedLeadsInList.getNextPage();
                    } else {
                        tokenForCDVActivities = "INVALID";
                    }
                }

                // fetch activities of type Deleted leads (typeId 37)
                if (hasMoreDeleteActivities) {
                    MarketoEntityPage deletedLeadsActivities = retrieveActivities(request, GET_ACTIVITIES_BY_TYPE_ENDPOINT, List.of(LEAD_DELETE_ACTIVITY_TYPE),
                            tokenForDeletedLeadsActivities, Optional.empty());
                    for (EntityData data : deletedLeadsActivities.getData()) {
                        var leadId = data.getValueAsString("leadId");
                        var activityWm = dateUtil.toEpochMilli(data.getValueAsString("activityDate"));
                        // create deleted lead record
                        EntityData leadToDelete = new EntityData(request.getEntityName());
                        leadToDelete.setId(leadId);
                        leadToDelete.setConnectorId(request.getConnector().getId());
                        leadToDelete.setDeleted(true);
                        leadToDelete.setLastModified(activityWm);
                        deletedLeads.add(leadToDelete);
                        lastWmDeletedLeadActivities = Math.max(lastWmDeletedLeadActivities, activityWm);
                    }
                    hasMoreDeleteActivities = deletedLeadsActivities.isHasMore() && lastWmDeletedLeadActivities <= watermark.getEnd();
                    if(hasMoreDeleteActivities) {
                        prevDeleteToken = tokenForDeletedLeadsActivities;
                        tokenForDeletedLeadsActivities = deletedLeadsActivities.getNextPage();
                    } else {
                        tokenForDeletedLeadsActivities = "INVALID";
                    }
                }

                log.info("Updated Leads Count from Activity: {}. Lead Ids: [{}]", updatedLeadsToFetch.size(),
                        String.join(",", updatedLeadsToFetch));
                log.info("Deleted Leads Count from Activity: {}. Lead Ids: [{}]", deletedLeads.size(),
                        deletedLeads.stream().map(l -> l.getId()).collect(Collectors.joining(",")));

                try {
                    updatedLeads = getById(request.getConnector(), request.getEntityName(), new ArrayList<>(updatedLeadsToFetch),
                            getActiveFields(request.getEntitySchemaWithMappedFields()));
                } catch (NonRetriableException e) {
                    if (ErrorCodes.TOKEN_EXPIRED.name().equalsIgnoreCase(e.getErrorCode())) {
                        AuthConfig updatedAuthConfig = forceRefreshToken(request.getConnector());
                        request.getConnector().setAuthConfig(updatedAuthConfig);
                        updatedLeads = getById(request.getConnector(), request.getEntityName(), new ArrayList<>(updatedLeadsToFetch),
                                getActiveFields(request.getEntitySchemaWithMappedFields()));
                    } else {
                        throw e;
                    }
                }
                // set the lastModifiedTime of each Lead to its corresponding latestActivityWm
                // This is needed to avoid unnecessary lead pruning and also guarantee the lead on activity is processed
                updatedLeads.forEach(l -> l.setLastModified(leadToActivityTimestamp.getOrDefault(l.getId(), l.getLastModified())));
                // prune leads outside of activity wm window
                updatedLeads = pruneLeadsOutsideActivityWm(updatedLeads, lastWmCDVActivities);
                log.info("Last CDV Activity wm: {}, Updated Leads count after Pruning: {}. Lead Ids: [{}]", lastWmCDVActivities, updatedLeads.size(),
                        updatedLeads.stream().map(l -> l.getId()).collect(Collectors.joining(",")));

            }

            // Add updated and deleted leads to result
            List<EntityData> result = new ArrayList<>();
            result.addAll(updatedLeads);
            result.addAll(deletedLeads);
            boolean hasMoreRecordsToPull = hasMoreCDVActivities || hasMoreDeleteActivities;
            MarketoEntityPage leads = new MarketoEntityPage();
            leads.setData(result);
            leads.setHasMore(hasMoreRecordsToPull);
            if(hasMoreRecordsToPull) {
                String token = String.format("%s,%s,%s,%s", prevCDVToken, prevDeleteToken, tokenForCDVActivities, tokenForDeletedLeadsActivities);
                leads.setNextPage(token);
            } else {
                leads.setNextPage(null);
            }
            return leads;
        };
        MarketoDataIterator iterator = new MarketoDataIterator(watermark, generator);
        if(request.getWatermark().getPruneState() != null && request.getWatermark().getPruneState().isPruned()) {
            fetchNewToken(request, watermark, iterator, request.getWatermark().getPruneState());
        } else if (StringUtils.isNotBlank(request.getWatermark().getChangeStream())) {
            iterator.setPageToken(request.getWatermark().getChangeStream());
        } else {
            fetchNewToken(request, watermark, iterator);
        }

        return new FetchResponse(request.getWatermark(), iterator);
    }

    private void fetchNewToken(SyncRequest request, WatermarkInfo watermark, MarketoDataIterator iterator) {
        fetchNewToken(request, watermark, iterator, null);
    }
    private void fetchNewToken(SyncRequest request, WatermarkInfo watermark, MarketoDataIterator iterator, PruneState pruneState) {
        long start = pruneState != null && pruneState.getTimestamp() != 0 ? pruneState.getTimestamp() : request.getWatermark().getStart();
        String activitiesPageToken = getPageToken(request.getConnector(), start);
        // set token for deletedActivities as INVALID if the cycle is running historical sync and no records will be fetched
        String tokenForDeleteActivities = watermark.isInitial() || watermark.isResync() || watermark.isPartialResync() ? "INVALID" : activitiesPageToken;
        iterator.setPageToken(activitiesPageToken + "," + tokenForDeleteActivities);
    }

    private List<EntityData> pruneLeadsOutsideActivityWm(List<EntityData> leads, long latestActivityWm){
        return leads.stream()
                .filter(lead -> lead.getLastModified() <= latestActivityWm || lead.getCreatedAt() <= latestActivityWm)
                .collect(Collectors.toList());
    }

    private FetchResponse getByWatermarkCompanies(SyncRequest request) {
        WatermarkInfo watermark = request.getWatermark();

        String token = getPageToken(request.getConnector(), request.getWatermark().getStart());
        Function2<WatermarkInfo, String, MarketoEntityPage> generator = (wm, pageToken) -> {

            // fetch updated lead in single batch
            var leadActivities = retrieveActivities(request, GET_ACTIVITIES_CDV_ENDPOINT, LEADS_COMPANY_FIELDS, pageToken, Optional.empty());
            List<EntityData> leadActivitiesData = leadActivities.getData();
            var updatedLeads = leadActivitiesData.stream().map(a -> a.getValue("leadId").toString()).collect(Collectors.toSet());
            var leadsWithUpdatedCompanyFields = getById(request.getConnector(), Constants.LEAD.toLowerCase(),
                    new ArrayList<>(updatedLeads), LEADS_COMPANY_FIELDS);
            // check if leadsWithUpdatedCompanyFields have linked company
            var companyIds = leadsWithUpdatedCompanyFields.stream()
                    .filter(l -> l.getValue("contactCompany") != null)
                    .filter(l -> l.getValue("externalCompanyId") != null)
                    .map(l -> l.getValue("contactCompany").toString())
                    .collect(Collectors.toSet());

            var result = getById(request.getConnector(), request.getEntityName(),
                    new ArrayList<>(companyIds), getActiveFields(request.getEntitySchemaWithMappedFields()));

            MarketoEntityPage companies = new MarketoEntityPage();
            companies.setData(result);
            companies.setHasMore(leadActivities.isHasMore());
            companies.setNextPage(leadActivities.isHasMore() ? leadActivities.getNextPage() : null);
            companies.setOffset(leadActivities.getOffset());
            return companies;
        };

        MarketoDataIterator iterator = new MarketoDataIterator(watermark, generator);
        iterator.setPageToken(token);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private FetchResponse getByWatermarkPrograms(SyncRequest request) {
        WatermarkInfo watermark = request.getWatermark();
        Long start = Instant.ofEpochMilli(watermark.getStart()).truncatedTo(ChronoUnit.SECONDS).getEpochSecond();

        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            var results = getProgramsByDateRange(request.getConnector(), request.getWatermark(), request.getEntityName(), offset);
            return Pair.of(Long.valueOf(results.size()), results.stream());
        };

        dbStorageIterator.provisionIfNotExists(request, Constants.MARKETO + request.getEntityName());
        if (watermark.isResync() && start==0) {
            dbStorageIterator.cleanupDB(request);
        }

        int pgSize = (request.getPageSize() <= 0) ? PROGRAM_MAX_PAGESIZE : request.getPageSize();
        DefaultDataIterator iterator = new DefaultDataIterator(watermark, watermark.getOffset(),
                generator, new ArrayList<>(), request.getEntitySchema().getWatermarkField(), pgSize, request.getWatermark().getLimit());
        dbStorageIterator.fetch(request,iterator);
        return dbStorageIterator.getByWatermark(request);
    }

    private String getPgmIdsKey(String entityName) {
        return entityName.toLowerCase()+"_PROGRAM_IDS";
    }

    private FetchResponse getByWatermarkProgramMembership(SyncRequest request) {
        WatermarkInfo watermark = request.getWatermark();
        String pgmIds = request.getSourceParams().getOrDefault(getPgmIdsKey(request.getEntityName()), "").toString();

        Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize, pageToken) -> {

            if(StringUtils.isBlank(pageToken)){
                pageToken = getPageToken(request.getConnector(), watermark.getStart());
            }

            Map<String, Long> programMemberToActivityTimestamp = new HashMap<>();

            long lastActivityWatermark = -1l;

            var leadActivities = retrieveActivities(request, GET_ACTIVITIES_BY_TYPE_ENDPOINT,
                    List.of(PROGRAM_STATUS_CHANGE_ACTIVITY_TYPES), pageToken, Optional.of(pgmIds));
            Set<String> membershipIdsToRetrieve = new HashSet<>();
            for(EntityData activity: leadActivities.getData()){
                String leadId = activity.getValue("leadId").toString();
                String programId = activity.getValue("primaryAttributeValueId").toString();
                String programMembershipId = leadId + "_" + programId;
                var activityWm = dateUtil.toEpochMilli(activity.getValueAsString("activityDate"));

                // discard activities outside of watermark
                // Record the timestamps to and use them to set on the program membership so that there wont be any issue on the sync cycle
                if (activityWm <= watermark.getEnd()){
                    membershipIdsToRetrieve.add(programMembershipId);
                    programMemberToActivityTimestamp.putIfAbsent(programMembershipId, activity.getLastModified());
                    programMemberToActivityTimestamp.put(programMembershipId, Math.max(programMemberToActivityTimestamp.get(programMembershipId), activity.getLastModified()));
                    lastActivityWatermark = Math.max(lastActivityWatermark, activityWm);
                }
            }

            log.info("Retrieving membership data for ids: {}", membershipIdsToRetrieve);
            List<EntityData> membershipData = getProgramMembershipById(request, new ArrayList<>(membershipIdsToRetrieve));
            log.debug("Retrieved {} records:", membershipData.size());

            membershipData.forEach(pm -> pm.setLastModified(programMemberToActivityTimestamp.getOrDefault(pm.getId(), pm.getLastModified())));

            // Prune the results so that the lastmodified is inside the scope
            long finalLastActivityWatermark = lastActivityWatermark;
            List<EntityData> prunedMembershipData = membershipData.stream()
                    .filter(lead -> lead.getLastModified() <= finalLastActivityWatermark)
                    .collect(Collectors.toList());
            log.info("Retrieved Records after pruning using maxActivityWm {}: Ids: {}", lastActivityWatermark, prunedMembershipData.stream().map(d -> d.getId()).collect(Collectors.toList()));

            return new DataWithCursor(pageToken,
                    leadActivities.isHasMore() && lastActivityWatermark <= watermark.getEnd() ? leadActivities.getNextPage(): "",
                    prunedMembershipData);
        };

        DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(watermark,
                watermark.getChangeStream(),
                watermark.getOffset(),
                generator, new ArrayList<>(),
                MAX_QUERY_BATCHSIZE, watermark.getLimit(), true);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private List<EntityData> getProgramsByDateRange(ConnectorInfo connector, WatermarkInfo watermark, String entity, long offset) {
        String munchkin = getMunchkin(connector);
        String url = getHost(munchkin) + PROGRAMS_ENDPOINT + "?earliestUpdatedAt=%s&latestUpdatedAt=%s&maxReturn=%s&offset=%s";

        MarketoRestClient restClient = getRestClient(getJsonConfig(entity, "id"), connector.getId());
        DateFormat format = new SimpleDateFormat(DATE_FORMAT);
        format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
        String startDate = format.format(new Date(watermark.getStart()));
        //String endDate = format.format(new Date(watermark.isInitial()? Instant.now().toEpochMilli(): watermark.getEnd()));
        String endDate = format.format(new Date(watermark.getEnd()));

        var limit = watermark.getLimit() > 0 ? watermark.getLimit() : PROGRAM_MAX_PAGESIZE;
        List<EntityData> results = new ArrayList<>();
        try {
            var result = restClient.get(format(url, startDate, endDate, limit, offset), connector, getTokenHandler(connector));
            result.forEach(r -> {
                r.setId(r.getValueAsString("id"));
                r.setName(entity);
                r.setConnectorId(connector.getId());
                r.setLastModified(parseDate(r.getValueAsString("updatedAt")));
                r.setCreatedAt(parseDate(r.getValueAsString("createdAt")));

            });
            results.addAll(result);

        } catch (PathNotFoundException e){
            log.info("No records at offset {}. All programs retrieved for watermark {}", offset, watermark.toString());
        } catch (ResourceAccessException e){
            throw new RetriableException("IOError", e.getMessage(), "IOError");
        }

        return results;
    }

    private FetchResponse getByWatermarkCustomObjects(SyncRequest request) {
        String munchkin = getMunchkin(request.getConnector());
        String createURL = String.format(getHost(munchkin) + CREATE_BATCHJOB_ENDPOINT, request.getEntitySchema().getApiName());

        MarketoRestClient restClient = new MarketoRestClient(new ObjectMapper(), request.getConnector().getId());

        List<BatchJob> pendingJobs = request.getBatchJobs();
        List<BatchJob> batchJobs = new ArrayList<>();
        if (org.apache.commons.collections.CollectionUtils.isEmpty(pendingJobs) || pendingJobs.size() == 0) {
            log.debug("Submitting new export job for {}", request.getEntitySchema().getApiName());
            BatchJob batchJob = restClient.createAsyncJob(request, createURL, dateUtil);
            if(batchJob.getStatus() != BatchJobStatus.ERROR) {
                String enqueueURL = String.format(getHost(munchkin) + ENQUEUE_BATCHJOB_ENDPOINT, request.getEntitySchema().getApiName(), batchJob.getJobId());
                batchJobs = List.of(restClient.enqueueAsyncJob(request, enqueueURL, batchJob));
            } else {
                batchJobs = List.of(batchJob);
            }
        }

        List<BatchJob> newJobStatuses = pendingJobs.stream()
                .map(pending -> {
                    BatchJob newJob = pending;
                    if (pending.isError()) {
                        //retry failed download;
                        newJob = restClient.createAsyncJob(request, createURL, dateUtil);
                        if(newJob.getStatus() != BatchJobStatus.ERROR) {
                            String enqueueURL = String.format(getHost(munchkin) + ENQUEUE_BATCHJOB_ENDPOINT, request.getEntitySchema().getApiName(), newJob.getJobId());
                            newJob = restClient.enqueueAsyncJob(request, enqueueURL, newJob);
                        }
                    } else if (pending.isPending()) {
                        String getStatusURL = String.format(getHost(munchkin) + GET_BATCHJOB_STATUS, request.getEntitySchema().getApiName(), pending.getJobId());
                        newJob = restClient.queryJobStatus(request, getStatusURL, pending);
                        if (newJob.isCompleted()) {
                            String downloadURL = String.format(getHost(munchkin) + DOWNLOAD_BATCHJOB_RESULT, request.getEntitySchema().getApiName(), pending.getJobId());
                            newJob = restClient.downloadAsyncJobResults(request, downloadURL, newJob);
                        }
                    }
                    newJob.setInternalId(pending.getInternalId());
                    return newJob;
                }).collect(Collectors.toList());

        int pageSize = request.getPageSize() == 0 ? 1000 : request.getPageSize();
        List<CSVStorageIterator> csvStorageIteratorStream = newJobStatuses.stream()
                .filter(j -> j.isCompleted())
                .map(job -> new MarketoCSVIterator(request.getStorage(), job, pageSize, request, true))
                .collect(Collectors.toList());
        FetchResponse fetchResponse = new FetchResponse(request.getWatermark(), new CompositeEntityDataIterator(csvStorageIteratorStream, 0));
        List<BatchJob> allJobs = new ArrayList<>();
        allJobs.addAll(batchJobs);
        allJobs.addAll(newJobStatuses);
        fetchResponse.setBatchJobs(allJobs);
        return fetchResponse;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        /*EntityDataBatchIterator iterator = getByWatermark(request).getIterator();
        while (iterator.hasNext()) {
            List<EntityData> data = iterator.next();
            if (data == null || data.isEmpty())
                break;
            //return parseDate(data.get(0).getValue(request.getEntitySchema().getWatermarkField().getApiName()).toString());
            return data.get(0).getLastModified();
        }*/
        return -1;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        List<String> ids = entityList.stream().map(e -> e.getId()).collect(Collectors.toList());

        // if test limit is specified consider only those many leads
        var idsToProcess = request.getWatermark() != null && request.getWatermark().getLimit() > 0
                ? ids.stream().limit(request.getWatermark().getLimit()).collect(Collectors.toList())
                : ids;

        log.info("Retrieving {} records from {}", idsToProcess.size(), request.getEntityName());
        List<EntityData> result;
        if("programMembership".equalsIgnoreCase(request.getEntityName())){
            result = getProgramMembershipById(request, idsToProcess);
        } else if(Constants.ACTIVITY.equalsIgnoreCase(request.getEntityName())){
            throw new NotSupportedException("getByIds is not supported for entity activity by Marketo "+ request.getEntityName().toLowerCase());
//            result = getActivityById(request, idsToProcess);
        } else if(Constants.PROGRAM.equalsIgnoreCase(request.getEntityName())){
            result = getProgramById(request, idsToProcess);
        }else {
            result = getById(request.getConnector(), request.getEntityName(), idsToProcess,
                    getActiveFields(request.getEntitySchemaWithMappedFields()), request.getEntitySchema().isCustom());
        }
        return result;
    }

    protected List<EntityData> getProgramMembershipById(SyncRequest request, List<String> idsToProcess) {
        String munchkin = getMunchkin(request.getConnector());
        MarketoRestClient restClient = getRestClient(getJsonConfig(request.getEntityName(), "id"), request.getConnector().getId());

        Map<String, List<String>> programToLeadsMap = idsToProcess.stream()
                .map(idStr -> {
                    if(idStr.contains(":")) {
                        return idStr.split(":");
                    } else if(idStr.contains("_")){
                        return idStr.split("_");
                    } else {
                        log.info("Incorrect Program Membership Id {}. It should be in format leadId:programId or leadId_programId", idStr);
                    }
                    return new String[]{idStr};
                }) // program member id can have : or _ as separator between leadId and programId
                .filter(r->r.length==2) //drop everything that doesn't fit the criteria
                .collect(Collectors.groupingBy(pair -> pair[1], Collectors.mapping(pair -> pair[0], Collectors.toList())));
        List<EntityData> records = new ArrayList<>();
        programToLeadsMap.forEach((programId, leadIds) -> {
            List<List<String>> partitions = ListUtils.partition(leadIds, 300);
            for (List<String> partition : partitions) {
                String url = getHost(munchkin) + format(PROGRAM_MEMBERS_ENDPOINT, programId)
                        + format("?filterType=leadId&filterValues=%s&fields=acquiredBy,leadId,membershipDate,programId,reachedSuccess,statusName,updatedAt", String.join(",", partition));
                try {
                    ResponseEntity<String> response = ConnectorHelper.backoffAndThrowOriginalException(() -> restClient.getResponse(url, request.getConnector(), getTokenHandler(request.getConnector())), 1000, 5000, 3, Optional.empty());
                    var result = restClient.getBatchResponse(response);

                    result.forEach(r -> {
                        String id = r.getValueAsString("leadId") + "_" + r.getValueAsString("programId");
                        r.addValue("id", id);
                        r.setId(id);
                        r.setName(request.getEntityName());
                        r.setConnectorId(request.getConnector().getId());
                        r.setLastModified(parseDate(r.getValueAsString("updatedAt")));
                        r.addValue("progressionStatus", r.getValueAsString("statusName"));
                    });
                    records.addAll(result);
                } catch (NonRetriableException | RetriableException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return records;
    }

    //Each id is of the format leadId:activityTypeId
    private List<EntityData> getActivityById(SyncRequest request, List<String> idsToProcess) {
        Map<String, List<String[]>> leadIdsByActivityTypeId = idsToProcess.stream()
                .map(e -> e.split(":"))//split at ":", returns an array [leadId, activityTypeId]
                .map(r -> r.length<2? new String[]{r[0],"*"}: r)//split at ":", returns an array [leadId, activityTypeId], if no activityType specified, append *, to retrieve all supported
                .filter(r->r.length==2)//drop everything that doesn't fit the criteria
                .filter(r-> isSupportedActivityType(r[1]))//drop all activitiestypes that we don't support
                .collect(Collectors.groupingBy(r -> r[1]));
        String activitiesPageToken = getPageToken(request.getConnector(), Instant.EPOCH.toEpochMilli());
        List<EntityData> records = new ArrayList<>();

        leadIdsByActivityTypeId.forEach((activityType, leads) -> {
            List<String> leadIds = leads.stream().map(l -> l[0]).collect(Collectors.toList());
            MarketoEntityPage marketoEntityPage = retrieveActivities(request, GET_LEAD_ACTIVITIES_BY_TYPE_ENDPOINT, "*".equals(activityType)? SUPPORTED_ACTIVITY_TYPES : List.of(activityType), activitiesPageToken, null, leadIds, Optional.empty());
            records.addAll(marketoEntityPage.getData());
        });
        return records;
    }

    private boolean isSupportedActivityType( String activityTypeId) {
        return SUPPORTED_ACTIVITY_TYPES.contains(activityTypeId) || "*".equals(activityTypeId);
    }

    protected List<String> getSupportedActivityTypes(ConnectorInfo connector, EntitySchema entitySchema){
        // selected standard activities + all custom activities
        Set<String> activitiesSupportedInMarketo = getActivityIds(entitySchema);
        List<String> supportedActivities = SUPPORTED_ACTIVITY_TYPES.stream().filter(activitiesSupportedInMarketo::contains).collect(Collectors.toList());
        supportedActivities.addAll(getCustomActivityTypeIds(connector));
        return supportedActivities;
    }

    private Set<String> getActivityIds(EntitySchema entitySchema) {
        Set<String> activityIds = new HashSet<>();
        entitySchema.getAttributes().forEach(attributeSchema -> {
            String apiName = attributeSchema.getApiName();
            // Find the last underscore
            int lastUnderscoreIndex = apiName.lastIndexOf('_');
            if (lastUnderscoreIndex != -1) {
                // Extract substring after the last underscore
                activityIds.add(apiName.substring(lastUnderscoreIndex + 1));
            }
        });
        return activityIds;
    }

    public long removeFromList(String listId, List<Integer> leadIds, ConnectorInfo connector) {
        if (leadIds.isEmpty() || StringUtils.isBlank(listId)) {
            log.info("Leads or List empty {} {}", listId, leadIds);
            return 0;
        }

        String munchkin = getMunchkin(connector);
        MarketoRestClient restClient = new MarketoRestClient(mapper, connector.getId());
        List<List<Integer>> partitions = ListUtils.partition(leadIds, 100);
        long totalCount=0;
        for (List<Integer> partition : partitions) {
            ListMembers listMembers = new ListMembers().setInput(partition.stream().map(i -> new ListMember(i)).collect(Collectors.toList()));
            try {
                String url = getHost(munchkin) + format(STATIC_LIST_ENDPOINT, listId);
                ResponseEntity<String> response = restClient.delete(url, listMembers, connector.getAuthConfig());
                ListResponse deleteResponse = rethrow(()->mapper.readValue(response.getBody(),ListResponse.class));
                totalCount+=deleteResponse.removedFromList();
            } catch (PathNotFoundException e) {
                log.info("List with id {} not found", listId);
            }
        }
        return totalCount;
    }

    public long addToList(String listId, List<Integer> leadIds, ConnectorInfo connector) {
        if (leadIds.isEmpty() || StringUtils.isBlank(listId)) {
            log.info("Leads or List empty {} {}", listId, leadIds);
            return 0;
        }
        String munchkin = getMunchkin(connector);
        MarketoRestClient restClient = new MarketoRestClient(mapper, connector.getId());
        List<List<Integer>> partitions = ListUtils.partition(leadIds, 100);
        long totalCount =0;
        for (List<Integer> partition : partitions) {
            ListMembers listMembers = new ListMembers().setInput(partition.stream().map(i -> new ListMember(i)).collect(Collectors.toList()));
            try {
                String url = getHost(munchkin) + format(STATIC_LIST_ENDPOINT, listId);
                ResponseEntity<String> response = restClient.postRaw(url, rethrow(() -> mapper.writeValueAsString(listMembers)), connector.getAuthConfig());
                ListResponse addResponse = rethrow(() -> mapper.readValue(response.getBody(), ListResponse.class));
                totalCount+=addResponse.addedToList();
            } catch (PathNotFoundException e) {
                log.info("List with id {} not found", listId);
            } catch (ResourceAccessException e) {
                throw new RetriableException("IOError", e.getMessage(), "IOError");
            }
        }
        return totalCount;
    }

    /**
     * Adds a given list of leads to program.
     * Note: Add to Program action is idempotent. Adding a lead to a program again has no impact
     * @param programId - id of a program in which leads need to be added
     * @param leadIds - List of lead ids need to be added to given program
     * @param connector - ConnectorInfo
     * @return - count of all leads successfully added to given program
     */
    public long addToProgram(String programId, List<Integer> leadIds, String programStatus, ConnectorInfo connector) {
        if (leadIds.isEmpty() || StringUtils.isBlank(programId)) {
            log.info("Leads or Program empty {} {}", programId, leadIds);
            return 0;
        }
        String munchkin = getMunchkin(connector);
        MarketoRestClient restClient = new MarketoRestClient(mapper, connector.getId());
        List<List<Integer>> partitions = ListUtils.partition(leadIds, 100);
        long totalCount =0;
        String programName = getProgramName(programId, connector);
        if(StringUtils.isBlank(programName)){
            log.info("Empty Program name for programId {}", programId);
            return 0;
        }
        for (List<Integer> partition : partitions) {
            AddToProgramRequest request = new AddToProgramRequest().setProgramName(programName)
                    .setInput(partition.stream().map(i -> new ProgramMemberInput(i)).collect(Collectors.toList()));
            if(!StringUtils.isBlank(programStatus)){
                request.setProgramStatus(programStatus);
            }
            try {
                String url = getHost(munchkin) + LEAD_PUSH_ENDPOINT;
                ResponseEntity<String> response = restClient.postRaw(url, rethrow(() -> mapper.writeValueAsString(request)), connector.getAuthConfig());
                AddToProgramResponse addResponse = rethrow(() -> mapper.readValue(response.getBody(), AddToProgramResponse.class));
                totalCount += addResponse.addedToProgram();
            } catch (ResourceAccessException e) {
                throw new RetriableException("IOError", e.getMessage(), "IOError");
            }
        }
        return totalCount;
    }

    private List<Integer> extractValidAndExistingLeadIds(ConnectorInfo connector, List<Integer> leadIds) {
        List<EntityData> leads = getById(connector, "lead",
                leadIds.stream().map(String::valueOf).collect(Collectors.toList()), List.of());

        return leads.stream().map(lead -> Integer.valueOf(lead.getId())).collect(Collectors.toList());
    }

    private String getProgramName(String programId, ConnectorInfo connector){
        SyncRequest request = new SyncRequest().setEntitySchema(new EntitySchema(Constants.PROGRAM, "Program"));
        request.setConnector(connector);

        List<EntityData> programs = getProgramById(request, List.of(programId));
        if(programs.isEmpty()){
            log.error("No programs found for id {}", programId);
            return null;
        }
        return programs.get(0).getValueAsString("name");
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        if(request.getEntityName().equalsIgnoreCase(Constants.COMPANY)){
            response = upsertCompanies(request);
        }else if(request.getEntityName().equalsIgnoreCase(Constants.LEAD)){
            // upsertLead - if the lead with email doesn't exist it will create new record else update an existing one
            response = upsertLeads(request, Map.of(
                        Constants.MARKETO_ACTION, (String) request.getDestParams().getOrDefault(Constants.MARKETO_ACTION, Constants.MARKETO_CREATE_ONLY),
                    Constants.MARKETO_LOOK_UP_FIELD, getOrDefaultLookUpFiled(request, "email")));
        } else if(request.getEntityName().equalsIgnoreCase(Constants.PROGRAM)){
            response = createProgram(request);
        } else if(request.getEntityName().equalsIgnoreCase("programMembership")){
            response = createProgramMember(request);
        }else if(request.getEntitySchema().isCustom()){
            response = upsertCustomObjects(request, true);
        }
        var createdRecordIds = response.getResults().stream().filter(r -> r.isSuccess())
                .map(r -> r.getId()).collect(Collectors.toList());
        log.info("Created {} records in {} in {} connector",
                createdRecordIds.size(), request.getEntityName(), request.getConnector().getName());
        return response;
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        SyncResponse response;
        if(request.getEntityName().equalsIgnoreCase(Constants.COMPANY)){
            response = upsertCompanies(request);
        }else if(request.getEntityName().equalsIgnoreCase(Constants.PROGRAM)){
            response = updateProgram(request);
        }else if(request.getEntityName().equalsIgnoreCase("programMembership")){
            response = updateProgramMember(request);
        }else if(request.getEntitySchema().isCustom()){
            response = upsertCustomObjects(request, false);
        }else{
            // upsertLead - if the lead with email doesn't exist it will create new record else update an existing one
            response = upsertLeads(request, Map.of(Constants.MARKETO_ACTION, Constants.MARKETO_UPDATE_ONLY, Constants.MARKETO_LOOK_UP_FIELD, "id"));
        }
        var updatedRecordIds = response.getResults().stream().filter(r -> r.isSuccess())
                .map(r -> r.getId()).collect(Collectors.toList());
        log.info("Updated {} records in {} in {} connector",
                updatedRecordIds.size(), request.getEntityName(), request.getConnector().getName());
        return response;
    }

    private String getOrDefaultLookUpFiled(SyncRequest request, String defaultValue) {
        String changeSetFieldId = (String) request.getDestParams().getOrDefault(Constants.MARKETO_LOOK_UP_FIELD, null);
        if (changeSetFieldId == null) {
            return defaultValue;
        }
        return request.getEntitySchema().getAttributes().stream()
                .filter(attributeSchema -> attributeSchema.getId().equalsIgnoreCase(changeSetFieldId))
                .findFirst()
                .map(AttributeSchema::getApiName).orElse(defaultValue);
    }

    private SyncResponse updateProgram(SyncRequest request) {
        String munchkin = getMunchkin(request.getConnector());
        SyncResponse response = new SyncResponse();
        MarketoRestClient restClient = getRestClient(getJsonConfig(request.getEntityName(), "id"), request.getConnector().getId());

        String url = getHost(munchkin) + PROGRAM_BY_ID_ENDPOINT;
        List<EntityData> toBeUpdated = request.getData().get(request.getConnector().getId());
        toBeUpdated.forEach(program -> {
            List<EntityData> responseObjects = null;
            try{
                MultiValueMap map = new LinkedMultiValueMap();
                program.getValues().forEach((k, v) -> map.add(k, v));
                responseObjects = restClient.postProgram(format(url, program.getId()), map, request.getConnector(),
                        getTokenHandler(request.getConnector()));
                Result result = new Result(true, responseObjects.get(0).getId(), program.getSyncariEntityId());
                response.getResults().add(result);
            } catch (ResourceAccessException e){
                throw new RetriableException("IOError", e.getMessage(), "IOError");
            } catch(RestClientException e) {
                log.error(e.getMessage(), e);
                Result error = new Result(false, null, responseObjects.get(0).getValueAsString("status"));
                error.getErrors().add(e.getMessage());
                response.getResults().add(error);
            }
        });
        return response;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        String munchkin = getMunchkin(request.getConnector());
        if(request.getEntityName().equalsIgnoreCase(Constants.PROGRAM)) {
            return deleteProgram(request);
        } else if(request.getEntityName().equalsIgnoreCase("programMembership")) {
            return deleteProgramMember(request);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        SyncResponse response = new SyncResponse();
        MarketoRestClient restClient = getRestClient(getJsonConfig(request.getEntityName(), "id"), request.getConnector().getId());
        List<EntityData> toBeDeleted = request.getData().get(request.getConnector().getId());

        Map<String, Object> requestBody = new HashMap<>();
        List<List<EntityData>> partitions = Lists.partition(toBeDeleted, getQuerySizeLimit(request.getWatermark()));
        String url;
        if(request.getEntityName().equalsIgnoreCase(Constants.COMPANY)){
            url = getUrl(request.getEntityName(), munchkin, "/rest/v1/%s/delete.json");
        } else {
            url = getUrl(request.getEntityName(), munchkin, request.getEntitySchema().isCustom() ? CUSTOM_OBJECT_DELETE : ENTITY_DATA_ENDPOINT + "?_method=DELETE");
        }
        partitions.forEach( partition -> {
            var objects = new ArrayList<>();
            var idField = request.getEntitySchema().isCustom() ? "marketoGUID" : "id";
            for (EntityData data : partition) {
                objects.add(Map.of(idField, request.getEntitySchema().isCustom() ? data.getId() : Integer.parseInt(data.getId())));
            }
            requestBody.put("input", objects);
            if(request.getEntityName().equalsIgnoreCase(Constants.COMPANY) || request.getEntitySchema().isCustom()) {
                requestBody.put("deleteBy", "idField");
            }
            List<EntityData> responseObjects = null;
            try {
                responseObjects = restClient.postMultiple(url, objectMapper.writeValueAsString(requestBody),
                        request.getConnector(), getTokenHandler(request.getConnector()));
                for(int i = 0; i < responseObjects.size(); i++){
                    Result result = new Result(true, responseObjects.get(i).getId(), partition.get(i).getSyncariEntityId());
                    response.getResults().add(result);
                }
            } catch (ResourceAccessException e){
                throw new RetriableException("IOError", e.getMessage(), "IOError");
            } catch (RestClientException e) {
                log.error(e.getMessage(), e);
                responseObjects.forEach(d -> {
                    Result error = new Result(false, null, d.getValueAsString("status"));
                    error.getErrors().add(e.getMessage());
                    response.getResults().add(error);
                });

            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
        });

        log.info("Deleted {} records from {} in {} connector",
                response.getResults().size(), request.getEntityName(), request.getConnector().getName());

        return response;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {

        String munchkin = getMunchkin(request.getConnector());
        if(Constants.ACTIVITY.equals(request.getEntity())){
            return describeActivity(request);
        }
        if(MarketoSeed.seededEntities.contains(request.getEntity())){
            return Optional.of(MarketoSeed.getSeededEntity(request.getEntity()));
        }
        JsonParserConfig parserConfig;
        if(List.of("company", "opportunity").contains(request.getEntity().toLowerCase())) {
            parserConfig = new JsonParserConfig("result[0].fields", "result[0].fields[{i}]", null,
                    "id", true, "result[0].fields[{i}].__key__");
        }else if (supportedEntities.contains(request.getEntity().toLowerCase())){
            parserConfig = getJsonConfig(request.getEntity(), "id");
        }else {
            // retrieve custom object Schema
            List<EntitySchema> entitySchemas = getCustomObjectSchemasByNames(request.getConnector(), request.getEntity());
            if(!entitySchemas.isEmpty()) return Optional.of(entitySchemas.get(0));
            return Optional.empty();
        }
        MarketoRestClient restClient = getRestClient(parserConfig, request.getConnector().getId());
        EntitySchema entity = new EntitySchema(request.getEntity(), StringUtils.capitalize(request.getEntity()));
        try {
            List<EntityData> result = restClient.get(
                    getUrl(request.getEntity(), munchkin, DESCRIBE_ENTITY_ENDPOINT),
                    request.getConnector(), getTokenHandler(request.getConnector()));
            result.forEach(r -> {
                AttributeSchema attrSchema = retrieveAttribute(request.getEntity(), r);
                if(attrSchema != null) {
                    entity.addField(attrSchema);
                }
            });
        } catch (ResourceAccessException e){
            log.error(e.getMessage(), e);
            throw new RetriableException("IOError", e.getMessage(), "IOError");
        } catch (ConnectorException e){
            log.error(e.getMessage(), e);
            // Skip the describe if CRM sync is enabled for entity
            if("1018".equals(e.getStatusCode())){
                return Optional.empty();
            }
            throw e;
        }
        if ("lead".equalsIgnoreCase(entity.getApiName())) {
            AttributeSchema mergeInCRM = new AttributeSchema(Constants.LEAD_MERGE_IN_CRM, "boolean");
            mergeInCRM.setDisplayName("Merge In CRM");
            entity.getDestParams().add(mergeInCRM);

            AttributeSchema action = new AttributeSchema(Constants.MARKETO_ACTION, "picklist")
                    .setDisplayName("Action")
                    .setInitializable(true)
                    .setUpdateable(true)
                    .setDefaultValue(Constants.MARKETO_CREATE_ONLY)
                    .setPicklist(List.of(
                            new AttributeSchema.Picklist(Constants.MARKETO_CREATE_OR_UPDATE, "Create or Update"),
                            new AttributeSchema.Picklist(Constants.MARKETO_CREATE_ONLY, "Create Only"),
                            new AttributeSchema.Picklist(Constants.MARKETO_UPDATE_ONLY, "Update Only"),
                            new AttributeSchema.Picklist(Constants.MARKETO_CREATE_DUPLICATE, "Create Duplicate")));
            entity.getDestParams().add(action);

            AttributeSchema lookUpField = new AttributeSchema(Constants.MARKETO_LOOK_UP_FIELD, "picklist")
                    .setDisplayName("Lookup field")
                    .setInitializable(true)
                    .setUpdateable(true);

            entity.getDestParams().add(lookUpField);
        }
        return Optional.of(entity);
    }

    private Optional<EntitySchema> describeActivity(DescribeRequest request) {
        String munchkin = getMunchkin(request.getConnector());
        JsonParserConfig parserConfig = getJsonConfig(request.getEntity(), "id");
        MarketoRestClient restClient = getRestClient(parserConfig, request.getConnector().getId());
        List<String> customActivityTypeIds = getCustomActivityTypeIds(request.getConnector());
        EntitySchema entity = MarketoSeed.getSeededEntity(Constants.ACTIVITY);
        boolean moreResult = false;
        String pageToken = "";
        do {
            try {
                ResponseEntity<String> response = restClient.getResponse(
                        getHost(munchkin) + String.format(DESCRIBE_ACTIVITY_ENDPOINT, pageToken),
                        request.getConnector(), getTokenHandler(request.getConnector()));
                List<EntityData> result = restClient.getBatchResponse(response);
                result.forEach(r -> {
                    String activityTypeId = r.getValueAsString("id");
                    if(SUPPORTED_ACTIVITY_TYPES.contains(activityTypeId)
                            || customActivityTypeIds.contains(activityTypeId)) {
                        List<AttributeSchema> attributes = retrieveActivityAttributes(r);
                        attributes.forEach(a -> entity.addField(a));
                    }
                });

                ReadContext ctx = JsonPath.parse(response.getBody());
                moreResult = BooleanUtils.isTrue(ctx.read("moreResult"));
                pageToken = ctx.read("nextPageToken");
            } catch (PathNotFoundException e) {
                moreResult = false;
            } catch (ResourceAccessException e) {
                log.error(e.getMessage(), e);
                throw new RetriableException("IOError", e.getMessage(), "IOError");
            }
        } while (moreResult);

        return Optional.of(entity);
    }

    private List<AttributeSchema> retrieveActivityAttributes(EntityData activityData){
        List<AttributeSchema> attributes = new ArrayList<>();
        String activityTypeName = activityData.getValueAsString("name");
        String activityTypeId = activityData.getValueAsString("id");

        List<Map> listOfAttribMaps = new ArrayList<>();
        Map primaryAttribute = (Map) activityData.getValue("primaryAttribute");
        if (primaryAttribute != null && !primaryAttribute.isEmpty()) {
            listOfAttribMaps.add(primaryAttribute);
        }

        var attribArray = (JSONArray) activityData.getValue("attributes");
        attribArray.forEach(att -> listOfAttribMaps.add((Map) att));
        listOfAttribMaps.forEach(a -> {
            var displayName = a.get("name").toString();
            var apiName = a.get("apiName") == null ? displayName : a.get("apiName").toString();
            var dataType = a.get("dataType").toString();
            attributes.add(createAttr(createActivityAttributesApiName(apiName, activityTypeId),
                    createActivityAttributesDisplayName(displayName, activityTypeName),
                    dataType, false, true, false, false));
        });

        return attributes;
    }

    protected List<String> getCustomActivityTypeIds(ConnectorInfo connectorInfo){
        // TODO: Enable custom activities after additing support for activityTypeIds partitioning support for GET_ACTIVITIES_BY_TYPE_ENDPOINT API call
        //  We are not supporting custom activities at this moment as there is a limit of 10 activities per call in GET_ACTIVITIES_BY_TYPE_ENDPOINT
        //  Enable it once we have added support to partition activityTypeIds in getByWatermarkActivities()
        /*String munchkin = connectorInfo.getMetaConfig().get(MUNCHKIN).toString();
        JsonParserConfig parserConfig = getJsonConfig(Constants.ACTIVITY, "id");
        MarketoRestClient restClient = getRestClient(parserConfig, connectorInfo.getId());
        boolean moreResult = false;
        String pageToken = "";
        List<String> customActivityTypeIds = new ArrayList<>();
        do {
            try {
                ResponseEntity<String> response = restClient.getResponse(
                        getHost(munchkin) + String.format(DESCRIBE_CUSTOM_ACTIVITY_ENDPOINT, pageToken),
                        connectorInfo, getTokenHandler(connectorInfo));
                List<EntityData> result = restClient.getBatchResponse(response);
                result.forEach(r -> {
                    customActivityTypeIds.add(r.getId());
                });

                ReadContext ctx = JsonPath.parse(response.getBody());
                moreResult = BooleanUtils.isTrue(ctx.read("moreResult"));
                pageToken = ctx.read("nextPageToken");
            } catch (PathNotFoundException e) {
                moreResult = false;
            } catch (ResourceAccessException e) {
                log.error(e.getMessage(), e);
                throw new RetriableException("IOError", e.getMessage(), "IOError");
            }
        } while (moreResult);

        return customActivityTypeIds;*/

        return List.of();
    }

    private AttributeSchema retrieveAttribute(String entityName, EntityData entityData){
        String apiName, displayName, dataType;
        boolean readOnly;
        displayName = entityData.getValueAsString("displayName");
        dataType = entityData.getValueAsString("dataType");
        if(entityName.equalsIgnoreCase(Constants.LEAD)){
            Map restAttributes = (HashMap<String, Object>)entityData.getValue("rest");
            if(MapUtils.isEmpty(restAttributes)){
                log.debug("Skipping field {} from entity {} as its missing rest configuration", displayName, entityName);
                return null;
            }
            apiName = restAttributes.get("name").toString();
            readOnly = BooleanUtils.toBoolean(restAttributes.get("readOnly").toString());
        } else {
            apiName = entityData.getValueAsString("name");
            readOnly = !BooleanUtils.toBoolean(entityData.getValueAsString("updateable"));
        }
        boolean isId = "id".equalsIgnoreCase(apiName);
        boolean isRequired = mandatoryFieldsOfEntity.containsKey(entityName) ? mandatoryFieldsOfEntity.get(entityName).contains(apiName) : false;
        boolean isUnique = isId;
        return createAttr(apiName, displayName, dataType, isId, readOnly, isRequired, isUnique);
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> objects = new ArrayList<>();
        // add entities schema available through describe api
        supportedEntities.forEach(e -> {
            DescribeRequest req = new DescribeRequest(request.getConnector(), e);
            Optional<EntitySchema> entity = Optional.empty();
            try{
                entity = describe(req);
                entity.ifPresent(objects::add);
            }catch (NonRetriableException ex){
                log.error("Error describing entity {} ErrCode: {} ErrorMsg:", e, ex.getErrorCode(), ex.getMessage());
                // check if error is not because of CRM Enablement, throw the exception
                if(!ErrorCodes.SCHEMA_ERROR.name().equals(ex.getErrorCode())) {
                    throw ex;
                }
            }
        });

        // add seeded entities
        objects.addAll(MarketoSeed.getAllSeededEntities());

        // Add custom objects - returns no objects if there are no permissions
        objects.addAll(getAllCustomObjectSchemas(request.getConnector()));
        return objects;

        // TODO: Custom Objects
    }

    private List<EntitySchema> getAllCustomObjectSchemas(ConnectorInfo connector){
        // Get list of approved/approvedWithDraft custom objects
        List<String> customObjectStrings = listCustomObjects(connector);

        // Get the individual schemas
        if(!customObjectStrings.isEmpty()){
            return getCustomObjectSchemasByNames(connector, String.join(",", customObjectStrings ));
        }
        return Collections.EMPTY_LIST;
    }

    private List<EntitySchema> getCustomObjectSchemasByNames(ConnectorInfo connector, String names){
        String munchkin = getMunchkin(connector);
        JsonParserConfig parserConfig =  new JsonParserConfig("result", "result[{i}].approved", null,
                "apiName", true, "result[{i}].approved.__key__");
        MarketoRestClient restClient = getRestClient(parserConfig, connector.getId());

        List<EntityData> entities = restClient.get(
                getHost(munchkin) + String.format(DESCRIBE_CUSTOM_OBJECT_ENTITY_ENDPOINT, names),
                connector, getTokenHandler(connector));

        List<EntitySchema> entitySchemas = new ArrayList<>();

        for(EntityData e:entities){
            EntitySchema entitySchema = new EntitySchema(e.getId(), e.getValueAsString("displayName"));
            entitySchema.setCustom(true);
            String idField = "marketoGUID";
            entitySchema.setPluralName(
                    StringUtils.isEmpty(e.getValueAsString("pluralName")) ?
                            StringUtils.capitalize(e.getId()):
                            e.getValueAsString("pluralName"));

            var dedupeFields = (JSONArray) e.getValue("dedupeFields");

            List<String> mandatoryFields = dedupeFields.stream().map(f -> (String)f).collect(Collectors.toList());


            var fieldArray = (JSONArray) e.getValue("fields");
            fieldArray.forEach(f -> {
                var field = (Map) f;
                String apiName, displayName, dataType;
                boolean readOnly;
                apiName = (String)field.get("name");
                displayName = (String)field.get("displayName");
                dataType = (String) field.get("dataType");
                readOnly = !(boolean) (field.get("updateable"));

                boolean isId = idField.equalsIgnoreCase(apiName);
                boolean isRequired = mandatoryFields.contains(apiName);
                boolean isUnique = isId;
                AttributeSchema attr =  createAttr(apiName, displayName, dataType, isId, readOnly, isRequired, isUnique);
                if (attr != null){
                    entitySchema.addField(attr);
                }
            });


            entitySchemas.add(entitySchema);
        }

        return entitySchemas;
    }

    private List<String> listCustomObjects(ConnectorInfo connector) {
        try{
            String munchkin = getMunchkin(connector);
            JsonParserConfig parserConfig =  getJsonConfig("customObjects", "name");
            MarketoRestClient restClient = getRestClient(parserConfig, connector.getId());

            List<EntityData> result = restClient.get(
                    getHost(munchkin) + LIST_CUSTOM_OBJECTS_ENDPOINT,
                    connector, getTokenHandler(connector));

            return result.stream().map(e->e.getId()).collect(Collectors.toList());

        } catch(NonRetriableException nre){

        }
        return Collections.EMPTY_LIST;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        return null;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("Field creation not supported. Please create required field manually in Marketo");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("Field deletion not supported. Please delete required field manually in Marketo");
    }

    private String getHost(String munchkin) {
        return format("https://%s.mktorest.com", munchkin.toLowerCase());
    }

    private String getUrl(String entityName, String munchkin, String path) {
        if (path == null)
            throw new RuntimeException("Path empty for " + entityName);
        return getHost(munchkin) + String.format(path, supportedEntities.contains(entityName) ? objPluralMap.get(entityName) : entityName);
    }

    private JsonParserConfig getJsonConfig(String entity, String idField) {
        return new JsonParserConfig("result", "result[{i}]", null,
                idField, true, "result[{i}].__key__");
    }

    private AttributeSchema createAttr(String apiName, String displayName, String type, boolean isId, boolean readOnly, boolean isRequired, boolean isUnique) {
        AttributeSchema attr = new AttributeSchema();
        attr.setApiName(apiName);
        attr.setDisplayName(displayName);
        attr.setDataType(type);
        if("reference".equals(type) && referenceFieldsMetadata.containsKey(apiName)){
            // set the referenceTo and referenceTargetField
            Reference reference = referenceFieldsMetadata.get(apiName);
            attr.setReferenceTo(reference.getReferenceTo());
            attr.setReferenceTargetField(reference.getReferenceTargetField());
        }
        attr.setIdField(isId);
        attr.setUnique(isUnique);
        attr.setNillable(!isId && !isRequired);
        attr.setUpdateable(!readOnly);
        attr.setSystem(systemFields.contains(apiName));
        attr.setWatermarkField(WATERMARK_FIELD_NAME.equalsIgnoreCase(apiName));
        return attr;
    }

    protected MarketoEntityPage retrieveActivities(SyncRequest request, String endpoint, List<String> fields, String pageToken, Optional<String> assetIds){
        return retrieveActivities(request, endpoint, fields, pageToken, "", List.of(), assetIds);
    }

    protected MarketoEntityPage retrieveActivities(SyncRequest request, String endpoint, List<String> fields, String pageToken,String staticListId, Optional<String> assetIds){
        return retrieveActivities(request, endpoint, fields, pageToken, staticListId, List.of(), assetIds);
    }

    private MarketoEntityPage retrieveActivities(SyncRequest request, String endpoint, List<String> fields,
                                                 String pageToken, String staticListId,List<String> leadIds, Optional<String> assetIds) {

        MarketoEntityPage entityPage = new MarketoEntityPage();
        String munchkin = getMunchkin(request.getConnector());
        MarketoRestClient restClient = getRestClient(getJsonConfig("activities", "leadId"), request.getConnector().getId());

        boolean hasMore = true;
        List<EntityData> result = new ArrayList<>();
        while (hasMore && pageToken != null && result.isEmpty()) {
            String activitiesPath = null;
            if (GET_ACTIVITIES_CDV_ENDPOINT.equals(endpoint)) {
                activitiesPath = String.format(GET_ACTIVITIES_CDV_ENDPOINT, pageToken, String.join(",", fields), staticListId);
            } else if (GET_ACTIVITIES_DELETED_LEADS_ENDPOINT.equals(endpoint)) {
                activitiesPath = String.format(GET_ACTIVITIES_DELETED_LEADS_ENDPOINT, pageToken);
            } else if (GET_ACTIVITIES_BY_TYPE_ENDPOINT.equals(endpoint)) {
                activitiesPath = String.format(GET_ACTIVITIES_BY_TYPE_ENDPOINT, pageToken, String.join(",", fields), staticListId);
                activitiesPath = appendAssetId(assetIds, activitiesPath);
            } else if (GET_LEAD_ACTIVITIES_BY_TYPE_ENDPOINT.equals(endpoint)) {
                activitiesPath = String.format(GET_LEAD_ACTIVITIES_BY_TYPE_ENDPOINT, pageToken, String.join(",", fields), String.join(",", leadIds));
                activitiesPath = appendAssetId(assetIds, activitiesPath);
            }

            ResponseEntity<String> response = null;
            try {
                response = restClient.getResponse(String.format(getHost(munchkin) + activitiesPath),
                        request.getConnector(), getTokenHandler(request.getConnector()));
                ReadContext ctx = JsonPath.parse(response.getBody());
                hasMore = BooleanUtils.isTrue(ctx.read("moreResult"));
                entityPage.setHasMore(hasMore);
                pageToken = ctx.read("nextPageToken");
                entityPage.setNextPage(pageToken);
                try {
                    result = restClient.getBatchResponse(response);
                } catch (PathNotFoundException e) {
                    // if results are not found then set it as empty list of results, there can be more activities in next page
                    log.info("No activities Found at {} for watermark {}", activitiesPath, request.getWatermark());
                }
            } catch (PathNotFoundException e) {
                log.error("Error deserializing JSON Response for activites", e);
                throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e.getMessage());
            } catch (ResourceAccessException e) {
                throw new RetriableException("IOError", e.getMessage(), "IOError");
            }
        }
        long lastWm = -1l;
        for (EntityData r : result) {
            r.setName(request.getEntityName());
            r.setId(r.getValueAsString("id"));
            r.setConnectorId(request.getConnector().getId());
            long lastModified = dateUtil.toEpochMilli(r.getValueAsString("activityDate"));
            r.setLastModified(lastModified);
            r.setCreatedAt(lastModified);
            lastWm = Math.max(lastWm, lastModified);
            String activityTypeId = r.getValueAsString("activityTypeId");
            r.addValue("activityTypeId", activityTypeId);
            r.addValue("leadId", r.getValueAsString("leadId"));
            // convert attributes from JSON array to Map
            Map attribsMap = new HashMap<>();
            var attribArray = (JSONArray) r.getValue("attributes");
            attribArray.forEach(att -> {
                var a = (Map) att;
                attribsMap.put(a.get("name"), a.get("value"));
            });
            r.addValue("attributes", attribsMap);
            // add single level flatten fields from attributes to result
            attribsMap.forEach((key, value) -> {
                // TODO: BACKWARD COMPATIBILITY - Remove this once pipelines are changed to use new attribute fields
                r.addValue(textUtil.createApiName(key.toString()), value);

                var fieldApiName = createActivityAttributesApiName(key.toString(), activityTypeId);
                // only add values for fields which are mapped
                request.getEntitySchemaWithMappedFields().getField(fieldApiName).ifPresent(field -> {
                    r.addValue(fieldApiName, value);
                    if ("datetime".equals(field.getDataType())) {
                        try {
                            Date parsedDate = dateUtil.parseWithTimezone(value.toString(), ACTIVITY_ATTRIBUTE_DATE_FORMAT, CENTRAL_TIME_ZONE);
                            // if parsed correctly replace the field value with actual date
                            r.addValue(fieldApiName, parsedDate);
                        } catch (Exception e) {
                            log.debug(String.format("Unable to parse datetime field {} with value {}", fieldApiName, value), e);
                        }
                    }
                });
            });
        }

        entityPage.setData(result);
        entityPage.setOffset(lastWm);
        return entityPage;
    }

    private static String appendAssetId(Optional<String> assetIds, String activitiesPath) {
        if(assetIds.isPresent() && !StringUtils.isBlank(assetIds.get())) {
            activitiesPath = activitiesPath.concat("&assetIds="+ assetIds.get());
        }
        return activitiesPath;
    }

    private String createActivityAttributesApiName(String attributeName, String activityTypeId){
        return textUtil.createApiName(attributeName).trim() + "_" + activityTypeId;
    }

    private String createActivityAttributesDisplayName(String displayName, String activityTypeName){
        return String.format("%s (%s)", displayName, activityTypeName);
    }

    private List<String> getDeletedLeadIds(SyncRequest request){
        WatermarkInfo watermark = request.getWatermark();
        String pageToken = getPageToken(request.getConnector(), request.getWatermark().getStart());
        String munchkin = getMunchkin(request.getConnector());
        MarketoRestClient restClient = getRestClient(getJsonConfig("activities", "leadId"), request.getConnector().getId());
        boolean moreResult = false;
        List<EntityData> leads = new ArrayList<>();
        do{
            try {
                String activitiesPath = String.format(GET_ACTIVITIES_DELETED_LEADS_ENDPOINT, pageToken);
                ResponseEntity<String> response = restClient.getResponse(String.format(getHost(munchkin) + activitiesPath),
                        request.getConnector(), getTokenHandler(request.getConnector()));
                var result = restClient.getBatchResponse(response);
                // check if activities has read beyond specified watermark end
                boolean isBeyondWatermark = result.stream().anyMatch(l -> {
                    var deletionTimestamp = l.getValueAsString("activityDate") != null
                            ? ZonedDateTime.parse(l.getValueAsString("activityDate")).toEpochSecond() * 1000 : 0L;
                    return deletionTimestamp > watermark.getEnd();
                });
                ReadContext ctx = JsonPath.parse(response.getBody());
                moreResult = BooleanUtils.isTrue(ctx.read("moreResult")) && !isBeyondWatermark;
                pageToken = ctx.read("nextPageToken");
                leads.addAll(result);
            } catch (PathNotFoundException e) {
                // response is empty - parsing caused this error
                log.info("No deleted lead activities Found at watermark {}", request.getWatermark());
                moreResult = false;
            } catch (ResourceAccessException e){
                throw new RetriableException("IOError", e.getMessage(), "IOError");
            }
        } while(moreResult);
        var leadIds = leads.stream().map(a -> a.getValue("leadId").toString())
                .collect(Collectors.toSet());
        return new ArrayList<>(leadIds);
    }

    private List<String> getActiveFields(EntitySchema entity){
        List<AttributeSchema> attributes = entity.getAttributes();
        return attributes.stream().filter(a -> !a.isIdField() && a.getStatus() == Status.ACTIVE).map(a -> a.getApiName()).collect(Collectors.toList());
    }


    protected String getPageToken(ConnectorInfo connector, long epoch) {

        String munchkin = getMunchkin(connector);
        AuthConfig authConfig = connector.getAuthConfig();
        DateFormat format = new SimpleDateFormat(DATE_FORMAT);
        format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
        long startTime = epoch + 1; // we increment time by 1 to make sure startWm sent is not inclusive
        String formattedDate = format.format(new Date(startTime));

        MarketoRestClient restClient = new MarketoRestClient(connector.getId());
        String path = String.format(GET_PAGE_TOKEN_ENDPOINT, formattedDate);
        ResponseEntity<String> response = restClient.getResponse(String.format(getHost(munchkin) + path), connector, getTokenHandler(connector));

        Map responseValues;
        try {
            responseValues = rethrow(()->mapper.readValue(response.getBody(), Map.class));
            if (response.getStatusCode() != HttpStatus.OK || !responseValues.containsKey("nextPageToken")) {
                String msg = format("Error while fetching page token for Marketo: code: %s, details:%s", response.getStatusCode().value(),
                        response.getBody());
                log.error(msg);
                throw new RuntimeException(msg);
            }
        } catch (ResourceAccessException e){
            throw new RetriableException("IOError", e.getMessage(), "IOError");
        }

        return responseValues.get("nextPageToken").toString();
    }

    @Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public SyncResponse upsertLeads(SyncRequest request, Map params){
        String munchkin = getMunchkin(request.getConnector());
        ObjectMapper objectMapper = new ObjectMapper();
        String entity = objPluralMap.get(request.getEntityName());
        SyncResponse response = new SyncResponse();
        MarketoRestClient restClient = getRestClient(getJsonConfig(request.getEntityName(), "id"), request.getConnector().getId());
        List<EntityData> toBeCreated = request.getData().get(request.getConnector().getId());

        Map<String, Object> requestBody = new HashMap<>(params);
        List<List<EntityData>> partitions = Lists.partition(toBeCreated, getQuerySizeLimit(request.getWatermark()));
        String url = getUrl(request.getEntityName(), munchkin, ENTITY_DATA_ENDPOINT);
        partitions.forEach( partition -> {

            Function1<List<EntityData>, List<Result>> updator = (records) -> {
                List<Result> results = new ArrayList<>();
                var objects = new ArrayList<>();
                for (EntityData data : records) {
                    transformData(data, request.getEntitySchema());
                    var leadData = data.getValues();
                    if(data.getId() != null){
                        leadData.put("id", data.getId());
                    }
                    objects.add(leadData);
                }
                requestBody.put("input", objects);
                List<EntityData> responseObjects = null;
                try {
                    String payload = objectMapper.writeValueAsString(requestBody);
                    log.debug("Payload - {}", payload);
                    responseObjects = restClient.postMultiple(url, payload,
                            request.getConnector(), getTokenHandler(request.getConnector()));
                    for(int i = 0; i < responseObjects.size(); i++){
                        Result result = getUpsertResult(responseObjects.get(i));
                        if(!result.isSuccess() && !result.getErrors().isEmpty() && result.getErrors().get(0).contains("Value for required field 'email' not specified")) {
                            log.error("Value for required field 'email' not specified. Payload - {}", requestBody);
                        }
                        result.setSyncariId(partition.get(i).getSyncariEntityId());
                        if (result.getId() == null) {
                            result.setId(partition.get(i).getId());
                        }
                        results.add(result);
                    }
                } catch (ResourceAccessException e){
                    throw new RetriableException("IOError", e.getMessage(), "IOError");
                } catch (RestClientException e) {
                    log.error(e.getMessage(), e);
                    responseObjects.forEach(d -> {
                        Result error = new Result(false, null, d.getValueAsString("status"));
                        error.getErrors().add(e.getMessage());
                        results.add(error);
                    });
                } catch (JsonProcessingException e) {
                    log.error(e.getMessage(), e);
                }

                return results;
            };

            var results = ConnectorHelper.doPayloadAdaptivePost(partition, updator);
            response.getResults().addAll(results);
        });

        return response;
    }

    private SyncResponse upsertCustomObjects(SyncRequest request, boolean insert) {
        String munchkin = getMunchkin(request.getConnector());
        ObjectMapper objectMapper = new ObjectMapper();
        SyncResponse response = new SyncResponse();
        MarketoRestClient restClient = getRestClient(getJsonConfig(request.getEntityName(), "marketoGUID"), request.getConnector().getId());
        List<EntityData> toBeCreated = request.getData().get(request.getConnector().getId());
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("action", insert ? "createOnly" : "updateOnly");
        if(!insert) {
            requestBody.put("dedupeBy", "idField");
        }

        List<List<EntityData>> partitions = Lists.partition(toBeCreated, getQuerySizeLimit(request.getWatermark()));
        String url = getUrl(request.getEntityName(), munchkin, CUSTOM_ENTITY_DATA_ENDPOINT);
        partitions.forEach( partition -> {
            var objects = new ArrayList<>();
            for (EntityData data : partition) {
                transformData(data, request.getEntitySchema());
                var values = data.getValues();
                values.put("marketoGUID", data.getId());
                objects.add(values);
            }
            requestBody.put("input", objects);
            List<EntityData> responseObjects = null;
            try {
                responseObjects = restClient.postMultiple(url, objectMapper.writeValueAsString(requestBody),
                        request.getConnector(), getTokenHandler(request.getConnector()));
                for(int i = 0; i < responseObjects.size(); i++){
                    Result result = getUpsertResult(responseObjects.get(i));
                    result.setSyncariId(partition.get(i).getSyncariEntityId());
                    response.getResults().add(result);
                }
            } catch (ResourceAccessException e){
                throw new RetriableException("IOError", e.getMessage(), "IOError");
            } catch (RestClientException e) {
                log.error(e.getMessage(), e);
                responseObjects.forEach(d -> {
                    Result error = new Result(false, null, d.getValueAsString("status"));
                    error.getErrors().add(e.getMessage());
                    response.getResults().add(error);
                });
            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
        });

        return response;
    }
    private SyncResponse upsertCompanies(SyncRequest request) {
        String munchkin = getMunchkin(request.getConnector());
        ObjectMapper objectMapper = new ObjectMapper();
        SyncResponse response = new SyncResponse();
        MarketoRestClient restClient = getRestClient(getJsonConfig(request.getEntityName(), "id"), request.getConnector().getId());
        List<EntityData> toBeCreated = request.getData().get(request.getConnector().getId());
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("action", "createOrUpdate");
        requestBody.put("dedupeBy", "dedupeFields");

        List<List<EntityData>> partitions = Lists.partition(toBeCreated, getQuerySizeLimit(request.getWatermark()));
        String url = getUrl(request.getEntityName(), munchkin, ENTITY_DATA_ENDPOINT);
        partitions.forEach( partition -> {
            var objects = new ArrayList<>();
            for (EntityData data : partition) {
                objects.add(data.getValues());
            }
            requestBody.put("input", objects);
            List<EntityData> responseObjects = null;
            try {
                responseObjects = restClient.postMultiple(url, objectMapper.writeValueAsString(requestBody),
                        request.getConnector(), getTokenHandler(request.getConnector()));
                for(int i = 0; i < responseObjects.size(); i++){
                    Result result = getUpsertResult(responseObjects.get(i));
                    result.setSyncariId(partition.get(i).getSyncariEntityId());
                    response.getResults().add(result);
                }
            } catch (ResourceAccessException e){
                throw new RetriableException("IOError", e.getMessage(), "IOError");
            } catch (RestClientException e) {
                log.error(e.getMessage(), e);
                responseObjects.forEach(d -> {
                    Result error = new Result(false, null, d.getValueAsString("status"));
                    error.getErrors().add(e.getMessage());
                    response.getResults().add(error);
                });
            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
        });

        return response;
    }

    private SyncResponse createProgramMember(SyncRequest request){
        String munchkin = getMunchkin(request.getConnector());
        List<EntityData> toBeCreated = request.getData().get(request.getConnector().getId());
        return upsertProgramMember(request.getConnector(), request.getEntityName(), munchkin, toBeCreated);
    }

    private SyncResponse updateProgramMember(SyncRequest request){
        String munchkin = getMunchkin(request.getConnector());
        List<EntityData> toBeUpdated = request.getData().get(request.getConnector().getId());
        toBeUpdated.forEach(data -> {
            String idStr = data.getId();
            String[] split = idStr.split("_");
            if(split.length == 2){
                data.addValue("leadId", split[0]);
                data.addValue("programId", split[1]);
            }
        });

        return upsertProgramMember(request.getConnector(), request.getEntityName(), munchkin, toBeUpdated);
    }

    private SyncResponse upsertProgramMember(ConnectorInfo connector, String entityName, String munchkin, List<EntityData> inputs){
        ObjectMapper objectMapper = new ObjectMapper();
        MarketoRestClient restClient = getRestClient(getJsonConfig(entityName, "id"), connector.getId());
        SyncResponse response = new SyncResponse();
        // handle missing programId, leadId and progressionStatus and raise sync error for them
        inputs.forEach(data -> {
            Result error = new Result(false, null, data.getSyncariEntityId());
            if(StringUtils.isBlank(data.getValueAsString("programId"))){
                error.getErrors().add("Program Id cannot be empty");
                response.getResults().add(error);
            } else if(StringUtils.isBlank(data.getValueAsString("leadId"))){
                error.getErrors().add("Lead Id cannot be empty");
                response.getResults().add(error);
            } else if(StringUtils.isBlank(data.getValueAsString("progressionStatus"))){
                error.getErrors().add("Progression Status cannot be empty");
                response.getResults().add(error);
            }
        });
        Map<String, List<EntityData>> recordsByProgramId = inputs.stream()
                .filter(data -> !StringUtils.isBlank(data.getValueAsString("programId")) &&
                        !StringUtils.isBlank(data.getValueAsString("leadId")) &&
                        !StringUtils.isBlank(data.getValueAsString("progressionStatus")))
                .collect(Collectors.groupingBy(data -> data.getValueAsString("programId")));

        recordsByProgramId.forEach((programId, dataList) -> {
            Map<String, List<EntityData>> recordsByStatus = dataList.stream()
                    .collect(Collectors.groupingBy(data -> data.getValueAsString("progressionStatus")));

            recordsByStatus.forEach((status, memberRecords) -> {
                String reqUrl = getUrl(entityName, munchkin, String.format(SYNC_PROGRAM_MEMBER_STATUS_ENDPOINT, programId));
                Lists.partition(memberRecords, MAX_QUERY_BATCHSIZE).forEach(records -> {
                    List leadIds = records.stream()
                            .map(record -> Map.of("leadId", record.getValueAsString("leadId")))
                            .collect(Collectors.toList());
                    Map reqObject = Map.of("statusName", status, "input", leadIds);
                    List<EntityData> responseObjects = null;
                    try {
                        responseObjects = restClient.postMultiple(reqUrl, objectMapper.writeValueAsString(reqObject),
                                connector, getTokenHandler(connector));
                        for(int i = 0; i < responseObjects.size(); i++){
                            Result result = getUpsertResult(responseObjects.get(i));
                            result.setSyncariId(records.get(i).getSyncariEntityId());
                            result.setId(records.get(i).getValueAsString("leadId")+"_"+programId);
                            response.getResults().add(result);
                        }
                    } catch (ResourceAccessException e){
                        throw new RetriableException("IOError", e.getMessage(), "IOError");
                    } catch (NonRetriableException e){
                        log.error(e.getMessage(), e);
                        records.forEach(r -> {
                            Result error = new Result(false, null, r.getSyncariEntityId());
                            error.getErrors().add(e.getMessage());
                            response.getResults().add(error);
                        });
                    } catch (RestClientException e) {
                        log.error(e.getMessage(), e);
                        responseObjects.forEach(d -> {
                            Result error = new Result(false, null, d.getValueAsString("status"));
                            error.getErrors().add(e.getMessage());
                            response.getResults().add(error);
                        });
                    } catch (JsonProcessingException e) {
                        log.error(e.getMessage(), e);
                    }
                });
            });

        });

        return response;
    }

    private SyncResponse deleteProgramMember(SyncRequest request){
        String munchkin = getMunchkin(request.getConnector());
        ConnectorInfo connector = request.getConnector();
        List<EntityData> toBeDeleted = request.getData().get(request.getConnector().getId());
        toBeDeleted.forEach(data -> {
            String idStr = data.getId();
            String[] split = idStr.split("_");
            if(split.length == 2){
                data.addValue("leadId", split[0]);
                data.addValue("programId", split[1]);
            }
        });

        ObjectMapper objectMapper = new ObjectMapper();
        MarketoRestClient restClient = getRestClient(getJsonConfig(request.getEntityName(), "id"), connector.getId());
        SyncResponse response = new SyncResponse();
        Map<String, List<EntityData>> recordsByProgramId = toBeDeleted.stream()
                .filter(data -> data.getValue("programId") != null && data.getValue("leadId") != null)
                .collect(Collectors.groupingBy(data -> data.getValue("programId").toString()));

        recordsByProgramId.forEach((programId, memberRecords) -> {
            Lists.partition(memberRecords, MAX_QUERY_BATCHSIZE).forEach(records -> {
                String reqUrl = getUrl(request.getEntityName(), munchkin, String.format(DELETE_PROGRAM_MEMBER_ENDPOINT, programId));
                List leadIds = records.stream()
                        .map(record -> Map.of("leadId", record.getValueAsString("leadId")))
                        .collect(Collectors.toList());
                Map reqObject = Map.of("input", leadIds);
                List<EntityData> responseObjects = null;
                try {
                    responseObjects = restClient.postMultiple(reqUrl, objectMapper.writeValueAsString(reqObject),
                            connector, getTokenHandler(connector));
                    for(int i = 0; i < responseObjects.size(); i++){
                        Result result = getUpsertResult(responseObjects.get(i));
                        result.setSyncariId(records.get(i).getSyncariEntityId());
                        result.setId(records.get(i).getValueAsString("leadId")+"_"+programId);
                        response.getResults().add(result);
                    }
                } catch (ResourceAccessException e){
                    throw new RetriableException("IOError", e.getMessage(), "IOError");
                } catch (NonRetriableException e){
                    log.error(e.getMessage(), e);
                    records.forEach(r -> {
                        Result error = new Result(false, null, r.getSyncariEntityId());
                        error.getErrors().add(e.getMessage());
                        response.getResults().add(error);
                    });
                } catch (RestClientException e) {
                    log.error(e.getMessage(), e);
                    responseObjects.forEach(d -> {
                        Result error = new Result(false, null, d.getValueAsString("status"));
                        error.getErrors().add(e.getMessage());
                        response.getResults().add(error);
                    });
                } catch (JsonProcessingException e) {
                    log.error(e.getMessage(), e);
                }
            });

        });

        return response;
    }

    private void transformData(EntityData data, EntitySchema schema){
        // handle date field - convert date/datetime objects to string
        data.getValues().forEach((k, v) -> {
            var field = schema.getField(k);
            field.ifPresent( f -> {
                if(f.getDataType().equals("date")){
                    String dateString = DateUtil.format((Date)v, DATE_FORMAT);
                    data.getValues().put(k, dateString);
                } else if(f.getDataType().equals("datetime")){
                    String dateString = DateUtil.format((ZonedDateTime) v, DATE_FORMAT);
                    data.getValues().put(k, dateString);
                }
            });
        });
    }

    private SyncResponse createProgram(SyncRequest request) {
        String munchkin = getMunchkin(request.getConnector());
        SyncResponse response = new SyncResponse();
        MarketoRestClient restClient = getRestClient(getJsonConfig(request.getEntityName(), "id"), request.getConnector().getId());

        String url = getHost(munchkin) + PROGRAMS_ENDPOINT;
        List<EntityData> toBeCreated = request.getData().get(request.getConnector().getId());
        int retryCounter = 0;
        for(int i = 0; i < toBeCreated.size(); i++ ) {
            EntityData program = toBeCreated.get(i);
            List<EntityData> responseObjects = null;
            try{
                var syncariFolder = getOrCreateSyncariFolder(request.getConnector());
                Map folder = Map.of("id", syncariFolder.get("id"), "type", "folder");
                MultiValueMap map = new LinkedMultiValueMap();
                program.getValues().forEach((k, v) -> map.add(k, v));
                map.add("folder", folder); // folder is hardcoded as Syncari folderId
                // type and channel are required fields for creating a program
                //map.add("type", "Default");
                //map.add("channel", "Operational");
                responseObjects = restClient.postProgram(url, map, request.getConnector(),
                        getTokenHandler(request.getConnector()));
                Result result = new Result(true, responseObjects.get(0).getId(), program.getSyncariEntityId());
                response.getResults().add(result);
            } catch (ResourceAccessException e){
                throw new RetriableException("IOError", e.getMessage(), "IOError");
            } catch(RestClientException e) {
                log.error(e.getMessage(), e);
                Result error = new Result(false, null, responseObjects.get(0).getValueAsString("status"));
                error.getErrors().add(e.getMessage());
                response.getResults().add(error);
            } catch (NonRetriableException e){
                log.error(e.getMessage(), e);
                Result error = new Result(false, null, program.getSyncariEntityId());
                error.getErrors().add(e.getMessage());
                response.getResults().add(error);
            } catch (RetriableException e) {
                if(e.getStatusCode().equalsIgnoreCase("MAX_API_RATE_LIMIT") && retryCounter < 5) {
                    i--;
                    retryCounter++;
                } else throw e;
            }
        };
        return response;
    }

    private Result getUpsertResult(EntityData data){
        Result result;
        if(!RECORD_SUCCESS_STATUSES.contains(data.getValueAsString("status"))){
            result = new Result(false, null);
            List reasons = (List) data.getValue("reasons");
            reasons.forEach(r -> {
                Map reasonMap = (Map) r;
                if(reasonMap.get("message") != null) {
                    String errorMessage = reasonMap.get("message").toString();
                    if (errorMessage.contains("not found")) {
                        result.setErrorCode(ErrorCodes.DATA_NOT_FOUND.name());
                    }
                    result.addError(reasonMap.get("message").toString());
                }
            });
        }else {
            result = new Result(true, data.getId(), data.getSyncariEntityId());
        }

        return result;
    }

    private SyncResponse deleteProgram(SyncRequest request) {
        String munchkin = getMunchkin(request.getConnector());
        SyncResponse response = new SyncResponse();
        MarketoRestClient restClient = getRestClient(getJsonConfig(request.getEntityName(), "id"), request.getConnector().getId());

        String url = getHost(munchkin) + PROGRAM_DELETE_ENDPOINT;
        List<EntityData> toBeDeleted = request.getData().get(request.getConnector().getId());
        toBeDeleted.forEach(program -> {
            List<EntityData> responseObjects = null;
            try{
                responseObjects = restClient.postMultiple(format(url, program.getId()), "", request.getConnector(),
                        getTokenHandler(request.getConnector()));
                Result result = new Result(true, responseObjects.get(0).getId(), program.getSyncariEntityId());
                response.getResults().add(result);
            } catch(NonRetriableException nte){
                if(ErrorCodes.DATA_NOT_FOUND.name().equals(nte.getErrorCode())) {
                    log.warn("Skipping the program {} which might be deleted already", program.getId());
                    Result result = new Result(false, program.getId(), program.getSyncariEntityId());
                    result.addError(ErrorCodes.DATA_NOT_FOUND.name());
                    response.getResults().add(result);
                } else {
                    throw nte;
                }
            }
            catch (ResourceAccessException e){
                throw new RetriableException("IOError", e.getMessage(), "IOError");
            } catch(RestClientException e) {
                log.error(e.getMessage(), e);
                Result error = new Result(false, null, responseObjects.get(0).getValueAsString("status"));
                error.getErrors().add(e.getMessage());
                response.getResults().add(error);
            }
        });
        return response;
    }

    protected List<EntityData> getById(ConnectorInfo connector, String entity, List<String> ids, List<String> attributes){
        return getById(connector, entity, ids, attributes, false);
    }

    protected List<EntityData> getById(ConnectorInfo connector, String entity, List<String> ids, List<String> attributes, boolean isCustom){
        String munchkin = getMunchkin(connector);
        String idField = isCustom ? "marketoGUID" : "id";
        String additionalQueryParams = "?filterType="+idField+"&filterValues=%s&fields=%s";
        MarketoRestClient restClient = getRestClient(getJsonConfig(entity, idField), connector.getId());
        List<String> attributesWithCreatedAt = new ArrayList<>(attributes);
        if(!attributesWithCreatedAt.contains("createdAt")){
            attributesWithCreatedAt.add("createdAt");
        }
        String fields = String.join(",", attributesWithCreatedAt);
        List<EntityData> results = new ArrayList<>();
        Lists.partition(ids, MAX_QUERY_BATCHSIZE).forEach(partitionedIds -> {
            String url = getUrl(entity, munchkin, isCustom ? CUSTOM_ENTITY_DATA_ENDPOINT : ENTITY_DATA_ENDPOINT);
            String base = String.format(additionalQueryParams, String.join(",", partitionedIds), "%s"); // placeholders
            List<String> currentFields = new ArrayList<>();
            List<String> finalUrls = new ArrayList<>();
            String finalUrl = null;
            for (String field : attributesWithCreatedAt) {
                currentFields.add(field);
                String testUrl = url + String.format(base, String.join(",", currentFields));

                if (testUrl.length() > MAX_URL_LENGTH) {
                    // Remove last added field and finalize this URL
                    currentFields.remove(currentFields.size() - 1);
                    finalUrl = url + String.format(base, String.join(",", currentFields));
                    finalUrls.add(finalUrl);
                    currentFields = new ArrayList<>();
                    currentFields.add(field);
                    finalUrl = null;
                }
            }
            if (null == finalUrl){
                finalUrl = url + String.format(base, String.join(",", currentFields));
                finalUrls.add(finalUrl);
            }

            for (String urlToUse: finalUrls){
                List<EntityData> result = new ArrayList<>();
                if(!ids.isEmpty()) {
                    try {
                        result = restClient.get(urlToUse, connector, getTokenHandler(connector));
                        result.forEach(r -> {
                            r.setId(r.getValueAsString(idField));
                            r.setName(entity);
                            r.setConnectorId(connector.getId());
                            r.setLastModified(parseDate(r.getValueAsString("updatedAt")));
                            r.setCreatedAt(parseDate(r.getValueAsString("createdAt")));
                            r.getValues().remove(idField);
                        });
                    } catch (ResourceAccessException e){
                        throw new RetriableException("IOError", e.getMessage(), "IOError");
                    }
                }
                results.addAll(result);
            }

        });
        log.info("Retrieved {} {}", results.size(), entity);
        return results;
    }

    private List<EntityData> getProgramById(SyncRequest request, List<String> ids) {
        String munchkin = getMunchkin(request.getConnector());
        MarketoRestClient restClient = getRestClient(getJsonConfig(request.getEntityName(), "id"), request.getConnector().getId());
        List<EntityData> results = new ArrayList<>();
        ids.forEach(id -> {
            try {
                String url = getHost(munchkin) + String.format(PROGRAM_BY_ID_ENDPOINT, id);
                var result = restClient.get(url, request.getConnector(), getTokenHandler(request.getConnector()));
                result.forEach(r -> {
                    r.setId(r.getValueAsString("id"));
                    r.setName(request.getEntityName());
                    r.setConnectorId(request.getConnector().getId());
                    r.setLastModified(parseDate(r.getValueAsString("updatedAt")));
                    r.setCreatedAt(parseDate(r.getValueAsString("createdAt")));
                    r.getValues().remove("id");
                });
                results.addAll(result);
            } catch (PathNotFoundException e){
                log.info("Program with id {} not found", id);
            } catch (ResourceAccessException e){
                throw new RetriableException("IOError", e.getMessage(), "IOError");
            }
        });
        return results;
    }

    protected long parseDate(String dateString){
        if(dateString == null) {
            return 0l;
        }

        // check if datestring has both 'Z' as well as timezone hours
        String toParse = dateString;
        if(dateString.contains("Z+")){
            toParse = dateString.split("\\+")[0];
        }
        return ZonedDateTime.parse(toParse).toEpochSecond() * 1000;
    }

    private int getQuerySizeLimit(WatermarkInfo watermark){
        return watermark != null && watermark.getLimit() > 0 && watermark.getLimit() < MAX_QUERY_BATCHSIZE
                ? watermark.getLimit()
                : MAX_QUERY_BATCHSIZE;
    }

    private Map getOrCreateSyncariFolder(ConnectorInfo connector) {
        String munchkin = getMunchkin(connector);
        MarketoRestClient restClient = getRestClient(getJsonConfig("folder", "id"), connector.getId());
        // check by name if folder exists
        String getFolderUrl = getHost(munchkin) + format("/rest/asset/v1/folder/byName.json?name=%s", MARKETO_FOLDER_NAME);
        try{
            var result = restClient.get(getFolderUrl, connector, getTokenHandler(connector));
            if (result.get(0).getId() != null) {
                return result.get(0).getValues();
            }
        } catch (PathNotFoundException e) {
            log.info("Folder named {} does not exist in Marketo account", MARKETO_FOLDER_NAME);
        } catch (ResourceAccessException e){
            throw new RetriableException("IOError", e.getMessage(), "IOError");
        }

        // get parent folder to create syncari folder in
        getFolderUrl = getHost(munchkin) + format("/rest/asset/v1/folder/byName.json?name=%s", "Active Marketing Programs");
        var parentId = restClient.get(getFolderUrl, connector, getTokenHandler(connector)).get(0).getId();

        // if not then create folder and return details
        log.info("Creating Folder {} in Marketo account", MARKETO_FOLDER_NAME);
        String createFolderUrl = getHost(munchkin) + "/rest/asset/v1/folders.json";
        MultiValueMap folder = new LinkedMultiValueMap();
        folder.add("name", MARKETO_FOLDER_NAME);
        folder.add("parent", Map.of("id", parentId, "type", "folder"));
        folder.add("description", "Folder created by Syncari");
        var result = restClient.postProgram(createFolderUrl, folder, connector, getTokenHandler(connector));
        return result.get(0).getValues();
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of(Constants.LEAD.toLowerCase(), "lead");
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return MarketoSeed.getAttributeMappings(entityApiName);
    }

    public MarketoRestClient getRestClient(JsonParserConfig config, String connectorId){
        return new MarketoRestClient(connectorId, config);
    }

    @Override
    public Supplier<AuthConfig> getTokenHandler(ConnectorInfo connector){
        return () -> forceRefreshToken(connector);
    }

    private String getMunchkin(ConnectorInfo connector){
        return connector.getMetaConfig().get(MUNCHKIN).toString();
    }

    private String getStaticListId(ConnectorInfo connector){
        return isStaticListProvided(connector) ? connector.getMetaConfig().get(STATIC_LIST_ID).toString() : "";
    }
}

@Data
@Accessors(chain = true)
class ListMembers{
    List<ListMember> input=new ArrayList<>();
}

@Data
@Accessors(chain = true)
@AllArgsConstructor
class ListMember{
    int id;
    public ListMember(){
    }
}

@Data
class ListResponse {
    String requestId;
    boolean success;
    List<ListResult> result=List.of();

    public long addedToList(){
        return successCount("added");
    }
    public long removedFromList(){
        return successCount("removed");
    }

    public long successCount(String status){
        return result.stream().filter(r -> status.equals(r.getStatus())).count();
    }
}
@Data
class ListResult{
    String id;
    String status;
}

@Data
@Accessors(chain = true)
class AddToProgramRequest{
    String programName;
    String programStatus;
    String lookupField = "id";
    String source = "Syncari";
    String reason = "Syncari Add To Program Action";
    List<ProgramMemberInput> input=new ArrayList<>();

    public AddToProgramRequest(){

    }

    public AddToProgramRequest(String programName, List<ProgramMemberInput> input){
        this.programName = programName;
        this.input = input;
    }
}

@Data
@Accessors(chain = true)
@AllArgsConstructor
class ProgramMemberInput{
    int id;
    public ProgramMemberInput(){
    }
}

@Data
class AddToProgramResponse {
    String requestId;
    boolean success;
    List<ProgramMemberResult> result=List.of();

    public long addedToProgram(){
        return successCount("updated");
    }

    public long successCount(String status){
        return result.stream().filter(r -> status.equals(r.getStatus())).count();
    }
}

@Data
class ProgramMemberResult{
    String id;
    String status;
}

@Data
@AllArgsConstructor
class Reference{
    String fieldName;
    String referenceTo;
    String referenceTargetField;
}



