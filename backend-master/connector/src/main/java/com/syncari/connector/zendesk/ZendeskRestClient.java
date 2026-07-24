package com.syncari.connector.zendesk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.DataWithCursor;
import com.syncari.connector.data.Result;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import lombok.extern.slf4j.Slf4j;
import net.snowflake.client.jdbc.internal.apache.tika.Tika;
import net.snowflake.client.jdbc.internal.apache.tika.config.TikaConfig;
import net.snowflake.client.jdbc.internal.apache.tika.detect.Detector;
import net.snowflake.client.jdbc.internal.apache.tika.io.TikaInputStream;
import net.snowflake.client.jdbc.internal.apache.tika.metadata.Metadata;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.threeten.bp.Instant;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
public class ZendeskRestClient extends SyncariEntityDataRestClient  {

    public static final String JOB_COMPLETED_STATUS = "completed";
    public static final String JOB_IN_PROGRESS_STATUS = "queued";
    public static final String JOB_STATUS_KEY = "status";
    
    private static final int READ_TIMEOUT = 30000;

    public ZendeskRestClient() {}
    
    public ZendeskRestClient(JsonParserConfig parserConfig){
        super(parserConfig);
    }
    
    public ZendeskRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper) {
        super(parserConfig, objectMapper);
    }
    
    public DataWithCursor getDataWithCursor(String url, AuthConfig auth) {
        ResponseEntity<String> response = getResponse(url, auth);
        List<EntityData> result = getBatchResponse(response);
        ReadContext ctx = JsonPath.parse(response.getBody());
        String nextPageURL = "";
        String prevPageURL = url;
        boolean end_of_stream = false;
        try {
            end_of_stream = (boolean) ctx.read("end_of_stream");
        } catch (JsonPathException e) {
            // Nothing, links not found in the response.
        }
        if (!end_of_stream) {
            try {
                nextPageURL = ctx.read("next_page");
            } catch (JsonPathException e) {
                // Nothing, links not found in the response.
            }
        }
        return new DataWithCursor(prevPageURL, nextPageURL, result);
    }

    public void pollForUpdateResponse(SyncResponse updateResp, String updManyResponseString, AuthConfig auth, List<EntityData> origValues) {
        ReadContext ctx = JsonPath.parse(updManyResponseString);
        try {
            Map<String, Object> jobStatus = ctx.read("job_status");
            String url = jobStatus.get("url").toString();
            // Wait for 30 seconds to get a response.
            Instant expiry = Instant.now().plusSeconds(30);
            Map<String, String> syncariIdByExternalId = origValues.stream()
                .collect(Collectors.toMap(EntityData::getId, EntityData::getSyncariEntityId));
            while (Instant.now().isBefore(expiry)) {
                log.info("Polling for update job status with URL {}", url);
                ResponseEntity<String> jobResp = getResponse(url, auth);
                ReadContext jobCtx = JsonPath.parse(jobResp.getBody());
                jobStatus = jobCtx.read("job_status");
                if (JOB_COMPLETED_STATUS.equalsIgnoreCase(jobStatus.get(JOB_STATUS_KEY).toString())) {
                    List<Map<String, Object>> results = (List) jobStatus.get("results");
                    results.forEach(x -> {
                        Map<String, Object> result = (Map) x;
                        String updatedId = result.containsKey("id") ? result.get("id").toString() : "";
                        if (result.containsKey("success") && ((Boolean) result.get("success"))) {
                            updateResp.getResults().add(new Result(true, updatedId, syncariIdByExternalId.get(updatedId)));
                        } else {
                            log.error("Zendesk update failed. Response: {}", result);
                            String errorMsg = result.containsKey("error") ? result.get("error").toString() : "";
                            errorMsg += ": " + (result.containsKey("details") ? result.get("details").toString() : "");
                            updateResp.appendError(errorMsg);
                            Result updateResult = new Result(false, updatedId, syncariIdByExternalId.get(updatedId));
                            updateResult.addError(errorMsg);
                            updateResp.getResults().add(updateResult);
                        }
                    });
                    break;
                }
                // arbitrary time, poll every random 1-5 seconds.
                int ramdomSleepTime = new Random().nextInt(5 - 1);
                log.info("Job still not completed. Sleeping for ramdom time: {}", ramdomSleepTime);
                Thread.sleep(ramdomSleepTime);
            }
        } catch (InterruptedException e) {
            log.error("Failed to check job status due to thread interruption", e);
            updateResp.appendError("Failed to check job status due to thread interruption");
            updateResp.setSuccess(false);
        } catch (PathNotFoundException e) {
            log.error("Failed to check job status", e);
            updateResp.appendError("Failed to check job status due to : " + e.getMessage());
            updateResp.setSuccess(false);
        }
    }

    public String uploadAttachment(SyncRequest request, String attachment, String filename) {
        RestTemplate restTemplate = new RestTemplate();
        // Build URL with query parameter
        String url = request.getConnector().getEndpoint() + "/api/v2/uploads.json" + "?filename=" + filename;

        // Read file from GCS and set it as the request body
        try (InputStream is = request.getStorage().read(attachment)) {
            byte[] fileContent = IOUtils.toByteArray(is);
            Tika tika = new Tika();
            String contentType = tika.detect(fileContent);
            if(StringUtils.isBlank(contentType)) {
                contentType = URLConnection.guessContentTypeFromStream(is);
            }
            if(StringUtils.isBlank(contentType)) {
                contentType = getContentTypeFromFileName(attachment);
            }
            // Set headers
            HttpHeaders headers = getAuthHeaders(request.getConnector().getAuthConfig());
            headers.setContentType(MediaType.parseMediaType(contentType));

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileContent, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);
            Map<String, Object> uploadMap = (Map<String, Object>) responseMap.get("upload");
            if (uploadMap != null && uploadMap.containsKey("token")) {
                return (String) uploadMap.get("token");
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getContentTypeFromFileName(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        switch (extension) {
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "pdf":
                return "application/pdf";
            default:
                return "application/octet-stream";
        }
    }
    
}
