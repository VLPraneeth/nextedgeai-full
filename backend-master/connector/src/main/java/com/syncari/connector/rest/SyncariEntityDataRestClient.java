package com.syncari.connector.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.EntityPage;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.config.ProxyConfig;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.ConnectorException;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.exception.UnknownException;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import static com.syncari.connector.ConnectorHelper.withBackoffAndErrorHandling;
import static com.syncari.utils.ExceptionUtils.rethrow;

@Slf4j
public class SyncariEntityDataRestClient {
	protected JsonParserConfig parserConfig;
	protected ObjectMapper objectMapper;
	protected Optional<ProxyConfig> proxy = Optional.empty();
	private static Set<HttpStatus> SUCCESS_STATUSES = Set.of(HttpStatus.OK, HttpStatus.CREATED, HttpStatus.NO_CONTENT, HttpStatus.ACCEPTED);

	public SyncariEntityDataRestClient(JsonParserConfig parserConfig) {
		this.parserConfig = parserConfig;
	}
	public SyncariEntityDataRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper) {
		this.parserConfig = parserConfig;
		this.objectMapper = objectMapper;
	}
	
	public SyncariEntityDataRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper, ProxyConfig proxy) {
		this.parserConfig = parserConfig;
		this.objectMapper = objectMapper;
		this.proxy = Optional.ofNullable(proxy);
	}
	
	public SyncariEntityDataRestClient(ProxyConfig proxy) {
		this.proxy = Optional.ofNullable(proxy);
	}

	public SyncariEntityDataRestClient() { }

	public List<EntityData> get(String url, AuthConfig auth) {
		ResponseEntity<String> response = getResponse(url, auth);
		return getBatchResponse(response);
	}

	public EntityPage getPage(String url, AuthConfig auth) {
		log.info("GET PAGE : {}",url);
		ResponseEntity<String> response = getResponse(url, auth);
		return getPageResponse(response);

	}
	
	public ResponseEntity<String> getResponse(HttpHeaders headers, String url, AuthConfig auth, Object... uriArgs) {
		return getResponse(true, headers, url, auth, uriArgs);
	}

    public ResponseEntity<String> getResponse(boolean enableRedirect, HttpHeaders headers, String url, AuthConfig auth, Object... uriArgs) {
    	RestTemplate tmp = null;
    	if(!enableRedirect) {
    		tmp = getNonRedirectTemplate();
    	} else {
    		tmp = getTemplate();
    	}
    	RestTemplate restTemplate = tmp;
		log.info("GET Request: {}", url);
        // We dont want to log sensitive info like token.
        Map<String, Object> headerVals = Maps.newHashMap();
        headers.keySet().forEach(x -> {
            if (!"Authorization".equalsIgnoreCase(x)) headerVals.put(x, headers.get(x)); 
        });
        log.debug("GET Request Headers: {}", headerVals);
        return withBackoffAndErrorHandling(()->
						{
                            HttpEntity httpEntity = new HttpEntity(headers);
                            return uriArgs == null
            ? restTemplate.exchange(url, HttpMethod.GET, httpEntity, String.class)
            : restTemplate.exchange(url, HttpMethod.GET, httpEntity, String.class, uriArgs);
                        }
		);
    }

	public ResponseEntity<String> getResponse(String url, AuthConfig auth) {
		return getResponse(getHeaders(auth), url, auth);
	}

    public ResponseEntity<String> getResponse(String url, AuthConfig auth, Object... uriArgs) {
        return getResponse(getHeaders(auth), url, auth, uriArgs);
    }

	public List<EntityData> post(String url, List<EntityData> request, AuthConfig auth) {
		ResponseEntity<String> response = doPost(url, request, auth);
		return getBatchResponse(response);
	}

	public EntityData post(String url, EntityData request, AuthConfig auth) {
		RestTemplate restTemplate = getTemplate();
		try {
		    String body = objectMapper.writeValueAsString(request.getValues());
			log.debug("URL: {} Payload {}",url, body);
			ResponseEntity<String> response = withBackoffAndErrorHandling(() -> restTemplate.exchange(url, HttpMethod.POST,
					new HttpEntity(body, getHeaders(auth)), String.class));
			return getSingleResponse(response);
		} catch (IOException e) {
			throw new UnknownException(e.getMessage());
		}
	}
	public EntityData post(String url, Object request, AuthConfig auth) {
		RestTemplate restTemplate = getTemplate();
		try {
			String body = objectMapper.writeValueAsString(request);
			log.info("Post URL: {}", url);
			log.debug("Post Body: {}", body);
			ResponseEntity<String> response = withBackoffAndErrorHandling(() -> restTemplate.exchange(url, HttpMethod.POST,
					new HttpEntity(body, getHeaders(auth)), String.class));

			if (response.getStatusCode().isError()) {
				log.error("Payload generating error {}", body);
				log.error("HTTP Error Body {}", response.getBody());
			}

			return getSingleResponse(response);
		} catch (IOException e) {
			throw new UnknownException(e.getMessage());
		}
	}

	public EntityData post(String url, String body, AuthConfig auth) {
		RestTemplate restTemplate = getTemplate();
		try {
			ResponseEntity<String> response = withBackoffAndErrorHandling(() ->
					restTemplate.exchange(url, HttpMethod.POST,
							new HttpEntity(body, getHeaders(auth)), String.class));
			log.info("HTTP Status {}", response.getStatusCode());
			log.debug("HTTP Response {}", response.getBody());
			if (response.getStatusCode().isError()) {
				log.error("Payload generating error {}", body);
				log.error("HTTP Error Body {}", response.getBody());
			}
			return getSingleResponse(response);
		}catch(HttpClientErrorException e){
			log.error(e.getMessage(),e);
			log.error(e.getResponseBodyAsString());
			throw e;
		}
	}

	public ResponseEntity<String> postRaw(HttpHeaders headers, String url, String body, AuthConfig auth) {
	    RestTemplate restTemplate = getTemplate();
	    return withBackoffAndErrorHandling( () -> {
			log.info("postRaw URL {}", url);
			log.debug("postRaw body {}", body);
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST,
					new HttpEntity(body, headers), String.class);
			log.info("HTTP Status {}", response.getStatusCode());
			log.debug("HTTP Response {}", response.getBody());
			if (response.getStatusCode().isError()) {
				log.error("Payload generating error {}", body);
				log.error("HTTP Error Body {}", response.getBody());
			}
			return response;
		});
	}

	public ResponseEntity<String> post(HttpHeaders headers, String url, Map body) {
		RestTemplate restTemplate = getTemplate();
		return withBackoffAndErrorHandling( () -> {
			log.info("postRaw URL {}", url);
			log.debug("postRaw body {}", body);
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST,
					new HttpEntity(body, headers), String.class);
			log.info("HTTP Status {}", response.getStatusCode());
			log.debug("HTTP Response {}", response.getBody());
			if (response.getStatusCode().isError()) {
				log.error("Payload generating error {}", body);
				log.error("HTTP Error Body {}", response.getBody());
			}

			return response;
		});
	}
	
	public ResponseEntity<String> patch(HttpHeaders headers, String url, String body, AuthConfig auth) {
	    RestTemplate restTemplate = getTemplate();
	    return withBackoffAndErrorHandling( () -> {
			log.info("PATCH URL {}", url);
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PATCH,
					new HttpEntity(body, headers), String.class);
			log.info("HTTP Status {}", response.getStatusCode());
			log.debug("HTTP Response {}", response.getBody());
			if (response.getStatusCode().isError()) {
				log.error("Payload generating error {}", body);
				log.error("HTTP Error Body {}", response.getBody());
			}

			return response;
		});
	}

    public ResponseEntity<String> postFormDataURI(URI uri, MultiValueMap bodyMap, AuthConfig auth) {
        RestTemplate restTemplate = getTemplate();
		return withBackoffAndErrorHandling( () ->{
			log.info("HTTP POST FORM DATA at {}", uri.toString());
			log.debug("HTTP POST payload {}", bodyMap);
			HttpHeaders headers = getHeaders(auth);
			headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

			HttpEntity<MultiValueMap<String, String>> request =
					new HttpEntity<MultiValueMap<String, String>>(bodyMap, headers);
			ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, request, String.class);
			log.info("HTTP Status {}", response.getStatusCode());
			log.debug("HTTP Response {}", response.getBody());
			if (response.getStatusCode().isError()) {
				log.error("Payload generating error {}", bodyMap);
				log.error("HTTP Error Body {}", response.getBody());
			}

			return response;
		});
    }
    
    public ResponseEntity<String> patch(String url, String body, AuthConfig auth) {
        return patch(getHeaders(auth), url, body, auth);
    }
	
	public ResponseEntity<String> postRaw(String url, String body, AuthConfig auth) {
	    return postRaw(getHeaders(auth), url, body, auth);
	}

	public ResponseEntity<String> postRawURI(URI uri, String body, AuthConfig auth) {
		RestTemplate restTemplate = getTemplate();
		return withBackoffAndErrorHandling( () -> {
			log.info("HTTP POST RAW at {}", uri.toString());
			log.debug("HTTP POST payload {}", body);
			ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST,
					new HttpEntity(body, getHeaders(auth)), String.class);
			log.info("HTTP Status {}", response.getStatusCode());
			log.debug(response.getBody());
			if (response.getStatusCode().isError()) {
				log.error("Payload generating error {}", body);
				log.error("HTTP Error Body {}", response.getBody());
			}
			return response;
		});
	}

	public EntityData patch(String url, Map<String, Object> payload, AuthConfig auth) {
		RestTemplate restTemplate = getTemplate();
		String payloadString = rethrow(() -> objectMapper.writeValueAsString(payload));
		try {
			ResponseEntity<String> response = patch(url, payloadString, auth);
			return getSingleResponse(response);
		}catch(HttpClientErrorException e){
			log.error(e.getMessage(),e);
			log.error(e.getResponseBodyAsString());
			throw e;
		} catch(ConnectorException ce){
			log.error("Payload generating error {}", payloadString);
			throw ce;
		}
	}

    public ResponseEntity<String> postWithoutBody(URI uri) {
		RestTemplate restTemplate = getTemplate();
		return withBackoffAndErrorHandling( () -> {
			log.info("HTTP postAcquireTokenURI at {}", uri.toString());
			ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity(""), String.class);
			log.info("HTTP Status {}", response.getStatusCode());
			log.debug(response.getBody());
			if (response.getStatusCode().isError()) {
				log.error("Eppty payload generated this error");
				log.error("HTTP Error Body {}", response.getBody());
			}
			return response;
		});
	}

	public EntityData post(String url, Map<String, Object> payload, AuthConfig auth) {
		RestTemplate restTemplate = getTemplate();
		String payloadString = null;
		try {
			payloadString = objectMapper.writeValueAsString(payload);
			log.debug("HTTP POST at {}, payload {}", url, payloadString);
			String finalPayloadString = payloadString;
			ResponseEntity<String> response = withBackoffAndErrorHandling(() -> restTemplate.exchange(url, HttpMethod.POST,
					new HttpEntity(finalPayloadString, getHeaders(auth)), String.class));
			log.info("POST: HTTP Status {}", response.getStatusCode());
			if (response.getStatusCode().isError()) {
				log.error("Payload generating error {}", payloadString);
				log.error("HTTP Error Body {}", response.getBody());
			}
			log.debug(response.getBody());
			return getSingleResponse(response);
		}catch(HttpClientErrorException e){
			log.error(e.getMessage(),e);
			log.error(e.getResponseBodyAsString());
			throw e;
		}catch(IOException e){
			log.error(e.getMessage(),e);
			throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR.name(),e.getMessage(),"500");
		} catch(ConnectorException ce){
			log.error("Payload generating error {}", payloadString);
			throw ce;
		}
	}
	
	public ResponseEntity<String> put(HttpHeaders headers, String url, String body, AuthConfig auth) {
	    RestTemplate restTemplate = getTemplate();
		return withBackoffAndErrorHandling(()-> {
			log.info("Post URL: {}", url);
			log.debug("Post Payload: {}", body);
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT,
					new HttpEntity(body, headers), String.class);
			log.info("HTTP Status {}", response.getStatusCode());
			if (response.getStatusCode().isError()) {
				log.error("Payload generating error {}", body);
				log.error("HTTP Error Body {}", response.getBody());
			}
			log.debug(response.getBody());
			return response;
		});
	}
	
	public ResponseEntity<String> put(String url, String body, AuthConfig auth) {
	    return put(getHeaders(auth), url, body, auth);
	}

	public ResponseEntity<String> put(String url, Map<String, Object> payload, AuthConfig auth) {
		RestTemplate restTemplate = getTemplate();

		return withBackoffAndErrorHandling(()-> {
			String bodyAsString = rethrow(() -> objectMapper.writeValueAsString(payload));
			log.info("PUT URL: {}", url);
			log.debug("PUT Payload: {} ", bodyAsString);
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT,
					new HttpEntity(bodyAsString, getHeaders(auth)), String.class);
			log.info("HTTP Status {}", response.getStatusCode());
			if (response.getStatusCode().isError()) {
				log.error("Payload generating error {}", bodyAsString);
				log.error("HTTP Error Body {}", response.getBody());
			}

			return response;
		});
	}

	public ResponseEntity<String> delete(HttpHeaders headers, String url, AuthConfig auth) {
		return withBackoffAndErrorHandling(()-> {
			RestTemplate restTemplate = getTemplate();
			log.info("Delete URL {}", url);
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE,
					new HttpEntity(headers), String.class);
			if (response.getStatusCode().isError()) {
				log.error("HTTP Error Body {}", response.getBody());
			}
			checkResponse(response);
			return response;
		});
	}
	
	public ResponseEntity<String> delete(String url, AuthConfig auth) {
	    return delete(getHeaders(auth), url, auth);
	}
	public ResponseEntity<String> delete(String url, Object payload, AuthConfig auth) {
		return withBackoffAndErrorHandling(()-> {
			String bodyAsString = rethrow(() -> objectMapper.writeValueAsString(payload));
			log.info("DELETE: URL: {}", url);
			log.debug("DELETE: Payload {}", bodyAsString);
			RestTemplate restTemplate = getTemplate();
			final ResponseEntity<String> exchangeResult = restTemplate.exchange(url, HttpMethod.DELETE,
					new HttpEntity(bodyAsString, getHeaders(auth)), String.class);
			if (exchangeResult.getStatusCode().isError()) {
				log.error("Payload generating error {}", bodyAsString);
				log.error("HTTP Error Body {}", exchangeResult.getBody());
			}

			return exchangeResult;
		});
	}

	public void download(String url, AuthConfig auth, BiConsumer<InputStream, MediaType> consumer, boolean setOriginalHeadersOnRedirect) {
		withBackoffAndErrorHandling(() -> {
			RestTemplate restTemplate = getTemplate(setOriginalHeadersOnRedirect);
			RequestCallback requestCallback = request -> {
				HttpHeaders headers = getAuthHeaders(auth);
				HttpHeaders requestHeaders = request.getHeaders();
				requestHeaders.addAll(headers);
				requestHeaders
						.setAccept(Arrays.asList(MediaType.ALL));
			};
			ResponseExtractor<Void> responseExtractor = response -> {
				consumer.accept(response.getBody(), response.getHeaders().getContentType());
				return null;
			};
			restTemplate.execute(url, HttpMethod.GET,requestCallback,responseExtractor);
		});
	}

	public ResponseEntity<String> put(String url, Object  body, AuthConfig auth) {
		return withBackoffAndErrorHandling(()-> {
			RestTemplate restTemplate = getTemplate();
			var response = restTemplate.exchange(url, HttpMethod.PUT,
					new HttpEntity(body, getHeaders(auth)), String.class);
			if (response.getStatusCode().isError()) {
				log.error("Payload generating error {}", body);
				log.error("HTTP Error Body {}", response.getBody());
			}

			checkResponse(response);
			return response;
		});
	}

	public List<EntityData> getBatchResponse(ResponseEntity<String> response) {
		log.debug("Batch Response body: {}", response.getBody());
		checkResponse(response);
		ReadContext ctx = JsonPath.parse(response.getBody());
		JSONArray results = ctx.read(parserConfig.getResultsArrayPath());
		return extractEntityData(ctx, results);
	}
	
	private EntityPage getPageResponse(ResponseEntity<String> response) {
	    checkResponse(response);
	    log.debug("Batch Response body: {}", response.getBody());
	    EntityPage page = new EntityPage();
	    ReadContext ctx = JsonPath.parse(response.getBody());
	    JSONArray results = ctx.read(parserConfig.getResultsArrayPath());
	    page.setData(extractEntityData(ctx, results));
	    if(parserConfig.getOffsetPath() != null) {
	        Object offsetObj = ctx.read(parserConfig.getOffsetPath());
	        try {
	            page.setOffset((Long) offsetObj);
            } catch (Exception e) {
            }
	        try {
	            page.setOffset((Integer) offsetObj);
	        } catch (Exception e) {
	        }
	    }
	    if(parserConfig.getHasMorePath() != null) {
	        Boolean hasMore = (Boolean) ctx.read(parserConfig.getHasMorePath());
	        page.setHasMore(hasMore);
	    }
	    return page;
	}

    protected List<EntityData> extractEntityData(ReadContext ctx, JSONArray results) {
        List<EntityData> extracted = new ArrayList<>();
		for (int i = 0; i < results.size(); i++) {
			var e = new EntityData();
			if (parserConfig.isFieldKey()) {
				Map<String, Object> obj = ctx.read(parserConfig.getFieldsPath().replace("{i}", String.valueOf(i)));
				for (String key : obj.keySet()) {
					Object value = ctx.read(
							parserConfig.getValuePath().replace("{i}", String.valueOf(i)).replace("__key__", key));
					e.addValue(key, value);
					if (key.equalsIgnoreCase(parserConfig.getIdFieldName())) {
						e.setId(value.toString());
					}
				}
				if (parserConfig.getIdPath() != null) {
					e.setId(ctx.read(parserConfig.getIdPath().replace("{i}", String.valueOf(i))).toString());
				}
			} else {
				// TODO: This would be an array of properties
			}
			extracted.add(e);
		}
		return extracted;
    }

	protected EntityData getSingleResponse(ResponseEntity<String> response) {
		checkResponse(response);
		if(StringUtils.isBlank(response.getBody())) return null;
		log.debug("Response body: {}", response.getBody());
		ReadContext ctx = JsonPath.parse(response.getBody());
		var e = new EntityData();
		Map<String, Object> results = new HashMap<String, Object>();
		if(parserConfig.getResultsArrayPath() != null) {
		    results = ctx.read(parserConfig.getResultsArrayPath());
		    try {
		        Object idField = ctx.read(parserConfig.getIdFieldName());
		        if (idField != null) {
		            e.setId(idField.toString());
		        }
		    } catch (Exception ex) {
		        // TODO: what do we do here?
		    }
		} else {
		    ObjectMapper objectMapper = new ObjectMapper();
		    try {
		        results = objectMapper.readValue(response.getBody(), Map.class);
            } catch (Exception e1) {
                throw new RuntimeException(e1);
            }
		}
		if (parserConfig.isFieldKey()) {
			for (Entry<String, Object> entry : results.entrySet()) {
				e.addValue(entry.getKey(), entry.getValue());
				if (e.getId() != null && entry.getKey().equalsIgnoreCase(parserConfig.getIdFieldName())) {
					e.setId(entry.getValue().toString());
				}
			}
		} else {
			// TODO: This would be an array of properties
		}
		return e;
	}

	public HttpHeaders getHeaders(AuthConfig authConf) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
		HttpHeaders authHeaders = getAuthHeaders(authConf);
		headers.addAll(authHeaders);
		return headers;
	}

	public HttpHeaders getAuthHeaders(AuthConfig authConf) {
		HttpHeaders headers = new HttpHeaders();
		if (authConf.getAccessToken() != null) {
            if (authConf.getUserName() != null) {
                headers.set("Authorization", getAuthHeader(authConf.getUserName() + ":" + authConf.getAccessToken()));
            } else if (authConf.getAdditionalHeaders() != null && authConf.getAdditionalHeaders().containsKey("AuthType") &&
					authConf.getAdditionalHeaders().get("AuthType").equalsIgnoreCase("ApiKeyAsUsername")) {
				headers.set("Authorization", getAuthHeader(authConf.getAccessToken() + ":"));
			} else {
                headers.set("Authorization", "Bearer " + authConf.getAccessToken());
            }
        } else {
            if (authConf.getToken() == null) {
                if (authConf.getUserName() != null && authConf.getPassword() != null) {
                    headers.set("Authorization", getAuthHeader(authConf.getUserName() + ":" + authConf.getPassword()));
                }
            } else {
                if (authConf.getClientSecret() != null) {
                    headers.set("Authorization", getAuthHeader(authConf.getToken() + ":" + authConf.getClientSecret()));
                }
            }
        }
		if(authConf.getAdditionalHeaders() != null && !authConf.getAdditionalHeaders().isEmpty()) {
		    authConf.getAdditionalHeaders().forEach((k, v) -> headers.set(k, v));
		}
		return headers;
	}

	private String getAuthHeader(String auth) {
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(Charset.forName("US-ASCII")));
        return "Basic " + new String(encodedAuth);
    }

	private ResponseEntity<String> doPost(String url, List<EntityData> request, AuthConfig auth) {
		return withBackoffAndErrorHandling(() -> {
					ObjectMapper objectMapper = new ObjectMapper();
					RestTemplate restTemplate = getTemplate();
					String requestAsString = "";
					for (EntityData r : request) {
						try {
							if (!StringUtils.isBlank(requestAsString)) {
								requestAsString = requestAsString.concat(",");
							}
							requestAsString = requestAsString.concat(objectMapper.writeValueAsString(r.getValues()));
						} catch (IOException e) {
							throw new UnknownException(e.getMessage());
						}
					}
			String body = String.format("{ \"%s\": [ %s ] }", parserConfig.getResultsArrayPath(), requestAsString);
			log.info("URL: {}", url);
			log.debug("POST Payload {}", body);
			final ResponseEntity<String> exchangeResults = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity(body, getHeaders(auth)), String.class);
			if (exchangeResults.getStatusCode().isError()) {
				log.error("Payload generating error {}", body);
				log.error("HTTP Error Body {}", exchangeResults.getBody());
			}
			return exchangeResults;

		});

	}

	public void checkResponse(ResponseEntity<String> response) {
		if (SUCCESS_STATUSES.contains(response.getStatusCode())) {
			return;
		}
		if (response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
			throw new UnknownException(response.getBody());
		}
		log.info("Got response code: " + response.getStatusCode().name());
		List<HttpStatus> retriable = List.of(HttpStatus.GATEWAY_TIMEOUT, HttpStatus.SERVICE_UNAVAILABLE,
				HttpStatus.TOO_MANY_REQUESTS, HttpStatus.REQUEST_TIMEOUT, HttpStatus.BANDWIDTH_LIMIT_EXCEEDED);
		if (retriable.contains(response.getStatusCode())) {
			throw new RetriableException(response.getStatusCode().name(), response.getBody(),
					String.valueOf(response.getStatusCode()));
		}
		throw new NonRetriableException(response.getStatusCode().name(), response.getBody(),
				String.valueOf(response.getStatusCode()));
	}

	public void checkResponseNonStrict(ResponseEntity<String> response) {
		if (SUCCESS_STATUSES.contains(response.getStatusCode()) || response.getStatusCode().is3xxRedirection()) {
			return;
		}
		if (response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
			throw new UnknownException(response.getBody());
		}
		log.info("Got response code: " + response.getStatusCode().name());
		List<HttpStatus> retriable = List.of(HttpStatus.GATEWAY_TIMEOUT, HttpStatus.SERVICE_UNAVAILABLE,
				HttpStatus.TOO_MANY_REQUESTS, HttpStatus.REQUEST_TIMEOUT, HttpStatus.BANDWIDTH_LIMIT_EXCEEDED);
		if (retriable.contains(response.getStatusCode())) {
			throw new RetriableException(response.getStatusCode().name(), response.getBody(),
					String.valueOf(response.getStatusCode()));
		}
		throw new NonRetriableException(response.getStatusCode().name(), response.getBody(),
				String.valueOf(response.getStatusCode()));
	}

	public RestTemplate getTemplate() {
		return getTemplate(true);
	}
	
	public RestTemplate getNonRedirectTemplate() {
		int timeout = 30000;
		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create().disableRedirectHandling();
		if(proxy.isPresent() && StringUtils.isNotEmpty(proxy.get().getHost())) {
			HttpHost httpProxy = new HttpHost(proxy.get().getHost(), proxy.get().getPort());
			httpClientBuilder.setProxy(httpProxy);
			log.debug("Setting proxy with {} {}", proxy.get().getHost(), proxy.get().getPort());
		}
		CloseableHttpClient client =
				httpClientBuilder.build();
		HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(client);
		clientHttpRequestFactory.setConnectTimeout(timeout);
		clientHttpRequestFactory.setReadTimeout(timeout);
		return new RestTemplate(clientHttpRequestFactory);
	}

	public RestTemplate getTemplate(boolean setOriginalHeadersOnRedirect) {
		int timeout = 30000;
		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
		if(!setOriginalHeadersOnRedirect){
			httpClientBuilder = httpClientBuilder.setRedirectStrategy(new RedirectStrategyWithoutOriginalHeaders());
		}
		if(proxy.isPresent() && StringUtils.isNotEmpty(proxy.get().getHost())) {
			HttpHost httpProxy = new HttpHost(proxy.get().getHost(), proxy.get().getPort());
			httpClientBuilder.setProxy(httpProxy);
			log.debug("Setting proxy with {} {}", proxy.get().getHost(), proxy.get().getPort());
		}
		CloseableHttpClient client =
				httpClientBuilder.build();
		HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(client);
		clientHttpRequestFactory.setConnectTimeout(timeout);
		clientHttpRequestFactory.setReadTimeout(timeout);
		return new RestTemplate(clientHttpRequestFactory);
	}

    public static void parseAndGetResponseFields(Map rawResponse, Map flattenObj, String fieldApiName, String respKey) {
        if (!respKey.contains(".") && rawResponse.containsKey(respKey)) {
            flattenObj.put(fieldApiName, rawResponse.get(respKey));
        } else if (respKey.contains(".")) {
            String[] innerKeys = respKey.split("\\.");
            Object value = rawResponse.get(innerKeys[0]); 
            for (int i = 1; i < innerKeys.length; i++) {
                // When value is not found, do not proceed, nothing to look for.
                if (value == null) break;
                if ("$".equals(innerKeys[i])) {
                    List values = (List) ((Map) value).get(innerKeys[i-1]);
                    if (values.isEmpty()) break;
                    // Move the keys
                    i++;
                    for (int j = 0; j < values.size(); j++) {
                        if (((Map) values.get(j)).containsKey(innerKeys[i])) {
                            if (!flattenObj.containsKey(fieldApiName)) {
                                flattenObj.put(fieldApiName, new ArrayList<>());
                            }
                            ((List) flattenObj.get(fieldApiName)).add(((Map) values.get(j)).get(innerKeys[i]));
                        }
                    }
                } else if (i == innerKeys.length - 1) {
                    flattenObj.put(fieldApiName, ((Map) value).get(innerKeys[i]));
                } else {
                    if (!"$".equals(innerKeys[i+1])) {
                        value = ((Map) value).get(innerKeys[i]);
                    }
                }
            }
        }
    }

    public static void parseAndPrepareRequest(Map<String, Object> rawRequest, Map<String, Object> unflattenObj, String fieldApiName, String respKey) {
        if (!rawRequest.containsKey(fieldApiName)) return;
        Object value = rawRequest.get(fieldApiName);
        Object validValue = validateObject(value);
        // Convert any incoming instant value to timestamp epoch millis.
        if (validValue instanceof Instant) {
            validValue = ((Instant) validValue).toEpochMilli();
        } else if (validValue instanceof ZonedDateTime) {
            validValue = ((ZonedDateTime) validValue).toInstant().toEpochMilli();
        }

        if (!respKey.contains(".") && rawRequest.containsKey(respKey)) {
            unflattenObj.put(respKey, value);
        } else if (respKey.contains(".")) {
            String[] innerKeys = respKey.split("\\.");
            Map<String, Object> innerMap = unflattenObj;
            for (int i = 0; i < innerKeys.length; i++) {
                if (i == innerKeys.length - 1) {
					innerMap.put(innerKeys[i], validValue);
                } else if ("$".equals(innerKeys[i])) {
                    String listKey = innerKeys[i-1];
                    List<Map<String, Object>> unflattenListValues = (List) innerMap.getOrDefault(listKey, new ArrayList<>());
                    List<Map<String, Object>> listValues = (List) value;
                    // Move the keys
                    i++;
                    for (int j = 0; validValue != null && j < listValues.size(); j++) {
                        if (unflattenListValues.size() < j+1) unflattenListValues.add(new HashMap<>());
                        ((Map<String, Object>) unflattenListValues.get(j)).put(innerKeys[i], listValues.get(j));
                    }
                    innerMap.put(listKey, unflattenListValues);
                } else {
                    if ("$".equals(innerKeys[i+1])) {
                        if (!innerMap.containsKey(innerKeys[i])) {
                            innerMap.put(innerKeys[i], new ArrayList<>());    
                        }
                        continue;
                    }
                    if (!innerMap.containsKey(innerKeys[i])) {
                        innerMap.put(innerKeys[i], new HashMap<>());
                    }
                    innerMap = (Map) innerMap.get(innerKeys[i]);
                }
            }
        }
    }

	private static Object validateObject(Object value) {
		if(value == null) return value;
		if(value instanceof List) {
			List listValue = (List) value;
			if(!listValue.isEmpty() && listValue.get(0) instanceof String) {
				if(listValue.get(0).equals("[]")) return null;
			}
		}
		return value;
	}
	public Object getNoRedirectResponse(String url, AuthConfig auth) {
		return getResponse(false, getHeaders(auth), url, auth);
	}
}

/**
 * This redirect strategt adds an Accept:*\/* header to the redirect. This forces the underlying http client to ignore
 * original headers. Used only for certain use cases (like when the redirect is a file download from AWS etc) and not by default
 *
 */
class RedirectStrategyWithoutOriginalHeaders extends DefaultRedirectStrategy{
	@Override
	public HttpUriRequest getRedirect(org.apache.http.HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
		HttpUriRequest redirect = super.getRedirect(request, response, context);
		if(!redirect.headerIterator().hasNext()){
			redirect.addHeader("Accept","*/*");
		}
		return redirect;
	}
}