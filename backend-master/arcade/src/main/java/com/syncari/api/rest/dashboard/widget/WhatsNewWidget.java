package com.syncari.api.rest.dashboard.widget;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.syncari.core.model.misc.ChartType;
import com.syncari.core.model.misc.WidgetType;

@Component
public class WhatsNewWidget extends BaseWidgetCreator {
	private static final String NAME = "What's New?";

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
		return "/icons/widgets/whatsNew.jpg";
	}

	@Override
	protected WidgetType getWidgetType() {
		return WidgetType.list;
	}

	@Override
	protected ChartType getChartType() {
		return null;
	}

	@Override
	protected Map<String, Object> getLayout() {
		return Map.of("x", 3, "y", 0, "w", 3, "h", 6);
	}

	@Override
	protected Map<String, Object> getTemporal() {
		return null;
	}

	@Override
	protected Map<String, Object> getData() {
		List<Map> whatsNew = List.of(
			Map.of("label", "13 New Functions added", "url", "https://support.syncari.com/hc/en-us/sections/360012103912-Functions"),
			Map.of("label", "Redshift is now supported", "url", "https://support.syncari.com/hc/en-us/articles/360052656731-Redshift-Setup"),
			Map.of("label", "Company lookup dataset", "url", "https://support.syncari.com/hc/en-us/articles/360052207512-Using-Reference-Data"));
		return Map.of("list", whatsNew);
	}

	@Override
	public String getName() {
		return NAME;
	}

    @Override
    public String getApiName() {
        return "whatsNew";
    }

}
