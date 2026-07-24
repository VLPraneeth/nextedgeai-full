package com.syncari.api.rest.dashboard.widget;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.syncari.core.model.misc.ChartType;
import com.syncari.core.model.misc.WidgetType;

@Component
public class CreateSynapseWidget extends BaseWidgetCreator {
	private static final String NAME = "Create your first Synapse";

	@Override
	public boolean isDisplayable() {
		return true;
	}

	@Override
	protected String getSubTitle() {
		return NAME;
	}

	@Override
	protected String getIcon() {
		return "/icons/widgets/createYourFirstSynapse.jpg";
	}

	@Override
	protected WidgetType getWidgetType() {
		return WidgetType.synapse;
	}

	@Override
	protected ChartType getChartType() {
		return null;
	}

	@Override
	protected Map<String, Object> getLayout() {
		return Map.of("x", 6, "y", 0, "w", 3, "h", 6);
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
	protected String getTitle() {
		return null;
	}

	@Override
	public String getName() {
		return NAME;
	}

    @Override
    public String getApiName() {
        return "createYourFirstSynapse";
    }

}
