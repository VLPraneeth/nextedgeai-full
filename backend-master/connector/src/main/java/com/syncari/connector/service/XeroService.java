package com.syncari.connector.service;

import static com.syncari.utils.DateUtil.dateFormat;
import static java.lang.String.format;
import static org.apache.axis.utils.XMLUtils.base64encode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.syncari.connector.data.*;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.syncari.connector.ConnectorErrorCodes;
import com.syncari.connector.ConnectorHelper;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.DefaultAuthTokenHandler;
import com.syncari.connector.EntityData;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.data.iterator.DefaultDataIterator;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.connector.rest.XeroRestClient;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.OauthAuthenticationService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.connector.service.seed.XeroSeed;
import com.syncari.utils.DateUtil;
import com.syncari.utils.I18n;
import com.syncari.utils.Pair;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(Constants.XERO)
public class XeroService implements CommonDataService, MetadataService, SynapseInfoService, OauthAuthenticationService {
    private static final String ORG_NAME = "orgName";
    public static final String REPORT_BALANCESHEET = "BalanceSheet";
    public static final String REPORT_PROFITANDLOSS = "ProfitAndLoss";
    @Autowired
    Transformer transformer;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    DefaultAuthTokenHandler tokenHandler;

    private static final List<String> SEED_ENTITIES = List.of(Constants.CONTACT, "contact", Constants.ACCOUNT,
            "account", REPORT_BALANCESHEET, "balancesheet", REPORT_PROFITANDLOSS, "profitandloss");

    private static final Map<String, String> objPluralMap = Map.of(Constants.CONTACT, "contacts", Constants.ACCOUNT,
            "accounts", REPORT_BALANCESHEET, "balancesheets", REPORT_PROFITANDLOSS, "profitandloss");

    private static final String CONTACT_END_POINT = "https://api.xero.com/api.xro/2.0/Contacts";
    private static final String ACCOUNTS_END_POINT = "https://api.xero.com/api.xro/2.0/Accounts";
    private static final String BALANCE_SHEET_END_POINT = "https://api.xero.com/api.xro/2.0/Reports/BalanceSheet";
    private static final String PROFIT_LOSS_END_POINT= "https://api.xero.com/api.xro/2.0/Reports/ProfitAndLoss";
    private static final String WATERMARK_QUERY = "UpdatedDateUTC>=%s && UpdatedDateUTC<=%s";
    private static final String BALANCE_SHEET_WATERMARK_QUERY = "date=%s&standardLayout=true";
    private static final String PROFIT_LOSS_WATERMARK_QUERY = "fromDate=%s&toDate=%s&standardLayout=true";
    private static final int PROGRAM_MAX_PAGESIZE = 200;
    private static final String OAUTH_HOST = "https://login.xero.com/identity/connect";
    private static final String OAUTH_URL = "https://identity.xero.com/connect/token";
    private static final String scope = "scope=openid profile offline_access email accounting.settings accounting.transactions accounting.contacts accounting.reports.read";
    private static final String LOGIN_URL= "https://login.xero.com/identity/connect/authorize?response_type=code&client_id=%s&redirect_uri=%s&"+scope;
    private static final String ORG_URL = "https://api.xero.com/api.xro/2.0/Organisation";
    private static final int MAX_REPORT_TITLES=5;
    private static final String XERO_AUTHORIZED_CONNECTIONS_URL = "https://api.xero.com/connections";

    LoadingCache<BalanceSheetCacheKey, List<EntityData>> balanceSheetCache = CacheBuilder.newBuilder().maximumSize(1000)
            .build(new CacheLoader<>() {
                @Override
                public List<EntityData> load(BalanceSheetCacheKey key) {
                    // Get the last day of previous month since the reports are generated monthly
                    String lasDayOfLastMonth = dateUtil.getLasDayOfLastMonth(key.getRequest().getWatermark().getEnd());
                    String condition = format(BALANCE_SHEET_WATERMARK_QUERY, lasDayOfLastMonth);
                    List<EntityData> reports = getReports(BALANCE_SHEET_END_POINT, key.getRequest() , condition);
                    for(int i=0;i<reports.size();i++) {
                        reports.get(i).setId(lasDayOfLastMonth+"_"+i);
                    }
                    return reports;
                }
            });
    
