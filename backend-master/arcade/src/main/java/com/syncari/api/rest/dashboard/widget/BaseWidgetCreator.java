package com.syncari.api.rest.dashboard.widget;

import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.syncari.api.rest.controllers.data.WidgetDTO;
import com.syncari.core.model.UserPreference;
import com.syncari.core.model.misc.ChartType;
import com.syncari.core.model.misc.WidgetSetting;
import com.syncari.core.model.misc.WidgetType;
import com.syncari.utils.DateUtil;

@Component
public abstract class BaseWidgetCreator implements WidgetCreator {

	protected abstract String getTitle();

	protected abstract String getSubTitle();
	
	protected abstract String getIcon();
	
	protected abstract WidgetType getWidgetType();
	
	protected abstract ChartType getChartType();
	
	protected abstract Map<String, Object> getLayout();
	
	protected abstract Map<String, Object> getTemporal();
	
	protected abstract Map<String, Object> getData();
	
	public abstract String getName();

	@Override
	public WidgetDTO populate(WidgetDTO dto, UserPreference userPreference) {
		this.populateMetadata(dto, userPreference);
		Map<String, Object> data  = getData();
		dto.setData(data == null ? new HashMap<String, Object>() : data);
		return dto;
	}

	public WidgetDTO populateMetadata(WidgetDTO dto, UserPreference userPreference) {
		dto.setApiName(getApiName());
		dto.setName(getName());
		dto.setSubTitle(getSubTitle());
		dto.setTitle(getTitle());
		dto.setWidgetType(getWidgetType());
		dto.setIcon(getIcon());
		WidgetSetting setting = null;
		if (userPreference != null && userPreference.getDashboard() != null) {
			setting = userPreference.getDashboard().getWidgetSetting(getApiName());
		}

		if (setting == null) {
			setting = new WidgetSetting();
			setting.setLayout(getLayout());
			setting.setWidgetId(getApiName());
			setting.setTemporal(getTemporal());
		}

		dto.setSetting(setting);
		dto.setChartType(getChartType());
		return dto;
	}	

	public Date getStart(WidgetDTO dto, UserPreference userPreference) {
	    Date start = DateUtil.subtractDaysFromToday(4);
        if (userPreference != null && userPreference.getDashboard() != null
                && userPreference.getDashboard().getWidgetPreferences() != null) {
            List<WidgetSetting> dashPref = (List<WidgetSetting>) userPreference.getDashboard().getWidgetPreferences();
            for (WidgetSetting pref : dashPref) {
                if (dto.getApiName().equalsIgnoreCase(pref.getWidgetId())) {
                    String startString = null;
                    try {
                        if(pref.getTemporal() != null && pref.getTemporal().get("startDate") != null) {
                            startString = pref.getTemporal().get("startDate").toString();
                            start = DateUtil.parse(startString);
                        }
                    } catch (ParseException e) {
                        throw new RuntimeException(String.format(
                                "Invalid date in dashboard preference start: %s", startString));
                    }
                }
            }
        }
        return start;
	}	
	
	public Date getEnd(WidgetDTO dto, UserPreference userPreference) {
	    Date end = new Date();
	    if (userPreference != null && userPreference.getDashboard() != null
	            && userPreference.getDashboard().getWidgetPreferences() != null) {
	        List<WidgetSetting> dashPref = (List<WidgetSetting>) userPreference.getDashboard().getWidgetPreferences();
	        for (WidgetSetting pref : dashPref) {
	            if (dto.getApiName().equalsIgnoreCase(pref.getWidgetId())) {
	                String endString = null;
	                try {
	                    if(pref.getTemporal() != null && pref.getTemporal().get("endDate") != null) {
	                        endString = pref.getTemporal().get("endDate").toString();
	                        end = DateUtil.parse(endString);
	                    }
	                } catch (ParseException e) {
	                    throw new RuntimeException(String.format(
	                            "Invalid date in dashboard preference end: %s", endString));
	                }
	            }
	        }
	    }
	    return end;
	}	
}
