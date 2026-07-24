package com.syncari.api.rest.dashboard.widget;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.api.rest.controllers.data.WidgetDTO;
import com.syncari.core.model.UserPreference;
import com.syncari.core.model.misc.ChartType;
import com.syncari.core.model.misc.TemporalType;
import com.syncari.core.model.misc.WidgetType;
import com.syncari.core.service.EntityRepoService;
import com.syncari.utils.DateUtil;

@Component
public class EmailValidationWidget extends BaseWidgetCreator {
	private static final String NAME = "Contact Email Quality";
	@Autowired
	EntityRepoService repoService;
	@Autowired
	DateUtil dateUtil;

	@Override
	public WidgetDTO populate(WidgetDTO dto, UserPreference userPreference) {
		dto = super.populate(dto, userPreference);
		Map<String, Object> results = repoService.getContactEmailValidationCount();
		dto.setTitle("Contact");
		dto.setSubTitle("Email Quality");
		dto.setData(results);
		return dto;
	}

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
		return "/icons/widgets/contactEmailQuality.jpg";
	}

	@Override
	protected WidgetType getWidgetType() {
		return WidgetType.chart;
	}

	@Override
	protected ChartType getChartType() {
		return ChartType.bar;
	}

	@Override
	protected Map<String, Object> getLayout() {
		return Map.of("x", 3, "y", 3, "w", 3, "h", 6);
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getName() {
		return NAME;
	}

    @Override
    public String getApiName() {
        return "contactEmailQuality";
    }

}
