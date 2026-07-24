package com.syncari.connector.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.BatchJob;
import com.syncari.connector.data.BatchJobStatus;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.QuotaExceededException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.utils.DateUtil;
import com.syncari.utils.CsvUtils;
import com.syncari.utils.CSVOptions;
import com.syncari.utils.Storage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.syncari.connector.ConnectorHelper.withBackoffAndErrorHandling;
import static com.syncari.utils.ExceptionUtils.rethrow;

@Slf4j
@Data
public class MarketoRestClient extends SyncariEntityDataRestClient {

    public static final String batchJobDateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    String connectorId;

    private static final int READ_TIMEOUT = 30000;
    private static final Map<String, String> errorCodes = Map.ofEntries(
            Map.entry("CRM_SYNC_ENABLED", "1018"),
            Map.entry("INVALID_TOKEN", "601"),
            Map.entry("MAX_API_RATE_LIMIT", "606"),
            Map.entry("MAX_API_DAILY_LIMIT", "607"),
            Map.entry("CONCURRENT_ACCESS_LIMIT", "615"),
            Map.entry("REQUEST_TIMED_OUT", "604"),
            Map.entry("TOKEN_EXPIRED", "602"),
            Map.entry("TRANSIENT_ERROR", "713"),
            Map.entry("API_TEMP_UNAVAILABLE", "608"),
            Map.entry("DATA_NOT_FOUND", "702"),
            Map.entry("SYSTEM_ERROR", "611")
    );

    public MarketoRestClient(String connectorId){
        super();
        this.connectorId = connectorId;
    }

    public MarketoRestClient(String connectorId, JsonParserConfig parserConfig){
        super(parserConfig);
        this.connectorId = connectorId;
    }

    public MarketoRestClient(ObjectMapper mapper, String connectorId){
        super(null, mapper);
        this.connectorId = connectorId;
    }

    public MarketoRestClient(){
        super();
    }

    public List<EntityData> get(String url, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        try{
            return get(url, connector.getAuthConfig());
        } catch (NonRetriableException e){
            if(ErrorCodes.TOKEN_EXPIRED.name().equals(e.getErrorCode()) && tokenHandler != null) {
                AuthConfig updatedAuth = tokenHandler.get();
                connector.setAuthConfig(updatedAuth);
                return get(url, updatedAuth);
            }
            throw e;
        }
    }

    public ResponseEntity<String> getResponse(String url, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler, Object... uriArgs) {
        try{
            ResponseEntity<String> response = getResponse(url, connector.getAuthConfig(), uriArgs);
            checkResponse(response);
            return response;
        } catch (NonRetriableException e){
            if(ErrorCodes.TOKEN_EXPIRED.name().equals(e.getErrorCode()) && tokenHandler != null) {
                AuthConfig updatedAuth = tokenHandler.get();
                connector.setAuthConfig(updatedAuth);
                return getResponse(url, updatedAuth, uriArgs);
            }
            throw e;
        }
    }

    public List<EntityData> postMultiple(String url, String body, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        try {
            return doPostMultiple(url, body, connector);
        } catch (NonRetriableException e){
            if(ErrorCodes.TOKEN_EXPIRED.name().equals(e.getErrorCode()) && tokenHandler != null) {
                AuthConfig updatedAuth = tokenHandler.get();
                connector.setAuthConfig(updatedAuth);
                return doPostMultiple(url, body, connector);
            }
            throw e;
        }
    }

    public List<EntityData> postProgram(String url, MultiValueMap body, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        try {
            return doPostProgram(url, body, connector);
        } catch (NonRetriableException e){
            if(ErrorCodes.TOKEN_EXPIRED.name().equals(e.getErrorCode()) && tokenHandler != null) {
                AuthConfig updatedAuth = tokenHandler.get();
                connector.setAuthConfig(updatedAuth);
                return doPostProgram(url, body, connector);
            }
            throw e;
        }
    }

