package com.syncari.connector.zuora;

import static com.syncari.connector.ConnectorHelper.withBackoffAndErrorHandling;
import static com.syncari.utils.ExceptionUtils.rethrow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.BatchJob;
import com.syncari.connector.data.BatchJobStatus;
import com.syncari.connector.data.DataWithCursor;
import com.syncari.connector.data.Result;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.utils.DateUtil;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;

@Slf4j
public class ZuoraRestClient extends SyncariEntityDataRestClient  {
    private static final int READ_TIMEOUT = 30000;

    public static final String BILLING_PREVIEW_RUN_ID = "billingPreviewRunId";
    public static final String JOB_STATUS_KEY = "status";
    public static final String JOB_COMPLETED_STATUS = "Completed";
    public static final List<String> JOB_IN_PROGRESS_STATUSES = List.of("Pending", "Processing");
    public static final String JOB_RESULTS_DOWNLOAD_URL = "resultFileUrl";
    public static final String ZUORA_WSDL_VERSION_REQ_HEADER_NAME = "X-Zuora-WSDL-Version";
    public static final String ZUORA_WSDL_VERSION_REQ_HEADER_VALUE = "122";

    private static final String POST_BODY_BILLING_PREVIEW_RUN = 
        "{\"assumeRenewal\":\"None\",\"includingEvergreenSubscription\":\"true\",\"targetDate\":\"%s\"}";
    
    public ZuoraRestClient(JsonParserConfig parserConfig){
        super(parserConfig);
    }
    
