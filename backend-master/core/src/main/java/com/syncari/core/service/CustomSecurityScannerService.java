package com.syncari.core.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdToken;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.gson.*;
import com.syncari.core.config.AppConfig;
import com.syncari.core.utils.CustomSynapseDraftIssue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.*;

@Slf4j
@Component
public class CustomSecurityScannerService {

    @Autowired
    AppConfig config;

    private static final Gson GSON = new Gson();

    public List<CustomSynapseDraftIssue> scan(MultipartFile file) {
        String cloudFunctionResponse = invokeCloudFunction(file);
        return parseCloudFunctionResponse(cloudFunctionResponse);
    }

    private String invokeCloudFunction(MultipartFile file) {
        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(config.getCfExecutorCredentialsKey())))
                    .createScoped(Collections.singleton("https://www.googleapis.com/auth/cloud-platform"));

            if (!(credentials instanceof IdTokenProvider)) {
                throw new IllegalArgumentException("Credentials are not an instance of IdTokenProvider.");
            }

            String url = config.getCloudFunctionEndPoint() + "securityscanner";
            IdTokenProvider idTokenProvider = (IdTokenProvider) credentials;
            IdToken idToken = idTokenProvider.idTokenWithAudience(url, List.of());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(idToken.getTokenValue());

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return "file";
                }

                @Override
                public long contentLength() {
                    return file.getSize();
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            RestTemplate restTemplate = new RestTemplate();
            return restTemplate.postForObject(url, requestEntity, String.class);
        } catch (Exception e) {
            log.error("Failed to call custom synapse security scanner, {}", e.getMessage());
            log.debug(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("Failed to call custom synapse security scanner", e);
        }
    }

    private List<CustomSynapseDraftIssue> parseCloudFunctionResponse(String response) {
        List<CustomSynapseDraftIssue> issues = new ArrayList<>();
        JsonParser jsonParser = new JsonParser();
        JsonElement jsonElement = jsonParser.parse(response);

        // Check if the response is an empty object
        if (jsonElement.isJsonObject()) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            if (jsonObject.size() == 0) {
                return issues;
            }
        }

        if (jsonElement.isJsonArray()) {
            JsonArray issuesArray = jsonParser.parse(response).getAsJsonArray();

            for (int i = 0; i < issuesArray.size(); i++) {
                JsonObject issueJson = issuesArray.get(i).getAsJsonObject();
                CustomSynapseDraftIssue issue = GSON.fromJson(issueJson, CustomSynapseDraftIssue.class);
                issues.add(issue);
            }
        }

        return issues;
    }

}
