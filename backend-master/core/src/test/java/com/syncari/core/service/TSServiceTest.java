package com.syncari.core.service;

import com.google.cloud.bigquery.MaterializedViewDefinition;
import com.google.cloud.bigquery.Schema;
import com.syncari.core.SyncariContext;
import com.syncari.core.TestConfig;
import com.syncari.core.event.store.BigQueryHelper;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.provider.InsightsProviderConnection;
import com.syncari.core.model.insights.provider.InsightsProviderGroup;
import com.syncari.core.model.insights.provider.InsightsProviderUser;
import com.syncari.core.model.insights.provider.ts.*;
import com.syncari.core.model.util.Status;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@Slf4j
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class TSServiceTest{

    @Autowired
    TSService tsService;

    @Autowired
    BigQueryHelper bigQueryHelper;

    @MockBean
    DatastoreService datastoreService;

    @Before
    public void setUp() {
        // Mock the DatastoreService to return empty Optional so token refresh is skipped
        when(datastoreService.findActiveDatastore()).thenReturn(Optional.empty());
    }

    // Do not run this, think why are u running this
    @Ignore
    @Test
    public void testOrgCreationDeletion(){
        Organization org = new Organization();
        org.setName("Test_Org");
        User user = new User();
        user.setEmail("rohit@syncari.com");
        user.setFirstName("Rohit");
        SyncariContext.setUser(user);
        HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER), 120L);
        String orgId = tsService.createOrganization(org,headers);
        org.setInsightsProviderOrgId(orgId);
        Assert.assertNotNull(orgId);
    }

    // This will provision org in Nonprod TS org
    @Ignore
    @Test
    public void testProvisioningFlow(){
        Organization org = new Organization();
        org.setName("Test_Org");
        User user = new User();
        user.setEmail("rohit+2@syncari.com");
        user.setFirstName("Rohit");
        SyncariContext.setUser(user);
        SyncariContext.setOrganziation(org);
        HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER), 300L);
        String orgId = tsService.createOrganization(org,headers);
        org.setInsightsProviderOrgId(orgId);
        InsightsProviderUser providerUser = new InsightsProviderUser();
        providerUser.setDisplay_name(user.getName());
        providerUser.setName(user.getEmail());
        providerUser.setEmail(user.getEmail());
        providerUser.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
        Optional<TSUserResponse> tsUserResponse = tsService.searchUser(providerUser, Optional.of(TSService.TS_ADMIN_USER),false,headers);
        if (!tsUserResponse.isPresent()){
            tsUserResponse = Optional.of(tsService.createUser(providerUser, Optional.of(TSService.TS_ADMIN_USER),headers));
        }
        user.setInsightsProviderUserId(tsUserResponse.get().getId());
        user.setInsightsProviderUserName(user.getEmail());
        String syncariid = "test_syncariid";

        providerUser.setOrg_identifiers(List.of(orgId));
        providerUser.setOperation("ADD");
        tsService.updateUser(providerUser, Optional.of(TSService.TS_ADMIN_USER),true,headers);

        InsightsProviderGroup group = new InsightsProviderGroup();
        group.setName(syncariid + "_ADMINISTRATOR");
        group.setDisplay_name(syncariid + "_ADMINISTRATOR");
        group.setPrivileges(List.of(TSPrivileges.DATAMANAGEMENT.name(),TSPrivileges.SHAREWITHALL.name(),TSPrivileges.DATADOWNLOADING.name(),TSPrivileges.PREVIEW_THOUGHTSPOT_SAGE.name()));
        group.setUser_identifiers(List.of(tsUserResponse.get().getId()));
        String grpId = tsService.createGroup(group,Optional.of(TSService.TS_ADMIN_USER),headers);
        Assert.assertNotNull(grpId);
        tsService.deleteGroup(grpId,Optional.of(TSService.TS_ADMIN_USER),headers);
        tsService.deleteUser(tsUserResponse.get().getId(),Optional.of(TSService.TS_ADMIN_USER),headers);
    }

    @Ignore
    @Test
    public void testConnectionCreationDeletion(){
        InsightsProviderConnection connection = new InsightsProviderConnection();
        String name = "qwak1t";
        User u = new User().setInsightsProviderUserName("nikhil+1@syncari.com").setEmail("nikhil+1@syncari.com");
        Instance ins = new Instance();
        ins.setSyncariId(name);
        Organization o = new Organization();
        o.setInsightsProviderOrgId("929251882");
        SyncariContext.runWithContext(o, ins, u, () -> {
            connection.setName(name.toUpperCase() + "_CONNECTION_TEST");
            connection.setValidate(false);
            Map<String, Object> mapt = new HashMap<>();
            mapt = new HashMap<>();
            mapt.put("configuration",Map.of("accountName", "thoughtspotpoc", "password","!SyncariDemo12#","port","5432"
                    ,"host","35.230.89.186","user", "demo","database","syncari_"+name));
            mapt.put("externalDatabases",List.of());
            connection.setData_warehouse_config(mapt);
            HttpHeaders headers = tsService.getHeaders(Optional.of("nikhil+1@syncari.com"),300L);
            String connectionId = tsService.createConnection(connection,Optional.of("nikhil+1@syncari.com"),headers);
            connection.setConnection_identifier(connectionId);
            tsService.updateConnection(connection,Optional.of("nikhil+1@syncari.com"),headers);
            tsService.deleteConnection(connection, Optional.of("nikhil+1@syncari.com"),headers);
        });
    }

    @Ignore
    @Test
    public void testDatasetCreationDeletion(){
        Instance i = new Instance();
        i.setSyncariId("qwak1t");
        i.setStatus(Status.ACTIVE);
        i.setDisplayName("THOUGHTSPOTPOC");
        i.setName("thoughtspotpoc");
        Organization org = new Organization();
        org.setInsightsProviderOrgId("929251882");
        User u = new User().setInsightsProviderUserName("nikhil+1@syncari.com");

        SyncariContext.runWithContext(org, i, u, () -> {
            String conName = SyncariContext.getSyncariId().toUpperCase() + "_CONNECTION";
            HttpHeaders headers = tsService.getHeaders(Optional.of("nikhil+1@syncari.com"),300L);
            Dataset dataset1 = new Dataset().setName("testdataset2").setRawQuery("SELECT amount, closedate, isclosed FROM syncari_qwak1t.\"opportunity__c\" \"Opportunity\" group by");
            try{
                Map<String, String> dsMap1 = tsService.createOrUpdateDataset(dataset1,conName,Optional.of("nikhil+1@syncari.com"),true,headers);
            }catch (Exception e){
                Assert.assertNotNull(e.getMessage());
                e.getMessage().contains("QUERY_EXECUTION_FAILED");
                e.getMessage().contains("syntax error");
            }
            Dataset dataset = new Dataset().setName("testdataset2").setRawQuery("SELECT amount, closedate, isclosed FROM syncari_qwak1t.\"opportunity__c\" \"Opportunity\"")
                    .setDescription("testdataset2").setDisplayName("testdataset2");

            Map<String, String> dsMap = tsService.createOrUpdateDataset(dataset,conName,Optional.of("nikhil+1@syncari.com"),true,headers);
            dataset.setInsightsProviderSQLViewId(dsMap.get("SQL_VIEW_ID"));
            dataset.setInsightsProviderId(dsMap.get("WORKSHEET_ID"));
            dataset.setRawQuery("SELECT amount, closedate, isclosed FROM syncari_qwak1t.\"opportunity__c\" \"Opportunity\" where amount > 5000");
            Map<String, String> dsMapUpdate = tsService.createOrUpdateDataset(dataset,conName,Optional.of("nikhil+1@syncari.com"),false,headers);
            Assert.assertFalse(dsMap.get("SQL_VIEW_ID").equals(dsMapUpdate.get("SQL_VIEW_ID")));
            dataset.setInsightsProviderSQLViewId(dsMapUpdate.get("SQL_VIEW_ID"));
            Assert.assertTrue(dsMap.get("WORKSHEET_ID").equals(dsMapUpdate.get("WORKSHEET_ID")));
            tsService.deleteDataset(dataset,Optional.of("nikhil+1@syncari.com"),headers);
        });

    }

    @Ignore
    @Test
    public void testBQConnectionCreationDeletion(){
        InsightsProviderConnection connection = new InsightsProviderConnection();
        String name = "qwak1t";
        User u = new User().setInsightsProviderUserName("nikhil+1@syncari.com").setEmail("nikhil+1@syncari.com");
        Instance ins = new Instance();
        ins.setSyncariId(name);
        Organization o = new Organization();
        o.setInsightsProviderOrgId("929251882");
        SyncariContext.runWithContext(o, ins, u, () -> {
            connection.setName(name.toUpperCase() + "_CONNECTION_BQ_TEST");
            connection.setValidate(false);
            connection.setData_warehouse_type("GOOGLE_BIGQUERY");
            Map<String, Object> mapt = new HashMap<>();
            mapt = new HashMap<>();
            mapt.put("configuration",Map.of("accountName", "thoughtspotpoc", "project_id","hopeful-sunset-238922","oauth_pvt_key","{  \"type\": \"service_account\", \"project_id\": \"hopeful-sunset-238922\",  \"private_key_id\": \"REPLACE_ME\",  \"private_key\": \"REPLACE_ME\", \"client_email\": \"bq-ts-serviceaccount@hopeful-sunset-238922.iam.gserviceaccount.com\",\"client_id\": \"114775897260124693785\",  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",  \"token_uri\": \"https://oauth2.googleapis.com/token\",  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/bq-ts-serviceaccount%40hopeful-sunset-238922.iam.gserviceaccount.com\",  \"universe_domain\": \"googleapis.com\"}"
                    ,"schema","002DB8"));
            mapt.put("externalDatabases",List.of());
            connection.setData_warehouse_config(mapt);
            HttpHeaders headers = tsService.getHeaders(Optional.of("nikhil+1@syncari.com"),300L);
            String connectionId = tsService.createConnection(connection,Optional.of("nikhil+1@syncari.com"),headers);
            connection.setConnection_identifier(connectionId);
            tsService.updateConnection(connection,Optional.of("nikhil+1@syncari.com"),headers);
            tsService.deleteConnection(connection, Optional.of("nikhil+1@syncari.com"),headers);
        });
    }

    @Ignore
    @Test
    public void testBQConnectionCreationDeletion2(){
        String name = "O3OOQ2";
        User u = new User().setInsightsProviderUserName("nikhil+1@syncari.com").setEmail("nikhil+1@syncari.com");
        Instance ins = new Instance();
        ins.setSyncariId(name);
        Organization o = new Organization();
        ins.setStatus(Status.ACTIVE);
        ins.setDisplayName("THOUGHTSPOTPOC");
        ins.setName("thoughtspotpoc");
        o.setName("THOUGHTSPOTPOC");
        o.setInsightsProviderOrgId("929251882");

        SyncariContext.runWithContext(o, ins, u, () -> {
            InsightsProviderConnection connection = new InsightsProviderConnection();
            String instanceName = "O3OOQ2";
            connection.setData_warehouse_type("GOOGLE_BIGQUERY");
            Map<String, Object> data_warehouse_config = new HashMap<>();
            Map<String, Object> dbConfig = new HashMap<>();
            dbConfig.put("project_id","hopeful-sunset-238922");
            dbConfig.put("oauth_pvt_key",new String(Base64.getDecoder().decode("cmVkYWN0ZWQ=")));
            data_warehouse_config.put("configuration",dbConfig);
            data_warehouse_config.put("authenticationType","SERVICE_ACCOUNT");

            List<Map<String, Object>> externalDbs = new ArrayList<>();
            Map<String, Object> externalDb = new HashMap<>();
            externalDb.put("name","hopeful-sunset-238922");
            externalDb.put("isAutoCreated",false);
            List<String> viewNames = List.of("dfiCurrentScoreByEntity","dfiCurrentScoreByEntityAndCategory","dfiOverallScoreByCategory"
                    ,"dfiOverallScoreByTime","dfiOverallScoreByTimeAndCategory","dfiScoreOverTimeByEntity"
                    ,"dfiScoreOverTimeByEntityAndCategory","dfiScoreOverTimeByEntityAndRule");

            List<TSTable> tables = new ArrayList<>();
            viewNames.forEach(view -> {
                MaterializedViewDefinition viewDefinition = bigQueryHelper.getViewDefinition(view, instanceName);
                Schema schema = viewDefinition.getSchema();
                List<TSColumn> columns = new ArrayList<>();
                TSTable table = new TSTable();
                table.setName(view);
                table.setType("VIEW");
                table.setDescription(view);
                table.setSelected(true);
                table.setLinked(true);
                schema.getFields().forEach(f -> {
                    TSColumn tsColumn = new TSColumn();
                    tsColumn.setName(f.getName());
                    String dataType = mapDataType(f.getType().name());
                    tsColumn.setType(dataType);
                    tsColumn.setSchemaName(instanceName);
                    tsColumn.setSelected(true);
                    tsColumn.setCanImport(true);
                    tsColumn.setTableName(view);
                    tsColumn.setDbName(instanceName);
                    columns.add(tsColumn);
                });
                table.setColumns(columns);
                tables.add(table);
            });

            if (CollectionUtils.isNotEmpty(tables)){
                TSSchema schema = new TSSchema();
                schema.setName(instanceName);
                schema.setTables(tables);
                externalDb.put("schemas",List.of(schema));
                externalDbs.add(externalDb);
                data_warehouse_config.put("externalDatabases", externalDbs);
                connection.setValidate(true);
            }
            connection.setName(String.format("%s_BQ_CONNECTION", instanceName));
            connection.setData_warehouse_config(data_warehouse_config);
            HttpHeaders headers = tsService.getHeaders(Optional.of("nikhil+1@syncari.com"),300L); // Creating 5 mins token, for searching connection and create/update connection
            Optional<String> connectionId = Optional.empty();//tsService.searchConnection(connection,Optional.of("nikhil+1@syncari.com"),headers);
            if (!connectionId.isPresent()){
                connectionId = Optional.of(tsService.createConnection(connection,Optional.of("nikhil+1@syncari.com"),headers));
            }
            connection.setConnection_identifier(connectionId.get());
            Optional<TSConnResponse> response = tsService.searchConnectionV2(new InsightsProviderConnection().setName(String.format("%s_BQ_CONNECTION", instanceName)).setConnection_identifier(connectionId.get()),Optional.of("nikhil+1@syncari.com"), headers);
            Assert.assertTrue(response.isPresent());
            Assert.assertTrue(response.get().getId().equalsIgnoreCase(connectionId.get()));
            tsService.deleteConnection(connection,Optional.of("nikhil+1@syncari.com"),headers);
            log.info("BQ Connection created successfully with id {}", connectionId);
        });
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
            case "date":
                return "DATE";
            case "string":
                return "VARCHAR";
            default:
                return dataType.toUpperCase();
        }
    }

    @Ignore
    @Test
    public void testSearchLiveBoards(){
        Organization org = new Organization();
        org.setInsightsProviderOrgId("929251882");
        User u = new User().setInsightsProviderUserName("nikhil+1@syncari.com");
        Instance i = new Instance();
        i.setSyncariId("qwak1t");
        i.setStatus(Status.ACTIVE);
        i.setDisplayName("THOUGHTSPOTPOC");
        i.setName("thoughtspotpoc");
        SyncariContext.runWithContext(org, i, u, () -> {
            TSMetadataSearchReq req = new TSMetadataSearchReq();
            TSSearchMetadataSort sort = new TSSearchMetadataSort().setField_name("LAST_ACCESSED");
            req.setMetadata(List.of(new TSMetadataListItemInput().setType(TSMetadataType.LIVEBOARD.name())));
            req.setInclude_headers(true);
            req.setSort_options(sort);
            HttpHeaders headers = tsService.getHeaders(Optional.of(u.getInsightsProviderUserName()),300L);

            List<TSMetadataSearchResponse> responseList = tsService.searchMetadata(req, Optional.of(u.getInsightsProviderUserName()),headers)
                    .stream().filter(m -> !((String)m.getMetadata_header().get("authorName")).equalsIgnoreCase("system")).collect(Collectors.toList());
            List<String> list =  responseList.stream().map(resp -> resp.getMetadata_name()).collect(Collectors.toList());
            Assert.assertTrue(CollectionUtils.isNotEmpty(list));
        });
  }

    @Ignore
    @Test
    public void testCreateLiveBoard(){
        Organization org = new Organization();
        org.setInsightsProviderOrgId("929251882");
        User u = new User().setInsightsProviderUserName("tsadmin");
        Instance i = new Instance();
        i.setSyncariId("qwak1t");
        i.setStatus(Status.ACTIVE);
        i.setDisplayName("THOUGHTSPOTPOC");
        i.setName("thoughtspotpoc");
        SyncariContext.runWithContext(org, i, u, () -> {
            HttpHeaders headers = tsService.getHeaders(Optional.of(u.getInsightsProviderUserName()),300L);
            Optional<TSConnResponse> connResponse = tsService.searchConnectionV2(new InsightsProviderConnection().setName("2OGVNA_BQ_CONNECTION").setConnection_identifier("2OGVNA_BQ_CONNECTION").setInclude_details(true),Optional.of(TSService.TS_ADMIN_USER),headers);
            connResponse.ifPresentOrElse(resp -> {
                TSConnectionDetail tsConnectionDetail  = resp.getDetails();
                if (null != tsConnectionDetail){
                    Map<String, String> fqnsMap = new HashMap<>();
                    tsConnectionDetail.getTables().forEach(t -> {
                        fqnsMap.put(t.getName(), t.getId());
                    });
                    String seededLiveBoardId = tsService.seedSystemLiveboard("rk",fqnsMap,headers);
                    tsService.deleteMetadata(seededLiveBoardId, headers);

                }else{
                    log.error("Could not find Connection details with BQ in TS, Provisioning of DFI report in Insights failed");
                }
            },() -> {
                log.error("Could not find connection with BQ, Provisioning of DFI report in Insights failed");
            });
        });
    }

    @Ignore
    @Test
    public void testSearchTables(){
        Organization org = new Organization();
        org.setInsightsProviderOrgId("929251882");
        Instance i = new Instance();
        i.setSyncariId("qwak1t");
        i.setStatus(Status.ACTIVE);
        i.setDisplayName("THOUGHTSPOTPOC");
        i.setName("thoughtspotpoc");
        User u = new User().setInsightsProviderUserName("64c342df1c030b00015bfd0d");
        SyncariContext.runWithContext(org, i, u, () -> {
            TSMetadataSearchReq req = new TSMetadataSearchReq();
            req.setMetadata(List.of(new TSMetadataListItemInput().setType(TSMetadataType.LOGICAL_TABLE.name()).setIdentifier("lead")));
            req.setInclude_headers(true);
            HttpHeaders headers = tsService.getHeaders(Optional.of(u.getInsightsProviderUserName()),300L);
            List<TSMetadataSearchResponse> responseList = tsService.searchMetadata(req, Optional.of(u.getInsightsProviderUserName()),headers)
                    .stream().filter(m -> !((String)m.getMetadata_header().get("authorName")).equalsIgnoreCase("system")).collect(Collectors.toList());
            List<String> list =  responseList.stream().map(resp -> resp.getMetadata_name()).collect(Collectors.toList());
            Assert.assertTrue(CollectionUtils.isNotEmpty(list));
        });
    }

    @Ignore
    @Test
    public void testAssociateUser(){
        Organization org = new Organization();
        org.setInsightsProviderOrgId("0");
        Instance i = new Instance();
        i.setSyncariId("qwak1t");
        i.setStatus(Status.ACTIVE);
        i.setDisplayName("THOUGHTSPOTPOC");
        i.setName("thoughtspotpoc");
        User u = new User().setInsightsProviderUserName("rohit@syncari.com");
        SyncariContext.runWithContext(org, i, u, () -> {
            HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),60L);
            Optional<TSUserResponse> tsUserResponse = tsService.searchUser(new InsightsProviderUser().setName("nikhil+1@syncari.com").setEmail("nikhil+1@syncari.com"),Optional.of("tsadmin"),true,headers);
            Assert.assertTrue(tsUserResponse.isPresent());
            InsightsProviderUser tsUser = new InsightsProviderUser();
            tsUser.setEmail("nikhil+1@syncari.com");
            tsUser.setName("nikhil+1@syncari.com");
            tsUser.setDisplay_name("nikhil+1@syncari.com");
            tsUser.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
            tsUser.setUser_identifier(tsUserResponse.get().getId());
            tsUser.setOrg_identifiers(List.of("780224115"));
            tsUser.setOperation("ADD");
            tsService.updateUser(tsUser, Optional.of(TSService.TS_ADMIN_USER),true,headers);
        });
    }

    @Test
    public void testSearchUser(){
        Organization org = new Organization();
        org.setInsightsProviderOrgId("0");
        Instance i = new Instance();
        i.setSyncariId("qwak1t");
        i.setStatus(Status.ACTIVE);
        i.setDisplayName("THOUGHTSPOTPOC");
        i.setName("thoughtspotpoc");
        User u = new User().setInsightsProviderUserName("rohit@syncari.com");
        SyncariContext.runWithContext(org, i, u, () -> {
            InsightsProviderUser usersearch = new InsightsProviderUser().setName("nikhil+1@syncari.com").setEmail("nikhil+1@syncari.com");
            HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),60L);
            Optional<TSUserResponse> tsUserResponse = tsService.searchUser(usersearch,Optional.of("tsadmin"),true,headers);
            Assert.assertTrue(tsUserResponse.isPresent());
            InsightsProviderUser tsUser = new InsightsProviderUser();
            tsUser.setEmail("nikhil+1@syncari.com");
            tsUser.setName("nikhil+1@syncari.com");
            tsUser.setDisplay_name("nikhil+1@syncari.com");
            tsUser.setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
            tsUser.setUser_identifier(tsUserResponse.get().getId());
        });
    }

    @Ignore
    @Test
    public void testSearchGroup(){
        Organization org = new Organization();
        org.setInsightsProviderOrgId("929251882");
        Instance i = new Instance();
        i.setSyncariId("qwak1t");
        i.setStatus(Status.ACTIVE);
        i.setDisplayName("THOUGHTSPOTPOC");
        i.setName("thoughtspotpoc");
        User u = new User().setInsightsProviderUserName("rohit@syncari.com");
        SyncariContext.runWithContext(org, i, u, () -> {
            String grpName = i.getSyncariId().toUpperCase() + "_ADMINISTRATION";
            HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),60L);
            Optional<TSGrpResponse> tsGrpResponse = tsService.searchGroup(grpName,Optional.of("tsadmin"),headers);
            Assert.assertTrue(tsGrpResponse.isPresent());
        });
    }

    @Ignore
    @Test
    public void testSearchGroupsForAUser(){
        Organization org = new Organization();
        org.setInsightsProviderOrgId("929251882");
        Instance i = new Instance();
        i.setSyncariId("qwak1t");
        i.setStatus(Status.ACTIVE);
        i.setDisplayName("THOUGHTSPOTPOC");
        i.setName("thoughtspotpoc");
        User u = new User().setInsightsProviderUserName("rohit@syncari.com");
        SyncariContext.runWithContext(org, i, u, () -> {
            InsightsProviderUser user = new InsightsProviderUser().setName("64c342df1c030b00015bfd0d");
            HttpHeaders headers = tsService.getHeaders(Optional.of(TSService.TS_ADMIN_USER),30L);
            Optional<TSUserResponse> resp = tsService.searchUser(user,Optional.of(TSService.TS_ADMIN_USER),false,headers);
            resp.ifPresentOrElse(usr -> {
                List<TSGrpResponse> tsGrpResponsesAll = tsService.searchAllLocalGroups(Optional.of(usr.getId()),headers);
                Assert.assertTrue(CollectionUtils.isNotEmpty(tsGrpResponsesAll));
                tsGrpResponsesAll = tsService.searchLocalGroupsForAUser(usr.getId(),Optional.of(usr.getId()),headers);
                List<TSGrpResponse> filteredGroupsAll = tsGrpResponsesAll.stream().filter(g ->
                        !g.getName().startsWith(SyncariContext.getSyncariId().toUpperCase()) && !g.isSystem_group()
                                && !g.isDeleted() && !"Demo Retail Group".equalsIgnoreCase(g.getName()) ).collect(Collectors.toList());
                Assert.assertTrue(CollectionUtils.isNotEmpty(filteredGroupsAll));
                filteredGroupsAll.forEach(g -> {
                    tsService.addOrRemoveUserToGroup(g.getName(), List.of(usr.getId()),
                            Optional.of(TSService.TS_ADMIN_USER),headers,GroupOperation.REMOVE);
                    tsService.addOrRemoveUserToGroup(g.getName(), List.of(usr.getId()),
                            Optional.of(TSService.TS_ADMIN_USER),headers,GroupOperation.ADD);
                });
            },() -> Assert.fail());
        });
    }

    @Ignore
    @Test
    public void testGetBearerToken(){
        TSToken token = tsService.getBearerToken("rohit@syncari.com", "0",200L);
        Assert.assertNotNull(token);
        boolean result = tsService.validateToken(token.getToken());
        Assert.assertTrue(result);
    }

    @Ignore
    @Test
    public void testShareWithUser(){
        Organization org = new Organization();
        org.setInsightsProviderOrgId("929251882");
        User u = new User().setInsightsProviderUserName("nikhil+1@syncari.com");
        SyncariContext.setOrganziation(org);
        Instance i = new Instance();
        i.setSyncariId("qwak1t");
        i.setStatus(Status.ACTIVE);
        i.setDisplayName("THOUGHTSPOTPOC");
        i.setName("thoughtspotpoc");
        SyncariContext.runWithContext(org, i, u, () -> {
            HttpHeaders headers = tsService.getHeaders(Optional.of("nikhil+1@syncari.com"),120L);

            Optional<TSUserResponse> tsUserResponse = tsService.searchUser(new InsightsProviderUser().setName("nikhil+1@syncari.com").setEmail("nikhil+1@syncari.com"),Optional.of("tsadmin"),true,headers);
            Assert.assertTrue(tsUserResponse.isPresent());

            TSMetadataSearchReq req = new TSMetadataSearchReq();
            req.setMetadata(List.of(new TSMetadataListItemInput().setType(TSMetadataType.LOGICAL_TABLE.name())));
            req.setInclude_headers(true);

            // Search metadata
            List<TSMetadataSearchResponse> responseList = tsService.searchMetadata(req, Optional.of(u.getInsightsProviderUserName()),headers)
                    .stream().filter(m -> !((String)m.getMetadata_header().get("authorName")).equalsIgnoreCase("system")).collect(Collectors.toList());
            List<String> list =  responseList.stream().map(resp -> resp.getMetadata_id()).collect(Collectors.toList());
            Assert.assertNotNull(list);

            // Create group
            String syncariid = "test_syncariid";
            InsightsProviderGroup group = new InsightsProviderGroup();
            group.setName(syncariid);
            group.setDisplay_name(syncariid);
            group.setPrivileges(List.of(TSPrivileges.ADMINISTRATION.name()));
            group.setUser_identifiers(List.of(tsUserResponse.get().getId()));
            String grpId = tsService.createGroup(group,Optional.of(TSService.TS_ADMIN_USER),headers);

            responseList.forEach(r -> {
                System.out.println("metadata is " + r.getMetadata_id() + " "+ r.getMetadata_name() + " " + r.getMetadata_type());
            });
            // Share Metadata
            TSMetadataShareRequest shareRequest = new TSMetadataShareRequest();
            shareRequest.setMetadata_identifiers(list);
            shareRequest.setMetadata_type("LOGICAL_TABLE");
            TSPermission permission = new TSPermission()
                    .setPrincipal(new TSPrincipalInput().setIdentifier(grpId).setType("USER_GROUP")).setShare_mode("MODIFY");
            shareRequest.setPermissions(List.of(permission));
            shareRequest.setEmails(List.of("dev@syncari.com"));
            tsService.shareMetadata(shareRequest, Optional.of("nikhil+1@syncari.com"),headers);
            tsService.deleteGroup(grpId,Optional.of(TSService.TS_ADMIN_USER),headers);
        });
    }

    @Ignore
    @Test
    public void testChangeOwner(){
        Organization org = new Organization();
        org.setInsightsProviderOrgId("929251882");
        User u = new User().setInsightsProviderUserName("nikhil+1@syncari.com");
        SyncariContext.setOrganziation(org);
        Instance i = new Instance();
        i.setSyncariId("qwak1t");
        i.setStatus(Status.ACTIVE);
        i.setDisplayName("THOUGHTSPOTPOC");
        i.setName("thoughtspotpoc");
        SyncariContext.runWithContext(org, i, u, () -> {
            HttpHeaders headers = tsService.getHeaders(Optional.of("nikhil+1@syncari.com"),120L);

            Optional<TSUserResponse> tsUserResponse = tsService.searchUser(new InsightsProviderUser().setName("nikhil+1@syncari.com").setEmail("nikhil+1@syncari.com"),Optional.of("tsadmin"),true,headers);
            Assert.assertTrue(tsUserResponse.isPresent());

            TSMetadataSearchReq req = new TSMetadataSearchReq();
            req.setMetadata(List.of(new TSMetadataListItemInput().setType(TSMetadataType.LIVEBOARD.name())));
            req.setInclude_headers(true);

            // Search metadata
            List<TSMetadataSearchResponse> responseList = tsService.searchMetadata(req, Optional.of(u.getInsightsProviderUserName()),headers)
                    .stream().filter(m -> !((String)m.getMetadata_header().get("authorName")).equalsIgnoreCase("system")).collect(Collectors.toList());
            List<String> list =  responseList.stream().map(resp -> resp.getMetadata_id()).collect(Collectors.toList());
            Assert.assertNotNull(list);

            // Create group
            String syncariid = "test_syncariid";
            Optional<TSUserResponse> tsUserResponseTSAdmin = tsService.searchUser(new InsightsProviderUser().setName(TSService.TS_ADMIN_USER),Optional.of("tsadmin"),true,headers);


            tsUserResponseTSAdmin.ifPresent(ts -> {
                TSChangeOwnerRequest request = new TSChangeOwnerRequest();
                TSMetadataListItemInput input = new TSMetadataListItemInput().setIdentifier(list.stream().findFirst().get()).setType("LIVEBOARD");
                request.setMetadata(List.of(input));
                request.setUser_identifier(ts.getId());
                tsService.changeOwnerMetadata(request,Optional.of("nikhil+1@syncari.com"),headers);
            });

        });
    }


}
