package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.connector.EntityData;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.dashboard.WidgetSeed;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Dashboard;
import com.syncari.core.model.EntityDataScoreSnapshot;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.DataScoreCard;
import com.syncari.core.model.misc.EntityScoreWrapper;
import com.syncari.core.model.misc.Widget;
import com.syncari.core.model.misc.WidgetType;
import com.syncari.core.repositories.customer.CustomDataScoreRepoImpl;
import com.syncari.core.repositories.customer.EntityDataScoreSnapshotRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.FieldDataScoreSnapshotRepo;
import com.syncari.utils.DateUtil;
import com.syncari.utils.KeyValue;

public class DashboardServiceTest extends AbstractSyncariTest {
	@Autowired
	DashboardService service;
	@Autowired
	SchemaService schemaService;
	@Autowired
	ConnectorService connectorService;
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    EntityRepoService repoService;
    @Autowired
    EntityDataScoreSnapshotRepo entitySnapshotRepo;
    @Autowired
    FieldDataScoreSnapshotRepo fieldSnapshotRepo;
    @Autowired
    CustomDataScoreRepoImpl scoreImpl;
    @Autowired
    DateUtil util;
    
    @Override
    public void setUp() {
        super.setUp();
        entityRepo.deleteAll("account");
        resetRepos(entitySnapshotRepo, fieldSnapshotRepo);
    }

    @After
    public void tearDown() {
        super.tearDown();
        entityRepo.deleteAll("account");
        resetRepos(entitySnapshotRepo, fieldSnapshotRepo);
    }

	@Test
	public void getDashBoard() throws InterruptedException {
	    try {
	        service.getDashBoard(null);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals("Dashboard name is required", e.getMessage());
        }
        try {
            service.getDashBoard("invalid");
            fail();
        } catch (SyncariValidationException e) {
            assertEquals("Dashboard with name invalid not found", e.getMessage());
        }
        Dashboard dashboard = service.getDashBoard("dqsOverview");
        assertEquals("dqsOverview", dashboard.getName());
        assertEquals("DQS Overview", dashboard.getTitle());
        assertEquals(4, dashboard.getWidgets().size());
        //assertNotNull(dashboard.getId());
        // We send dqsOverview as entityId and entityApiName for frontend.
        assertNotNull(dashboard.getEntityId());
        assertNotNull(dashboard.getEntityApiName());
        
        assertEquals("Overall Data Fitness Index", dashboard.getWidget("overallFitness").get().getTitle());
        assertEquals("Data Fitness Over Time", dashboard.getWidget("dataFitnessOvertime").get().getTitle());
        assertEquals("Improvement Opportunities", dashboard.getWidget("improvementOpportunities").get().getTitle());
        assertEquals("Entity Data Quality Breakdown", dashboard.getWidget("entityDataQualityBreakdown").get().getTitle());

        Dashboard acctDashboard = service.getDashBoard("account_dqsOverview");
        assertEquals("account_dqsOverview", acctDashboard.getName());
        assertEquals("Account", acctDashboard.getTitle());
        assertEquals(3, acctDashboard.getWidgets().size());
        //assertNotNull(acctDashboard.getId());
        assertNotNull(acctDashboard.getEntityId());
        assertEquals("account", acctDashboard.getEntityApiName());
	}
	
	@Test
	public void getDashBoards() throws InterruptedException {
	    List<Dashboard> dashboard = service.getDashBoards("dqs");
	    assertTrue(dashboard.size() >= 8);
	    dashboard = service.getDashBoards(null);
	    assertEquals(0, dashboard.size());
	    dashboard = service.getDashBoards("invalid");
	    assertEquals(0, dashboard.size());
	}
	
	@Test
	public void getWidgetOverallFitnessNoData() throws InterruptedException {
	    Widget widget = service.getWidget("dqsOverview", "overallFitness");
	    assertEquals("overallFitness", widget.getName());
	    assertEquals("Overall Data Fitness Index", widget.getTitle());
	    assertTrue(widget.getContent("fitnessGauge").isPresent());
	    KeyValue keyValue = (KeyValue) widget.getContent("fitnessGauge").get().getData().get(0);
        assertEquals("Poor", keyValue.get("label"));
        assertEquals("0", keyValue.get("value").toString());
        assertTrue(widget.getContent("fitnessBadge").isPresent());
        keyValue = (KeyValue) widget.getContent("fitnessBadge").get().getData().get(0);
        assertEquals("No change in last 30 days", keyValue.get("value").toString());
	}
	
