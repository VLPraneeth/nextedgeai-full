package com.syncari.core.service;

import com.syncari.connector.ConnectorInfo;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.insights.query.InsightsQueryBuilder;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.insights.Projection;
import com.syncari.core.model.insights.QueryConfig;
import com.syncari.core.model.insights.QueryFunction;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetFrom;
import com.syncari.core.model.insights.dataset.Join;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.repositories.customer.DatasetRepo;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static org.junit.Assert.*;

public class DatasetServiceTest extends AbstractSyncariTest {

    @Autowired
    DatasetRepo datasetRepo;

    @Autowired
    DatasetService service;

    @Autowired
    InsightsService insightsService;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    InsightsQueryBuilder queryBuilder;

    @Autowired
    SchemaService schemaService;

    @Autowired
    ComponentDependencyService dependencyService;

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void testReadSampleData() {
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset dataset1 = service.getDataset(dataset.stream().findFirst().get().getId());
        assertNotNull(dataset1);
        assertTrue(dataset1.getName().contains(dataset.stream().findFirst().get().getName()));
        QueryConfig config = service.buildQueryConfigFromDataset(dataset1);
        ConnectorInfo connectorInfo = datastoreService.toConnectorInfo(Optional.ofNullable(datastoreService.createOrGetSyncariDSConnector(SyncariContext.getSyncariId())));
        String query =  queryBuilder.buildQuery(config, connectorInfo, Optional.empty(),new HashMap<>(),new HashMap<>());

        // Step 2: validate query
        boolean isValidQuery = queryBuilder.validateQuery(query);
        validateCondition(!isValidQuery, String.format("Invalid Query %s", query));

        Map<String, String> fields = service.toQueryFieldsFromProjectionList(dataset1.getDatasetConfig().getProjectionsList(), null,         dataset1.getDatasetConfig().getFromDatasets().stream().collect(Collectors.toMap(DatasetFrom::getDatasetId, DatasetFrom::getDatastoreName))
        ).stream().collect(Collectors.toMap(c -> c.getAlias(), c -> (null != c.getDisplayFormat() ? c.getDisplayFormat() : c.getQueryFunction().getDataType())));
        assertTrue(MapUtils.isNotEmpty(fields));
    }

    @Test
    public void createDraftForSeededDataset() {
        Dataset seeded = service.getAllApprovedDatasetsWithVersion().stream().filter(d -> d.isSeeded()).findFirst().get();
        try {
            service.createDraftFor(seeded);
            fail();
        } catch (SyncariValidationException ex) {
            assertEquals("Draft can not be created for seeded datasets.", ex.getMessage());
        }
    }

    @Test
    public void testCreateDatasetValidation(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName(null).setDisplayName("allOpenNewPipelineCountDSForDraft").setDescription("New description");
        datasetCopy.setSeeded(false);
        try{
            service.createDataset(null);
            fail();
        }catch (SyncariValidationException e){
            assertTrue(e.getMessage().contains("Dataset information cannot be empty to create dataset"));
        }
        datasetCopy.setName("test").setDisplayName(null).setDescription("New description");
        try{
            service.createDataset(datasetCopy);
            fail();
        }catch (SyncariValidationException e){
            assertTrue(e.getMessage().contains("Dataset Display Name cannot be empty"));
        }
    }

    @Test
    public void testUpdateDatasetValidation(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("testvalidation").setDisplayName("allOpenNewPipelineCountDSForDraft").setDescription("New description");
        datasetCopy.setSeeded(false);

        Dataset datasetDTOCreated = service.createDataset(datasetCopy);
        assertNotNull(datasetDTOCreated);
        try{
            service.updateDataset(datasetDTOCreated.getId(), null);
            fail();
        }catch (SyncariValidationException e){
            assertTrue(e.getMessage().contains("Dataset information cannot be empty to create dataset"));
        }
    }


    @Test
    public void testCreateDatasetProjectionValidation(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("testvalidation").setDisplayName("allOpenNewPipelineCountDSForDraft").setDescription("New description");
        datasetCopy.setSeeded(false);
        datasetCopy.getDatasetConfig().setProjectionsList(List.of());
        try{
            service.createDataset(datasetCopy);
            fail();
        }catch (SyncariValidationException e){
            assertEquals(e.getMessage(), "Please add one or more fields from your selected Data Set(s).");
        }
    }

