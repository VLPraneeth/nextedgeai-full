package com.syncari.core.functions;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.config.AuthConfig;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataTransformer;
import com.syncari.core.TestConfig;
import com.syncari.core.enrich.similarweb.MetricRange;
import com.syncari.core.enrich.similarweb.SimilarWebAPICategory;
import com.syncari.core.enrich.similarweb.SimilarWebAPIName;
import com.syncari.core.enrich.similarweb.SimilarWebService;
import com.syncari.core.enrich.similarweb.VisitMetric;
import com.syncari.core.model.Connector;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.ParameterValue;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.token.TokenHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

public class SimilarWebFunctionsTest extends AbstractSyncariTest {

        @Autowired
        SimilarWebFunctions function;
        @Autowired
        DataTransformer transformer;

        public static final String TOKEN = "TOKEN";

    @Test
    public void similarWebTrafficData_OutsideAvailableRange(){

        var orgConnService = function.connectorService;
        var orgSwService = function.similarWebService;
        try {
            Connector swConn = createConnector();
            ConnectorInfo swConnInfo = transformer.toConnectorInfo(swConn);
            ConnectorService mockConnService = mock(ConnectorService.class);
            doReturn(Optional.of(swConn)).when(mockConnService).find(swConn.getId());
            SimilarWebService mockSwService = mock(SimilarWebService.class);
            function.similarWebService = mockSwService;
            function.connectorService = mockConnService;
            String domain = "syncari.com";
            doReturn(new MetricRange("2020-01", "2021-01"))
                    .when(mockSwService).getMetricRange(swConnInfo, domain, SimilarWebAPICategory.TOTAL_TRAFFIC);
            doReturn(List.of(new VisitMetric("2021-01", 100.0)))
                    .when(mockSwService).trafficMetrics(swConnInfo, domain, "2021-01", "2021-01",
                    "world", SimilarWebAPICategory.TOTAL_TRAFFIC, SimilarWebAPIName.VISIT);

            FunctionCall functionCall = createCall("countryCode", "world", "date", "2021-05", "similarWebConnectorId",
                    swConn.getId(), "apiCategory", SimilarWebAPICategory.TOTAL_TRAFFIC.name(), "apiName", SimilarWebAPIName.VISIT.name());
            var value = function.similarWebTrafficData(domain, functionCall, new GraphContext());

            assertEquals(value, 100.0d);

            verify(mockSwService).getMetricRange(swConnInfo, domain, SimilarWebAPICategory.TOTAL_TRAFFIC);
            verify(mockSwService).trafficMetrics(swConnInfo, domain, "2021-01", "2021-01",
                    "world", SimilarWebAPICategory.TOTAL_TRAFFIC, SimilarWebAPIName.VISIT);
        } finally {
            function.similarWebService = orgSwService;
            function.connectorService = orgConnService;
        }
    }

    @Test
    public void similarWebTrafficData_WithTokenResolution(){

        var orgConnService = function.connectorService;
        var orgSwService = function.similarWebService;
        try {
            Connector swConn = createConnector();
            ConnectorInfo swConnInfo = transformer.toConnectorInfo(swConn);
            ConnectorService mockConnService = mock(ConnectorService.class);
            doReturn(Optional.of(swConn)).when(mockConnService).find(swConn.getId());
            SimilarWebService mockSwService = mock(SimilarWebService.class);

            function.similarWebService = mockSwService;
            function.connectorService = mockConnService;
            String domain = "syncari.com";
            doReturn(new MetricRange("2020-01", "2021-01"))
                    .when(mockSwService).getMetricRange(swConnInfo, domain, SimilarWebAPICategory.TOTAL_TRAFFIC);
            doReturn(List.of(new VisitMetric("2021-01", 100.0)))
                    .when(mockSwService).trafficMetrics(swConnInfo, domain, "2021-01", "2021-01",
                    "US", SimilarWebAPICategory.TOTAL_TRAFFIC, SimilarWebAPIName.VISIT);

            GraphContext graphContext = new GraphContext().set("mkto",
                    Map.of("lead", Map.of("countryCode", "US")));
            FunctionCall functionCall = createCall("countryCode", "{{mkto.lead.countryCode}}", "date", "2021-05", "similarWebConnectorId",
                    swConn.getId(), "apiCategory", SimilarWebAPICategory.TOTAL_TRAFFIC.name(), "apiName", SimilarWebAPIName.VISIT.name());
            var value = function.similarWebTrafficData(domain, functionCall, graphContext);

            assertEquals(value, 100.0d);

            verify(mockSwService).getMetricRange(swConnInfo, domain, SimilarWebAPICategory.TOTAL_TRAFFIC);
            // token resolved countryCode is used
            verify(mockSwService).trafficMetrics(swConnInfo, domain, "2021-01", "2021-01",
                    "US", SimilarWebAPICategory.TOTAL_TRAFFIC, SimilarWebAPIName.VISIT);
        } finally {
            function.similarWebService = orgSwService;
            function.connectorService = orgConnService;
        }
    }

    private Connector createConnector(){
        Connector conn = new Connector();
        conn.setId("swConnId");
        conn.setName("similarweb");
        conn.setMetadataId("swMetaId");
        conn.setAuthConfig(new AuthConfig().setToken(TOKEN));
        return conn;
    }

    private FunctionCall createCall(Object... keyValues) {
        Map<String, Object> config = new HashMap<>();
        if (keyValues != null) {
            for (int i = 0; i < keyValues.length; i += 2) {
                config.put(keyValues[i].toString(), keyValues[i + 1]);
            }
        }
        return new FunctionCall().setConfig(config).setParams(List.of(ParameterValue.string("param", "input")));
    }
}
