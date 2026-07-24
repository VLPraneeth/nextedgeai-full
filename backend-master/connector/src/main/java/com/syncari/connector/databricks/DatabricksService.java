package com.syncari.connector.databricks;

import com.databricks.sdk.WorkspaceClient;
import com.databricks.sdk.core.DatabricksConfig;
import com.databricks.sdk.service.catalog.ColumnInfo;
import com.databricks.sdk.service.catalog.ColumnTypeName;
import com.databricks.sdk.service.catalog.TableInfo;
import com.databricks.sdk.service.sql.ExecuteStatementRequest;
import com.databricks.sdk.service.sql.ExecuteStatementResponse;
import com.databricks.sdk.service.sql.StatementState;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultCursorBasedIterator;
import com.syncari.connector.database.CompositeKeyHelper;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.function.Function3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Slf4j
@Component(Constants.DATABRICKS)
public class DatabricksService implements AuthenticationService, CommonDataService, MetadataService, SynapseInfoService {

    public static final String BY_WM_QUERY = "SELECT * FROM %s.%s.%s WHERE %s >= TIMESTAMP '%s' AND %s < TIMESTAMP '%s' %s";
    public static final String BY_NO_WM_QUERY = "SELECT * FROM %s.%s.%s WHERE %s";
    public static final String BY_ID_QUERY = "SELECT * FROM %s.%s.%s WHERE %s IN (%s)";
    public static final String BY_COMPOSITE_ID_QUERY = "SELECT * FROM %s.%s.%s WHERE %s";
    public static final String SYNCARI_DEFINED_WATERMARK = "syncari_watermark_timestamp";
    public static final String CATALOG = "catalog";
    public static final String SCHEMA = "schema";
    public static final String QUOTE = "'";


    @Autowired
    DateUtil utils;

    @Autowired
    CompositeKeyHelper compositeKeyHelper;
    
