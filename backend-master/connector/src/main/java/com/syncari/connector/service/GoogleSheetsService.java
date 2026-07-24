package com.syncari.connector.service;

import static com.syncari.utils.I18n.i18n;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.syncari.connector.Capability;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.rest.SyncariOauthRestClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.DefaultAuthTokenHandler;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.data.iterator.GoogleFileIterator;
import com.syncari.connector.data.iterator.GoogleSheetsIterator;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.googlesheets.SheetInfo;
import com.syncari.utils.DateUtil;
import com.syncari.utils.KeyValue;
import com.syncari.utils.TextUtil;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(Constants.GOOGLESHEETS)
public class GoogleSheetsService implements OauthAuthenticationService, CommonDataService, MetadataService, SynapseInfoService {
    public static final String SYNCARI_LAST_MODIFIED = "syncariLastModified";
    private static final int PAGE_SIZE = 1000;
    private static final String PROPERTIES = "properties";
    private static final String SPREADSHEET = "spreadsheet";
    private static final String SHEETS = "sheets";
    private static final String FILES = "files";
    private static final String ID = "id";
    private static final String FOLDER_ID = "folderId";
    private static final String TITLE = "title";
    private static final String NAME = "name";
    private static final String EFFECTIVE_VALUE = "effectiveValue";
    private static final String CELL_FORMAT = "userEnteredFormat";
    private static final String QUOTA_USER = "quotaUser=";
    private static final String LOOKUP_SHEET = "SYNCARI_LOOKUP_SHEET";
    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;
    @Autowired
    TextUtil textUtil;
    private static final String BRAND_IMG = "/assets/icons/logos/btn_google_signin_dark_normal_web.png";
    private static final String SHEETS_V4 = "https://sheets.googleapis.com/v4";
    private static final String DRIVE_V3 = "https://www.googleapis.com/drive/v3";
    private static final String OAUTH_HOST = "https://accounts.google.com";
    private static final String OAUTH_URL = "https://www.googleapis.com/oauth2/v4/token";
    private static final String GET_FOLDERS = DRIVE_V3 + "/files?q='%s'+in+parents+and+mimeType='application/vnd.google-apps.folder'+and+trashed=false";
    private static final String GET_FOLDER_BY_ID = DRIVE_V3 + "/files/%s";
    private static final String GET_FILES = DRIVE_V3 + "/files?q='%s'+in+parents+and+mimeType='application/vnd.google-apps.spreadsheet'+and+trashed=false&fields=files(id,kind,name,mimeType,createdTime,modifiedTime)";
    private static final String DELETE_FILE = DRIVE_V3 + "/files/%s";
    private static final String CREATE_FILE = DRIVE_V3 + "/files";
    private static final String GET_BY_WATERMARK = DRIVE_V3 + "/files?q='%s'+in+parents+and+mimeType='application/vnd.google-apps.spreadsheet'+and+trashed=false+and+modifiedTime>'%s'&fields=files(id,kind,name,mimeType,createdTime,modifiedTime)";
    public static final String GET_SHEET_VALUES_BY_RANGE = SHEETS_V4 + "/spreadsheets/%s/values:batchGet?majorDimension=ROWS%s&quotaUser=%s";
    private static final String GET_SHEET_META = SHEETS_V4 + "/spreadsheets/%s?fields=properties.title,sheets(properties,data.rowData.values(effectiveValue),data.rowData.values(userEnteredFormat))&ranges=1:1";
    private static final String GET_SHEET_BY_ID = SHEETS_V4 + "/spreadsheets/%s";
    private static final String ADD_SHEET = SHEETS_V4 + "/spreadsheets/%s:batchUpdate";
    private static final String UPDATE_SHEET = SHEETS_V4 + "/spreadsheets/%s/values:batchUpdate?includeValuesInResponse=true&responseValueRenderOption=UNFORMATTED_VALUE&valueInputOption=USER_ENTERED";
    private static final String WRITE_ROWS = SHEETS_V4 + "/spreadsheets/%s/values/%s:append?valueInputOption=USER_ENTERED";
    private static final String DELETE_ROWS = SHEETS_V4 + "/spreadsheets/%s:batchUpdate";
    private static final String MODIFY_ROWS = SHEETS_V4 + "/spreadsheets/%s/values:batchUpdate";
    public static final char[] COLUMNS= new char[] {'Z','A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y'};

    private final static int CLOCK_SKEW_TOLERANCE_SECS = 5 * 60;

    private static final long SHEETS_EPOCH_DIFFERENCE = 2209161600000L;

