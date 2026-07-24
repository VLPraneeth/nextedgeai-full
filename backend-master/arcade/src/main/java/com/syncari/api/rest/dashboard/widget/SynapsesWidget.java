package com.syncari.api.rest.dashboard.widget;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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

@Component
public class SynapsesWidget extends BaseWidgetCreator {
	private static final String NAME = "Transactions";
	@Autowired
	ConnectorService connectorService;
    @Autowired
    AnalyticsService analyticsService;

	@Override
	public WidgetDTO populate(WidgetDTO dto, UserPreference userPreference) {
		dto = super.populate(dto, userPreference);
		Map<String, Long> data = analyticsService.topActiveSynapses(Instant.now().minus(7, ChronoUnit.DAYS), Instant.now());
		for (Entry<String, Long> entry : data.entrySet()) {
		    dto.getData().put(entry.getKey(), entry.getValue());
        }
		dto.setTitle("Synapse activity");
		return dto;
	}

	@Override
	public boolean isDisplayable() {
		List<Connector> list = connectorService.getAll();
		return list.size() > 0;
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
		return "/icons/widgets/synapses.jpg";
	}

	@Override
	protected WidgetType getWidgetType() {
		return WidgetType.chart;
	}

	@Override
	protected ChartType getChartType() {
		return ChartType.donut;
	}

	@Override
	protected Map<String, Object> getLayout() {
		return Map.of("x", 0, "y", 0, "w", 3, "h", 6);
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
	public String getName() {
		return NAME;
	}

    @Override
    public String getApiName() {
        return "synapses";
    }

}
