package com.syncari.api.rest.dashboard.widget;

import com.syncari.api.rest.controllers.data.WidgetDTO;
import com.syncari.core.model.UserPreference;

public interface WidgetCreator {
	public WidgetDTO populate(WidgetDTO dto, UserPreference userPreference);

	public WidgetDTO populateMetadata(WidgetDTO dto, UserPreference userPreference);

	public String getName();

	public String getApiName();

	public boolean isDisplayable();
	
}