	@Test
	public void getWidgetOverallFitnessWithData() throws InterruptedException {
	    setupScore();
	    Widget widget = service.getWidget("dqsOverview", "overallFitness");
	    assertEquals("overallFitness", widget.getName());
	    assertEquals("Overall Data Fitness Index", widget.getTitle());
	    assertTrue(widget.getContent("fitnessGauge").isPresent());
	    KeyValue keyValue = (KeyValue) widget.getContent("fitnessGauge").get().getData().get(0);
	    assertEquals("Needs Improvement", keyValue.get("label"));
	    assertEquals("32", keyValue.get("value").toString());
	    assertTrue(widget.getContent("fitnessBadge").isPresent());
	    keyValue = (KeyValue) widget.getContent("fitnessBadge").get().getData().get(0);
	    assertEquals("Up 32% last 30 days", keyValue.get("value").toString());
	}
	
	@Test
	public void getWidgetDataFitnessOvertimeNoData() throws InterruptedException {
	    Widget widget = service.getWidget("dqsOverview", "dataFitnessOvertime");
	    assertEquals("dataFitnessOvertime", widget.getName());
	    assertEquals("Data Fitness Over Time", widget.getTitle());
	    assertTrue(widget.getContent("trend").isPresent());
	    assertTrue(((List)widget.getContent("trend").get().getData().get(0)).isEmpty());
	}
	
   @Test
    public void getAccountOverallFitnessWithData() throws InterruptedException {
        setupScore();
        Widget widget = service.getWidget("account_dqsOverview", "overallFitness");
        assertEquals("overallFitness", widget.getName());
        assertEquals("Overall Data Fitness Index", widget.getTitle());
        assertTrue(widget.getContent("fitnessGauge").isPresent());
        KeyValue keyValue = (KeyValue) widget.getContent("fitnessGauge").get().getData().get(0);
        assertEquals("Excellent", keyValue.get("label"));
        assertEquals("97", keyValue.get("value").toString());
        assertTrue(widget.getContent("fitnessBadge").isPresent());
        keyValue = (KeyValue) widget.getContent("fitnessBadge").get().getData().get(0);
        assertEquals("Up 97% last 30 days", keyValue.get("value").toString());
    }
   
   @Test
   public void getContactOverallFitnessWithData() throws InterruptedException {
       setupScore();
       Widget widget = service.getWidget("contact_dqsOverview", "overallFitness");
       assertEquals("overallFitness", widget.getName());
       assertEquals("Overall Data Fitness Index", widget.getTitle());
       assertTrue(widget.getContent("fitnessGauge").isPresent());
       KeyValue keyValue = (KeyValue) widget.getContent("fitnessGauge").get().getData().get(0);
       assertEquals("Poor", keyValue.get("label"));
       assertEquals("0", keyValue.get("value").toString());
       assertTrue(widget.getContent("fitnessBadge").isPresent());
       keyValue = (KeyValue) widget.getContent("fitnessBadge").get().getData().get(0);
       assertEquals("No change in last 30 days", keyValue.get("value").toString());
   }
   
   @Test
   public void getLeadOverallFitnessWithData() throws InterruptedException {
       setupScore();
       Widget widget = service.getWidget("lead_dqsOverview", "overallFitness");
       assertEquals("overallFitness", widget.getName());
       assertEquals("Overall Data Fitness Index", widget.getTitle());
       assertTrue(widget.getContent("fitnessGauge").isPresent());
       KeyValue keyValue = (KeyValue) widget.getContent("fitnessGauge").get().getData().get(0);
       assertEquals("Poor", keyValue.get("label"));
       assertEquals("0", keyValue.get("value").toString());
       assertTrue(widget.getContent("fitnessBadge").isPresent());
       keyValue = (KeyValue) widget.getContent("fitnessBadge").get().getData().get(0);
       assertEquals("No change in last 30 days", keyValue.get("value").toString());
   }
	