    @Override
    public void checkResponse(ResponseEntity<String> response) {

        super.checkResponse(response);
        // Marketo's response status is 200 even in case of failure.
        ReadContext ctx = JsonPath.parse(response.getBody());
        boolean isSuccess = ctx.read("success");
        if(!isSuccess){
            JSONArray errors = ctx.read("errors");
            Map<String, String> error = errors.isEmpty()
                    ? Map.of("code", "Unknown", "message", "Unknown Error")
                    : ctx.read("errors[0]");

            // handle individual error codes as needed
            log.error("Marketo ErrorCode:{}, ErrorMessage:{}", error.get("code"), error.get("message"));
            if(error.get("code").equals(errorCodes.get("TOKEN_EXPIRED")) || error.get("code").equals(errorCodes.get("INVALID_TOKEN"))){
                throw new NonRetriableException(ErrorCodes.TOKEN_EXPIRED.name(), error.get("message"), error.get("code"));
            } else if(error.get("code").equals(errorCodes.get("REQUEST_TIMED_OUT"))){
                throw new RetriableException(ErrorCodes.TIME_OUT.name(), error.get("message"), error.get("code"));
            } else if(error.get("code").equals(errorCodes.get("SYSTEM_ERROR"))){
                throw new RetriableException(ErrorCodes.SYSTEM_ERROR.name(), error.get("message"), error.get("code"));
            } else if(error.get("code").equals(errorCodes.get("MAX_API_RATE_LIMIT"))){
                log.error("Marketo API rate limit exceeded. Scheduling retry after 25 seconds");
                throw new QuotaExceededException(ErrorCodes.TOO_MANY_REQUESTS.name(), error.get("message"), error.get("code"), connectorId, 25);
            } else if(error.get("code").equals(errorCodes.get("DATA_NOT_FOUND"))){
                throw new NonRetriableException(ErrorCodes.DATA_NOT_FOUND.name(), error.get("message"), error.get("code"));
            } else if(error.get("code").equals(errorCodes.get("MAX_API_DAILY_LIMIT"))){
                long tryAfterSeconds = DateUtil.getTodayEndWithTimezone(ZoneId.of(DateUtil.CENTRAL_TIME_ZONE)).getEpochSecond() - Instant.now().getEpochSecond();
                log.error("Marketo daily api limit exceeded. Scheduling next run after {} seconds", tryAfterSeconds);
                throw new QuotaExceededException(ErrorCodes.TOO_MANY_REQUESTS.name(), error.get("message"), error.get("code"), connectorId, tryAfterSeconds);
            } else if(error.get("code").equals(errorCodes.get("CONCURRENT_ACCESS_LIMIT"))){
                log.error("Marketo Concurrent api access exceeded. Scheduling retry after 25 seconds");
                throw new QuotaExceededException(ErrorCodes.TOO_MANY_REQUESTS.name(), error.get("message"), error.get("code"), connectorId, 25);
            } else if(error.get("code").equals(errorCodes.get("CRM_SYNC_ENABLED"))){
                throw new NonRetriableException(ErrorCodes.SCHEMA_ERROR.name(), error.get("message"), error.get("code"));
            } else if(error.get("code").equals(errorCodes.get("TRANSIENT_ERROR")) || error.get("code").equals(errorCodes.get("API_TEMP_UNAVAILABLE"))){
                throw new RetriableException(ErrorCodes.API_ERROR.name(), error.get("message"), error.get("code"));
            }

            throw new NonRetriableException(error.get("code"), error.get("message"), error.get("code"));
        }

    }

    
    private List<EntityData> doPostMultiple(String url, String body, ConnectorInfo connector) {
        RestTemplate restTemplate = getTemplate();
        ResponseEntity<String> response = withBackoffAndErrorHandling(()-> restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity(body, getHeaders(connector.getAuthConfig())), String.class));
        log.info("HTTP Status {}",response.getStatusCode());
        log.debug("HTTP Response {}", response.getBody());
        return getBatchResponse(response);
    }
    
    private List<EntityData> doPostProgram(String url, MultiValueMap body, ConnectorInfo connector) {
        RestTemplate restTemplate = getTemplate();
        HttpHeaders header = getHeaders(connector.getAuthConfig());
        log.info("POST URL: {}", url);
        log.debug("POST Body: {}", body);
        // Program post request accept Content-Type: application/x-www-form-urlencoded
        header.set("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        ResponseEntity<String> response = withBackoffAndErrorHandling(()->restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity(body, header), String.class));
        log.info("HTTP Status {}",response.getStatusCode());
        log.debug("HTTP Response {}", response.getBody());
        return getBatchResponse(response);
    }

    public BatchJob createAsyncJob(SyncRequest request, String url, DateUtil dateUtil) {
        String body = createBatchJobPayload(request, dateUtil);
        ResponseEntity<String> response = withBackoffAndErrorHandling(() -> postRaw(url, body, request.getConnector().getAuthConfig()));
        Map<String, Object> jobResponse = rethrow(() -> objectMapper.readValue(response.getBody(), Map.class));
        String jobId = "";
        BatchJobStatus status = getBatchJobStatus(jobResponse);

        if (status != BatchJobStatus.ERROR) {
            Object resultObj = jobResponse.get("result");
            if (resultObj instanceof List && !((List)resultObj).isEmpty()) {
                Map<String, Object> result = (Map<String, Object>) ((List)resultObj).get(0);
                Object exportIdObj = result.get("exportId");
                if (exportIdObj instanceof String) {
                    jobId = (String) exportIdObj;
                }
            }
        }
        if(StringUtils.isBlank(jobId)) {
            status = BatchJobStatus.ERROR;
        }
        BatchJob batchJob = new BatchJob();
        batchJob.setConnectorId(request.getConnector().getId());
        batchJob.setExternalEntityName(request.getEntityName());
        batchJob.setJobId(jobId);
        batchJob.setJobDetails(jobResponse);
        batchJob.setStatus(status);
        return batchJob;
    }

    public BatchJob enqueueAsyncJob(SyncRequest request, String url, BatchJob job) {
        ResponseEntity<String> response = withBackoffAndErrorHandling(() -> postRaw(url, "", request.getConnector().getAuthConfig()));
        Map<String, Object> jobResponse = rethrow(() -> objectMapper.readValue(response.getBody(), Map.class));
        BatchJobStatus status = getBatchJobStatus(jobResponse);

        BatchJob batchJob = new BatchJob();
        batchJob.setConnectorId(request.getConnector().getId());
        batchJob.setExternalEntityName(request.getEntityName());
        batchJob.setJobId(job.getJobId());
        batchJob.setJobDetails(jobResponse);
        batchJob.setStatus(status);
        return batchJob;
    }

    private String createBatchJobPayload(SyncRequest request, DateUtil dateUtil) {
        String startDate = checkDateWithinLast31Days(request.getWatermark().getStart());
        String endDate = dateUtil.formatDate(Instant.ofEpochMilli(request.getWatermark().getEnd()), batchJobDateFormat);

        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("format", "csv");
        jsonMap.put("fields", request.getEntitySchema().getAttributes().stream().map(AttributeSchema::getApiName).collect(Collectors.toList()));

        Map<String, Object> filterMap = new HashMap<>();
        Map<String, Object> updatedAtMap = new HashMap<>();
        updatedAtMap.put("startAt", startDate);
        updatedAtMap.put("endAt", endDate);
        filterMap.put("updatedAt", updatedAtMap);

        jsonMap.put("filter", filterMap);
        try {
            return objectMapper.writeValueAsString(jsonMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public BatchJob queryJobStatus(SyncRequest request, String getStatusURL, BatchJob job) {
        ResponseEntity<String> response = withBackoffAndErrorHandling(() -> getResponse(getStatusURL, request.getConnector().getAuthConfig()));
        Map<String, Object> jobResponse = rethrow(() -> objectMapper.readValue(response.getBody(), Map.class));
        BatchJobStatus status = getBatchJobStatus(jobResponse);
        BatchJob batchJob = new BatchJob();
        batchJob.setConnectorId(request.getConnector().getId());
        batchJob.setExternalEntityName(request.getEntityName());
        batchJob.setJobId(job.getJobId());
        batchJob.setJobDetails(jobResponse);
        batchJob.setStatus(status);
        return batchJob;
    }

    private static BatchJobStatus getBatchJobStatus(Map<String, Object> jobResponse) {
        BatchJobStatus status;
        boolean isSuccess = (boolean) jobResponse.getOrDefault("success", false);
        if (!isSuccess) {
            status = BatchJobStatus.ERROR;
        } else {
            Object resultObj = jobResponse.get("result");
            if (resultObj instanceof List && !((List)resultObj).isEmpty()) {
                Map<String, Object> result = (Map<String, Object>) ((List)resultObj).get(0);
                String jobStatus = (String) result.getOrDefault("status", "");
                switch (jobStatus.toLowerCase()) {
                    case "created":
                    case "queued":
                    case "processing":
                        status = BatchJobStatus.PENDING;
                        break;
                    case "completed":
                        status = BatchJobStatus.COMPLETED;
                        break;
                    default:
                        status = BatchJobStatus.ERROR;
                        break;
                }
            } else {
                status = BatchJobStatus.ERROR;
            }
        }
        return status;
    }

    public BatchJob downloadAsyncJobResults(SyncRequest request, String downloadURL, BatchJob job) {
        BiConsumer<InputStream, MediaType> saveFile = (stream, mediaType) -> {
            String filePath = filePath(request.getEntitySchema().getApiName(), job.getJobId());
            request.getStorage().write(stream, filePath);

            if (!validateFileContent(request.getStorage(), filePath)) {
                request.getStorage().delete(filePath);
                log.error("Downloaded CSV file contains rate limit error. Scheduling retry after 25 seconds");
                throw new QuotaExceededException(ErrorCodes.TOO_MANY_REQUESTS.name(), "Downloaded file contains rate limit error", "606", connectorId, 25);
            }

            job.setContentType(mediaType.toString());
            job.setDownloadedFielURLs(List.of(filePath));
            job.setStatus(BatchJobStatus.COMPLETED);
            log.debug("Downloaded csv for job id {} to file {}", job.getJobId(), filePath);
        };

        withBackoffAndErrorHandling(() -> download
                (downloadURL, request.getConnector().getAuthConfig(), saveFile,false));
        return job;
    }

    private boolean validateFileContent(Storage storage, String filePath) {
        try {
            try (java.io.InputStream stream = storage.read(filePath)) {
                if (stream == null) {
                    return false;
                }

                CsvUtils csvUtils = new CsvUtils();
                if (!csvUtils.isStreamParsable(stream, new CSVOptions())) {
                    return false;
                }
            }

            try (java.io.InputStream stream = storage.read(filePath)) {
                String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                String lowerContent = content.toLowerCase();
                return !lowerContent.contains("rate limit") &&
                       !lowerContent.contains("too many requests") &&
                       !lowerContent.contains("api limit exceeded");
            }
        } catch (Exception e) {
            log.error("Failed to validate file content: {}", e.getMessage());
            return false;
        }
    }

    protected String filePath(String entity, String jobId){
        return String.format("marketo/%s/%s.csv", entity, jobId);
    }

    // Bulk export API only supports fetching data from last 31 days
    public static String checkDateWithinLast31Days(long epochTimestamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(batchJobDateFormat);
        ZonedDateTime givenDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochTimestamp), ZoneId.of("UTC"));
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        ZonedDateTime thirtyOneDaysAgo = now.minusDays(31);
        if (givenDate.isAfter(thirtyOneDaysAgo)) {
            // If within the last 31 days, return the given date
            return formatter.format(givenDate);
        } else {
            // If not, return the date 31 days ago
            return formatter.format(thirtyOneDaysAgo);
        }
    }

}
