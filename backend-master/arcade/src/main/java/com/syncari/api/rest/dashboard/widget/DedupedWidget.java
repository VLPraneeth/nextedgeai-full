package com.syncari.api.rest.dashboard.widget;

import java.time.Instant;
import java.util.Date;
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
public class DedupedWidget extends BaseWidgetCreator {
	private static final String NAME = "Contacts";
	@Autowired
	AnalyticsService service;
	@Autowired
	ConnectorService connectorService;
	@Autowired
	DateUtil dateUtil;

	@Override
	public WidgetDTO populate(WidgetDTO dto, UserPreference userPreference) {
		dto = super.populate(dto, userPreference);
		Instant startDate = getStart(dto, userPreference).toInstant();
		Instant endDate = getEnd(dto, userPreference).toInstant();
        List<MetricOverTime> dedupes = service.getDedupeCount(startDate, endDate, "Contact");
		long dupFoundCount = 0;
		long mergedCount = 0;
		for (MetricOverTime d : dedupes) {
			if("Duplicates".equalsIgnoreCase(d.getConnectorName())) {
			    dupFoundCount = dupFoundCount + d.getCount();
			} else {
			    mergedCount = mergedCount + d.getCount();
			}
		}
		
		dto.setTitle(String.format("Duplicates removed", dupFoundCount, mergedCount));
		dto.setData(Map.of("metric", dupFoundCount - mergedCount));
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
		return "/icons/widgets/deduped.jpg";
	}

	@Override
	protected WidgetType getWidgetType() {
		return WidgetType.chart;
	}

	@Override
	protected ChartType getChartType() {
		return ChartType.number;
	}

	@Override
	protected Map<String, Object> getLayout() {
		return Map.of("x", 3, "y", 6, "w", 3, "h", 6);
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
        return "deduped";
    }

}
