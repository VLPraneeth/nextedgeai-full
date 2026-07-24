package com.syncari.api.rest.dashboard.widget;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.syncari.core.model.misc.ChartType;
import com.syncari.core.model.misc.WidgetType;

@Component
public class GettingStartedWidget extends BaseWidgetCreator {
	private static final String NAME = "Getting Started with Syncari";

	@Override
	public boolean isDisplayable() {
		return true;
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
		return "/icons/widgets/gettingStartedWithSyncari.jpg";
	}

	@Override
	protected WidgetType getWidgetType() {
		return WidgetType.gettingstarted;
	}

	@Override
	protected ChartType getChartType() {
		return null;
	}

	@Override
	protected Map<String, Object> getLayout() {
		return Map.of("x", 0, "y", 0, "w", 3, "h", 6);
	}

	@Override
	protected Map<String, Object> getTemporal() {
		return null;
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
        return "gettingStartedWithSyncari";
    }

}
