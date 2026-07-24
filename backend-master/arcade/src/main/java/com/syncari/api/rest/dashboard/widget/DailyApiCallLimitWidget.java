package com.syncari.api.rest.dashboard.widget;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.analytics.service.AnalyticsService;
import com.syncari.api.rest.controllers.data.WidgetDTO;
import com.syncari.core.model.Connector;
import com.syncari.core.model.UserPreference;
import com.syncari.core.model.misc.ChartType;
import com.syncari.core.model.misc.TemporalType;
import com.syncari.core.model.misc.WidgetType;
import com.syncari.core.service.ConnectorService;
import com.syncari.utils.DateUtil;

@Component
public class DailyApiCallLimitWidget extends BaseWidgetCreator {
    private static final String NAME = "Daily Api Call Limit";
    @Autowired
    AnalyticsService analyticsService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    DateUtil dateUtil;

    @Override
    public WidgetDTO populate(WidgetDTO dto, UserPreference userPreference) {
        dto = super.populate(dto, userPreference);
        long used = analyticsService.totalApiCalls(dateUtil.getTodayStart(), dateUtil.getTodayEnd());
        long available = 0;
        List<Connector> all = connectorService.getAllActive();
        for (Connector connector : all) {
            if(connector.isActive()) {
                available = available + connector.getDailyQuota();
            }
        }
        dto.setTitle(String.format("%s / %s", used, available));
        dto.setData(Map.of("used", used, "available", available));
        return dto;
    }

    @Override
    public boolean isDisplayable() {
        int activeConnections = connectorService.getTotalActiveConnections();
        return activeConnections > 0;
    }

    @Override
    protected String getSubTitle() {
        return NAME;
    }

    @Override
    protected String getIcon() {
        return "/icons/widgets/dailyApiCallLimit.jpg";
    }

    @Override
    protected WidgetType getWidgetType() {
        return WidgetType.chart;
    }

    @Override
    protected ChartType getChartType() {
        return ChartType.dial;
    }

    @Override
    protected Map<String, Object> getLayout() {
        return Map.of("x", 3, "y", 6, "w", 3, "h", 6);
    }

    @Override
    protected Map<String, Object> getTemporal() {
        return Map.of("type", TemporalType.now);
    }

    @Override
    protected Map<String, Object> getData() {
        return null;
    }

    @Override
    protected String getTitle() {
        return null;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getApiName() {
        return "dailyApiCallLimit";
    }

}
