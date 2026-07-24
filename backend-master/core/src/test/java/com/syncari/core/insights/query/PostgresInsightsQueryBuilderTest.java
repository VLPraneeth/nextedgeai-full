package com.syncari.core.insights.query;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.database.PostgresService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.insights.*;
import com.syncari.core.model.insights.dataset.*;
import com.syncari.core.repositories.customer.DatacardRepo;
import com.syncari.core.repositories.customer.DatasetRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DatacardService;
import com.syncari.core.service.DatasetService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@Slf4j
public class PostgresInsightsQueryBuilderTest extends AbstractSyncariTest {

    @Autowired
    PostgresInsightsQueryBuilder postgresInsightsQueryBuilder;

    @Autowired
    DatasetRepo datasetRepo;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    DatacardRepo datacardRepo;

    @Autowired
    DatacardService datacardService;

    @Autowired
    DatasetService datasetService;

    @Autowired
    ConnectorService connectorService;

    @Test
    public void testBuildQueryQClosePipleineRev(){
        Optional<Dataset> dataset = datasetRepo.findApprovedByName("quarterlyClosedPipelineRevenueDS");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(dataset.isPresent());

        assertTrue(dataset.isPresent());
        final String newdsId = dataset.get().getId();
        dataset.ifPresent(qcp -> {
            DatasetConfig dsconfig = qcp.getDatasetConfig();
            qcp.setDatasetConfig(dsconfig);
            QueryConfig config = datasetService.buildQueryConfigFromDataset(qcp);
            String queryToExecute = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(newdsId),new HashMap<>(),new HashMap<>());
            assertNotNull(queryToExecute);
            log.info("Output query is {}", queryToExecute);
            assertTrue(queryToExecute.contains("CONCAT('Q',\"fiscalquarter\",' ',\"fiscalyear\")"));
            assertTrue(queryToExecute.contains("GROUP BY \"fiscalquarter\",\"fiscalyear\" ORDER BY \"fiscalyear\" ASC,\"fiscalquarter\" ASC"));
        });

