package com.syncari.api.rest.controllers.data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.syncari.core.model.misc.ChartType;
import com.syncari.core.model.misc.WidgetSetting;
import com.syncari.core.model.misc.WidgetType;

import lombok.Data;

@Data
public class WidgetDTO implements Serializable {
    private String name;
    private String apiName;
    private String title;
    private String subTitle;
    private String icon;
    private WidgetType widgetType;
    private ChartType chartType;
    WidgetSetting setting;
    Map<String, Object> data = new HashMap<>();
}
