package com.syncari.core.actions.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.ProxyConfig;
import com.syncari.connector.custom.CustomActionRestClient;
import com.syncari.connector.custom.CustomService;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.core.DataTransformer;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.actions.ActionTestResult;
import com.syncari.core.actions.CustomAction;
import com.syncari.core.actions.DefaultAction;
import com.syncari.core.config.AppConfig;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.HttpActionException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.BatchActionContext;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.CustomActionDefinitionRepoImpl;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;
import com.syncari.utils.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

// Design time and run time logic for HTTP Action
@Slf4j
@Component(ActionConstants.HTTP_ACTION)
public class HTTPAction extends DefaultAction implements CustomAction {
    private static String SYNCARI_USER_AGENT = "Syncari/v1 HTTP Client";
    
	private static List<String> PROTOCOL_WHITELIST = List.of("http", "https");
	private static List<String> DOMAIN_BLACKLIST = List.of("syncari.net", ".internal", "metadata.google.internal");

    @Autowired
    private TokenHelper tokenHelper;
    @Autowired
    private ConnectorService connectorService;
    @Autowired
    private DataServiceFactory dataServiceFactory;

    @Autowired
    private CustomActionDefinitionRepoImpl customActionDefinitionRepoImpl;

    @Autowired
    private DataTransformer dataTransformer;

    @Autowired
    private CustomService customService;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private AppConfig appConfig;


	protected SyncariEntityDataRestClient restClient;

    // This is for testing
    public void setRestClient(SyncariEntityDataRestClient restClient) {
        this.restClient = restClient;
    }
    
	public SyncariEntityDataRestClient getRestClient() {
		if (this.restClient == null) {
			log.info("Creating CustomActionRestClient with {} {} {}", appConfig.isProxyEnabled(), appConfig.getProxyHost(),
					appConfig.getProxyPort());
			this.restClient = appConfig.isProxyEnabled()
					? new CustomActionRestClient(new ObjectMapper(),
							new ProxyConfig(appConfig.getProxyHost(), appConfig.getProxyPort()))
					: new CustomActionRestClient(new ObjectMapper());
		}
		return restClient;
	}

    private Optional<Connector> handleAuth(UriComponentsBuilder uriBuilder, Map<String, String> headers, AuthenticationInfo authInfo,
                                           Optional<GenericActionConfig> config) {
        // get the credential info
        Optional<Connector> credentialMaybe = StringUtils.isEmpty(authInfo.getCredentialId()) ? Optional.empty() : connectorService.find(authInfo.getCredentialId());
        if(credentialMaybe.isEmpty() && config.isPresent()  && config.get().getConfigMap().containsKey("authSynapseId")
                && !StringUtils.isBlank(config.get().getConfigMap().getOrDefault("authSynapseId", "").toString())) {
            credentialMaybe = connectorService.find(config.get().getConfigMap().get("authSynapseId").toString());
        }
		if (credentialMaybe.isPresent() 
				&& (credentialMaybe.get().isSyncariConnector()
						|| Constants.IMPORTED_FILES.equalsIgnoreCase(credentialMaybe.get().getName()))) {
			return Optional.empty();
		}
        credentialMaybe.ifPresent(credential -> {
            if (AuthType.ApiKey.equals(credential.getAuthType())) {
                uriBuilder.queryParam("api_key", credential.getAuthConfig().getToken());
                if (!StringUtils.isBlank(credential.getAuthConfig().getToken())) {
                    headers.put("X-API-KEY", credential.getAuthConfig().getToken());
                }
            }
            credential.getAuthConfig().setAdditionalHeaders(headers);
        });
        return credentialMaybe;
    }