	@Test
	public void getWidgetDataFitnessOvertimeWithData() throws InterruptedException {
	    setupDfiTrend();
	    Widget widget = service.getWidget("dqsOverview", "dataFitnessOvertime");
        assertEquals("dataFitnessOvertime", widget.getName());
        assertEquals("Data Fitness Over Time", widget.getTitle());
	    assertTrue(widget.getContent("trend").isPresent());
	    List<KeyValue> list = (List<KeyValue>) widget.getContent("trend").get().getData().get(0);
	    KeyValue keyValue = list.get(0);
	    assertEquals(util.formatDate(Instant.now().minus(1, ChronoUnit.DAYS), DateUtil.dateOnlyFormat2), keyValue.get("x"));
	    assertEquals("18", keyValue.get("y").toString());
	    assertEquals(keyValue.get("x").toString(), keyValue.get("label").toString());
	    assertNotNull(keyValue.get("id"));
	    list = (List<KeyValue>) widget.getContent("trend").get().getData().get(0);
	    keyValue = list.get(1);
	    assertEquals(util.formatDate(Instant.now(), DateUtil.dateOnlyFormat2), keyValue.get("x"));
	    assertEquals("23", keyValue.get("y").toString());
	    assertEquals(keyValue.get("x").toString(), keyValue.get("label").toString());
	    assertNotNull(keyValue.get("id"));
	}
	
	@Test
	public void getImprovementOpptiesWithData() throws InterruptedException {
	    setupDfiTrend();
	    Widget widget = service.getWidget("dqsOverview", WidgetSeed.IMPROVEMENT_OPPORTUNITIES);
	    assertEquals(WidgetSeed.IMPROVEMENT_OPPORTUNITIES, widget.getName());
	    assertEquals("Improvement Opportunities", widget.getTitle());
	    assertTrue(widget.getContent(WidgetType.dataScoreLineItems.name()).isPresent());
	    assertTrue(widget.getContent(WidgetType.dataScoreLineItems.name()).get().getData().size() == 3);
	    KeyValue keyValue = (KeyValue) widget.getContent(WidgetType.dataScoreLineItems.name()).get().getData().get(0);
	    assertEquals("Account", ((DataScoreCard)keyValue.get("card")).getEntityName());
	    assertEquals(69, ((DataScoreCard)keyValue.get("card")).getScore());
	    assertEquals(3, ((DataScoreCard)keyValue.get("card")).getFactors().size());
	    keyValue = (KeyValue) widget.getContent(WidgetType.dataScoreLineItems.name()).get().getData().get(1);
        assertEquals("Lead", ((DataScoreCard)keyValue.get("card")).getEntityName());
        assertEquals(0, ((DataScoreCard)keyValue.get("card")).getScore());
        keyValue = (KeyValue) widget.getContent(WidgetType.dataScoreLineItems.name()).get().getData().get(2);
        assertEquals("Contact", ((DataScoreCard)keyValue.get("card")).getEntityName());
        assertEquals(0, ((DataScoreCard)keyValue.get("card")).getScore());
	}
	
	@Test
	public void getEntityBreakDownWithData() throws InterruptedException {
	    setupDfiTrend();
	    Widget widget = service.getWidget("dqsOverview", WidgetSeed.ENTITY_DATA_QUALITY_BREAKDOWN);
	    assertEquals(WidgetSeed.ENTITY_DATA_QUALITY_BREAKDOWN, widget.getName());
	    assertEquals("Entity Data Quality Breakdown", widget.getTitle());
	    assertTrue(widget.getContent("dataQualityBreakdown").isPresent());
	    assertTrue(widget.getContent("dataQualityBreakdown").get().getData().size() == 3);
	    KeyValue keyValue = (KeyValue) widget.getContent("dataQualityBreakdown").get().getData().get(0);
	    assertEquals("contact", keyValue.get("label"));
	    assertEquals("0", keyValue.get("value").toString());
	    keyValue = (KeyValue) widget.getContent("dataQualityBreakdown").get().getData().get(1);
	    assertEquals("account", keyValue.get("label"));
        assertEquals("69", keyValue.get("value").toString());
        keyValue = (KeyValue) widget.getContent("dataQualityBreakdown").get().getData().get(2);
        assertEquals("lead", keyValue.get("label"));
        assertEquals("0", keyValue.get("value").toString());
	}
	