    LoadingCache<ProfitLossCacheKey, List<EntityData>> profitLossCache = CacheBuilder.newBuilder().maximumSize(1000)
            .build(new CacheLoader<>() {
                @Override
                public List<EntityData> load(ProfitLossCacheKey key) {
                    // Get the first and last day of previous month since the reports are generated monthly
                    String firstDayOfLastMonth = dateUtil.getFirstDayOfLastMonth(key.getRequest().getWatermark().getEnd());
                    String lasDayOfLastMonth = dateUtil.getLasDayOfLastMonth(key.getRequest().getWatermark().getEnd());
                    String condition = format(PROFIT_LOSS_WATERMARK_QUERY, firstDayOfLastMonth, lasDayOfLastMonth);
                    List<EntityData> reports = getReports(PROFIT_LOSS_END_POINT, key.getRequest() , condition);
                    for(int i=0;i<reports.size();i++) {
                        reports.get(i).setId(firstDayOfLastMonth+"_"+lasDayOfLastMonth+"_"+i);
                    }
                    return reports;
                }
            });
    
    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(new AuthMetadata(AuthType.Oauth,
                List.of(ConnectorHelper.getClientIdField(), ConnectorHelper.getClientSecretField()), "OAuth", ""));
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField baseId = new AuthField();
        baseId.setDataType("text");
        baseId.setName(ORG_NAME);
        baseId.setLabel("Organisation Name");
        baseId.setHelpSummary("The id of the Organisation to which the api calls would be done");
        return List.of(baseId, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public boolean isSink() {
        return false;
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19200050510996";
    }

    @Override
    public String getCategory() {
        return "Productivity";
    }

    @Override
    public String getName() {
        return Constants.XERO;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/xero.svg")
                .setDisplayName("XERO")
                .setBackgroundColor("#EAF5F7")
                .setHelpUrl(helpArticlesBaseUrl + "/360052156612-Xero-Setup");
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "/authorize?response_type=code&redirect_uri={{redirect_uri}}&client_id={{client_id}}&" + scope;
    }

    @Override
    public String getAuthHost(AuthConfig config) {
        return OAUTH_HOST;
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, DefaultAuthTokenHandler.REFRESH_TOKEN,
                DefaultAuthTokenHandler.REFRESH_TOKEN, config.getRefreshToken());
        Map<String, String> headerMap = Map.of("Authorization", "Basic " + base64encode((config.getClientId() + ":" + config.getClientSecret()).getBytes()));
        return tokenHandler.refreshToken(config, OAUTH_URL, map, headerMap);
    }

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, "authorization_code",
                DefaultAuthTokenHandler.CODE, oAuthRequest.getCode(),
                DefaultAuthTokenHandler.REDIRECT_URI, oAuthRequest.getRedirectUri());
        Map<String, String> headerMap = Map.of("Authorization", "Basic " + base64encode((oAuthRequest.getConfig().getClientId() + ":" + oAuthRequest.getConfig().getClientSecret()).getBytes()));
        return tokenHandler.getAccessToken(OAUTH_URL, map, headerMap);
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        if(Constants.CONTACT.equalsIgnoreCase(request.getEntityName())){
            return getByWatermarkContacts(request);
        }
        if(Constants.ACCOUNT.equalsIgnoreCase(request.getEntityName())){
            return getByWatermarkAccounts(request);
        }
        if(REPORT_BALANCESHEET.equalsIgnoreCase(request.getEntityName())){
            return getByWaterMarkReports(request, BALANCE_SHEET_END_POINT);
        }
        if(REPORT_PROFITANDLOSS.equalsIgnoreCase(request.getEntityName())){
            return getByWaterMarkReports(request, PROFIT_LOSS_END_POINT);
        }
        throw new RuntimeException("Getbywatermark not supported for " + request.getEntityName());
    }

    private FetchResponse getByWatermarkContacts(SyncRequest request) {
         Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            var results = getContacts(CONTACT_END_POINT, request, formatWaterMarkQuery(request));
            return Pair.of(Long.valueOf(results.size()), results.stream());
        };

        DefaultDataIterator iterator = new DefaultDataIterator(request.getWatermark(), request.getWatermark().getOffset(),
                generator, new ArrayList<>(), request.getEntitySchema().getWatermarkField(),PROGRAM_MAX_PAGESIZE, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private FetchResponse getByWatermarkAccounts(SyncRequest request) {
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            var results = getAccounts(ACCOUNTS_END_POINT, request, formatWaterMarkQuery(request));
            return Pair.of(Long.valueOf(results.size()), results.stream());
        };
        DefaultDataIterator iterator = new DefaultDataIterator(request.getWatermark(), request.getWatermark().getOffset(),
                generator, new ArrayList<>(), request.getEntitySchema().getWatermarkField(),PROGRAM_MAX_PAGESIZE, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    private FetchResponse getByWaterMarkReports(SyncRequest request, String endPoint) {
        Function3<WatermarkInfo, Integer, Long, Pair<Long, Stream<EntityData>>> generator = (wm, pageSize, offset) -> {
            try {
                List<EntityData> results = new ArrayList<>();
                if(REPORT_BALANCESHEET.equalsIgnoreCase(request.getEntityName())){
                    results = balanceSheetCache.get(new BalanceSheetCacheKey(request, request.getWatermark().getEnd()));
                }
                if(REPORT_PROFITANDLOSS.equalsIgnoreCase(request.getEntityName())){
                    results = profitLossCache.get(new ProfitLossCacheKey(request, request.getWatermark().getStart(), request.getWatermark().getEnd()));
                }
                return Pair.of(Long.valueOf(results.size()), results.stream());
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getMessage());
            }
        };

        DefaultDataIterator iterator = new DefaultDataIterator(request.getWatermark(), request.getWatermark().getOffset(),
                generator, new ArrayList<>(), request.getEntitySchema().getWatermarkField(),PROGRAM_MAX_PAGESIZE, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        if(SEED_ENTITIES.contains(request.getEntity())){
            return Optional.of(XeroSeed.getSeedEntitySchema(request.getEntity()));
        }
        return Optional.empty();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> allSchemas = new ArrayList<>();
        ConnectorInfo connector = request.getConnector();
        objPluralMap.keySet().forEach( k -> {
            allSchemas.add(describe(new DescribeRequest(connector, k)).get());
        });
        return allSchemas;
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            if(!config.getMetaConfig().containsKey(ORG_NAME)) {
                throw new RuntimeException(I18n.i18n("xero_org_required"));
            }
            String orgName = config.getMetaConfig().get(ORG_NAME).toString();
            String uri = format(LOGIN_URL, config.getAuthConfig().getClientId(), config.getAuthConfig().getRedirectUri());
            SyncariEntityDataRestClient restClient = new SyncariEntityDataRestClient(getSingleJsonConfig(""), mapper);
            restClient.getResponse(uri, config.getAuthConfig());

            ResponseEntity<String> restResponse = restClient.getResponse(XERO_AUTHORIZED_CONNECTIONS_URL, config.getAuthConfig());
            List<Map<String, String>> connections = mapper.readValue(restResponse.getBody(), new TypeReference<List<Map<String, String>>>(){});
            boolean foundTenantId = false;
            for (Map<String, String> connMap : connections) {
                String tenantId = connMap.get("tenantId");
                config.getMetaConfig().put("tenantId", tenantId);
                
                ReadContext ctx = get(ORG_URL, config);
                List orgRows = ctx.read("Organisations");
                for (Object o : orgRows) {
                    Map<String, Object> map = (Map) o;
                    String name = map.get("Name").toString();
                    if (name.equalsIgnoreCase(orgName)) {
                        foundTenantId = true;
                        break;
                    }
                }
            }
            if (!foundTenantId) {
                config.getMetaConfig().remove("tenantId");
                throw new RuntimeException(format(I18n.i18n("xero_org_not_found"), orgName));
            }
            response.setMetaConfig(config.getMetaConfig());
            log.info(format("Successfully authenticated Xero connection for %s", config.getName()));
            return response;
        } catch (Exception e) {
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("Xero does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("Xero does not support delete field");
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        throw new NotSupportedException("Xero does not support getbyids");
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        return response;
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        return response;
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        return response;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in airtable yet");
    }

    @Override
    public void deleteObject(DeleteObjectRequest request) {

    }

    @Override
    public Map<String, String> getEntityMappings() {
        return Map.of(Constants.CONTACT.toLowerCase(),
                Constants.CONTACT,
                Constants.ACCOUNT.toLowerCase(),
                Constants.ACCOUNT,
                REPORT_BALANCESHEET.toLowerCase(),
                REPORT_BALANCESHEET,
                REPORT_PROFITANDLOSS.toLowerCase(),
                REPORT_PROFITANDLOSS);
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
       return XeroSeed.getAttributeMappings(entityApiName);
    }

    private JsonParserConfig getSingleJsonConfig(String plural) {
        return new JsonParserConfig(null, null, null, StringUtils.capitalize("Id"), true, null);
    }

    private String formatWaterMarkQuery(SyncRequest request){
        return format(WATERMARK_QUERY,
                dateUtil.format(request.getWatermark().getStart(), dateFormat),dateUtil.format(request.getWatermark().getEnd(), dateFormat) );
    }

    private List<EntityData> getContacts(String url, SyncRequest request,Object...uriArgs) {
        List<EntityData> result = new ArrayList<>();
        ReadContext Ctx = get(url, request.getConnector(), uriArgs);
        List rows = Ctx.read("Contacts");
         if (rows != null && rows.size() > 0) {
            for (int i = 0; i < rows.size(); i++) {
                EntityData data = new EntityData(request.getEntityName());
                Map resultMap = (Map)rows.get(i);
                resultMap.forEach((k, v) -> {
                    if(request.getEntitySchema().getWatermarkField().getApiName().equalsIgnoreCase((String)k)) {
                        data.setLastModified(dateUtil.toEpochMilli(convertXeroDate(v.toString())));
                    }else if(request.getEntitySchema().getIdField().getApiName().equalsIgnoreCase((String)k)){
                        data.setId(v.toString());
                    }else if(((String) k).equalsIgnoreCase("Phones")){
                        List<Map<String,String>> phonesList = (List<Map<String, String>>)v;
                        phonesList.stream().forEach( pMap -> {
                            switch (pMap.get("PhoneType")){
                                case "DDI":
                                    data.addValue("DDINumber",pMap.get("PhoneCountryCode") + pMap.get("PhoneAreaCode") + pMap.get("PhoneNumber"));
                                case "DEFAULT":
                                    data.addValue("PhoneNumber",pMap.get("PhoneCountryCode") + pMap.get("PhoneAreaCode") + pMap.get("PhoneNumber"));
                                case "FAX":
                                    data.addValue("FaxNumber",pMap.get("PhoneCountryCode") + pMap.get("PhoneAreaCode") + pMap.get("PhoneNumber"));
                                case "MOBILE":
                                    data.addValue("MobileNumber",pMap.get("PhoneCountryCode") + pMap.get("PhoneAreaCode") + pMap.get("PhoneNumber"));
                                default:
                                        break;
                            }
                        });
                    }else if(((String) k).equalsIgnoreCase("Addresses")){
                        List<Map<String,String>> addressList = (List<Map<String, String>>)v;
                        addressList.stream().forEach( aMap -> {
                            if(aMap.get("AddressType").equalsIgnoreCase("POBOX")){
                                data.addValue("POAddressLine1", aMap.get("AddressLine1"));
                                data.addValue("POAddressLine2", aMap.get("AddressLine2"));
                                data.addValue("POAddressLine3", aMap.get("AddressLine3"));
                                data.addValue("POAddressLine4", aMap.get("AddressLine4"));
                                data.addValue("POAttentionTo", aMap.get("AttentionTo"));
                                data.addValue("POCity", aMap.get("City"));
                                data.addValue("PORegion", aMap.get("Region"));
                                data.addValue("POZipCode", aMap.get("PostalCode"));
                                data.addValue("POCountry", aMap.get("Country"));
                            }else if(aMap.get("AddressType").equalsIgnoreCase("STREET")){
                                data.addValue("SAAddressLine1", aMap.get("AddressLine1"));
                                data.addValue("SAAddressLine2", aMap.get("AddressLine2"));
                                data.addValue("SAAddressLine3", aMap.get("AddressLine3"));
                                data.addValue("SAAddressLine4", aMap.get("AddressLine4"));
                                data.addValue("SAAttentionTo", aMap.get("AttentionTo"));
                                data.addValue("SACity", aMap.get("City"));
                                data.addValue("SARegion", aMap.get("Region"));
                                data.addValue("SAZipCode", aMap.get("PostalCode"));
                                data.addValue("SACountry", aMap.get("Country"));
                            }
                        });
                    }else if(((String) k).equalsIgnoreCase("ContactPersons")) {
                        List<Map<String,String>> personList = (List<Map<String, String>>)v;
                        for(int index=0; index<personList.size(); index++ ){
                            int finalIndex = index+1;
                            personList.get(index).forEach((key, value) -> {
                                data.addValue("Person"+finalIndex+key, value);
                            });

                        }

                    }else {
                        data.addValue((String) k,v);
                    }
                });
                result.add(data);
            }
        }
        return result;
    }

    private List<EntityData> getAccounts(String url, SyncRequest request,Object...uriArgs) {
        List<EntityData> result = new ArrayList<>();
        ReadContext Ctx = get(url, request.getConnector(), uriArgs);
        List rows = Ctx.read("Accounts");
        if (rows != null && rows.size() > 0) {
            for (int i = 0; i < rows.size(); i++) {
                EntityData data = new EntityData(request.getEntityName());
                Map resultMap = (Map)rows.get(i);
                resultMap.forEach((k, v) -> {
                    if(request.getEntitySchema().getWatermarkField().getApiName().equalsIgnoreCase((String)k)) {
                        data.setLastModified(dateUtil.toEpochMilli(convertXeroDate(v.toString())));
                    }else if(request.getEntitySchema().getIdField().getApiName().equalsIgnoreCase((String)k)) {
                        data.setId(v.toString());
                    }else {
                        data.addValue((String) k,v);
                    }
                });
                result.add(data);
            }
        }
        return result;
    }

    private List<EntityData> getReports(String url, SyncRequest request, Object...uriArgs) {
        List<EntityData> result = new ArrayList<>();
        ReadContext Ctx = get(url, request.getConnector(), uriArgs);
        List rows = Ctx.read("Reports");
        if (rows != null && rows.size() > 0) {
            for (int i = 0; i < rows.size(); i++) {
                Map resultMap = (Map)rows.get(i);
                Map<String, Object> commonMap = new HashMap<>();
                commonMap.put("ReportID", resultMap.get("ReportID").toString());
                commonMap.put("ReportName", resultMap.get("ReportName").toString());
                commonMap.put("ReportType", resultMap.get("ReportType").toString());
                Object reportDate = resultMap.get("ReportDate");
                if(reportDate != null) {
                    commonMap.put("ReportDate", dateUtil.parse(reportDate.toString(), dateUtil.dateOnlyFormat1));
                }
                int index = 0;
                for (Object entry : resultMap.entrySet()) {
                    Object k = ((Entry)entry).getKey();
                    Object v = ((Entry)entry).getValue();
                    if(((String) k).equalsIgnoreCase("ReportTitles")){
                        List<String> titleList = (List<String>)v;
                        for(int titleIndex =0; titleIndex < Math.min(titleList.size(), MAX_REPORT_TITLES); titleIndex++){
                            commonMap.put("ReportTitle" + (titleIndex+1), titleList.get(titleIndex));
                        }
                    }else if(((String) k).equalsIgnoreCase("Rows")){
                        List<Map<String, Object>> reportRows = (List<Map<String,Object>>)v;
                        for (Object reportEntry : reportRows) {
                            Map<String, Object> rowsMap = (Map<String, Object>) reportEntry;
                            if(((String)rowsMap.get("RowType")).equalsIgnoreCase("Header")){
                                List<Map<String, Object>> headerRows = (List<Map<String, Object>> ) rowsMap.get("Cells");
                                for(int headerIndex = 1; headerIndex < headerRows.size(); headerIndex++) {
                                    Object header = headerRows.get(headerIndex).get("Value");
                                    if(header != null) {
                                        commonMap.put("Header" + headerIndex, (String) (headerRows.get(headerIndex).get("Value")));
                                    }
                                }
                            }else if(((String)rowsMap.get("RowType")).equalsIgnoreCase("Section")){
                                List<Map<String, Object>> valueRows = (List<Map<String, Object>> ) rowsMap.get("Rows");
                                for (Object valueEntry : valueRows) {
                                    Map<String, Object> valueMap = (Map<String, Object>) valueEntry;
                                    if(((String)valueMap.get("RowType")).equalsIgnoreCase("SummaryRow")){
                                        Map<String, Object> summaryReportMap = new HashMap<>();
                                        summaryReportMap.put("SectionName", (String)rowsMap.get("Title"));
                                        List<Map<String, Object>> summaryRows = (List<Map<String, Object>> ) valueMap.get("Cells");
                                        summaryReportMap.put("LineItem",(String)summaryRows.get(0).get("Value"));
                                        summaryReportMap.put("IsSummaryRow", String.valueOf(true));
                                        for(int remainingIndex = 1; remainingIndex < summaryRows.size(); remainingIndex++){
                                            Object value = summaryRows.get(remainingIndex).get("Value");
                                            if(value != null) {
                                                summaryReportMap.put("Value" + remainingIndex, (String)value);
                                            }
                                        }
                                        summaryReportMap.put("LineNumber", index);
                                        result.add(addReportEntityData(commonMap, summaryReportMap, request));
                                        index++;
                                    }
                                    else if(((String)valueMap.get("RowType")).equalsIgnoreCase("Row")){
                                        Map<String, Object> rowReportMap = new HashMap<>();
                                        rowReportMap.put("SectionName", (String)rowsMap.get("Title"));
                                        List<Map<String, Object>> values = (List<Map<String, Object>> ) valueMap.get("Cells");
                                        rowReportMap.put("LineItem",(String)values.get(0).get("Value"));
                                        rowReportMap.put("IsSummary", String.valueOf(false));
                                        for(int remainingIndex = 1; remainingIndex < values.size(); remainingIndex++){
                                            Object value = values.get(remainingIndex).get("Value");
                                            if(value != null) {
                                                rowReportMap.put("Value" + remainingIndex, (String)value);
                                            }
                                        }
                                        rowReportMap.put("LineNumber", index);
                                        result.add(addReportEntityData(commonMap, rowReportMap, request));
                                        index++;
                                    }
                                }
                            }
                        }
                    } else {
                        commonMap.put((String)k,v.toString());
                    }
                }
            }
        }
        return result;
    }

    private EntityData addReportEntityData(Map commonMap, Map reportMap, SyncRequest request){
        EntityData data = new EntityData(request.getEntityName());
        data.setConnectorId(request.getConnector().getId());
        data.setLastModified(request.getWatermark().getEnd());
        commonMap.forEach((key,value) ->{
            if(((String)key).equalsIgnoreCase("UpdatedDateUTC")){
                data.addValue(((String)key),dateUtil.toEpochMilli(convertXeroDate(value.toString())));
            }else {
                data.addValue((String) key, value);
            }});
        reportMap.forEach((key,value) -> {
            if(value != null) {
                data.addValue((String)key,value);
            }
        });
        return data;
    }

    private ReadContext get(String url, ConnectorInfo info,Object...uriArgs){
        XeroRestClient restClient = new XeroRestClient(getSingleJsonConfig(""), mapper);
        ResponseEntity<String> response = restClient.getResponse(url, info.getAuthConfig(), info.getMetaConfig(), uriArgs);
        ReadContext Ctx = JsonPath.parse(response.getBody());
        return Ctx;
    }

    private String convertXeroDate(String xeroDate){
        Pattern jsonDatePattern = Pattern.compile("/Date\\((\\d+)([+-]\\d{4})\\)/");
        String dateFromJson = xeroDate;
        Matcher m = jsonDatePattern.matcher(dateFromJson);
        if (m.matches()) {
            long epochMillis = Long.parseLong(m.group(1));
            String offsetString = m.group(2);
            OffsetDateTime dateTime = Instant.ofEpochMilli(epochMillis)
                    .atOffset(ZoneOffset.of(offsetString));
           return dateTime.toString();
        }else {
            DateTimeFormatter jsonDateFormatter = new DateTimeFormatterBuilder()
                    .appendLiteral("/Date(")
                    .appendValue(ChronoField.INSTANT_SECONDS)
                    .appendValue(ChronoField.MILLI_OF_SECOND, 3)
                    .appendLiteral(")/")
                    .toFormatter();
            Instant created = jsonDateFormatter.parse(xeroDate, Instant::from);
            return created.toString();
        }
    }

    @Data
    public static class BalanceSheetCacheKey {
        long date;
        SyncRequest request;
        
        public BalanceSheetCacheKey(SyncRequest request, long date) {
            this.request = request;
            this.date = date;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!super.equals(obj))
                return false;
            if (getClass() != obj.getClass())
                return false;
            BalanceSheetCacheKey other = (BalanceSheetCacheKey) obj;
            return date == other.date && request.getConnector().getId().equalsIgnoreCase(other.getRequest().getConnector().getId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(date, request.getConnector().getId());
        }
    }
    
    @Data
    public static class ProfitLossCacheKey {
        long startDate;
        long endDate;
        SyncRequest request;
        
        public ProfitLossCacheKey(SyncRequest request, long startDate, long endDate) {
            this.request = request;
            this.startDate = startDate;
            this.endDate = endDate;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!super.equals(obj))
                return false;
            if (getClass() != obj.getClass())
                return false;
            ProfitLossCacheKey other = (ProfitLossCacheKey) obj;
            return (startDate == other.startDate && endDate == other.endDate) && request.getConnector().getId().equalsIgnoreCase(other.getRequest().getConnector().getId());
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(startDate, endDate, request.getConnector().getId());
        }
    }
}