    @Test
    @Ignore("Add back when duplicate apiName validation is reimplemented")
    public void testCreateDatasetProjectionDupsValidation(){
        List<Dataset> dataset = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(dataset));
        assertTrue(dataset.stream().findFirst().isPresent());
        Dataset datasetCopy = dataset.stream().findFirst().get().makeCopy();
        datasetCopy.setName("testvalidation").setDisplayName("allOpenNewPipelineCountDSForDraft").setDescription("New description");
        datasetCopy.setSeeded(false);
        List<Projection> projections = datasetCopy.getDatasetConfig().getProjectionsList();
        assertTrue(projections.stream().findFirst().isPresent());
        Projection proj = projections.stream().findFirst().get();
        QueryFunction f = proj.getFunction().makeCopy();
        Projection projection = new Projection().setAliasName(proj.getAliasName()).setFunction(f);
        projections.add(projection);
        try{
            service.createDataset(datasetCopy);
            fail();
        }catch (SyncariValidationException e){
            assertTrue(e.getMessage().contains("Dataset projections either selected fields or calculated fields contains same alias name. Please use unique names."));
        }
    }


    @Test
    public void createDataset_DuplicateApiName(){
        List<Dataset> datasetList = datasetRepo.findByName("allOpenNewPipelineCountDS");
        Dataset dataset = datasetList.stream().findFirst().get().makeCopy();
        dataset.setName("testDataset").setDisplayName("Test Dataset").setDescription("Test Datset Description").setVersion("v1");

        Dataset saved = service.createDataset(dataset);
        assertTrue(saved.isApproved());

        List<Dataset> datasetList2 = datasetRepo.findByName("allOpenNewPipelineCountDS");
        Dataset dataset2 = datasetList.stream().findFirst().get().makeCopy();

        dataset2.setName("testDataset").setDisplayName("Test Dataset - 2").setDescription("Test Dataset Description - 2").setVersion("v1");
        var duplicateDS = service.createDataset(dataset2);
        assertEquals("testDataset_1", duplicateDS.getName());
        assertEquals("Test Dataset - 2", duplicateDS.getDisplayName());

    }

    @Test
    public void testDatasetDependency_CreateAndDelete(){
        List<Dataset> datasets = datasetRepo.findByName("allOpenNewPipelineCountDS");
        assertTrue(CollectionUtils.isNotEmpty(datasets));
        assertTrue(datasets.stream().findFirst().isPresent());
        Dataset datasetCopy = datasets.stream().findFirst().get().makeCopy();
        datasetCopy.setName("allOpenNewPipelineCountDS_"+ RandomUtils.nextInt(1, 1000));
        datasetCopy.setSeeded(false);

        if(datasetCopy.getDatasetConfig().getFromDatasets().isEmpty()) {
            // populate fromDatasets from fromEntityIdWithAlias map
            datasetCopy.getDatasetConfig().getFromDatasets().forEach(fd -> {
                var e = schemaService.getSyncariEntityByName(fd.getApiName()).get();
                datasetCopy.getDatasetConfig().getFromDatasets().add(
                        new DatasetFrom().setApiName(e.getApiName()).setDisplayName(e.getDisplayName())
                                .setDatastoreName(e.getDataStoreName()).setDatasetId(e.getId())
                                .setDatasetType(DatasourceType.ENTITY));
            });
        }
        var saved = service.createDataset(datasetCopy);
        assertTrue(saved.isApproved());
        var dependencies = dependencyService.findDependenciesBy(saved.getId(), ComponentType.dataset);
        assertFalse(dependencies.isEmpty());
        var d = dependencies.get(0);
        assertEquals(ComponentType.dataset, d.getFromComponent());
        assertEquals(saved.getId(), d.getFromId());
        assertEquals(ComponentType.entity, d.getToComponent());

        // delete dataset
        service.deleteDataset(saved);
        dependencies = dependencyService.findDependenciesBy(saved.getId(), ComponentType.dataset);
        assertTrue(dependencies.isEmpty());
    }

    @Test
    public void testFetchAutoJoinSuggestions(){
        Optional<EntityDefinition> account = schemaService.getSyncariEntityByName("account");
        Optional<EntityDefinition> oppty = schemaService.getSyncariEntityByName("opportunity");
        assertTrue(oppty.isPresent());
        assertTrue(account.isPresent());
        EntityDefinition opp = oppty.get();
        EntityDefinition acct = account.get();
        DatasetFrom from1 = new DatasetFrom();
        from1.setApiName(opp.getApiName()).setDisplayName(opp.getDisplayName()).setDatasetId(opp.getId())
                .setDatasetType(DatasourceType.ENTITY).setDatastoreName(opp.getDataStoreName());

        DatasetFrom from2 = new DatasetFrom();
        from2.setApiName(acct.getApiName()).setDisplayName(acct.getDisplayName()).setDatasetId(acct.getId())
                .setDatasetType(DatasourceType.ENTITY).setDatastoreName(acct.getDataStoreName());

        List<Join> autoJoins = service.fetchAutoJoins(List.of(from1), List.of(from2));
        assertTrue(CollectionUtils.isNotEmpty(autoJoins));
        assertEquals(1, autoJoins.size());
    }

    @Test
    public void testFormatOrderByItem() {
        // Null/empty cases
        assertEquals("", DatasetService.formatOrderByItem(null));
        assertEquals("", DatasetService.formatOrderByItem(""));
        assertEquals("", DatasetService.formatOrderByItem("   "));

        // Basic column formatting
        assertEquals("\"name\"", DatasetService.formatOrderByItem("name"));
        assertEquals("\"name\"", DatasetService.formatOrderByItem("  name  "));

        // With ASC/DESC
        assertEquals("\"name\" ASC", DatasetService.formatOrderByItem("name ASC"));
        assertEquals("\"name\" DESC", DatasetService.formatOrderByItem("name DESC"));
        assertEquals("\"name\" asc", DatasetService.formatOrderByItem("name asc"));
        assertEquals("\"name\" desc", DatasetService.formatOrderByItem("name desc"));

        // Extra spaces
        assertEquals("\"name\" ASC", DatasetService.formatOrderByItem("  name   ASC  "));

        // Edge cases - ASC/DESC in column name
        assertEquals("\"name_asc_field\"", DatasetService.formatOrderByItem("name_asc_field"));
        assertEquals("\"nameASC\"", DatasetService.formatOrderByItem("nameASC"));
    }

    @Test
    public void testFetchAutoJoinSequential(){
        Optional<EntityDefinition> account = schemaService.getSyncariEntityByName("account");
        Optional<EntityDefinition> oppty = schemaService.getSyncariEntityByName("opportunity");
        Optional<EntityDefinition> contact = schemaService.getSyncariEntityByName("contact");
        assertTrue(oppty.isPresent());
        assertTrue(account.isPresent());
        EntityDefinition opp = oppty.get();
        EntityDefinition acct = account.get();
        EntityDefinition contct = contact.get();
        DatasetFrom from1 = new DatasetFrom();
        from1.setApiName(opp.getApiName()).setDisplayName(opp.getDisplayName()).setDatasetId(opp.getId())
                .setDatasetType(DatasourceType.ENTITY).setDatastoreName(opp.getDataStoreName()).setAlias("opp");

        DatasetFrom from2 = new DatasetFrom();
        from2.setApiName(acct.getApiName()).setDisplayName(acct.getDisplayName()).setDatasetId(acct.getId())
                .setDatasetType(DatasourceType.ENTITY).setDatastoreName(acct.getDataStoreName()).setAlias("acct");

        DatasetFrom from3 = new DatasetFrom();
        from3.setApiName(contct.getApiName()).setDisplayName(contct.getDisplayName()).setDatasetId(contct.getId())
                .setDatasetType(DatasourceType.ENTITY).setDatastoreName(contct.getDataStoreName()).setAlias("contact");

        List<Join> autoJoins = service.fetchAutoJoins(List.of(from1), List.of(from2));
        assertTrue(CollectionUtils.isNotEmpty(autoJoins));
        assertEquals(1, autoJoins.size());

        List<Join> autoJoinsSequential = service.fetchAutoJoins(List.of(from1, from2), List.of(from3));
        assertTrue(CollectionUtils.isNotEmpty(autoJoinsSequential));
        assertEquals(1, autoJoinsSequential.size());
    }


}
