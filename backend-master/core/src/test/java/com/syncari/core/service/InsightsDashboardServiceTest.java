package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.model.InsightsUserPreference;
import com.syncari.core.model.insights.DataCardSetting;
import com.syncari.core.model.insights.Datacard;
import com.syncari.core.model.insights.InsightsDashboard;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetFrom;
import com.syncari.core.model.insights.dataset.Variable;
import com.syncari.core.model.insights.dataset.VariableValue;
import com.syncari.core.service.InsightsService;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.repositories.customer.DatacardRepo;
import com.syncari.core.repositories.customer.InsightsDashboardRepo;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

public class InsightsDashboardServiceTest extends AbstractSyncariTest {

    @Autowired
    InsightsDashboardService dashboardService;

    @Autowired
    InsightsDashboardRepo dashboardRepo;

    @Autowired
    DatacardService datacardService;

    @Autowired
    InsightsService insightsService;

    @Autowired
    DatacardRepo datacardRepo;

    @Autowired
    ComponentDependencyService dependencyService;

    @Override
    public void tearDown() {
        super.tearDown();
        resetRepos(dashboardRepo);
    }

    @Test
        public void createDashboardDraft(){
        InsightsDashboard dashboard = new InsightsDashboard();
        dashboard.setName("testDashboard").setDescription("Test Dashboard Description");

        try{
            dashboardService.createDashboardDraft(dashboard);
            fail();
        } catch (SyncariValidationException ex){
            assertEquals("Dashboard Display Name cannot be empty", ex.getMessage());
        }
        dashboard.setDisplayName("Test Dashboard");
        dashboard.setDraftStatus(DraftStatus.APPROVED);
        try{
            dashboardService.createDashboardDraft(dashboard);
            fail();
        } catch (SyncariValidationException ex){
            assertEquals("Cannot add/update non draft Dashboard Test Dashboard", ex.getMessage());
        }
        dashboard.setDraftStatus(DraftStatus.NEW);
        InsightsDashboard draft = dashboardService.createDashboardDraft(dashboard);
        assertTrue(draft.isDraft());
        InsightsDashboard retrieved = dashboardService.getDashboard(draft.getId());
        assertEquals(draft.getId(), retrieved.getId());
        assertFalse(dashboardService.hasDraft(retrieved));
    }

    @Test
    public void createDashboardDraft_DuplicateName(){
        InsightsDashboard dashboard = new InsightsDashboard();
        dashboard.setDisplayName("Test Dashboard").setDescription("Test Dashboard Description");

        InsightsDashboard draft = dashboardService.createDashboardDraft(dashboard);
        assertTrue(draft.isDraft());
        assertEquals("test_dashboard", draft.getName());

        InsightsDashboard dashboard2 = new InsightsDashboard();
        dashboard2.setDisplayName("Test Dashboard").setDescription("Test Dashboard Description - 1");

        var dashboardWithApiName = dashboardService.createDashboardDraft(dashboard2);
        assertEquals("test_dashboard_1", dashboardWithApiName.getName());
    }

    @Test
    public void createApproveAndDeleteDashboardDraft(){
        InsightsDashboard dashboard = new InsightsDashboard();
        dashboard.setName("testDashboard").setDisplayName("Test Dashboard").setDescription("Test Dashboard Description");

        InsightsDashboard draft = dashboardService.createDashboardDraft(dashboard);
        assertTrue(draft.isDraft());
        InsightsDashboard retrieved = dashboardService.getDashboard(draft.getId());
        assertEquals(draft.getId(), retrieved.getId());
        assertTrue(retrieved.isDraft());

        InsightsDashboard approved = dashboardService.approveDraftDashboard(draft);
        assertTrue(approved.isApproved());
        assertFalse(dashboardService.hasDraft(approved));

        // approve an existing approved dashboard - fail
        try{
            dashboardService.approveDraftDashboard(approved);
            fail();
        } catch (SyncariValidationException ex){
            assertEquals("Cannot approve non draft dashboard Test Dashboard", ex.getMessage());
        }

        // create draft from existing published
        InsightsDashboard newDraft = dashboardService.createDraftFor(approved);
        assertTrue(newDraft.isDraft());
        assertEquals(approved.getId(), newDraft.getParentId());
        assertTrue(dashboardService.hasDraft(approved));

        // try and create draft when draft exists
        try{
            dashboardService.createDraftFor(approved);
            fail();
        } catch (SyncariValidationException ex){
            assertEquals("Draft for dashboard Test Dashboard already exists", ex.getMessage());
        }

        // try and create draft from existing draft
        try{
            dashboardService.createDraftFor(newDraft);
            fail();
        } catch (SyncariValidationException ex){
            assertEquals("Draft dashboard can be created only from approved dashboard.", ex.getMessage());
        }

        // list all dashboards
        List<InsightsDashboard> dashboards = dashboardService.getAllDashboards();
        assertTrue(dashboards.size() > 0);
        var approvedDC = dashboards.stream().filter(d -> DraftStatus.APPROVED.equals(d.getDraftStatus()) && d.getName().equals("testDashboard")).findAny().get();
        assertNotNull(approvedDC);
        assertNull(approvedDC.getParentId());
        var draftDC = dashboards.stream().filter(d -> DraftStatus.NEW.equals(d.getDraftStatus()) && d.getName().equals("testDashboard")).findAny().get();
        assertNotNull(draftDC);
        assertEquals(approvedDC.getId(), draftDC.getParentId());

        // delete published dashboard
        dashboardService.deleteDashboard(approved);
        var deletedDash = dashboardService.findDashboard(approved.getId());
        assertFalse(deletedDash.isPresent());

        var draftDash = dashboardService.findDashboard(newDraft.getId());
        assertTrue(draftDash.isPresent());
        assertNull(draftDash.get().getParentId());
    }

