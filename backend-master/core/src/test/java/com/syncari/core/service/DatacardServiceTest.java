package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.model.ComponentDependency;
import com.syncari.core.model.insights.DataCardSetting;
import com.syncari.core.model.insights.Datacard;
import com.syncari.core.model.insights.InsightsDashboard;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetFrom;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.repositories.customer.DatacardRepo;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.junit.Assert.assertTrue;


public class DatacardServiceTest extends AbstractSyncariTest {

    @Autowired
    DatacardService datacardService;

    @Autowired
    DatacardRepo datacardRepo;

    @Autowired
    ComponentDependencyService dependencyService;

    @Autowired
    InsightsDashboardService dashboardService;

    @Override
    public void tearDown() {
        super.tearDown();
        resetRepos(datacardRepo);
    }

    @Test
    public void createDatacard(){
        Datacard datacard = new Datacard();
        datacard.setName("testDatacard").setDescription("Test Datacard Description");

        try{
            datacardService.createDatacard(datacard);
            fail();
        } catch (SyncariValidationException ex){
            assertEquals("Datacard Display Name cannot be empty", ex.getMessage());
        }
        datacard.setDisplayName("Test Datacard");

        datacard.setDraftStatus(DraftStatus.NEW);
        Datacard approved = datacardService.createDatacard(datacard);
        assertTrue(approved.isApproved());
        Datacard retrieved = datacardService.getDatacard(approved.getId());
        assertEquals(approved.getId(), retrieved.getId());
    }

    @Test
    public void createDatacard_DuplicateApiName(){
        Datacard datacard = new Datacard();
        datacard.setName("testDatacard").setDisplayName("Test Datacard").setDescription("Test Datacard Description");

        Datacard approved = datacardService.createDatacard(datacard);
        InsightsDashboard dashboard = new InsightsDashboard();
        dashboard.setName("testDashboard").setDisplayName("Test Dashboard").setDescription("Test Dashboard Description");
        dashboard.getDataCardSettings().add(new DataCardSetting().setDatacardId(datacard.getId()));

        InsightsDashboard draft = dashboardService.createDashboardDraft(dashboard);
        assertTrue(draft.isDraft());

        Datacard datacard2 = new Datacard();
        datacard2.setName("testDatacard").setDisplayName("Test Datacard - 2").setDescription("Test Datacard Description - 2");
        var duplicateDC = datacardService.createDatacard(datacard2);
        assertEquals("testDatacard_1", duplicateDC.getName());
        assertEquals("Test Datacard - 2", duplicateDC.getDisplayName());

    }

    @Test
    public void createApproveAndDeleteDatacard(){
        InsightsDashboardService mockDashService = mock(InsightsDashboardService.class);
        Datacard datacard = new Datacard();
        datacard.setName("testDatacard").setDisplayName("Test Datacard").setDescription("Test Datacard Description");

        Datacard approved = datacardService.createDatacard(datacard);
        assertTrue(approved.isApproved());
        Datacard retrieved = datacardService.getDatacard(approved.getId());
        assertEquals(approved.getId(), retrieved.getId());
        assertTrue(retrieved.isApproved());

        // create a draft dashboard and add the datacard on it
        InsightsDashboard dashboard = new InsightsDashboard();
        dashboard.setName("testDashboard").setDisplayName("Test Dashboard").setDescription("Test Dashboard Description");
        dashboard.getDataCardSettings().add(new DataCardSetting().setDatacardId(datacard.getId()));

        InsightsDashboard draft = dashboardService.createDashboardDraft(dashboard);
        assertTrue(draft.isDraft());

        try {
            datacardService.deleteDatacard(approved);
            fail();
        } catch (SyncariValidationException ex) {
            assertEquals("Datacard Test Datacard cannot be deleted as it is used in dashboards Test Dashboard(Draft).", ex.getMessage());
        }

        // remove all usage of datacards in dashboard
        dashboardService.discardDraftDashboard(draft);
        datacardService.deleteDatacard(approved);

        // assert published is deleted
        assertFalse(datacardService.findDatacard(approved.getId()).isPresent());
    }

    @Test
    public void deleteSeededDatacard(){
        Datacard seeded = datacardService.getAllDatacards().stream().filter(d -> d.isSeeded()).findFirst().get();
        try {
            datacardService.deleteDatacard(seeded);
            fail();
        } catch (SyncariValidationException ex) {
            assertEquals("Seeded datacard cannot be deleted", ex.getMessage());
        }

    }

    @Test
    public void testDatasetDependency_CreateAndDelete(){
        Optional<Datacard> datacard = datacardRepo.findByName("allOpenPipelineNewCount");
        assertTrue(datacard.isPresent());
        Datacard datacardCopy = datacard.get().makeCopy();
        datacardCopy.setName("allOpenPipelineNewCount_"+ RandomUtils.nextInt(1, 1000));
        datacardCopy.setSeeded(false);

        var saved = datacardService.createDatacard(datacardCopy);
        assertTrue(saved.isApproved());
        var dependencies = dependencyService.findDependenciesBy(saved.getId(), ComponentType.datacard);
        assertFalse(dependencies.isEmpty());
        var d = dependencies.get(0);
        assertEquals(ComponentType.datacard, d.getFromComponent());
        assertEquals(saved.getId(), d.getFromId());
        assertEquals(ComponentType.dataset, d.getToComponent());

        // delete dataset
        datacardService.deleteDatacard(saved);
        dependencies = dependencyService.findDependenciesBy(saved.getId(), ComponentType.datacard);
        assertTrue(dependencies.isEmpty());
    }
}