    private Map<String, Object> evaluateVariables(Map<String, Object> variables, Map<String, Boolean> variableTypes, GraphContext context) {
        // Preserve original data types and only resolve string values that contain tokens

        return variables.entrySet().stream().filter(e -> e.getValue() != null).collect(Collectors.toMap(Map.Entry::getKey,
                var -> {
                    Object value = var.getValue();
                    
                    // Only resolve if it's a string that contains tokens, otherwise preserve original type and value
                    if (value instanceof String && TokenHelper.hasTokens(value.toString())) {
                        final Object resolvedValue = tokenHelper.resolveTokensObject(context, value.toString());
                        if (variableTypes.containsKey(var.getKey())) {
                            final Datatype datatype = DatatypeFactory.getDatatype(var.getKey());
                            if (String.class.isInstance(resolvedValue) && datatype.getName().equals(ObjectType.VALUE.getName())) {
                                return ObjectType.VALUE.convertFromJsonString(String.class.cast(resolvedValue));
                            }
                        }
                        return resolvedValue == null ? "" : resolvedValue;
                    } else {
                        // Preserve original value and type (boolean, number, etc.)
                        return value;
                    }
                }));
    }

    private HTTPResult handleResponse(ResponseEntity<String> response) {
        restClient.checkResponseNonStrict(response); // TODO: Check this, error in the response is bubbled up

        HTTPResult result = new HTTPResult();
        result.setStatusCode(response.getStatusCodeValue());
        result.setHeaders(response.getHeaders());
        result.setBodyString(response.getBody());
        if(StringUtils.isBlank(response.getBody())) return result;

        log.debug("Response Code {} Response Body {}", response.getStatusCode(), response.getBody());

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            if (jsonNode.isArray()) {
                result.setBody(objectMapper.convertValue(jsonNode, new TypeReference<List<Object>>() {}));
            } else {
                result.setBody(objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>(){}));
            }
            return result;
        } catch (Exception e1) {
            throw new RuntimeException(e1);
        }
    }

    public Pair<RequestEntity<String>, ResponseEntity<String>> execute(HttpActionProperties actionProperties, Map<String, Object> context,
        boolean returnPayLoadOnError, Optional<GenericActionConfig> config) {
        Timer timer = new Timer(1000, "HttpAction::execute", log);
    	context.forEach((k, v) -> {
    		if(v != null && v instanceof String) {
    			context.put(k, StringEscapeUtils.escapeJava(v.toString().trim()));
    		}
    	});

        var endpoint = getResolvedAndEncodedEndpoint(actionProperties.getEndPoint(), context);
        validateEndpoint(endpoint);
        log.debug("Encoded Endpoint: {}", endpoint);
        var body = tokenHelper.resolveJTwigToken(context, actionProperties.getBody()).x;
        var headers = actionProperties.getHeaders().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, var -> tokenHelper.resolveJTwigToken(context, var.getValue()).x
        ));
        //Explicitly add the user agent
        headers.put("User-Agent", SYNCARI_USER_AGENT);

        var uriBuilder = UriComponentsBuilder.fromUriString(endpoint);
        var connector = handleAuth(uriBuilder, headers, actionProperties.getAuthenticationInfo(), config);
        // Add customSynapseHeaders if required
        headers = addCustomSynapseHeaders(headers, connector);

        // update endpoint with api_key if any added
        final URI encodedURI = uriBuilder.build(true).toUri();
        endpoint = encodedURI.toString();

        var authConfig = connector.map(Connector::getAuthConfig).orElse(new AuthConfig().setAdditionalHeaders(headers));
        HttpHeaders requestHeaders = getRestClient().getHeaders(authConfig);
        RequestEntity<String> requestEntity = new RequestEntity<>(body,requestHeaders , actionProperties.getMethod(), encodedURI);
        Map<String, Object> responseMap = Map.of();
        Pair result;
        // this is to support to no auth custom actions
        try {
            if (connector.isPresent() && dataServiceFactory.isRestClientService(connector.get().getMetadata())) {
                var restService = dataServiceFactory.getRestClientService(connector.get().getMetadata());
				var restClient = appConfig.isProxyEnabled()
						? restService.getRestClient(new ProxyConfig(appConfig.getProxyHost(), appConfig.getProxyPort()))
						: restService.getRestClient();
                requestEntity = new RequestEntity<>(body, restClient.getHeaders(authConfig), actionProperties.getMethod(), encodedURI);
                switch (actionProperties.getMethod()) {
	                case POST:
	                    result = Pair.of(requestEntity, restClient.postRaw(endpoint, body, authConfig));
	                    break;
	                case GET:
	                    result = Pair.of(requestEntity, restClient.getResponse(endpoint, authConfig));
	                    break;
	                case PUT:
	                    result = Pair.of(requestEntity, restClient.put(endpoint, body, authConfig));
	                    break;
	                case DELETE:
	                    result = Pair.of(requestEntity, restClient.delete(endpoint, authConfig));
	                    break;
	                case PATCH:
	                    result = Pair.of(requestEntity, restClient.patch(endpoint, body, authConfig));
	                    break;
	                default:
	                    throw new RuntimeException("Invalid Method " + actionProperties.getMethod());
	            }
	        }else {
	            switch(actionProperties.getMethod()) {
	                case POST:
	                    result = Pair.of(requestEntity, getRestClient().postRaw(endpoint, body, authConfig));
	                    break;
	                case GET:
	                    result = Pair.of(requestEntity, getRestClient().getNoRedirectResponse(endpoint, authConfig));
	                    break;
	                case PUT:
	                    result = Pair.of(requestEntity, getRestClient().put(endpoint, body, authConfig));
	                    break;
	                case DELETE:
	                    result = StringUtils.isBlank(body) ? Pair.of(requestEntity, getRestClient().delete(endpoint, authConfig)) :
	                            Pair.of(requestEntity, getRestClient().delete(endpoint, body, authConfig));
	                    break;
	                case PATCH:
	                    result = Pair.of(requestEntity, getRestClient().patch(endpoint, body, authConfig));
	                    break;
	                default:
	                    throw new RuntimeException("Invalid Method " + actionProperties.getMethod());
	            }
	        }
        } catch (Exception e) {
			timer.close();
			if(returnPayLoadOnError) {
				HttpClientErrorException clientError = null;
				if (e instanceof HttpClientErrorException) {
					clientError = (HttpClientErrorException) e;
				} else if (e.getCause() instanceof HttpClientErrorException) {
					clientError = (HttpClientErrorException) e.getCause();
				}
				if (clientError != null) {
					if(clientError.getStatusCode() == HttpStatus.PROXY_AUTHENTICATION_REQUIRED) {
						return Pair.of(requestEntity, ResponseEntity.status(HttpStatus.FORBIDDEN).body(String.format(i18n("forbidden_http_request"))));
					} else {
						return Pair.of(requestEntity, ResponseEntity.status(clientError.getStatusCode())
								.headers(clientError.getResponseHeaders()).body(clientError.getResponseBodyAsString()));
					}
				}
			}
			throw new HttpActionException(e).setRequest(requestEntity);
		}
        timer.close();
        return result;
    }

    private Map<String, String> addCustomSynapseHeaders(Map<String, String> headers,Optional<Connector> connector){
        Map<String, String> httpHeaders = new HashMap<>();
        if (MapUtils.isNotEmpty(headers)){
            httpHeaders.putAll(headers);
        }
        if(connector.isPresent() && connector.get().getMetadata().isCustom()){
            Map<String, String> customHeaders = customService.getHeaders(dataTransformer.toConnectorInfo(connector.get()));
            customHeaders.entrySet().forEach(e -> httpHeaders.put(e.getKey(), e.getValue()));
            AuthConfig config = connector.get().getAuthConfig();
            config.setAdditionalHeaders(httpHeaders);
        }
        return httpHeaders;

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

    public ActionResult execute(GenericActionConfig actionConfig, GraphContext context) {
        HttpActionProperties actionProperties = (HttpActionProperties) actionConfig.getActionProperties();
        var configMap = actionConfig.getConfigMap();
        ActionDefinition retrievedDefinition = context.cache("customActionDefinition" + configMap.get("configId"), () ->
                customActionDefinitionRepoImpl.findByObjectId(configMap.get("configId").toString())
                        .orElseThrow(() -> new NotFoundException(String.format("Unable to find actionDefinition for configId %s", configMap.get("configId")))));
        actionConfig.setActionDefinition(retrievedDefinition);
        Map<String, Boolean> variableTypeMap = actionConfig.getActionDefinition()
                .getConfiguration()
                .stream()
                .collect(Collectors.toMap(a -> a.getName(), a -> a.isMultiValuedVariable()));
        var evaluatedContext = evaluateVariables(actionConfig.getConfigMap(), variableTypeMap, context);
        if (actionProperties.isBatch()) {
            //Batch Operation
            Integer batchSize = actionProperties.getBatchSize();
            BatchActionContext batchActionContext = context.getBatchActionContext();

            if (batchActionContext.shouldRunActions()) {
                List<Object> actualList = batchActionContext.get(context.getCurrentNode().getId());
                int lastIndex = actualList.size() < batchSize ? actualList.size() : batchSize;
                List<List<Object>> lists = Lists.partition(actualList, lastIndex);
                List<HttpHeaders> responseHeaders = new ArrayList<>();
                List<String> urls = new ArrayList<>();
                List<String> payloads = new ArrayList<>();
                List<Map<String, Object>> actionParams = new ArrayList<>();
                lists.stream()
                        .filter(list -> !CollectionUtils.isEmpty(lists))
                        .forEach(list -> {
                          Map<String, Object> toPost = generateBatchedContext(list, evaluatedContext, variableTypeMap);
                          try {
                            var response = execute(actionProperties, toPost, false, Optional.of(actionConfig));
                            responseHeaders.add(response.x.getHeaders());
                            urls.add(response.x.getUrl().toString());
                            payloads.add(response.x.getBody());
                            actionParams.add(toPost);
                            var responseMap = handleResponse(response.y);
                            batchActionContext.addBatchResponse(responseMap.getBody());
                          }catch (HttpActionException e) {
                            responseHeaders.add(e.getRequest().getHeaders());
                            urls.add(e.getRequest().getUrl().toString());
                            payloads.add(e.getRequest().getBody());
                            actionParams.add(toPost);
                            throw (RuntimeException) e.getCause();
                          }
                        });

                context.recordNodeInputs("headers", responseHeaders);
                context.recordNodeInputs("url", urls);
                context.recordNodeInputs("payload", payloads);
                context.recordNodeInputs("actionParams", actionParams);
                return new ActionResult(true, batchActionContext.getBatchResponse());

            }else{
                //collect mode
                Map<String,Object> map = new HashMap<>();
                for(Map.Entry<String,Object> entry : evaluatedContext.entrySet()){
                    if(variableTypeMap.containsKey(entry.getKey())){
                        map.put(entry.getKey(), entry.getValue());
                    }
                }
                batchActionContext.updateBatchContext(context.getCurrentNode(),map);
                return new ActionResult(true, evaluatedContext);
            }

        }else{
            try {
              var response= execute(actionProperties, evaluatedContext, false, Optional.of(actionConfig));
              recordActionInputs(context, evaluatedContext, response.getX());
              var responseMap = handleResponse(response.y);
              if (context.getCurrentNode() != null) {
                context.put(String.format("Headers From Action %s", context.getCurrentNode().getName()), responseMap.getHeaders());
                context.put(String.format("Status Code From Action %s", context.getCurrentNode().getName()), responseMap.getStatusCode());
              }
              return new ActionResult(true, responseMap.getBody());
            }catch (HttpActionException e) {
              recordActionInputs(context, evaluatedContext, e.getRequest());
              throw (RuntimeException) e.getCause();
            }
        }
    }

    private static void recordActionInputs(GraphContext context, Map<String, Object> evaluatedContext, RequestEntity<String> req) {
        context.recordNodeInputs("headers", req.getHeaders());
        context.recordNodeInputs("url", req.getUrl().toString());
        context.recordNodeInputs("payload", req.getBody());
        context.recordNodeInputs("actionParams", evaluatedContext);
    }

    private Map<String, Object> generateBatchedContext(List<Object> subList, Map<String, Object> evaluatedContext, Map<String, Boolean> variableTypeMap) {
        Map<String, Object> map = new HashMap<>(evaluatedContext);
        Map<String, Object> subMap = new HashMap<>();
        for (Object batchableObject : subList) {
            if (batchableObject instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) batchableObject).entrySet()) {
                    if (variableTypeMap.containsKey(entry.getKey()) && variableTypeMap.get(entry.getKey())) {
                        if (subMap.containsKey(entry.getKey())) {
                            List<Object> list = (List<Object>) subMap.get(entry.getKey());
                            list.add(entry.getValue());
                            subMap.put((String) entry.getKey(),list);
                        }else{
                            List<Object> list = new ArrayList<>();
                            list.add(entry.getValue());
                            subMap.put((String)entry.getKey(),list);
                        }
                    }else{
                        subMap.put((String)entry.getKey(),entry.getValue());
                    }
                }
            }
        }
        map.putAll(subMap);
        return map;
    }

    @Override
    public void validate(ActionDefinition actionDefinition) {

        HttpActionProperties properties = (HttpActionProperties) actionDefinition.getProperties();

        validateCondition(StringUtils.isBlank(properties.getEndPoint()) ,
                String.format(i18n("invalid_param_http_action"), "Endpoint"));

        validateCondition(properties.getMethod() == null,
                String.format(i18n("invalid_param_http_action"), "HTTP Method"));

        if(!StringUtils.isBlank(properties.getAuthenticationInfo().getCredentialId())) {
        	var connector = connectorService.find(properties.getAuthenticationInfo().getCredentialId()); 
        	validateCondition(connector.isEmpty() || !connector.get().isActive(),
        			String.format(i18n("invalid_param_http_action"), "Credential"));
        }

        // check if variables are well formed
        validateCondition(!actionDefinition.getConfiguration().stream().filter(f -> StringUtils.isBlank(f.getName())).findFirst().isEmpty(), i18n("invalid_variable_name_action"));

        // check if variable names are unique
        Set<String> checkDups = new HashSet<>();
        var dupNames = actionDefinition.getConfiguration().stream()
                .map(FunctionConfiguration::getName).filter(name -> !checkDups.add(name)).collect(Collectors.toSet());
        validateCondition(dupNames.size() > 0, String.format(i18n("duplicate_variable_name"), String.join(",", dupNames)));
        
		validateEndpoint(properties.getEndPoint());
		validateBody(properties.getBody());
    }
    
    private void validateEndpoint(String endPoint) {
    	validateCondition(StringUtils.isBlank(endPoint) ,
                String.format(i18n("invalid_param_http_action") + ", Endpoint - %s", "Endpoint", endPoint));
		if (!endPoint.trim().startsWith("{{")) { // Skip static validation if starting with variables
			String domainName = extractDomainName(endPoint);
			validateCondition((domainName == null),
					String.format(i18n("invalid_param_http_action") + ", Endpoint - %s", "Endpoint", endPoint));
			domainName = domainName.toLowerCase();

			// disallow endPoint having IPAddress
			validateCondition((InetAddresses.isUriInetAddress(domainName)),
					String.format(i18n("invalid_param_http_action_inet") + " Endpoint %s ", endPoint));

			// disallow endPoint having certain domain names
			for(String blackListedDomain: DOMAIN_BLACKLIST) {
				validateCondition((domainName.contains(blackListedDomain)),
						String.format(i18n("invalid_param_http_action") + ", Endpoint - %s",  "Endpoint", endPoint));
			}

			String scheme = extractScheme(endPoint);
			validateCondition((scheme == null),
					String.format(i18n("invalid_param_http_action") + ", Endpoint - %s",  "Endpoint", endPoint));
			scheme = scheme.toLowerCase();
			// allow only certain protocols
			validateCondition(!PROTOCOL_WHITELIST.contains(scheme),
					String.format(i18n("invalid_param_http_action") + ", Endpoint - %s",  "Endpoint", endPoint));
		}
    }
    
    private void validateBody(String body) {
      if (StringUtils.isNotBlank(body)) {
          var tokens = tokenHelper.extractTokensFromTemplateWithoutWrapper(body);
          List<String> filteredStrings = tokens.stream()
              .filter(s -> s.length() != s.trim().length()) // Trimming changes length => it had leading/trailing whitespace
              .collect(Collectors.toList());
          validateCondition(!filteredStrings.isEmpty(),
              i18n("invalid_token_http_body", filteredStrings));
      }
  }

    private String extractDomainName(String endPoint) {
        try{
            var uri = UriComponentsBuilder.fromUriString(endPoint).build();
            return uri.getHost();
        }catch (Exception e){
            log.error(String.format("Error occured while extracting Domain Name %s",endPoint), e);
        }
        return endPoint;
    }
    
    private String extractScheme(String endPoint) {
        try{
            var uri = UriComponentsBuilder.fromUriString(endPoint).build();
            return uri.getScheme();
        }catch (Exception e){
            log.error(String.format("Error occured while extracting Scheme %s",endPoint), e);
        }
        return endPoint;
    }

    @Override
    public ActionTestResult test(ActionDefinition actionDefinition, Map<String, Object> contextMap) {
        HttpActionProperties properties = (HttpActionProperties) actionDefinition.getProperties();
        var requestResponse= execute(properties, contextMap, true, Optional.empty());
        var request = requestResponse.x;
        var response = requestResponse.y;

        return new HTTPActionTestResult()
                .setRequest(
                        new HTTPActionTestResult.Request().setMethod(request.getMethod()).setBody(request.getBody()).setEndpoint(request.getUrl().toString()).setRequestHeaders(request.getHeaders()))
                .setResponse(
                        new HTTPActionTestResult.Response().setHttpStatus(response.getStatusCode()).setResponseHeaders(response.getHeaders()).setBody(response.getBody()));
    }

    @Override
    public boolean resolve(ActionDefinition actionDefinition) {

        var httpProps = (HttpActionProperties) actionDefinition.getProperties();
        boolean toReturn = false;
        if((StringUtils.isEmpty(httpProps.getAuthenticationInfo().getMetadataId())
                && StringUtils.isEmpty(httpProps.getAuthenticationInfo().getCredentialId()))
        || (!StringUtils.isEmpty(httpProps.getAuthenticationInfo().getMetadataId())
                && StringUtils.isEmpty(httpProps.getAuthenticationInfo().getCredentialId()))) {
            return true;
        }
        List<Connector> connectors = connectorService.findByMetadata(httpProps.getAuthenticationInfo().getMetadataId());
        if (connectors.size() == 1) {
            httpProps.getAuthenticationInfo().setCredentialId(connectors.get(0).getId());
            toReturn = true;
        }
        return toReturn;
    }

    // Action validation during pipeline design
    @Override
    public void validate(ValidationContext validationContext) {
    	var errors = validateWithoutException(validationContext);
    	if(errors != null && !errors.isEmpty()) {
    		throw new SyncariValidationException(errors.get(0).getMessage());
    	}
    }
    
    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
        return super.validateWithoutException(validationContext);
    }

    @Override
    public boolean isBatchable() {
        return false;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return null;
    }
}
