package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.ANALYTICS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Dashboard;

public class DashboardControllerTest extends AbstractSyncariTest {
    @Autowired
    private NewDashboardController controller;

    @Test
    @WithMockUser(username = "test@email.com", authorities = {ANALYTICS})
    public void getDashboard() throws Exception {
        try {
            controller.getDashboardByName(null);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals("Dashboard name is required", e.getMessage());
        }
        try {
            controller.getDashboardByName("invalid");
            fail();
        } catch (SyncariValidationException e) {
            assertEquals("Dashboard with name invalid not found", e.getMessage());
        }
        Dashboard dashboard = controller.getDashboardByName("dqsOverview");
        assertEquals("dqsOverview", dashboard.getName());
        assertEquals("DQS Overview", dashboard.getTitle());
        assertEquals(4, dashboard.getWidgets().size());
        //assertNotNull(dashboard.getId());

        assertEquals("Overall Data Fitness Index", dashboard.getWidget("overallFitness").get().getTitle());
        assertEquals("Data Fitness Over Time", dashboard.getWidget("dataFitnessOvertime").get().getTitle());
        assertEquals("Improvement Opportunities", dashboard.getWidget("improvementOpportunities").get().getTitle());
        assertEquals("Entity Data Quality Breakdown", dashboard.getWidget("entityDataQualityBreakdown").get().getTitle());
    }

}
