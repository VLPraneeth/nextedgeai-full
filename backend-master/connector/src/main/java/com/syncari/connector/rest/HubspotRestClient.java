package com.syncari.connector.rest;

import static java.util.Map.entry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.utils.ThrowingSupplier;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.EntitySchema;

import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;

import static com.syncari.connector.ConnectorHelper.withBackoffAndErrorHandling;

@Slf4j
public class HubspotRestClient extends SyncariEntityDataRestClient {
    private static final String ASSOCIATED_COMPANY_IDS = "associatedCompanyIds";
    private static final String ASSOCIATED_CONTACT_IDS = "associatedVids";

    private static final Map<String, String> ENGAGEMENT_FLATTEN_MAP = Map.ofEntries(
        entry("hs_object_id", "id"),
        //"portalId"
        //"active"
        entry("hs_createdate", "engagement.createdAt"),
        entry("hs_lastmodifieddate", "engagement.lastUpdated"),
        entry("hubspot_owner_id", "engagement.ownerId"),
        entry("timestamp", "engagement.timestamp"),
        entry("hs_engagement_type", "engagement.type"),
        //"timestamp",
        entry("associatedcompanyid", "associations.companyIds"),
        entry("associatedVids", "associations.contactIds")
    );

    private static final Map<String, String> ENGAGEMENT_EMAIL_FLATTEN_MAP = Map.ofEntries(
        entry("hs_email_from_email", "metadata.from.email"),
        entry("hs_email_from_firstname", "metadata.from.firstName"),
        entry("hs_email_from_lastname", "metadata.from.lastName"),
        entry("hs_email_to_email", "metadata.to.$.email"),
        entry("hs_email_to_firstname", "metadata.to.$.firstName"),
        entry("hs_email_to_lastname", "metadata.to.$.lastName"),
        entry("hs_email_cc_email", "metadata.cc.$.email"),
        entry("hs_email_cc_firstname", "metadata.cc.$.firstName"),
        entry("hs_email_cc_lastname", "metadata.cc.$.lastName"),
        entry("hs_email_bcc_email", "metadata.bcc.$.email"),
        entry("hs_email_bcc_firstname", "metadata.bcc.$.firstName"),
        entry("hs_email_bcc_lastname", "metadata.bcc.$.lastName"),
        entry("hs_email_subject", "metadata.subject"),
        entry("hs_email_html", "metadata.html"),
        entry("hs_email_text", "metadata.text")
    );

    private static final Map<String, String> ENGAGEMENT_NOTE_FLATTEN_MAP = Map.ofEntries(
        entry("hs_note_body", "metadata.body")
    );

    private static final Map<String, String> ENGAGEMENT_TASK_FLATTEN_MAP = Map.ofEntries(
        entry("hs_task_body", "metadata.body"),
        entry("hs_task_subject", "metadata.subject"),
        entry("hs_task_status", "metadata.status"),
        entry("hs_task_for_object_type", "metadata.forObjectType")
    );

    private static final Map<String, String> ENGAGEMENT_CALL_FLATTEN_MAP = Map.ofEntries(
        entry("hs_call_to_number", "metadata.toNumber"),
        entry("hs_call_from_number", "metadata.fromNumber"),
        entry("hs_call_status", "metadata.status"),
        entry("hs_call_external_id", "metadata.externalId"),
        entry("hs_call_duration", "metadata.durationMilliseconds"),
        entry("hs_call_external_account_id", "metadata.externalAccountId"),
        entry("hs_call_recording_url", "metadata.recordingUrl"),
        entry("hs_call_body", "metadata.body"),
        entry("hs_call_disposition", "metadata.disposition")
    );

    private static final Map<String, String> ENGAGEMENT_MEETING_FLATTEN_MAP = Map.ofEntries(
        entry("hs_meeting_body", "metadata.body"),
        entry("hs_meeting_start_time", "metadata.startTime"),
        entry("hs_meeting_end_time", "metadata.endTime"),
        entry("hs_meeting_title", "metadata.title")
    );


    public HubspotRestClient(JsonParserConfig parserConfig){
        super(parserConfig);
    }

    public HubspotRestClient(JsonParserConfig parserConfig, ObjectMapper objectMapper){
        super(parserConfig, objectMapper);
    }