    public static List<ErrorCodes> SHEETS_TOKEN_REFRESH_ERROR_CODES = List.of(ErrorCodes.ACCESS_DENIED);

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        var oneClickOauth = new AuthMetadata(AuthType.OneClickOAuth, Lists.newArrayList(), "One Click OAuth", "This type of authentication uses Syncari's Oauth client app");
        oneClickOauth.setOptions(KeyValue.of("oneClickOauth", true));
        oneClickOauth.setBrandImagePath(BRAND_IMG);
        var oAuth = new AuthMetadata(AuthType.Oauth, List.of(ConnectorHelper.getClientIdField(), ConnectorHelper.getClientSecretField()), "OAuth", "");
        oAuth.setBrandImagePath(BRAND_IMG);
        return List.of(oAuth, oneClickOauth);

    }
    
    @Override
    public List<AuthField> getConfigureFields() {
        AuthField folderId = new AuthField();
        folderId.setDataType("text");
        folderId.setName("folderId");
        folderId.setLabel("Folder Id");
        folderId.setHelpSummary("The folder id for Syncari folder");
        return List.of(folderId, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19198672348820";
    }

    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<Capability>();
        capabilities.add(Capability.schemaEditInSyncari);
        capabilities.add(Capability.userEditableId);
        capabilities.add(Capability.userEditableWm);
        capabilities.add(Capability.create);
        capabilities.add(Capability.update);
        capabilities.add(Capability.delete);
        return capabilities;
    }

    @Override
    public String getCategory() {
        return "Productivity";
    }
    
    @Override
    public String getName() {
        return Constants.GOOGLESHEETS;
    }

    @Override
    public int clockSkewTolerance(ConnectorInfo connectorInfo) { return CLOCK_SKEW_TOLERANCE_SECS; }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/googlesheets.svg")
                .setDisplayName("Google Sheets")
                .setBackgroundColor("#F4FFFA")
                .setHelpUrl(helpArticlesBaseUrl + "/360052150852-Google-Sheets-Setup");
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        SyncariOauthRestClient restClient = new SyncariOauthRestClient(getSingleJsonConfig(""), mapper);
        return ConnectorHelper.withHttpErrorHandling(() -> {
            String folderId = request.getEntityName();
            WatermarkInfo watermark = request.getWatermark();
            List<?> spreadSheets = getSpreadsheetsByModifiedTime(folderId, request.getWatermark().getStart(),
                    request.getConnector(), restClient);
            Optional<SheetInfo> spreadSheet = Optional.empty();
            if(!spreadSheets.isEmpty()) {
                Map<?, ?> sheetProps = (Map<?, ?>) spreadSheets.get(0);
                spreadSheet = Optional.of(toSheetInfo(sheetProps));
            } else {
                log.debug("Got empty spreadSheets from getSpreadsheetsByModifiedTime for {}", folderId);
            }
            return getIterator(request, restClient, watermark, spreadSheet);
        });
    }


    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        return describe(request, false, true);
    }

    private Optional<EntitySchema> describe(DescribeRequest request, boolean includePartitions, boolean includeMissingIdField) {
        SyncariOauthRestClient restClient = new SyncariOauthRestClient(getSingleJsonConfig(""), mapper);
        return ConnectorHelper.withHttpErrorHandling(() -> {
            String rootFolderId = request.getEntity();
            EntitySchema entityDefinition = new EntitySchema();
            try {
                log.debug("rootFolderId: {}", rootFolderId);
                Map entityFolder = getFolderById(rootFolderId, request.getConnector(), restClient);
                log.debug("entityFolder: {}", entityFolder);
                entityDefinition.setApiName(entityFolder.get(ID).toString());
                entityDefinition.setDisplayName(entityFolder.get(NAME).toString());
                List spreadSheets = getSpreadsheets(entityFolder.get(ID).toString(), request.getConnector(), restClient);
                log.debug("spreadSheets count: {}", spreadSheets.size());
                if (spreadSheets.size() > 0) {
                    List relevantSheets = includePartitions ? spreadSheets : List.of(spreadSheets.get(0));
                    for (int i = 0; i < relevantSheets.size(); i++) {
                        Map sheetProps = (Map) relevantSheets.get(i);
                        SheetInfo sheetInfo = toSheetInfo(sheetProps);
                        String spreadSheetId = sheetInfo.getSpreadsheetId();
                        String spreadSheetName =sheetInfo.getSpreadsheetName();
                        Map sheet = getSheet(spreadSheetId, request.getConnector(), restClient, Optional.empty());
                        String sheetName = ((Map)sheet.get(PROPERTIES)).get(TITLE).toString();
                        sheetInfo.setSheetName(sheetName);
                        String rowCount = ((Map)((Map)sheet.get(PROPERTIES)).get("gridProperties")).get("rowCount").toString();
                        String colCount = ((Map)((Map)sheet.get(PROPERTIES)).get("gridProperties")).get("columnCount").toString();
                        String partitionId = generatePartitionId(rootFolderId, entityFolder.get(ID).toString(), spreadSheetId);
                        entityDefinition.addPartition(new Partition(partitionId, spreadSheetName, Instant.now(), Long.valueOf(rowCount), Long.valueOf(colCount)));
                        entityDefinition.addProperty(spreadSheetId,sheetName);
                        entityDefinition.addProperty("sheetInfo",sheetInfo);
                        if(entityDefinition.getAttributes().isEmpty()) {
                            entityDefinition.setAttributes(getAttributes(spreadSheetId, sheetName, request.getConnector(), restClient, includeMissingIdField));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to describe for folder {} ", rootFolderId);
                log.error("Error {} ", e.getMessage(),  e);
                throw e;
            }
            log.debug("Successfully completed google sheet describe for {} with attrs {}", request.getEntity(), entityDefinition.getAttributes().size());
            return Optional.of(entityDefinition);
        });
    }

    private SheetInfo toSheetInfo(Map sheetProps) {
        return new SheetInfo(sheetProps.get(ID).toString(), sheetProps.get(NAME).toString(), null, null,
                ZonedDateTime.parse(sheetProps.get("modifiedTime").toString()), ZonedDateTime.parse(sheetProps.get("createdTime").toString()));
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        return ConnectorHelper.withHttpErrorHandling(() -> {
            List<EntitySchema> results = new ArrayList<>();
            String rootFolderId = getRootFolderId(request.getConnector());
            List folders = getFolders(rootFolderId, request.getConnector());
            for (int i = 0; i < folders.size(); ++i) {
                String folderId = ((Map) folders.get(i)).get(ID).toString();
                Optional<EntitySchema> entity = describe(new DescribeRequest(request.getConnector(), folderId));
                entity.ifPresent(e -> results.add(e));
            }
            log.debug("Successfully completed google sheet describeall");
            return results;
        });
    }

    
    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "/o/oauth2/v2/auth?client_id={{client_id}}&redirect_uri={{redirect_uri}}&state={{state}}&response_type=code&access_type=offline&prompt=consent&scope=https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive";
    }

    @Override
    public String getAuthHost(AuthConfig config) {
        return OAUTH_HOST;
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.REFRESH_TOKEN,
                DefaultAuthTokenHandler.REFRESH_TOKEN, config.getRefreshToken(), DefaultAuthTokenHandler.CLIENT_ID,
                config.getClientId(), DefaultAuthTokenHandler.CLIENT_SECRET, config.getClientSecret(),
                DefaultAuthTokenHandler.REDIRECT_URI, config.getRedirectUri(), "prompt", "consent", "access_type",
                "offline");

        return tokenHandler.refreshToken(config, OAUTH_URL, map);
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, "authorization_code",
                DefaultAuthTokenHandler.CODE, oAuthRequest.getCode(), DefaultAuthTokenHandler.CLIENT_ID,
                oAuthRequest.getConfig().getClientId(), DefaultAuthTokenHandler.CLIENT_SECRET,
                oAuthRequest.getConfig().getClientSecret(), DefaultAuthTokenHandler.REDIRECT_URI,
                oAuthRequest.getRedirectUri());

        return tokenHandler.getAccessToken(OAUTH_URL, map);
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();
        try {
            getRootFolderId(config);
            getFolders(config.getMetaConfig().get(FOLDER_ID).toString(), config);
            List<EntitySchema> describeAll = describeAll(new DescribeAllRequest(config, List.of()));
            if(describeAll.isEmpty()) {
                result.setMessage("Folder does not have any entities");
                result.setCode(HttpStatus.BAD_REQUEST.name());
            }
        } catch (Exception e) {
            result.setMessage(e.getMessage());
            result.setCode(HttpStatus.UNAUTHORIZED.name());
        }
        return result;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        DescribeRequest describeRequest = new DescribeRequest(request.getConnector(), request.getEntityName());
        Optional<EntitySchema> entity = describe(describeRequest);
        if(entity.isEmpty()) {
            throw new RuntimeException(String.format("Google drive folder with id %s not found", request.getEntityName()));
        }
        if(entity.get().hasField(request.getSchema().getApiName())) {
            return entity.get().getField(request.getSchema().getApiName()).get();
        }
        // TODO add new col header to sheet
        return request.getSchema();
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("GS does not support delete field");
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        throw new NotSupportedException("GS does not support getbyids");
    }

    private List<String> getHeaderColumns(List<AttributeSchema> attributes){
        List<String> columns = new ArrayList<>();
        int currIndex = 1;
        for (AttributeSchema attr:attributes.stream().sorted(Comparator.comparing(AttributeSchema::getIndex)).collect(Collectors.toList())){
            while (currIndex < attr.getIndex()){
                columns.add("");
                currIndex++;
            }
            columns.add(attr.getApiName());
            currIndex++;
        }
        return columns;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        List<EntityData> entities = request.getData().get(request.getConnector().getId());
        if(entities == null || entities.isEmpty()) return response;
        SyncariOauthRestClient restClient = new SyncariOauthRestClient(getSingleJsonConfig(""), mapper);
        DescribeRequest describeRequest = new DescribeRequest(request.getConnector(), request.getEntityName());
        // Do not include Id and watermarkfield if its not in the schema already. Id field is being added explicitly
        EntitySchema entity = describe(describeRequest, false, false).orElseThrow(() -> new RuntimeException(
                String.format("Google drive folder with id %s not found", request.getEntityName())));
        List<String> attributes = getHeaderColumns(entity.getAttributes());
        if(attributes.isEmpty()) {
           entities.stream().forEach(e -> {
               attributes.addAll(e.getValues().keySet());
           });
        }
        try {
            // Create records into existing file which has less than 10k records, if none create a new file
            CreateRequest create = new CreateRequest();
            // Create new spreadsheet and capture first row as header
            Partition partition = entity.getPartitions().stream().findFirst()
                    .orElseGet(() -> createPartition(request.getEntitySchema(), request.getConnector(), restClient, attributes));
            String spreadSheetId = getSpreadSheetId(partition);
            String sheetName = Optional.of(entity).flatMap(e -> e.getProperty(spreadSheetId)).orElse("Sheet1").toString();
            String columnAlphabet = ConnectorHelper.getColumnAlphabet(attributes.size());

            if(!entity.hasField(Constants.SYNCARI_ID)
                    && (!request.getEntitySchema().hasIdField() || request.getEntitySchema().getIdField().getApiName().equalsIgnoreCase(Constants.SYNCARI_ID))) {
                attributes.add(Constants.SYNCARI_ID);
                columnAlphabet = ConnectorHelper.getColumnAlphabet(attributes.size());
                // append SYNCARI_RECORD_ID to header
                String range = String.format("%s!%s1:%s1", sheetName,columnAlphabet, columnAlphabet);
                String url = String.format(decorate(WRITE_ROWS, request.getConnector()), spreadSheetId, range);
                CreateRequest create1 = new CreateRequest().setRange(range);
                create1.getValues().add(List.of(Constants.SYNCARI_ID));
                restClient.postRaw(url, mapper.writeValueAsString(create1), request.getConnector(), getTokenHandler(request.getConnector()), SHEETS_TOKEN_REFRESH_ERROR_CODES);
                entity.addField(new AttributeSchema(Constants.SYNCARI_ID, "string").setIdField(true));
                log.debug("Successfully added SYNCARI_RECORD_ID to sheet {}", url);
            }

            String range = String.format("%s!A1", sheetName);
            String url = String.format(decorate(WRITE_ROWS, request.getConnector()), getSpreadSheetId(partition), range);
            create.setRange(range);
            
            // Since google sheet columns cannot be configured as unique, we need to query the existing syncari ids to see
            // if the row already exists. To do so, we'll create a lookup formula in a new sheet and identify the row index.
            // Skip all the records that already exist in the google sheet. Delete the newly created sheet
            Integer sheetId = addSheet(request, spreadSheetId, LOOKUP_SHEET, restClient);
            List existingIds = getExistingRowIndex(request, entity, spreadSheetId, sheetName, restClient);
            if(sheetId != null) {
                deleteSheet(request, sheetId, spreadSheetId, restClient);
            }
            int p = 0;
            for (EntityData e : entities) {
                List values = attributes.stream()
                        .map(a -> {
                            String dataType = entity.getField(a).map(AttributeSchema::getDataType).orElse("string");
                            return (Constants.SYNCARI_ID.equalsIgnoreCase(a) ? e.getSyncariEntityId() : convertValue(e.getValue(a), dataType));
                        })
                        .collect(Collectors.toList());
                if((existingIds.isEmpty() || existingIds.size() >= p && !(existingIds.get(p) instanceof Integer))) {
                    create.getValues().add(values);
                }
                p++;
            }
            restClient.postRaw(url, mapper.writeValueAsString(create), request.getConnector(), getTokenHandler(request.getConnector()), SHEETS_TOKEN_REFRESH_ERROR_CODES);
            for (int i = 0; i < entities.size(); i++) {
                Result result = new Result(true, getIdValue(request, entities.get(i)), entities.get(i).getSyncariEntityId());
                response.getResults().add(result);
            }
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            response.setSuccess(false);
            response.setErrors(List.of(e.getMessage()));
        }
        return response;
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        List<EntityData> entities = request.getData().get(request.getConnector().getId());
        if(entities == null || entities.isEmpty()) return response;
        SyncariOauthRestClient restClient = new SyncariOauthRestClient(getSingleJsonConfig(""), mapper);
        // Do not include watermarkfield if its not already in the schema as it breaks the attribute index
        Optional<EntitySchema> entity = describe(new DescribeRequest(request.getConnector(), request.getEntityName()), true, true);
        if(entity.isEmpty()) {
            throw new RuntimeException(String.format("Google drive folder with id %s not found", request.getEntityName()));
        }
        List<String> attributes = getHeaderColumns(entity.get().getAttributes());
        log.debug("Attributes {}", attributes);
        Map<String , EntityData> dataMap = entities.stream().collect(Collectors.toMap(e -> e.getId(), e -> e));
        // To update the rows first query by syncariRecordId and compute the index to be updated
        try {
            Optional<SheetInfo> sheetInfo = entity.get().getTypedProperty("sheetInfo");
            entity.get().getPartitions().stream().forEach(p -> {
                String spreadSheetId = getSpreadSheetId(p);
                Optional<String> sheetId = Optional.of(spreadSheetId);
                String sheetName = entity.flatMap(e->sheetId.flatMap(s->e.getProperty(s))).orElse("Sheet1").toString();
                WatermarkInfo wm = new WatermarkInfo(0, 0, false, 2);
                if(request.getWatermark() == null) request.setWatermark(wm);
                EntityDataBatchIterator iterator = getIterator(request, restClient, wm, sheetInfo).getIterator();
                int index = 1;
                // Find the index of all the rows that need to be updated by looking up using syncariRecordId
                EditRequest update = new EditRequest();
                while(iterator.hasNext()) {
                    List<EntityData> nextBatch = iterator.next();
                    for (EntityData entityData : nextBatch) {
                        // Log an error if the syncari_id is set as id field and syncariID is null in the destination
                        // Log only for the first record not to spam the log
                        if (entity.get().hasIdField()
                                && Constants.SYNCARI_ID.equalsIgnoreCase(entity.get().getIdField().getApiName())
                                && index == 1 && StringUtils.isBlank(entityData.getSyncariEntityId()
                        )
                        ){
                            log.warn("Syncari Id header is probably not defined for the entity {}", entity.get().getDisplayName());
                        }
                        index++;
                        if(entityData.getId() == null) {
                            continue;
                        }
                        if(dataMap.keySet().contains(entityData.getId())) {
                            for (Entry<String, Object> values : dataMap.get(entityData.getId()).getValues().entrySet()) {
                                CreateRequest entry = new CreateRequest();
                                // Add +1 here since GS indexes start from 1. -1 means the column wa snot found
                                int indexOf = attributes.indexOf(values.getKey());
                                if(indexOf == -1) continue;
                                entry.range = sheetName+"!"+ConnectorHelper.getColumnAlphabet(indexOf+1) +index;

                                String dataType = entity.get().getField(values.getKey()).map(AttributeSchema::getDataType).orElse("string");
                                entry.values = List.of(List.of(values.getValue() == null ? "" : convertValue(values.getValue(), dataType)));
                                update.data.add(entry);
                            }
                        }
                    }
                }
                
                // Update those ranges
                String url = String.format(decorate(MODIFY_ROWS, request.getConnector()), sheetId.get());
                try {
                    if(!update.data.isEmpty()) {
                        restClient.post(url, mapper.writeValueAsString(update), request.getConnector(), getTokenHandler(request.getConnector()), SHEETS_TOKEN_REFRESH_ERROR_CODES);
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                for (int i = 0; i < entities.size(); i++) {
                    Result result = new Result(true, entities.get(i).getId(), entities.get(i).getSyncariEntityId());
                    response.getResults().add(result);
                }
            });
        } catch (Exception e) {
            log.error("Error in GS update");
            log.error(ExceptionUtils.getStackTrace(e));
            response.setSuccess(false);
            response.getErrors().add((e.getMessage()));
        }
        return response;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        List<EntityData> entities = request.getData().get(request.getConnector().getId());
        if(entities == null || entities.isEmpty()) return response;
        SyncariOauthRestClient restClient = new SyncariOauthRestClient(getSingleJsonConfig(""), mapper);
        Optional<EntitySchema> entity = describe(new DescribeRequest(request.getConnector(), request.getEntityName()));
        if(entity.isEmpty()) {
            throw new RuntimeException(String.format("Google drive folder with id %s not found", request.getEntityName()));
        }
        // To delete the rows first query by syncariRecordId and compute the ranges to be deleted
        try {
            List<String> idsToBeDeleted = entities.stream().map(e -> e.getId()).collect(Collectors.toList());
            Optional<SheetInfo> sheetInfo = entity.get().getTypedProperty("sheetInfo");
            entity.get().getPartitions().stream().forEach(p -> {
                Optional<String> sheetId = Optional.of(getSpreadSheetId(p));
                EntityDataBatchIterator iterator = getIterator(request, restClient, new WatermarkInfo(0, 0, false, 2), sheetInfo).getIterator();
                // google sheet is 0 based index
                int index = 0;
                // Find the index of all the rows that need to be deleted by looking up using syncariRecordId
                DeleteRequest delete = new DeleteRequest();
                while(iterator.hasNext()) {
                    List<EntityData> nextBatch = iterator.next();
                    for (EntityData entityData : nextBatch) {
                        // Log an error if the syncari_id is set as id field and syncariID is null in the destination
                        // Log only for the first record not to spam the log
                        if (entity.get().hasIdField()
                                && Constants.SYNCARI_ID.equalsIgnoreCase(entity.get().getIdField().getApiName())
                                && index == 1 && StringUtils.isBlank(entityData.getSyncariEntityId()
                        )
                        ){
                            log.warn("Syncari Id header is probably not defined for the entity {}", entity.get().getDisplayName());
                        }
                        // skip the first row since its the header
                        index++;
                        if(entityData.getId() == null) {
                            continue;
                        }
                        if(idsToBeDeleted.contains(getIdValue(request, entityData))) {
                            // Compute index of the row in the sheet
                            DeleteEntry entry = new DeleteEntry();
                            entry.deleteDimension.put("range", new Range().setStartIndex(index).setEndIndex(index+1));
                            delete.requests.add(entry);
                        }
                    }
                }
                
                Collections.reverse(delete.requests);
                // Delete those ranges
                String url = String.format(decorate(DELETE_ROWS, request.getConnector()), sheetId.get());
                try {
                    if(!delete.requests.isEmpty()) {
                        restClient.post(url, mapper.writeValueAsString(delete), request.getConnector(), getTokenHandler(request.getConnector()), SHEETS_TOKEN_REFRESH_ERROR_CODES);
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                for (int i = 0; i < entities.size(); i++) {
                    Result result = new Result(true, entities.get(i).getId(), entities.get(i).getSyncariEntityId());
                    response.getResults().add(result);
                }
            });
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            response.setSuccess(false);
            response.setErrors(List.of(e.getMessage()));
        }
        return response;
    }

    private Object convertValue(Object value, String dataType) {
        if (value == null) {
            return value;
        }

        if (List.class.isAssignableFrom(value.getClass())){
            List<String> listValues = (List<String>) ((List)value).stream().map(v->convertValue(v, dataType)).collect(Collectors.toList());
            return String.join(",", listValues);
        }

        switch (dataType) {
            case "date":
                return getDateTimeNumericalValue(((Date)value).getTime());
            case "datetime":
                return getDateTimeNumericalValue(((ZonedDateTime)value).toInstant().toEpochMilli());
            default:
                return value;
        }
    }

    private double getDateTimeNumericalValue(long unixEpoch) {
        long sheetsEpoch = unixEpoch + SHEETS_EPOCH_DIFFERENCE;
        // double value in days since sheet epoch
        return sheetsEpoch / (double) TimeUnit.DAYS.toMillis(1);
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in GS yet");
    }
    
    @Override
    public void deleteObject(DeleteObjectRequest request) {
        SyncariOauthRestClient restClient = new SyncariOauthRestClient(getSingleJsonConfig(""), mapper);
        DescribeRequest describeReq = new DescribeRequest(request.getConnector(), request.getEntityName());
        EntitySchema schema = describe(describeReq).get();
        schema.getPartitions().stream().forEach(p -> {
            String url = String.format(DELETE_FILE, p.getId().split(":")[2]);
            restClient.delete(url, request.getConnector(), getTokenHandler(request.getConnector()), SHEETS_TOKEN_REFRESH_ERROR_CODES);
        });
    }

    private String getRootFolderId(ConnectorInfo config) {
        if (!config.getMetaConfig().containsKey(FOLDER_ID) || config.getMetaConfig().get(FOLDER_ID) == null
                || StringUtils.isBlank(config.getMetaConfig().get(FOLDER_ID).toString())) {
            throw new RuntimeException(i18n("folder_id_required"));
        }
        return config.getMetaConfig().get(FOLDER_ID).toString();
    }

    SyncariOauthRestClient getClient(JsonParserConfig config) {
        return new SyncariOauthRestClient(config, mapper);
    }

    private JsonParserConfig getSingleJsonConfig(String plural) {
        return new JsonParserConfig(null, null, null, StringUtils.capitalize(ID), true, null);
    }

    private String getSpreadSheetId(Partition partition) {
        return partition.getId().split(":")[2];
    }

    private Map<?, ?> getSheet(String spreadSheetId, ConnectorInfo info, SyncariOauthRestClient restClient,
            Optional<String> sheetName) {
        String sheetUrl = String.format(GET_SHEET_BY_ID, spreadSheetId);
        ResponseEntity<String> sheetResponse = restClient.getOauthResponse(sheetUrl, info, getTokenHandler(info), SHEETS_TOKEN_REFRESH_ERROR_CODES);
        ReadContext sheetCtx = JsonPath.parse(sheetResponse.getBody());
        List<?> rows = sheetCtx.read(SHEETS);
        if (rows != null && rows.size() > 0) {
            if(sheetName.isEmpty()) {
                // By default return first sheet
                return (Map) rows.get(0);
            } else {
                for (Object r : rows) {
                    Map row = (Map) r;
                    Map props = (Map) row.get("properties");
                    if(props.get("title").toString().equalsIgnoreCase(sheetName.get())) {
                        return row;
                    }
                }
                return Map.of();
            }
        }
        // This should never happen
        throw new RuntimeException(i18n("sheet_not_found"));
    }

    private String generatePartitionId(String rootFolderId, String entityFolderId, String spreadSheetId) {
        // rootfolder id + entity folder id + spread sheet id
        return rootFolderId + ":" + entityFolderId + ":" + spreadSheetId;
    }

    private List<?> getFolders(String folderId, ConnectorInfo info) {
        SyncariOauthRestClient restClient = new SyncariOauthRestClient(getSingleJsonConfig(""), mapper);
        String folderUrl = String.format(decorate(GET_FOLDERS, info), folderId);
        ResponseEntity<String> responseEntity = restClient.getOauthResponse(folderUrl, info, getTokenHandler(info), SHEETS_TOKEN_REFRESH_ERROR_CODES);
        ReadContext ctx = JsonPath.parse(responseEntity.getBody());
        return filterByMimeType(ctx, FILES, "folder");
    }

    private Map<?, ?> getFolderById(String folderId, ConnectorInfo info, SyncariOauthRestClient restClient) {
        String folderUrl = String.format(decorate(GET_FOLDER_BY_ID, info), folderId);
        ResponseEntity<String> responseEntity = restClient.getOauthResponse(folderUrl, info, getTokenHandler(info), SHEETS_TOKEN_REFRESH_ERROR_CODES);
        return JsonPath.parse(responseEntity.getBody()).json();
    }

    private List<?> getSpreadsheets(String folderId, ConnectorInfo info, SyncariOauthRestClient restClient) {
        String filesUrl = String.format(decorate(GET_FILES, info), folderId);
        ResponseEntity<String> filesResponse = restClient.getOauthResponse(filesUrl, info, getTokenHandler(info), SHEETS_TOKEN_REFRESH_ERROR_CODES);
        ReadContext fileCtx = JsonPath.parse(filesResponse.getBody());
        return filterByMimeType(fileCtx, FILES, SPREADSHEET);
    }

    private List<?> getSpreadsheetsByModifiedTime(String folderId, long lastModified, ConnectorInfo info,
            SyncariOauthRestClient restClient) {
        String filesUrl = String.format(decorate(GET_BY_WATERMARK, info), folderId, Instant.ofEpochMilli(lastModified));
        ResponseEntity<String> filesResponse = restClient.getOauthResponse(filesUrl, info, getTokenHandler(info), SHEETS_TOKEN_REFRESH_ERROR_CODES);
        log.debug(filesResponse.getBody());
        ReadContext fileCtx = JsonPath.parse(filesResponse.getBody());
        return filterByMimeType(fileCtx, FILES, SPREADSHEET);
    }
    
    private List<?> filterByMimeType(ReadContext ctx, String path, String mimeType) {
        String type = "application/vnd.google-apps." + mimeType;
        try {
            List<?> files = ctx.read(path);
            if (files == null || files.isEmpty()) {
                log.debug("got empty files for mimeType {}", mimeType);
                return List.of();
            }
            log.debug("files are : {} ", files);
            return (List<?>) files.stream().filter(s -> type.equalsIgnoreCase(((Map<?, ?>) s).get("mimeType").toString()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to filter by MimeType");
            log.error(e.getMessage(), e);
            return List.of();
        }
    }

    private List<AttributeSchema> getAttributes(String spreadSheetId, String sheetName, ConnectorInfo info,
                                                 SyncariOauthRestClient restClient, boolean includeMissingIdField) {
        List<AttributeSchema> attributes = new ArrayList<>();
        String sheetUrl = String.format(decorate(GET_SHEET_META, info), spreadSheetId, sheetName);
        ResponseEntity<String> sheetResponse = restClient.getOauthResponse(sheetUrl, info, getTokenHandler(info), SHEETS_TOKEN_REFRESH_ERROR_CODES);
        ReadContext sheetCtx = JsonPath.parse(sheetResponse.getBody());
        Map<?, ?> first = sheetCtx.read("sheets[0]");
        List data = (List) first.get("data");
        // Index to track the column number
        int currIndex = 0;
        if(!data.isEmpty()) {
            List rows = (List) ((Map)data.get(0)).get("rowData");
            if(rows == null || rows.isEmpty()) return attributes;
            List values = (List) ((Map)rows.get(0)).get("values");
            if (values != null && values.size() > 0) {
                for(Object r:values){
                    ++currIndex;
                    if(((Map)r).isEmpty()) continue;
                    String name = getName(((Map)r));
                    if(name!=null) {
                        String apiName = textUtil.createApiName(name);
                        AttributeSchema f = new AttributeSchema(apiName, getDatatype(((Map) r)));
                        f.setDisplayName(name);
                        if(apiName.equalsIgnoreCase(Constants.SYNCARI_ID)) {
                            f.setIdField(true);
                        }
                        f.setIndex(currIndex);
                        attributes.add(f);
                    }
                }
            }
        }

        AttributeSchema lastModified = new AttributeSchema(SYNCARI_LAST_MODIFIED, "datetime");
        lastModified.setDisplayName("Last Modified");
        lastModified.setWatermarkField(true);
        lastModified.setNillable(false);
        lastModified.setSystem(true);
        lastModified.setIndex(++currIndex);
        attributes.add(lastModified);
        if (includeMissingIdField && !attributes.stream().anyMatch(a -> a.getApiName().equals(Constants.SYNCARI_ID))) {
            AttributeSchema rowId = new AttributeSchema(Constants.SYNCARI_ID, "string");
            rowId.setDisplayName("Syncari ID");
            rowId.setIdField(true);
            rowId.setNillable(false);
            rowId.setIndex(++currIndex);
            attributes.add(rowId);
        }
        return attributes;
    }

    private String getDatatype(Map<?, ?> data) {
        Map<?, ?> values = (Map<?, ?>) data.get(CELL_FORMAT);
        if(values==null){
            values = (Map<?, ?>) data.get(EFFECTIVE_VALUE);
            Optional<?> key = values.keySet().stream().findFirst();
            if(key.isPresent()) {
                return key.get().toString().replace("Value", "");
            }
            return null;
        }else{
            if(values.containsKey("numberFormat")){
                Map<?,Object> formatDetails = (Map<?, Object>) values.get("numberFormat");
                String datatype = (String) formatDetails.getOrDefault("type","TEXT");
                switch(datatype){
                    case "NUMBER": return "number";
                    case "PERCENT": return "percent";
                    case "CURRENCY": return "currency";
                    case "DATE": return "date";
                    case "DATE_TIME": return "datetime";
                    default:return "string";
                }
            }
        }
        return "string";
    }
    
    private String getName(Map<?, ?> data) {
        Map<?, ?> values = (Map<?, ?>) data.get(EFFECTIVE_VALUE);
        if(values==null) return null;
        Optional<?> entry = values.values().stream().findFirst();
        if(entry.isPresent()) {
            return entry.get().toString();
        }
        return null;
    }

    private Partition createPartition(EntitySchema entity, ConnectorInfo info, SyncariOauthRestClient restClient, List<String> headers) {
        Map payload = new HashMap<>();
        payload.put("mimeType", "application/vnd.google-apps.spreadsheet");
        payload.put("name", entity.getDisplayName() + "_" + Instant.now());
        payload.put("parents", List.of(entity.getApiName()));
        String url = String.format(decorate(CREATE_FILE, info), entity.getDisplayName() + "_" + Instant.now(), entity.getApiName());
        try {
            ResponseEntity<String> post = restClient.postRaw(url, mapper.writeValueAsString(payload), info, getTokenHandler(info), SHEETS_TOKEN_REFRESH_ERROR_CODES);
            ReadContext ctx = JsonPath.parse(post.getBody());
            String spreadSheetId = ctx.read("id");
            String partitionId = generatePartitionId(getRootFolderId(info), entity.getApiName(), spreadSheetId);
            
            // add the header row with all column names
            String sheetName = Optional.of(entity).flatMap(e -> e.getProperty(spreadSheetId)).orElse("Sheet1").toString();

            String range = String.format("%s!%s1", sheetName, 'A');
            String headerUrl = String.format(decorate(WRITE_ROWS, info), spreadSheetId, range);
            CreateRequest create1 = new CreateRequest().setRange(range);

            create1.getValues().add(headers);
            restClient.postRaw(headerUrl, mapper.writeValueAsString(create1), info, getTokenHandler(info), SHEETS_TOKEN_REFRESH_ERROR_CODES);
            log.debug("Successfully added {} columns to sheet {}", headers.size(), spreadSheetId);
            
            return new Partition(partitionId, entity.getDisplayName() + "_" + Instant.now(), Instant.now(), 0, 0);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private FetchResponse getIterator(SyncRequest request, SyncariOauthRestClient restClient,
                                      WatermarkInfo watermark, Optional<SheetInfo> spreadSheet) {
        List<GoogleSheetsIterator> sheetIterators = new ArrayList<>();
        spreadSheet.ifPresent(spreadsheetFile -> {
            Map<?, ?> sheet = getSheet(spreadsheetFile.getSpreadsheetId(), request.getConnector(), restClient, Optional.empty());
            String sheetName = ((Map<?, ?>) sheet.get(PROPERTIES)).get(TITLE).toString();
            String colCount = ((Map<?, ?>) ((Map<?, ?>) sheet.get(PROPERTIES)).get("gridProperties")).get("columnCount")
                    .toString();
            log.debug("colCount {} sheetId {}", colCount, spreadsheetFile.getSpreadsheetId());
            // The index is 2 because we skip the header on index 1
            int limit = request.getWatermark() == null ? PAGE_SIZE : request.getWatermark().getLimit();
            sheetIterators.add(new GoogleSheetsIterator(request, spreadsheetFile.withSheetName(sheetName),
                    watermark, 2, Integer.valueOf(colCount), mapper, limit, getTokenHandler(request.getConnector())));
        });

        GoogleFileIterator iterator = new GoogleFileIterator(watermark, sheetIterators);
        return new FetchResponse(request.getWatermark(), iterator);
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }
    
    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }
    
    protected String decorate(String url, ConnectorInfo info) {
        if(info == null || StringUtils.isBlank(info.getId())) return url;
        if(url.contains("?")) {
            url = url.concat("&").concat(QUOTA_USER).concat(info.getId());
        } else {
            url = url.concat("?").concat(QUOTA_USER).concat(info.getId());
        }
        if(url.startsWith(DRIVE_V3)) {
            url = url.concat("&includeItemsFromAllDrives=true&supportsAllDrives=true");
        }
        return url;
    }
    
    private Integer addSheet(SyncRequest request, String spreadSheetId, String sheetName, SyncariOauthRestClient restClient) {
        try {
            // Try to delete the sheet first if it was not cleanup from previous run
            Map sheet = getSheet(spreadSheetId, request.getConnector(), restClient, Optional.of(sheetName));
            if(!sheet.isEmpty()) {
                Integer sheetId = (Integer) ((Map)sheet.get("properties")).get("sheetId");
                deleteSheet(request, sheetId, spreadSheetId, restClient);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        try {
            String valueAsString = mapper.writeValueAsString(Map.of("requests", List.of(
                    Map.of("addSheet", Map.of("properties", Map.of("hidden", true, "title", sheetName))))));
            String url = String.format(decorate(ADD_SHEET, request.getConnector()), spreadSheetId);
            ResponseEntity<String> responseEntity = restClient.postRaw(url, valueAsString,
                    request.getConnector(), getTokenHandler(request.getConnector()), SHEETS_TOKEN_REFRESH_ERROR_CODES);
            Map row = mapper.readValue(responseEntity.getBody(), Map.class);
            return (Integer) ((Map)((Map)((Map)(((List)row.get("replies")).get(0))).get("addSheet")).get("properties")).get("sheetId");
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }
    
    private void deleteSheet(SyncRequest request, Integer sheetId, String spreadSheetId, SyncariOauthRestClient restClient) {
        try {
            String valueAsString = mapper.writeValueAsString(Map.of("requests", List.of(Map.of("deleteSheet", Map.of("sheetId", sheetId)))));
            String url = String.format(decorate(ADD_SHEET, request.getConnector()), spreadSheetId);
            restClient.postRaw(url, valueAsString, request.getConnector(), getTokenHandler(request.getConnector()), SHEETS_TOKEN_REFRESH_ERROR_CODES);
        } catch (JsonProcessingException e) {
            log.error(e.getMessage());
        }
    }

    private List getExistingRowIndex(SyncRequest request, EntitySchema entity, String spreadSheetId, String sheetName,
            SyncariOauthRestClient restClient) {
        // Create a formula
        List<String> attributes = entity.getAttributes().stream().map(a -> a.getApiName().toLowerCase()).collect(Collectors.toList());
        String apiName = entity.hasIdField() ? entity.getIdField().getApiName().toLowerCase() : Constants.SYNCARI_ID;
        int indexOf = attributes.indexOf(apiName);
        if(indexOf == -1) return List.of();
        String columnAlphabet = ConnectorHelper.getColumnAlphabet(indexOf + 1);
        String range = String.format("%s!%s:%s", sheetName, columnAlphabet, columnAlphabet);
        String formula = "=MATCH(\"%s\", " + range + ", 0)";
        List<String> parts = request.getData().get(request.getConnector().getId()).stream()
                .map(e -> String.format(formula, getIdValue(request, e))).collect(Collectors.toList());
        EditRequest update = new EditRequest();
        update.valueInputOption = "USER_ENTERED";
        CreateRequest entry = new CreateRequest();
        entry.range = LOOKUP_SHEET+"!A1";
        entry.values = List.of(parts);
        update.data.add(entry);
        
        try {
            String url = String.format(decorate(UPDATE_SHEET, request.getConnector()), spreadSheetId);
            ResponseEntity<String> responseEntity = restClient.postRaw(url, mapper.writeValueAsString(update),
                    request.getConnector(), getTokenHandler(request.getConnector()), SHEETS_TOKEN_REFRESH_ERROR_CODES);
            Map row = mapper.readValue(responseEntity.getBody(), Map.class);
            List values = (List) ((Map)((Map)(((List)row.get("responses")).get(0))).get("updatedData")).get("values");
            if(values == null || values.isEmpty()) List.of();
            return (List) values.get(0);
        } catch (Exception e1) {
            log.error(e1.getMessage());
            return List.of();
        }
    }

    private String getIdValue(SyncRequest request, EntityData entityData) {
        if(entityData.getId()!=null) {
            return entityData.getId();
        }
        
        return request.getEntitySchema().hasIdField() && !request.getEntitySchema().getIdField().getApiName().equalsIgnoreCase(Constants.SYNCARI_ID)
                ? entityData.getValueAsString(request.getEntitySchema().getIdField().getApiName())
                :entityData.getSyncariEntityId() ;
    }
}

@Data
@Accessors(chain = true)
class CreateRequest {
    String range;
    String majorDimension = "ROWS";
    List values = new ArrayList<>();
}

@Data
@Accessors(chain = true)
class DeleteRequest {
    List requests = new ArrayList<>();
}

@Data
@Accessors(chain = true)
class DeleteEntry {
    Map deleteDimension = new HashMap();
}

@Data
@Accessors(chain = true)
class Range {
    int sheetId = 0;
    String dimension = "ROWS";
    long startIndex;
    long endIndex;
}

@Data
@Accessors(chain = true)
class EditRequest {
    String valueInputOption = "RAW";
    List data = new ArrayList<>();
}
