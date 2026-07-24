package com.syncari.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.syncari.connector.exception.RetriableException;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.provider.InsightsProvider;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.provider.InsightsProviderConnection;
import com.syncari.core.model.insights.provider.InsightsProviderGroup;
import com.syncari.core.model.insights.provider.InsightsProviderUser;
import com.syncari.core.model.insights.provider.ts.*;
import com.syncari.core.service.secrets.SecretManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.*;
import org.springframework.retry.RetryException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;


@Slf4j
@Component
public class TSService implements InsightsProviderService{

    public static final String ORG_CREATE_ENDPOINT="api/rest/2.0/orgs/create";
    public static final String ORG_SEARCH_ENDPOINT="api/rest/2.0/orgs/search";
    public static final String ORG_DELETE_ENDPOINT="/api/rest/2.0/orgs/%s/delete";
    public static final String GROUP_CREATE_ENDPOINT="api/rest/2.0/groups/create";
    public static final String GROUP_UPDATE_ENDPOINT="api/rest/2.0/groups/%s/update";
    public static final String GROUP_DELETE_ENDPOINT="api/rest/2.0/groups/%s/delete";
    public static final String USER_CREATE_ENDPOINT="api/rest/2.0/users/create";
    public static final String USER_UPDATE_ENDPOINT="api/rest/2.0/users/%s/update";
    public static final String USER_SEARCH_ENDPOINT="api/rest/2.0/users/search";
    public static final String USER_DELETE_ENDPOINT="api/rest/2.0/users/%s/delete";
    public static final String IMPORT_TML_ENDPOINT="api/rest/2.0/metadata/tml/import";
    public static final String DELETE_METADATA="api/rest/2.0/metadata/delete";
    public static final String CONNECTION_CREATE_ENDPOINT="api/rest/2.0/connection/create";
    public static final String CONNECTION_SEARCH_ENDPOINT="api/rest/2.0/connection/search";
    public static final String CONNECTION_DELETE_ENDPOINT="api/rest/2.0/connection/delete";
    public static final String CONNECTION_UPDATE_ENDPOINT="api/rest/2.0/connection/update";
    public static final String FULL_ACCESS_TOKEN_ENDPOINT="/api/rest/2.0/auth/token/full";
    public static final String VALIDATE_TOKEN_ENDPOINT="/api/rest/2.0/auth/token/validate";
    public static final String GROUP_SEARCH_ENDPOINT="api/rest/2.0/groups/search";
    public static final String METADATA_SEARCH_ENDPOINT="api/rest/2.0/metadata/search";
    public static final String SHARE_METADATA_ENDPOINT="api/rest/2.0/security/metadata/share";
    public static final String CHANGE_OWNER_METADATA_ENDPOINT="api/rest/2.0/security/metadata/assign";
    private static final String TS_API_KEY = "ts_apikey";
    private static final String QUERY_INITIAL_COMMENTED_TEXT = "--- SQL Mode for new Dataset";

    public static final String DFI_SEEDED_DASHBOARD_TML_TEMPLATE="ts/dfi_liveboard_template.json";
    public static final String TS_ADMIN_USER = "tsadmin";
    public static final String WORKSHEET_TML_COLUMN_MEASURE = "{\n" +
            "        \\\"name\\\": \\\"%s\\\",\n" +
            "        \\\"column_id\\\": \\\"%s\\\",\n" +
            "        \\\"properties\\\": {\n" +
            "          \\\"column_type\\\": \\\"MEASURE\\\",\n" +
            "          \\\"index_type\\\": \\\"DONT_INDEX\\\",\n" +
            "          \\\"aggregation\\\": \\\"%s\\\"\n" +
            "        }\n" +
            "      }";

    public static final String WORKSHEET_TML_COLUMN = "{\n" +
            "        \\\"name\\\": \\\"%s\\\",\n" +
            "        \\\"column_id\\\": \\\"%s\\\",\n" +
            "        \\\"properties\\\": {\n" +
            "          \\\"column_type\\\": \\\"ATTRIBUTE\\\",\n" +
            "          \\\"index_type\\\": \\\"DONT_INDEX\\\"\n" +
            "        }\n" +
            "      }";

    public static final String SQL_TML_CREATE="{\\\"sql_view\\\": {\\\"name\\\": \\\"%s\\\", \\\"description\\\": \\\"%s\\\", \\\"connection\\\" : {\\\"name\\\" : \\\"%s\\\"}, \\\"sql_query\\\" : \\\"%s\\\"}}";

    public static final String SYSTEM_LIVEBOARD_CREATE = "{\\\"liveboard\\\": {\\\"name\\\": \\\"%s\\\"}}";

    public static final String SQL_TML_UPDATE="{\\\"guid\\\":\\\"%s\\\",\\\"sql_view\\\": {\\\"name\\\": \\\"%s\\\", \\\"description\\\": \\\"%s\\\", \\\"connection\\\" : {\\\"name\\\" : \\\"%s\\\"}, \\\"sql_query\\\" : \\\"%s\\\",\n"
            +"\\\"sql_view_columns\\\": [%s]}}";

    public static final String WORKSHEET_TML = "{\\\"guid\\\":\\\"%s\\\",\\\"worksheet\\\": {\n" +
            "    \\\"name\\\": \\\"%s\\\",\n" +
            "    \\\"description\\\": \\\"%s\\\",\n" +
            "    \\\"tables\\\": [\n" +
            "      {\n" +
            "        \\\"name\\\": \\\"%s\\\",\n" +
            "        \\\"fqn\\\": \\\"%s\\\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \\\"table_paths\\\": [\n" +
            "      {\n" +
            "        \\\"id\\\": \\\"%s\\\",\n" +
            "        \\\"table\\\": \\\"%s\\\",\n" +
            "        \\\"join_path\\\": [\n" +
            "          {}\n" +
            "        ]\n" +
            "      }\n" +
            "    ],\n" +
            "    \\\"worksheet_columns\\\": [%s],\n" +
            "    \\\"properties\\\": {\n" +
            "      \\\"is_bypass_rls\\\": false,\n" +
            "      \\\"join_progressive\\\": true\n" +
            "    }\n" +
            "  }}";

    @Autowired
    ObjectMapper mapper;

    @Autowired
    AppConfig config;

    @Autowired
    SecretManager secretManager;

    @Autowired
    UserService userService;

    @Autowired
    private ResourceLoader resourceLoader;