        dataset.ifPresent(ds -> {
            Optional<Datacard> quaterlclosePipeline = datacardRepo.findByName("quarterlyClosedPipelineRevenueDC");
            assertTrue(quaterlclosePipeline.isPresent());
            quaterlclosePipeline.ifPresent(qcp -> {
                Datacard datacard = datacardService.getSeededOrFromDataset(qcp.getId());
                assertTrue(CollectionUtils.isNotEmpty(datacard.getContents()));
                assertEquals(1,datacard.getContents().size());
                Visualization visualization = datacard.getContents().get(0);
                VizConfig vizConfig = visualization.getConfig();
                List<Sort> sortList = vizConfig.getSortList();
                QueryConfig config = new QueryConfig().setColumns(vizConfig.getColumns()).setGroupingColumns(((BarVizConfig)vizConfig).getGroupingColumns()).
                        setSortList(sortList);
                config.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
                String query = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo, Optional.of(ds.getId()), new HashMap<>(),new HashMap<>());
                assertNotNull(query);
                log.info("Output query is {}", query);
                assertTrue(query.contains("CONCAT('Q',\"fiscalquarter\",' ',\"fiscalyear\")"));
                assertTrue(query.contains("GROUP BY \"fiscalquarter\",\"fiscalyear\" ORDER BY \"fiscalyear\" ASC,\"fiscalquarter\" ASC"));
            });
        });
    }

    @Test
    public void testBuildQueryOpenPipelineCountDSWithInOtherDataset(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get();
        Dataset newDataset = new Dataset();
        newDataset.setName("allOpenNewPipelineCountDSInOtherDS").setDisplayName("allOpenNewPipelineCountDSInOtherDS Display").setDescription("allOpenNewPipelineCountDSInOtherDS New description");
        newDataset.setDraftStatus(DraftStatus.NEW);
        Projection projection = new Projection();
        projection.setAliasName("countinnerdsprojection");
        projection.setFunction(new NoQueryFunction().setColumns(List.of(new QField().setDatasetId(datasetCopy.getId()).setType(QField.Type.DATASET)
                .setName(datasetCopy.getDatasetConfig().getProjectionsList().stream().findFirst().get().getAliasName()).setDataType("integer"))));

        DatasetConfig conf = new DatasetConfig().setFromDatasets(List.of(new DatasetFrom().setDatasetId(datasetCopy.getId()).setDatasetType(DatasourceType.DATASET)
                .setApiName(datasetCopy.getName()).setDisplayName(datasetCopy.getDisplayName())))
                .setProjectionsList(List.of());
        conf.setProjectionsList(List.of(projection));
        newDataset.setDatasetConfig(conf);
        newDataset = datasetService.createDataset(newDataset);
        assertNotNull(newDataset);
        assertTrue(newDataset.getName().contains("allOpenNewPipelineCountDSInOtherDS"));
        ConnectorInfo connectorInfo = createDbConnector();
        if (null != newDataset){
            {
                Optional<Dataset> allOpenPipelineCountInOtherDs = datasetRepo.findById(newDataset.getId());
                assertTrue(allOpenPipelineCountInOtherDs.isPresent());
                final String newdsId = newDataset.getId();
                allOpenPipelineCountInOtherDs.ifPresent(qcp -> {
                    DatasetConfig dsconfig = qcp.getDatasetConfig();
                    qcp.setDatasetConfig(dsconfig);
                    QueryConfig config = datasetService.buildQueryConfigFromDataset(qcp);
                    String queryToExecute = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(newdsId),new HashMap<>(),new HashMap<>());
                    assertNotNull(queryToExecute);
                    log.info("Output query is {}", queryToExecute);
                    assertTrue(queryToExecute.contains("COUNT"));
                });
            }
        }
    }

    @Test
    public void testBuildQueryOpenPipelineCountDSInOtherDSWithFilter(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get();
        Dataset newDataset = new Dataset();
        newDataset.setName("allOpenNewPipelineCountDSInOtherDSWithFilter").setDisplayName("allOpenNewPipelineCountDSInOtherDSWithFilter Display").setDescription("allOpenNewPipelineCountDSInOtherDSWithFilter New description");
        newDataset.setDraftStatus(DraftStatus.NEW);
        String fieldAliasName = datasetCopy.getDatasetConfig().getProjectionsList().stream().findFirst().get().getAliasName();
        QField field = new QField().setDatasetId(datasetCopy.getId()).setType(QField.Type.DATASET)
                .setName(fieldAliasName).setDataType("integer");

        Projection projection = new Projection();
        projection.setAliasName("countinnerdsprojection");
        projection.setFunction(new NoQueryFunction().setColumns(List.of(field)));

        DatasetConfig conf = new DatasetConfig().setFromDatasets(List.of(new DatasetFrom().setDatasetId(datasetCopy.getId()).setDatasetType(DatasourceType.DATASET)
                .setApiName(datasetCopy.getName()).setDisplayName(datasetCopy.getDisplayName())));
        conf.setProjectionsList(List.of(projection));
        conf.setPredicate(Map.of("operator", "AND", "groupPredicateId", "testGroupPred"
                ,"predicates", List.of(Map.of("operator", "gt", "left", Map.of("type", "variable", "datasetId",datasetCopy.getId()
                        , "apiName",fieldAliasName,"datasetType", "DATASET", "datasetApiName", datasetCopy.getName()),
                        "right", Map.of("type", "literal", "value", 0)))));
        newDataset.setDatasetConfig(conf);
        newDataset = datasetService.createDataset(newDataset);
        assertNotNull(newDataset);
        assertTrue(newDataset.getName().contains("allOpenNewPipelineCountDSInOtherDS"));
        ConnectorInfo connectorInfo = createDbConnector();
        if (null != newDataset){
            {
                Optional<Dataset> allOpenPipelineCountInOtherDs = datasetRepo.findById(newDataset.getId());
                assertTrue(allOpenPipelineCountInOtherDs.isPresent());
                final String newdsId = newDataset.getId();
                allOpenPipelineCountInOtherDs.ifPresent(qcp -> {
                    DatasetConfig dsconfig = qcp.getDatasetConfig();
                    qcp.setDatasetConfig(dsconfig);
                    QueryConfig config = datasetService.buildQueryConfigFromDataset(qcp);
                    String queryToExecute = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(newdsId),new HashMap<>(),new HashMap<>());
                    assertNotNull(queryToExecute);
                    log.info("Output query is {}", queryToExecute);
                    assertTrue(queryToExecute.contains("COUNT"));
                    assertTrue(queryToExecute.contains("WHERE"));
                    assertTrue(queryToExecute.contains(">"));
                });
            }
        }
    }

    @Test
    public void testBuildQueryOpenPipelineCountDSInOtherDSWithFilterAndSort(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get();
        Dataset newDataset = new Dataset();
        newDataset.setName("allOpenNewPipelineCountDSInOtherDSWithFilterAndSort").setDisplayName("allOpenNewPipelineCountDSInOtherDSWithFilterAndSort Display").setDescription("allOpenNewPipelineCountDSInOtherDSWithFilterAndSort New description");
        newDataset.setDraftStatus(DraftStatus.NEW);
        String fieldAliasName = datasetCopy.getDatasetConfig().getProjectionsList().stream().findFirst().get().getAliasName();
        QField field = new QField().setDatasetId(datasetCopy.getId()).setType(QField.Type.DATASET)
                .setName(fieldAliasName).setDataType("integer");

        Projection projection = new Projection();
        projection.setAliasName("countinnerdsprojection");
        projection.setFunction(new NoQueryFunction().setColumns(List.of(field)));

        DatasetConfig conf = new DatasetConfig().setFromDatasets(List.of(new DatasetFrom().setDatasetId(datasetCopy.getId()).setDatasetType(DatasourceType.DATASET)
                .setApiName(datasetCopy.getName()).setDisplayName(datasetCopy.getDisplayName())));
        conf.setProjectionsList(List.of(projection));
        conf.setPredicate(Map.of("operator", "AND", "groupPredicateId", "testGroupPred"
                ,"predicates", List.of(Map.of("operator", "gt", "left", Map.of("type", "variable", "datasetId",datasetCopy.getId()
                        , "apiName",fieldAliasName,"datasetType", "DATASET", "datasetApiName", datasetCopy.getName()),
                        "right", Map.of("type", "literal", "value", 0)))));
        newDataset.setDatasetConfig(conf);
        conf.setOrder(List.of(new Sort(field, true)));
        newDataset = datasetService.createDataset(newDataset);
        assertNotNull(newDataset);
        assertTrue(newDataset.getName().contains("allOpenNewPipelineCountDSInOtherDS"));
        ConnectorInfo connectorInfo = createDbConnector();
        if (null != newDataset){
            {
                Optional<Dataset> allOpenPipelineCountInOtherDs = datasetRepo.findById(newDataset.getId());
                assertTrue(allOpenPipelineCountInOtherDs.isPresent());
                final String newdsId = newDataset.getId();
                allOpenPipelineCountInOtherDs.ifPresent(qcp -> {
                    DatasetConfig dsconfig = qcp.getDatasetConfig();
                    qcp.setDatasetConfig(dsconfig);
                    QueryConfig config = datasetService.buildQueryConfigFromDataset(qcp);
                    String queryToExecute = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(newdsId),new HashMap<>(),new HashMap<>());
                    assertNotNull(queryToExecute);
                    log.info("Output query is {}", queryToExecute);
                    assertTrue(queryToExecute.contains("COUNT"));
                    assertTrue(queryToExecute.contains("WHERE"));
                    assertTrue(queryToExecute.contains(">"));
                    assertTrue(queryToExecute.contains("ORDER BY"));
                });
            }
        }
    }

    @Test
    public void testBuildQueryDatePartFunctionDataset(){
        Dataset dataset = new Dataset();
        dataset.setName("datePartQuery").setDisplayName("datePartQuery").setDescription("datePartQuery");
        Connector syncariConn = connectorService.getSyncariConnector();
        Optional<EntityDefinition> edefOpt = entityProxyRepo.findEntityByConnectorIdAndApiName(syncariConn.getId(), "opportunity");
        assertTrue(edefOpt.isPresent());
        String opptyEntityDefId = edefOpt.get().getId();
        DatasetConfig config = new DatasetConfig();
        var field = new QField().setType(QField.Type.ENTITY).setDataType("datetime").setDatasetId(opptyEntityDefId).setName("closedate");
        config.setFromDatasets(List.of(new DatasetFrom().setDatasetType(DatasourceType.ENTITY).setDatasetId(opptyEntityDefId).setApiName("opportunity").setDisplayName("opportunity").setDatastoreName("opportunity")));
        config.setAggregate(List.of(new AggregateConfig().setQueryFunction(new DatePartQueryFunction().setFunction(AggFunctions.DATE_PART).setDatePartField("quarter")
                .setColumns(List.of(field))).setAggregateField(field)));
        Projection projection = new Projection();
        projection.setFunction(new DatePartQueryFunction().setFunction(AggFunctions.DATE_PART).setDatePartField("quarter")
                .setColumns(List.of(new QField().setType(QField.Type.ENTITY).setDataType("datetime").setDatasetId(opptyEntityDefId).setName("closedate"))));
        projection.setAliasName("closedate");
        config.setProjectionsList(List.of(projection));
        dataset.setDatasetConfig(config);
        Dataset createdDataset = datasetService.createDataset(dataset);
        assertNotNull(createdDataset);
        assertTrue(createdDataset.getName().equals(dataset.getName()));
        Optional<Dataset> datepartDataset = datasetRepo.findById(createdDataset.getId());
        assertTrue(datepartDataset.isPresent());
        ConnectorInfo connectorInfo = createDbConnector();
        datepartDataset.ifPresent(datepart -> {
            QueryConfig queryConfig = datasetService.buildQueryConfigFromDataset(datepart);
            String queryToExecute = postgresInsightsQueryBuilder.buildQuery(queryConfig, connectorInfo,Optional.of(createdDataset.getId()),new HashMap<>(),new HashMap<>());
            log.info("Output query is {}", queryToExecute);
            assertNotNull(queryToExecute);
            assertTrue(queryToExecute.contains("DATE_PART"));
            assertTrue(queryToExecute.contains("quarter"));
        });
    }

    @Test
    public void testBuildQueryDatePartFunctionDatasetWithVariable(){
        Dataset dataset = new Dataset();
        dataset.setName("datePartQueryWithVar").setDisplayName("datePartQueryWithVar").setDescription("datePartQueryWithVar");
        Connector syncariConn = connectorService.getSyncariConnector();
        Optional<EntityDefinition> edefOpt = entityProxyRepo.findEntityByConnectorIdAndApiName(syncariConn.getId(), "opportunity");
        assertTrue(edefOpt.isPresent());
        String opptyEntityDefId = edefOpt.get().getId();
        DatasetConfig config = new DatasetConfig();
        config.setFromDatasets(List.of(new DatasetFrom().setDatasetType(DatasourceType.ENTITY).setDatasetId(opptyEntityDefId).setApiName("opportunity").setDisplayName("opportunity").setDatastoreName("opportunity")));
        var field = new QField().setType(QField.Type.ENTITY).setDataType("datetime").setDatasetId(opptyEntityDefId).setName("closedate");
        QueryFunction qf = new DatePartQueryFunction().setFunction(AggFunctions.DATE_PART).setDatePartField("{{var1}}")
                .setColumns(List.of(field));
        config.setAggregate(List.of(new AggregateConfig().setQueryFunction(qf).setAggregateField(field)));
        Projection projection = new Projection();
        projection.setFunction(qf);
        projection.setAliasName("closedate");
        config.setProjectionsList(List.of(projection));
        dataset.setDatasetConfig(config);
        Dataset createdDataset = datasetService.createDataset(dataset);
        assertNotNull(createdDataset);
        assertTrue(createdDataset.getName().equals(dataset.getName()));

        Variable variable = new Variable().setDisplayName("var1").setApiName("var1")
                .setDatatype("datetime").setHelpText("help for variable").setRequired(true)
                .setVariableValue(new VariableValue().setDefaultValue("quarter").setDatatype("datetime").setDefaultValueType(VariableValue.VariableType.LITERAL));
        Variable variable1 = datasetService.createVariable(createdDataset.getId(), variable);
        assertNotNull(variable1);
        assertTrue(variable1.getApiName().equals(variable.getApiName()));
        Optional<Dataset> datepartDataset = datasetRepo.findById(createdDataset.getId());
        assertTrue(datepartDataset.isPresent());
        ConnectorInfo connectorInfo = createDbConnector();
        datepartDataset.ifPresent(datepart -> {
            QueryConfig queryConfig = datasetService.buildQueryConfigFromDataset(datepart);
            String queryToExecute = postgresInsightsQueryBuilder.buildQuery(queryConfig, connectorInfo,Optional.of(createdDataset.getId()),new HashMap<>(),new HashMap<>());
            log.info("Output query is {}", queryToExecute);
            assertNotNull(queryToExecute);
            assertTrue(queryToExecute.contains("DATE_PART"));
            assertTrue(queryToExecute.contains("var1"));
        });
    }

    @Test
    public void testBuildQueryProjectionAliasinGrouping(){
        Dataset dataset = new Dataset();
        dataset.setName("datePartQueryWithVar").setDisplayName("datePartQueryWithVar").setDescription("datePartQueryWithVar");
        Connector syncariConn = connectorService.getSyncariConnector();
        Optional<EntityDefinition> edefOpt = entityProxyRepo.findEntityByConnectorIdAndApiName(syncariConn.getId(), "opportunity");
        assertTrue(edefOpt.isPresent());
        String opptyEntityDefId = edefOpt.get().getId();
        DatasetConfig config = new DatasetConfig();
        config.setFromDatasets(List.of(new DatasetFrom().setDatasetType(DatasourceType.ENTITY).setDatasetId(opptyEntityDefId).setApiName("opportunity").setDisplayName("opportunity").setDatastoreName("opportunity")));
        QueryFunction qf = new DatePartQueryFunction().setFunction(AggFunctions.DATE_PART).setDatePartField("{{var1}}")
                .setColumns(List.of(new QField().setType(QField.Type.ENTITY).setDataType("datetime").setDatasetId(opptyEntityDefId).setName("closedate")));
        config.setAggregate(List.of(new AggregateConfig().setQueryFunction(qf)));
        Projection projection = new Projection();
        projection.setFunction(qf);
        projection.setAliasName("closedate");
        config.setProjectionsList(List.of(projection));
        AggregateConfig group = new AggregateConfig().setAggregateField(new QField().setName("closedate").setType(QField.Type.DATASET).setDatasetId(opptyEntityDefId));
        config.setAggregate(List.of(group));
        dataset.setDatasetConfig(config);
        Dataset createdDataset = datasetService.createDataset(dataset);
        assertNotNull(createdDataset);
        assertTrue(createdDataset.getName().equals(dataset.getName()));

        Variable variable = new Variable().setDisplayName("var1").setApiName("var1")
                .setDatatype("datetime").setHelpText("help for variable").setRequired(true)
                .setVariableValue(new VariableValue().setDefaultValue("quarter").setDatatype("datetime").setDefaultValueType(VariableValue.VariableType.LITERAL));
        Variable variable1 = datasetService.createVariable(createdDataset.getId(), variable);
        assertNotNull(variable1);
        assertTrue(variable1.getApiName().equals(variable.getApiName()));
        Optional<Dataset> datepartDataset = datasetRepo.findById(createdDataset.getId());
        assertTrue(datepartDataset.isPresent());
        ConnectorInfo connectorInfo = createDbConnector();
        datepartDataset.ifPresent(datepart -> {
            QueryConfig queryConfig = datasetService.buildQueryConfigFromDataset(datepart);
            String queryToExecute = postgresInsightsQueryBuilder.buildQuery(queryConfig, connectorInfo,Optional.of(createdDataset.getId()),new HashMap<>(),new HashMap<>());
            log.info("Output query is {}", queryToExecute);
            assertNotNull(queryToExecute);
            assertTrue(queryToExecute.contains("DATE_PART"));
            assertTrue(queryToExecute.contains("{{var1}}"));
            assertTrue(queryToExecute.contains("GROUP BY \"opportunity\".\"closedate\""));
        });
    }

    @Test
    public void testBuildQueryClosedPipelineCountWithHypheninAlias(){

        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSEditedForVar").setDisplayName("allOpenNewPipelineCountDSEditedForVar").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);
        DatasetConfig conf = datasetCopy.getDatasetConfig();
        conf.getFromDatasets().forEach(df -> {
            df.setAlias(df.getDatastoreName() + "-1");
        });
        datasetCopy.setDatasetConfig(conf);
        Dataset newDataset = datasetService.createDataset(datasetCopy);
        assertTrue(newDataset.getName().contains(datasetCopy.getName()));
        ConnectorInfo connectorInfo = createDbConnector();
        assertNotNull(newDataset);
        Optional<Dataset> allOpenPipelineCount = datasetRepo.findById(newDataset.getId());
        assertTrue(allOpenPipelineCount.isPresent());
        allOpenPipelineCount.ifPresent(qcp -> {
            QueryConfig config = datasetService.buildQueryConfigFromDataset(qcp);
            String queryToExecute = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(newDataset.getId()),new HashMap<>(),new HashMap<>());
            assertNotNull(queryToExecute);
            log.info("Output query is {}", queryToExecute);
            assertTrue(queryToExecute.contains("COUNT"));
            assertTrue(queryToExecute.contains("\"opportunity-1\""));
            assertFalse(queryToExecute.contains("syncari_syncari_admin.\"opportunity\" \"opportunity\""));
        });
    }

    @Test
    public void testBuildQueryClosedPipelineCountWithVariables(){

        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSEditedForVar").setDisplayName("allOpenNewPipelineCountDSEditedForVar").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);

        Dataset newDataset = datasetService.createDataset(datasetCopy);
        assertTrue(newDataset.getName().contains(datasetCopy.getName()));
        Variable variable = new Variable().setDisplayName("testvaar")
                .setDatatype("boolean").setHelpText("help for variable").setRequired(true)
                .setVariableValue(new VariableValue().setDefaultValue("false").setDatatype("boolean"));
        Variable variable1 = datasetService.createVariable(newDataset.getId(), variable);
        ConnectorInfo connectorInfo = createDbConnector();
        assertNotNull(newDataset);
        assertNotNull(variable1);
        Optional<Dataset> allOpenPipelineCount = datasetRepo.findById(newDataset.getId());
        assertTrue(allOpenPipelineCount.isPresent());
        allOpenPipelineCount.ifPresent(qcp -> {
            DatasetConfig dsconfig = qcp.getDatasetConfig();
            Map<String, Object> pred = dsconfig.getPredicate();
            pred.put("right", Map.of("type", "literal", "value", "{{testvaar}}"));
            dsconfig.setPredicate(pred);
            qcp.setDatasetConfig(dsconfig);
            datasetRepo.save(qcp);
            QueryConfig config = datasetService.buildQueryConfigFromDataset(qcp);
            String queryToExecute = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(newDataset.getId()),new HashMap<>(),new HashMap<>());
            assertNotNull(queryToExecute);
            log.info("Output query is {}", queryToExecute);
            assertTrue(queryToExecute.contains("COUNT"));
            assertTrue(queryToExecute.contains("isclosed != '{{testvaar}}'"));
        });
    }

    @Test
    public void testBuildQueryClosedPipelineCountWithVariablesAndPredicateLast10Months(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSForRelativeTime").setDisplayName("allOpenNewPipelineCountDSForRelativeTime").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);

        Dataset newDataset = datasetService.createDataset(datasetCopy);
        assertTrue(newDataset.getName().contains(datasetCopy.getName()));
        Variable variable = new Variable().setDisplayName("testvaar")
                .setDatatype("datetime").setHelpText("help for variable").setRequired(true)
                .setVariableValue(new VariableValue().setDefaultValue("last 10 months").setDatatype("datetime"));
        Variable variable1 = datasetService.createVariable(newDataset.getId(), variable);
        ConnectorInfo connectorInfo = createDbConnector();
        assertNotNull(newDataset);
        assertNotNull(variable1);
        Optional<Dataset> allOpenPipelineCountWithRelativeTime = datasetRepo.findById(newDataset.getId());
        assertTrue(allOpenPipelineCountWithRelativeTime.isPresent());
        Connector syncariConn = connectorService.getSyncariConnector();
        Optional<EntityDefinition> edefOpt = entityProxyRepo.findEntityByConnectorIdAndApiName(syncariConn.getId(), "opportunity");
        String opptyId = edefOpt.get().getId();
        allOpenPipelineCountWithRelativeTime.ifPresent(qcp -> {
            DatasetConfig dsconfig = qcp.getDatasetConfig();
            Map<String, Object> pred = dsconfig.getPredicate();
            pred.put("right", Map.of("type", "literal", "value", "{{testvaar}}"));
            pred.put("operator", "gte");
            pred.put("left", Map.of("type", "variable", "dataType", "date", "value", "string", "apiName",
                    "CloseDate","datasetId",opptyId, "datasetApiName", "opportunity", "datasetType", "ENTITY"));
            dsconfig.setPredicate(pred);
            qcp.setDatasetConfig(dsconfig);
            datasetRepo.save(qcp);
            QueryConfig config = datasetService.buildQueryConfigFromDataset(qcp);
            String queryToExecute = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(newDataset.getId()),new HashMap<>(),new HashMap<>());
            assertNotNull(queryToExecute);
            log.info("Output query is {}", queryToExecute);
            assertTrue(queryToExecute.contains("COUNT"));
            assertTrue(queryToExecute.contains(">="));
            assertTrue(!queryToExecute.contains("last 10 months"));
        });
    }

    @Test
    public void testBuildQueryClosedPipelineCountPredicateLast10Months(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSForRelativeTime").setDisplayName("allOpenNewPipelineCountDSForRelativeTime").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);

        Dataset newDataset = datasetService.createDataset(datasetCopy);
        assertTrue(newDataset.getName().contains(datasetCopy.getName()));
        ConnectorInfo connectorInfo = createDbConnector();
        assertNotNull(newDataset);
        Optional<Dataset> allOpenPipelineCountWithRelativeTime = datasetRepo.findById(newDataset.getId());
        assertTrue(allOpenPipelineCountWithRelativeTime.isPresent());
        Connector syncariConn = connectorService.getSyncariConnector();
        Optional<EntityDefinition> edefOpt = entityProxyRepo.findEntityByConnectorIdAndApiName(syncariConn.getId(), "opportunity");
        String opptyId = edefOpt.get().getId();
        allOpenPipelineCountWithRelativeTime.ifPresent(qcp -> {
            DatasetConfig dsconfig = qcp.getDatasetConfig();
            Map<String, Object> pred = dsconfig.getPredicate();
            pred.put("right", Map.of("type", "literal", "value", "last 10 months"));
            pred.put("operator", "gte");
            pred.put("left", Map.of("type", "variable", "dataType", "date", "value", "string", "apiName",
                    "CloseDate","datasetId",opptyId, "datasetApiName", "opportunity", "datasetType", "ENTITY"));
            dsconfig.setPredicate(pred);
            qcp.setDatasetConfig(dsconfig);
            datasetRepo.save(qcp);
            QueryConfig config = datasetService.buildQueryConfigFromDataset(qcp);
            String queryToExecute = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(newDataset.getId()),new HashMap<>(),new HashMap<>());
            assertNotNull(queryToExecute);
            log.info("Output query is {}", queryToExecute);
            assertTrue(queryToExecute.contains("COUNT"));
            assertTrue(queryToExecute.contains(">="));
            assertTrue(!queryToExecute.contains("last 10 months"));
        });
    }

    @Test
    public void testBuildQueryClosedPipelineCountWithVariablesAndPredicateThisYear(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSForRelativeTimeThisYear").setDisplayName("allOpenNewPipelineCountDSForRelativeTime").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);

        Dataset newDataset = datasetService.createDataset(datasetCopy);
        assertTrue(newDataset.getName().contains(datasetCopy.getName()));
        Variable variable = new Variable().setDisplayName("testvaar")
                .setDatatype("boolean").setHelpText("help for variable").setRequired(true)
                .setVariableValue(new VariableValue().setDefaultValue("this year").setDatatype("datetime"));
        Variable variable1 = datasetService.createVariable(newDataset.getId(), variable);
        ConnectorInfo connectorInfo = createDbConnector();
        assertNotNull(newDataset);
        assertNotNull(variable1);
        Optional<Dataset> allOpenPipelineCountWithRelativeTime = datasetRepo.findById(newDataset.getId());
        assertTrue(allOpenPipelineCountWithRelativeTime.isPresent());
        Connector syncariConn = connectorService.getSyncariConnector();
        Optional<EntityDefinition> edefOpt = entityProxyRepo.findEntityByConnectorIdAndApiName(syncariConn.getId(), "opportunity");
        String opptyId = edefOpt.get().getId();
        allOpenPipelineCountWithRelativeTime.ifPresent(qcp -> {
            DatasetConfig dsconfig = qcp.getDatasetConfig();
            Map<String, Object> pred = dsconfig.getPredicate();
            pred.put("right", Map.of("type", "literal", "value", "{{testvaar}}"));
            pred.put("operator", "eq");
            pred.put("left", Map.of("type", "variable", "dataType", "date", "value", "string", "apiName",
                    "CloseDate","datasetId",opptyId, "datasetApiName", "opportunity", "datasetType", "ENTITY"));
            dsconfig.setPredicate(pred);
            qcp.setDatasetConfig(dsconfig);
            datasetRepo.save(qcp);
            QueryConfig config = datasetService.buildQueryConfigFromDataset(qcp);
            String queryToExecute = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(newDataset.getId()),new HashMap<>(),new HashMap<>());
            assertNotNull(queryToExecute);
            log.info("Output query is {}", queryToExecute);
            assertTrue(queryToExecute.contains("COUNT"));
            assertTrue(queryToExecute.contains("DATE_TRUNC"));
            assertTrue(queryToExecute.contains("{{testvaar}}"));
        });
    }

    @Test
    public void testBuildQueryClosedPipelineCountPredicateThisYear(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSForRelativeTimeThisYear").setDisplayName("allOpenNewPipelineCountDSForRelativeTime").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);

        Dataset newDataset = datasetService.createDataset(datasetCopy);
        assertTrue(newDataset.getName().contains(datasetCopy.getName()));
        ConnectorInfo connectorInfo = createDbConnector();
        assertNotNull(newDataset);
        Optional<Dataset> allOpenPipelineCountWithRelativeTime = datasetRepo.findById(newDataset.getId());
        assertTrue(allOpenPipelineCountWithRelativeTime.isPresent());
        Connector syncariConn = connectorService.getSyncariConnector();
        Optional<EntityDefinition> edefOpt = entityProxyRepo.findEntityByConnectorIdAndApiName(syncariConn.getId(), "opportunity");
        String opptyId = edefOpt.get().getId();
        allOpenPipelineCountWithRelativeTime.ifPresent(qcp -> {
            DatasetConfig dsconfig = qcp.getDatasetConfig();
            Map<String, Object> pred = dsconfig.getPredicate();
            pred.put("right", Map.of("type", "literal", "value", "this year"));
            pred.put("operator", "eq");
            pred.put("left", Map.of("type", "variable", "dataType", "date", "value", "string", "apiName",
                    "CloseDate","datasetId",opptyId, "datasetApiName", "opportunity", "datasetType", "ENTITY"));
            dsconfig.setPredicate(pred);
            qcp.setDatasetConfig(dsconfig);
            datasetRepo.save(qcp);
            QueryConfig config = datasetService.buildQueryConfigFromDataset(qcp);
            String queryToExecute = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(newDataset.getId()),new HashMap<>(),new HashMap<>());
            assertNotNull(queryToExecute);
            log.info("Output query is {}", queryToExecute);
            assertTrue(queryToExecute.contains("COUNT"));
            assertTrue(queryToExecute.contains("DATE_TRUNC"));
            assertTrue(queryToExecute.contains("DATE_TRUNC('year',CURRENT_DATE))"));
        });
    }

    @Test
    public void testBuildQueryClosedPipelineCountPredicateThisYearWithDatetime(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("testBuildQueryClosedPipelineCountPredicateThisYearWithDatetime").setDisplayName("testBuildQueryClosedPipelineCountPredicateThisYearWithDatetime").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);

        Dataset newDataset = datasetService.createDataset(datasetCopy);
        assertTrue(newDataset.getName().contains(datasetCopy.getName()));
        ConnectorInfo connectorInfo = createDbConnector();
        Variable variable = new Variable().setDisplayName("testvaar")
                .setDatatype("datetime").setHelpText("help for variable").setRequired(true)
                .setVariableValue(new VariableValue().setDefaultValue("today").setDatatype("datetime"));
        Variable variable1 = datasetService.createVariable(newDataset.getId(), variable);
        assertNotNull(variable1);
        assertNotNull(newDataset);
        Optional<Dataset> allOpenPipelineCountWithRelativeTime = datasetRepo.findById(newDataset.getId());
        assertTrue(allOpenPipelineCountWithRelativeTime.isPresent());
        Connector syncariConn = connectorService.getSyncariConnector();
        Optional<EntityDefinition> edefOpt = entityProxyRepo.findEntityByConnectorIdAndApiName(syncariConn.getId(), "opportunity");
        String opptyId = edefOpt.get().getId();
        allOpenPipelineCountWithRelativeTime.ifPresent(qcp -> {
            DatasetConfig dsconfig = qcp.getDatasetConfig();
            Map<String, Object> pred = dsconfig.getPredicate();
            pred.put("right", Map.of("type", "literal", "value", "{{testvaar}}"));
            pred.put("operator", "eq");
            pred.put("left", Map.of("type", "variable", "dataType", "datetime", "value", "string", "apiName",
                    "CreatedDate","datasetId",opptyId, "datasetApiName", "opportunity", "datasetType", "ENTITY"));
            dsconfig.setPredicate(pred);
            qcp.setDatasetConfig(dsconfig);
            datasetRepo.save(qcp);
            QueryConfig config = datasetService.buildQueryConfigFromDataset(qcp);
            String queryToExecute = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(newDataset.getId()),new HashMap<>(),new HashMap<>());
            assertNotNull(queryToExecute);
            log.info("Output query is {}", queryToExecute);
            assertTrue(queryToExecute.contains("COUNT"));
            assertTrue(queryToExecute.contains("DATE_TRUNC"));
            assertTrue(queryToExecute.contains("{{testvaar}}"));
        });
    }

    @Test
    public void testBuildQueryClosedPipelineCountWithExternalVariableValues(){

        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSEditedForVar").setDisplayName("allOpenNewPipelineCountDSEditedForVar").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);

        Dataset newDataset = datasetService.createDataset(datasetCopy);
        assertTrue(newDataset.getName().contains(datasetCopy.getName()));
        Variable variable = new Variable().setDisplayName("testvaar")
                .setDatatype("boolean").setHelpText("help for variable").setRequired(true)
                .setVariableValue(new VariableValue().setDefaultValue("false").setDatatype("boolean"));
        Variable variable1 = datasetService.createVariable(newDataset.getId(), variable);
        ConnectorInfo connectorInfo = createDbConnector();
        assertNotNull(newDataset);
        assertNotNull(variable1);
        Optional<Dataset> allOpenPipelineCount = datasetRepo.findById(newDataset.getId());
        assertTrue(allOpenPipelineCount.isPresent());
        allOpenPipelineCount.ifPresent(qcp -> {
            DatasetConfig dsconfig = qcp.getDatasetConfig();
            Map<String, Object> pred = dsconfig.getPredicate();
            pred.put("right", Map.of("type", "literal", "value", "{{testvaar}}"));
            dsconfig.setPredicate(pred);
            qcp.setDatasetConfig(dsconfig);
            datasetRepo.save(qcp);
            QueryConfig config = datasetService.buildQueryConfigFromDataset(qcp);
            Map<String, VariableValue> val = new HashMap<>();
            val.put(variable1.getApiName(),new VariableValue().setDefaultValue("true").setDatatype("boolean"));
            String queryToExecute = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(newDataset.getId()),val,new HashMap<>());
            assertNotNull(queryToExecute);
            log.info("Output query is {}", queryToExecute);
            assertTrue(queryToExecute.contains("COUNT"));
            assertTrue(queryToExecute.contains("isclosed != '{{testvaar}}'"));
        });
    }

    @Test
    public void testBuildQueryClosedPipelineCountWithVariableMultivaluedValues(){

        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDSEditedForVar").setDisplayName("allOpenNewPipelineCountDSEditedForVar").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.setDraftStatus(DraftStatus.NEW);

        Dataset newDataset = datasetService.createDataset(datasetCopy);
        assertTrue(newDataset.getName().contains(datasetCopy.getName()));
        Variable variable = new Variable().setDisplayName("testvaar")
                .setDatatype("boolean").setHelpText("help for variable").setRequired(true).setMultiValueField(true)
                .setVariableValue(new VariableValue().setDefaultValue(List.of("true", "false")).setDatatype("boolean"));
        Variable variable1 = datasetService.createVariable(newDataset.getId(), variable);
        ConnectorInfo connectorInfo = createDbConnector();
        assertNotNull(newDataset);
        assertNotNull(variable1);
        Optional<Dataset> allOpenPipelineCount = datasetRepo.findById(newDataset.getId());
        assertTrue(allOpenPipelineCount.isPresent());
        allOpenPipelineCount.ifPresent(qcp -> {
            DatasetConfig dsconfig = qcp.getDatasetConfig();
            Map<String, Object> pred = dsconfig.getPredicate();
            pred.put("operator","in");
            pred.put("right", Map.of("type", "literal", "value", List.of("{{testvaar}}")));
            dsconfig.setPredicate(pred);
            qcp.setDatasetConfig(dsconfig);
            datasetRepo.save(qcp);
            QueryConfig config = datasetService.buildQueryConfigFromDataset(qcp);
            Map<String, VariableValue> val = new HashMap<>();
            val.put(variable1.getApiName(),
                    new VariableValue().setDefaultValue(List.of("true", "false")).setDatatype("boolean"));
            String queryToExecute = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(newDataset.getId()),val,new HashMap<>());
            assertNotNull(queryToExecute);
            log.info("Output query is {}", queryToExecute);
            assertTrue(queryToExecute.contains("COUNT"));
            assertTrue(queryToExecute.contains("WHERE  isclosed in ('{{testvaar}}')"));
        });
    }

    @Test
    public void testBuildQueryQClosePipleineRevByType(){
        Optional<Dataset> dataset = datasetRepo.findApprovedByName("quarterlyClosedPipelineRevenueByTypeDS");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(dataset.isPresent());
        dataset.ifPresent(ds -> {
            Optional<Datacard> quaterlclosePipeline = datacardRepo.findByName("quarterlyClosedPipelineRevenueByTypeDC");
            assertTrue(quaterlclosePipeline.isPresent());
            quaterlclosePipeline.ifPresent(qcp -> {
                Datacard datacard = DatacardSeed.populateDataCard(qcp);
                assertTrue(CollectionUtils.isNotEmpty(datacard.getContents()));
                assertEquals(1,datacard.getContents().size());
                Visualization visualization = datacard.getContents().get(0);
                VizConfig vizConfig = visualization.getConfig();
                List<Sort> sortList = new ArrayList<>();
                QueryConfig config = new QueryConfig().setColumns(vizConfig.getColumns()).setGroupingColumns(((LineVizConfig)vizConfig).getGroupingColumns()).
                        setSortList(sortList);
                config.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
                String query = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo, Optional.of(ds.getId()), new HashMap<>(),new HashMap<>());
                assertNotNull(query);
                log.info("Output query is {}", query);
                assertTrue(query.contains("SUM(\"amount\") \"Total\""));
                assertTrue(query.contains("\"opptype\" FROM syncari_syncari_admin.\"opportunity\""));
            });
        });

    }

    @Test
    public void testBuildQueryClosedPipleineRevByTypeWithDateFilter(){
        Optional<Dataset> dataset = datasetRepo.findApprovedByName("quarterlyClosedPipelineRevenueByTypeDS");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(dataset.isPresent());
        dataset.ifPresent(ds -> {
            Optional<Datacard> quaterlclosePipeline = datacardRepo.findByName("quarterlyClosedPipelineRevenueByTypeDC");
            assertTrue(quaterlclosePipeline.isPresent());
            quaterlclosePipeline.ifPresent(qcp -> {
                Datacard datacard = DatacardSeed.populateDataCard(qcp);
                assertTrue(CollectionUtils.isNotEmpty(datacard.getContents()));
                assertEquals(1,datacard.getContents().size());
                Visualization visualization = datacard.getContents().get(0);
                VizConfig vizConfig = visualization.getConfig();
                Calendar end = Calendar.getInstance();
                end.setTimeInMillis(Instant.EPOCH.toEpochMilli());
                NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
                closedateNoQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                        .setAlias("closedate").setDataType("text");

                QueryField dateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction);
                SimpleDateFormat sm = new SimpleDateFormat("yyyy-MM-dd");

                Map<String, Object> pred1 = Map.of(
                        "left", Map.of("datatype", "string", "type", "variable", "value",dateField.getAlias()),
                        "operator", "gt",
                        "right", Map.of("type", "literal", "value", sm.format(end.getTime()))
                );

                Map<String, Object> pred2 = Map.of(
                        "left", Map.of("datatype", "string", "type", "variable", "value",dateField.getAlias()),
                        "operator", "lt",
                        "right", Map.of("type", "literal", "value", sm.format(Calendar.getInstance().getTime()))
                );

                List<Sort> sortList = new ArrayList<>();
                QueryConfig config = new QueryConfig().setColumns(vizConfig.getColumns()).setGroupingColumns(((LineVizConfig)vizConfig).getGroupingColumns()).
                        setSortList(sortList);
                config.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
                config.setPredicate(Map.of("predicates", List.of(pred1, pred2),"operator", "AND"));
                String query = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(ds.getId()),new HashMap<>(),new HashMap<>());
                assertNotNull(query);
                log.info("Output query is {}", query);
                assertTrue(query.contains("SUM(\"amount\") \"Total\""));
                assertTrue(query.contains("CONCAT('Q',\"fiscalquarter\",' ',\"fiscalyear\") \"Quarter\""));
            });
        });

    }

    @Test
    public void testBuildYearlyClosedPipleineRev(){
        Optional<Dataset> dataset = datasetRepo.findApprovedByName("yearlyClosedPipelineRevenueDS");
        ConnectorInfo connectorInfo = createDbConnector();
        String thisYear = Year.now().toString();
        assertTrue(dataset.isPresent());
        dataset.ifPresent(ds -> {
            Optional<Datacard> yearlyclosedPipeline = datacardRepo.findByName("annualRecurringRevenueDC");
            assertTrue(yearlyclosedPipeline.isPresent());
            yearlyclosedPipeline.ifPresent(qcp -> {
                Datacard datacard = DatacardSeed.populateDataCard(qcp);
                assertTrue(CollectionUtils.isNotEmpty(datacard.getContents()));
                assertEquals(1,datacard.getContents().size());
                Visualization visualization = datacard.getContents().get(0);
                VizConfig vizConfig = visualization.getConfig();
                QueryConfig config = new QueryConfig().setColumns(vizConfig.getColumns()).setGroupingColumns(((MetricVizConfig)vizConfig).getGroupingColumns()).setPredicate(((MetricVizConfig)vizConfig).getPredicate());
                config.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
                config.setGroup(ds.getDatasetConfig().isGroup());
                config.setGroupingColumns(ds.getDatasetConfig().getAggregate());
                config.setPredicate(ds.getDatasetConfig().getPredicate());
                String query = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(ds.getId()), new HashMap<>(),new HashMap<>());
                assertNotNull(query);
                log.info("Output query is {}", query);
                assertTrue(query.contains(String.format("WHERE  ( ( ( \"opportunity\".\"isclosed\" != false and  (\"opportunity\".\"fiscalyear\" = '{{currentyear}}') )  and  (\"opportunity\".\"stagename\" = 'Closed Won') )  and  \"opportunity\".\"iswon\" != false)  GROUP BY \"fiscalyear\"")));
            });
        });
    }

    @Test
    public void testBuildExistingCustomerCount(){
        Optional<Dataset> datasetPipeline = datasetRepo.findApprovedByName("existingCustomerCountDS");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(datasetPipeline.isPresent());
        datasetPipeline.ifPresent(ds -> {
            Optional<Datacard> existingCustomercount = datacardRepo.findByName("existingCustomerCountDC");
            assertTrue(existingCustomercount.isPresent());
            existingCustomercount.ifPresent(existing -> {
                Datacard datacard = DatacardSeed.populateDataCard(existing);
                assertTrue(CollectionUtils.isNotEmpty(datacard.getContents()));
                assertTrue(datacard.getContents().size() == 1);
                Visualization visualization = datacard.getContents().get(0);
                VizConfig vizConfig = visualization.getConfig();
                Map<String, Object> pred1 = vizConfig.getPredicate();

                QueryConfig config = new QueryConfig().setColumns(vizConfig.getColumns());
                config.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
                config.setPredicate(pred1);
                String query = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(ds.getId()),new HashMap<>(),new HashMap<>());
                assertNotNull(query);
                log.info("Output query is {}", query);
                assertTrue(query.contains("\"customercount\" FROM syncari_syncari_admin.\"opportunity\""));
            });
        });

    }

   @Test
    public void testLeadCountBySource(){
        Optional<Dataset> dataset = datasetRepo.findApprovedByName("leadsBySourceDS");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(dataset.isPresent());
        dataset.ifPresent(ds -> {
            Optional<Datacard> leadCountBySource = datacardRepo.findByName("leadsBySourceDC");
            assertTrue(leadCountBySource.isPresent());
            leadCountBySource.ifPresent(dc -> {
                Datacard datacard = DatacardSeed.populateDataCard(dc);
                assertTrue(CollectionUtils.isNotEmpty(datacard.getContents()));
                assertTrue(datacard.getContents().size() == 1);
                Visualization visualization = datacard.getContents().get(0);
                VizConfig vizConfig = visualization.getConfig();
                QueryConfig config = new QueryConfig().setColumns(vizConfig.getColumns()).setGroupingColumns(((TableVizConfig)vizConfig).getGroupingColumns()).setPredicate(((TableVizConfig)vizConfig).getPredicate());
                config.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
                String query = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(ds.getId()), new HashMap<>(),new HashMap<>());
                assertNotNull(query);
                log.info("Output query is {}", query);
                assertTrue(query.contains("Lead Count"));
                assertTrue(query.contains("Lead Source"));
            });
        });

    }

    @Test
    public void testSalesFunnelByStage(){
        Optional<Dataset> dataset = datasetRepo.findApprovedByName("salesFunnelDS");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(dataset.isPresent());
        dataset.ifPresent(ds -> {
            Optional<Datacard> salesFunnel = datacardRepo.findByName("salesFunnelDC");
            assertTrue(salesFunnel.isPresent());
            salesFunnel.ifPresent(dc -> {
                Datacard datacard = DatacardSeed.populateDataCard(dc);
                assertTrue(CollectionUtils.isNotEmpty(datacard.getContents()));
                assertEquals(1,datacard.getContents().size());
                Visualization visualization = datacard.getContents().get(0);
                VizConfig vizConfig = visualization.getConfig();
                QueryConfig config = new QueryConfig().setColumns(vizConfig.getColumns()).setGroupingColumns(((TableVizConfig)vizConfig).getGroupingColumns()).setPredicate(((TableVizConfig)vizConfig).getPredicate());
                config.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
                NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
                closedateNoQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                        .setAlias("closedate").setDataType("text");

                QueryField dateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction).setDisplayFormat("text")
                        .setDescription("Date");

                LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
                DateRange range = new DateRange(currentLocalDate, currentLocalDate.plusMonths(12));
                DateFilter dateFilter = new DateFilter().setField(dateField).setDateRange(range);

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                Map<String, Object> pred1 = Map.of(
                        "left", Map.of("datatype", "string", "type", "variable", "value",dateField.getAlias()),
                        "operator", "gt",
                        "right", Map.of("type", "literal", "value", dateFilter.getDateRange().getStart().format(formatter))
                );

                Map<String, Object> pred2 = Map.of(
                        "left", Map.of("datatype", "string", "type", "variable", "value",dateField.getAlias()),
                        "operator", "lt",
                        "right", Map.of("type", "literal", "value", dateFilter.getDateRange().getEnd().format(formatter))
                );
                Map<String, Object> predicateMap = new HashMap<>();
                predicateMap.putAll(Map.of("predicates", List.of(pred1, pred2),"operator", "AND"));
                config.setPredicate(predicateMap);
                config.setGroup(ds.getDatasetConfig().isGroup());
                config.setGroupingColumns(ds.getDatasetConfig().getAggregate());
                String query = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(ds.getId()), new HashMap<>(),new HashMap<>());
                assertNotNull(query);
                log.info("Output query is {}", query);
                assertTrue(query.contains("closedate >"));
                assertTrue(query.contains("closedate <"));
                assertTrue(query.contains("GROUP BY \"Stage Name\""));
            });
        });

    }

    @Test
    public void testSQLCountByOwner(){
        Optional<Dataset> dataset = datasetRepo.findApprovedByName("sqlCountByOwnerDS");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(dataset.isPresent());
        dataset.ifPresent(ds -> {
            Optional<Datacard> leadCountBySource = datacardRepo.findByName("sqlCountByOwnerDC");
            assertTrue(leadCountBySource.isPresent());
            leadCountBySource.ifPresent(dc -> {
                Datacard datacard = DatacardSeed.populateDataCard(dc);
                assertTrue(CollectionUtils.isNotEmpty(datacard.getContents()));
                assertTrue(datacard.getContents().size() == 1);
                Visualization visualization = datacard.getContents().get(0);
                VizConfig vizConfig = visualization.getConfig();
                QueryConfig config = new QueryConfig().setColumns(vizConfig.getColumns()).setGroupingColumns(((TableVizConfig)vizConfig).getGroupingColumns()).setPredicate(((TableVizConfig)vizConfig).getPredicate());
                config.setLimit(vizConfig.getLimit());
                config.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
                String query = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(ds.getId()), new HashMap<>(),new HashMap<>());
                assertNotNull(query);
                log.info("Output query is {}", query);
                assertTrue(query.contains("Qualified Lead Count"));
                assertTrue(query.contains("Owner Name"));
            });
        });

    }

    @Test
    public void testMQLCount(){
        Optional<Dataset> dataset = datasetRepo.findApprovedByName("mqlCountInQuarterDS");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(dataset.isPresent());
        dataset.ifPresent(ds -> {
            Optional<Datacard> leadCountBySource = datacardRepo.findByName("mqlCountInQuarterDC");
            assertTrue(leadCountBySource.isPresent());
            leadCountBySource.ifPresent(dc -> {
                Datacard datacard = DatacardSeed.populateDataCard(dc);
                assertTrue(CollectionUtils.isNotEmpty(datacard.getContents()));
                assertTrue(datacard.getContents().size() == 1);
                Visualization visualization = datacard.getContents().get(0);
                VizConfig vizConfig = visualization.getConfig();
                QueryConfig config = new QueryConfig().setColumns(vizConfig.getColumns()).setGroupingColumns(((MetricVizConfig)vizConfig).getGroupingColumns()).setPredicate(((MetricVizConfig)vizConfig).getPredicate());
                config.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
                String query = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(ds.getId()),  new HashMap<>(),new HashMap<>());
                assertNotNull(query);
                log.info("Output query is {}", query);
                assertTrue(query.contains("Lead Count"));
            });
        });

    }


    private ConnectorInfo createDbConnector() {
        ConnectorInfo connector = new ConnectorInfo("123", "postgres", null,"instance1", "rohitkanchan", "");
        connector.getMetaConfig().put(Constants.CLUSTER_NAME, "localhost:5432");
        connector.getMetaConfig().put(Constants.DATABASE_NAME, "syncari_syncari_admin");
        connector.getMetaConfig().put(PostgresService.SCHEMA_NAME, "syncari_syncari_admin");
        return connector;
    }

    @Test
    public void testBuildtop10custbyrev(){
        Optional<Dataset> datasetPipeline = datasetRepo.findApprovedByName("top10CustomersByRevenueDS");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(datasetPipeline.isPresent());
        datasetPipeline.ifPresent(ds -> {
            Optional<Datacard> existingCustomercount = datacardRepo.findByName("top10CustomersByRevenueDC");
            assertTrue(existingCustomercount.isPresent());
            existingCustomercount.ifPresent(existing -> {
                Datacard datacard = DatacardSeed.populateDataCard(existing);
                assertTrue(CollectionUtils.isNotEmpty(datacard.getContents()));
                assertTrue(datacard.getContents().size() == 1);
                Visualization visualization = datacard.getContents().get(0);
                VizConfig vizConfig = visualization.getConfig();
                Map<String, Object> pred1 = vizConfig.getPredicate();
                List<QueryField> allfields = vizConfig.getColumns();
                Optional<QueryField> field = allfields.stream().filter(x -> x.getAlias().equalsIgnoreCase("Amount")).findFirst();
                QueryConfig config = new QueryConfig().setColumns(vizConfig.getColumns());
                config.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
                config.setPredicate(pred1);
                config.setSortList(List.of(new Sort(new QField().setName(field.get().getAlias()), false)));
                config.setLimit(10);
                String query = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(ds.getId()), new HashMap<>(),new HashMap<>());
                assertNotNull(query);
                log.info("Output query is {}", query);
                assertTrue(query.contains("\"Amount\""));
                assertTrue(query.contains("LIMIT"));
            });
        });

    }

    @Test
    public void testOpenEscalatedTicketsCount(){
        Optional<Dataset> dataset = datasetRepo.findApprovedByName("openEscalatedTicketCountDS");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(dataset.isPresent());
        dataset.ifPresent(ds -> {
            Optional<Datacard> openEscalatedTicketCount = datacardRepo.findByName("openEscalatedTicketCountDC");
            assertTrue(openEscalatedTicketCount.isPresent());
            openEscalatedTicketCount.ifPresent(dc -> {
                Datacard datacard = DatacardSeed.populateDataCard(dc);
                assertTrue(CollectionUtils.isNotEmpty(datacard.getContents()));
                assertTrue(datacard.getContents().size() == 1);
                Visualization visualization = datacard.getContents().get(0);
                VizConfig vizConfig = visualization.getConfig();
                QueryConfig config = new QueryConfig().setColumns(vizConfig.getColumns()).setGroupingColumns(((MetricVizConfig)vizConfig).getGroupingColumns()).setPredicate(((MetricVizConfig)vizConfig).getPredicate());
                config.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
                String query = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(ds.getId()), new HashMap<>(),new HashMap<>());
                assertNotNull(query);
                log.info("Output query is {}", query);
                assertTrue(query.contains("Open Escalated Ticket Count"));
            });
        });
    }

    @Test
    public void testOpenTicketsCountByAccount(){
        Optional<Dataset> dataset = datasetRepo.findApprovedByName("openTicketsCountByAccountDS");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(dataset.isPresent());
        dataset.ifPresent(ds -> {
            Optional<Datacard> openEscalatedTicketCount = datacardRepo.findByName("openTicketsCountByAccountDC");
            assertTrue(openEscalatedTicketCount.isPresent());
            openEscalatedTicketCount.ifPresent(dc -> {
                Datacard datacard = DatacardSeed.populateDataCard(dc);
                assertTrue(CollectionUtils.isNotEmpty(datacard.getContents()));
                assertTrue(datacard.getContents().size() == 1);
                Visualization visualization = datacard.getContents().get(0);
                VizConfig vizConfig = visualization.getConfig();
                QueryConfig config = new QueryConfig().setColumns(vizConfig.getColumns()).setGroupingColumns(((TableVizConfig)vizConfig).getGroupingColumns()).setPredicate(((TableVizConfig)vizConfig).getPredicate());
                config.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
                String query = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(ds.getId()), new HashMap<>(),new HashMap<>());
                assertNotNull(query);
                log.info("Output query is {}", query);
                assertTrue(query.contains("Account Name"));
                assertTrue(query.contains("Open Tickets"));
            });
        });

    }
    @Test
    public void testBuildRevenueLostByChurnedCustomer(){
        Optional<Dataset> datasetPipeline = datasetRepo.findApprovedByName("revenueChurnByQuarterDS");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(datasetPipeline.isPresent());
        datasetPipeline.ifPresent(ds -> {
            Optional<Datacard> revenueChurnByQuarter = datacardRepo.findByName("revenueChurnByQuarterDC");
            assertTrue(revenueChurnByQuarter.isPresent());
            revenueChurnByQuarter.ifPresent(existing -> {
                Datacard datacard = DatacardSeed.populateDataCard(existing);
                assertTrue(CollectionUtils.isNotEmpty(datacard.getContents()));
                assertTrue(datacard.getContents().size() == 1);
                Visualization visualization = datacard.getContents().get(0);
                VizConfig vizConfig = visualization.getConfig();
                Map<String, Object> pred1 = vizConfig.getPredicate();

                List<Sort> sortList = new ArrayList<>();
                QueryConfig config = new QueryConfig().setColumns(vizConfig.getColumns());
                config.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
                config.setPredicate(pred1);
                config.setGroupingColumns(vizConfig.getGroupingColumns());
                config.setSortList(sortList);
                NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
                closedateNoQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                        .setAlias("closedate").setDataType("text");

                QueryField dateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction).setDisplayFormat("text")
                        .setDescription("Date");

                LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
                DateRange range = new DateRange(currentLocalDate.minusMonths(12), currentLocalDate);
                DateFilter dateFilter = new DateFilter().setField(dateField).setDateRange(range);

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                Map<String, Object> pred2 = Map.of(
                        "left", Map.of("datatype", "string", "type", "variable", "value",dateField.getAlias()),
                        "operator", "gt",
                        "right", Map.of("type", "literal", "value", dateFilter.getDateRange().getStart().format(formatter))
                );

                Map<String, Object> pred3 = Map.of(
                        "left", Map.of("datatype", "string", "type", "variable", "value",dateField.getAlias()),
                        "operator", "lt",
                        "right", Map.of("type", "literal", "value", dateFilter.getDateRange().getEnd().format(formatter))
                );
                Map<String, Object> predicateMap = new HashMap<>();
                predicateMap.putAll(Map.of("predicates", List.of(pred2, pred3),"operator", "AND"));
                config.setPredicate(predicateMap);
                String query = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo,Optional.of(ds.getId()), new HashMap<>(),new HashMap<>());
                assertNotNull(query);
                log.info("Output query is {}", query);
                assertTrue(query.contains("Revenue"));
                assertTrue(query.contains("Quarter"));
            });
        });

    }

    private static LocalDateTime getCurrentQuarterFirsDate(){
        LocalDateTime localDate = LocalDateTime.now();
        LocalDateTime firstDayOfQuarter = localDate.with(localDate.getMonth().firstMonthOfQuarter())
                .with(TemporalAdjusters.firstDayOfMonth());
        return firstDayOfQuarter;

    }

    @Test
    public void testBuildAllOpenPipelineCountNew(){
        Optional<Dataset> dataset = datasetRepo.findApprovedByName("allOpenNewPipelineCountDS");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(dataset.isPresent());
        dataset.ifPresent(ds -> {
            Optional<Datacard> openPipelineCount = datacardRepo.findByName("allOpenPipelineNewCount");
            assertTrue(openPipelineCount.isPresent());
            openPipelineCount.ifPresent(qcp -> {
                Datacard datacard = DatacardSeed.populateDataCard(qcp);
                assertTrue(CollectionUtils.isNotEmpty(datacard.getContents()));
                assertTrue(datacard.getContents().size() == 1);
                Visualization visualization = datacard.getContents().get(0);
                visualization = datacardService.createVisualizationFromDataset(visualization);
                VizConfig vizConfig = visualization.getConfig();
                QueryConfig config = new QueryConfig().setColumns(vizConfig.getColumns()).setPredicate(ds.getDatasetConfig().getPredicate());
                config.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
                String query = postgresInsightsQueryBuilder.buildQuery(config, connectorInfo, Optional.of(ds.getId()),new HashMap<>(),new HashMap<>());
                assertNotNull(query);
                log.info("Output query is {}", query);
                assertTrue(query.contains("COUNT(\"opportunity\".\"closedate\")"));
            });
        });

    }

    // Dataset Alias test
    // Create dataset with datasource alias name
    // Create dataset with multiple same entities

    @Test
    public void testBuildDatasetWithDatasourceAlias(){
        Connector syncariConn = connectorService.getSyncariConnector();
        Optional<EntityDefinition> edefOpt = entityProxyRepo.findEntityByConnectorIdAndApiName(syncariConn.getId(), "opportunity");
        Dataset dsToCreate = new Dataset().setName("testwithalias").setDisplayName("testwithalias").setSeeded(false).setVersion("v1");
        dsToCreate.setDraftStatus(DraftStatus.APPROVED);
        DatasetConfig config = new DatasetConfig().setFromDatasets(List.of(new DatasetFrom().setDatasetId(edefOpt.get().getId())
                .setDatasetType(DatasourceType.ENTITY).setDatastoreName(edefOpt.get().getDataStoreName())
                .setApiName(edefOpt.get().getApiName()).setDisplayName(edefOpt.get().getDisplayName()).setAlias("oppty")));
        config.setProjectionsList(List.of(new Projection().setFunction(new NoQueryFunction().setColumns(List.of(new QField().setName("Name")
                .setDatasetId(edefOpt.get().getId()).setDatasourceAlias("oppty").setDataType("text"))))));
        dsToCreate.setDatasetConfig(config);
        datasetRepo.save(dsToCreate);
        Optional<Dataset> dataset = datasetRepo.findApprovedByName("testwithalias");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(dataset.isPresent());
        dataset.ifPresent(ds -> {
            QueryConfig queryConfig = new QueryConfig().setColumns(List.of(new SimpleQField().setQueryFunction(ds.getDatasetConfig().getProjectionsList().stream().findFirst().get().getFunction())));
            queryConfig.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
            String query = postgresInsightsQueryBuilder.buildQuery(queryConfig, connectorInfo, Optional.of(ds.getId()),new HashMap<>(),new HashMap<>());
            assertNotNull(query);
            log.info("Output query is {}", query);
            assertEquals("SELECT \"oppty\".\"name\" \"name\" FROM syncari_syncari_admin.\"opportunity\" \"oppty\"",query);
        });

    }

    @Test
    public void testBuildDatasetWithDatasourceAliasAndSelfJoin(){
        Connector syncariConn = connectorService.getSyncariConnector();
        Optional<EntityDefinition> edefOpt = entityProxyRepo.findEntityByConnectorIdAndApiName(syncariConn.getId(), "opportunity");
        Dataset dsToCreate = new Dataset().setName("testwithaliaswithselfjoin").setDisplayName("testwithaliaswithselfjoin").setSeeded(false).setVersion("v1");
        dsToCreate.setDraftStatus(DraftStatus.APPROVED);
        DatasetFrom from1 = new DatasetFrom().setDatasetId(edefOpt.get().getId())
                .setDatasetType(DatasourceType.ENTITY).setDatastoreName(edefOpt.get().getDataStoreName())
                .setApiName(edefOpt.get().getApiName()).setDisplayName(edefOpt.get().getDisplayName()).setAlias("oppty1");
        DatasetFrom from2 = new DatasetFrom().setDatasetId(edefOpt.get().getId())
                .setDatasetType(DatasourceType.ENTITY).setDatastoreName(edefOpt.get().getDataStoreName())
                .setApiName(edefOpt.get().getApiName()).setDisplayName(edefOpt.get().getDisplayName()).setAlias("oppty2");
        DatasetConfig config = new DatasetConfig().setFromDatasets(List.of(from1,from2));
        config.setProjectionsList(List.of(new Projection().setFunction(new NoQueryFunction().setColumns(List.of(new QField().setName("Name")
                .setDatasetId(edefOpt.get().getId()).setDatasourceAlias("oppty").setDataType("text"))))));
        QField qfFrom = new QField().setName("syncariid")
                .setDatasetId(edefOpt.get().getId()).setDatasourceAlias("oppty1").setDataType("text");
        QField qfTo = new QField().setName("syncariid")
                .setDatasetId(edefOpt.get().getId()).setDatasourceAlias("oppty2").setDataType("text");
        config.setJoin(List.of(new Join().setDatasetFieldFrom(qfFrom).setDatasetFieldTo(qfTo).setJoinType(JoinType.Inner)));
        dsToCreate.setDatasetConfig(config);
        datasetRepo.save(dsToCreate);
        Optional<Dataset> dataset = datasetRepo.findApprovedByName("testwithaliaswithselfjoin");
        ConnectorInfo connectorInfo = createDbConnector();
        assertTrue(dataset.isPresent());
        dataset.ifPresent(ds -> {
            QueryConfig queryConfig = new QueryConfig().setColumns(List.of(new SimpleQField().setQueryFunction(ds.getDatasetConfig().getProjectionsList().stream().findFirst().get().getFunction())));
            queryConfig.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
            queryConfig.setJoins(ds.getDatasetConfig().getJoin());
            String query = postgresInsightsQueryBuilder.buildQuery(queryConfig, connectorInfo, Optional.of(ds.getId()),new HashMap<>(),new HashMap<>());
            assertNotNull(query);
            log.info("Output query is {}", query);
        });

    }

    @Test
    public void testAvg() {
        String expected = "SELECT AVG(\"oppty\".\"amount\") FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("avgFunc", expected, AggFunctions.AVG.createQueryFunction());
    }

    @Test
    public void testMode() {
        String expected = "SELECT mode() within group (order by \"oppty\".\"amount\") FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("mode", expected, AggFunctions.MODE.createQueryFunction());
    }

    @Test
    public void testAdd() {
        String expected = "SELECT (trunc(cast(\"oppty\".\"amount\" as decimal), 2) + trunc(cast(\"oppty\".\"total\" as decimal), 2)) FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("addition", expected, AggFunctions.ADD.createQueryFunction(), "amount", "total");
    }

    @Test
    public void testMultiply() {
        String expected = "SELECT (trunc(cast(\"oppty\".\"amount\" as decimal), 2) * trunc(cast(\"oppty\".\"total\" as decimal), 2)) FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("multiplication", expected, AggFunctions.MULTIPLY.createQueryFunction(), "amount", "total");
    }

    @Test
    public void testRemainder() {
        String expected = "SELECT (trunc(cast(\"oppty\".\"amount\" as decimal), 2) % trunc(cast(\"oppty\".\"total\" as decimal), 2)) FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("addition", expected, AggFunctions.REMAINDER.createQueryFunction(), "amount", "total");
    }

    @Test
    public void testPower() {
        String expected = "SELECT (trunc(cast(\"oppty\".\"amount\" as decimal), 2) ^ trunc(cast(\"oppty\".\"total\" as decimal), 2)) FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("addition", expected, AggFunctions.POWER.createQueryFunction(), "amount", "total");
    }

    @Test
    public void testMedian() {
        String expected = "SELECT percentile_cont(0.50) within group (order by \"oppty\".\"amount\" asc) FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("median", expected, AggFunctions.MEDIAN.createQueryFunction());
    }

    @Test
    public void testPercentile75() {
        String expected = "SELECT percentile_cont(0.75) within group (order by \"oppty\".\"amount\" asc) FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("percentile75", expected, AggFunctions.PERCENTILE_75.createQueryFunction());
    }

    @Test
    public void testPercentile25() {
        String expected = "SELECT percentile_cont(0.25) within group (order by \"oppty\".\"amount\" asc) FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("percentile25", expected, AggFunctions.PERCENTILE_25.createQueryFunction());
    }

    @Test
    public void testStdDev() {
        String expected = "SELECT STDDEV_POP(\"oppty\".\"amount\") FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("stddev", expected, AggFunctions.STDDEV_POP.createQueryFunction());
    }

    @Test
    public void testVariance() {
        String expected = "SELECT VAR_POP(\"oppty\".\"amount\") FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("variance", expected, AggFunctions.VAR_POP.createQueryFunction());
    }

    @Test
    public void testMin() {
        String expected = "SELECT MIN(\"oppty\".\"amount\") FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("minFunc", expected, AggFunctions.MIN.createQueryFunction());
    }

    @Test
    public void testMax() {
        String expected = "SELECT MAX(\"oppty\".\"amount\") FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("maxFunc", expected, AggFunctions.MAX.createQueryFunction());
    }

    @Test
    public void testSubtract() {
        String expected = "SELECT (trunc(cast(\"oppty\".\"amount\" as decimal), 2) - trunc(cast(\"oppty\".\"total\" as decimal), 2)) FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("subtraction", expected, AggFunctions.SUBTRACT.createQueryFunction(), "amount", "total");
    }

    @Test
    public void testGrowth() {
        String expected = "SELECT (ROUND(CAST(((trunc(cast(\"oppty\".\"amount\" as decimal), 2) - trunc(cast(\"oppty\".\"total\" as decimal), 2))/NULLIF(trunc(cast(\"oppty\".\"total\" as decimal), 2),0))*100 as numeric), 2)) FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        testAggregateFunction("growth", expected, AggFunctions.GROWTH.createQueryFunction(), "amount", "total");
    }

    @Test
    public void testFormula() {
        String expected = "SELECT (ROUND ( 100 * ( \"oppty\".\"amount\" - \"oppty\".\"total\" ) /nullif( \"oppty\".\"total\" ,0),2)) FROM syncari_syncari_admin.\"opportunity\" \"oppty\"";
        final List<QField> qFields = List.of(
                new QField("ROUND", QField.Type.LITERAL),
                new QField("(", QField.Type.LITERAL),
                new QField("100", QField.Type.LITERAL),
                new QField("*", QField.Type.LITERAL),
                new QField("(", QField.Type.LITERAL),
                new QField("amount", QField.Type.COLUMN),
                new QField("-", QField.Type.LITERAL),
                new QField("total", QField.Type.COLUMN),
                new QField(")", QField.Type.LITERAL),
                new QField("/nullif(", QField.Type.LITERAL),
                new QField("total", QField.Type.COLUMN),
                new QField(",0),2)", QField.Type.LITERAL)
        );
        testAggregateFunction("formula", expected, AggFunctions.FORMULA.createQueryFunction(), qFields);
    }

    private void testAggregateFunction(String functionName, String expectedQuery, QueryFunction aggFunction, List<QField> fields) {
        Connector syncariConn = connectorService.getSyncariConnector();
        Optional<EntityDefinition> edefOpt = entityProxyRepo.findEntityByConnectorIdAndApiName(syncariConn.getId(), "opportunity");
        Dataset dsToCreate = new Dataset().setName(functionName).
                setDisplayName(functionName)
                .setSeeded(false).setVersion("v1");
        dsToCreate.setDraftStatus(DraftStatus.APPROVED);
        DatasetConfig config = new DatasetConfig().setFromDatasets(List.of(new DatasetFrom().setDatasetId(edefOpt.get().getId())
                .setDatasetType(DatasourceType.ENTITY).setDatastoreName(edefOpt.get().getDataStoreName())
                .setApiName(edefOpt.get().getApiName()).setDisplayName(edefOpt.get().getDisplayName()).setAlias("oppty")));
        QField defaultField = new QField().setName("amount")
                .setDatasetId(edefOpt.get().getId()).setDatasourceAlias("oppty").setDataType("double");
        List<QField> fieldList = (fields == null || fields.isEmpty()) ? List.of(defaultField) : fields;
        fieldList.forEach(f -> {
            if (f.isColumnType()) {
                f.setDatasetId(edefOpt.get().getId()).setDatasourceAlias("oppty").setDataType("double");
            }
        });
        config.setProjectionsList(List.of(new Projection().setFunction(aggFunction
                .setColumns(fieldList))));
        dsToCreate.setDatasetConfig(config);
        datasetRepo.save(dsToCreate);
        ConnectorInfo connectorInfo = createDbConnector();
        Optional<Dataset> dataset = datasetRepo.findApprovedByName(functionName);
        assertTrue(dataset.isPresent());
        dataset.ifPresent(ds -> {
            QueryConfig queryConfig = new QueryConfig().setColumns(List.of(new SimpleQField().setQueryFunction(ds.getDatasetConfig().getProjectionsList().stream().findFirst().get().getFunction())));
            queryConfig.setFromDatasets(ds.getDatasetConfig().getFromDatasets());
            String query = postgresInsightsQueryBuilder.buildQuery(queryConfig, connectorInfo, Optional.of(ds.getId()), new HashMap<>(),new HashMap<>());
            assertEquals(expectedQuery, query);
            log.info("Output query is {}", query);
        });
    }

    private void testAggregateFunction(String functionName, String expectedQuery, QueryFunction aggFunction, String... fields) {
        List<QField> qfields = (fields == null || fields.length == 0) ? List.of() : Arrays.stream(fields).map(f -> new QField().setName(f)).collect(Collectors.toList());
        testAggregateFunction(functionName, expectedQuery, aggFunction, qfields);
    }
}