    public HubspotRestClient(){
        super();
    }

    @Override
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
                populateAssociations(ctx, e, parserConfig.getAssociationsPath().replace("{i}", String.valueOf(i)));
            } else {
                // TODO: This would be an array of properties
            }
            extracted.add(e);
        }
        return extracted;
    }

    public List<EntityData> getContactsByIds(String url, List<String> ids, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        ResponseEntity<String> response = getResponse(url, connector, tokenHandler);
        List<EntityData> extracted = new ArrayList<>();
        ReadContext ctx = JsonPath.parse(response.getBody());
        ids.stream().forEach(id -> {
            try {
                Map<String, Object> obj = ctx.read(id);
                var e = new EntityData();
                for (String key : obj.keySet()) {
                    if (key.equalsIgnoreCase(parserConfig.getIdFieldName())) {
                        e.setId(id);
                    }
                }
                Map<String, Object> properties = ctx.read(id + ".properties");
                for (String key : properties.keySet()) {
                    Object value = ctx.read(id + ".properties." + key + ".value");
                    e.addValue(key, value);
                }
                extracted.add(e);
            }catch(com.jayway.jsonpath.PathNotFoundException ex){
                log.warn("No contact record found for id {}",id);
            }
        });
        return extracted;
    }

    public Optional<EntityData> getHubspotObjectById(String url, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        return getHubspotObjectById(url, connector, null, tokenHandler);
    }
    
    public Optional<EntityData> getHubspotObjectById(String url, ConnectorInfo connector, String entityPath, Supplier<AuthConfig> tokenHandler) {
        log.info("Hubspot GetById {}",url);
        try {
            ResponseEntity<String> response = getResponse(url, connector, tokenHandler);
            log.info("Hubspot GetById Response Status: {}", response.getStatusCode().toString());
            log.debug("Hubspot GetById Response Body: {}", response.getBody());
            ReadContext ctx = JsonPath.parse(response.getBody());
            if (StringUtils.isNotEmpty(entityPath)) {
                Map<String, Object> entityResponse = ctx.json();
                ctx = JsonPath.parse(entityResponse.get(entityPath));
            }
            var e = new EntityData();
            Map<String, Object> properties = ctx.json();
            for (String key : properties.keySet()) {
                Object value = ctx.read(key);
                e.addValue(key, value);
            }
            e.setId(properties.get("id") == null? null : properties.get("id").toString());
            return Optional.of(e);
		} catch (NonRetriableException nor) {
			if (ErrorCodes.BAD_ENDPOINT.name().equals(nor.getErrorCode())) {
				return Optional.empty();
			} else {
				try {
					if (nor.getMessage() != null && nor.getMessage().contains("message")
							&& nor.getMessage().contains("error")) {
						Map row = objectMapper.readValue(nor.getMessage(), Map.class);
						String message = (String) row.get("message");
						log.info("Hubspot error messages received {} ", message);
						throw new NonRetriableException(nor.getErrorCode(), message, nor.getStatusCode().toString(),
								nor);
					}
					throw nor;
				} catch (JsonProcessingException ex) {
					log.error("Could not parse the response ", ex);
					throw new RuntimeException(ex.getMessage());

				} catch (Exception e) {
					log.error("Error in getting HubSpot response ", e);
					throw new RuntimeException(e.getMessage());
				}
			}
		}
    }

    private void populateAssociations(ReadContext ctx, EntityData e, String associationsPath) {
        // Extract the associated account for deal and set in entity data
        if("dealId".equalsIgnoreCase(parserConfig.getIdFieldName())) {
            List<String> associations = ctx.read(associationsPath+"."+ASSOCIATED_COMPANY_IDS);
            if(associations != null && !associations.isEmpty()) {
                e.addValue("associatedcompanyid", associations.get(0));
            }
            List<String> contactAssociations = ctx.read(associationsPath+"."+ASSOCIATED_CONTACT_IDS);
            if(contactAssociations != null && !contactAssociations.isEmpty()) {
                e.addValue(ASSOCIATED_CONTACT_IDS, contactAssociations);
            }

        }
    }

    public Optional<EntityData> getHubspotEngagementObject(String url, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        log.info("Hubspot GetById {}",url);
        try {
            ResponseEntity<String> response = getResponse(url, connector, tokenHandler);
            log.info("Hubspot GetById Response Status: {}", response.getStatusCode().toString());
            log.debug("Hubspot GetById Response Body: {}", response.getBody());
            Map respBody = objectMapper.readValue(response.getBody(), Map.class);
            EntityData ed = new EntityData("engagement");
            ed.setValues(respBody);
            return Optional.of(ed);
        } catch(NonRetriableException nor) {
            if (ErrorCodes.BAD_ENDPOINT.name().equals(nor.getErrorCode())) {
                return Optional.empty();
            } else {
                throw nor;
            }
        } catch(IOException e) {
            log.error(e.getMessage(),e);
            throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR.name(),e.getMessage(),"500");
        }
    }

    public static Map<String, Object> flattenEngagementResults(String engagementType, Map rawEngagementResponse) {
        Map<String, Object> flatEngagementObj = new HashMap<>();
        ENGAGEMENT_FLATTEN_MAP.forEach((fieldApiName, respKey) -> {
            parseAndGetResponseFields(rawEngagementResponse, flatEngagementObj, fieldApiName, respKey);
        });

        Map metadataMapToFlatten = null;
        switch (engagementType) {
            case "EMAIL":
            case "INCOMING_EMAIL":
            case "FORWARDED_EMAIL":
                metadataMapToFlatten = ENGAGEMENT_EMAIL_FLATTEN_MAP;
                break;
            case "NOTE":
                metadataMapToFlatten = ENGAGEMENT_NOTE_FLATTEN_MAP;
                break;
            case "TASK":
                metadataMapToFlatten = ENGAGEMENT_TASK_FLATTEN_MAP;
                break;
            case "MEETING":
                metadataMapToFlatten = ENGAGEMENT_MEETING_FLATTEN_MAP;
                break;
            case "CALL":
            default:
                metadataMapToFlatten = ENGAGEMENT_CALL_FLATTEN_MAP;
                break;
        }

        metadataMapToFlatten.forEach((fieldApiName, respKey) -> {
            parseAndGetResponseFields(rawEngagementResponse, flatEngagementObj, fieldApiName.toString(), respKey.toString());
        });
        return flatEngagementObj;
    }

    public static Map<String, Object> unFlattenEngagementObject(String engagementType, Map rawEngagementRequest) {
            Map<String, Object> unFlattenedEngagementObj = new HashMap<>();
        ENGAGEMENT_FLATTEN_MAP.forEach((fieldApiName, respKey) -> {
            parseAndPrepareRequest(rawEngagementRequest, unFlattenedEngagementObj, fieldApiName, respKey);
        });

        // HACK: The framework does not send the "hs_engagement_type" since it does not change for updates :(. 
        // Here we try to interpret the call type based on the parameters being sent.
        if (StringUtils.isEmpty(engagementType)) {
            Set<String> keys = rawEngagementRequest.keySet();
            if (keys.stream().anyMatch(x -> x.toLowerCase().startsWith("hs_email"))) {
                engagementType = "EMAIL";
            } else if (keys.stream().anyMatch(x -> x.toLowerCase().startsWith("hs_note"))) {
                engagementType = "NOTE";
            } else if (keys.stream().anyMatch(x -> x.toLowerCase().startsWith("hs_task"))) {
                engagementType = "TASK";
            } else if (keys.stream().anyMatch(x -> x.toLowerCase().startsWith("hs_meeting"))) {
                engagementType = "MEETING";
            } else if (keys.stream().anyMatch(x -> x.toLowerCase().startsWith("hs_call"))) {
                engagementType = "CALL";
            } else {
                engagementType = "";
            }
        }

        Map metadataMapToUnflatten = null;
        switch (engagementType) {
            case "EMAIL":
                metadataMapToUnflatten = ENGAGEMENT_EMAIL_FLATTEN_MAP;
                break;
            case "NOTE":
                metadataMapToUnflatten = ENGAGEMENT_NOTE_FLATTEN_MAP;
                break;
            case "TASK":
                metadataMapToUnflatten = ENGAGEMENT_TASK_FLATTEN_MAP;
                break;
            case "MEETING":
                metadataMapToUnflatten = ENGAGEMENT_MEETING_FLATTEN_MAP;
                break;
            case "CALL":
            default:
                metadataMapToUnflatten = ENGAGEMENT_CALL_FLATTEN_MAP;
                break;
        }

        metadataMapToUnflatten.forEach((fieldApiName, respKey) -> {
            parseAndPrepareRequest(rawEngagementRequest, unFlattenedEngagementObj, fieldApiName.toString(), respKey.toString());
        });
        return unFlattenedEngagementObj;
    }

    public EntityData postEngagement(String url, Map<String, Object> payload, HttpMethod httpMethod, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        try {
            return postEngagement(url, payload, httpMethod, connector.getAuthConfig());
        } catch (NonRetriableException e){
            if(shouldRetry(connector, tokenHandler, e)) {
                AuthConfig updatedAuth = tokenHandler.get();
                updateAuthConfig(connector.getAuthConfig(), updatedAuth);
                return postEngagement(url, payload, httpMethod, connector.getAuthConfig());
            }
            throw e;
        }
    }

    private static boolean shouldRetry(ConnectorInfo connector, Supplier<AuthConfig> tokenHandler, NonRetriableException e) {
        return ErrorCodes.ACCESS_DENIED.name().equals(e.getErrorCode()) && tokenHandler != null && connector.getAuthType() != AuthType.ApiKey;
    }

    public EntityData postEngagement(String url, Map<String, Object> payload, HttpMethod httpMethod, AuthConfig auth) {
        RestTemplate restTemplate = getTemplate();
        try {
            String payloadString = objectMapper.writeValueAsString(payload);
            log.info("HTTP POST at {}", url);
            log.debug("HTTP POST payload {}", payloadString);
            ThrowingSupplier<ResponseEntity<String>> supplier = ()-> restTemplate.exchange(url, httpMethod,
                new HttpEntity(payloadString, getHeaders(auth)), String.class);
            ResponseEntity<String> response = withBackoffAndErrorHandling(supplier);
            log.info("POST: HTTP Status {}",response.getStatusCode());
            log.debug(response.getBody());
            Map respBody = objectMapper.readValue(response.getBody(), Map.class);
            EntityData ed = new EntityData("engagement");
            ed.setValues(flattenEngagementResults(((Map) respBody.get("engagement")).get("type").toString(), respBody));
            ed.setId(((Map) respBody.get("engagement")).get("id").toString());
            return ed;
        }catch(HttpClientErrorException e){
            log.error(e.getMessage(),e);
            log.error(e.getResponseBodyAsString());
            throw e;
        }catch(IOException e){
            log.error(e.getMessage(),e);
            throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR.name(),e.getMessage(),"500");
        }
    }

    public EntityData postEntityObject(EntitySchema schema, String url, Map<String, Object> payload,
                                       HttpMethod httpMethod,  ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        try {
            return postEntityObject(schema, url, payload, httpMethod, connector.getAuthConfig());
        } catch (NonRetriableException e){
            if(shouldRetry(connector, tokenHandler, e)) {
                AuthConfig updatedAuth = tokenHandler.get();
                updateAuthConfig(connector.getAuthConfig(), updatedAuth);
                return postEntityObject(schema, url, payload, httpMethod, connector.getAuthConfig());
            }
            throw e;
        }
    }

    public EntityData postEntityObject(EntitySchema schema, String url, Map<String, Object> payload,
                                       HttpMethod httpMethod, AuthConfig auth) {
        RestTemplate restTemplate = getTemplate();
        try {
            String payloadString = objectMapper.writeValueAsString(payload);
            log.info("HTTP POST at {}", url);
            log.debug("HTTP POST payload {}", payloadString);
            ThrowingSupplier<ResponseEntity<String>> supplier = ()-> restTemplate.exchange(url, httpMethod,
                new HttpEntity(payloadString, getHeaders(auth)), String.class);
            ResponseEntity<String> response = withBackoffAndErrorHandling(supplier);
            log.info("POST: HTTP Status {}",response.getStatusCode());
            log.debug(response.getBody());
            Map respBody = objectMapper.readValue(response.getBody(), Map.class);
            EntityData ed = new EntityData(schema.getApiName());
            ed.setValues(respBody);
            ed.setId(respBody.get("id").toString());
            return ed;
        }catch(HttpClientErrorException e){
            log.error(e.getMessage(),e);
            log.error(e.getResponseBodyAsString());
            throw e;
        }catch(IOException e){
            log.error(e.getMessage(),e);
            throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR.name(),e.getMessage(),"500");
        }
    }

    public Map<String, String> getCallDispositionMap(String url, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        objectMapper = new ObjectMapper();
        ResponseEntity<String> responseEntity = getResponse(url, connector, tokenHandler);
        try {
            List<Map<String, Object>> dispositions = objectMapper.readValue(responseEntity.getBody(), TypeFactory.defaultInstance().constructCollectionType(List.class, Map.class));
            return dispositions.stream().filter(d -> d.containsKey("deleted") && !(boolean)d.get("deleted") && d.containsKey("label"))
                    .collect(Collectors.toMap(d -> (String)d.get("id"), d -> (String)d.get("label")));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to fetch call dispositions");
        }
    }

    public ResponseEntity<String> getResponse(String url, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        try {
            return getResponse(getHeaders(connector.getAuthConfig()), url, connector.getAuthConfig());
        } catch (NonRetriableException e){
            if(shouldRetry(connector, tokenHandler, e)) {
                AuthConfig updatedAuth = tokenHandler.get();
                updateAuthConfig(connector.getAuthConfig(), updatedAuth);
                return getResponse(getHeaders(connector.getAuthConfig()), url, connector.getAuthConfig());
            }
            throw e;
        }
    }

    public ResponseEntity<String> postRaw(String url, String body, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        try {
            return postRaw(url, body, connector.getAuthConfig());
        } catch (NonRetriableException e){
            if(shouldRetry(connector, tokenHandler, e)) {
                AuthConfig updatedAuth = tokenHandler.get();
                updateAuthConfig(connector.getAuthConfig(), updatedAuth);
                return postRaw(url, body, connector.getAuthConfig());
            }
            throw e;
        }
    }

    public EntityData post(String url, Map<String, Object> body, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        try {
            return post(url, body, connector.getAuthConfig());
        } catch (NonRetriableException e){
            if(shouldRetry(connector, tokenHandler, e)) {
                AuthConfig updatedAuth = tokenHandler.get();
                updateAuthConfig(connector.getAuthConfig(), updatedAuth);
                return post(url, body, connector.getAuthConfig());
            }
            throw e;
        }
    }

    public EntityData patch(String url, Map<String, Object> body, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        try {
            return patch(url, body, connector.getAuthConfig());
        } catch (NonRetriableException e){
            if(shouldRetry(connector, tokenHandler, e)) {
                AuthConfig updatedAuth = tokenHandler.get();
                updateAuthConfig(connector.getAuthConfig(), updatedAuth);
                return patch(url, body, connector.getAuthConfig());
            }
            throw e;
        }
    }

    public ResponseEntity<String> put(String url, String body, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        try {
            return put(url, body, connector.getAuthConfig());
        } catch (NonRetriableException e){
            if(shouldRetry(connector, tokenHandler, e)) {
                AuthConfig updatedAuth = tokenHandler.get();
                updateAuthConfig(connector.getAuthConfig(), updatedAuth);
                return put(url, body, connector.getAuthConfig());
            }
            throw e;
        }
    }

    public ResponseEntity<String> put(String url,  Map<String, Object> payload, ConnectorInfo connector, Supplier<AuthConfig> tokenHandler) {
        try {
            return put(url, payload, connector.getAuthConfig());
        } catch (NonRetriableException e){
            if(shouldRetry(connector, tokenHandler, e)) {
                AuthConfig updatedAuth = tokenHandler.get();
                updateAuthConfig(connector.getAuthConfig(), updatedAuth);
                return put(url, payload, connector.getAuthConfig());
            }
            throw e;
        }
    }

    private void updateAuthConfig(AuthConfig old, AuthConfig updatedAuth){
        old.setAccessToken(updatedAuth.getAccessToken());
        old.setRefreshToken(updatedAuth.getRefreshToken());
        old.setExpiresIn(updatedAuth.getExpiresIn());
        old.setLastRefreshed(updatedAuth.getLastRefreshed());
    }

}
