package com.syncari.connector.impartner;

import static com.syncari.utils.I18n.i18n;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.Status;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.CreateFieldRequest;
import com.syncari.connector.data.CreateObjectRequest;
import com.syncari.connector.data.DataWithOffset;
import com.syncari.connector.data.DeleteFieldRequest;
import com.syncari.connector.data.DescribeAllRequest;
import com.syncari.connector.data.DescribeRequest;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.OAuthRequest;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.UIMetadata;
import com.syncari.connector.data.WatermarkInfo;
import com.syncari.connector.data.iterator.DefaultDataOffsetIterator;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.service.Transformer;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.DateUtil;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(Constants.IMPARTNER)
public class ImpartnerService implements OauthAuthenticationService, CommonDataService, MetadataService, SynapseInfoService {

    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;

    @Autowired
    DateUtil dateUtil;

    public static final String TIME_ZONE_ID = "timeZoneId";
    private static final String dateFormat = "yyyy-MM-dd HH:mm:ss";

    // Connector level constants
    public static final String API_HOST_URL = "https://prod.impartner.live";
    public static final String ACCESS_KEY_HEADER = "prm-key %s";
    public static final long TOKEN_EXPIRY_SECONDS = 3600;
    public static final String GET_TOKEN_ENDPOINT = "%s/api/Auth/v1/Key";

    // METADATA
    private static final String MODULES_URL = "%s/api/objects/v1/_describe";
    private static final String FIELDS_URL = "%s/api/objects/v1/%s/_describe";
    private static final String IMPARTNER_SYNAPSE_SETUP_ARTICLE = "/13559907909524-Impartner-Synapse-Setup";
    // TODO: Company ???


    public static final List<String> NOT_SUPPORTED = List.of(
            "MDFClaimRewardHistory",
            "BPPartnerPlanCustomMetric",
            "BPTemplateCustomMetric");

    public static final List<String> SUPPORTED_OBJECTS_WITHOUT_WATERMARK = List.of(

            "AdministratorRole"
            , "ApprovalStatus"
            ,"ApplicantApprovalStatus"
            ,"ConfigurationBundleImportError"
            ,"CrmEnvironment"
            ,"ReferralSettings"
            ,"DealApprovalStatus"
            ,"DealStage"
            ,"EventRegistrationApprovalStatus"
            ,"LeadApprovalStatus"
            ,"LeadStatus"
            ,"OpportunityProduct"
            ,"PartnerLevel"
            ,"PasswordRecoveryQuestion"
            ,"ProductCategory"
            ,"QuestionType"
            ,"SaleProduct"
            ,"SupportedLocale"
            ,"TierGroupPartnerLevel"
            ,"TierGroupRegion"
    );

    public static final List<String> SUPPORTED_OBJECTS_WITH_WATERMARK = List.of(
            "Account", "User", "Lead", "Deal", "Customer", "Contact", "Opportunity",

            "AccountAttachment",
            "AccountEngagement",
            "AccountFieldSegment",
            "AccountFilterSegment",
            "AccountFilterSegmentGroup",
            "AccountListing",
            "AccountListingAttachment",
            "AccountListingCategory",
            "AccountSnapshot",
            "AccountTierHistory",
            "ActiveTierRequirements",
            "Administrator",
            "Applicant",
            "ApplicantAttachment",
            "Asset",
            "AssetCollection",
            "AssetEngagement",
            "AssetPlaybook",
            "AssetShare",
            "Benefit",
            "Calendar",
            "CalendarEvent",
            "CertificationCompletion",
            "ChronoTierGoal",
            "CMSLayout",
            "CMSLayoutColumn",
            "CMSLayoutRow",
            "CMSPage",
            "CMSPageEngagement",
            "CobrandedDocument",
            "CobrandTemplate",
            "CobrandTemplateAreaContent",
            "ContactAttachment",
            "ContentWidget",
            "Country",
            "CourseCompletion",
            "CustomerAttachment",
            "CustomObjectType",
            "DealAttachment",
            "DealContact",
            "EventRegistration",
            "FileBlob",
            "FileBlobTemp",
            "FileSecuritySetting",
            "ImageCobrandArea",
            "ImportTemplate",
            "InstalledLocale",
            "LeadAttachment",
            "Location",
            "MDFClaimEngagement",
            "MDFRequestEngagement",
            "Menu",
            "MenuItem",
            "MFBDashboard",
            "MFBForm",
            "MFBModule",
            "NumericTierGoal",
            "OpportunitySalesStage",
            "PartnerTransaction",
            "Product",
            "PushMessage",
            "Question",
            "QuestionCategory",
            "QuestionOption",
            "RebateType",
            "Region",
            "Sale",
            "Solution",
            "SolutionAttachment",
            "SolutionEngagement",
            "SolutionListing",
            "SolutionListingAttachment",
            "SolutionListingCategory",
            "SolutionListingEngagement",
            "StandardFieldPicklistOption",
            "State",
            "Tag",
            "TagLink",
            "TextCobrandArea",
            "Tier",
            "TierGroup",
            "TranslationItem",
            "TranslationRequest",
            "UserAttachment",
            "UserFieldSegment",
            "UserFilterSegment",
            "UserFilterSegmentGroup",
            "UserJourneyActivity",
            "UserJourneyPhase",
            "VendorPartnerNotes",
            "WorkflowProcess"
            );