	@Test
	public void getFieldScoresTablenWithData() throws InterruptedException {
	    setupDfiTrend();
	    Widget widget = service.getWidget("account_dqsOverview", WidgetSeed.FIELD_SCORE);
	    assertEquals(WidgetSeed.FIELD_SCORE, widget.getName());
	    assertEquals("Field Score", widget.getTitle());
	    assertTrue(widget.getContents().get(0).getData().size() == 10);
	    assertTrue(widget.getContents().get(0).getConfig().size() == 1);
	    assertTrue(widget.getContents().get(0).getConfig().get(0).containsKey("pageInfo"));
	    assertTrue(widget.getContents().get(0).getConfig().get(0).containsKey("metadata"));
	}
	
	private void setupScore() {
	    EntityDefinition def = schemaService.getEntityByName(connectorService.getSyncariConnector().getId(), "account").get();
        entityRepo.createCollection(def);
        
        // Name not empty, not camel cased
        EntityData data = new EntityData("account");
        data.addValue("name", "test account");
        data.addValue("phone", "6505554545");
        data.addValue("website", "www.test.com");
        data.addValue("NumberOfEmployees", 10);
        data.addValue("Domain", "test account");
        data.addValue("billingCity", "Foster");
        data.addValue("billingCountry", "USA");
        data.addValue("billingState", "CA");
        data.addValue("billingPostalCode", "123456");
        data.addValue("AnnualRevenue", "123456");
        data = entityRepo.save(data);
        // Name empty
        EntityData data1 = new EntityData("account");
        data1.addValue("Domain", "www.test.com");
        data1 = entityRepo.save(data1);
        // Name not empty, camel cased
        EntityData data2 = new EntityData("account");
        data2.addValue("name", "Test Account");
        data2.addValue("phone", "+16505554545");
        data2.addValue("website", "www.test.com");
        data2.addValue("NumberOfEmployees", 10);
        data2.addValue("Domain", "test account");
        data2.addValue("billingCity", "Foster");
        data2.addValue("billingCountry", "USA");
        data2.addValue("billingState", "CA");
        data2.addValue("billingPostalCode", "123456");
        data2.addValue("AnnualRevenue", "123456");
        data2 = entityRepo.save(data2);
        
        repoService.computeScore(List.of(data,  data1, data2), def.getApiName());
        // Name = 100 + 70 / 2 (100 for not empty, 70 for camel case)
        assertEquals(97, data.getSyncariScore().getRecordScore());
        // Name = 0 + 0 / 2 (0 for not empty , 0 for camel case)
        assertEquals(10, data1.getSyncariScore().getRecordScore());
        // Name = 100 + 100 / 2 (100 for not empty, 100 for camel case)
        assertEquals(100, data2.getSyncariScore().getRecordScore());
        entityRepo.save(data);
	}
	
    private void setupDfiTrend() {
        EntityDefinition def = schemaService.getEntityByName(connectorService.getSyncariConnector().getId(), "account").get();
        setupScore();
        
        repoService.snapshotScore();
        List<EntityDataScoreSnapshot> all = entitySnapshotRepo.findAll();
        all.forEach(a -> {
            a.setId(null);
            a.setComputedOn(a.getComputedOn().minus(1, ChronoUnit.DAYS));
            a.setScore(a.getScore()-5);
        });
        entitySnapshotRepo.saveAll(all);
        
        Map<String, Integer> dfiTrend = repoService.getDfiTrend(def.getId(), 30);
        assertEquals(2, dfiTrend.size());
        assertEquals("69", dfiTrend.get(util.formatDate(Instant.now(), DateUtil.dateOnlyFormat2)).toString());
        assertEquals("64", dfiTrend.get(util.formatDate(Instant.now().minus(1, ChronoUnit.DAYS), DateUtil.dateOnlyFormat2)).toString());
     
        EntityScoreWrapper avgScores = scoreImpl.getAvgScores(def, Optional.of(3), Optional.empty());
        assertEquals(69, avgScores.getEntityScore().getScore());
        assertEquals(3, avgScores.getFieldScores().size());
    }
}