    @Override
    public String createOrganization(Organization organization,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null == organization), String.format(i18n("object_null"),"Organization"));
        validateCondition(StringUtils.isEmpty(organization.getName()), String.format(i18n("object_name_empty"),"Organization"));
        String envName = config.getEnvName();
        if (StringUtils.isNotEmpty(envName) && envName.equalsIgnoreCase("non-prod")){
            // search Non-prod organization that should always exists and return that id from here
            Organization tempOrg = new Organization();
            tempOrg.setName("Non-prod");
            tempOrg.setId("Non-prod");
            Optional<String> orgId = searchOrganization(tempOrg,headers);
            if (orgId.isPresent()){
                return orgId.get();
            }
        }
        if (StringUtils.isNotEmpty(organization.getInsightsProviderOrgId())){
            Optional<String> orgId = searchOrganization(organization,headers);
            if (orgId.isPresent()){
                return orgId.get();
            }
        }
        String orgName = organization.getId();  // Put orgId as name in TS
        Map<String, String> payload = new HashMap<>();
        payload.put("name", orgName);
        payload.put("description", "New org created by insights");
        try{
            final String payloadString = mapper.writer().writeValueAsString(payload);
            log.info("TS Payload:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String orgCreateEndPoint = getTSEndpoint() + ORG_CREATE_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(orgCreateEndPoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("Organization in ts is not created, response body is {}", result.getBody());
                throw new SyncariValidationException("Organization in ts is not created");
            }
            Org org = mapper.readValue(result.getBody(), Org.class);
            if (Objects.isNull(org)){
                log.error("Organization in ts is not created,response is not error, response body is {}", result.getBody());
                throw new SyncariValidationException("Organization in ts is not created");
            }
            return org.getId();
        }catch (JsonProcessingException e) {
            log.error("Group in ts is not created, response body is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Group in ts is not created");
        }catch (HttpClientErrorException e) {
            log.error("Organization in ts is not created, response body is {}", e.getResponseBodyAsString());
            throw new SyncariValidationException("Organization in ts is not created");
        }catch (Exception e){
            log.error("Organization in ts is not created, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Organization in ts is not created");
        }
    }

    // Returns empty optional if it does not find organization
    public Optional<String> searchOrganization(Organization organization,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null == organization), String.format(i18n("object_null"),"Organization"));
        validateCondition(StringUtils.isEmpty(organization.getName()), String.format(i18n("object_name_empty"),"Organization"));
        String orgName = organization.getId();
        Map<String, String> payload = new HashMap<>();
        payload.put("org_identifier", orgName);
        payload.put("status", "ACTIVE");
        try{
            final String payloadString = mapper.writer().writeValueAsString(payload);
            log.info("TS Payload:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String orgCreateEndPoint = getTSEndpoint() + ORG_SEARCH_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(orgCreateEndPoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("Organization in ts is not found, response body is {}", result.getBody());
                throw new SyncariValidationException("Organization in ts is not found");
            }
            List<Org> org = mapper.readValue(result.getBody(),  mapper.getTypeFactory().constructCollectionType(List.class, Org.class));
            if (CollectionUtils.isEmpty(org)){
                log.info("Organization in ts is not found response body is {}", result.getBody());
                return Optional.empty();
            }
            return Optional.of(org.get(0).getId());
        }catch (JsonProcessingException e) {
            log.error("Organization in ts not found, response body is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Organization in ts not found");
        }catch (HttpClientErrorException e) {
            log.error("Organization in ts not found, response body is {}", e.getResponseBodyAsString());
            throw new SyncariValidationException("Organization in ts not found");
        }catch (Exception e){
            log.error("Organization in ts is not found, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Organization in ts is not found");
        }
    }

    @Override
    public void deleteOrganization(Organization organization,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null == organization), String.format(i18n("object_null"),"Organization"));
        String tsOrgId = SyncariContext.getOrganziation().getInsightsProviderOrgId();
        validateCondition(StringUtils.isEmpty(tsOrgId), String.format(i18n("object_name_empty"),"Organization"));
        HttpEntity httpEntity = new HttpEntity(headers);
        try{
            String orgDeleteEndpoint = getTSEndpoint() + String.format(ORG_DELETE_ENDPOINT, tsOrgId);
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(orgDeleteEndpoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("Organization in ts is not deleted, response body is {}", result.getBody());
                throw new SyncariValidationException("Organization in ts is not deleted");
            }
            log.info("Successfully deleted org with id {}", tsOrgId);
        }catch (HttpClientErrorException e) {
            log.error("Organization in ts is not deleted, response body is {}", e.getResponseBodyAsString());
            throw new SyncariValidationException("Organization in ts is not deleted");
        }catch (Exception e){
            log.error("Organization in ts is not delete, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Organization in ts is not deleted");
        }
    }

    @Override
    public Optional<TSGrpResponse> searchGroup(String groupName, Optional<String> tsUsername,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        String tsOrgId = SyncariContext.getOrganziation().getInsightsProviderOrgId();
        validateCondition(StringUtils.isEmpty(groupName), String.format(i18n("object_name_empty"),"Group"));
        Map<String, String> payload = new HashMap<>();
        payload.put("group_identifier", groupName);
        try{
            final String payloadString = mapper.writer().writeValueAsString(payload);
            log.info("TS Payload for searchGroup:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String searchGrpEp = getTSEndpoint() + GROUP_SEARCH_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(searchGrpEp), HttpMethod.POST, httpEntity, String.class);
            log.info("TS Response for searchGroup:{}", result.getBody());
            if ((result.getStatusCode().isError()) || (StringUtils.isEmpty(result.getBody()))) {
                log.error("Group in ts is not found, response body is {}", result.getBody());
                throw new SyncariValidationException("Group in ts is not found");
            }
            List<TSGrpResponse> groups = mapper.readValue(result.getBody(),  mapper.getTypeFactory().constructCollectionType(List.class, TSGrpResponse.class));
            log.info("Successfully found group with name {}", groupName);
            return groups.stream().findFirst();
        }catch (HttpClientErrorException e) {
            log.error("HttpClientErrorException occurred, Group in ts is not found, response body is {} and stack trace is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Group in ts is not found");
        }catch (Exception e){
            log.error("Group in ts is not found, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Group in ts is not found");
        }
    }

    @Override
    public List<TSGrpResponse> searchLocalGroupsForAUser(String insightsProviderUserId, Optional<String> tsUsername,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition(StringUtils.isEmpty(insightsProviderUserId), String.format(i18n("object_name_empty"),"Group"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "LOCAL_GROUP");
        payload.put("user_identifiers", List.of(insightsProviderUserId));
        payload.put("record_offset", 0);
        payload.put("record_size", 100);
        try{
            final String payloadString = mapper.writer().writeValueAsString(payload);
            log.info("TS Payload for searchLocalGroupsForAUser:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String searchGrpEp = getTSEndpoint() + GROUP_SEARCH_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(searchGrpEp), HttpMethod.POST, httpEntity, String.class);
            log.info("TS Response for searchLocalGroupsForAUser:{}", result.getBody());
            if ((result.getStatusCode().isError()) || (StringUtils.isEmpty(result.getBody()))) {
                log.error("Groups in ts is not found in method searchLocalGroupsForAUser, response body is {}", result.getBody());
                throw new SyncariValidationException("Group in ts is not found");
            }
            List<TSGrpResponse> groups = mapper.readValue(result.getBody(),  mapper.getTypeFactory().constructCollectionType(List.class, TSGrpResponse.class));
            log.info("Successfully found groups with userId {}", insightsProviderUserId);
            return groups;
        } catch (JsonProcessingException e) {
            log.error("Group in ts is not found in method searchLocalGroupsForAUser, stack trace is {}", ExceptionUtils.getStackTrace(e));
            return List.of();
        }catch (HttpClientErrorException e) {
            log.error("Group in ts is not found in method searchLocalGroupsForAUser, response body is {} and stack trace is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Group in ts is not found in method searchLocalGroupsForAUser");
        }catch (Exception e){
            log.error("Group in ts is not found in method searchLocalGroupsForAUser, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Group in ts is not found in method searchLocalGroupsForAUser");
        }
    }

    @Override
    public List<TSGrpResponse> searchAllLocalGroups(Optional<String> tsUsername,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "LOCAL_GROUP");
        try{
            final String payloadString = mapper.writer().writeValueAsString(payload);
            log.info("TS Payload for searchAllLocalGroups:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String searchGrpEp = getTSEndpoint() + GROUP_SEARCH_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(searchGrpEp), HttpMethod.POST, httpEntity, String.class);
            log.info("TS Response for searchAllLocalGroups:{}", result.getBody());
            if ((result.getStatusCode().isError()) || (StringUtils.isEmpty(result.getBody()))) {
                log.error("Groups in ts is not found in method searchAllLocalGroups, response body is {}", result.getBody());
                throw new SyncariValidationException("Group in ts is not found");
            }
            List<TSGrpResponse> groups = mapper.readValue(result.getBody(),  mapper.getTypeFactory().constructCollectionType(List.class, TSGrpResponse.class));
            log.info("Successfully found all local groups");
            return groups;
        } catch (JsonProcessingException e) {
            log.error("Group in ts is not found in method searchAllLocalGroups, stack trace is {}", ExceptionUtils.getStackTrace(e));
            return List.of();
        }catch (HttpClientErrorException e) {
            log.error("Group in ts is not found in method searchAllLocalGroups, response body is {} and stack trace is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Group in ts is not found in method searchLocalGroupsForAUser");
        }catch (Exception e){
            log.error("Group in ts is not found in method searchAllLocalGroups, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Group in ts is not found in method searchLocalGroupsForAUser");
        }
    }


    @Override
    public boolean addOrRemoveUserToGroup(String groupName,List<String> tsUserIds, Optional<String> tsUsername,HttpHeaders headers,GroupOperation groupOperation) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition(StringUtils.isEmpty(groupName), String.format(i18n("object_name_empty"),"Group"));
        try{
            Map<String, Object> payload = new HashMap<>();
            payload.put("operation", groupOperation.name());
            payload.put("user_identifiers", tsUserIds);
            payload.put("name", groupName);

            final String payloadString = mapper.writer().writeValueAsString(payload);
            log.info("TS Payload for addOrRemoveUserToGroup :{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String grpCreateEndPoint = getTSEndpoint() + String.format(GROUP_UPDATE_ENDPOINT, groupName);
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(grpCreateEndPoint), HttpMethod.POST, httpEntity, String.class);
            log.info("TS Response for addOrRemoveUserToGroup :{}", result.getBody());
            if (result.getStatusCode().isError()) {
                log.error("Group in ts is not updated, response body is {}", result.getBody());
                return false;
            }
        } catch (JsonProcessingException e) {
            log.error("Group in ts is not updated, stack trace is {}", ExceptionUtils.getStackTrace(e));
            return false;
        }catch (HttpClientErrorException e) {
            log.error("Group in ts is not updated, response body is {} and stack trace is ", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            return false;
        }catch (Exception e){
            if (e instanceof HttpServerErrorException.InternalServerError){
                log.error("Update group failed, Error is {}",((HttpServerErrorException.InternalServerError)e).getResponseBodyAsString());
            }
            log.error("Group in ts is not updated, exception stack is {}", ExceptionUtils.getStackTrace(e));
            return false;
        }
        return true;
    }
    @Override
    public String createGroup(InsightsProviderGroup group,Optional<String> tsUsername,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null ==group), String.format(i18n("object_null"),"Group"));
        validateCondition(StringUtils.isEmpty(group.getName()), String.format(i18n("object_name_empty"),"Group"));
        try{
            final String payloadString = mapper.writer().writeValueAsString(group);
            log.info("TS Payload for createGroup:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String grpCreateEndPoint = getTSEndpoint() + GROUP_CREATE_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(grpCreateEndPoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("Group in ts is not created, response body is {}", result.getBody());
                throw new SyncariValidationException("Group in ts is not created");
            }
            TSGrpResponse grp = mapper.readValue(result.getBody(), TSGrpResponse.class);
            if (Objects.isNull(grp)){
                log.error("Group in ts is not created,response is not error, response body is {}", result.getBody());
                throw new SyncariValidationException("Group in ts is not created");
            }
            return grp.getId();
        } catch (JsonProcessingException e) {
            log.error("Group in ts is not created, stack trace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Group in ts is not created");
        }catch (HttpClientErrorException e) {
            log.error("Group in ts is not created, response body is {} and stack trace is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Group in ts is not created");
        }catch (Exception e){
            log.error("Group in ts is not created, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Group in ts is not created");
        }
    }


    @Override
    public void deleteGroup(String tsGroupId,Optional<String> tsUsername,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition(StringUtils.isEmpty(tsGroupId), String.format(i18n("object_name_empty"),"Group"));
        HttpEntity httpEntity = new HttpEntity(headers);
        try{
            String groupDelEndpoint = getTSEndpoint() + String.format(GROUP_DELETE_ENDPOINT, tsGroupId);
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(groupDelEndpoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("Group in ts is not deleted, response body is {}", result.getBody());
                throw new SyncariValidationException("Group in ts is not deleted");
            }
            log.info("Successfully deleted group with id {}", tsGroupId);
        }catch (HttpClientErrorException e) {
            log.error("Group in ts is not deleted, response body is {} and stack is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Group in ts is not deleted");
        }catch (Exception e){
            log.error("Group in ts is not deleted, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Group in ts is not deleted");
        }
    }

    @Override
    public TSUserResponse createUser(InsightsProviderUser user,Optional<String> tsUsername,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null ==user), String.format(i18n("object_null"),"User"));
        validateCondition(StringUtils.isEmpty(user.getName()), String.format(i18n("object_name_empty"),"User"));
        Optional<TSUserResponse> tsUserResponseOpt = searchUser(user, tsUsername,true,headers);
        if (tsUserResponseOpt.isPresent()){
            user.setUser_identifier(tsUserResponseOpt.get().getId());
            return tsUserResponseOpt.get();
        }
        try{
            final String payloadString = mapper.writer().writeValueAsString(user);
            log.info("TS Payload for create user:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String usrCreateEndPoint = getTSEndpoint() + USER_CREATE_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(usrCreateEndPoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("User in ts is not created, response body is {}", result.getBody());
                throw new SyncariValidationException("Group in ts is not created");
            }
            TSUserResponse tsUserResponse = mapper.readValue(result.getBody(), TSUserResponse.class);
            if (Objects.isNull(tsUserResponse)){
                log.error("User in ts is not created,response is not error, response body is {}", result.getBody());
                return null;
            }
            user.setUser_identifier(tsUserResponse.getId());
            return tsUserResponse;
        } catch (JsonProcessingException e) {
            log.error("User in ts is not created, stack trace is {}", ExceptionUtils.getStackTrace(e));
            return null;
        }catch (HttpClientErrorException e) {
            log.error("User in ts is not created, response body is {} and stack trace is {}", e.getResponseBodyAsString(),ExceptionUtils.getStackTrace(e));
            return null;
        }catch (Exception e){
            log.error("User in ts is not created, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("User in ts is not created");
        }
    }

    @Override
    public void updateUser(InsightsProviderUser user,Optional<String> tsUsername, boolean isHeaderPrimaryOrg,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null ==user), String.format(i18n("object_null"),"User"));
        validateCondition(StringUtils.isEmpty(user.getName()), String.format(i18n("object_name_empty"),"User"));
        HttpHeaders headersToBeUsed = isHeaderPrimaryOrg? getHeadersForPrimaryOrg(tsUsername) : headers;
        try{
            final String payloadString = mapper.writer().writeValueAsString(user);
            log.info("TS Payload:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headersToBeUsed);
            String usrCreateEndPoint = getTSEndpoint() + String.format(USER_UPDATE_ENDPOINT,user.getUser_identifier());
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(usrCreateEndPoint), HttpMethod.POST, httpEntity, String.class);
            log.info("TS Update User response {}", result);
            if (result.getStatusCode().isError()) {
                log.error("User in ts is not updated, response body is {}", result.getBody());
                throw new SyncariValidationException("Group in ts is not updated");
            }
        } catch (JsonProcessingException e) {
            log.error("User in ts is not updated, stack trace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("User in ts is not updated");
        }catch (HttpClientErrorException e) {
            log.error("User in ts is not updated, response body is {} and stack trace is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("User in ts is not updated");
        }catch (Exception e){
            if (e instanceof HttpServerErrorException.InternalServerError){
                log.error("Update user did not happen, Error is {}",((HttpServerErrorException.InternalServerError)e).getResponseBodyAsString());
            }
            log.error("User in ts is not updated, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("User in ts is not updated");
        }
    }

    @Override
    public Optional<TSUserResponse> searchUser(InsightsProviderUser user,Optional<String> tsUsername, boolean isPrimaryOrg,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null ==user), String.format(i18n("object_null"),"User"));
        HttpHeaders headersTobeUsed = isPrimaryOrg? getHeadersForPrimaryOrg(tsUsername): headers;
        try{
            if (CollectionUtils.isNotEmpty(user.getOrg_identifiers())){
                user.setOrg_identifiers(user.getOrg_identifiers());
            }
            Map<String, String> payload = new HashMap<>();
            payload.put("user_identifier", user.getName());
            final String payloadString = mapper.writer().writeValueAsString(payload);
            log.info("TS Payload for user search :{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headersTobeUsed);
            String searchEndPoint = getTSEndpoint() + USER_SEARCH_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(searchEndPoint), HttpMethod.POST, httpEntity, String.class);
            log.info("TS Response for user search :{}", result);
            if (result.getStatusCode().isError()) {
                log.error("User in ts not exists, response body is {}", result.getBody());
                throw new SyncariValidationException("User in ts not exists");
            }
            List<TSUserResponse> tsUserResponse = mapper.readValue(result.getBody(), mapper.getTypeFactory().constructCollectionType(List.class, TSUserResponse.class));
            if (CollectionUtils.isEmpty(tsUserResponse)){
                log.info("User in ts not exists, response body is {}", result.getBody());
                return Optional.empty();
            }
            return Optional.of(tsUserResponse.get(0));
        } catch (JsonProcessingException e) {
            log.error("User in ts is not present, stack trace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("User in ts is not created");
        }catch (HttpClientErrorException e) {
            log.error("User in ts is not present, response body is {} and stack trace is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            return Optional.empty();
        }catch (Exception e){
            if (e instanceof HttpServerErrorException.InternalServerError){
                log.error("Search user errored, Error is {}",((HttpServerErrorException.InternalServerError)e).getResponseBodyAsString());
            }
            log.error("User in ts is not found, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("User in ts is not found");
        }
    }

    @Override
    public void deleteUser(String tsUserId,Optional<String> tsUsername,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition(StringUtils.isEmpty(tsUserId), String.format(i18n("object_name_empty"),"User"));
        HttpEntity httpEntity = new HttpEntity(headers);
        try{
            String userDelEndpoint = getTSEndpoint() + String.format(USER_DELETE_ENDPOINT, tsUserId);
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(userDelEndpoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("User in ts is not deleted, response body is {}", result.getBody());
                throw new SyncariValidationException("User in ts is not deleted");
            }
            log.info("Successfully deleted user with id {}", tsUserId);
        }catch (HttpClientErrorException e) {
            log.error("User in ts is not deleted, response body is {}", e.getResponseBodyAsString());
            throw new SyncariValidationException("User in ts is not deleted");
        }catch (Exception e){
            log.error("User in ts is not deleted, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("User in ts is not deleted");
        }
    }

    private List<String> getOrderedListOfFqns(Map<String, String> fqnsMap){
        /*
        This is the order of mat views being used in file, fqns passed should be in this order, otherwise this will fail in other instances
        1 dfiCurrentScoreByEntity, 2 dfiCurrentScoreByEntityAndCategory, 3 dfiOverallScoreByTime,4 dfiCurrentScoreByEntityAndCategory,5 dfiOverallScoreByTimeAndCategory,6 dfiScoreOverTimeByEntity,7 dfiScoreOverTimeByEntityAndCategory,8 dfiScoreOverTimeByEntityAndRule
         */
        List<String> fqnOrderedList = new LinkedList<>();
        fqnOrderedList.add(fqnsMap.get("dfiCurrentScoreByEntity"));
        fqnOrderedList.add(fqnsMap.get("dfiCurrentScoreByEntityAndCategory"));
        fqnOrderedList.add(fqnsMap.get("dfiOverallScoreByTime"));
        fqnOrderedList.add(fqnsMap.get("dfiCurrentScoreByEntityAndCategory"));
        fqnOrderedList.add(fqnsMap.get("dfiOverallScoreByTimeAndCategory"));
        fqnOrderedList.add(fqnsMap.get("dfiScoreOverTimeByEntity"));
        fqnOrderedList.add(fqnsMap.get("dfiScoreOverTimeByEntityAndCategory"));
        fqnOrderedList.add(fqnsMap.get("dfiScoreOverTimeByEntityAndRule"));
        return fqnOrderedList;

    }

    public String seedSystemLiveboard(String liveBoardName, Map<String, String> fqnsMap, HttpHeaders headers){

        RestTemplate tsTemplate = new RestTemplate();
        try{

            List<String> fqns = getOrderedListOfFqns(fqnsMap);
            InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(DFI_SEEDED_DASHBOARD_TML_TEMPLATE);
            String json = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            log.debug("Loaded json is empty : {}", StringUtils.isBlank(json));

            String payloadString = String.format(json,liveBoardName,fqns.get(0),fqns.get(1),fqns.get(2),fqns.get(3),fqns.get(4),fqns.get(5),fqns.get(6),fqns.get(7));
            log.info("TS Payload for Create Liveboard before prettyFormat:{}", payloadString);
            payloadString = toPrettyFormat(payloadString);
            String importEndPoint = getTSEndpoint() + IMPORT_TML_ENDPOINT;
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            log.info("TS Payload for Create Liveboard:{}", payloadString);
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(importEndPoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("Create empty liveboard, response body is {}", result.getBody());
                throw new SyncariValidationException("Create empty live board");
            }
            log.info("Response of Create empty liveboard is {}", result);
            List<Map<String, Object>> resp;
            Map<String, Object> response;
            resp = mapper.readValue(result.getBody(), List.class);
            if (CollectionUtils.isEmpty(resp)){
                log.error("Liveboard in ts is not created,response is not error, response body is {}", result.getBody());
                throw new SyncariValidationException("Liveboard in ts is not created/updated");
            }else{
                response = resp.get(0);
            }
            if ((null != response.get("response")) && (response.get("response") instanceof Map)){
                Map<String, Map<String, String>> respMap = (Map<String, Map<String, String>>)response.get("response");
                if ((null != respMap.get("status")) && (respMap.get("status") instanceof Map)){
                    Map<String, String> status = respMap.get("status");
                    if ((null != status) && (StringUtils.isNotEmpty(status.get("status_code"))) && (status.get("status_code").equalsIgnoreCase("ERROR"))) {
                        log.error("Liveboard in ts is not created, response body is {}", result.getBody());
                        if (StringUtils.isNotEmpty(status.get("error_message"))){
                            throw new SyncariValidationException(status.get("error_message"));
                        }else{
                            throw new SyncariValidationException("liveboard not created");
                        }
                    }
                }

            }
            return ((Map<String, Object>)((Map<String, Object>)response.get("response")).get("header")).get("id_guid").toString();
        }catch (IOException exception) {
            log.error("IOException occurred {}",ExceptionUtils.getStackTrace(exception));
        }catch (HttpClientErrorException e) {
            log.error("Liveboard in ts is not created, response body is {} and stack is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
        }catch (Exception e){
            log.error("Liveboard in ts is not created, exception stack is {}", ExceptionUtils.getStackTrace(e));
        }
        return null;
    }

    // Part 1: SQL View Creation
    @Retryable(value = { RetryException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private String createSQLView(Dataset dataset, String connectionName, HttpHeaders headers) throws JsonProcessingException {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null == dataset), String.format(i18n("object_null"), "Dataset"));
        validateCondition(StringUtils.isEmpty(dataset.getName()), String.format(i18n("object_name_empty"), "Dataset"));
        String query = dataset.getRawQuery();
        validateCondition(StringUtils.isEmpty(query), String.format(i18n("object_null"),"Dataset Query"));
        try{
            query = query.replace("syncari_"+SyncariContext.getSyncariId(), "syncari_"+SyncariContext.getSyncariId()+".syncari_"+SyncariContext.getSyncariId());
            query = query.startsWith(QUERY_INITIAL_COMMENTED_TEXT) ? query.replace(QUERY_INITIAL_COMMENTED_TEXT,""): query;
            query = query.replace("\"","\\\\\\\"").trim();
            log.info("Query to create SQL view is {}", query);
            String payloadString = "{\n" +
                    "  \"metadata_tmls\": [\n" +
                    "    \"%s\"\n" +
                    "  ],\n" +
                    "  \"import_policy\": \"ALL_OR_NONE\",\n" +
                    "  \"create_new\": %s\n" +
                    "}";

            String sqlview = String.format(SQL_TML_CREATE,dataset.getName(), dataset.getDescription(), connectionName,query);
            payloadString = String.format(payloadString,sqlview,true);
            payloadString = toPrettyFormat(payloadString);
            StringBuilder sqlViewId = new StringBuilder();
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String importEndPoint = getTSEndpoint() + IMPORT_TML_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(importEndPoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("SQL View in ts is not created/updated, response body is {}", result.getBody());
                throw new SyncariValidationException("SQL View in ts is not created/updated");
            }
            log.info("Response of SQL View creation/updation is {}", result);
            List<Map<String, Object>> resp;
            Map<String, Object> response;
            resp = mapper.readValue(result.getBody(), List.class);
            if (CollectionUtils.isEmpty(resp)){
                log.error("SQL View in ts is not created/updated,response is not error, response body is {}", result.getBody());
                throw new SyncariValidationException("SQL View in ts is not created/updated");
            }else{
                response = resp.get(0);
            }
            if ((null != response.get("response")) && (response.get("response") instanceof Map)){
                Map<String, Map<String, String>> respMap = (Map<String, Map<String, String>>)response.get("response");
                if ((null != respMap.get("status")) && (respMap.get("status") instanceof Map)){
                    Map<String, String> status = respMap.get("status");
                    if ((null != status) && (StringUtils.isNotEmpty(status.get("status_code"))) && (status.get("status_code").equalsIgnoreCase("ERROR"))) {
                        log.error("SQL View in ts is not created/updated, response body is {}", result.getBody());
                        if (StringUtils.isNotEmpty(status.get("error_message"))){
                            throw new SyncariValidationException(status.get("error_message"));
                        }else{
                            throw new SyncariValidationException("View not created");
                        }
                    }
                }

            }
            return ((Map<String, Object>)((Map<String, Object>)response.get("response")).get("header")).get("id_guid").toString();
        } catch (JsonProcessingException e) {
            log.error("Dataset in ts is not created, stack trace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Error is %s", e.getMessage());
        }catch (HttpClientErrorException e) {
            log.error("Dataset in ts is not created, response body is {} and stack trace is {}", e.getResponseBodyAsString(),ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Error is %s", e.getMessage());
        }catch (Exception e){
            log.error("Dataset in ts is not created, exception stack is {}", ExceptionUtils.getStackTrace(e));
            if (e.getMessage().contains("The connection attempt failed")){
                throw new RetryException("The connection attempt failed");
            }
            throw new SyncariValidationException("Error is %s", e.getMessage());
        }
    }

    // Part 2: Worksheet Creation
    @Retryable(value = { RetryException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private String createWorksheetFromSQLView(String sqlViewIdentifier, Dataset dataset, boolean isCreate, Optional<String> tsUsername, HttpHeaders headers){
        RestTemplate tsTemplate = new RestTemplate();
        String importEndPoint = getTSEndpoint() + IMPORT_TML_ENDPOINT;
        try{
            TSMetadataSearchReq req = new TSMetadataSearchReq().setMetadata(List.of(new TSMetadataListItemInput().setType(TSMetadataType.LOGICAL_TABLE.name()).setIdentifier(sqlViewIdentifier))).setInclude_details(true);
            List<String> columns = new ArrayList<>();
            List<TSMetadataSearchResponse> tsMetadataSearchResponses = this.searchMetadata(req, tsUsername,headers);
            Optional<TSMetadataSearchResponse> tsMetadataSearchResponse = tsMetadataSearchResponses.stream().findFirst();
            return tsMetadataSearchResponse.map(tsResp -> {
                Map<String, Object> details = tsResp.getMetadata_detail();
                String viewName = tsResp.getMetadata_name();
                String desc =  dataset.getDescription();
                List<Map<String, Object>> cols = (List<Map<String, Object>>)((Map<String, Object>)details).get("columns");
                String tableFabricatedId = viewName + "_1";
                cols.forEach(c -> {
                    String colName = c.get("sqlColumnName").toString();
                    String colDataType = c.get("sqlDataType").toString();
                    String type = c.get("type").toString();
                    String colTobeUsed;
                    String colId = tableFabricatedId + "::" + colName;
                    if (type.equalsIgnoreCase("ATTRIBUTE")){
                        colTobeUsed = String.format(WORKSHEET_TML_COLUMN, colName,colId);
                    }else{
                        String aggregation = c.get("defaultAggrType").toString();
                        colTobeUsed = String.format(WORKSHEET_TML_COLUMN_MEASURE, colName,colId,aggregation);
                    }
                    columns.add(colTobeUsed);
                });
                String worksheetName = viewName;
                String commaSeparatedColumns = StringUtils.join(columns, ",");
                String workSheet;
                if (!isCreate){
                    workSheet = String.format(WORKSHEET_TML,dataset.getInsightsProviderId(), worksheetName, desc, viewName,sqlViewIdentifier,tableFabricatedId, viewName, commaSeparatedColumns);
                }else{
                    workSheet = String.format(WORKSHEET_TML,"", worksheetName, desc, viewName,sqlViewIdentifier,tableFabricatedId, viewName, commaSeparatedColumns);
                }
                // Create Worksheet from SQL View
                String payloadStr = "{\n" +
                        "\"metadata_tmls\": [\n \"" + workSheet +
                        "\"],\n" +
                        "\"import_policy\": \"ALL_OR_NONE\",\n" +
                        "\"create_new\": "+ isCreate + ",\n" +
                        "\"all_orgs_context\": false\n" +
                        "}";
                log.info("TS Payload for Worksheet:{}", payloadStr);
                payloadStr = toPrettyFormat(payloadStr);
                log.debug("TS Payload for Worksheet after prettyFormat :{}", payloadStr);

                HttpEntity httpEntity_ws = new HttpEntity(payloadStr, headers);
                final ResponseEntity<String> result_ws = tsTemplate.exchange(URI.create(importEndPoint), HttpMethod.POST, httpEntity_ws, String.class);
                if (result_ws.getStatusCode().isError()) {
                    log.error("Worksheet in ts is not created/updated, response body is {}", result_ws.getBody());
                    throw new SyncariValidationException("Worksheet in ts is not created/updated");
                }
                List<Map<String, Object>> resp_ws;
                Map<String, Object> response_ws;
                log.info("Response of Worksheet creation/updated is {}", result_ws);
                try {
                    resp_ws = mapper.readValue(result_ws.getBody(), List.class);
                    if (CollectionUtils.isEmpty(resp_ws)){
                        log.error("Worksheet in ts is not created/updated,response is not error, response body is {}", result_ws.getBody());
                        throw new SyncariValidationException("Worksheet in ts is not created/updated");
                    }else{
                        response_ws = resp_ws.get(0);
                    }
                    Map<String, Object> response = Map.class.cast(response_ws.get("response"));
                    if ((response != null && null != response.get("status")) && (response.get("status") instanceof Map)) {
                        Map<String, String> status = (Map<String, String>) response.get("status");
                        if ((null != status) && (StringUtils.isNotEmpty(status.get("status_code"))) && (status.get("status_code").equalsIgnoreCase("ERROR"))) {
                            log.error("Worksheet in ts is not created/updated,response is not error, response body is {}", result_ws.getBody());
                            if (StringUtils.isNotEmpty(status.get("error_message"))){
                                throw new SyncariValidationException(status.get("error_message"));
                            }else{
                                throw new SyncariValidationException("Worksheet not created");
                            }
                        }
                    }

                    if (!isCreate && StringUtils.isNotEmpty(dataset.getInsightsProviderSQLViewId())){
                        try{
                            this.deleteMetadata(dataset.getInsightsProviderSQLViewId(),headers);
                        }catch (Exception e){
                            log.error("Dataset view deletion was not successful, eating exception, stack trace is  {}", ExceptionUtils.getStackTrace(e));
                        }
                    }
                    return ((Map<String, Object>)((Map<String, Object>)response_ws.get("response")).get("header")).get("id_guid").toString();

                } catch (JsonProcessingException e) {
                    log.error("Worksheet in ts is not created,response is not error, response body is {}", result_ws.getBody());
                }
                throw new SyncariValidationException("Worksheet in ts is not created");
            }).orElseThrow();
        }catch (HttpClientErrorException e) {
            log.error("Dataset in ts is not created, response body is {} and stack trace is {}", e.getResponseBodyAsString(),ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Dataset is not created HttpClientErrorException occurred in ws, Error is %s", e.getMessage());
        }catch (Exception e){
            log.error("Dataset in ts is not created, exception stack is {}", ExceptionUtils.getStackTrace(e));
            if (e.getMessage().contains("The connection attempt failed")){
                throw new RetryException("The connection attempt failed");
            }
            throw new SyncariValidationException(e.getMessage());

        }
    }

    /*
    To Create worksheet in TS
        1 Create sql view
        2 Find sql view
        3 Create worksheet by passing new sql view identifier
        4 delete old sqlview
    To Update worksheet in TS follow following steps
        1 Create new sql view
        2 Find new sql view
        3 Update worksheet by passing new sql view identifier
        4 Delete old sqlview
     */
    @Override
    public Map<String, String> createOrUpdateDataset(Dataset dataset,String connectionName, Optional<String> tsUsername,boolean isCreate,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        StringBuilder sqlViewId = new StringBuilder();
        boolean isException;
        String exceptionMessage;
        try{
            String sqlViewIdentifier = createSQLView(dataset, connectionName, headers);
            sqlViewId.append(sqlViewIdentifier);
            String worksheetIdent = createWorksheetFromSQLView(sqlViewIdentifier, dataset, isCreate, tsUsername, headers);
            return Map.of("SQL_VIEW_ID", sqlViewIdentifier, "WORKSHEET_ID", worksheetIdent);
        } catch (JsonProcessingException e) {
            log.error("Dataset in ts is not created, stack trace is {}", ExceptionUtils.getStackTrace(e));
            isException = true;
            exceptionMessage = e.getMessage();
        }catch (HttpClientErrorException e) {
            log.error("Dataset in ts is not created, response body is {} and stack trace is {}", e.getResponseBodyAsString(),ExceptionUtils.getStackTrace(e));
            isException = true;
            exceptionMessage = e.getMessage();
        }catch (Exception e){
            log.error("Dataset in ts is not created, exception stack is {}", ExceptionUtils.getStackTrace(e));
            isException = true;
            exceptionMessage = e.getMessage();
        }
        if (StringUtils.isNotEmpty(sqlViewId.toString()) && isException){
            try{
                this.deleteMetadata(sqlViewId.toString(),headers);
            }catch (Exception exception){
                log.error("Dataset view deletion was not successful, eating exception, stack trace is  {}", ExceptionUtils.getStackTrace(exception));
            }
        }
        if (isException){
            if (StringUtils.isNotEmpty(exceptionMessage)){
                throw new SyncariValidationException("Dataset is not created, Error is %s", exceptionMessage);
            }else{
                throw new SyncariValidationException("Dataset is not created, please reach out to support");
            }
        }
        return null;
    }

    private String toPrettyFormat(String jsonString)
    {
        JsonParser parser = new JsonParser();
        JsonObject json = parser.parse(jsonString).getAsJsonObject();

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String prettyJson = gson.toJson(json);

        return prettyJson;
    }

    @Override
    public void deleteDataset(Dataset dataset,Optional<String> tsUsername,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null ==dataset), String.format(i18n("object_null"),"Dataset"));
        validateCondition(StringUtils.isEmpty(dataset.getDisplayName()), String.format(i18n("object_name_empty"),"Dataset"));
        validateCondition(StringUtils.isEmpty(dataset.getInsightsProviderSQLViewId()), String.format(i18n("object_null"),"Dataset Query"));
        Map<String, List<Map<String, String>>> payload = new HashMap<>();
        payload.put("metadata", List.of(Map.of("identifier", dataset.getInsightsProviderId())));
        // Delete worksheet first
        try{
            String payloadString = mapper.writeValueAsString(payload);
            log.info("TS Payload for Worksheet:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String delEndPoint = getTSEndpoint() + DELETE_METADATA;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(delEndPoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("Worksheet in ts is not deleted, response body is {}", result.getBody());
                throw new SyncariValidationException("Worksheet in ts is not deleted");
            }
            log.info("Response is {}", result);
        } catch (JsonProcessingException e) {
            log.error("Worksheet in ts is not deleted, stack trace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Worksheet in ts is not deleted");
        }catch (HttpClientErrorException e) {
            log.error("Worksheet in ts is not deleted, response body is {} and stack trace is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Worksheet in ts is not deleted");
        }catch (Exception e){
            log.error("Worksheet in ts is not deleted, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Worksheet in ts is not deleted");
        }
        // Delete sql view second
        deleteMetadata(dataset.getInsightsProviderSQLViewId(),headers);
    }

    @Override
    public void deleteMetadata(String metadataId,HttpHeaders headers){
        RestTemplate tsTemplate = new RestTemplate();
        Map<String, List<Map<String, String>>> payload2 = new HashMap<>();
        payload2.put("metadata", List.of(Map.of("identifier",metadataId)));

        try{
            String payloadString = mapper.writeValueAsString(payload2);
            log.info("TS Payload for metadata deletion :{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String delEndPoint = getTSEndpoint() + DELETE_METADATA;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(delEndPoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("Metadata in ts is not deleted, response body is {}", result.getBody());
                throw new SyncariValidationException("Metadata in ts is not deleted");
            }
            log.info("Response for Metadata deletion is {}", result);
        } catch (JsonProcessingException e) {
            log.error("Metadata in ts is not deleted. Error {}, response body is {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Metadata in ts is not deleted");
        }catch (HttpClientErrorException e) {
            log.error("Metadata in ts is not deleted. Error {}, response body is {} and stack is {}", e.getMessage(), e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Metadata in ts is not deleted");
        }catch (Exception e){
            log.error("Metadata in ts is not deleted. Error {}, exception stack is {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Metadata in ts is not deleted");
        }
    }

    @Override
    public void shareMetadata(TSMetadataShareRequest request,Optional<String> tsUsername,HttpHeaders headers){
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null ==request), String.format(i18n("object_null"),"Share"));
        validateCondition(StringUtils.isEmpty(request.getMetadata_type()), String.format(i18n("object_name_empty"),"Metadata type"));
        validateCondition(CollectionUtils.isEmpty(request.getMetadata_identifiers()), String.format(i18n("object_name_empty"),"Metadata Identifiers"));

        try{
            String payloadString = mapper.writeValueAsString(request);
            log.info("TS Payload:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String importEndPoint = getTSEndpoint() + SHARE_METADATA_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(importEndPoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("Metadata in ts is not shared, response body is {}", result.getBody());
                throw new SyncariValidationException("Metadata in ts is not shared");
            }
            log.info("Response is {}", result);
            return ;
        } catch (JsonProcessingException e) {
            log.error("Metadata in ts is not shared, stack trace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Metadata in ts is not shared");
        }catch (HttpClientErrorException e) {
            log.error("Metadata in ts is not shared, response body is {} and stack trace is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Metadata in ts is not shared");
        }catch (Exception e){
            log.error("Metadata in ts is not shared, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Metadata in ts is not shared");
        }
    }

    @Override
    public void changeOwnerMetadata(TSChangeOwnerRequest ownerReq,Optional<String> tsUsername,HttpHeaders headers){
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null ==ownerReq), String.format(i18n("object_null"),"Change Owner"));
        validateCondition(StringUtils.isEmpty(ownerReq.getUser_identifier()), String.format(i18n("object_name_empty"),"Change owner identifier"));
        validateCondition(CollectionUtils.isEmpty(ownerReq.getMetadata()), String.format(i18n("object_name_empty"),"Metadata Identifiers"));

        try{
            String payloadString = mapper.writeValueAsString(ownerReq);
            log.info("TS Payload for change owner :{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String importEndPoint = getTSEndpoint() + CHANGE_OWNER_METADATA_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(importEndPoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("Owner of Metadata in ts is not changed, response body is {}", result.getBody());
                throw new SyncariValidationException("Owner of Metadata in ts is not changed");
            }
            log.info("Response of change owner is  {}", result);
            return ;
        } catch (JsonProcessingException e) {
            log.error("Owner of Metadata in ts is not changed, stack trace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Owner of Metadata in ts is not changed");
        }catch (HttpClientErrorException e) {
            log.error("Owner of Metadata in ts is not changed, response body is {} and stack trace is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Owner of Metadata in ts is not changed");
        }catch (Exception e){
            log.error("Owner of Metadata in ts is not changed, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Owner of Metadata in ts is not changed");
        }
    }

    @Override
    @Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String createConnection(InsightsProviderConnection connection,Optional<String> tsUsername,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null == connection), String.format(i18n("object_null"),"Connection"));
        validateCondition(StringUtils.isEmpty(connection.getName()), String.format(i18n("object_name_empty"),"Connection"));
        try{
            final String payloadString = mapper.writer().writeValueAsString(connection);
            log.info("TS Payload for create:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String connCreateEndPoint = getTSEndpoint() + CONNECTION_CREATE_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(connCreateEndPoint), HttpMethod.POST, httpEntity, String.class);
            log.debug("TS Response for create:{}", result);
            if (result.getStatusCode().isError()) {
                log.error("Connection in ts is not created, response body is {}", result.getBody());
                throw new SyncariValidationException("Connection in ts is not created");
            }
            TSConnResponse conn = mapper.readValue(result.getBody(), TSConnResponse.class);
            if (Objects.isNull(conn)){
                log.error("Connection in ts is not created,response is not error, response body is {}", result.getBody());
                throw new SyncariValidationException("Connection in ts is not created");
            }
            return conn.getId();
        } catch (JsonProcessingException e) {
            log.error("Connection in ts is not created, stack trace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Connection in ts is not created");
        }catch (HttpClientErrorException e) {
            log.error("Connection in ts is not created, response body is {} and stack trace is {}", e.getResponseBodyAsString(),ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Connection in ts is not created");
        }catch (Exception e) {
            if (e instanceof HttpServerErrorException.InternalServerError){
                log.error("Connection in ts is not created Error is {}",((HttpServerErrorException.InternalServerError)e).getResponseBodyAsString());
                if (((HttpServerErrorException.InternalServerError) e).getResponseBodyAsString().contains("Connection to Postgres could not be established. The connection attempt failed")){
                    throw new RetriableException("CONNECTION_ERROR","Connection Attempt failed","CONNECTION_ERROR");
                }
            }
            log.error("Connection in ts is not created, stacktrace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Connection in ts is not created");
        }
    }

    // This assumes connections passed to search is 1 in request
    @Override
    public Optional<String> searchConnection(InsightsProviderConnection connection,Optional<String> tsUsername,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null == connection), String.format(i18n("object_null"),"Connection"));
        validateCondition(StringUtils.isEmpty(connection.getName()), String.format(i18n("object_name_empty"),"Connection Identifier"));
        Map<String, List<Map<String, String>>> payload = new HashMap<>();
        payload.put("connections",List.of(Map.of("identifier", connection.getName())));
        try{
            final String payloadString = mapper.writer().writeValueAsString(payload);
            log.info("TS Payload for search connection is :{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String connCreateEndPoint = getTSEndpoint() + CONNECTION_SEARCH_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(connCreateEndPoint), HttpMethod.POST, httpEntity, String.class);
            log.info("TS Response for search connection :{}", result);
            if (result.getStatusCode().isError()) {
                log.error("Connection in ts does not exists, response body is {}", result.getBody());
                throw new SyncariValidationException("Connection in ts does not exists");
            }
            List<TSConnResponse> conn = mapper.readValue(result.getBody(), mapper.getTypeFactory().constructCollectionType(List.class, TSConnResponse.class));
            if (CollectionUtils.isEmpty(conn)){
                log.error("Connection in ts does not exists,response is not error, response body is {}", result.getBody());
                return Optional.empty();
            }
            return Optional.of(conn.get(0).getId());
        } catch (JsonProcessingException e) {
            log.error("Connection in ts does not exists, stack trace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Connection in ts does not exists");
        }catch (HttpClientErrorException e) {
            log.error("Connection in ts does not exists, response body is {} and stack trace is {}", e.getResponseBodyAsString(),ExceptionUtils.getStackTrace(e));
            return Optional.empty();
        }catch (Exception e) {
            log.error("Connection in ts does not exists, stacktrace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Connection in ts does not exists");
        }
    }

    @Override
    public Optional<TSConnResponse> searchConnectionV2(InsightsProviderConnection connection,Optional<String> tsUsername,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null == connection), String.format(i18n("object_null"),"Connection"));
        validateCondition(StringUtils.isEmpty(connection.getName()), String.format(i18n("object_name_empty"),"Connection"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("connections",List.of(Map.of("identifier", connection.getName())));
        payload.put("include_details", connection.isInclude_details());
        try{
            final String payloadString = mapper.writer().writeValueAsString(payload);
            log.info("TS Payload for search connection is :{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String connCreateEndPoint = getTSEndpoint() + CONNECTION_SEARCH_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(connCreateEndPoint), HttpMethod.POST, httpEntity, String.class);
            log.info("TS Response for search connection :{}", result);
            if (result.getStatusCode().isError()) {
                log.error("Connection in ts does not exists, response body is {}", result.getBody());
                throw new SyncariValidationException("Connection in ts does not exists");
            }
            List<TSConnResponse> connResponseList  = mapper.readValue(result.getBody(), mapper.getTypeFactory().constructCollectionType(List.class, TSConnResponse.class));
            if (CollectionUtils.isEmpty(connResponseList)){
                log.error("Connection in ts does not exists,response is not error, response body is {}", result.getBody());
                return Optional.empty();
            }
            // Assuming we only care about first connection in list, return that
            return Optional.of(connResponseList.get(0));
        } catch (JsonProcessingException e) {
            log.error("Connection in ts does not exists, stack trace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Connection in ts does not exists");
        }catch (HttpClientErrorException e) {
            log.error("Connection in ts does not exists, response body is {} and stack trace is {}", e.getResponseBodyAsString(),ExceptionUtils.getStackTrace(e));
            return Optional.empty();
        }catch (Exception e) {
            log.error("Connection in ts does not exists, stacktrace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Connection in ts does not exists");
        }
    }


    @Override
    public List<TSMetadataSearchResponse> searchMetadata(TSMetadataSearchReq metadataReq, Optional<String> tsUsername,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null == metadataReq), String.format(i18n("object_null"),"Metadata"));
        validateCondition(CollectionUtils.isEmpty(metadataReq.getMetadata()), String.format(i18n("object_name_empty"),"Metadata Type"));
        try{
            final String payloadString = mapper.writer().writeValueAsString(metadataReq);
            log.info("TS Payload of searchMetadata:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String liveboardSearchEp = getTSEndpoint() + METADATA_SEARCH_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(liveboardSearchEp), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("Metadata in ts does not exists, response body is {}", result.getBody());
                throw new SyncariValidationException("Metadata in ts does not exists");
            }
            List<TSMetadataSearchResponse> metadatas = mapper.readValue(result.getBody(), mapper.getTypeFactory().constructCollectionType(List.class, TSMetadataSearchResponse.class));
            log.info("TS Response of searchMetadata:{} and metadatas are {}", result,metadatas);
            if (CollectionUtils.isEmpty(metadatas)){
                log.error("Metadata in ts does not exists,response is not error, response body is {}", result.getBody());
                return List.of();
            }
            return metadatas;
        } catch (JsonProcessingException e) {
            log.error("Metadata in ts does not exists, stack trace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Metadata in ts does not exists");
        }catch (HttpClientErrorException e) {
            log.error("Metadata in ts does not exists, response body is {} and stack trace is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Metadata in ts does not exists");
        }catch (Exception e) {
            log.error("Metadata in ts does not exists, stacktrace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Metadata in ts does not exists");
        }
    }

    @Override
    public void deleteConnection(InsightsProviderConnection connection,Optional<String> tsUsername,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null == connection), String.format(i18n("object_null"),"Connection"));
        String connectionId = connection.getConnection_identifier();
        validateCondition(StringUtils.isEmpty(connectionId), String.format(i18n("object_name_empty"),"Connection"));
        Map<String, String> payload = new HashMap<>();
        try{
            payload.put("connection_identifier", connectionId);
            final String payloadString = mapper.writer().writeValueAsString(payload);
            log.info("TS Payload:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String conDelEndpoint = getTSEndpoint() + CONNECTION_DELETE_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(conDelEndpoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("Connection in ts is not deleted, response body is {}", result.getBody());
                throw new SyncariValidationException("Connection in ts is not deleted");
            }
            log.info("Successfully deleted connection with id {}", connectionId);
        }catch (HttpClientErrorException e) {
            log.error("Connection in ts is not deleted, response body is {} and stacktrace is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Connection in ts is not deleted");
        }catch (Exception e){
            log.error("Connection in ts is not deleted, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Connection in ts is not deleted");
        }
    }

    @Override
    @Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void updateConnection(InsightsProviderConnection connection,Optional<String> tsUsername,HttpHeaders headers) {
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((null == connection), String.format(i18n("object_null"),"Connection"));
        validateCondition(StringUtils.isEmpty(connection.getName()), String.format(i18n("object_name_empty"),"Connection"));
        try{
            final String payloadString = mapper.writer().writeValueAsString(connection);
            log.debug("TS Payload for update:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString, headers);
            String connUpdateEndPoint = getTSEndpoint() + CONNECTION_UPDATE_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(connUpdateEndPoint), HttpMethod.POST, httpEntity, String.class);
            log.debug("TS Response for update:{}", result);
            if (result.getStatusCode().isError()) {
                log.error("Connection in ts is not updated, response body is {}", result.getBody());
                throw new SyncariValidationException("Connection in ts is not updated");
            }
        } catch (JsonProcessingException e) {
            log.error("Connection in ts is not updated, stacktrace  is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Connection in ts is not updated");
        }catch (HttpClientErrorException e) {
            log.error("Connection in ts is not updated, response body is {} and stacktrace is {}" , e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Connection in ts is not updated");
        }catch (Exception e) {
            if (e instanceof HttpServerErrorException.InternalServerError){
                log.error("Update connection did not happen, Error is {}",((HttpServerErrorException.InternalServerError)e).getResponseBodyAsString());
                if (((HttpServerErrorException.InternalServerError) e).getResponseBodyAsString().contains("Connection to Postgres could not be established. The connection attempt failed")){
                    throw new RetriableException("CONNECTION_ERROR","Connection Attempt failed","CONNECTION_ERROR");
                }
            }
            log.error("Connection in ts is not updated, stacktrace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Connection in ts is not updated");
        }
    }


    private String getTSEndpoint(){
        return InsightsProvider.THOUGHTSPOT.endpoint();
    }

    @Override
    public HttpHeaders getHeaders(Optional<String> insightsProviderUserName, Long tokenTimeOut){
        HttpHeaders headers = new HttpHeaders();
        User user = SyncariContext.getUser();
        Organization org = SyncariContext.getOrganziation();
        String insightsUserName = StringUtils.isNotEmpty(user.getInsightsProviderUserName()) ? user.getInsightsProviderUserName() : TSService.TS_ADMIN_USER;
        String username = insightsProviderUserName.orElse(insightsUserName);
        final TSToken token = getBearerToken(username,org.getInsightsProviderOrgId(), tokenTimeOut.longValue());  // to create organization we need to user admin bearer token
        headers.set("Authorization", "Bearer " + token.getToken());
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.set(HttpHeaders.ACCEPT, "*/*");
        return headers;
    }

    private HttpHeaders getHeadersForPrimaryOrg(Optional<String> insightsProviderUserName){
        HttpHeaders headers = new HttpHeaders();
        User user = SyncariContext.getUser();
        String username = insightsProviderUserName.orElse(user.getInsightsProviderUserName());
        final TSToken token = getBearerToken(username,"0",300l);  // to create organization we need to user admin bearer token
        headers.set("Authorization", "Bearer " + token.getToken());
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.set(HttpHeaders.ACCEPT, "*/*");
        return headers;
    }

    @Override
    public TSToken getBearerToken(String userName, String orgId,Long tokenTimeOut){
       return getBearerTokenHelper(userName, orgId, tokenTimeOut);
    }

    private TSToken getBearerTokenHelper(String userName, String orgId,Long tokenTimeOut){
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((StringUtils.isBlank(userName)), String.format(i18n("object_name_empty"),"Username"));
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.set(HttpHeaders.ACCEPT, "*/*");
        Map<String, Object> payload = new HashMap<>();
        payload.put("username",userName);
        payload.put("validity_time_in_sec",tokenTimeOut.toString()); //86400
        payload.put("org_id",orgId);
        payload.put("auto_create",false);
        payload.put("secret_key",secretManager.getSecret(TS_API_KEY));

        try{
            final String payloadString = mapper.writer().writeValueAsString(payload);
            log.info("TS Payload for getBearerToken:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString,headers);

            String accessEndPoint = getTSEndpoint() + FULL_ACCESS_TOKEN_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(accessEndPoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("Token in ts is not found for user {}, response body is {}", userName,result.getBody());
                throw new SyncariValidationException("Token in ts is not found");
            }
            TSToken token = mapper.readValue(result.getBody(), TSToken.class);
            if (Objects.isNull(token)){
                log.error("Token in ts is not found for user {}, response body is {}", userName,result.getBody());
                throw new SyncariValidationException("Token in ts is not found for user {}", userName);
            }
            log.info("Token in ts found for user {}", userName);
            return token;
        } catch (JsonProcessingException e) {
            log.error("Token in ts not found, stack trace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Token in ts not found");
        }catch (HttpClientErrorException e) {
            log.error("Token in ts is not found for user, response body is {} and stack trace is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Token in ts is not found for user");
        }catch (Exception e){
            log.error("Token in ts is not found, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Token in ts is not found for user {}", userName);
        }
    }

    @Override
    public boolean validateToken(String token){
        RestTemplate tsTemplate = new RestTemplate();
        validateCondition((StringUtils.isBlank(token)), String.format(i18n("object_name_empty"),"Token"));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.set(HttpHeaders.ACCEPT, "*/*");
        Map<String, Object> payload = new HashMap<>();
        payload.put("token",token);
        try{
            final String payloadString = mapper.writer().writeValueAsString(payload);
            log.info("TS Payload for validate token:{}", payloadString);
            HttpEntity httpEntity = new HttpEntity(payloadString,headers);

            String accessEndPoint = getTSEndpoint() + VALIDATE_TOKEN_ENDPOINT;
            final ResponseEntity<String> result = tsTemplate.exchange(URI.create(accessEndPoint), HttpMethod.POST, httpEntity, String.class);
            if (result.getStatusCode().isError()) {
                log.error("Token in ts is not valid, response body is {}", result.getBody());
                throw new SyncariValidationException("Token in ts is not found");
            }
            log.info("Token in ts is valid");
            return true;
        } catch (JsonProcessingException e) {
            log.error("Token in ts is not valid, Stacktrace is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Token in ts not valid");
        }catch (HttpClientErrorException e) {
            log.error("Token in ts is not valid, Response body is {} and stacktrace is {}", e.getResponseBodyAsString(), ExceptionUtils.getStackTrace(e));
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED){
                log.error("Token in ts is not valid, UNAUTHORIZED request, token may not be valid");
                return false;
            }
            throw new SyncariValidationException("Token in ts is not valid");
        }catch (Exception e){
            log.error("Token in ts is not valid, exception stack is {}", ExceptionUtils.getStackTrace(e));
            throw new SyncariValidationException("Token in ts is not valid");
        }
    }
}
