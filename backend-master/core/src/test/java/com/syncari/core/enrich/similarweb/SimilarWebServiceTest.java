package com.syncari.core.enrich.similarweb;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.core.model.EnrichmentCache;
import com.syncari.core.repositories.customer.EnrichmentCacheRepo;
import com.syncari.utils.DateUtil;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class SimilarWebServiceTest {

    public static final String TOKEN = "INVALID_TOKEN";

    private ConnectorInfo connectorInfo;

    @Before
    public void setup(){
        if(connectorInfo == null){
            connectorInfo = createConnector();
        }
    }

    @Test
    public void similarWebTrafficTestConnection_Invalid() {
        SimilarWebService similarWebService = new SimilarWebService();
        similarWebService.cacheRepo = mock(EnrichmentCacheRepo.class);
        TestConnectionResponse testConnectionResponse = similarWebService.testConnection(connectorInfo, List.of());
        assertFalse(testConnectionResponse.isSuccess());
        assertEquals("invalid API key", testConnectionResponse.getMessage());
    }

    @Ignore
    @Test
    public void similarWebTrafficTestConnection() {
        SimilarWebService similarWebService = new SimilarWebService();
        similarWebService.cacheRepo = mock(EnrichmentCacheRepo.class);
        TestConnectionResponse testConnectionResponse = similarWebService.testConnection(connectorInfo, List.of());
        assertTrue(testConnectionResponse.isSuccess());
    }

    @Ignore
    @Test
    public void similarWebTraffic() {
        SimilarWebService similarWebService = new SimilarWebService();
        similarWebService.cacheRepo = mock(EnrichmentCacheRepo.class);
        String yearAndMonth = ZonedDateTime.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        List<VisitMetric> visits = similarWebService.
                trafficMetrics(connectorInfo, "syncari.com", yearAndMonth, yearAndMonth, "world", SimilarWebAPICategory.TOTAL_TRAFFIC, SimilarWebAPIName.VISIT);
        assertEquals(1,visits.size());
        assertTrue(visits.get(0).getMetric() > 0.0);
    }

    @Ignore
    @Test
    public void similarWebLeadEnrichmentWithCache() {
        SimilarWebService similarWebService = new SimilarWebService();
        similarWebService.cacheRepo = mock(EnrichmentCacheRepo.class);
        String yearAndMonth = ZonedDateTime.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));

        Map<String, EnrichmentCache> cache = new HashMap<>();
        when(similarWebService.cacheRepo.findByServiceIdAndEntityNameAndEnrichKey(eq("swConnId"),eq("trafficData"),anyString())).then(
                invocationOnMock -> {
                    String url =invocationOnMock.getArgument(2);
                    return Optional.ofNullable(cache.get(url));
                }
        );
        when(similarWebService.cacheRepo.save(any(EnrichmentCache.class))).then(
                invocationOnMock -> {
                    EnrichmentCache enrichment =invocationOnMock.getArgument(0);
                    cache.put(enrichment.getEnrichKey(),enrichment);
                    return enrichment;
                }
        );
        assertTrue(cache.isEmpty());
        // assert site_type
        String siteType = similarWebService.leadEnrichment(connectorInfo, "bbc.com", yearAndMonth,
                yearAndMonth, "world", SimilarWebAPICategory.LEAD_ENRICHEMNT, SimilarWebAPIName.SITE_TYPE);
        assertFalse(siteType.isBlank());
        assertEquals("content", siteType);
        assertEquals(1, cache.size());
        verify(similarWebService.cacheRepo, atLeastOnce()).findByServiceIdAndEntityNameAndEnrichKey(eq("swConnId"),eq("trafficData"),anyString());
        verify(similarWebService.cacheRepo, times(1)).save(any(EnrichmentCache.class));

        // assert website category
        String websiteCategory = similarWebService.leadEnrichment(connectorInfo, "bbc.com", yearAndMonth,
                yearAndMonth, "world", SimilarWebAPICategory.LEAD_ENRICHEMNT, SimilarWebAPIName.WEBSITE_CATEGORY);
        assertFalse(websiteCategory.isBlank());
        assertEquals("News_and_Media", websiteCategory);
        assertEquals(1, cache.size());
        verify(similarWebService.cacheRepo, atLeastOnce()).findByServiceIdAndEntityNameAndEnrichKey(eq("swConnId"),eq("trafficData"),anyString());
        verify(similarWebService.cacheRepo, times(1)).save(any(EnrichmentCache.class)); // save on cache is not called again

        // assert company hq
        String hq = similarWebService.leadEnrichment(connectorInfo, "bbc.com", yearAndMonth,
                yearAndMonth, "world", SimilarWebAPICategory.LEAD_ENRICHEMNT, SimilarWebAPIName.COMPANY_HQ);
        assertFalse(hq.isBlank());
        assertEquals("London, United Kingdom", hq);
        assertEquals(1, cache.size());
        verify(similarWebService.cacheRepo, atLeastOnce()).findByServiceIdAndEntityNameAndEnrichKey(eq("swConnId"),eq("trafficData"),anyString());
        verify(similarWebService.cacheRepo, times(1)).save(any(EnrichmentCache.class)); // save on cache is not called again

        // assert employee range
        String employeeRange = similarWebService.leadEnrichment(connectorInfo, "bbc.com", yearAndMonth,
                yearAndMonth, "world", SimilarWebAPICategory.LEAD_ENRICHEMNT, SimilarWebAPIName.EMPLOYEE_RANGE);
        assertFalse(employeeRange.isBlank());
        assertEquals(1, cache.size());
        verify(similarWebService.cacheRepo, atLeastOnce()).findByServiceIdAndEntityNameAndEnrichKey(eq("swConnId"),eq("trafficData"),anyString());
        verify(similarWebService.cacheRepo, times(1)).save(any(EnrichmentCache.class)); // save on cache is not called again

        // assert estimated revenuee
        String estRev = similarWebService.leadEnrichment(connectorInfo, "bbc.com", yearAndMonth,
                yearAndMonth, "world", SimilarWebAPICategory.LEAD_ENRICHEMNT, SimilarWebAPIName.ESTIMATED_REVENUE);
        assertFalse(estRev.isBlank());
        assertEquals(1, cache.size());
        verify(similarWebService.cacheRepo, atLeastOnce()).findByServiceIdAndEntityNameAndEnrichKey(eq("swConnId"),eq("trafficData"),anyString());
        verify(similarWebService.cacheRepo, times(1)).save(any(EnrichmentCache.class)); // save on cache is not called again

    }

    @Ignore
    @Test
    public void similarWebTrafficDataIsCached() {
        SimilarWebService similarWebService = new SimilarWebService();
        similarWebService.cacheRepo = mock(EnrichmentCacheRepo.class);
        String yearAndMonth = ZonedDateTime.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Map<String, EnrichmentCache> cache = new HashMap<>();

        when(similarWebService.cacheRepo.findByServiceIdAndEntityNameAndEnrichKey(eq("swConnId"),eq("trafficData"),anyString())).then(
                invocationOnMock -> {
                    String url =invocationOnMock.getArgument(2);
                    return Optional.ofNullable(cache.get(url));
                }
        );
        when(similarWebService.cacheRepo.save(any(EnrichmentCache.class))).then(
                invocationOnMock -> {
                    EnrichmentCache enrichment =invocationOnMock.getArgument(0);
                    cache.put(enrichment.getEnrichKey(),enrichment);
                    return enrichment;
                }
        );
        List<VisitMetric> visits = similarWebService.
                trafficMetrics(connectorInfo, "syncari.com", yearAndMonth, yearAndMonth, "world", SimilarWebAPICategory.TOTAL_TRAFFIC, SimilarWebAPIName.VISIT);
        assertEquals(1,visits.size());
        assertTrue(visits.get(0).getMetric() > 0.0);
        similarWebService.
                trafficMetrics(connectorInfo, "syncari.com", yearAndMonth, yearAndMonth, "world", SimilarWebAPICategory.TOTAL_TRAFFIC, SimilarWebAPIName.VISIT);
        assertFalse(cache.isEmpty());

        // desktop and mobile visit split
        List<VisitMetric> desktopVisits = similarWebService.
                trafficMetrics(connectorInfo, "syncari.com", yearAndMonth, yearAndMonth, "world", SimilarWebAPICategory.TOTAL_TRAFFIC, SimilarWebAPIName.DESKTOP_VISITS);
        assertEquals(1,desktopVisits.size());
        assertTrue(desktopVisits.get(0).getMetric() > 0.0);

        List<VisitMetric> mobileVisits = similarWebService.
                trafficMetrics(connectorInfo, "syncari.com", yearAndMonth, yearAndMonth, "world", SimilarWebAPICategory.TOTAL_TRAFFIC, SimilarWebAPIName.MOBILE_VISITS);
        assertEquals(1,mobileVisits.size());
        assertTrue(mobileVisits.get(0).getMetric() > 0.0);

        assertEquals(1, (int) Math.round(desktopVisits.get(0).getMetric() + mobileVisits.get(0).getMetric()));
    }

    @Ignore
    @Test
    public void similarWebGlobalAndCountryRank() {
        SimilarWebService similarWebService = new SimilarWebService();
        similarWebService.cacheRepo = mock(EnrichmentCacheRepo.class);
        String yearAndMonth = ZonedDateTime.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        List<VisitMetric> visits = similarWebService.
                trafficMetrics(connectorInfo, "syncari.com", yearAndMonth, yearAndMonth, "world", SimilarWebAPICategory.GLOBAL_RANK, SimilarWebAPIName.GLOBAL_RANK);
        assertEquals(1,visits.size());
        assertTrue(visits.get(0).getMetric() > 1.0);
        visits = similarWebService.
                trafficMetrics(connectorInfo, "syncari.com", yearAndMonth, yearAndMonth, "US", SimilarWebAPICategory.COUNTRY_RANK, SimilarWebAPIName.COUNTRY_RANK);
        assertEquals(1,visits.size());
        assertTrue(visits.get(0).getMetric() > 1.0);
    }

    @Ignore
    @Test
    public void similarWebTechnographics() {
        SimilarWebService similarWebService = new SimilarWebService();
        similarWebService.cacheRepo = mock(EnrichmentCacheRepo.class);
        String technologies = similarWebService.
                retrieveTechnographics(connectorInfo, "syncari.com", SimilarWebAPICategory.TECHNOGRAPHICS, SimilarWebAPIName.TECHNOGRAPHICS);
        List<String> techList = Arrays.asList(technologies.split(", "));
        assertTrue(techList.size() > 1);
    }

    @Test
    public void similarWebMetricRange(){
        SimilarWebService similarWebService = new SimilarWebService();
        similarWebService.cacheRepo = mock(EnrichmentCacheRepo.class);
        Map<String, EnrichmentCache> cache = new HashMap<>();
        when(similarWebService.cacheRepo.findByServiceIdAndEntityNameAndEnrichKey(eq("swConnId"),eq("trafficData"),anyString())).then(
                invocationOnMock -> {
                    String url =invocationOnMock.getArgument(2);
                    return Optional.ofNullable(cache.get(url));
                }
        );
        when(similarWebService.cacheRepo.save(any(EnrichmentCache.class))).then(
                invocationOnMock -> {
                    EnrichmentCache enrichment =invocationOnMock.getArgument(0);
                    cache.put(enrichment.getEnrichKey(),enrichment);
                    return enrichment;
                }
        );

        MetricRange range = similarWebService.getMetricRange(connectorInfo, "syncari.com", SimilarWebAPICategory.TOTAL_TRAFFIC);
        assertNotNull(range);
        assertNotNull(range.getStartDate());
        assertNotNull(range.getEndDate());
        Date startDate = DateUtil.parse(range.getStartDate(), "yyyy-MM");
        Date endDate = DateUtil.parse(range.getEndDate(), "yyyy-MM");
        assertEquals(1, cache.size());

        range = similarWebService.getMetricRange(connectorInfo, "syncari.com", SimilarWebAPICategory.DESKTOP_TRAFFIC);
        assertNotNull(range);
        assertNotNull(range.getStartDate());
        assertNotNull(range.getEndDate());
        startDate = DateUtil.parse(range.getStartDate(), "yyyy-MM");
        endDate = DateUtil.parse(range.getEndDate(), "yyyy-MM");
        assertEquals(2, cache.size());

        range = similarWebService.getMetricRange(connectorInfo, "syncari.com", SimilarWebAPICategory.MOBILE_WEB_TRAFFIC);
        assertNotNull(range);
        assertNotNull(range.getStartDate());
        assertNotNull(range.getEndDate());
        startDate = DateUtil.parse(range.getStartDate(), "yyyy-MM");
        endDate = DateUtil.parse(range.getEndDate(), "yyyy-MM");
        assertEquals(2, cache.size()); // mobile web uses same desktop traffic endpoint to get range

        range = similarWebService.getMetricRange(connectorInfo, "syncari.com", SimilarWebAPICategory.GLOBAL_RANK);
        assertNotNull(range);
        assertNotNull(range.getStartDate());
        assertNotNull(range.getEndDate());
        startDate = DateUtil.parse(range.getStartDate(), "yyyy-MM");
        endDate = DateUtil.parse(range.getEndDate(), "yyyy-MM");
        assertEquals(3, cache.size());

        range = similarWebService.getMetricRange(connectorInfo, "syncari.com", SimilarWebAPICategory.MOBILE_WEB_TRAFFIC);
        assertNotNull(range);
        assertNotNull(range.getStartDate());
        assertNotNull(range.getEndDate());
        startDate = DateUtil.parse(range.getStartDate(), "yyyy-MM");
        endDate = DateUtil.parse(range.getEndDate(), "yyyy-MM");
        assertEquals(3, cache.size()); // country-rank uses same global-rank endpoint to get range

        // range not available for lead enrichment and technographics
        range = similarWebService.getMetricRange(connectorInfo, "syncari.com", SimilarWebAPICategory.LEAD_ENRICHEMNT);
        assertNotNull(range);
        assertNotNull(range.getStartDate());
        assertNotNull(range.getEndDate());
        startDate = DateUtil.parse(range.getStartDate(), "yyyy-MM");
        endDate = DateUtil.parse(range.getEndDate(), "yyyy-MM");
        assertEquals(4, cache.size());

        range = similarWebService.getMetricRange(connectorInfo, "syncari.com", SimilarWebAPICategory.TECHNOGRAPHICS);
        assertNull(range);
    }


    private ConnectorInfo createConnector(){
        ConnectorInfo conn = new ConnectorInfo("swConnId", "similarweb", "", "1235");
        conn.setAuthConfig(new AuthConfig().setToken(TOKEN));
        return conn;
    }

}