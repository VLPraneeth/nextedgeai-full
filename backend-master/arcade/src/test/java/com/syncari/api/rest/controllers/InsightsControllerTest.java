package com.syncari.api.rest.controllers;

import com.syncari.api.rest.controllers.data.insights.LastVisitedDashboardDTO;
import com.syncari.connector.ConnectorInfo;
import com.syncari.core.DataTransformer;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Connector;
import com.syncari.core.model.InsightsUserPreference;
import com.syncari.core.model.insights.InsightsDashboard;
import com.syncari.core.repositories.customer.FeatureRepo;
import com.syncari.core.repositories.customer.InsightsDashboardRepo;
import com.syncari.core.repositories.customer.InsightsUserPreferenceRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DatastoreService;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Optional;

import static com.syncari.core.security.Permissions.READ_INSIGHTS;
import static org.junit.Assert.*;

public class InsightsControllerTest extends AbstractSyncariTest{

    @Autowired
    InsightsController insightsController;

    @Autowired
    InsightsDashboardRepo insightsDashboardRepo;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    DataTransformer transformer;

    @Autowired
    FeatureRepo featureRepo;

    @Autowired
    InsightsUserPreferenceRepo insightsUserPreferenceRepo;

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS})
    public void testGetDashboards(){
        assertNotNull(insightsController.getDashboards());
        assertTrue(CollectionUtils.isNotEmpty(insightsController.getDashboards()));
        assertTrue(insightsController.getDashboards().size() > 0);
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS})
    public void testGetDashboard(){
        Optional<InsightsDashboard> insightsDashboard = insightsDashboardRepo.findByName("marketing");
        assertTrue(insightsDashboard.isPresent());
        assertNotNull(insightsController.getDashboard(insightsDashboard.get().getId()));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS})
    public void testSetAndGetDefaultDashboard(){
        Optional<InsightsDashboard> insightsDashboard = insightsDashboardRepo.findByName("marketing");
        assertTrue(insightsDashboard.isPresent());
        String dashboardId = insightsDashboard.get().getId();
        insightsController.setLastVisitedUserDashboard(new LastVisitedDashboardDTO().setLastVisitedDashboardId(dashboardId));

        Optional<InsightsUserPreference> insightsUserPreference = insightsUserPreferenceRepo.findByUserId(SyncariContext.getUser().getId());
        assertTrue(insightsUserPreference.isPresent());
        assertEquals(dashboardId, insightsUserPreference.get().getLastVisitedDashboardId());

        LastVisitedDashboardDTO lastVisitedDashboard = insightsController.getLastVisitedUserDashboard();
        assertNotNull(lastVisitedDashboard);
        assertEquals(dashboardId, lastVisitedDashboard.getLastVisitedDashboardId());
    }

    @Test
    @Ignore
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS})
    public void testGetDatacard(){
        Optional<InsightsDashboard> insightsDashboard = insightsDashboardRepo.findByName("marketing");
        assertTrue(insightsDashboard.isPresent());
        assertNotNull(insightsController.getDashboard(insightsDashboard.get().getId()));
        assertTrue(CollectionUtils.isNotEmpty(insightsDashboard.get().getDataCardIds()));
        assertTrue(insightsDashboard.get().getDataCardIds().size() > 1);
        createDbConnector();
        assertNotNull(insightsController.getDatacard(insightsDashboard.get().getId(), insightsDashboard.get().getDataCardIds().get(0)/*, new KeyValue()*/));
    }

    private ConnectorInfo createDbConnector() {
        String schema = SyncariContext.getSyncariId();
        datastoreService.provision(schema);
        Connector connector = connectorService.getSyncariConnector();
        assertNotNull(connector);
        return transformer.toConnectorInfo(connector);
    }

}
