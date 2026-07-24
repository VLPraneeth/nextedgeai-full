package com.syncari.core.model.misc;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class DashboardPreference {
	List<WidgetSetting> widgetPreferences = new ArrayList<>();

	public WidgetSetting getWidgetSetting(String id) {
		for (WidgetSetting w : widgetPreferences) {
			if (w.getWidgetId().equalsIgnoreCase(id))
				return w;
		}
		return null;
	}
	
	public List<String> getWidgetIds() {
	    List<String> result = new ArrayList<>();
	    for (WidgetSetting w : widgetPreferences) {
	        result.add(w.getWidgetId());
	    }
	    return result;
	}
	
	public boolean hasWidgetSetting(String id) {
	    for (WidgetSetting w : widgetPreferences) {
            if (w.getWidgetId().equalsIgnoreCase(id))
                return true;
        }
        return false;
	}
}