    // RECORDS GET
    private static final int API_MAX_PAGESIZE = 10;
    private static final String GET_BY_WATERMARK_URL =
            "%s/api/objects/v1/%s?fields=%s&filter=updated>'%s' and updated<='%s' %s&orderBy=updated asc&skip=%s&take=%s";

    private static final String GET_URL =
            "%s/api/objects/v1/%s?fields=%s%s&orderBy=id asc&skip=%s&take=%s";

    // CRUD APIs
    private static final String CRUD_ID_URL = "%s/api/objects/v1/%s/%s";
    private static final String CRUD_URL = "%s/api/objects/v1/%s";

    // attribute level constants
    public static final String ID_FIELD = "Id";

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd(), ConnectorHelper.getApiKey());
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19202134443924";
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField timeZone = new AuthField();
        timeZone.setDataType("text");
        timeZone.setName(TIME_ZONE_ID);
        timeZone.setLabel(i18n("impartner_timezone_label"));
        timeZone.setHelpSummary(i18n("impartner_timezone_help"));
        return List.of(ConnectorHelper.getEndpointField(), timeZone, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public boolean validate(ConnectorInfo connector) {
        String zoneId = connector.getMetaConfig().getOrDefault(TIME_ZONE_ID, "").toString();
        try {
            ZoneId z = ZoneId.of(zoneId);
        } catch (DateTimeException e) {
            throw new RuntimeException(i18n("impartner_invalid_timezone_id"));
        }
        return true;
    }

    @Override
    public String getCategory() {
        return "PRM";
    }
    
    @Override
    public String getName() {
        return Constants.IMPARTNER;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/impartner.svg")
                .setDisplayName("Impartner")
                .setBackgroundColor("#F9F9F9")
                .setHelpUrl(helpArticlesBaseUrl + IMPARTNER_SYNAPSE_SETUP_ARTICLE);
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        throw new RuntimeException("OAuth Implicit Flow not supported by Impartner");
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        return ConnectorHelper.backoffAndThrowNonRetriableException(() -> {
            try {
                String prmKey = (new ImpartnerRestClient(getSingleJsonConfig(), mapper)).getAccessToken(connector);
                AuthConfig authConfig = connector.getAuthConfig().clone();
                authConfig.setAccessToken(prmKey);
                authConfig.setRefreshToken(prmKey);
                authConfig.setExpiresIn(String.valueOf(TOKEN_EXPIRY_SECONDS));
                authConfig.setLastRefreshed(Instant.now());
                return authConfig;
            } catch (NonRetriableException ex) {
                // Impartner synapse throws 401 Unauthorized Invalid username/password occasionally, we need to retry for those special cases.
                if (ex.getErrorCode().equals(ErrorCodes.ACCESS_DENIED.toString())) {
                    log.error("Encountered 401. Original Exception {} ", ex.getMessage(), ex);
                    throw new RetriableException(ErrorCodes.ACCESS_DENIED, ex.getMessage(), ex.getStatusCode().toString());
                } else {
                    throw ex;
                }
            }
        }, 1000, 2000, 5, Optional.empty());
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        throw new RuntimeException("OAuth Implicit Flow not supported by Impartner");
    }

    private JsonParserConfig getSingleJsonConfig() {
        return new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
    }

    protected ImpartnerRestClient getClient(AuthConfig config) {
        config.addHeader("Authorization", String.format(ACCESS_KEY_HEADER, config.getAccessToken()));
        return new ImpartnerRestClient(getSingleJsonConfig(), mapper);
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            config.setAuthConfig(refreshToken(config));
            response.setAuthConfig(config.getAuthConfig());
            ResponseEntity<String> data = getClient(config.getAuthConfig())
                .getResponse(String.format(MODULES_URL, getHost(config)), config.getAuthConfig());
            log.debug("Data received " + data);
        } catch (Exception e) {
            log.error("Impartner testConnection failed due to " + e.getMessage(), e);
            handleAuthenticationErrorMessage(response, e);
        }
        return response;
    }

	@Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        List<EntitySchema> entitySchemas = describeAll(new DescribeAllRequest(request.getConnector(), List.of(request.getEntity())));
        if (entitySchemas.isEmpty()) return Optional.empty();
        return Optional.of(entitySchemas.get(0));
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        ResponseEntity<String> data = getClient(request.getConnector().getAuthConfig())
            .getResponse(String.format(MODULES_URL, getHost(request.getConnector())), request.getConnector().getAuthConfig());
        return toEntitySchemas(request, data);
    }

    private String getPredicateKey(String entityName) {
        return entityName.toLowerCase()+"_"+Constants.SYNCARI_SRC_PREDICATE;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {


        String zoneIdStr = request.getConnector().getMetaConfig().getOrDefault(TIME_ZONE_ID, "").toString();
        ZoneId userZoneId = StringUtils.isEmpty(zoneIdStr) ? ZoneId.systemDefault() : ZoneId.of(zoneIdStr);
        Function3<WatermarkInfo, Integer, Long, DataWithOffset> generator = (wm, pageSize, offset) -> {
            String queryPredicate = request.getSourceParams().getOrDefault(getPredicateKey(request.getEntityName()), "").toString();

            String start = dateUtil.format(request.getWatermark().getStart(), dateFormat, userZoneId);
            String end = dateUtil.format(request.getWatermark().getEnd(), dateFormat, userZoneId);
            List<AttributeSchema> attributesToQuery = request.getEntitySchemaWithMappedFields().getAttributes();
            String fields = String.join(",", attributesToQuery.stream().filter(a -> !a.isIdField() && a.getStatus() == Status.ACTIVE &&
                a.getDataType() != "polymorphicreference")
                .map(a -> a.getApiName()).collect(Collectors.toList())) + "," + request.getEntitySchema().getIdField().getApiName();
            String url = null;
            String queryPredicateStr = "";
            if(SUPPORTED_OBJECTS_WITHOUT_WATERMARK.contains(request.getEntityName())) {
                if(!queryPredicate.isBlank()) {
                    queryPredicateStr = "&filter=" + queryPredicate;
                }
                url = String.format(GET_URL, getHost(request.getConnector()), request.getEntityName(), fields, queryPredicateStr, offset, pageSize);
            } else {
                if(!queryPredicate.isBlank()) {
                    queryPredicateStr = " AND " + queryPredicate;
                }
                url = String.format(GET_BY_WATERMARK_URL, getHost(request.getConnector()), request.getEntityName(), fields, start, end, queryPredicateStr, offset, pageSize);
            }


            return get(url, offset, request, false);
        };

        int pgSize = (request.getPageSize() <= 0) ? API_MAX_PAGESIZE : request.getPageSize();

        DefaultDataOffsetIterator iterator = new DefaultDataOffsetIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pgSize, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private DataWithOffset get(String url, long offset, SyncRequest request, boolean isSingleObject) {
        return getClient(request.getConnector().getAuthConfig()).getData(url, offset, request, isSingleObject);
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        List<EntityData> data = new ArrayList<>();
        request.getIds().forEach(id -> {
            try {
                String url = String.format(CRUD_ID_URL, getHost(request.getConnector()), request.getEntityName(), id);
                data.addAll(get(url, 0l, request, true).getData());
            } catch (NonRetriableException e) {
                if (e.getMessage().contains("404 NOT_FOUND") || e.getMessage().contains("Record not found:")) {
                    log.error("Record with id {} not found", id, e);
                } else {
                    throw e;
                }
            }
        });
        return data;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        String url = String.format(CRUD_URL, getHost(request.getConnector()), request.getEntityName().toLowerCase());
        return getClient(request.getConnector().getAuthConfig()).upsertRecords(url, HttpMethod.PUT, transformData(request));
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        String url = String.format(CRUD_URL, getHost(request.getConnector()), request.getEntityName().toLowerCase());
        return getClient(request.getConnector().getAuthConfig()).upsertRecords(url, HttpMethod.PATCH, transformData(request));
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return getClient(request.getConnector().getAuthConfig()).deleteRecords(CRUD_ID_URL, request);
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException(this.getUIMetadata().getDisplayName() + " does not support delete field");
    }
    
    SyncariEntityDataRestClient getClient(JsonParserConfig config) {
        return new SyncariEntityDataRestClient(config,mapper);
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in " + this.getUIMetadata().getDisplayName()  + " yet");
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }
    
    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    private Object getHost(ConnectorInfo config) {
		return StringUtils.isBlank(config.getEndpoint()) ? API_HOST_URL : config.getEndpoint();
	}
    
    private List<EntitySchema> toEntitySchemas(DescribeAllRequest request, ResponseEntity<String> resp) {
        List<EntitySchema> entitySchemas = new ArrayList<>();
        if (resp.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException(resp.getBody());
        }
        Map respMap;
        try {
            respMap = mapper.readValue(resp.getBody(), Map.class);
        } catch (JsonProcessingException e1) {
            throw new RuntimeException("Failed to read entities.", e1);
        }
        List<Map<String, Object>> rawSchemas = (ArrayList<Map<String, Object>>) respMap.get("data");
        rawSchemas.forEach(x -> {
            String objectName = x.get("name").toString();
            // Some Objects are not supported.
            if (NOT_SUPPORTED.contains(objectName))
                return;
            if (CollectionUtils.isNotEmpty(request.getEntities()) && !request.getEntities().contains(objectName)) return;

            //Map<String, Object> entitySchemaWithFields = datas.get(0);
            EntitySchema entitySchema = toEntitySchema(x);

            String fieldsURL = String.format(FIELDS_URL, getHost(request.getConnector()), objectName);
            ResponseEntity<String> fieldResp = getClient(request.getConnector().getAuthConfig())
                .getResponse(fieldsURL, request.getConnector().getAuthConfig());
            if (fieldResp.getStatusCode() != HttpStatus.OK) {
                log.error("fieldResp received " + fieldResp);
                throw new RuntimeException(String.format("Failed to get fields for entity %s due to %s", objectName, fieldResp));
            }

            Map fieldRespMap;
            try {
                fieldRespMap = mapper.readValue(fieldResp.getBody(), Map.class);
            } catch (JsonProcessingException e1) {
                throw new RuntimeException("Failed to read detailed object response.", e1);
            }
            List<Map<String, Object>> datas = (ArrayList<Map<String, Object>>) fieldRespMap.get("data");
            log.debug("raw:\n " +datas);
            datas.forEach(y -> {
                // We dont support child relationships yet.
                if (y.containsKey("fieldType") && y.get("fieldType").toString().equalsIgnoreCase("RelatedList")) return;
                entitySchema.addField(toAttributeSchema(y, objectName));
            });
            entitySchemas.add(entitySchema);
        });
        return entitySchemas;
    }

    private EntitySchema toEntitySchema(Map<String, Object> schemaRawValue) {
        return new EntitySchema(schemaRawValue.get("name").toString())
            .setDisplayName(schemaRawValue.get("display").toString())
            .setPluralName(schemaRawValue.get("display").toString())
            .setReadOnly(
                !((Boolean) schemaRawValue.getOrDefault("creatable", false) && (Boolean) schemaRawValue.getOrDefault("updatable", false)));
    }

    private AttributeSchema toAttributeSchema(Map<String, Object> fieldRawValue, String objectName) {
        log.debug("fieldRawValue {} ", fieldRawValue);
        AttributeSchema field = new AttributeSchema(fieldRawValue.get("name").toString(), fieldRawValue.get("dataType").toString().toLowerCase())
            .setDisplayName(fieldRawValue.get("display").toString());
        
        field.setNillable(!(Boolean) fieldRawValue.get("isRequired"));
        field.setInitializable(fieldRawValue.containsKey("isReadOnly") ? !(Boolean) fieldRawValue.get("isReadOnly") : true);
        field.setUpdateable(fieldRawValue.containsKey("isReadOnly") ? !(Boolean) fieldRawValue.get("isReadOnly") : true);

        switch (field.getApiName()) {
            case "Updated":
                field.setWatermarkField(true);
                field.setUpdatedAtField(true);
                field.setSystem(true);
                field.setNillable(false);
                break;
            case "Created":
                field.setCreatedAtField(true);
                field.setSystem(true);
                break;
            case ID_FIELD:
                field.setIdField(true);
                field.setSystem(true);
                field.setUpdateable(false);
                field.setUnique(true);
                field.setNillable(false);

                if(SUPPORTED_OBJECTS_WITHOUT_WATERMARK.contains(objectName)){
                    field.setWatermarkField(true);
                }
                break;
            case "CreatedBy":
            case "UpdatedBy":
                field.setSystem(true);
                break;
            default:
                break;
        }

        // The Decimal type comes as "Decimal(9,6)", yes, really!
        if (field.getDataType().toLowerCase().startsWith("decimal(")) {
            String[] ps = field.getDataType().substring(8, field.getDataType().length() - 1).split(",");
            field.setPrecision(Integer.parseInt(ps[0].trim()));
            field.setScale(Integer.parseInt(ps[1].trim()));
            field.setLength(Integer.parseInt(ps[0].trim()));
            field.setDataType("number");
        }

        if (fieldRawValue.containsKey("fieldType") && !StringUtils.isEmpty("fieldType") && 
            "LongText".equalsIgnoreCase(fieldRawValue.get("fieldType").toString())) {
            field.setDataType("textarea");
        }

        if (fieldRawValue.containsKey("fieldType") && !StringUtils.isEmpty("fieldType") && 
            "LongInteger".equalsIgnoreCase(fieldRawValue.get("fieldType").toString())) {
            field.setDataType("long");
        }

        if (fieldRawValue.containsKey("fieldType") && !StringUtils.isEmpty("fieldType") &&
            (fieldRawValue.get("fieldType").toString().equalsIgnoreCase("Fk") ||
                fieldRawValue.get("fieldType").toString().equalsIgnoreCase("RelatedList"))) {
            field.setDataType(fieldRawValue.get("fieldType").toString().equalsIgnoreCase("RelatedList") ? "polymorphicreference" : "reference");
            field.setReferenceTo(fieldRawValue.get("fieldType").toString().equalsIgnoreCase("RelatedList") ?
                fieldRawValue.get("relatedListFieldType").toString() : 
                fieldRawValue.get("fkFieldType").toString());
            field.setReferenceTargetField(ID_FIELD);
            // We do not have a way to assign multiple references.
            if ("polymorphicreference".equalsIgnoreCase(field.getDataType())) {
                field.setInitializable(false);
                field.setUpdateable(false);
            }
        }
        if (StringUtils.isNotEmpty(field.getApiName()) && field.getApiName().toLowerCase().endsWith("__cf")){
            field.setCustom(true);
        }

        if (!"boolean".equalsIgnoreCase(field.getDataType().toLowerCase()) && 
            fieldRawValue.containsKey("picklistType") && !StringUtils.isEmpty("picklistType") &&
            !fieldRawValue.get("picklistType").toString().equalsIgnoreCase("None")) {
            List<String> pickListValues = new ArrayList();
            Map<String, Object> plValues = (Map) fieldRawValue.get("values");
            if (MapUtils.isNotEmpty(plValues)) {
                for (Map.Entry<String, Object> val : plValues.entrySet()) {
                    Map<String, Object> plName = (Map) val.getValue();
                    pickListValues.add(plName.get("name").toString());
                }
            }
            field.setDataType("picklist");
            field.setPicklistValues(pickListValues);
            if ("Multiple".equalsIgnoreCase(fieldRawValue.get("picklistType").toString())) field.setMultiValueField(true);
            String subDataType = fieldRawValue.get("dataType").toString().toLowerCase();
            // Remove the extra suffix " list" if this is a multivalue field
            if (StringUtils.isNotBlank(subDataType)) {
                if (field.isMultiValueField() || subDataType.equalsIgnoreCase("Long Integer")){
                    subDataType = subDataType.split(" ")[0];
                }
                if (subDataType.startsWith("decimal(")) {
                    subDataType = "number";
                }
            }
            if (StringUtils.isNotBlank(subDataType)){
                field.setSubDataType(subDataType);
            }
        }

        return field;
    }

    protected SyncRequest transformData(SyncRequest syncRequest) {

        var schema = syncRequest.getEntitySchema();
        var data = syncRequest.getData().get(syncRequest.getConnector().getId());
        data.forEach(d -> d.getValues().forEach((k, v) -> {
            var field = schema.getField(k);
            field.ifPresent( f -> {
                if(f.getDataType().equals("date") && v instanceof Date) {
                    LocalDate locateDate = ((Date)v).toInstant().atZone(ZoneId.of("UTC")).toLocalDate();
                    d.getValues().put(k, locateDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                }

                // TODO: This code needs to be moved to platform eventually
                if ("picklist".equals(f.getDataType()) && "string".equals(f.getSubDataType()) && v != null) {
                    if (v instanceof List){
                        d.getValues().put(k, List.class.cast(v).stream()
                                .filter(Objects::nonNull)
                                .map(value -> value.toString())
                                .collect(Collectors.toList()));
                    } else {
                        d.getValues().put(k, v.toString());
                    }
                }
            });
        }));
        return syncRequest;
    }
}
