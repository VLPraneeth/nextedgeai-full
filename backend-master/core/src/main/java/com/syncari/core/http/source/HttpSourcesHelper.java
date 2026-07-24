package com.syncari.core.http.source;

import static com.syncari.utils.I18n.i18n;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.HttpSourceConfigInfo;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.ProxyConfig;
import com.syncari.connector.custom.CustomActionRestClient;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.data.HTTPSourceResult;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.core.config.AppConfig;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.Timer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class HttpSourcesHelper {
    private static String SYNCARI_USER_AGENT = "Syncari/v1 HTTP Client";
    
	@Autowired
	private AppConfig appConfig;
	@Autowired
	TokenHelper tokenHelper;
	@Autowired
	private ObjectMapper mapper;
	
	protected SyncariEntityDataRestClient restClient;
	
	public SyncariEntityDataRestClient getRestClient() {
		if (this.restClient == null) {
			log.info("Creating CustomActionRestClient with {} {} {}", appConfig.isProxyEnabled(), appConfig.getProxyHost(),
					appConfig.getProxyPort());
			this.restClient = appConfig.isProxyEnabled()
					? new CustomActionRestClient(mapper,
							new ProxyConfig(appConfig.getProxyHost(), appConfig.getProxyPort()))
					: new CustomActionRestClient(mapper);
		}
		return restClient;
	}

    private void handleAuth(UriComponentsBuilder uriBuilder, Map<String, String> headers, ConnectorInfo connectorInfo) {
    	if (AuthType.ApiKey.equals(connectorInfo.getAuthType())) {
    		uriBuilder.queryParam("api_key", connectorInfo.getAuthConfig().getAccessToken());
    		headers.put("X-API-KEY", connectorInfo.getAuthConfig().getAccessToken());
    		//Reset access token
    		connectorInfo.getAuthConfig().setAccessToken(null);
    	}
    }

    private HTTPSourceResult handleResponse(ResponseEntity<String> response) {
        HTTPSourceResult result = new HTTPSourceResult();
        result.setStatusCode(response.getStatusCodeValue());
        result.setStatus(response.getStatusCode().name());
        result.setHeaders(response.getHeaders());
        result.setBodyString(response.getBody());
        result.setCalledAt(ZonedDateTime.now());
        if(StringUtils.isBlank(response.getBody())) return result;

        log.debug("Response Code {} Response Body {}", response.getStatusCode(), response.getBody());

        try {
            JsonNode jsonNode = mapper.readTree(response.getBody());
            result.setBody(jsonNode);
            return result;
        } catch (Exception e1) {
          log.info("Error occured Response Code {} Response Body {}", response.getStatusCode(), response.getBody());
          throw new RuntimeException(e1);
        }
    }
    
    public HTTPSourceResult execute(ConnectorInfo connector, HttpSourceConfigInfo httpConfig, Map<String, Object> context, boolean returnPayLoadOnError) {
        Timer timer = new Timer(1000, "HttpServiveHelper::execute", log);
        
        var endpoint = getResolvedAndEncodedEndpoint(httpConfig.getEndpoint(), context);
        log.debug("Encoded Endpoint: {}", endpoint);
        var body = tokenHelper.resolveJTwigToken(context, httpConfig.getBody()).x;
        var headers = httpConfig.getHeaders().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, var -> tokenHelper.resolveJTwigToken(context, var.getValue()).x
        ));
        //Explicitly add the user agent
        headers.put("User-Agent", SYNCARI_USER_AGENT);
        var uriBuilder = UriComponentsBuilder.fromUriString(endpoint);
        handleAuth(uriBuilder, headers, connector);
        final URI encodedURI = uriBuilder.build(true).toUri();
        endpoint = encodedURI.toString();
        AuthConfig authConfig = connector.getAuthConfig();
        if(authConfig != null) {
        	authConfig = authConfig.setAdditionalHeaders(headers);
        }
        ResponseEntity<String> result = null;
        try {
			switch(HttpMethod.valueOf(httpConfig.getMethod())) {
			case POST:
				result = getRestClient().postRaw(endpoint, body, authConfig);
				break;
			case GET:
				result = (ResponseEntity<String>) getRestClient().getResponse(endpoint, authConfig);
				break;
			case PUT:
				result = getRestClient().put(endpoint, body, authConfig);
				break;
			case DELETE:
				result = StringUtils.isBlank(body) ? getRestClient().delete(endpoint, authConfig) :
					getRestClient().delete(endpoint, body, authConfig);
				break;
			case PATCH:
				result = getRestClient().patch(endpoint, body, authConfig);
				break;
			default:
				timer.close();
				throw new RuntimeException("Invalid Method " + httpConfig.getMethod());
			}
		} catch (Exception e) {
			if(returnPayLoadOnError) {
				HttpClientErrorException clientError = null;
				if (e instanceof HttpClientErrorException) {
					clientError = (HttpClientErrorException) e;
				} else if (e.getCause() instanceof HttpClientErrorException) {
					clientError = (HttpClientErrorException) e.getCause();
				}
				if (clientError != null) {
					if(clientError.getStatusCode() == HttpStatus.PROXY_AUTHENTICATION_REQUIRED) {
						result = ResponseEntity.status(HttpStatus.FORBIDDEN).body(String.format(i18n("forbidden_http_request")));
					} else {
						result = ResponseEntity.status(clientError.getStatusCode())
								.headers(clientError.getResponseHeaders()).body(clientError.getResponseBodyAsString());
					}
				}
			}
			if(result == null) {
				throw e;
			}
		} finally {
			timer.close();
		}
        // Set request headers used
        HTTPSourceResult res = handleResponse(result);
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach((k,v) -> {
            httpHeaders.add(k,v);
        });
        res.setRequestHeaders(httpHeaders);
        return res;
    }

    public String getResolvedAndEncodedEndpoint(String rawEndpoint, Map<String, Object> context){

        // we need to resolve token as single string before encoding query params as there can be if condition within url as shown below
        // e.g. https://example.com/rest/v1/actions/ticket/{{ticketId}}{% if user %}?id={{user}}{% endif %}
        String resolvedEndpoint = tokenHelper.resolveJTwigToken(context, rawEndpoint).x;
        // this is to reverse the escape we do after the first level token is resolved
        resolvedEndpoint = StringEscapeUtils.unescapeJava(resolvedEndpoint);
        var uriBuilder = UriComponentsBuilder.fromUriString(resolvedEndpoint);

        // Known issue
        // Since we are resolving tokens before building the URI the case where queryParam value contains `&` will fail
        // e.g https://icanhazdadjoke.com/search?term=d&d -> will not work as anything after & is considered separate query param
        // To Fix this the '&' in potential values can be replaced with %26

        // resolve and encode query params
        MultiValueMap<String, String> encodedQueryParams = new LinkedMultiValueMap<>();
        uriBuilder.build().getQueryParams().forEach((k, v) -> {
            // TODO: Should we encode query param key?
            List<String> encodedParamValues = new ArrayList<>();
            v.forEach(value -> {
                if(!StringUtils.isBlank(value)) {
                    // special case for + -> replace all + with %2B otherwise decode will decode + as ' ' (space)
                    value = value.replaceAll("\\+", "%2B");
                    // try and decode if already encoded - to avoid double encoding
                    String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
                    // encode again
                    String encoded = URLEncoder.encode(decoded, StandardCharsets.UTF_8);
                    encodedParamValues.add(encoded);
                }
            });
            encodedQueryParams.put(k, encodedParamValues);
        });
        List<String> pathVals = new LinkedList<>();
        String path = uriBuilder.build().getPath();
        if (StringUtils.isNotEmpty(path)){
            String [] pathValues = path.split("/");
            if (null != pathValues){
                for (String p : pathValues){
                    pathVals.add(URLEncoder.encode(p, StandardCharsets.UTF_8));
                }
            }
        }

        var uriBuilderWithEncodedParams = uriBuilder.cloneBuilder()
                .replaceQueryParams(encodedQueryParams);
        if (!CollectionUtils.isEmpty(pathVals)){
            String pathToReplaceWith = path.endsWith("/") ? StringUtils.join(pathVals,"/") + "/" : StringUtils.join(pathVals,"/");
            uriBuilderWithEncodedParams.replacePath(pathToReplaceWith);
        }

        return uriBuilderWithEncodedParams.build(true).toUri().toString();
    }
}
