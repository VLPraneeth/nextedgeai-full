package com.syncari.connector.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.sforce.soap.metadata.FieldType;
import com.sforce.soap.metadata.*;
import com.sforce.soap.partner.Connector;
import com.sforce.soap.partner.DeleteResult;
import com.sforce.soap.partner.Error;
import com.sforce.soap.partner.MergeRequest;
import com.sforce.soap.partner.SaveResult;
import com.sforce.soap.partner.*;
import com.sforce.soap.partner.fault.*;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;
import com.sforce.ws.SessionRenewer;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.SObjectIterator;
import com.syncari.connector.data.iterator.SalesforceAttachmentIterator;
import com.syncari.connector.exception.AuthenticationException;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.query.SalesforceSoql;
import com.syncari.connector.service.seed.SalesforceSeed;
import com.syncari.utils.DateUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.connector.service.helper.SalesforceHelper.getApiFaultMessage;
import static com.syncari.connector.service.helper.SalesforceHelper.handleException;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component(Constants.SALESFORCE)
public class SalesforceService implements CommonDataService, MetadataService, SynapseInfoService, OauthAuthenticationService {
	private static final String CONVERT_ERROR = "Converted objects can only be owned by users.  If the lead is not owned by a user, you must specify a user for the Owner field.";
    private static final int DELETE_BATCH_SIZE = 100;
    private static final int CONVERT_BATCH_SIZE = 30;
    private static final int DEFAULT_ENCRYPTED_TEXT_LENGTH = 175;
    private static final int DEFAULT_TEXT_LENGTH = 255;
    private static final int DEFAULT_LONG_TEXT_LENGTH = 32768;
    public static final String SYSTEM_MOD_STAMP = "SystemModstamp";
    public static final String CREATED_DATE = "CreatedDate";
    public static final String LAST_MODIFIED_DATE = "LastModifiedDate";
    private static final int QUERY_BATCHSIZE = 2000;
    private static final int SFDC_SESSION_DURATION = 110; //MINUTES, 10 mins before the sfdc default limit of 2 hrs;
    private static final int SALESFORCE_OAUTH_TOKEN_EXPIRY_SECS = SFDC_SESSION_DURATION*60; // a little less than 2 hours in seconds.
    private final static String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private final static Set<String> mergeSupportedEntities = Set.of("Contact", "Lead", "Account");
    private final static Set<String> relatedEntities = Set.of("Contact", "Account");
    private final static Set<String> systemFields = Set.of("Id", "IsDeleted", "CreatedById", "CreatedDate", "LastModifiedById", "LastModifiedDate", "SystemModstamp");
    public final static List<String> SFDC_DOCUMENT_OBJECTS = List.of("Document", "ContentDocument", "Attachment");
    public final static List<String> SFDC_FILE_SUPPORTED_OBJECTS = List.of("Opportunity", "Order");
    private static final String ATTACHMENT = "Attachment";
    //non-final for tests
    protected static int MERGE_BATCH_SIZE=100;
    private final int CLOCK_SKEW_TOLERANCE_SECS = 5 * 60;

    // Set connection timeout to 60 seconds.
    protected static int CONNECTION_TIMEOUT=60000;
    // Set socket timeout to 10 minutes
    protected static int SOCKET_TIMEOUT=600000;
    private final static String GET_ACCESS_TOKEN_URL = "/services/oauth2/token";

    @Autowired
    DefaultAuthTokenHandler tokenHandler;
    @Autowired
    Transformer transformer;
    @Autowired
    DateUtil dateUtil;
    LoadingCache<ConnectorInfo, PartnerConnection> cache = CacheBuilder.newBuilder()
            .maximumSize(100000).expireAfterAccess(SFDC_SESSION_DURATION, TimeUnit.MINUTES).build(
                    new CacheLoader<>() {
                        @Override
                        public PartnerConnection load(ConnectorInfo config) throws Exception {
                            return ConnectorHelper.backoffAndThrowOriginalException(() -> createConnection(config)
                                    ,5000, 10000, 5, Optional.empty());
                        }
                    }
            );

    private PartnerConnection createConnection(ConnectorInfo config) throws ConnectionException {
            validateConfig(config);
            ConnectorConfig c = getSalesforceConnector(config);
            return Connector.newConnection(c);
    }

    LoadingCache<ConnectorInfo, MetadataConnection> metaCache = CacheBuilder.newBuilder()
            .maximumSize(100000).expireAfterAccess(SFDC_SESSION_DURATION, TimeUnit.MINUTES).build(
                    new CacheLoader<>() {
                        @Override
                        public MetadataConnection load(ConnectorInfo config) throws Exception {
                            validateConfig(config);
                            ConnectorConfig c = getSalesforceConnector(config);
                            String authType = getAuthType(config);
                            if (authType.equalsIgnoreCase(AuthType.Oauth.name())) {
                                c.setSessionId(config.getAuthConfig().getAccessToken());
                            }
                            else if(authType.equalsIgnoreCase(AuthType.UserPasswordToken.name())) {
                                c.setManualLogin(true);
                                var pc = new PartnerConnection(c);
                                LoginResult loginResult = pc.login(c.getUsername(), c.getPassword());
                                c.setServiceEndpoint(loginResult.getMetadataServerUrl());
                                c.setAuthEndpoint(loginResult.getMetadataServerUrl());
                                String sessionId = cache.get(config).getSessionHeader().getSessionId();
                                c.setSessionId(sessionId);
                            }
                            return com.sforce.soap.metadata.Connector.newConnection(c);
                        }
                    }
            );

