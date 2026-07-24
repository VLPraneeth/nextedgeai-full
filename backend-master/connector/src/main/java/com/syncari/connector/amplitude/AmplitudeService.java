package com.syncari.connector.amplitude;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.syncari.connector.*;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.CSVStorageIterator;
import com.syncari.connector.data.iterator.CompositeEntityDataIterator;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.Transformer;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.DateUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.syncari.connector.ConnectorHelper.withBackoffAndErrorHandling;
import static com.syncari.utils.ExceptionUtils.rethrow;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component(Constants.AMPLITUDE)
public class AmplitudeService implements CommonDataService, MetadataService, SynapseInfoService, AuthenticationService {
    public static final String JOB_COMPLETED_STATUS = "JOB COMPLETED";
    public static final String JOB_IN_PROGRESS_STATUS = "JOB INPROGRESS";
    public static final String JOB_STATUS_KEY = "async_status";
    public static final String REQUEST_ID = "request_id";
    public static final String COHORT_ID = "cohort_id";
    public static final String USER_ID = "user_id";
    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DateUtil dateUtil;
    private static final String HOST = "https://amplitude.com";
    private static final String POST_USER = "https://api2.amplitude.com/identify";
    private static final String POST_EVENT = "https://api2.amplitude.com/2/httpapi";
    private static final String LIST_COHORT = "/api/3/cohorts";
    private static final String SUBMIT_COHORT_DOWNLOAD = "/api/5/cohorts/request/%s?props=1";
    private static final String QUERY_COHORT_DOWNLOAD_STATUS = "/api/5/cohorts/request-status/%s";
    private static final String DOWNLOAD_COHORT = "/api/5/cohorts/request/%s/file";
    private static final List<String> SEED_ENTITIES = List.of(AmplitudeSeed.COHORT, AmplitudeSeed.COHORTMEMBERSHIP,
            AmplitudeSeed.EVENT, AmplitudeSeed.USER);
    private static final List<String> supportWriteEntities = List.of("user", "event");

    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<Capability>();
        capabilities.add(Capability.schemaEditInSyncari);
        return capabilities;
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        AuthField api = new AuthField();
        api.setDataType("password");
        api.setName("token");
        api.setLabel("Api Key");
        AuthField secret = new AuthField();
        secret.setDataType("password");
        secret.setName("clientSecret");
        secret.setLabel("Secret Key");
        List<AuthField> fields = new ArrayList<AuthField>();
        fields.add(api);
        fields.add(secret);
        return List.of(new AuthMetadata(AuthType.ApiSecretKey, fields, "Api / Secret Key", ""));
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19202718428564";
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField userFields = new AuthField()
                .setDataType("string")
                .setLabel(i18n("amplitude_user_fields_config_label"))
                .setHelpSummary(i18n("amplitude_user_fields_config_summary"))
                .setName("userFields")
                .setRequired(true);
        AuthField cohorts = new AuthField()
                .setDataType("string")
                .setLabel(i18n("amplitude_cohorts_config_label"))
                .setHelpSummary(i18n("amplitude_cohorts_config_summary"))
                .setName("cohorts")
                .setRequired(true);
        return List.of(ConnectorHelper.getSupportedAuthPicker(),cohorts, userFields);
    }

    @Override
    public String getCategory() {
        return "Analytics";
    }

    @Override
    public String getName() {
        return Constants.AMPLITUDE;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/amplitude.svg")
                .setDisplayName(StringUtils.capitalize(Constants.AMPLITUDE))
                .setBackgroundColor("#F6FDFF")
                .setHelpUrl(helpArticlesBaseUrl + "/360053979172-Amplitude-Setup");
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if (AmplitudeSeed.COHORTMEMBERSHIP.equalsIgnoreCase(request.getEntityName())) {
            return fetchCohortMembership(request);
        } else if (AmplitudeSeed.COHORT.equalsIgnoreCase(request.getEntityName())) {
            return fetchCohorts(request);
        } else {
            throw new UnsupportedOperationException("Can ready from cohorts or memberships");
        }
    }

    private FetchResponse fetchCohorts(SyncRequest request) {
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        ResponseEntity<String> response = withBackoffAndErrorHandling(() -> restClient.getResponse(HOST + LIST_COHORT, request.getConnector().getAuthConfig()));
        Cohorts cohorts = rethrow(() -> mapper.readValue(response.getBody(), Cohorts.class));
        List<EntityData> cohortRecords = cohorts.getCohorts().stream().map(cohort ->
            new EntityData().setConnectorId(request.getConnector().getId())
                    .setName(AmplitudeSeed.COHORT)
                    .setLastModified(cohort.getLastMod())
                    .setId(cohort.getId())
                    .setValues(cohort.toMap())
        ).collect(Collectors.toList());
        return new FetchResponse(request.getWatermark(), new ListBasedIterator(cohortRecords, request.getWatermark()));
    }

    private FetchResponse fetchCohortMembership(SyncRequest request) {
        List<String> userFields = getConfigAsList(request, "userFields");
        List<String> cohortIds = getConfigAsList(request, "cohorts");
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        SyncRequest cohortRequest = new SyncRequest()
                .setEntitySchema(AmplitudeSeed.getSeedEntitySchema(AmplitudeSeed.COHORT,request.getConnector()))
                .setConnector(request.getConnector())
                .setWatermark(new WatermarkInfo(Instant.EPOCH.toEpochMilli(), Instant.now().toEpochMilli(), false, 0));
        FetchResponse cohortResponse = fetchCohorts(cohortRequest);
        List<EntityData> cohorts = cohortResponse.getIterator().next();
        Map<String, String> idToCohortMapping = cohorts.stream().collect(Collectors.toMap(e->e.getId(),e->e.getValueAsString("name")));
        List<BatchJob> pendingJobs = request.getBatchJobs();
        Set<String> pendingCohorts = pendingJobs.stream().filter(j -> j.getJobDetail("cohort_id") != null).map(j -> j.getJobDetailString("cohort_id")).collect(Collectors.toSet());
        log.debug("Pending Cohort ids {}",pendingCohorts);
        List<BatchJob> batchJobs = cohortIds.stream()
                .filter(c -> !pendingCohorts.contains(c))
                .map(cohort -> submitCohortDownload(request, restClient, cohort, userFields))
                .collect(Collectors.toList());

        List<BatchJob> newJobStatuses = pendingJobs.stream()
                .map(pending -> {
                    String cohortId = pending.getJobDetailString("cohort_id");
                    BatchJob newJob = pending;
                    if (pending.isError()) {
                        //retry failed cohort download;
                        newJob = submitCohortDownload(request, restClient, cohortId, userFields);
                    } else if (pending.isPending()) {
                        newJob = queryJobStatus(request, restClient, pending);
                        if (newJob.isCompleted()) {
                            newJob = downloadCohort(request, restClient, newJob);
                        }
                    }
                    newJob.setInternalId(pending.getInternalId());
                    return newJob;
                }).collect(Collectors.toList());

        //TODO: Build an iterator for downloaded files
        int pageSize = request.getPageSize() ==0? 1000 : request.getPageSize();
        List<CSVStorageIterator> csvStorageIteratorStream = newJobStatuses.stream()
                .filter(j -> j.isCompleted())
                .map(job -> new CohortCSVIterator(job.getJobDetailString(COHORT_ID),idToCohortMapping.getOrDefault(job.getJobDetailString(COHORT_ID),""),request.getStorage(), job, pageSize, request, true))
                .collect(Collectors.toList());
        FetchResponse fetchResponse = new FetchResponse(request.getWatermark(), new CompositeEntityDataIterator(csvStorageIteratorStream, 0));
        List<BatchJob> allJobs = new ArrayList<>();
        allJobs.addAll(batchJobs);
        allJobs.addAll(newJobStatuses);
        fetchResponse.setBatchJobs(allJobs);
        return fetchResponse;
    }

    private List<String> getConfigAsList(SyncRequest request, String key) {
        return Arrays.asList(request.getConnector().getMetaConfig().getOrDefault(key,"").toString().split(",")).stream().map(e->e.trim()).collect(Collectors.toList());
    }

    protected String filePath(String requestId){
        return String.format("amplitude/cohorts/%s",requestId);
    }

    private BatchJob downloadCohort(SyncRequest request, SyncariEntityDataRestClient restClient, BatchJob job) {

        String requestId = job.getJobDetailString(REQUEST_ID);
        String cohortId = job.getJobDetailString(COHORT_ID);
        log.info("Downloading cohort {} with request {}",cohortId,requestId);
        BiConsumer<InputStream, MediaType> saveFile = (stream, mediaType) -> {
            String filePath = filePath(requestId);
            request.getStorage().write(stream, filePath);
            job.setContentType(mediaType.toString());
            job.setDownloadedFielURLs(List.of(filePath));
            job.setStatus(BatchJobStatus.COMPLETED);
            log.info("Downloaded cohort {} with request {} to file {}",cohortId,requestId,filePath);
        };

        withBackoffAndErrorHandling(() -> restClient.download
                (String.format(HOST + DOWNLOAD_COHORT, requestId), request.getConnector().getAuthConfig(), saveFile,false));
        return job;
    }

    private BatchJob queryJobStatus(SyncRequest request, SyncariEntityDataRestClient restClient, BatchJob job) {
        String requestId = job.getJobDetailString(REQUEST_ID);
        String cohortId = job.getJobDetailString(COHORT_ID);
        log.info("Querying cohort download job status for request Id {}, cohort id {}",requestId, cohortId);
        ResponseEntity<String> response = withBackoffAndErrorHandling(() -> restClient.getResponse(String.format(HOST + QUERY_COHORT_DOWNLOAD_STATUS, requestId)
                , request.getConnector().getAuthConfig()));
        log.info("Query Response for cohort download job status for request Id {}, cohort id {} {} ",requestId, cohortId, response.getBody());
        Map<String, Object> jobResponse = rethrow(() -> mapper.readValue(response.getBody(), Map.class));
        BatchJob batchJob = new BatchJob();
        batchJob.setConnectorId(request.getConnector().getId());
        batchJob.setExternalEntityName(request.getEntityName());
        batchJob.setJobId(jobResponse.getOrDefault(REQUEST_ID, "").toString());
        batchJob.setJobDetails(jobResponse);
        Object jobStatus = jobResponse.get(JOB_STATUS_KEY);
        if (JOB_COMPLETED_STATUS.equals(jobStatus)) {
            batchJob.setStatus(BatchJobStatus.COMPLETED);
        } else if (JOB_IN_PROGRESS_STATUS.equals(jobStatus)) {
            batchJob.setStatus(BatchJobStatus.PENDING);
        }
        return batchJob;
    }

    private BatchJob submitCohortDownload(SyncRequest request, SyncariEntityDataRestClient restClient, String cohortId, List<String> userFields) {
        String propKeys = userFields.stream().map(u -> String.format("propKeys=%s", u)).reduce((k1, k2) -> String.format("%s&%s", k1, k2)).orElse(null);
        log.info("Submitting cohort download request for cohort {}",cohortId);
        ResponseEntity<String> response = withBackoffAndErrorHandling(() -> {
            String url = String.format(HOST + SUBMIT_COHORT_DOWNLOAD, cohortId) + (StringUtils.isBlank(propKeys)?"":"&"+propKeys);
            return restClient.getResponse(url, request.getConnector().getAuthConfig());
        });
        Map<String, Object> jobResponse = rethrow(() -> mapper.readValue(response.getBody(), Map.class));
        BatchJob batchJob = new BatchJob();
        batchJob.setConnectorId(request.getConnector().getId());
        batchJob.setExternalEntityName(request.getEntityName());
        batchJob.setJobId(jobResponse.getOrDefault(REQUEST_ID, "").toString());
        batchJob.setJobDetails(jobResponse);
        batchJob.setStatus(BatchJobStatus.PENDING);
        return batchJob;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        DescribeAllRequest req = new DescribeAllRequest(request.getConnector(), List.of());
        return describeAll(req).stream().filter(e -> e.getApiName().equalsIgnoreCase(request.getEntity())).findFirst();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> schemaList = new ArrayList<>();
        SEED_ENTITIES.forEach(e -> {
            schemaList.add(AmplitudeSeed.getSeedEntitySchema(e,request.getConnector()));
        });
        return schemaList;
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();
        try {
            if (StringUtils.isBlank(config.getAuthConfig().getToken())) {
                throw new RuntimeException(i18n("api_key_required"));
            }
            if (StringUtils.isBlank(config.getAuthConfig().getClientSecret())) {
                throw new RuntimeException(i18n("secret_key_required"));
            }
            listCohort(new SyncRequest().Builder(config, new EntitySchema("cohort")));
        } catch (Exception e) {
            result.setMessage(e.getMessage());
            result.setCode(HttpStatus.UNAUTHORIZED.name());
        }
        return result;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return -1;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("amplitude does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("amplitude does not support delete field");
    }


    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        throw new NotSupportedException("amplitude does not support getbyids");
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        if (!supportWriteEntities.contains(request.getEntityName().toLowerCase())) {
            throw new RuntimeException("Create entity " + request.getEntityName() + " not supported in Amplitude");
        }
        try {
            switch (request.getEntityName().toLowerCase()) {
            case "user":
                return upsertUsers(request);
            case "event":
                return createEvents(request);

            default:
                break;
            }
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            response.setSuccess(false);
            response.setErrors(List.of(e.getMessage()));
        }
        return response;
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return create(request);
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        return response;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in amplitude yet");
    }

    @Override
    public void deleteObject(DeleteObjectRequest request) {

    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    private void listCohort(SyncRequest request) {
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        ResponseEntity<String> response = restClient.getResponse(HOST + LIST_COHORT, request.getConnector().getAuthConfig());
        JsonPath.parse(response.getBody());
    }

    private JsonParserConfig getSingleJsonConfig(String plural) {
        return new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
    }

    @Data
    public static class Cohorts {
        private List<Cohort> cohorts;
    }

    @Data
    public static class Cohort {
        long lastComputed;
        List<String> owners;
        String description;
        boolean published;
        boolean archived;
        String name;
        String appId;
        long lastMod;
        String type;
        String id;
        int size;

        public Map<String, Object> toMap() {
            HashMap<String, Object> cohort = new HashMap<>();
            cohort.put("lastComputed", lastComputed);
            cohort.put("owners", owners);
            cohort.put("description", description);
            cohort.put("published", published);
            cohort.put("archived", archived);
            cohort.put("name", name);
            cohort.put("appId", appId);
            cohort.put("lastMod", lastMod);
            cohort.put("id", id);
            cohort.put("type", type);
            cohort.put("size", size);
            return cohort;
        }
    }

    private SyncResponse upsertUsers(SyncRequest request) throws JsonProcessingException {
        SyncResponse response = new SyncResponse();
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        List<AttributeSchema> attributes = request.getEntitySchema().getAttributes();
        List<EntityData> list = request.getData().get(request.getConnector().getId());
        List<Map<String, Object>> body = new ArrayList<>();
        list.forEach(item -> {
            //update has id set
            Object userIds = item.getId();
            if(userIds==null){
                //if no id, use user_ids from the user_id field
                userIds = item.getValue(AmplitudeSeed.USER_ID);
            }
            List<Object> userIdList = List.class.isAssignableFrom(userIds.getClass()) ? (List<Object>) userIds : Arrays.asList(userIds.toString().split(","));
            List<String> collectedUserIds = new ArrayList<>();
            for(Object userId : userIdList) {
                Map<String, Object> payload = new HashMap<String, Object>();
                Map<String, Object> userProperties = new HashMap<String, Object>();
                Map<String, Object> groupProperties = new HashMap<String, Object>();
                attributes.forEach(a -> {
                    if (isChildOf(a, AmplitudeSeed.USER_PROPERTIES, request.getEntitySchema())) {
                        userProperties.put(a.getApiName(), item.getValue(a.getApiName()));
                    } else if (isChildOf(a, AmplitudeSeed.GROUPS, request.getEntitySchema())) {
                        groupProperties.put(a.getApiName(), item.getValue(a.getApiName()));
                    } else {
                        payload.put(a.getApiName(), item.getValue(a.getApiName()));
                    }
                });
                if (!userProperties.isEmpty()) {
                    payload.put(AmplitudeSeed.USER_PROPERTIES, userProperties);
                }
                if (!groupProperties.isEmpty()) {
                    payload.put(AmplitudeSeed.GROUPS, userProperties);
                }
                collectedUserIds.add(userId.toString());
                payload.put(AmplitudeService.USER_ID,userId);
                body.add(payload);
            }
            Result result = new Result(true, String.join(",",collectedUserIds), item.getSyncariEntityId());
            response.getResults().add(result);
        });
        StringBuilder req = new StringBuilder();
        if (!StringUtils.isBlank(request.getConnector().getAuthConfig().getToken())) {
            req = req.append("api_key=").append(request.getConnector().getAuthConfig().getToken()).append("&");
        }
        req = req.append("identification=" + mapper.writeValueAsString(body));
        ResponseEntity<String> resp = restClient.postRaw(POST_USER, req.toString(), request.getConnector().getAuthConfig());
        log.debug(resp.getBody());
        if(!"success".equalsIgnoreCase(resp.getBody())) {
            throw new RuntimeException(resp.getBody());
        }
        response.setSuccess(true);
        return response;
    }
    
    private SyncResponse createEvents(SyncRequest request) throws IOException {
        SyncResponse response = new SyncResponse();
        SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
        List<AttributeSchema> attributes = request.getEntitySchema().getAttributes();
        List<EntityData> list = request.getData().get(request.getConnector().getId());
        List<Map<String, Object>> body = new ArrayList<>();
        list.forEach(item -> {
            Object userIds = item.getValue(AmplitudeSeed.USER_ID);
            List<Object> userIdList = List.class.isAssignableFrom(userIds.getClass()) ? (List<Object>) userIds : Arrays.asList(userIds.toString().split(","));
            List<String> eventIds = new ArrayList<>();
            for(Object userId: userIdList){
                Map<String, Object> payload = new HashMap<String, Object>();
                Map<String, Object> userProperties = new HashMap<String, Object>();
                Map<String, Object> eventProperties = new HashMap<String, Object>();
                // generate a syncari id to identify the event
                String id = UUID.randomUUID().toString();
                eventProperties.put("syncari_id", id);
                attributes.forEach(a -> {
                    if(isChildOf(a, AmplitudeSeed.USER_PROPERTIES, request.getEntitySchema())) {
                        userProperties.put(a.getApiName(), item.getValue(a.getApiName()));
                    } else if(isChildOf(a, AmplitudeSeed.EVENT_PROPERTIES, request.getEntitySchema())) {
                        eventProperties.put(a.getApiName(), item.getValue(a.getApiName()));
                    } else {
                        payload.put(a.getApiName(), item.getValue(a.getApiName()));
                    }
                });
                if(!userProperties.isEmpty()) {
                    payload.put(AmplitudeSeed.USER_PROPERTIES, userProperties);
                }
                if(!eventProperties.isEmpty()) {
                    payload.put(AmplitudeSeed.EVENT_PROPERTIES, eventProperties);
                }
                payload.put(AmplitudeSeed.USER_ID,userId);
                body.add(payload);
                eventIds.add(id);
            }
            Result e = new Result(true, String.join(",",eventIds), item.getSyncariEntityId());
            response.getResults().add(e);
        });
        StringBuilder req = new StringBuilder();
        Map events = new HashMap();
        events.put("events", body);
        if (!StringUtils.isBlank(request.getConnector().getAuthConfig().getToken())) {
            events.put("api_key", request.getConnector().getAuthConfig().getToken());
        }
        req = req.append(mapper.writeValueAsString(events));
        ResponseEntity<String> resp = restClient.postRaw(POST_EVENT, req.toString(), request.getConnector().getAuthConfig());
        log.debug(resp.getBody());
        Map respMap = mapper.readValue(resp.getBody(), Map.class);
        if(!"200".equalsIgnoreCase(respMap.get("code").toString())) {
            throw new RuntimeException(resp.getBody());
        }
        response.setSuccess(true);
        return response;
    }

    private boolean isChildOf(AttributeSchema child, String field, EntitySchema entity) {
        return child.getParentAttributeId() != null && entity.getField(field).get().getId().equalsIgnoreCase(child.getParentAttributeId());
    }

}


