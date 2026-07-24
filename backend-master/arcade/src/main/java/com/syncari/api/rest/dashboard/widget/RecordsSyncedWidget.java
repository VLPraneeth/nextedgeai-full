package com.syncari.api.rest.dashboard.widget;

import java.util.Date;
import java.util.Map;

import com.syncari.api.rest.controllers.TransactionServiceWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.api.rest.controllers.data.WidgetDTO;
import com.syncari.core.model.UserPreference;
import com.syncari.core.model.misc.ChartType;
import com.syncari.core.model.misc.TemporalType;
import com.syncari.core.model.misc.WidgetType;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.TransactionLogService;
import com.syncari.utils.DateUtil;

@Component
public class RecordsSyncedWidget extends BaseWidgetCreator {
    private static final String NAME = "Total Transactions";
    @Autowired
    TransactionServiceWrapper service;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    DateUtil dateUtil;

    @Override
    public WidgetDTO populate(WidgetDTO dto, UserPreference userPreference) {
        dto = super.populate(dto, userPreference);
        Date startDate = getStart(dto, userPreference);
        Date endDate = getEnd(dto, userPreference);
        Long countByRange = service.countByRange(startDate, endDate, null);
        dto.setTitle(String.valueOf(countByRange));
        dto.setData(service.countByDayByRange(startDate, endDate));
        return dto;
    }

    @Override
    public boolean isDisplayable() {
        int activeConnections = connectorService.getTotalActiveConnections();
        return activeConnections > 0;
    }

    @Override
    protected String getTitle() {
        return null;
    }

    @Override
    protected String getSubTitle() {
        return NAME;
    }

    @Override
    protected String getIcon() {
        return "/icons/widgets/recordsSynced.jpg";
    }

    @Override
    protected WidgetType getWidgetType() {
        return WidgetType.chart;
    }

    @Override
    protected ChartType getChartType() {
        return ChartType.line;
    }

    @Override
    protected Map<String, Object> getLayout() {
        return Map.of("x", 0, "y", 6, "w", 3, "h", 6);
    }

    @Override
    protected Map<String, Object> getTemporal() {
        return Map.of("type", TemporalType.thisWeek, "startDate", new Date(), "endDate", new Date());
    }

    @Override
    protected Map<String, Object> getData() {
        return null;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getApiName() {
        return "recordsSynced";
    }

}
