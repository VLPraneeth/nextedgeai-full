package com.syncari.core.insights;

import com.google.cloud.bigquery.MaterializedViewDefinition;
import com.google.cloud.bigquery.Schema;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.datastore.PostgresqlDatastoreService;
import com.syncari.connector.datastore.SnowflakeDatastoreService;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Connector;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.provider.InsightsProviderConnection;
import com.syncari.core.model.insights.provider.InsightsProviderGroup;
import com.syncari.core.model.insights.provider.InsightsProviderUser;
import com.syncari.core.model.insights.provider.ts.*;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InsightsProviderIntegrator {

    @Autowired
    TSService tsService;

    @Autowired
    FeatureService featureService;

    @Autowired
    UserService userService;

    @Autowired
    PostgresqlDatastoreService postgresqlDatastoreService;

    @Autowired
    SnowflakeDatastoreService snowflakeDatastoreService;

    @Autowired
    DataServiceFactory dataServiceFactory;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    OrganizationRepo organizationRepo;

    @Autowired
    DatasetSchemaService datasetSchemaService;

    @Autowired
    DatasetService datasetService;

    @Autowired
    AppConfig appConfig;

    @Autowired
    BigQueryHelper bigQueryHelper;

    public static final String CONNECTION_NAME_FORMAT = "%s_CONNECTION";
    public static final String BQ_CONNECTION_NAME_FORMAT = "%s_BQ_CONNECTION";
    public static final String DFI_SYSTEM_SEEDED_LIVEBOARD_NAME = "Data Quality Dashboard";
    public static final String DFI_SYSTEM_SEEDED_LIVEBOARD_NAME_NONPROD = "Data Quality Dashboard_%s";

    private static final String THOUGHTSPOT_TYPE_SNOWFLAKE = "SNOWFLAKE";
    private static final String THOUGHTSPOT_TYPE_POSTGRES = "POSTGRES";


    public boolean provisionTSOrganization(){
        refreshDatastoreTokensIfNeeded();

        // Steps to provision
        // create organization with ORG NAME, either org already exists or gets created, this returns orgId
        // Create a connection for this syncariid with naming convention SYNCARIID_CONNECTION
        // To create connection fetch all the metadata of schema in db including all table names and their attributes
        // Fetch all users from Syncari instance
        // Create each user in TS for given org and create a Map of GROUP -> List of user ids
        // Iterate through map and create new groups in TS to set the respective privileges of Group in TS
        // Name of group would be SYNCARIID_Map key and privilege would map key
        // Org Admin, Instance Admin is DATAMANAGEMENT in TS
        // Sync Mgmt, Viewer is  NONE (READONLY) in TS
        try{
            Organization org = SyncariContext.getOrganziation();
            HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),600L); // Creating 10 mins token, assuming 20 mins provisioning would finish
            String orgId = tsService.createOrganization(org,headers);
            org.setInsightsProviderOrgId(orgId);
            organizationRepo.save(org);
            String instanceid = SyncariContext.getSyncariId();
            List<User> users = userService.getAllUsersFromInstance().stream().filter(u ->
                    (!u.isSuperAdmin() && !u.isGhostUser() && !u.isSystemUser() && !u.isApiUser())).collect(Collectors.toList());
            Map<String, Map<String, String>> groupUsersMap = new HashMap<>();
            for (User user : users){
                if (user.isGhostUser() || user.isSystemUser() || user.isSuperAdmin() || user.isApiUser()) continue;
                String userId = user.getId();
                String displayName = user.getId();
                InsightsProviderUser tsUser = new InsightsProviderUser();
                tsUser.setPassword("hardcoded@1"); // no use
                tsUser.setName(userId);
                tsUser.setDisplay_name(displayName);
                tsUser.setEmail(userId + "@syncari.io");
                TSUserResponse tsUserResponse = tsService.createUser(tsUser, Optional.of(TSService.TS_ADMIN_USER),headers);
                user.setInsightsProviderUserName(userId);
                user.setInsightsProviderUserId(tsUserResponse.getId());
                userService.saveUser(user);
                tsUser.setOrg_identifiers(List.of(orgId));
                tsUser.setOperation("ADD");
                tsService.updateUser(tsUser, Optional.of(TSService.TS_ADMIN_USER),true,headers);
                Set<String> roles = userService.getUserRolesForCurrentInstance(user.getId());
                String permission = getPermission(roles);
                groupUsersMap = addToMap(groupUsersMap,user.getId(), tsUser.getUser_identifier(), permission);
            }
            // get token again, last token does not have org id
            HttpHeaders groupTokenHeaders = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),600L); // Creating 10 mins token, assuming 20 mins provisioning would finish
            groupUsersMap.forEach((k,v) -> {
                // group naming convention based on current instanceid
                String grpName = instanceid + "_" + k;
                Optional<TSGrpResponse> grpResponse = tsService.searchGroup(grpName, Optional.of(TSService.TS_ADMIN_USER),groupTokenHeaders);
                log.info("Group name is {}", grpName);
                grpResponse.ifPresentOrElse(resp -> {
                    log.info("Group name {} already exists, add users to group", grpName);
                    tsService.addOrRemoveUserToGroup(grpName,v.values().stream().collect(Collectors.toList()),Optional.of(TSService.TS_ADMIN_USER),groupTokenHeaders, GroupOperation.ADD);
                },()->{
                    InsightsProviderGroup grp = new InsightsProviderGroup();
                    grp.setName(grpName);
                    grp.setUser_identifiers(new ArrayList<>(v.values()));
                    if (!k.equalsIgnoreCase(TSPrivileges.NONE.name())){
                        grp.setPrivileges(List.of(k,TSPrivileges.DATADOWNLOADING.name(),TSPrivileges.SHAREWITHALL.name(),
                                TSPrivileges.PREVIEW_THOUGHTSPOT_SAGE.name(), TSPrivileges.JOBSCHEDULING.name()));
                    }
                    grp.setDisplay_name(grpName);
                    tsService.createGroup(grp, Optional.of(TSService.TS_ADMIN_USER),groupTokenHeaders);
                });
            });
            Map<String, String> userIdsMap = groupUsersMap.get(TSPrivileges.DATAMANAGEMENT.name());
            log.info("userIds size is {}", MapUtils.isNotEmpty(userIdsMap) ? userIdsMap.size() : 0);
            Instance instance = SyncariContext.getInstance();
            // Use TS Admin to create connection and then share with the group
            Optional<String> conId = createOrUpdateConnection(instance.getName(), Optional.of(TSService.TS_ADMIN_USER), true);
            conId.ifPresent(id -> this.shareWithDMGroup(List.of(id),Optional.empty(),true));
            datasetSchemaService.createDatasetsFromAllEntities(Optional.ofNullable(TSService.TS_ADMIN_USER));
            migrateExistingDatasetsToV2();
            log.error("Provision TS Organization is successful for instance {}. done by user {}",SyncariContext.getSyncariId(), SyncariContext.getUser().getId());
            featureService.activateFeature(Features.InsightsProvider);
        }catch (Exception e){
            log.error("Provision TS Organization was not successful. Error : ", e);
            return false;
        }
        return true;
    }

    private String getPermission(Set<String> roles){
        return roles.contains(RoleConstants.ORG_ADMIN) || roles.contains(RoleConstants.INSTANCE_ADMIN) || roles.contains(RoleConstants.SYNC_MANAGER) || roles.contains(RoleConstants.DASHBOARD_AUTHOR)  ?  TSPrivileges.DATAMANAGEMENT.name() : TSPrivileges.NONE.name();
    }

    private void migrateExistingDatasetsToV2(){
        List<Dataset> datasets = datasetService.getAllApprovedDatasetsWithVersion();
        List<Dataset> v1Datasets = datasets.stream().filter(d -> d.getVersion().equalsIgnoreCase("v1") && !d.isSeeded()).collect(Collectors.toList());
        v1Datasets.forEach(ds -> {
            try{
                if (StringUtils.isEmpty(ds.getInsightsProviderId())){
                    log.info("Dataset getting created in TS is {} with display name {}, ", ds.getId(), ds.getDisplayName());
                    datasetService.createOrUpdateDatasetInInsightsProvider(ds, true);
                }else{
                    log.info("Dataset getting updated in TS is {} with display name {}, type {} and insights provider id {} ",
                            ds.getId(), ds.getDisplayName(), ds.getDatasetType(),ds.getInsightsProviderId());
                    datasetService.createOrUpdateDatasetInInsightsProvider(ds, false);
                }
                datasetService.updateDataset(ds.getId(),ds);
            }catch (Exception e){
                log.error("Exception occurred while migrating dataset {} with name {} from old to new {}",ds.getId(), ds.getName(),e );
            }
        });

        List<Dataset> v2Datasets = datasets.stream().filter(d -> d.getVersion().equalsIgnoreCase("v2") && !d.isSeeded()).collect(Collectors.toList());
        v2Datasets.forEach(ds -> {
            try{
                if (StringUtils.isEmpty(ds.getInsightsProviderId())){
                    log.info("Dataset getting created in TS is {} with display name {}, ", ds.getId(), ds.getDisplayName());
                    datasetService.createOrUpdateDatasetInInsightsProvider(ds, true);
                }else{
                    log.info("Dataset getting updated in TS is {} with display name {}, type {} and insights provider id {} ",
                            ds.getId(), ds.getDisplayName(), ds.getDatasetType(),ds.getInsightsProviderId());
                    datasetService.createOrUpdateDatasetInInsightsProvider(ds, false);
                }
                datasetService.updateDataset(ds.getId(),ds);
            }catch (Exception e){
                log.error("Exception occurred while migrating dataset v2 id {} and name {} from old to new {} ",ds.getId(),ds.getName(),e );
            }

        });

    }

    public void provisionBQforDFI(){
        if (!featureService.isEnabled(Features.InsightsProvider)) return;

        refreshDatastoreTokensIfNeeded();

        // Todo : Check DFI feature flag, if not enabled then return
        // Create BQ Connection
        // Seed DFI Liveboard
        // Share liveboard with all groups so that they can view it only, no update
        Instance instance = SyncariContext.getInstance();
        String connectionName = String.format(BQ_CONNECTION_NAME_FORMAT, SyncariContext.getInstance().getName());
        Optional<String> conId = createBQConnection(instance.getName(), Optional.of(TSService.TS_ADMIN_USER), true);
        conId.ifPresentOrElse(c -> {
            String envName = appConfig.getEnvName();
            String liveboardName;
            if (StringUtils.isNotEmpty(envName) && envName.equalsIgnoreCase("non-prod")){
                liveboardName = String.format(DFI_SYSTEM_SEEDED_LIVEBOARD_NAME_NONPROD, instance.getName());
            }else{
                liveboardName = DFI_SYSTEM_SEEDED_LIVEBOARD_NAME;
            }
            HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),300L); // Creating 10 mins token, assuming 20 mins provisioning would finish
            // keep the name as connection fo search as we are using name as identifier during search
            InsightsProviderConnection connection = new InsightsProviderConnection().setName(c).setConnection_identifier(c).setInclude_details(true);
            Optional<TSConnResponse> connResponse = tsService.searchConnectionV2(connection,Optional.of(TSService.TS_ADMIN_USER),headers);
            connResponse.ifPresentOrElse(resp -> {
                TSConnectionDetail tsConnectionDetail  = resp.getDetails();
                if (null != tsConnectionDetail){
                    Map<String, String> fqnsMap = new HashMap<>();
                    tsConnectionDetail.getTables().forEach(t -> {
                        fqnsMap.put(t.getName(), t.getId());
                    });
                    TSMetadataSearchReq req = new TSMetadataSearchReq();
                    req.setMetadata(List.of(new TSMetadataListItemInput().setType(TSMetadataType.LIVEBOARD.name()).setIdentifier(liveboardName)));
                    req.setInclude_headers(true);
                    List<TSMetadataSearchResponse> responseList = tsService.searchMetadata(req,Optional.of(TSService.TS_ADMIN_USER),headers);
                    if (CollectionUtils.isNotEmpty(responseList) && responseList.get(0).getMetadata_name().equalsIgnoreCase(liveboardName)){
                        log.info("Seeded dashboard with name {} already exists, Not seeding again", liveboardName);
                        return;
                    }
                    String seededLiveBoardId = tsService.seedSystemLiveboard(liveboardName,fqnsMap,headers);
                    shareWithDMGroup(List.of(seededLiveBoardId), Optional.of("LIVEBOARD"), false);
                    shareWithNoneGroup(List.of(seededLiveBoardId), Optional.of("LIVEBOARD"));
                }else{
                    log.error("Could not find Connection details with BQ in TS, Provisioning of DFI report in Insights failed");
                    throw new RuntimeException("Could not find Connection details with BQ in TS, Provisioning of DFI report in Insights failed for syncariId " + instance.getSyncariId());
                }
            },() -> {
                log.error("Could not find connection with BQ, Provisioning of DFI report in Insights failed");
                throw new RuntimeException("Could not find connection with BQ, Provisioning of DFI report in Insights failed for syncariId " + instance.getSyncariId());
            });
        },() -> {
            log.error("Could not create connection with BQ, Provisioning of DFI report in Insights failed");
            throw new RuntimeException("Could not create connection with BQ, Provisioning of DFI report in Insights failed for syncariId " + instance.getSyncariId());
        });


    }

    public Map<String, Object> createConnectionConfig(ConnectorInfo connectorInfo, String orgName) {
        return DatastoreConnectionHelper.createConnectionConfig(connectorInfo, orgName, appConfig);
    }

    public Optional<String> createOrUpdateConnection(String orgName, Optional<String> username, boolean isCreate){
        refreshDatastoreTokensIfNeeded();

        Optional<Connector> connector = datastoreService.findActiveDatastore();
        ConnectorInfo connectorInfo = datastoreService.toConnectorInfo(connector);
        if (null == connectorInfo){
            throw new SyncariValidationException("Active datastore connector is not present");
        }

        Map<String, Object> data_warehouse_config = new HashMap<>();
        Map<String, Object> dbConfig = new HashMap<>();

        String metadataName = connectorInfo.getConnectorMetadataName();
        String dbName;
        String host;
        String port;

        if (Constants.SNOWFLAKE_DATASTORE.equals(metadataName)) {
            String endpoint = (String)connectorInfo.getMetaConfig().get("endpoint");
            String accountName = (String)connectorInfo.getMetaConfig().get("accountName");
            dbName = (String)connectorInfo.getMetaConfig().get("dbName");

            if (endpoint.startsWith("https://")) {
                endpoint = endpoint.substring(8);
            }
            if (endpoint.endsWith("/")) {
                endpoint = endpoint.substring(0, endpoint.length() - 1);
            }
            host = endpoint;
            port = "443";

            dbConfig.put("accountName", accountName != null ? accountName : orgName);
            String warehouseName = (String)connectorInfo.getMetaConfig().get("warehouseName");
            if (warehouseName != null) {
                dbConfig.put("warehouse", warehouseName);
            }
            String role = (String)connectorInfo.getMetaConfig().get("role");
            if (role != null) {
                dbConfig.put("role", role);
            }
        } else {
            port = (String)connectorInfo.getMetaConfig().get("port");
            dbName = (String)connectorInfo.getMetaConfig().get("dbName");
            String clusterName = (String)connectorInfo.getMetaConfig().get("clusterName");
            String [] hostParts = clusterName != null ? clusterName.split(":") : new String[]{};
            host = hostParts.length > 0 ? hostParts[0] : null;
            dbConfig.put("accountName", orgName);
        }

        String authType = connectorInfo.getMetaConfig().getOrDefault("authType", AuthType.UserPasswordToken.toString()).toString();
        if (authType.equalsIgnoreCase(AuthType.Oauth.toString())) {
            dbConfig.put("authenticator", "oauth");
            dbConfig.put("token", connectorInfo.getAuthConfig().getAccessToken());
        } else {
            dbConfig.put("user", connectorInfo.getAuthConfig().getUserName());
            dbConfig.put("password", connectorInfo.getAuthConfig().getPassword());
        }
        if (Constants.DATASTORE.equals(metadataName)) {
            String envName = appConfig.getEnvName();
            if (StringUtils.isNotEmpty(envName) && envName.equalsIgnoreCase("non-prod")) {
                dbConfig.put("host", host);
            } else {
                dbConfig.put("host", appConfig.getDatastorePublicHost());
            }
        } else {
            dbConfig.put("host", host);
        }
        dbConfig.put("port", port);
        dbConfig.put("database", dbName);

        String schemaName = (String)connectorInfo.getMetaConfig().get("schemaName");
        if (schemaName != null) {
            dbConfig.put("schema", schemaName);
        }

        data_warehouse_config.put("configuration",dbConfig);

        List<Map<String, Object>> externalDbs = new ArrayList<>();
        Map<String, Object> externalDb = new HashMap<>();
        externalDb.put("name",schemaName);
        externalDb.put("isAutoCreated",false);

        List<TSTable> tables = new ArrayList<>();
        Map<String, List<Map<String, String>>> tablesWithColumnInfo = getSchemaMetadataForDatastore(connectorInfo);
        tablesWithColumnInfo.forEach((k,v) -> {
            List<TSColumn> columns = new ArrayList<>();
            TSTable table = new TSTable();
            table.setName(k);
            table.setDescription(k);
            v.forEach(val -> {
                TSColumn tsColumn = new TSColumn();
                tsColumn.setName(val.get("columnName"));
                String dataType = mapDataType(val.get("columnType"));
                tsColumn.setType(dataType);
                tsColumn.setDbName(dbName);
                tsColumn.setSchemaName(schemaName);
                tsColumn.setTableName(k);
                columns.add(tsColumn);
            });
            table.setColumns(columns);
            tables.add(table);
        });
        InsightsProviderConnection connection = new InsightsProviderConnection();

        String dataWarehouseType = getThoughtSpotDataWarehouseType(connectorInfo);
        if (dataWarehouseType != null) {
            connection.setData_warehouse_type(dataWarehouseType);
        }

        if (CollectionUtils.isNotEmpty(tables)){
            TSSchema schema = new TSSchema();
            schema.setName(schemaName);
            schema.setTables(tables);
            externalDb.put("schemas",List.of(schema));
            externalDbs.add(externalDb);
            data_warehouse_config.put("externalDatabases", externalDbs);
            connection.setValidate(true);
        }
        String instanceId = SyncariContext.getSyncariId();
        connection.setName(String.format(CONNECTION_NAME_FORMAT, instanceId));
        connection.setData_warehouse_config(data_warehouse_config);
        HttpHeaders headers = tsService.getHeaders(username,240L); // Creating 5 mins token, for searching connection and create/update connection
        Optional<String> conId = tsService.searchConnection(connection,username,headers);
        if (!isCreate || conId.isPresent()){
            connection.setConnection_identifier(conId.get());
            tsService.updateConnection(connection, username,headers);
            log.info("Connection updated successfully for id {}", conId.get());
            return conId;
        }else{
            String connectionId = tsService.createConnection(connection,username,headers);
            log.info("Connection created successfully with id {}", connectionId);
            return Optional.of(connectionId);
        }
    }

    public Optional<String> createBQConnection(String orgName, Optional<String> username, boolean isCreate){
        refreshDatastoreTokensIfNeeded();

        InsightsProviderConnection connection = new InsightsProviderConnection();
        connection.setValidate(false);
        connection.setData_warehouse_type("GOOGLE_BIGQUERY");

        Map<String, Object> data_warehouse_config = new HashMap<>();
        Map<String, Object> dbConfig = new HashMap<>();
        String schemaName = SyncariContext.getSyncariId();

        dbConfig.put("project_id",appConfig.getGcpProjectId());
        dbConfig.put("oauth_pvt_key",new String(Base64.getDecoder().decode(appConfig.getTsBqSaKey())));
        dbConfig.put("schema",schemaName);
        data_warehouse_config.put("configuration",dbConfig);

        List<Map<String, Object>> externalDbs = new ArrayList<>();
        Map<String, Object> externalDb = new HashMap<>();
        externalDb.put("name",appConfig.getGcpProjectId());
        externalDb.put("isAutoCreated",false);
        List<String> viewNames = List.of("dfiCurrentScoreByEntity","dfiCurrentScoreByEntityAndCategory","dfiOverallScoreByCategory"
                ,"dfiOverallScoreByTime","dfiOverallScoreByTimeAndCategory","dfiScoreOverTimeByEntity"
                ,"dfiScoreOverTimeByEntityAndCategory","dfiScoreOverTimeByEntityAndRule");

        List<TSTable> tables = new ArrayList<>();
        viewNames.forEach(view -> {
            MaterializedViewDefinition viewDefinition = bigQueryHelper.getViewDefinition(view, schemaName);
            Schema schemaBq = viewDefinition.getSchema();
            List<TSColumn> columns = new ArrayList<>();
            TSTable table = new TSTable();
            table.setName(view);
            table.setDescription(view);
            schemaBq.getFields().forEach(f -> {
                TSColumn tsColumn = new TSColumn();
                tsColumn.setName(f.getName());
                String dataType = mapDataType(f.getType().name());
                tsColumn.setType(dataType);
                tsColumn.setSchemaName(schemaName);
                tsColumn.setSelected(true);
                tsColumn.setCanImport(true);
                columns.add(tsColumn);
            });
            table.setColumns(columns);
            tables.add(table);
        });

        if (CollectionUtils.isNotEmpty(tables)){
            TSSchema tsSchema = new TSSchema();
            tsSchema.setName(schemaName);
            tsSchema.setTables(tables);
            externalDb.put("schemas",List.of(tsSchema));
            externalDbs.add(externalDb);
            data_warehouse_config.put("externalDatabases", externalDbs);
            connection.setValidate(true);
        }
        connection.setName(String.format(BQ_CONNECTION_NAME_FORMAT, SyncariContext.getSyncariId()));
        connection.setData_warehouse_config(data_warehouse_config);
        HttpHeaders headers = tsService.getHeaders(username,240L); // Creating 5 mins token, for searching connection and create/update connection
        Optional<String> conId = tsService.searchConnection(connection,username,headers);
        if (!isCreate || conId.isPresent()){
            connection.setConnection_identifier(conId.get());
            tsService.updateConnection(connection, username,headers);
            log.info("BQ Connection updated successfully for id {}", conId.get());
            return conId;
        }else{
            String connectionId = tsService.createConnection(connection,username,headers);
            log.info("BQ Connection created successfully with id {}", connectionId);
            return Optional.of(connectionId);
        }
    }

    public void deleteConnectionForCurrentInstance(){
        if (!featureService.isEnabled(Features.InsightsProvider)) return;

        refreshDatastoreTokensIfNeeded();

        String instanceId = SyncariContext.getSyncariId();
        String connectionName = String.format(CONNECTION_NAME_FORMAT, instanceId); // This is one per instance only
        InsightsProviderConnection providerConnection = new InsightsProviderConnection();
        providerConnection.setConnection_identifier(connectionName);
        HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),120L);
        tsService.deleteConnection(providerConnection, Optional.of(TSService.TS_ADMIN_USER),headers);
    }

    public void assignGroupForRoles(Set<String> roles, User user, String instanceId){
        if (!featureService.isEnabled(Features.InsightsProvider)) return;

        refreshDatastoreTokensIfNeeded();

        String providerUserName = user.getInsightsProviderUserName();
        if (null != providerUserName){
            HttpHeaders tsAdminHeaders = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),60L);
            Optional<TSUserResponse> tsUserResponseTSAdmin = tsService.searchUser(new InsightsProviderUser().setName(providerUserName),Optional.of("tsadmin"),false,tsAdminHeaders);
            tsUserResponseTSAdmin.ifPresentOrElse(tsUserResponse -> {
                Map<String, Map<String,String>> groupUsersMap = new HashMap<>();
                String permission = getPermission(roles);
                addToMap(groupUsersMap,user.getId(), tsUserResponse.getId(), permission);
                // add user to groupName
                groupUsersMap.forEach((k,v) -> {
                    // group naming convention based on current instanceid
                    String grpName = instanceId + "_" + k;
                    Optional<TSGrpResponse> grpResponse = tsService.searchGroup(grpName, Optional.of(TSService.TS_ADMIN_USER),tsAdminHeaders);
                    log.info("Group name is {}", grpName);
                    grpResponse.ifPresentOrElse(resp -> {
                        log.info("Group name {} already exists, add users to group", grpName);
                        tsService.addOrRemoveUserToGroup(grpName,v.values().stream().collect(Collectors.toList()),Optional.of(TSService.TS_ADMIN_USER),tsAdminHeaders, GroupOperation.ADD);
                    },()-> log.error("Group with name {} does not exists", grpName));
                });
            },() -> log.info("User with id {} does not exists to update group permission {}", user.getId(), getPermission(roles)));
        }else{
            // this creates a user in TS and assign respective role in TS.
            if (StringUtils.isEmpty(user.getInsightsProviderUserId())){
                this.createUserByAdmin(user);
            }
        }
    }

    private String mapDataType(String dataType){
        switch (dataType.toLowerCase()) {
            case "boolean":
            case "bool":
            case "tinyint":
                return "BOOL";
            case "long":
            case "double":
            case "real":
            case "float":
            case "numeric":
            case "number":
            case "decimal":
            case "binary_float":
            case "binary_double":
                return "DOUBLE";
            case "timestamp":
            case "timestamp with time zone":
            case "timestamp without time zone":
            case "timestamp with local time zone":
            case "timestamp_ltz":
            case "timestamp_ntz":
            case "timestampntz":
            case "timestamp_tz":
            case "timestamptz":
            case "timetz":
            case "datetime":
                return "DATE_TIME";
            case "integer":
            case "bigint":
            case "int":
            case "int8":
            case "smallint":
                return "INT64";
            case "int4":
                return "INT32";
            case "date":
                return "DATE";
            case "string":
            case "varchar":
            case "text":
                return "VARCHAR";
            default:
                log.debug("Data type not found in mapping is {}",dataType.toUpperCase());
                return dataType.toUpperCase();
        }
    }

    public void deleteUserById(String providerUserId, Optional<String> tsUserName){
        if (!featureService.isEnabled(Features.InsightsProvider)){
            return;
        }

        refreshDatastoreTokensIfNeeded();

        HttpHeaders headers = tsService.getHeaders(tsUserName,60L);
        tsService.deleteUser(providerUserId,tsUserName,headers);
    }

    public void createUserByAdmin(User user){
        if (featureService.isEnabled(Features.InsightsProvider)){
            refreshDatastoreTokensIfNeeded();

            log.info("Creating user with email {} in Insights Provider", user.getEmail());
            InsightsProviderUser insightsProviderUser = new InsightsProviderUser();
            insightsProviderUser.setName(user.getId());
            insightsProviderUser.setDisplay_name(user.getId());
            insightsProviderUser.setEmail(user.getId() + "@syncari.io");
            insightsProviderUser.setPassword("hardcoded@1");
            HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),60L);
            TSUserResponse tsUserResponse = tsService.createUser(insightsProviderUser, Optional.of(TSService.TS_ADMIN_USER),headers);
            if ((tsUserResponse != null) && StringUtils.isNotEmpty(tsUserResponse.getId())){
                user.setInsightsProviderUserId(tsUserResponse.getId());
                user.setInsightsProviderUserName(user.getId());
                userService.saveUser(user);
                addUserToGroupAndCurrentOrg(user);
            }else{
                log.error("User {} is not created in TS, error logged before this log",user.getId());
            }
        }
    }

    public Map<String, String> listLiveboards(){
        if (featureService.isEnabled(Features.InsightsProvider)){
            refreshDatastoreTokensIfNeeded();

            TSMetadataSearchReq req = new TSMetadataSearchReq();
            TSSearchMetadataSort sortOption = new TSSearchMetadataSort().setField_name("LAST_ACCESSED");
            req.setMetadata(List.of(new TSMetadataListItemInput().setType(TSMetadataType.LIVEBOARD.name())));
            req.setInclude_headers(true);
            req.setSort_options(sortOption);
            User u = SyncariContext.getUser();
            String expectedGrpName = this.getExpectedGroupName();
            HttpHeaders headers = tsService.getHeaders(Optional.of(u.getInsightsProviderUserName()),60L);
            List<TSMetadataSearchResponse> responseList = tsService.searchMetadata(req, Optional.of(u.getInsightsProviderUserName()),headers)
                    .stream().filter(m -> !((String)m.getMetadata_header().get("authorName")).equalsIgnoreCase("system")).collect(Collectors.toList());
            return responseList.stream().collect(Collectors.toMap(k -> k.getMetadata_name(), v -> v.getMetadata_id() ));
        }
        return Map.of();
    }

    public void changeOwnerToTSAdmin(List<String> metadataIds, Optional<String> metadataType){
        if (CollectionUtils.isEmpty(metadataIds)) return;

        refreshDatastoreTokensIfNeeded();

        List<TSMetadataListItemInput> listMetadatas = new ArrayList<>();
        TSChangeOwnerRequest request = new TSChangeOwnerRequest();
        HttpHeaders tsAdminHeaders = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),60L);
        Optional<TSUserResponse> tsUserResponseTSAdmin = tsService.searchUser(new InsightsProviderUser().setName(TSService.TS_ADMIN_USER),Optional.of("tsadmin"),true,tsAdminHeaders);
        tsUserResponseTSAdmin.ifPresent(ts -> {
            metadataIds.forEach(id -> {
                TSMetadataListItemInput input = new TSMetadataListItemInput().setIdentifier(id);
                metadataType.ifPresent(t -> input.setType(t));
                listMetadatas.add(input);
            });
            request.setUser_identifier(ts.getId());
            request.setMetadata(listMetadatas);
            tsService.changeOwnerMetadata(request,Optional.of(TSService.TS_ADMIN_USER),tsAdminHeaders);
        });
    }


    public void addUserToGroupAndCurrentOrg(User user){
        // Look for current user in current org all groups
        if (featureService.isEnabled(Features.InsightsProvider)){
            refreshDatastoreTokensIfNeeded();

            String providerUserName = user.getInsightsProviderUserName();
            HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),240L);
            Optional<TSUserResponse> tsUserResponse = tsService.searchUser(new InsightsProviderUser().setName(providerUserName).setEmail(providerUserName + "@syncari.io"),Optional.of(TSService.TS_ADMIN_USER),true,headers);
            Set<String> userRoles = userService.getUserRolesForCurrentInstance(user.getId());
            String permission = getPermission(userRoles);

            // Add user to org before addition to group
            Organization currentOrg = SyncariContext.getOrganziation();
            tsUserResponse.ifPresent(tsResp -> {
                List<Org> existingUserOrgs = tsResp.getOrgs();
                List<String> existingUserOrgIds = existingUserOrgs.stream().map(o -> o.getId()).collect(Collectors.toList());
                if (CollectionUtils.isEmpty(existingUserOrgIds) || (CollectionUtils.isNotEmpty(existingUserOrgIds) && !existingUserOrgIds.contains(currentOrg.getInsightsProviderOrgId()))){
                    InsightsProviderUser insightsProviderUser = new InsightsProviderUser();
                    insightsProviderUser.setName(StringUtils.isNotEmpty(user.getInsightsProviderUserName()) ? user.getInsightsProviderUserName() : user.getId());
                    insightsProviderUser.setUser_identifier(tsResp.getId());
                    insightsProviderUser.setOperation("ADD");
                    insightsProviderUser.setOrg_identifiers(List.of(currentOrg.getInsightsProviderOrgId()));
                    tsService.updateUser(insightsProviderUser, Optional.of(TSService.TS_ADMIN_USER), true,headers);
                }
            });

            String expectedGroupName = this.getExpectedGroupName();
            Optional<TSGrpResponse> grpResponse = tsService.searchGroup(expectedGroupName, Optional.of(TSService.TS_ADMIN_USER),headers);
            grpResponse.ifPresentOrElse(g -> {
                tsUserResponse.ifPresent(tsResp -> {
                    List<String> groupUserNames =  g.getUsers().stream().map(u -> u.getName()).collect(Collectors.toList());
                    if ((CollectionUtils.isEmpty(groupUserNames)) || ((CollectionUtils.isNotEmpty(groupUserNames)) && (!groupUserNames.contains(user.getInsightsProviderUserName())))){
                        log.info("Adding username {} to group {}", providerUserName, expectedGroupName);
                        String userId = StringUtils.isNotEmpty(tsResp.getId()) ? tsResp.getId() : StringUtils.isNotEmpty(user.getInsightsProviderUserName()) ? user.getInsightsProviderUserName() : user.getId();
                        tsService.addOrRemoveUserToGroup(expectedGroupName,List.of(userId),Optional.of(TSService.TS_ADMIN_USER),headers, GroupOperation.ADD);
                    }
                });
            },() -> {
                tsUserResponse.ifPresent(tsResp -> {
                    InsightsProviderGroup grp = new InsightsProviderGroup();
                    // group naming convention based on current instanceid
                    grp.setName(expectedGroupName);
                    grp.setUser_identifiers(List.of(tsResp.getId()));
                    if (!permission.equalsIgnoreCase(TSPrivileges.NONE.name())){
                        grp.setPrivileges(List.of(permission));
                    }
                    grp.setDisplay_name(expectedGroupName);
                    tsService.createGroup(grp, Optional.of(TSService.TS_ADMIN_USER),headers);
                });
            });
        }
    }


    private Map<String, Map<String, String>> addToMap(Map<String, Map<String, String>> groupUsersMap, String userId,String providerUserId, String permission){
        if (null == groupUsersMap){
            groupUsersMap = new HashMap<>();
        }
        if (groupUsersMap.containsKey(permission)){
            Map<String, String> userIdsMap = groupUsersMap.get(permission);
            userIdsMap.put(userId,providerUserId);
            groupUsersMap.put(permission, userIdsMap);
        }else{
            Map<String, String>  userIdsMap = new HashMap<>();
            userIdsMap.put(userId,providerUserId);
            groupUsersMap.put(permission,userIdsMap);
        }
        return groupUsersMap;
    }

    public void shareWithDMGroup(List<String> metadataIds, Optional<String> metadataType, boolean isModify){
        if (CollectionUtils.isEmpty(metadataIds)) return;

        refreshDatastoreTokensIfNeeded();

        String syncariId = SyncariContext.getSyncariId();
        String firstGroupName = syncariId + "_DATAMANAGEMENT";
        HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),180L);
        List<TSPermission> permissions = new ArrayList<>();
        Optional<TSGrpResponse> firstGroupResponse = tsService.searchGroup(firstGroupName, Optional.of(TSService.TS_ADMIN_USER),headers);

        firstGroupResponse.ifPresent(r -> {
            log.info("Group getting shared id is {}", r.getId());
            TSPrincipalInput principalInput = new TSPrincipalInput().setIdentifier(r.getId());
            String firstGroupAccess = isModify ? "MODIFY" : "READ_ONLY";
            TSPermission perm = new TSPermission().setPrincipal(principalInput);
            perm.setShare_mode(firstGroupAccess);
            permissions.add(perm);
        });

        if (CollectionUtils.isNotEmpty(permissions)){
            TSMetadataShareRequest requestToShare = new TSMetadataShareRequest();
            requestToShare.setMetadata_identifiers(metadataIds);
            requestToShare.setPermissions(permissions);
            metadataType.ifPresent(type -> requestToShare.setMetadata_type(type));
            List<String> emails = List.of("random@syncari.io");
            requestToShare.setEmails(emails);
            tsService.shareMetadata(requestToShare, Optional.of(TSService.TS_ADMIN_USER),headers);
        }

    }

    public void shareWithNoneGroup(List<String> metadataIds, Optional<String> metadataType){
        if (CollectionUtils.isEmpty(metadataIds)) return;

        refreshDatastoreTokensIfNeeded();

        String syncariId = SyncariContext.getSyncariId();
        String grpName = syncariId + "_NONE";
        HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),180L);

        List<TSPermission> permissions = new ArrayList<>();
        Optional<TSGrpResponse> firstGroupResponse = tsService.searchGroup(grpName, Optional.of(TSService.TS_ADMIN_USER),headers);

        firstGroupResponse.ifPresent(r -> {
            log.info("Group getting shared id is {}", r.getId());
            TSPrincipalInput principalInput = new TSPrincipalInput().setIdentifier(r.getId());
            String firstGroupAccess = "READ_ONLY";
            TSPermission perm = new TSPermission().setPrincipal(principalInput);
            perm.setShare_mode(firstGroupAccess);
            permissions.add(perm);
        });

        if (CollectionUtils.isNotEmpty(permissions)){
            TSMetadataShareRequest requestToShare = new TSMetadataShareRequest();
            requestToShare.setMetadata_identifiers(metadataIds);
            requestToShare.setPermissions(permissions);
            metadataType.ifPresent(type -> requestToShare.setMetadata_type(type));
            List<String> emails = List.of("random@syncari.io");
            requestToShare.setEmails(emails);
            tsService.shareMetadata(requestToShare, Optional.of(TSService.TS_ADMIN_USER),headers);
        }
    }

    // search groups for given user which contains that user
    // remove user from all groups except current syncariid group
    // need to do this for logged in user
    public void removeUserFromGroups(String insightsProviderUserId){
        if (!featureService.isEnabled(Features.InsightsProvider)) return;

        refreshDatastoreTokensIfNeeded();

        HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),180L);
        List<TSGrpResponse> groups = tsService.searchLocalGroupsForAUser(insightsProviderUserId, Optional.of(TSService.TS_ADMIN_USER),headers);
        List<TSGrpResponse> filteredGroups = groups.stream().filter(g ->
                !g.getName().startsWith(SyncariContext.getSyncariId())  && !g.isSystem_group() && !g.isDeleted()
        && !"Demo Retail Group".equalsIgnoreCase(g.getName())).collect(Collectors.toList());
        filteredGroups.forEach(grp -> tsService.addOrRemoveUserToGroup(grp.getName(),List.of(insightsProviderUserId),
                Optional.of(TSService.TS_ADMIN_USER),headers,GroupOperation.REMOVE));
    }

    public void shareUnshareAllMetadataWithUser(boolean isShare){
        if (!featureService.isEnabled(Features.InsightsProvider)){
            return;
        }

        refreshDatastoreTokensIfNeeded();

        // Get all datasets and its TS metadataid
        // Build Sharemetadata request
        // Assume group already exist with naming convention SyncariId_ADMINISTRATION
        List<Dataset> datasets = datasetService.getAllApprovedDatasetsWithVersion();
        // Filter only SQL View
        List<Dataset> workSheetDatasets = datasets.stream().filter(d ->  d.getDatasetType().equals(Dataset.DatasetType.TABLE)
                || d.getDatasetType().equals(Dataset.DatasetType.WORKSHEET)).collect(Collectors.toList());
        List<String> providerMetadataIds = new ArrayList<>();
        User u = SyncariContext.getUser();
        workSheetDatasets.forEach(d -> {
            String providerMetadataId = d.getInsightsProviderId();
            if (StringUtils.isNotEmpty(providerMetadataId)){
                providerMetadataIds.add(providerMetadataId);
            }
        });
        String expectedGroupName = getExpectedGroupName();
        List<String> emails = new ArrayList<>();
        List<TSPermission> permissions = new ArrayList<>();
        if (StringUtils.isNotEmpty(u.getInsightsProviderUserName())){
            Set<String> roles = userService.getUserRolesForCurrentInstance(u.getId());
            String expectedGrpName = this.getExpectedGroupName();
            HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),180L);
            Optional<TSGrpResponse> resp = tsService.searchGroup(expectedGroupName, Optional.of(TSService.TS_ADMIN_USER),headers);
            resp.ifPresent(r -> {
                log.info("Group getting shared id is {}", r.getId());
                TSPrincipalInput principalInput = new TSPrincipalInput().setIdentifier(r.getId());
                String access = isShare ? roles.contains(RoleConstants.ORG_ADMIN) || roles.contains(RoleConstants.INSTANCE_ADMIN) || roles.contains(RoleConstants.SYNC_MANAGER) || roles.contains(RoleConstants.DASHBOARD_AUTHOR) ?
                        "MODIFY" : "READ_ONLY" : "NO_ACCESS";
                TSPermission perm = new TSPermission().setPrincipal(principalInput);
                perm.setShare_mode(access);
                permissions.add(perm);
            });
            String principalId = u.getInsightsProviderUserId();
            log.info("User getting shared id is {}, with syncari email {}", principalId, u.getEmail());
            TSPrincipalInput principalInput = new TSPrincipalInput().setIdentifier(principalId).setType(TSMetadataType.USER.name());
            String access = isShare ? roles.contains(RoleConstants.ORG_ADMIN) || roles.contains(RoleConstants.INSTANCE_ADMIN) || roles.contains(RoleConstants.SYNC_MANAGER) || roles.contains(RoleConstants.DASHBOARD_AUTHOR) ?
                    "MODIFY" : "READ_ONLY" : "NO_ACCESS";
            emails.add(u.getId() + "@syncari.io");
            TSPermission perm = new TSPermission().setPrincipal(principalInput);
            perm.setShare_mode(access);
            permissions.add(perm);
            TSMetadataShareRequest requestToShare = new TSMetadataShareRequest();
            requestToShare.setMetadata_identifiers(providerMetadataIds);
            requestToShare.setPermissions(permissions);
            requestToShare.setEmails(emails);
            tsService.shareMetadata(requestToShare, Optional.of(TSService.TS_ADMIN_USER),headers);
        }

    }

    public String getExpectedGroupName(){
        User u = SyncariContext.getUser();
        String syncariId = SyncariContext.getSyncariId();
        String permission;
        if (u.isGhostUser() || u.isSuperAdmin()){
            permission =  TSPrivileges.DATAMANAGEMENT.name();
        }else{
            Set<String> userRoles = userService.getUserRolesForCurrentInstance(u.getId());
            permission = getPermission(userRoles);
        }
        return syncariId + "_" + permission;
    }

    private Map<String, List<Map<String, String>>> getSchemaMetadataForDatastore(ConnectorInfo connectorInfo) {
        String metadataName = connectorInfo.getConnectorMetadataName();
        String host = connectorInfo.getMetaConfig().get("endpoint") != null ?
            (String)connectorInfo.getMetaConfig().get("endpoint") :
            (String)connectorInfo.getMetaConfig().get("clusterName");

        log.info("Getting schema metadata for datastore - ConnectorName: {}, MetadataName: {}, Host: {}",
            connectorInfo.getName(), metadataName, host);

        if (Constants.POSTGRESQL_DATASTORE.equals(metadataName)) {
            return postgresqlDatastoreService.getSchemaMetadata(connectorInfo);
        } else if (Constants.SNOWFLAKE_DATASTORE.equals(metadataName)) {
            return snowflakeDatastoreService.getSchemaMetadata(connectorInfo);
        } else if ("datastore".equals(metadataName)) {
            // Handle internal Syncari datastore which is PostgreSQL-based
            log.info("Syncari internal datastore detected, using PostgreSQL service");
            return postgresqlDatastoreService.getSchemaMetadata(connectorInfo);
        } else {
            throw new RuntimeException("Unknown datastore type: " + metadataName);
        }
    }

    private String getThoughtSpotDataWarehouseType(ConnectorInfo connectorInfo) {
        if (connectorInfo == null) {
            return null;
        }

        String metadataName = connectorInfo.getConnectorMetadataName();
        if (metadataName == null) {
            return null;
        }

        switch (metadataName) {
            case Constants.SNOWFLAKE_DATASTORE:
                return THOUGHTSPOT_TYPE_SNOWFLAKE;
            case Constants.POSTGRESQL_DATASTORE:
                return THOUGHTSPOT_TYPE_POSTGRES;
            case "datastore":
                // Internal Syncari datastore is PostgreSQL-based
                return THOUGHTSPOT_TYPE_POSTGRES;
            default:
                log.warn("Unknown datastore type: {}, using default POSTGRES", metadataName);
                return THOUGHTSPOT_TYPE_POSTGRES;
        }
    }

    private String cleanEndpoint(String endpoint) {
        if (endpoint == null) {
            return null;
        }

        String cleaned = endpoint;

        // Remove protocol prefix
        if (cleaned.startsWith("https://")) {
            cleaned = cleaned.substring(8);
        } else if (cleaned.startsWith("http://")) {
            cleaned = cleaned.substring(7);
        }

        // Remove trailing slash
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        return cleaned;
    }

    private void refreshDatastoreTokensIfNeeded() {
        if (datastoreService == null) {
            log.debug("DatastoreService not available - skipping token refresh");
            return;
        }

        log.info("Attempt to refreshDatastoreTokens If Needed");
        Optional<Connector> datastore = datastoreService.findActiveDatastore();
        if (datastore.isPresent() && !datastore.get().isSyncariDatastore()) {
            String authType = datastore.get().getMetaConfig()
                .getOrDefault("authType", AuthType.UserPasswordToken.toString()).toString();

            if (authType.equalsIgnoreCase(AuthType.Oauth.toString())) {
                if (shouldRefreshToken(datastore.get())) {
                    log.info("Refreshing datastore OAuth tokens before ThoughtSpot operation");

                    connectorService.refreshAuthentication(datastore.get());

                    updateTSConnectionWithFreshTokens(datastore.get());
                }
            }
        }
    }

    private boolean shouldRefreshToken(Connector datastore) {
        String expiresIn = datastore.getAuthConfig().getExpiresIn();
        if (StringUtils.isNotEmpty(expiresIn)) {
            try {
                long expiryTime = Long.parseLong(expiresIn) * 1000;
                long currentTime = System.currentTimeMillis();
                long timeUntilExpiry = expiryTime - currentTime;

                return timeUntilExpiry < 300000;
            } catch (NumberFormatException e) {
                log.warn("Invalid expiresIn format: {}", expiresIn);
                return true;
            }
        }

        return false;
    }

    private void updateTSConnectionWithFreshTokens(Connector datastore) {
        try {
            String connectionName = String.format(CONNECTION_NAME_FORMAT,
                                                SyncariContext.getSyncariId().toUpperCase());

            HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER), 300L);

            InsightsProviderConnection searchConnection = new InsightsProviderConnection()
                .setName(connectionName)
                .setConnection_identifier(connectionName);

            Optional<TSConnResponse> existing = tsService.searchConnectionV2(searchConnection, Optional.of(TSService.TS_ADMIN_USER), headers);

            if (existing.isPresent()) {
                ConnectorInfo connectorInfo = datastoreService.toConnectorInfo(Optional.of(datastore));
                Map<String, Object> dbConfig = createConnectionConfig(connectorInfo, SyncariContext.getOrganziation().getName());

                InsightsProviderConnection connection = new InsightsProviderConnection()
                    .setName(connectionName)
                    .setConnection_identifier(existing.get().getId())
                    .setData_warehouse_config(dbConfig);

                tsService.updateConnection(connection, Optional.of(TSService.TS_ADMIN_USER), headers);
                log.info("Successfully updated ThoughtSpot connection {} with fresh OAuth tokens", connectionName);
            } else {
                log.warn("ThoughtSpot connection {} not found for token refresh", connectionName);
            }

        } catch (Exception e) {
            log.error("Failed to update ThoughtSpot connection with fresh tokens: {}", e.getMessage(), e);
        }
    }
}