    @Override
    public TestConnectionResponse testConnection(ConnectorInfo connector, List<String> entityNames) {
        TestConnectionResponse response = new TestConnectionResponse();
        try {
            log.info(format("Testing Databricks connection for %s", connector.getName()));
            testDatabricksConnectionWithoutDescribingAllTables(connector);
            return response;
        } catch (Exception e) {
            log.error("Databricks Authentication failed {}", e);
            response.setCode(ConnectorErrorCodes.CONNECTION_ERROR);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    private void testDatabricksConnectionWithoutDescribingAllTables(ConnectorInfo connectorInfo){
        try{
            String catalog = getValue(connectorInfo, CATALOG);
            // list summaries of a catalog and get tables to check get table permission.
            getClient(connectorInfo).tables().listSummaries(catalog).getTables();
        }catch (Exception e){
            throw new RuntimeException(e);
        }

    }
    
    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        try {
            String tableName = request.getEntity();
            String catalog = getValue(request.getConnector(), CATALOG);
            String schema = getValue(request.getConnector(), SCHEMA);
            if (StringUtils.isNotEmpty(tableName)){
                String fullTableName = catalog + "."+schema + "." + request.getEntity().toLowerCase();
                TableInfo table = getClient(request.getConnector()).tables().get(fullTableName);
                if (null != table){
                    if(table.getName().equalsIgnoreCase(tableName)) {
                        EntitySchema entitySchema = toEntitySchema(table);
                        return Optional.of(entitySchema);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to describe entity: {}", e);
            return Optional.empty();
        }
        return Optional.empty();
    }
    
    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> schemas = new ArrayList<>();
        String catalog = getValue(request.getConnector(), CATALOG);
        String schema = getValue(request.getConnector(), SCHEMA);
        Iterable<TableInfo> tables = getClient(request.getConnector()).tables().list(catalog, schema);
        for (TableInfo table : tables) {
            EntitySchema entitySchema = toEntitySchema(table);
            schemas.add(entitySchema);
        }
        return schemas;
    }

    private EntitySchema toEntitySchema(TableInfo tableInfo){
        String tableName = tableInfo.getName();
        EntitySchema entitySchema = new EntitySchema();
        entitySchema.setApiName(tableName);
        entitySchema.setDisplayName(tableName);

        List<AttributeSchema> attributes = new ArrayList<>();
        for (ColumnInfo column : tableInfo.getColumns()) {
            AttributeSchema attr = new AttributeSchema();
            attr.setApiName(column.getName());
            attr.setDisplayName(column.getName());
            attr.setDataType(mapDatabricksType(column,column.getName()));
            attributes.add(attr);
        }
        List<AttributeSchema> existingWatermarkField = attributes.stream().filter(a -> a.isWatermarkField()).collect(Collectors.toList());
        // Add a default syncari defined watermark field
        AttributeSchema watermarkattr = new AttributeSchema();
        watermarkattr.setApiName(SYNCARI_DEFINED_WATERMARK);
        watermarkattr.setDisplayName(SYNCARI_DEFINED_WATERMARK);
        watermarkattr.setWatermarkField(true);
        watermarkattr.setSyncariDefined(true);
        watermarkattr.setUpdateable(true);
        watermarkattr.setDataType("datetime");
        attributes.add(watermarkattr);
        entitySchema.setAttributes(attributes);
        return entitySchema;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        try {
            final String catalog = getValue(request.getConnector(), CATALOG);
            final String schema = getValue(request.getConnector(), SCHEMA);
            final String tableName = request.getEntityName();
            final String watermarkField = request.getEntitySchema().getWatermarkField().getApiName();
            final AttributeSchema idFieldAtt = request.getEntitySchema().getIdField();
            final String idFieldApiName = idFieldAtt.getApiName();

            Function3<WatermarkInfo, Integer, String, DataWithCursor> generator = (wm, pageSize, nextID) -> {
                String query;
                if (StringUtils.isNotEmpty(watermarkField) && watermarkField.equalsIgnoreCase(SYNCARI_DEFINED_WATERMARK)){
                    query = format(BY_NO_WM_QUERY,
                            catalog, schema, tableName,
                            getCursorConditionForNoWM(request,nextID,pageSize));
                }else{
                    query = format(BY_WM_QUERY,
                            catalog, schema, tableName, watermarkField,
                            utils.format(new Date(request.getWatermark().getStart()), DateUtil.dateFormat),
                            watermarkField,
                            utils.format(new Date(request.getWatermark().getEnd()), DateUtil.dateFormat),
                            getCursorCondition(request,nextID,pageSize));
                }


                log.info("Fetching data page for cursor {} and query {}", idFieldApiName, query);
                List<Map<String, Object>> rows = executeQuery(request.getConnector(), query);
                List<EntityData> results = new ArrayList<>();

                for (Map<String, Object> row : rows) {
                    EntityData data = new EntityData(request.getEntityName());
                    data.setConnectorId(request.getConnector().getId());

                    if (row.containsKey(idFieldApiName)) {
                        data.setId(row.get(idFieldApiName).toString());
                    }

                    if (row.containsKey(watermarkField)) {
                        Date watermarkDate = utils.parse(row.get(watermarkField).toString(), DateUtil.dateFormatMillis);
                        data.setLastModified(watermarkDate.getTime());
                    }

                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        data.addValue(entry.getKey(), entry.getValue());
                    }
                    if (idFieldAtt != null && !StringUtils.isBlank(idFieldAtt.getCompositeKey())) {
                        data.setId(compositeKeyHelper.composeIdKeys(data, request.getEntitySchema()));
                    }
                    results.add(data);
                }
                return new DataWithCursor(nextID, getNextCursor(results), results);
            };

            int pgSize = (request.getPageSize() <= 0) ? 1000 : request.getPageSize();
            DefaultCursorBasedIterator iterator = new DefaultCursorBasedIterator(request.getWatermark(), request.getWatermark().getChangeStream(),
                    request.getWatermark().getOffset(), generator, new ArrayList<>(), pgSize, request.getWatermark().getLimit(), false);
            return new FetchResponse(request.getWatermark(), iterator);
        } catch (Exception e) {
            log.error("Failed to fetch data: {}", e);
            throw e;
        }
    }

    private String getNextCursor(List<EntityData> result) {
        if (result != null && result.size() > 0) {
            return result.get(result.size() - 1).getId();
        }
        return null;
    }

    String getCursorCondition(SyncRequest request, String nextID, int pageSize) {
        String idField = request.getEntitySchema().getIdField().getApiName().toLowerCase();
        if (StringUtils.isEmpty(nextID)){
            return  " ORDER BY " + idField + " LIMIT " + pageSize;
        }

        if (("text".equalsIgnoreCase(request.getEntitySchema().getIdField().getDataType())) || ("string".equalsIgnoreCase(request.getEntitySchema().getIdField().getDataType()))) {
            nextID = String.format("'%s'", nextID);
        }
        return  " AND " + idField + " > " + nextID + " ORDER BY " + idField + " LIMIT " + pageSize;

    }

    String getCursorConditionForNoWM(SyncRequest request, String nextID, int pageSize) {
        String idField = request.getEntitySchema().getIdField().getApiName().toLowerCase();
        if (StringUtils.isEmpty(nextID)){
            return  " ORDER BY " + idField + " LIMIT " + pageSize;
        }

        if (("text".equalsIgnoreCase(request.getEntitySchema().getIdField().getDataType())) || ("string".equalsIgnoreCase(request.getEntitySchema().getIdField().getDataType()))) {
            nextID = String.format("'%s'", nextID);
        }
        return  " WHERE " + idField + " > " + nextID + " ORDER BY " + idField + " LIMIT " + pageSize;

    }

    private List<String> getIds(SyncRequest request, boolean quote) {
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        String datatype = request.getEntitySchema().getIdField().getDataType();
        return entityList.stream().map(e -> {
            if("string".equalsIgnoreCase(datatype) || "text".equalsIgnoreCase(datatype)) {
                return quote ? "'"+e.getId()+"'" : e.getId();
            } else return e.getId();
        }).collect(Collectors.toList());
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        AttributeSchema idField = request.getEntitySchema().getIdField();
        boolean isComposite = !StringUtils.isBlank(idField.getCompositeKey());

        if(!request.getEntitySchema().hasIdField()) {
            throw new RuntimeException("Id field not defined for entity " + request.getEntityName());
        }
        String catalog = getValue(request.getConnector(), CATALOG);
        String schema = getValue(request.getConnector(), SCHEMA);
        String tableName = request.getEntityName();
        List<EntityData> results = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        String query;
        if (isComposite){
            List<String> ids = getIds(request, false);
            List<String> idPredicates = new ArrayList<>();
            String[] keys = request.getEntitySchema().getCompositeKeyAttributes().stream().map(a -> a.getApiName()).toArray(String[]::new);
            for(String id : ids) {
                List<String> innerPredicate = new ArrayList<>();
                String[] values = id.split(Pattern.quote(EntitySchema.COMPOSITE_KEY_DELIMETER));
                for (int i =0; i< keys.length; i++) {
                    innerPredicate.add("\""+keys[i]+"\"" +" = "+ QUOTE+values[i]+QUOTE);
                }
                idPredicates.add("("+innerPredicate.stream().collect(Collectors.joining(" AND "))+")");
            }
            query = String.format(BY_COMPOSITE_ID_QUERY,
                    catalog, schema, tableName,
                    StringUtils.join(idPredicates, " OR "));
            rows.addAll(executeQuery(request.getConnector(), query));

        }else{
            String ids = request.getIds().stream()
                    .map(id -> "'" + id + "'")
                    .collect(Collectors.joining(","));

            query = format(BY_ID_QUERY,
                    catalog, schema, tableName, idField.getApiName(), ids);
            rows.addAll(executeQuery(request.getConnector(), query));
        }
        try {
            for (Map<String, Object> row : rows) {
                EntityData data = new EntityData(request.getEntityName());
                data.setConnectorId(request.getConnector().getId());
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    data.addValue(entry.getKey(), entry.getValue());
                }
                if (idField != null && !StringUtils.isBlank(idField.getCompositeKey())) {
                    data.setId(compositeKeyHelper.composeIdKeys(data, request.getEntitySchema()));
                } else {
                    data.setId(row.get(idField.getApiName()).toString());
                }
                results.add(data);
            }
        } catch (Exception e) {
            log.error("Failed to fetch data by IDs: {}", e.getMessage());
        }
        return results;
    }
    @Override
    public boolean supportsNoWatermark(ConnectorInfo connectorInfo) {
        return getCapabilities().contains(Capability.noWatermark);
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        throw new RuntimeException("create not supported for Databricks readonly connector");
    }
    
    @Override
    public SyncResponse update(SyncRequest request) {
        throw new RuntimeException("update not supported for Databricks readonly connector");
    }
    
    @Override
    public SyncResponse delete(SyncRequest request) {
        throw new RuntimeException("delete not supported for Databricks readonly connector");
    }
    
    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported for Databricks readonly connector");
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("createField not supported for Databricks readonly connector");
    }
    
    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("deleteField not supported for Databricks readonly connector");
    }
    
    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }
    

    @Override
    public MergeResponse merge(MergeRequest request) {
        throw new RuntimeException("merge not supported for Databricks readonly connector");
    }
    
    @Override
    public List<MergeResponse> merge(List<MergeRequest> requests) {
        return List.of();
    }
    
    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getSimpleOAuthType());
    }
    
    @Override
    public List<AuthField> getConfigureFields() {
        AuthField workspace = new AuthField();
        workspace.setName("endpoint");
        workspace.setLabel("Workspace URL");
        workspace.setDataType("text");
        workspace.setRequired(true);
        workspace.setHelpSummary("Your Databricks workspace URL (e.g., myworkspace.databricks.com)");

        AuthField warehouseId = new AuthField();
        warehouseId.setName("warehouseId");
        warehouseId.setLabel("SQL Warehouse ID");
        warehouseId.setDataType("text");
        warehouseId.setRequired(true);
        warehouseId.setHelpSummary("The ID of your SQL warehouse");

        AuthField catalog = new AuthField();
        catalog.setName("catalog");
        catalog.setLabel("Catalog");
        catalog.setDataType("text");
        catalog.setRequired(true);
        catalog.setHelpSummary("The catalog name to connect to");

        AuthField schema = new AuthField();
        schema.setName("schema");
        schema.setLabel("Schema");
        schema.setDataType("text");
        schema.setRequired(true);
        schema.setHelpSummary("The schema name within the catalog");
        return List.of(workspace, warehouseId,catalog, schema, ConnectorHelper.getSupportedAuthPicker());
    }
    
    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }
    
    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return new HashMap<>();
    }
    
    @Override
    public String getName() {
        return "databricks";
    }
    
    @Override
    public String getCategory() {
        return "Data Warehouse";
    }
    
    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata()
            .setIconPath("/assets/icons/logos/databricks.svg")
            .setDisplayName("Databricks")
            .setBackgroundColor("#F9F7F4")
            .setHelpUrl(helpArticlesBaseUrl + "/360056102571-Databricks");
    }
    
    @Override
    public boolean isSource() {
        return true;
    }
    
    @Override
    public boolean isSink() {
        return false;
    }
    
    @Override
    public List<Capability> getCapabilities() {
        return List.of(
            Capability.getByWatermark,
            Capability.getById,Capability.noWatermark,
            Capability.search,Capability.schemaEditInSyncari, Capability.userEditableId, Capability.userEditableWm,Capability.compositeId
        );
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "360056102571";
    }
    
    private HttpHeaders getAuthHeaders(AuthConfig authConfig) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + authConfig.getAccessToken());
        headers.set("Content-Type", "application/json");
        return headers;
    }
    
    private List<Map<String, Object>> executeQuery(ConnectorInfo connector, String query) {
        List<Map<String, Object>> results = new ArrayList<>();
        String warehouseId = getValue(connector, "warehouseId");
        ExecuteStatementRequest req = new ExecuteStatementRequest()
                .setWarehouseId(warehouseId)
                .setStatement(query);

        ExecuteStatementResponse response = getClient(connector).statementExecution().executeStatement(req);
        if(response.getStatus() != null && response.getStatus().getState() == StatementState.FAILED) {
            throw new RuntimeException(response.getStatus().getError().getMessage());
        }
        Collection<Collection<String>> dataArray =  ((null != response) && (null != response.getResult()) && (null != response.getResult().getDataArray())) ? response.getResult().getDataArray() : Collections.emptyList();
        List<com.databricks.sdk.service.sql.ColumnInfo> columns = ((null != response) && (null != response.getManifest()) && (null != response.getManifest().getSchema()) && (null != response.getManifest().getSchema().getColumns())) ?
                response.getManifest().getSchema().getColumns().stream().collect(Collectors.toList()) : Collections.emptyList();

        for (Collection row : dataArray) {
            Map<String, Object> result = new HashMap<>();
            for (int i = 0; i < row.size(); i++) {
                Object[] array = row.toArray();
                result.put(columns.get(i).getName(), array[i]);
            }
            results.add(result);
        }
        return results;
    }

    private WorkspaceClient getClient(ConnectorInfo connector) {
        DatabricksConfig cfg = new DatabricksConfig()
                .setHost(connector.getEndpoint())
                .setClientId(connector.getAuthConfig().getClientId())
                .setClientSecret(connector.getAuthConfig().getClientSecret())
                .setToken(connector.getAuthConfig().getAccessToken());
        return new WorkspaceClient(cfg);
    }
    
    private String mapDatabricksType(ColumnInfo columnInfo, String apiName) {
        String typeText = columnInfo.getTypeText();
        log.debug("For attribute with api name {} and typeText is {}",apiName, typeText);
        if (StringUtils.isEmpty(typeText)){
            return "text";
        }
        switch (typeText){
            case "int":
            case "long":
            case "short":
            case "float":
            case "double":
            case "decimal":
                return "number";
            case "bool":
                return "boolean";
            case "date":
            case "timestamp":
            case "timestamp_ntz":
                return "datetime";
            default: return "text";
        }
    }

    private String getValue(ConnectorInfo connectorInfo, String key) {
        return connectorInfo.getMetaConfig().get(key).toString();
    }
}