    public ZuoraRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper) {
        super(parserConfig, objectMapper);
    }
    
    public DataWithCursor getDataWithCursor(SyncRequest request, String url, String postBody, String prevQueryLocator) {
        log.info("postURL {} ;; postBody {} ", url, postBody);
        ResponseEntity<String> response = postRaw(url, postBody, request.getConnector().getAuthConfig());
        checkResponse(response);
        ReadContext ctx = JsonPath.parse(response.getBody());
        JSONArray results = ctx.read("records");
        String queryLocator = "";
        try {
            queryLocator = ctx.read("queryLocator");
        } catch (JsonPathException e) {
            // Nothing, queryLocator not found in the response.
        }
        
        String nextPageURL = "";
        String prevPageURL = "";
        if (StringUtils.isNotEmpty(queryLocator)) {
            nextPageURL = queryLocator;
        }
        if (StringUtils.isNotEmpty(prevQueryLocator)) {
            prevPageURL = prevQueryLocator;
        }
        List<EntityData> result = new ArrayList<>();

        List<String> datetimeAttributes = request.getEntitySchema().getAttributes().stream()
            .filter(x -> x.getDataType().equalsIgnoreCase("datetime"))
            .map(y -> y.getApiName().toLowerCase()).collect(Collectors.toList());

        for (Object r : results) {
            Map row = (Map) r;
            result.add(processResponseRow(row, request.getEntitySchema().getApiName(), datetimeAttributes));
        }
        
        return new DataWithCursor(prevPageURL, nextPageURL, result);
    }

    public EntityData processResponseRow(Map row, String entityName, List<String> datetimeAttributes) {
        var e = new EntityData();
        if (parserConfig.isFieldKey()) {
            e.setName(entityName);
            if (row.containsKey("Id")) {
                e.setId(row.get("Id").toString());
            } else if (row.containsKey("id")) {
                e.setId(row.get("id").toString());
            }
        }
        row.forEach((k, v) -> {
            if ("UpdatedDate".equalsIgnoreCase(k.toString())) {
                e.setLastModified(ZonedDateTime.parse(row.get("UpdatedDate").toString()).toEpochSecond()*1000);
            }
            if ("CreatedDate".equalsIgnoreCase(k.toString())) {
                e.setCreatedAt(ZonedDateTime.parse(row.get("CreatedDate").toString()).toEpochSecond()*1000);
            }
            if (datetimeAttributes.contains(k.toString().toLowerCase())) {
                e.addValue(k.toString(), ZonedDateTime.parse(row.get(k.toString()).toString()).toEpochSecond()*1000);
            } else {
                e.addValue(k.toString(), v);
            }
        });
        return e;
    }

    public List<EntityData> getByIds(String url, SyncRequest request) {
        List<EntityData> data = new ArrayList<>();

        List<String> datetimeAttributes = request.getEntitySchema().getAttributes().stream()
            .filter(x -> x.getDataType().equalsIgnoreCase("datetime"))
            .map(y -> y.getApiName().toLowerCase()).collect(Collectors.toList());

        request.getIds().forEach(id -> {
            try {
                ResponseEntity<String> response = getResponse(String.format("%s/%s", url, id), request.getConnector().getAuthConfig());
                checkResponse(response);
                try {
                    Map results = objectMapper.readValue(response.getBody(), Map.class);
                    data.add(processResponseRow(results, request.getEntitySchema().getApiName(), datetimeAttributes));
                } catch (IOException e) {
                    String errMsg = String.format("Failed to read response for object %s with id %s", request.getEntityName(), id);
                    throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR.name(), errMsg, "500");
                }
            } catch (NonRetriableException e) {
                if(!e.getStatusCode().equalsIgnoreCase("404 NOT_FOUND")) {
                    throw e;
                }
            }
        });
        return data;
    }

    public SyncResponse createOrUpdate(String url, SyncRequest request, boolean isCreate) {
        SyncResponse response = new SyncResponse(true);
        List<EntityData> toBeUpserted = request.getData().get(request.getConnector().getId());
        if (toBeUpserted == null || toBeUpserted.isEmpty()) {
            log.info("Nothing to be created for zuora");
            return response;
        }
        String idFieldApiName = request.getEntitySchema().getIdField().getApiName();
        log.info("Calling create for zuora with size {} for {}", toBeUpserted.size(), request.getEntityName());
        for (EntityData data : toBeUpserted) {
            String id = StringUtils.isNotEmpty(data.getId()) ? data.getId() : null;
            try {
                if (isCreate) {
                    EntityData d = post(url, data.getValues(), request.getConnector().getAuthConfig());
                    response.getResults().add(new Result(true, d.getValueAsString(idFieldApiName), data.getSyncariEntityId()));
                } else {
                    ResponseEntity<String> resp = put(String.format("%s/%s", url, data.getId()), data.getValues(), 
                        request.getConnector().getAuthConfig());
                    response.getResults().add(new Result(true, id, data.getSyncariEntityId()));
                }
            } catch (NonRetriableException | RestClientException e) {
                log.error(e.getMessage(), e);
                Result error = new Result(false, id, data.getSyncariEntityId());
                error.getErrors().add(e.getMessage());
                response.getResults().add(error);
            }
        }
        return response;
    }

    @Override
    public HttpHeaders getHeaders(AuthConfig authConf) {
        HttpHeaders headers = super.getHeaders(authConf);
        headers.set(ZUORA_WSDL_VERSION_REQ_HEADER_NAME, ZUORA_WSDL_VERSION_REQ_HEADER_VALUE);
        return headers;
    }

    public SyncResponse delete(String url, SyncRequest request) {
        SyncResponse response = new SyncResponse(true);
        List<EntityData> toBeDeleted = request.getData().get(request.getConnector().getId());
        if (toBeDeleted == null || toBeDeleted.isEmpty()) {
            log.info("Nothing to be deleted for zuora");
            return response;
        }
        log.info("Calling delete for zuora with size {} for {}", toBeDeleted.size(), request.getEntityName());
        for (EntityData data : toBeDeleted) {
            ResponseEntity<String> res = delete(String.format("%s/%s", url, data.getId()), request.getConnector().getAuthConfig());
            if (res.getStatusCode() != HttpStatus.OK) {
                Result errResult = new Result(false, data.getId(), data.getSyncariEntityId());
                response.getResults().add(errResult.addError(getErrorResponseValue(res)));
            } else {
                response.getResults().add(new Result(true, data.getId(), data.getSyncariEntityId()));
            }
        }
        return response;
    }

    private String getErrorResponseValue(ResponseEntity<String> response) {
        ReadContext ctx = JsonPath.parse(response.getBody());
        if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return "Authentication error";
        }
        JSONArray errors = null;
        try {
            errors = ctx.read("Errors");
        } catch (JsonPathException e) {
            return "";
        }
        String errString = "";
        for (Object r : errors) {
            Map row = (Map) r;
            errString += row.get("Message") + "; ";
        }
        return errString;
    }

    public BatchJob submitAsyncJob(SyncRequest request, String url, String targetDate) {
        log.info("Submitting billing preview run");
        String body = String.format(POST_BODY_BILLING_PREVIEW_RUN, targetDate);
        ResponseEntity<String> response = withBackoffAndErrorHandling(() -> {
            return postRaw(url, body, request.getConnector().getAuthConfig());
        });
        Map<String, Object> jobResponse = rethrow(() -> objectMapper.readValue(response.getBody(), Map.class));
        BatchJob batchJob = new BatchJob();
        batchJob.setConnectorId(request.getConnector().getId());
        batchJob.setExternalEntityName(request.getEntityName());
        batchJob.setJobId(jobResponse.getOrDefault(BILLING_PREVIEW_RUN_ID, "").toString());
        batchJob.setJobDetails(jobResponse);
        batchJob.setStatus(BatchJobStatus.PENDING);
        return batchJob;
    }

    public BatchJob queryJobStatus(SyncRequest request, String url, BatchJob job) {
        String billingPreviewRunId = job.getJobId();
        log.info("Querying billing preview run download job status for billingPreviewRunId {}", billingPreviewRunId);
        ResponseEntity<String> response = withBackoffAndErrorHandling(() -> 
            getResponse(url + "/" + billingPreviewRunId, request.getConnector().getAuthConfig()));
        log.info("Query Response for billing preview run download job status for billingPreviewRunId {}, {}", billingPreviewRunId, response.getBody());
        Map<String, Object> jobResponse = rethrow(() -> objectMapper.readValue(response.getBody(), Map.class));
        BatchJob batchJob = new BatchJob();
        batchJob.setConnectorId(request.getConnector().getId());
        batchJob.setExternalEntityName(request.getEntityName());
        batchJob.setJobId(billingPreviewRunId);
        batchJob.setJobDetails(jobResponse);
        Object jobStatus = jobResponse.get(JOB_STATUS_KEY);
        if (JOB_COMPLETED_STATUS.equalsIgnoreCase(jobStatus.toString())) {
            batchJob.setStatus(BatchJobStatus.COMPLETED);
        } else if (JOB_IN_PROGRESS_STATUSES.contains(jobStatus)) {
            batchJob.setStatus(BatchJobStatus.PENDING);
        } else {
            batchJob.setStatus(BatchJobStatus.ERROR);
        }
        return batchJob;
    }

    protected String filePath(String requestId){
        return String.format("zuora/billpreviewruns/%s.csv",requestId);
    }

    protected BatchJob downloadAsyncJobResults(SyncRequest request, BatchJob job) {
        String billingPreviewRunId = job.getJobId();
        if (MapUtils.isEmpty(job.getJobDetails()) || !job.getJobDetails().containsKey(JOB_RESULTS_DOWNLOAD_URL)) {
            throw new RuntimeException("No job results download URL found for billing preview run with id: " + billingPreviewRunId);
        }
        String jobDownloadURL = job.getJobDetails().get(JOB_RESULTS_DOWNLOAD_URL).toString();

        log.info("Downloading billing preview runs with billingPreviewRunId {}", billingPreviewRunId);
        BiConsumer<InputStream, MediaType> saveFile = (stream, mediaType) -> {
            String filePath = filePath(billingPreviewRunId);
            InputStream csvFileStream = null;
            // The actual file comes as zip file. Here we unzip and upload to GCS.
            try {
                final File tempFile = File.createTempFile(billingPreviewRunId, ".zip");
                tempFile.deleteOnExit();
                try (FileOutputStream out = new FileOutputStream(tempFile)) {
                    IOUtils.copy(stream, out);
                }
                ZipFile zf = new ZipFile(tempFile);
                Enumeration e = zf.entries();
                ZipEntry entry = (ZipEntry) e.nextElement();
                csvFileStream = zf.getInputStream(entry);
            } catch (IOException e) {
                String errMsg = "Error downloading billing preview run file from zuora with billingPreviewRunId " + billingPreviewRunId;
                log.error(errMsg, e);
                throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR.name(), e.getMessage(), "500");
            }
            request.getStorage().write(csvFileStream, filePath);
            job.setContentType(mediaType.toString());
            job.setDownloadedFielURLs(List.of(filePath));
            job.setStatus(BatchJobStatus.COMPLETED);
            log.info("Downloaded billing preview runs with billingPreviewRunId {} to file {}", billingPreviewRunId, filePath);
        };
        withBackoffAndErrorHandling(() -> download(jobDownloadURL, request.getConnector().getAuthConfig(), saveFile,false));
        return job;
    }
}