    @Test
    public void discardDashboardDraft(){
        InsightsDashboard dashboard = new InsightsDashboard();
        dashboard.setName("testDashboard").setDisplayName("Test Dashboard").setDescription("Test Dashboard Description");

        InsightsDashboard draft = dashboardService.createDashboardDraft(dashboard);
        assertTrue(draft.isDraft());
        dashboardService.discardDraftDashboard(draft);
        Optional<InsightsDashboard> retrieved = dashboardService.findDashboard(draft.getId());
        assertTrue(retrieved.isEmpty());
    }

    @Test
    public void createDraftForSeededDashboard(){
        InsightsDashboard seeded = dashboardService.getAllDashboards().stream().filter(d -> d.isSeeded()).findFirst().get();
        try {
            dashboardService.createDraftFor(seeded);
            fail();
        } catch (SyncariValidationException ex) {
            assertEquals("Draft dashboard can not be created for seeded dashboards.", ex.getMessage());
        }

    }

    @Test
    public void testDatasetDependency_CreateAndDelete(){

        Optional<Datacard> datacard = datacardRepo.findByName("allOpenPipelineNewCount");
        assertTrue(datacard.isPresent());

        InsightsDashboard dashboard = new InsightsDashboard();
        dashboard.setName("testDashboard").setDisplayName("Test Dashboard").setDescription("Test Dashboard Description");
        dashboard.getDataCardSettings().add(new DataCardSetting().setDatacardId(datacard.get().getId()));

        InsightsDashboard draft = dashboardService.createDashboardDraft(dashboard);
        assertTrue(draft.isDraft());

        var deps = datacardService.getDatacardDependencies(datacard.get().getId());
        assertFalse(deps.isEmpty());
        assertTrue(deps.stream().anyMatch(d -> d.getFromId().equals(draft.getId()) && d.getFromComponent().equals(ComponentType.dashboard)));

        // publish draft
        InsightsDashboard published = dashboardService.approveDraftDashboard(draft);
        assertTrue(published.isApproved());

        // create new draft
        InsightsDashboard newDraft = dashboardService.createDraftFor(published);
        deps = datacardService.getDatacardDependencies(datacard.get().getId());
        assertTrue(deps.stream().anyMatch(d -> d.getFromId().equals(published.getId()) && d.getFromComponent().equals(ComponentType.dashboard)));
        assertTrue(deps.stream().anyMatch(d -> d.getFromId().equals(newDraft.getId()) && d.getFromComponent().equals(ComponentType.dashboard)));

        // discard new draft
        dashboardService.discardDraftDashboard(newDraft);
        deps = datacardService.getDatacardDependencies(datacard.get().getId());
        assertTrue(deps.stream().anyMatch(d -> d.getFromId().equals(published.getId()) && d.getFromComponent().equals(ComponentType.dashboard)));
        assertFalse(deps.stream().anyMatch(d -> d.getFromId().equals(newDraft.getId()) && d.getFromComponent().equals(ComponentType.dashboard)));

    }

    @Test
    public void testDashboardDatacardPref(){

        Optional<Datacard> datacardOptional = datacardRepo.findByName("allOpenPipelineNewCount");
        assertTrue(datacardOptional.isPresent());

        var datacard = datacardOptional.get();

        InsightsDashboard dashboard = new InsightsDashboard();
        dashboard.setName("testDashboard").setDisplayName("Test Dashboard").setDescription("Test Dashboard Description");
        dashboard.getDataCardSettings().add(new DataCardSetting().setDatacardId(datacard.getId()));

        InsightsDashboard draft = dashboardService.createDashboardDraft(dashboard);

        // Verify initial state
        var nullPref = insightsService.getUserDashboardDatacardVariable(SyncariContext.getUser().getId(), dashboard.getId(), datacard.getId());
        assertNull(nullPref);

        var variable = new Variable().setApiName("testVar").setDisplayName("testVar").setVariableValue(new VariableValue().setDefaultValue("100"));
        insightsService.setUserDashboardDatacardVariable(SyncariContext.getUser().getId(), dashboard.getId(), datacard.getId(), List.of(variable));

        // Verify our user preference is saved
        var pref = insightsService.getUserDashboardDatacardVariable(SyncariContext.getUser().getId(), dashboard.getId(), datacard.getId());
        assertNotNull(pref);
        assertEquals("testVar", pref.get(0).getApiName());
        assertEquals("100", pref.get(0).getVariableValue().getDefaultValue() );

        draft.setDataCardSettings(List.of());
        draft.setDataCardIds(List.of());
        dashboardService.updateDashboard(dashboard.getId(), draft);

        // Verify our user preference was removed
        var removedPref = insightsService.getUserDashboardDatacardVariable(SyncariContext.getUser().getId(), dashboard.getId(), datacard.getId());
        assertNull(removedPref);

    }
}
