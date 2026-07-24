package com.syncari.connector.rest;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.ProxyConfig;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.service.OAuth1Authorization;
import com.syncari.utils.ThrowingSupplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.syncari.connector.ConnectorHelper.withBackoff;

@Slf4j
public class NetSuiteRestClient extends SyncariEntityDataRestClient {

    private static final String COMPANY_ID_PATTERN = "(http[s]:\\/\\/)?(([^.]+)\\.)?suitetalk\\.api\\.netsuite\\.com";

    HttpHeaders headers = new HttpHeaders();
    private RestTemplate restTemplate;

    public NetSuiteRestClient(RestTemplate restTemplate, ObjectMapper objectMapper, ProxyConfig proxy){
    	super(proxy);
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    public NetSuiteRestClient(RestTemplate restTemplate, ObjectMapper objectMapper){
    	super();
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    public void addHeader(String key, String value) {
        headers.set(key, value);
    }

    private void withHttpErrorHandling(Runnable block){
        withHttpErrorHandling(() -> {
            block.run();
            return null;
        });
    }

    private <T> T withHttpErrorHandling(ThrowingSupplier<T> supplier) {
        return withBackoff(() -> {
            try {
                return ConnectorHelper.withHttpErrorHandling(supplier);
            } catch (NonRetriableException ex) {
                if (StringUtils.isNotBlank(ex.getStatusCode()) && ex.getStatusCode().startsWith("4")){
                    String errorMessage = ex.getMessage();
                    try {
                        Map map = objectMapper.readValue(errorMessage, Map.class);
                        errorMessage = map.getOrDefault("title", errorMessage).toString();
                        List<Map<String,Object>> errors= (List<Map<String, Object>>) map.getOrDefault("o:errorDetails",List.of());
                        StringBuilder eb  =new StringBuilder();
                        errors.forEach(e ->{
                            String sfdcErrorCode = e.getOrDefault("o:errorCode","").toString();
                            String sfdcErrorDetails = e.getOrDefault("detail","").toString();
                            if (StringUtils.isNotBlank(sfdcErrorCode) && StringUtils.isNotBlank(sfdcErrorDetails)){
                                eb.append(sfdcErrorCode+": "+sfdcErrorDetails);
                            }
                        });
                        errorMessage =  eb.length() > 0 ? eb.toString() : errorMessage;
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                    throw new NonRetriableException(ex.getErrorCode(), errorMessage, ex.getStatusCode(), (Exception) ex.getCause());
                }
                throw ex;
            }
        });
    }

    @Override
    public ResponseEntity<String> getResponse(String url, AuthConfig auth) {
        return withHttpErrorHandling(()->{
            log.debug("Get URL: {}", url);
            RestTemplate restTemplate = getTemplate();
            return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(getHeaders(auth, HttpMethod.GET, url)),
                    String.class);
        });
    }


    public ResponseEntity<String> postRaw(String url, String body, AuthConfig auth) {
        return withHttpErrorHandling(()-> {
            try {
                RestTemplate restTemplate = getTemplate();
                log.info("POST URL: {}", url);
                log.debug("POST Payload {}", body);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST,
                        new HttpEntity(body, getHeaders(auth, HttpMethod.POST, url)), String.class);
                return response;
            } catch (Exception e) {
                log.error("POST failed for payload {}", body);
                log.error(e.getMessage(), e);
                throw e;
            }

        });
    }
    public EntityData put(String url, EntityData request, AuthConfig auth) {
        return withHttpErrorHandling(() -> {
            RestTemplate restTemplate = getTemplate();
            String body = objectMapper.writeValueAsString(request.getValues());
            try {
                log.info("PUT: URL: {} ", url);
                log.debug("PUT: Payload {}", body);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT,
                        new HttpEntity(body, getHeaders(auth, HttpMethod.PUT, url)), String.class);
                return getSingleResponse(response);
            } catch (Exception e) {
                log.error("PUT failed for payload {}", body);
                log.error(e.getMessage(), e);
                throw e;
            }
        });
    }

    public ResponseEntity<String> patch(String url, String body, AuthConfig auth) {
        return patchRaw(url, body, auth);
    }

    public ResponseEntity<String> patchRaw(String url, String body, AuthConfig auth) {
        return withHttpErrorHandling(()-> {
            try {
                RestTemplate restTemplate = getTemplate();
                log.info("PATCH RAW: URL: {}", url);
                log.debug("PATCH: Payload {}", body);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PATCH,
                        new HttpEntity(body, getHeaders(auth, HttpMethod.PATCH, url)), String.class);
                log.debug("PATCH: Response {}", response);
                return response;
            } catch (Exception e) {
                log.error("PATCH RAW: Request failed for payload {}", body);
                log.error(e.getMessage(), e);
                throw e;
            }
        });
    }
    public EntityData patch(String url, EntityData request, AuthConfig auth) {
        return withHttpErrorHandling(()-> {
            RestTemplate restTemplate = getTemplate();
            String body = objectMapper.writeValueAsString(request.getValues());
            try {


                log.info("PATCH: URL: {}", url);
                log.debug("PATCH: Payload {}", body);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PATCH,
                        new HttpEntity(body, getHeaders(auth, HttpMethod.PATCH, url)), String.class);
                log.debug("PATCH: Response {}",response);
                return getSingleResponse(response);
            } catch (Exception e) {
                log.error("PATCH: Request failed for payload {}", body);
                log.error(e.getMessage(), e);
                throw e;
            }
        });
    }

    public ResponseEntity<String> delete(String url, AuthConfig auth) {
        return withHttpErrorHandling(()-> {
            RestTemplate restTemplate = getTemplate();
            log.info("DELETE: URL: {}", url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE,
                    new HttpEntity(getHeaders(auth, HttpMethod.DELETE, url)), String.class);
            checkResponse(response);
            return response;
        });
    }

    public HttpHeaders getHeaders(AuthConfig authConf, HttpMethod method, String url) {
        OAuth1Authorization oauth1 = new OAuth1Authorization(authConf);
        oauth1.setUrl(url);
        oauth1.setMethod(method.toString());
        String companyId = getCompanyId(url);
        oauth1.setRealm(getRealmFromCompanyId(companyId));
        headers.set("Authorization", oauth1.getAuthorization());
        if (authConf.getAdditionalHeaders() != null && !authConf.getAdditionalHeaders().isEmpty())
            authConf.getAdditionalHeaders().entrySet().stream().forEach(entry -> headers.add(entry.getKey(), entry.getValue()));
        return headers;
    }

    private String  getRealmFromCompanyId(String companyId) {
        return companyId.replace('-','_').toUpperCase();
    }

    private String getCompanyId(String url) {
        String companyId = "";
        Pattern p = Pattern.compile(COMPANY_ID_PATTERN);
        Matcher m = p.matcher(url);
        if (m.find()) {
            try {
                companyId = m.group(3);
            } catch (IndexOutOfBoundsException e) {
                log.error("Invalid endpoint url: " + url + " with exception message " + e.getMessage());
                throw new RuntimeException("Invalid endpoint url: " + url + ". Format should be https://ACCOUNT_ID.suitetalk.api.netsuite.com");
            }
        }
        return companyId;
    }

    public RestTemplate getTemplate(){
        return restTemplate;
    }

}