    private String getAuthType(ConnectorInfo config) {
        return config.getMetaConfig().getOrDefault("authType", AuthType.UserPasswordToken).toString();
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, "authorization_code",
                DefaultAuthTokenHandler.CODE, oAuthRequest.getCode(),
                DefaultAuthTokenHandler.CLIENT_ID, oAuthRequest.getConfig().getClientId(),
                DefaultAuthTokenHandler.CLIENT_SECRET, oAuthRequest.getConfig().getClientSecret(),
                DefaultAuthTokenHandler.REDIRECT_URI, oAuthRequest.getRedirectUri(),
                "format", "json"
        );
        AuthConfig authConfig = tokenHandler.getAccessToken(oAuthRequest.getEndpoint() + GET_ACCESS_TOKEN_URL, map);
        authConfig.setClientId(oAuthRequest.getConfig().getClientId());
        authConfig.setClientSecret(oAuthRequest.getConfig().getClientSecret());
        ConnectorInfo connector = new ConnectorInfo();
        connector.setEndpoint(oAuthRequest.getEndpoint());
        connector.setAuthConfig(authConfig);
        connector.setMetaConfig(Map.of("authType", AuthType.Oauth));
        try {
            log.debug("Fetched access token. Attempting to create salesforce connector");
            PartnerConnection partnerConnection = createConnection(connector);
            int expiresIn = partnerConnection.getUserInfo().getSessionSecondsValid();
            authConfig.setExpiresIn(String.valueOf(expiresIn));
            log.debug("Expires in {}", expiresIn);
        } catch (ConnectionException e) {
            handleException(e, connector);
        }
        return authConfig;
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        String clientId = config.getClientId();
        String clientSecret = config.getClientSecret();
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.REFRESH_TOKEN,
                DefaultAuthTokenHandler.REFRESH_TOKEN, config.getRefreshToken(),
                DefaultAuthTokenHandler.CLIENT_ID, config.getClientId(),
                DefaultAuthTokenHandler.CLIENT_SECRET, config.getClientSecret(),
                "format", "json"
        );

        try {
            config = tokenHandler.refreshToken(config, connector.getEndpoint() + GET_ACCESS_TOKEN_URL, map);
        } catch (Exception e) {
            if (e.getMessage().contains("invalid_grant") && (e.getMessage().contains("authentication failure")
                    || e.getMessage().contains("expired authorization code") || e.getMessage().contains("token request is already being processed"))) {
                throw new RetriableException(ErrorCodes.BAD_REQUEST, "Authentication failed while refreshing access token", HttpStatus.BAD_REQUEST.getReasonPhrase());
            } else {
                throw e;
            }
        }
        config.setClientId(clientId);
        config.setClientSecret(clientSecret);
        cache.invalidate(connector);
        connector.setAuthConfig(config);
        connector.setMetaConfig(Map.of("authType", AuthType.Oauth));
        try {
            log.debug("Renewed refresh token. Attempting to create salesforce connector");
            PartnerConnection partnerConnection = createConnection(connector);
            int expiresIn = partnerConnection.getUserInfo().getSessionSecondsValid();
            config.setExpiresIn(String.valueOf(expiresIn));
            log.debug("Expires in {}", expiresIn);
        } catch (ConnectionException e) {
            handleException(e, connector);
        }
        return config;
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "/services/oauth2/authorize?client_id={{client_id}}&redirect_uri={{redirect_uri}}&response_type=code";
    }

    @AllArgsConstructor
    @Getter
    class TimestampedConnection {
        private final PartnerConnection partnerConnection;
        private final Instant instant;

        public boolean needsRefresh() {
            return (Instant.now().getEpochSecond() - instant.getEpochSecond()) < 600;
        }
    }

    @AllArgsConstructor
    class TimestampedMetaConnection {
        private final PartnerConnection partnerConnection;
        private final Instant instant;
    }

    @Override
    public int lookBehindDuration(ConnectorInfo connectorInfo) {
        return Constants.FIVE_MIN_IN_MILLI; }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of(Constants.CONTACT.toLowerCase(), Constants.CONTACT, Constants.OPPORTUNITY.toLowerCase(), Constants.OPPORTUNITY,
                Constants.ACCOUNT.toLowerCase(), Constants.ACCOUNT, Constants.LEAD.toLowerCase(), Constants.LEAD,
                Constants.USER.toLowerCase(), Constants.USER, Constants.TICKET.toLowerCase(), "Case",
                Constants.DOCUMENT.toLowerCase(), "ContentDocument");

    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return SalesforceSeed.getAttributeMappings(entityApiName);
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwdToken(), ConnectorHelper.getAccessTokenOauthType());
    }

    @Override
    public List<AuthField> getConfigureFields() {
    	AuthField contactAccountMerge = new AuthField().setDataType("checkbox").setName("contactAccountMerge").setRequired(false)
                .setLabel("Enable Contacts to Multiple Accounts Merge");
        return List.of(ConnectorHelper.getEndpointField(), ConnectorHelper.getSupportedAuthPicker(), contactAccountMerge);
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19123385531668";
    }

    @Override
    public String getCategory() {
        return "CRM";
    }

    @Override
    public String getName() {
        return Constants.SALESFORCE;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/salesforce.svg")
                .setDisplayName("Salesforce")
                .setBackgroundColor("#F0FBFF")
                .setHelpUrl(helpArticlesBaseUrl + "/360052204672-Salesforce-Setup");
    }

    @Override
    public boolean validateEntityConfig(EntityParams params) {
    	if(params == null || params.getSchema() == null || params.getSourceParams() == null || 
    			params.getSourceParams().isEmpty()) return true;
		SyncRequest request = new SyncRequest().setSourceParams(params.getSourceParams())
				.setConnector(params.getConnector()).setPageSize(1).setWatermark(new WatermarkInfo()
						.setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli() + 1))
				.setEntitySchema(params.getSchema()).setEntitySchemaWithMappedFields(params.getSchema());
		FetchResponse response = getByWatermark(request);
		log.debug("Response received ", response);
		try {
			while(response.getIterator().hasNext()) {
				response.getIterator().next();
				break;
			}
		} catch (Exception e) {
			log.error("Error  in validateEntityConfig", e);
            handleException(e, request.getConnector());
		}
    	return true;
    }

    private String getWatermarkField(SyncRequest request){
        Optional<AttributeSchema> watermarkField = request.getEntitySchema().getWatermarkAttr();
        if(watermarkField.isPresent() && !SYSTEM_MOD_STAMP.equalsIgnoreCase(watermarkField.get().getApiName())){
            return watermarkField.get().getApiName();
        }
        return SYSTEM_MOD_STAMP;
    }
    
    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
    	if(request != null) {
    		log.debug("Watermark : {}, Entity Name: {} SourceParams: {}", request.getWatermark(), request.getEntityName(), request.getSourceParams());
    	}
        int limit = request.getWatermark().getLimit() > 0 ? request.getWatermark().getLimit() : request.getEntityName().equalsIgnoreCase(ATTACHMENT)? 100 : QUERY_BATCHSIZE;
        String query = "";
        String queryPredicate = request.getSourceParams().getOrDefault(getPredicateKey(request.getEntityName()), "").toString();
        if(!queryPredicate.isBlank()) {
        	queryPredicate = " AND ( " + queryPredicate +" )";
        }
        String watermarkField = getWatermarkField(request);
        if (!request.getWatermark().hasEnd()) {
            String soql = (request.getEntityName().equalsIgnoreCase("ContentDocument") || request.getEntityName().equalsIgnoreCase("ContentDocumentLink")) ? SalesforceSoql.QUERY_BY_WATERMARK_NO_END_CONTENT_DOCUMENT : SalesforceSoql.QUERY_BY_WATERMARK_NO_END;
            query = format(soql,
                    "%s",
                    request.getEntityName(),
                    watermarkField,
                    dateUtil.format(request.getWatermark().getStart(), dateFormat),
                    "%s",
                    watermarkField
            );
        } else {
            long start = request.getWatermark().getStart();
            String soql = (request.getEntityName().equalsIgnoreCase("ContentDocument") || request.getEntityName().equalsIgnoreCase("ContentDocumentLink")) ? SalesforceSoql.QUERY_BY_WATERMARK_CONTENT_DOCUMENT : SalesforceSoql.QUERY_BY_WATERMARK;
            query = format(soql,
                    "%s",
                    request.getEntityName(),
                    watermarkField,
                    dateUtil.format(start, dateFormat),
                    watermarkField,
                    dateUtil.format(request.getWatermark().getEnd(), dateFormat),
                    "%s",
                    watermarkField
            );
        }
        return new FetchResponse(request.getWatermark(), get(query, queryPredicate, request));
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        return getByIds(request, true);
    }

    public List<EntityData> getByIds(SyncRequest request, boolean usePredicate) {
        String queryPredicate = request.getSourceParams().getOrDefault(getPredicateKey(request.getEntityName()), "").toString();
        if(usePredicate && !queryPredicate.isBlank()) {
            queryPredicate = " AND ( " + queryPredicate+ " )";
        }
        List<AttributeSchema> attributes = request.getEntitySchemaWithMappedFields().getAttributes();
        List<String> ids = extractValidSfdcIds(request.getIds());
        if(ids.isEmpty()){
            return List.of();
        }
        String idsAsString = String.join(", ",
                ids.stream().map(i -> String.format("'%s'", i)).collect(Collectors.toList()));
        String query = String.format(SalesforceSoql.QUERY_BY_IDS, getFields(request.getEntitySchemaWithMappedFields()),
                request.getEntityName(), idsAsString, queryPredicate);
        List<EntityData> response = new ArrayList<>();
        log.debug(query);
        try {
            PartnerConnection conn = getClient(request.getConnector());
            conn.setQueryOptions(QUERY_BATCHSIZE);
            QueryResult result = conn.query(query);
            SObject[] records = result.getRecords();
            response = transformer.toEntityData(request.getConnector().getId(), request.getEntityName(), records,
                    attributes);
            logLimits(request.getConnector(), conn);
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
        return response;
    }

    @Override
    public DocumentResponse getFileContents(DocumentRequest request) {
        InputStream fileContents = FileInputStream.nullInputStream();
        if (request.getFileMetadata() != null && StringUtils.isNotEmpty(request.getFileMetadata().getId())) {
            List<String> ids = List.of(request.getFileMetadata().getId());
            String idsAsString = String.join(", ",
            ids.stream().map(i -> String.format("'%s'", i)).collect(Collectors.toList()));
            String contentField = Transformer.DOC_OBJECT_CONTENT_FIELD.get(request.getEntityName());
            String query = getDocumentContentQuery(request, contentField, idsAsString);
            List<EntityData> response = new ArrayList<>();
            log.debug(query);
            try {
                PartnerConnection conn = getClient(request.getConnector());
                conn.setQueryOptions(QUERY_BATCHSIZE);
                QueryResult result = conn.query(query);
                SObject[] records = result.getRecords();
                if ("ContentDocument".equalsIgnoreCase(request.getEntityName())) {
                    response = transformer.toContentDocumentFileEntityData("ContentVersion", records, contentField);
                } else {
                    response = transformer.toContentDocumentFileEntityData(request.getEntityName(), records, contentField);
                }
                logLimits(request.getConnector(), conn);

                if(CollectionUtils.isNotEmpty(response)){
                    fileContents = new ByteArrayInputStream((byte[]) Base64.getDecoder().decode(response.get(0).getValue(contentField).toString()));
                }
            } catch (Exception e) {
                handleException(e, request.getConnector());
            }
        }
        
        return new DocumentResponse(fileContents, request.getFileMetadata());
    }

    private String getDocumentContentQuery(DocumentRequest request, String contentField, String idsAsString) {
        if ("ContentDocument".equalsIgnoreCase(request.getEntityName())) {
            return String.format("select %s from %s where ContentDocumentId in (%s) and IsLatest = true", 
                contentField, "ContentVersion", idsAsString);
        }
        return String.format(SalesforceSoql.QUERY_BY_IDS, contentField, request.getEntityName(), idsAsString, "");
    }

    protected List<String> extractValidSfdcIds(List<String> ids) {
        return ids.stream().filter(Objects::nonNull).filter(id->id.matches("[a-zA-Z0-9]{18}|[a-zA-Z0-9]{15}")).collect(Collectors.toList());
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        String watermarkField = getWatermarkField(request);
        String query = String.format(SalesforceSoql.QUERY_FIRST_RECORD, watermarkField, request.getEntityName(), watermarkField);
        try {
            PartnerConnection conn = getClient(request.getConnector());
            QueryResult result = conn.query(query);
            if (result != null && result.getRecords().length == 1) {
                Object systemModstamp = result.getRecords()[0].getField(watermarkField);
                // return epochmilli 1 less than the firstCreatedTime as getByWatermark queries do not have inclusive start time
                return dateUtil.toEpochMilli(systemModstamp.toString()) - 1;
            }
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
        log.warn("Could not get the first record's created time for {}", request.getEntityName());
        return Instant.EPOCH.toEpochMilli();
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        return post(request, Operation.create);
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return post(request, Operation.update);
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        if (SFDC_DOCUMENT_OBJECTS.contains(request.getEntitySchema().getApiName())) {
            return delete(request, true);
        }
        return delete(request, false);
    }

    public SyncResponse delete(SyncRequest request, boolean hardDelete) {
        SyncResponse response = null;
        try {
            PartnerConnection conn = getClient(request.getConnector());
            List<String> ids2 = request.getIds();
            Map<String, String> syncariIdBySfdcIdMap = new HashMap<>();
            request.getData().get(request.getConnector().getId()).stream().forEach(x -> {
                if (!StringUtils.isEmpty(x.getSyncariEntityId())) syncariIdBySfdcIdMap.put(x.getId(), x.getSyncariEntityId());
            });
            String reqBatchSize = (String) request.getDestParams().get(Constants.PIPELINE_BATCH_SIZE);
			int batchSize = StringUtils.isBlank(reqBatchSize) ? DELETE_BATCH_SIZE : Integer.parseInt(reqBatchSize.trim());
            var partitioned = Lists.partition(ids2, batchSize);
            List<DeleteResult> deleteResults = new ArrayList<>();
            partitioned.forEach(partition -> {
                try {
                    log.debug("Processing {} records to Salesforce", partition.size());
                    String[] ids = ArrayUtils.toStringArray(partition.toArray());
                    DeleteResult[] results = conn.delete(ids);
                    if (hardDelete) {
                        List<String> deletedIds = Arrays.asList(results).stream().map(r -> r.getId()).filter(r -> r != null)
                                .collect(Collectors.toList());
                        if(!deletedIds.isEmpty()) {
                            conn.emptyRecycleBin(ArrayUtils.toStringArray(deletedIds.toArray()));
                        }
                    }
                    deleteResults.addAll(Arrays.asList(results));
                } catch (Exception e) {
                    handleException(e, request.getConnector());
                }
            });
            response = transformer.toSyncResponse(deleteResults.toArray(new DeleteResult[0]), syncariIdBySfdcIdMap);
            log.debug(format("Delete completed successfully for connector %s", request.getConnector().getName()));
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
        // TODO: Is this the right place to stick this in?
        processFileReferences(request, response, Operation.delete);
        return response;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        EntitySchema schema = null;
        try {
            PartnerConnection conn = getClient(request.getConnector());
            DescribeSObjectResult result = conn.describeSObject(request.getEntity());
            schema = toEntitySchema(result);
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
        return Optional.of(schema);
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> definitions = new ArrayList<>();
        try {
            PartnerConnection conn = getClient(request.getConnector());
            List<String> allObjectTypes = getAllObjectTypes(request);
            var partitioned = Lists.partition(allObjectTypes, 100);
            partitioned.forEach(partition -> {
                log.debug(format("Fetching schema for %s with size: %s", request.getConnector().getName(),
                        partition.size()));
                DescribeSObjectResult[] result;
                try {
                    describeObjects(partition, conn, definitions);
                } catch (Exception e) {
                    log.error("Exception occurred while describing the objects " + partition);
                    boolean retrySuccess = false;
                    if (ApiFault.class.isAssignableFrom(e.getClass())) {
                        String msg = getApiFaultMessage(e);
                        log.error(msg);
                        if(msg.contains("sObject type") && msg.contains("is not supported.")) {
                            Optional<String> objectName = extractObjectName(msg);
                            if(objectName.isPresent()) {
                                log.info("Retrying describe after removing {}", objectName.get());
                                partition.remove(objectName.get());
                                try {
                                    describeObjects(partition, conn, definitions);
                                    retrySuccess = true;
                                } catch (Exception e1) {
                                    handleException(e1, request.getConnector());
                                }
                            }
                        }
                    }
                    if(!retrySuccess) {
                        handleException(e, request.getConnector());
                    }
                }
            });
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
        return definitions;
    }

    private Optional<String> extractObjectName(String msg) {
        Pattern pattern = Pattern.compile("sObject type '([^']+)'");
        Matcher matcher = pattern.matcher(msg);

        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }

        return Optional.empty();
    }

    private void describeObjects(List<String> partition, PartnerConnection conn, List<EntitySchema> definitions) throws ConnectionException {
        DescribeSObjectResult[] result;
        result = conn.describeSObjects(partition.stream().toArray(String[]::new));
        for (DescribeSObjectResult sObject : result) {
            EntitySchema entitySchema = toEntitySchema(sObject);
            if(entitySchema.hasIdField()) {
                definitions.add(entitySchema);
            }
        }
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        try {
            if (request.getSchema() == null || StringUtils.isBlank(request.getSchema().getApiName())) {
                throw new RuntimeException("Api name is required for field creation");
            }
            if (StringUtils.isBlank(request.getEntityName())) {
                throw new RuntimeException("Entity name is required for field creation");
            }
            PartnerConnection conn = getClient(request.getConnector());
            String fieldName = request.getEntityName() + "." + request.getSchema().getApiName();
            if (!request.getSchema().getApiName().endsWith("__c")) {
                fieldName = fieldName.concat("__c");
                request.getSchema().setApiName(request.getSchema().getApiName().concat("__c"));
            }

            // Check if field already exists
            DescribeSObjectResult schema = conn.describeSObject(request.getEntityName());
            Field[] fields = schema.getFields();
            for (int j = 0; j < fields.length; j++) {
                if (fields[j].getName().equalsIgnoreCase(fieldName)) {
                    throw new RuntimeException(
                            format("Field %s already exists in %s", fieldName, request.getConnector().getName()));
                }
            }

            MetadataConnection metaConn = getMetaClient(request.getConnector(), conn.getSessionHeader().getSessionId());
            CustomField field = new CustomField();
            field = setDataType(field, request.getSchema());
            field.setFullName(fieldName);
            field.setLabel(request.getSchema().getDisplayName());
            metaConn.createMetadata(new Metadata[]{field});
            assignPermissions(conn, metaConn, fieldName);
            log.info("Field {} created successfully in {}", fieldName, request.getConnector().getName());
        } catch (ConnectionException e) {
            handleException(e, request.getConnector());
        }

        return request.getSchema();
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        try {
            PartnerConnection conn = getClient(request.getConnector());
            MetadataConnection metaConn = getMetaClient(request.getConnector(), conn.getSessionHeader().getSessionId());
            CustomField field = new CustomField();
            field.setFullName(request.getEntityName() + "." + request.getFieldName());
            metaConn.deleteMetadata("CustomField", new String[]{field.getFullName()});
            log.info("Successfully deleted field {} in sfdc for {}", request.getFieldName(),
                    request.getConnector().getName());
        } catch (ConnectionException e) {
            handleException(e, request.getConnector());
        }
    }

    public ConvertResponse convertLead(ConvertRequest request) {
        ConvertResponse response = new ConvertResponse();
        try {
            PartnerConnection conn = getClient(request.getConnector());
            Map<String , LeadConvert> leadMap = new HashMap<>();

            List<List<ConvertData>> partitions = Lists.partition(request.getData(), CONVERT_BATCH_SIZE);
            for (List<ConvertData> partition : partitions) {
            	LeadConvert[] leads = new LeadConvert[partition.size()];
                int i = 0;
                for (ConvertData lead : partition) {
                    LeadConvert convert = toLeadConvert(lead, request.isDoNotCreateOpportunity());
                    leads[i] = convert;
                    leadMap.put(lead.getLeadId(), convert);
                    i++;
                }
                List<String> failedLeads = new ArrayList<>();
                
                // Try to convert lead without setting the ownerId
                LeadConvertResult[] results = conn.convertLead(leads);
                i = 0;
                for (LeadConvertResult result : results) {
                	ConvertResult r = toConvertResult(result);
                	if(!result.isSuccess()){
                        log.error("Error converting sfdc lead {}", r);

                        // Making sure leadId is set on the result
                        // Request and Response indexes will be same as per the documentation
                        if (StringUtils.isEmpty(r.getLeadId())){
                            r.setLeadId(leads[i].getLeadId());
                        }
                	}
                	if(result.getErrors().length > 0 && r.getError().contains(CONVERT_ERROR)) {
                		failedLeads.add(r.getLeadId());
                	}else{
                		response.getData().add(r);
                	}
                	i++;
                }
                log.debug("Successfully converted "+leads.length+" leads for "+request.getConnector().getName());
            }
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
        return response;
    }

    public SaveResult[] addToCampaign(String objectType, List<CampaignMember> newMembership, ConnectorInfo connector) {
        try {
            PartnerConnection conn = getClient(connector);
            Map<String, CampaignMember> entityToMember = new HashMap<String, CampaignMember>();
            newMembership.stream().forEach(p -> {
                entityToMember.put(p.getObjectId(), p);
            });

            // Query the existing membership to see if the contact/lead is already a member
            List<CampaignMember> existing = getExistingCampaignMembership(objectType, conn, entityToMember);
            List<CampaignMember> existingCamps = new ArrayList<>();
            List<CampaignMember> newCamps = new ArrayList<>();

            // For all new, create membership
            Map<String, Set<CampaignMember>> existingCampToLeadId = new HashMap<>();
            for (CampaignMember campaignMember : existing) {
                existingCampToLeadId.putIfAbsent(campaignMember.getCampaignId(), new HashSet<>());
                existingCampToLeadId.get(campaignMember.getCampaignId()).add(campaignMember);
            }
            for (CampaignMember campaignMember : newMembership) {
                if (existingCampToLeadId.containsKey(campaignMember.getCampaignId())) {
                    Optional<CampaignMember> mem = existingCampToLeadId.get(campaignMember.getCampaignId()).stream()
                            .filter(c -> c.getObjectId().equals(campaignMember.getObjectId())).findAny();
                    if(mem.isPresent()) {
                        campaignMember.setId(mem.get().getId());
                        existingCamps.add(campaignMember);
                    } else {
                        newCamps.add(campaignMember);
                    }
                } else {
                    newCamps.add(campaignMember);
                }
            }

            SObject[] createRequest = new SObject[newCamps.size()];
            int i = 0;
            for (CampaignMember c : newCamps) {
                SObject obj = new SObject("CampaignMember");
                obj.setField("CampaignId", c.getCampaignId());
                obj.setField(StringUtils.capitalize(objectType)+"Id", c.getObjectId());
                obj.setField("Status", c.getStatus());
                createRequest[i] = obj;
                i++;
            }
            SaveResult[] createResults = conn.create(createRequest);

            // For all existing, update status
            SaveResult[] updateResults = updateCampaignMembershipStatus(conn, existingCamps, entityToMember);
            logResult(createResults);
            logResult(updateResults);
            log.debug("Successfully completed addToCampaign with create results {}", createResults);
            log.debug("Successfully completed addToCampaign with update results {}", updateResults);
            return createResults;
        } catch (Exception e) {
            handleException(e, connector);
            return null;
        }
    }

    private void logResult(SaveResult[] createResults) {
        for(SaveResult r : createResults){
            log.debug("Campaign Member Create Result, isSucccess: {}, Returned Id {}", r.isSuccess(),r.getId());
            for(Error e :r.getErrors()){
                log.warn("Error Summary {}, {}, {}",e.getMessage(),e.getStatusCode(), Arrays.asList(e.getFields()));
                log.warn("Error Details {} ",
                        String.join("|", Arrays.asList(e.getExtendedErrorDetails()).stream().
                                map(ex->ex.getExtendedErrorCode().toString()).collect(Collectors.toList()))
                );
            }
        }
    }

    class MergeData {
        com.syncari.connector.data.MergeRequest syncariMR;
        Map<MergeRequest, MergeResult> sfdcMRs=new LinkedHashMap<>();
        MergeResponse response;

        public boolean hasSFDCMR(MergeRequest mr){
            return sfdcMRs.containsKey(mr);
        }
        public void attachResult(MergeRequest mr, MergeResult result){
            sfdcMRs.put(mr, result);
        }

        public void generateResponse() {
            if(sfdcMRs.isEmpty()){
                //handle cases where losers don't exist in SFDC, and winner is jusst an upssert
                response = upsertWinner(syncariMR);
            }else{
                response = transformer.toSyncResponse(syncariMR, sfdcMRs.values().toArray(MergeResult[]::new));
            }
        }
    }

    class MergeDataSet{
        List<MergeData>  mergeDataset = new ArrayList<>();

        public void add(com.syncari.connector.data.MergeRequest mr, List<MergeRequest> sfdcMRs){
            MergeData mergeData = new MergeData();
            mergeData.syncariMR = mr;
            sfdcMRs.forEach(sfdcMR-> mergeData.attachResult(sfdcMR,null));
            mergeDataset.add(mergeData);
        }

        public void attachSFDCResult(MergeRequest sfdcMR, MergeResult sfdcMRResult) {
            mergeDataset.stream().filter(m->m.hasSFDCMR(sfdcMR)).findFirst().ifPresent(mergeData ->mergeData.attachResult(sfdcMR, sfdcMRResult));
        }
    }

    @Override
    public List<MergeResponse> merge(List<com.syncari.connector.data.MergeRequest> requests) {
        if(requests.isEmpty()){
            return List.of();
        }
        com.syncari.connector.data.MergeRequest example = requests.get(0);
        String entityName = example.getEntityName();
        log.debug("Merge initiated for {}", entityName);
        if(!mergeSupportedEntities.contains(entityName)) {
            return CommonDataService.super.merge(requests);
        }
        boolean contactAccountMerge = false;
        if (example.getConnector() != null && example.getConnector().getMetaConfig() != null) {
        	var flag = example.getConnector().getMetaConfig().getOrDefault("contactAccountMerge", false);
        	if(flag != null) {
        		contactAccountMerge = BooleanUtils.toBoolean(flag.toString());
        	}
        }
        log.debug("contactAccountMerge flag {} connector {}", contactAccountMerge, example.getConnector().getName());
        try {
            PartnerConnection conn = getClient(example.getConnector());
            List<MergeRequest> allMergeRequests = new ArrayList<>();
            MergeDataSet mergeDataSet = new MergeDataSet();
            requests.forEach(request -> {
                List<MergeRequest> mergeRequests = createSFDCMergeRequests(request).collect(Collectors.toList());
                mergeDataSet.add(request, mergeRequests);
                allMergeRequests.addAll(mergeRequests);
            });
            List<List<MergeRequest>> partitions = Lists.partition(allMergeRequests, MERGE_BATCH_SIZE);
            for (List<MergeRequest> mrs : partitions) {
            	Map<String, List<String>> masterLooserMap = new HashMap<>();
            	List<AccountContactRelation> looserAcrs = null;
            	List<AccountContactRelation> winnerAcrs = null;
            	log.debug("Initating merge {} {}", entityName, contactAccountMerge);
            	if(contactAccountMerge && relatedEntities.contains(entityName)) {
            		//Get the account relations of loser records and winner records
            		List<String> objectIds = new ArrayList<String>();
            		List<String> winnerObjectIds = new ArrayList<String>();
            		mrs.forEach(mr -> {
            			winnerObjectIds.add(mr.getMasterRecord().getId());
            			objectIds.addAll(Arrays.asList(mr.getRecordToMergeIds()));
            			masterLooserMap.put(mr.getMasterRecord().getId(), Arrays.asList(mr.getRecordToMergeIds()));
            		});
            		log.debug("Loading Looser AccountContactRelation for {} with ids {}", entityName, objectIds);
            		looserAcrs = getExistingAccountContactRelation(entityName, example.getConnector(), objectIds);
            		log.debug("Loading Winner AccountContactRelation for {} with ids {}", entityName, winnerObjectIds);
            		winnerAcrs = getExistingAccountContactRelation(entityName, example.getConnector(), winnerObjectIds);
            		//Delete the loser/winner records relations
            		//Can't delete direct relations. So keep it as is
					List<String> acrIds = new ArrayList<String>();
					acrIds.addAll(looserAcrs.stream().filter(acr -> !acr.isDirect())
							.map(acr -> acr.getId()).collect(Collectors.toList()));
					List<String> otherEntityIds = looserAcrs.stream().map(acr -> getOtherEntityId(acr, entityName)).collect(Collectors.toList());
					List<String> winnerAcrIds = winnerAcrs.stream().filter(acr -> !acr.isDirect()).filter(acr -> otherEntityIds.contains(getOtherEntityId(acr, entityName))).map(acr -> acr.getId()).collect(Collectors.toList());
            		log.debug("Deleting AccountContactRelation(Looser) with ids {}", acrIds);
            		log.debug("Deleting AccountContactRelation(Winner) with ids {}", winnerAcrIds);
            		acrIds.addAll(winnerAcrIds);
            		var delResult = conn.delete(acrIds.toArray(String[]::new));
            		log.debug("Delete result AccountContactRelation {}", Arrays.asList(delResult));
            	}
                MergeResult[] mergeResults = conn.merge(mrs.toArray(MergeRequest[]::new));
                for(int i=0;i<mrs.size();i++) {
                    mergeDataSet.attachSFDCResult(mrs.get(i), mergeResults[i]);
                }
                if(contactAccountMerge && relatedEntities.contains(entityName)) {
                	log.debug("Creating indirect AccountContactRelation for master records {}", masterLooserMap.keySet());
                	createAccountContactRelation(entityName, example.getConnector(), masterLooserMap, looserAcrs);
                }
            }
            //genrate responses using merge results and potentially simple upsert
            mergeDataSet.mergeDataset.forEach(syncariMR -> {
                syncariMR.generateResponse();
            });
            return  mergeDataSet.mergeDataset.stream().map(m->m.response).collect(Collectors.toList());
        } catch (Exception e) {
            handleException(e, example.getConnector());
        }
        return List.of();
    }
    
    private String getOtherEntityId(AccountContactRelation acr, String entityName) {
    	if ("Contact".equalsIgnoreCase(entityName)) {
    		return acr.getAccountId();
    	} else {
    		return acr.getContactId();
    	}
    }

	protected void createAccountContactRelation(String entityName, ConnectorInfo connector,
			Map<String, List<String>> masterLooserMap, List<AccountContactRelation> acrs) throws Exception{
		if (MapUtils.isEmpty(masterLooserMap) || CollectionUtils.isEmpty(acrs)) {
			return;
		}
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		log.debug("Creating AccountContactRelation for winners {}", masterLooserMap.keySet());
		List<SObject> acrSObjects = new ArrayList<SObject>();
		for(String master : masterLooserMap.keySet()) {
			var loosers = masterLooserMap.get(master);
			List<AccountContactRelation> masterRelations = getExistingAccountContactRelation(entityName, connector, List.of(master));
			List<AccountContactRelation> filteredAcrs = List.of();
			if ("Contact".equalsIgnoreCase(entityName)) {
				// Filter by contact id
				List<String> masterAccounts = masterRelations.stream().map(acr -> acr.getAccountId()).collect(Collectors.toList());
				filteredAcrs = acrs
						.stream().filter(acr -> loosers.contains(acr.getContactId()) && !masterAccounts.contains(acr.getAccountId()))
						.collect(Collectors.toList());
			} else if ("Account".equalsIgnoreCase(entityName)) {
				// Filter by account id
				List<String> masterContacts = masterRelations.stream().map(acr -> acr.getContactId()).collect(Collectors.toList());
				filteredAcrs = acrs
						.stream().filter(acr -> !acr.isDirect()
								&& loosers.contains(acr.getAccountId()) && !masterContacts.contains(acr.getContactId()))
						.collect(Collectors.toList());
			}
			for(var acr : filteredAcrs) {
				SObject sArc = new SObject("AccountContactRelation");
				sArc.setId(null);
				if ("Contact".equalsIgnoreCase(entityName)) {
					sArc.setField("AccountId", acr.getAccountId());
					sArc.setField("ContactId", master);
				} else if ("Account".equalsIgnoreCase(entityName)) {
					sArc.setField("AccountId", master);
					sArc.setField("ContactId", acr.getContactId());
				}
				sArc.setField("IsActive", acr.isActive());
				if(acr.getStartDate() != null) {
					sArc.setField("StartDate", df.parse(acr.getStartDate().toString()));
				}
				if(acr.getEndDate() != null) {
					sArc.setField("EndDate", df.parse(acr.getEndDate().toString()));
				}
				sArc.setField("Roles", acr.getRoles());
				
				acrSObjects.add(sArc);
			}
		}
		log.debug("Creating AccountContactRelation {}", acrSObjects.size());
		if(!acrSObjects.isEmpty()) {
			var savedResult = getClient(connector).create(acrSObjects.toArray(SObject[]::new));
			log.debug("Create result AccountContactRelation {}", Arrays.asList(savedResult));
		}

	}

	@Override
    public MergeResponse merge(com.syncari.connector.data.MergeRequest request) {
        String entityName = request.getEntityName();
        // this check is to break the recursion from base class
        if(!mergeSupportedEntities.contains(entityName)) {
            return CommonDataService.super.merge(request);
        }
        List<MergeResponse> mergeResponses = merge(List.of(request));
        return mergeResponses.isEmpty() ? null : mergeResponses.get(0);
    }

    private Stream<MergeRequest> createSFDCMergeRequests(com.syncari.connector.data.MergeRequest request) {
        List<EntityData> entityList = request.getLosers();
        if (entityList == null || entityList.isEmpty()) {
            log.warn("SFDC Merge request  {} found , but with no losers.Skipping operation", request);
            return Stream.empty();
        }
        List<String> loserIds = entityList.stream().map(x -> x.getId()).collect(Collectors.toList());
        log.debug("Processing winner: {}, loserIds: {} ", request.getWinner().getId(), loserIds);
        final SObject masterRecord = transformer.toSObject(request.getEntitySchema(), null, request.getWinner());
        // Max 2 losers
        List<List<EntityData>> partitions = Lists.partition(entityList, 2);
        return partitions.stream().map(partition -> {
            var mr = new MergeRequest();
            mr.setMasterRecord(masterRecord);
            String[] losers = partition.stream().filter(p->p.getId()!=null).map(p -> p.getId()).toArray(String[]::new);
            mr.setRecordToMergeIds(losers);
            return mr;
        }).filter(mr -> mr.getRecordToMergeIds()!=null && mr.getRecordToMergeIds().length > 0);
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            String authType = getAuthType(config);
            if (authType.equalsIgnoreCase(AuthType.Oauth.name()) && StringUtils.isBlank(config.getAuthConfig().getRefreshToken())) {
                throw new AuthenticationException(config.getId(), config.getName(), "Refresh token not found. Make sure Oauth settings are correct in Salesforce 'Connected App' setup. For reference, please check https://support.syncari.com/hc/en-us/articles/8444561327892-Configure-the-Salesforce-Synapse-Using-OAuth-2-0-Authentication");
            }
            evict(config);
            PartnerConnection conn = getClient(config);
            conn.setQueryOptions(QUERY_BATCHSIZE);
            String profileId = conn.getUserInfo().getProfileId();
            log.debug(format("Checking permissions for user with profile id %s", profileId));

            String objectString = String.join(", ",
                    entityNames.stream().map(i -> String.format("'%s'", i)).collect(Collectors.toList()));
            String query = String.format(SalesforceSoql.QUERY_PROFILE_WITH_OBJECT_PERMISSIONS, profileId, objectString);
            log.debug(query);
            // query to ensure the creds are valid
            QueryResult result = conn.query(query);
            result.getRecords();
            log.debug(format("Test connection for salesforce done: %s", response));
            return response;
        } catch (ConnectionException | AuthenticationException e) {
            response = new TestConnectionResponse("Error when testing the authenticated connection", ConnectorErrorCodes.CONNECTION_ERROR,
                    Arrays.asList(e.getMessage()));
            if (ApiFault.class.isAssignableFrom(e.getClass()) && handleInvalidSession(config, (ApiFault) e)) {
                throw new RetriableException(e.getMessage(), e.getMessage(), e.getMessage());
            }
            if (LoginFault.class.isAssignableFrom(e.getClass())) {
                response = new TestConnectionResponse(TestConnectionResponse.AUTH_FAILED_MESSAGE, ConnectorErrorCodes.CONNECTION_ERROR,
                        Arrays.asList(((LoginFault) e).getExceptionMessage()));
            }
            if (UnexpectedErrorFault.class.isAssignableFrom(e.getClass()) && e.getMessage() != null
                    && e.getMessage().contains("API_CURRENTLY_DISABLED")) {
                response = new TestConnectionResponse("API_CURRENTLY_DISABLED", ConnectorErrorCodes.CONNECTION_ERROR,
                        Arrays.asList(((UnexpectedErrorFault) e).getExceptionMessage()));
            }
            if (InvalidSObjectFault.class.isAssignableFrom(e.getClass())) {
                response = new TestConnectionResponse("Error when checking permissions for user", ConnectorErrorCodes.CONNECTION_ERROR,
                        Arrays.asList(((InvalidSObjectFault) e).getExceptionMessage()));
            }
           if(response.getErrors() == null || response.getErrors().isEmpty()) {
               response.setErrors(Arrays.asList("Please verify credentials and try again."));
           }
            log.error(format("Test connection for %s failed: %s", config.getName(), response.getMessage()), e);
            return response;
        }
    }

    @Override
    public boolean validate(ConnectorInfo connector) {
        String endpoint = connector.getEndpoint();
        if (!StringUtils.isEmpty(endpoint)) {
            endpoint = endpoint.trim();
            if (!endpoint.endsWith("/")) {
                endpoint = endpoint + "/";
            }
            if (!endpoint.endsWith("salesforce.com/")) {
                throw new RuntimeException(i18n("salesforce_invalid_endpoint"));
            }
        }
        return true;
    }

    private String escape(String value) {
        return value.replace("'", "\\'").replace("\"", "\\\"").replace("\\", "\\\\").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\t", "\\t").replace("$", "\\$");
    }
    @Override
    public List<EntityData> search(SearchRequest request) {
        List<EntityData> response = new ArrayList<>();
        log.debug(request.getQuery());
        String requestQuery = request.getQuery();
        if(StringUtils.isBlank(request.getQuery())) return List.of();
        if(!CollectionUtils.isEmpty(request.getParams()) && request.getQuery().contains("?")) {
            int placeholderCount = StringUtils.countMatches(request.getQuery(), "?");
            if(placeholderCount != request.getParams().size()) {
                log.debug("invalid query {} and params", requestQuery);
                return List.of();
            }
            for(Object param : request.getParams()) {
                // escape special characters first
                String escapedParam = escape(param.toString());
                requestQuery = requestQuery.replaceFirst("\\?", escapedParam);
            }
        }
        // select Email,FirstName from Lead where id=''
        String[] parts = requestQuery.split(" ");
        if(parts.length < 4) return List.of();
        String[] fields = parts[1].split(",");
        String entity = parts[3];
        try {
            PartnerConnection conn = getClient(request.getConnector());
            conn.setQueryOptions(1000);
            QueryResult result = conn.query(requestQuery);
            SObject[] records = result.getRecords();
            response = transformer.toEntityData(request.getConnector().getId(), records, fields, entity);
            if(entity.equalsIgnoreCase("ContentDocument")) {
                EntitySchema entitySchema = describe(new DescribeRequest(request.getConnector(), "ContentDocument")).orElseThrow(() -> new RuntimeException("ContentDocument describe failed"));
                response.forEach(entityData -> {
                    DocumentRequest documentRequest = new DocumentRequest(request.getConnector(), entitySchema, entityData);
                    DocumentResponse documentResponse = getFileContents(documentRequest);
                    String filePath = String.format("%s/%s_%s_%s_%s", request.getConnector().getInstanceId(),
                    request.getConnector().getId(), entity, EntityData.SYNCARI_FILE_LINK_FIELD_NAME, entityData.getId());
                    request.getStorage().write(documentResponse.getContents(), filePath);
                    entityData.addValue(EntityData.SYNCARI_FILE_LINK_FIELD_NAME, filePath);
                });
            }
            logLimits(request.getConnector(), conn);
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
        return response;
    }

    @Override
    public List<Capability> getCapabilities() {
        return List.of(Capability.create, Capability.update, Capability.delete, Capability.search, Capability.getById,
                Capability.getByWatermark);
    }

    private SObjectIterator get(String query, String queryPredicate, SyncRequest request) {
        SObjectIterator iterator = null;
        try {
            log.debug(query);
            List<AttributeSchema> attributes = request.getEntitySchemaWithMappedFields().getAttributes();
            PartnerConnection conn = ConnectorHelper.backoffAndThrowOriginalException(() -> createConnection(request.getConnector())
                    ,5000, 10000, 5, Optional.empty());
            int pageSize = request.getPageSize() >0 ? request.getPageSize() : QUERY_BATCHSIZE;
            conn.setQueryOptions(pageSize);
            iterator = SObjectIterator.builder().attributes(attributes).entityName(request.getEntityName()).conn(conn)
                    .isInitial(request.getWatermark() == null ? false : request.getWatermark().isInitial())
                    .isInitial(request.getWatermark() == null ? false : request.getWatermark().isInitial())
                    .offset(request.getWatermark().getOffset()).transformer(transformer).dateUtil(dateUtil).query(query).queryPredicate(queryPredicate)
                    .connectorInfo(request.getConnector())
                    .maxResults(request.getWatermark().getLimit())
                    .build();

            if(request.getEntityName().equalsIgnoreCase(ATTACHMENT)) {
                iterator = new SalesforceAttachmentIterator(new ArrayList<>(), request.getEntityName(), transformer, attributes, conn, dateUtil, query, queryPredicate,
                        request.getWatermark().getOffset(), request.getWatermark() == null ? false : request.getWatermark().isInitial(), request.getConnector(),
                        1, new Stats(), Instant.EPOCH.toEpochMilli(), request.getWatermark().getLimit(), 0);
            }

            logLimits(request.getConnector(), conn);

        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
        log.debug("SObjectIterator {}", iterator);
        return iterator;
    }

    private void logLimits(ConnectorInfo connectorInfo, PartnerConnection conn) {
        LimitInfoHeader_element limitInfoHeader = conn.getLimitInfoHeader();
        if (limitInfoHeader != null && limitInfoHeader.getLimitInfo() != null) {
            for (int i = 0; i < limitInfoHeader.getLimitInfo().length; i++) {
                var limitInfo = limitInfoHeader.getLimitInfo()[i];
                log.info("SFDC LIMITS ConnectorId:{}, Current:{}, Limit:{}, Type:{}", connectorInfo.getId(),
                        limitInfo.getCurrent(), limitInfo.getLimit(), limitInfo.getType());
            }
        }
    }

    private EntitySchema getContentDocumentLinkSchema() {
        EntitySchema contentDocumentLinkSchema = new EntitySchema("ContentDocumentLink");
        contentDocumentLinkSchema.addField(new AttributeSchema("ContentDocumentId", "string").setStatus(Status.ACTIVE));
        contentDocumentLinkSchema.addField(new AttributeSchema("LinkedEntityId", "string").setStatus(Status.ACTIVE));
        contentDocumentLinkSchema.addField(new AttributeSchema("ShareType", "string").setStatus(Status.ACTIVE));
        return contentDocumentLinkSchema;
    }

    // File references are a special case where we need to create/upsert ContentDocumentLinks accordingly.
    // These are implicit operations as part of the main/parent object CUD.
    private void processFileReferences(SyncRequest request, SyncResponse response, Operation op) {
        if (!response.isSuccess()) return;

        boolean hasFileReferenceInSchema = request.getEntitySchema().getAttributes().stream()
            .anyMatch(x -> x.getApiName().equalsIgnoreCase(EntityData.SYNCARI_FILE_REFERENCE_FIELD_NAME));
        
        if (hasFileReferenceInSchema) {
            List<EntityData> parentObjects = request.getData().get(request.getConnector().getId());
            // For creates, we need to pick the created ids from the response and augment here.
            if (op == Operation.create) {
                for (int i = 0; i < parentObjects.size(); i++) {
                    if (StringUtils.isEmpty(parentObjects.get(i).getId())) {
                        parentObjects.get(i).setId(response.getResults().get(i).getId());
                    }
                }
            }
            List<EntityData> contentDocumentLinks = new ArrayList<>();
            List<String> linkedEntityIds = new ArrayList<>();
            for (EntityData ed: parentObjects) {
                if (ed.has(EntityData.SYNCARI_FILE_REFERENCE_FIELD_NAME)) {
                    List<String> fileRefs = (List<String>) ed.getValue(EntityData.SYNCARI_FILE_REFERENCE_FIELD_NAME);
                    fileRefs.forEach(x -> {
                        EntityData contentDocLink = new EntityData("ContentDocumentLink");
                        contentDocLink.addValue("ContentDocumentId", x);
                        contentDocLink.addValue("LinkedEntityId", ed.getId());
                        contentDocLink.addValue("ShareType", "I");
                        contentDocumentLinks.add(contentDocLink);
                        linkedEntityIds.add(ed.getId());
                    });
                }
            }

            if (contentDocumentLinks.size() > 0) {
                SyncRequest contentLinkRequest = new SyncRequest().setConnector(request.getConnector())
                    .setEntitySchema(getContentDocumentLinkSchema());
                contentLinkRequest.getData().put(request.getConnector().getId(), contentDocumentLinks);
                try {
                    PartnerConnection conn = getClient(request.getConnector());

                    // First cleanup existing links
                    deleteExistingContentDocumentLinks(conn, linkedEntityIds);

                    // If this is a main object delete operation, nothing to do here.
                    if(op == op.delete) {
                        return;
                    }

                    List<List<EntityData>> partitions = Lists.partition(contentDocumentLinks, POST_BATCH_SIZE);
                    for (List<EntityData> partition : partitions) {
                        SObject[] sObjects = transformer.toSObjects(contentLinkRequest, partition);
                        SaveResult[] results = new SaveResult[sObjects.length];
                        results = conn.create(sObjects);
                        var contentLinksResponse = transformer.toSyncResponse(results, partition, op);
                        logLimits(request.getConnector(), conn);
                    }
                } catch (Exception e) {
                    handleException(e, request.getConnector());
                }
            }
        }
    }

    protected SyncResponse post(SyncRequest request, Operation op) {
        SyncResponse fullResponse = new SyncResponse();
        boolean isSuccess = true;
        try {
            PartnerConnection conn = getClient(request.getConnector());
            List<EntityData> entityList = request.getData().get(request.getConnector().getId());
            String reqBatchSize = (String) request.getDestParams().get(Constants.PIPELINE_BATCH_SIZE);
			int batchSize = StringUtils.isBlank(reqBatchSize) ? POST_BATCH_SIZE : Integer.parseInt(reqBatchSize.trim());
            List<List<EntityData>> partitions = Lists.partition(entityList, SFDC_DOCUMENT_OBJECTS.contains(request.getEntityName()) ? 1 : batchSize);
            // ContentDocument sync always creates a new version of ContentVersion.
            if ("ContentDocument".equalsIgnoreCase(request.getEntityName())) op = Operation.create;

            boolean exceptionOccurred = false;

            for (List<EntityData> partition : partitions) {
                log.debug("Processing {} records to Salesforce", partition.size());

                if (!exceptionOccurred) {
                    try {
                        processPartition(conn, request, op, partition, fullResponse);
                    } catch (Exception e) {
                        //This exception means that the entire batch request fail due a Salesforce validation, this
                        //doesn't mean that the whole batch is wrong, instead we send individual request to log the proper
                        //record error
                        if (e.getMessage() != null && e.getMessage().contains("is not a valid value for the type xsd")) {
                            exceptionOccurred = true;
                            processIndividualRecords(conn, request, op, partition, fullResponse);
                        } else if (e.getMessage() != null && e.getMessage().contains("Failed to get next element")) {
                            exceptionOccurred = true;
                            processIndividualRecords(conn, request, op, partition, fullResponse);
                        } else {
                            throw e;
                        }
                    }
                }
            }
        } catch (Exception e) {
            handleException(e, request.getConnector());
            isSuccess = false;
        }
        fullResponse.setSuccess(isSuccess);
        processFileReferences(request, fullResponse, op);
        return fullResponse;
    }

    private void processPartition(PartnerConnection conn, SyncRequest request, Operation op, List<EntityData> partition, SyncResponse fullResponse) throws Exception {
        SObject[] sObjects = transformer.toSObjects(request, partition);
        SaveResult[] results = new SaveResult[sObjects.length];
        switch (op) {
            case create:
                results = conn.create(sObjects);
                break;
            case update:
                results = conn.update(sObjects);
                break;
            default:
                break;
        }
        var response = transformer.toSyncResponse(results, partition, op);
        if ("ContentDocument".equalsIgnoreCase(request.getEntityName())) {
            processDocumentResponses(request, response);
        }
        fullResponse.getResults().addAll(response.getResults());
        fullResponse.getErrors().addAll(response.getErrors());
        boolean isSuccess = response.isSuccess() && response.getErrors().isEmpty();
        logLimits(request.getConnector(), conn);
        log.debug("Processed {} records for op {} with status {}", sObjects.length, op, isSuccess);
    }

    private void processIndividualRecords(PartnerConnection conn, SyncRequest request, Operation op, List<EntityData> partition, SyncResponse fullResponse) {
        for (EntityData entity : partition) {
            try {
                SObject[] sObjects = transformer.toSObjects(request, List.of(entity));
                SaveResult[] results = new SaveResult[sObjects.length];
                switch (op) {
                    case create:
                        results = conn.create(sObjects);
                        break;
                    case update:
                        results = conn.update(sObjects);
                        break;
                    default:
                        break;
                }
                var response = transformer.toSyncResponse(results, List.of(entity), op);
                if ("ContentDocument".equalsIgnoreCase(request.getEntityName())) {
                    processDocumentResponses(request, response);
                }
                fullResponse.getResults().addAll(response.getResults());
                fullResponse.getErrors().addAll(response.getErrors());
                boolean isSuccess = response.isSuccess() && response.getErrors().isEmpty();
                logLimits(request.getConnector(), conn);
                log.debug("Processed 1 record for op {} with status {}", op, isSuccess);
            } catch (Exception ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("Failed to get next element")) {
                    log.error("Failed to process record {}", entity);
                }
                handleException(ex, request.getConnector());
                fullResponse.setSuccess(false);
            }
        }
    }

    private void processDocumentResponses(SyncRequest request, SyncResponse response) {
        List<String> contentVersionIDs = response.getResults().stream().map(x -> x.getId()).collect(Collectors.toList());
        // We dont want to do a describe call for this. We only need the ContentDocumentId from the ContentVersion.
        EntitySchema contentVersionSchema = new EntitySchema("ContentVersion");
        contentVersionSchema.addField(new AttributeSchema("Id", "id").setIdField(true).setStatus(Status.ACTIVE));
        contentVersionSchema.addField(new AttributeSchema("ContentDocumentId", "string").setStatus(Status.ACTIVE));
        SyncRequest contentVersionReq = new SyncRequest().setConnector(request.getConnector()).setEntitySchema(contentVersionSchema).setEntitySchemaWithMappedFields(contentVersionSchema);
        List<EntityData> edWithIds = new ArrayList<>();
        for (String contentVersionId: contentVersionIDs) {
            edWithIds.add(new EntityData("ContentVersion").setId(contentVersionId));
        }
        contentVersionReq.getData().put(request.getConnector().getId(), edWithIds);
        List<EntityData> contentVersions = getByIds(contentVersionReq, false);
        List<Result> contentDocumentResults = new ArrayList<>();
        for (EntityData contentVersion: contentVersions) {
            response.getResults().forEach(x -> {
                if (x.getId().equalsIgnoreCase(contentVersion.getId())) {
                    contentDocumentResults.add(
                        new Result(x.isSuccess(), contentVersion.getValueAsString("ContentDocumentId"), x.getSyncariId()));
                }
            });
        }
        response.setResults(contentDocumentResults);
    }

    private String getFields(EntitySchema entity) {
        return String.join(", ", entity.getAttributes().stream()
            .filter(a -> a.getStatus() == Status.ACTIVE && !a.isFileLink() 
                && !EntityData.SYNCARI_FILE_REFERENCE_FIELD_NAME.equalsIgnoreCase(a.getApiName()))
            .map(a -> a.getApiName()).collect(Collectors.toList()));
    }

    PartnerConnection getClient(ConnectorInfo config) throws ConnectionException {
        try {
            return cache.get(config);
        } catch (ExecutionException | UncheckedExecutionException e) {
            //Unwrap underlying runtimeexception and rethrow
            if(e.getCause()!=null && RuntimeException.class.isAssignableFrom(e.getCause().getClass())){
                throw (RuntimeException) e.getCause();
            }
            if(e.getCause()!=null && ConnectionException.class.isAssignableFrom(e.getCause().getClass())){
                throw (ConnectionException) e.getCause();
            }

            throw new RuntimeException(e);
        }
    }

    MetadataConnection getMetaClient(ConnectorInfo config, String sessionId) throws ConnectionException {
        try {
            return metaCache.get(config);
        } catch (Exception e) {
            if(e.getCause()!=null && RuntimeException.class.isAssignableFrom(e.getCause().getClass())){
                throw (RuntimeException) e.getCause();
            }
            if(e.getCause()!=null && ConnectionException.class.isAssignableFrom(e.getCause().getClass())){
                throw (ConnectionException) e.getCause();
            }

            throw new RuntimeException(e);
        }
    }

    private List<String> getAllObjectTypes(DescribeAllRequest request) {
        List<String> result = null;
        try {
            PartnerConnection conn = getClient(request.getConnector());
            DescribeGlobalResult describeGlobal = conn.describeGlobal();
            DescribeGlobalSObjectResult[] sobjects = describeGlobal.getSobjects();
            List<String> allSfdcObjects = new ArrayList<String>();
            List<String> resultObjects = new ArrayList<String>();
            for (DescribeGlobalSObjectResult sobject : sobjects) {
                allSfdcObjects.add(sobject.getName());
            }
            for (String entity : request.getEntities()) {
                // Only add objects stored in Syncari if it exists in SFDC, else it means
                // object is deleted in SFDC
                if (allSfdcObjects.contains(entity)) {
                    resultObjects.add(entity);
                }
            }
            for (DescribeGlobalSObjectResult sobject : sobjects) {
                resultObjects.add(sobject.getName());
                log.debug("Got object for sfdc : {}", sobject.getName());
            }
            result = resultObjects;
            logLimits(request.getConnector(), conn);
        } catch (Exception e) {
            handleException(e, request.getConnector());
        }
        return result;
    }

    private EntitySchema toEntitySchema(DescribeSObjectResult result) {
        EntitySchema entityDefinition = new EntitySchema();
        entityDefinition.setApiName(result.getName());
        entityDefinition.setDisplayName(result.getLabel());
        entityDefinition.setCustom(result.isCustom());
        log.debug("Transforming entity {} to EntitySchema", result.getName());
        for (Field f : result.getFields()) {
            log.debug("Transforming field {}({}) to AttributeSchema of entity {}", f.getName(), f.getLabel(), result.getName());
            if (f.getType() == com.sforce.soap.partner.FieldType.address) {
                log.debug("Not adding field {} to schema as it is of type address", f.getName());
                continue;
            }
            boolean isIdField = f.getName().equalsIgnoreCase("Id");
            AttributeSchema attr = new AttributeSchema();
            attr.setApiName(f.getName());
            attr.setDisplayName(f.getLabel());
            attr.setDataType(f.getType().toString());
            attr.setCustom(f.isCustom());
            if (f.getDefaultValue() != null) {
                attr.setDefaultValue(f.getDefaultValue().toString());
            } else if(f.getType() == com.sforce.soap.partner.FieldType._boolean) {
            	attr.setDefaultValue(Boolean.FALSE.toString());
            }
            attr.setInitializable(f.isCreateable());
            attr.setCalculated(f.isCalculated());
            if (result.getName().equals("Order") && attr.getApiName().equals("StatusCode")){
                attr.setNillable(true);
            }else{
                attr.setNillable(!isIdField && (f.isNillable() || f.getDefaultedOnCreate()));
            }
            attr.setUnique(f.isUnique() || isIdField);
            attr.setUpdateable(f.isUpdateable() || f.isCreateable());
            attr.setCreateOnly(f.isCreateable() && !f.isUpdateable());
            attr.setLength(f.getLength());
            attr.setPrecision(f.getPrecision());
            attr.setScale(f.getScale());
            attr.setIdField(isIdField);
            if (f.getReferenceTo() != null && f.getReferenceTo().length > 0) {
                // Add special case for ownerId- SYN-4286
                String apiName = attr.getApiName();
                if ((null != apiName) && (apiName.equalsIgnoreCase("OwnerId"))){
                    for (String ref: f.getReferenceTo()){
                        if (ref.equalsIgnoreCase("User")){
                            attr.setReferenceTo(ref);
                            break;
                        }
                    }
                    // If not find user - SYN-4286
                    if (null == attr.getReferenceTo()){
                        attr.setReferenceTo(f.getReferenceTo()[0]);
                    }
                }else{
                    attr.setReferenceTo(f.getReferenceTo()[0]);
                }
                attr.setReferenceTargetField((f.getReferenceTargetField() == null) ? "Id" : f.getReferenceTargetField());
            }
            if ("picklist".equals(f.getType().name()) || "multipicklist".equals(f.getType().name())) {
                attr.setPicklistValues(
                        Arrays.stream(f.getPicklistValues()).map(p -> p.getValue()).collect(Collectors.toList()));
                if ("multipicklist".equals(f.getType().name())) {
                    attr.setDataType("picklist");
                    attr.setMultiValueField(true);
                }
            }
            if ("SystemModstamp".equalsIgnoreCase(f.getName())) {
                attr.setWatermarkField(true);
            }
            if(systemFields.contains(f.getName())) {
                attr.setSystem(true);
            }

            entityDefinition.getAttributes().add(attr);
        }
        if (SFDC_DOCUMENT_OBJECTS.contains(entityDefinition.getApiName())) {
            entityDefinition.addField(new AttributeSchema(EntityData.SYNCARI_FILE_LINK_FIELD_NAME, "filelink").setDisplayName("Syncari File Link")
                .setSyncariDefined(true));
        }
        if (SFDC_FILE_SUPPORTED_OBJECTS.contains(entityDefinition.getApiName())) {
            entityDefinition.addField(new AttributeSchema(EntityData.SYNCARI_FILE_REFERENCE_FIELD_NAME,"reference")
                        .setStatus(Status.ACTIVE)
                        .setMultiValueField(true)
                        .setDisplayName("Files")
                        .setNillable(true)
                        .setReferenceTo("ContentDocument")
                        .setReferenceTargetField("Id")
                        .setSyncariDefined(true)
                );
        }
        if (Constants.LEAD.equalsIgnoreCase(entityDefinition.getApiName()) && entityDefinition.hasField("Status")) {
            entityDefinition.getField("Status").get().setNillable(false);
        }
        if (Constants.OPPORTUNITY.equalsIgnoreCase(entityDefinition.getApiName()) && entityDefinition.hasField("AccountId")) {
            entityDefinition.getField("AccountId").get().setNillable(false);
        }
        if (ATTACHMENT.equalsIgnoreCase(entityDefinition.getApiName())) {
            entityDefinition.setReadOnly(true);
            if (entityDefinition.hasField("Body")) {
                //We do not want to track binary information
                entityDefinition.removeField("Body");
            }
            if (entityDefinition.hasField("ParentId") ) {
                entityDefinition.getField("ParentId").get().setDataType("polymorphicreference");
            }
            if (!entityDefinition.hasField("Parent.Type")) {
                entityDefinition.addField(new AttributeSchema("Parent.type","string")
                        .setStatus(Status.ACTIVE)
                        .setSystem(true)
                        .setDisplayName("Parent Type"));
            }
        }
        AttributeSchema predicate = new AttributeSchema(getPredicateKey(entityDefinition.getApiName()), "textarea");
        predicate.setDisplayName("Filter Query for source");
		entityDefinition.getSourceParams().add(predicate);
        if (!entityDefinition.getWatermarkAttr().isPresent()){
            Optional<AttributeSchema> optLastModifiedDateField = entityDefinition.getField(LAST_MODIFIED_DATE);
            optLastModifiedDateField.ifPresentOrElse(
                    lastModifiedDateField -> {
                        if (lastModifiedDateField.getDataType().equalsIgnoreCase("datetime")){
                            entityDefinition.getField(LAST_MODIFIED_DATE).get().setWatermarkField(true);
                        } else {
                            if (entityDefinition.getField(CREATED_DATE).isPresent()){
                                entityDefinition.getField(CREATED_DATE).get().setWatermarkField(true);
                            }
                        }
                    },
                    () -> {
                        if (entityDefinition.getField(CREATED_DATE).isPresent()){
                            entityDefinition.getField(CREATED_DATE).get().setWatermarkField(true);
                        }
                    }
            );
        }

        AttributeSchema pipeLineBatchSize = new AttributeSchema(Constants.PIPELINE_BATCH_SIZE, "integer");
        pipeLineBatchSize.setDisplayName("Batch Size");
        entityDefinition.getDestParams().add(pipeLineBatchSize);

        return entityDefinition;
    }

    private void evict(ConnectorInfo config) {
        cache.invalidate(config);
        metaCache.invalidate(config);
    }

    private boolean handleInvalidSession(ConnectorInfo config, ApiFault ex) {
        if (ex.getExceptionCode() == ExceptionCode.INVALID_SESSION_ID) {
            // If the error is because of expired session id, clear the cache so the api can
            // be retried
            evict(config);
            return true;
        }
        return false;
    }

    private CustomField setDataType(CustomField customField, AttributeSchema schema) {
        customField.setDefaultValue(schema.getDefaultValue());
        switch (schema.getDataType().toLowerCase()) {
            case "string":
            case "text":
                customField.setType(FieldType.Text);
                customField.setLength(schema.getLength() == 0 ? DEFAULT_TEXT_LENGTH : schema.getLength());
                return customField;
            case "longtextarea":
                customField.setType(FieldType.LongTextArea);
                customField.setLength(schema.getLength() == 0 ? DEFAULT_LONG_TEXT_LENGTH : schema.getLength());
                // TODO make it configurable
                customField.setVisibleLines(10);
                return customField;
            case "encryptedtext":
                customField.setType(FieldType.EncryptedText);
                customField.setMaskChar(EncryptedFieldMaskChar.asterisk);
                // TODO make it configurable
                customField.setMaskType(EncryptedFieldMaskType.all);
                customField.setLength(schema.getLength() == 0 ? DEFAULT_ENCRYPTED_TEXT_LENGTH : schema.getLength());
                return customField;
            case "currency":
                customField.setType(FieldType.Currency);
                customField.setPrecision(schema.getPrecision() == 0 ? 3 : schema.getPrecision());
                customField.setScale(schema.getScale() == 0 ? 2 : schema.getScale());
                return customField;
            case "number":
                customField.setType(FieldType.Number);
                customField.setPrecision(schema.getPrecision() == 0 ? 3 : schema.getPrecision());
                customField.setScale(schema.getScale() == 0 ? 2 : schema.getScale());
                return customField;
            case "percent":
                customField.setType(FieldType.Percent);
                customField.setPrecision(schema.getPrecision() == 0 ? 3 : schema.getPrecision());
                customField.setScale(schema.getScale() == 0 ? 2 : schema.getScale());
                return customField;
            case "picklist":
                customField.setType(FieldType.Picklist);
                customField.setValueSet(getPicklist(schema));
                return customField;
            case "multiselectpicklist":
                customField.setType(FieldType.MultiselectPicklist);
                customField.setValueSet(getPicklist(schema));
                // TODO make it configurable
                customField.setVisibleLines(10);
                return customField;
            case "checkbox":
                customField.setType(FieldType.Checkbox);
                customField.setDefaultValue(
                        StringUtils.isBlank(schema.getDefaultValue()) ? "False" : schema.getDefaultValue());
                return customField;
            default:
                for (FieldType type : FieldType.values()) {
                    if (type.name().equalsIgnoreCase(schema.getDataType())) {
                        customField.setType(type);
                        return customField;
                    }
                }
        }
        throw new RuntimeException(format("Datatype %s not supported", schema.getDataType()));
    }

    private ValueSet getPicklist(AttributeSchema schema) {
        ValueSet valueSet = new ValueSet();
        int i = 0;
        CustomValue[] picklistValues = new CustomValue[schema.getPicklistValues().size()];
        ValueSetValuesDefinition values = new ValueSetValuesDefinition();
        for (String v : schema.getPicklistValues()) {
            CustomValue picklistValue = new CustomValue();
            picklistValue.setFullName(v);
            picklistValue.setLabel(v);
            picklistValues[i] = picklistValue;
            i++;
        }
        values.setValue(picklistValues);
        valueSet.setValueSetDefinition(values);
        return valueSet;
    }

    private ConnectorConfig getSalesforceConnector(ConnectorInfo config) {
        ConnectorConfig c = new ConnectorConfig();
        String authType = getAuthType(config);

        if (authType.equalsIgnoreCase(AuthType.Oauth.name())) {
            c.setSessionId(config.getAuthConfig().getAccessToken());
            c.setSessionRenewer(new SessionRenewer() {
                ConnectorInfo connectorInfo = config;
                @Override
                public SessionRenewalHeader renewSession(ConnectorConfig connectorConfig) throws ConnectionException {
                    log.debug("Attempting to renew session");
                    AuthConfig config = refreshToken(connectorInfo);
                    connectorConfig.setSessionId(config.getAccessToken());
                    PartnerConnection partnerConnection = Connector.newConnection(connectorConfig);
                    SessionRenewer.SessionRenewalHeader header = new SessionRenewer.SessionRenewalHeader();
                    header.name = new QName("urn:partner.soap.sforce.com", "SessionHeader");
                    header.headerElement = partnerConnection.getSessionHeader();
                    log.debug("Session renewed");
                    return header;
                }
            });
        }
        else if(authType.equalsIgnoreCase(AuthType.UserPasswordToken.name())) {
            c.setUsername(config.getAuthConfig().getUserName());
            c.setPassword(config.getAuthConfig().getPassword() + config.getAuthConfig().getToken());
        }
        if (config.getEndpoint() != null) {
            String endpoint = config.getEndpoint();
            if (!endpoint.endsWith("/")) {
                endpoint = endpoint + "/";
            }
            endpoint = endpoint + "services/Soap/u/62.0";
            c.setAuthEndpoint(endpoint);
            c.setConnectionTimeout(CONNECTION_TIMEOUT);
            c.setReadTimeout(SOCKET_TIMEOUT);
            c.setServiceEndpoint(endpoint);
        }
        return c;
    }

    private void validateConfig(ConnectorInfo config) {
        String authType = getAuthType(config);

        if (StringUtils.isEmpty(authType)) {
            String msg = String.format("Failed to acquire access token. No authentication type provided.");
            log.error(msg);
            throw new RuntimeException(msg);
        }

        if (config == null || config.getAuthConfig() == null) {
            throw new AuthenticationException(config.getId(), config.getName(), i18n("auth_failed"));
        }

        if (authType.equalsIgnoreCase(AuthType.UserPasswordToken.name())) {
            if ((StringUtils.isBlank(config.getAuthConfig().getUserName())
                    || StringUtils.isBlank(config.getAuthConfig().getPassword())
                    || StringUtils.isBlank(config.getAuthConfig().getToken()))) {
                log.error("Missing username/password/token");
                throw new AuthenticationException(config.getId(), config.getName(), i18n("auth_failed"));
            }
        }
        else if (authType.equalsIgnoreCase(AuthType.Oauth.name())) {
            if ((StringUtils.isBlank(config.getAuthConfig().getClientId()) || StringUtils.isBlank(config.getAuthConfig().getClientSecret()))) {
                log.error("Missing clientid/clientsecret");
                throw new AuthenticationException(config.getId(), config.getName(), i18n("auth_failed"));
            }
        }
    }

    private void assignPermissions(PartnerConnection con, MetadataConnection metaConn, String fullFieldName)
            throws ConnectionException {
        String userProfileId = con.getUserInfo().getProfileId();
        ListMetadataQuery listMetadataQuery = new ListMetadataQuery();
        listMetadataQuery.setType("Profile");
        FileProperties[] profiles = metaConn.listMetadata(new ListMetadataQuery[]{listMetadataQuery}, 62.0);
        var existingProfile = Arrays.asList(profiles).stream().filter(p -> userProfileId.equals(p.getId())).findFirst()
                .orElseThrow(() -> new RuntimeException("Profile with id " + userProfileId + " not found"));

        Profile profile = new Profile();
        profile.setFullName(existingProfile.getFullName());
        ProfileFieldLevelSecurity profileFieldLevelSecurity = new ProfileFieldLevelSecurity();
        profileFieldLevelSecurity.setEditable(true);
        profileFieldLevelSecurity.setField(fullFieldName);
        profileFieldLevelSecurity.setReadable(true);
        profile.setFieldPermissions(new ProfileFieldLevelSecurity[]{profileFieldLevelSecurity});
        metaConn.updateMetadata(new Metadata[]{profile});
        log.debug("Successfully set permissions for field {}", fullFieldName);
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in salesforce yet");
    }

    private String joinAsString(Collection<String> ids) {
        return String.join(", ",
                ids.stream().map(i -> String.format("'%s'", i)).collect(Collectors.toList()));
    }

    private SaveResult[] updateCampaignMembershipStatus(PartnerConnection conn, List<CampaignMember> existing, Map<String, CampaignMember> entityToMember)
            throws ConnectionException {
        SObject[] updateRequest = new SObject[existing.size()];
        int i=0;
        for (CampaignMember c : existing) {
            SObject obj = new SObject("CampaignMember");
            obj.setField("Id", c.getId());
            obj.setField("Status", entityToMember.get(c.getObjectId()).getStatus());
            updateRequest[i] = obj;
            i++;
        }
        return conn.update(updateRequest);
    }

    private List<CampaignMember> getExistingCampaignMembership(String objectType, PartnerConnection conn,
                                                               Map<String, CampaignMember> entityToMember) throws ConnectionException {
        if(entityToMember.isEmpty()) return List.of();
        String queryString = format(SalesforceSoql.QUERY_CAMPAIGN_MEMBERSHIP,
                StringUtils.capitalize(objectType) + "Id", joinAsString(entityToMember.keySet()));
        QueryResult existingMembers = conn.query(queryString);
        List<CampaignMember> existing = new ArrayList<>();
        for (SObject obj : existingMembers.getRecords()) {
            String objectId = obj.getField(StringUtils.capitalize(objectType)+"Id").toString();
            existing.add(new CampaignMember().setId(obj.getField("Id").toString()).setObjectId(objectId)
                    .setCampaignId(obj.getField("CampaignId").toString())
                    .setStatus(entityToMember.get(objectId).getStatus()));
        }
        return existing;
    }
    
    

    private void deleteExistingContentDocumentLinks(PartnerConnection conn, List<String> linkedEntityIds) 
            throws ConnectionException {
        if(linkedEntityIds.isEmpty()) return;
        String queryString = format(SalesforceSoql.QUERY_CONTENT_DOCUMENT_LINK, "LinkedEntityId", joinAsString(linkedEntityIds));
        QueryResult existingLinks = conn.query(queryString);
        List<String> ids = new ArrayList<>();
        for (SObject obj : existingLinks.getRecords()) {
            EntityData rec = new EntityData("ContentDocumentLink");
            ids.add(obj.getField("Id").toString());
        }
        if (ids.size() > 0) {
            List<List<String>> partitions = Lists.partition(ids, POST_BATCH_SIZE);
            for (List<String> partition : partitions) {
                DeleteResult[] results = conn.delete(ArrayUtils.toStringArray(partition.toArray()));
            }
        }
    }

    private ConvertResult toConvertResult(LeadConvertResult result) {
        ConvertResult r = new ConvertResult();
        r.setSuccess(result.isSuccess());
        r.setLeadId(result.getLeadId());
        r.setContactId(result.getContactId());
        r.setAccountId(result.getAccountId());
        r.setOpptyId(result.getOpportunityId());
        r.setError(StringUtils.join(result.getErrors(), ","));
        return r;
    }

    private LeadConvert toLeadConvert(ConvertData lead, boolean doNotCreateOpportunity) {
        LeadConvert convert = new LeadConvert();
        convert.setLeadId(lead.getLeadId());
        if(doNotCreateOpportunity) {
            convert.setDoNotCreateOpportunity(doNotCreateOpportunity);
        } else {
            convert.setOpportunityId(lead.getOpportunityId());
        }
        convert.setAccountId(lead.getAccountId());
        convert.setContactId(lead.getContactId());
        if(!StringUtils.isBlank(lead.getOwnerId())) {
            convert.setOwnerId(lead.getOwnerId());
        }
        String status = StringUtils.isBlank(lead.getConvertedStatus()) ? "Qualified" : lead.getConvertedStatus();
        convert.setConvertedStatus(status);
        log.debug("Convert request {}", convert);
        return convert;
    }
    
    private String getPredicateKey(String entityName) {
    	return entityName.toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE;
    }
    
	protected List<AccountContactRelation> getExistingAccountContactRelation(String objectType, ConnectorInfo connector,
			List<String> ids) throws ConnectionException {
		if (ids.isEmpty())
			return List.of();
		String queryString = format(SalesforceSoql.QUERY_ACCOUNT_CONTACT_RELATION, StringUtils.capitalize(objectType) + "Id",
				joinAsString(ids));
		QueryResult existingACRs = getClient(connector).query(queryString);
		List<AccountContactRelation> existing = new ArrayList<>();
		for (SObject obj : existingACRs.getRecords()) {
			boolean active = Optional.ofNullable(obj.getField("IsActive")).map(Object::toString).map(BooleanUtils::toBoolean).orElse(false);
			boolean direct = Optional.ofNullable(obj.getField("IsDirect")).map(Object::toString).map(BooleanUtils::toBoolean).orElse(false);
			existing.add(new AccountContactRelation().setId(obj.getField("Id").toString())
					.setAccountId(obj.getField("AccountId").toString())
					.setContactId(obj.getField("ContactId").toString())
					.setActive(active)
					.setDirect(direct)
					.setEndDate(obj.getField("EndDate"))
					.setStartDate(obj.getField("StartDate"))
					.setRoles(obj.getField("Roles")));
		}
		return existing;
	}
}
