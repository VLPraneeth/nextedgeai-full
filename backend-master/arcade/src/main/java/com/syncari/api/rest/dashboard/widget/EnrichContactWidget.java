package com.syncari.api.rest.dashboard.widget;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.analytics.service.AnalyticsService;
import com.syncari.analytics.service.data.MetricOverTime;
import com.syncari.api.rest.controllers.data.WidgetDTO;
import com.syncari.core.model.UserPreference;
import com.syncari.core.model.misc.ChartType;
import com.syncari.core.model.misc.TemporalType;
import com.syncari.core.model.misc.WidgetType;
import com.syncari.core.service.ConnectorService;
import com.syncari.utils.DateUtil;

@Component
public class EnrichContactWidget extends BaseWidgetCreator {
	private static final String NAME = "Enrich Contact";
	@Autowired
	AnalyticsService service;
	@Autowired
	ConnectorService connectorService;
	@Autowired
	DateUtil dateUtil;

	@Override
	public WidgetDTO populate(WidgetDTO dto, UserPreference userPreference) {
		dto = super.populate(dto, userPreference);
		Date start = getStart(dto, userPreference);
        Instant startDate = start.toInstant();
        Date end = getEnd(dto, userPreference);
        Instant endDate = end.toInstant();
        List<MetricOverTime> enriched = service.getEnrichCount(startDate, endDate, "enrichPerson");
        long total = 0;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        Date tempStart = dateUtil.clipToStart(start);
        while (tempStart.before(end)) {
            String key = String.format("%s/%s", dateUtil.getMonth(tempStart), dateUtil.getDay(tempStart));
            if(!result.containsKey(key)) {
                result.put(key, 0);
            }
            tempStart = dateUtil.plusOneDay(tempStart);
        }
        for (MetricOverTime e : enriched) {
            Long countByRange = e.getCount();
            total = total + countByRange;
            Date label = Date.from(Instant.ofEpochMilli(e.getTime()));
            result.put(String.format("%s/%s", dateUtil.getMonth(label), dateUtil.getDay(label)), countByRange);
        }
        dto.setTitle(String.format("%s", total));
        dto.setSubTitle("Enriched Contacts");
        dto.setData(result);
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
		return "/icons/widgets/enrichContact.jpg";
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
		return Map.of("x", 3, "y", 3, "w", 3, "h", 6);
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
        return "enrichContact";
    }

}
