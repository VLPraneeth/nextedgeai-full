package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.ANALYTICS;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.syncari.api.rest.controllers.data.WidgetDTO;
import com.syncari.api.rest.dashboard.widget.WidgetCreator;
import com.syncari.api.rest.dashboard.widget.WidgetCreatorFactory;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.UserPreference;
import com.syncari.core.service.UserService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    @Autowired
    UserService userService;
    @Autowired
    WidgetCreatorFactory factory;

    @Secured(ANALYTICS)
    @RequestMapping(method = RequestMethod.GET)
    public List<WidgetDTO> getDashboard() {
        log.info("Starting widget fetch");
        List<WidgetDTO> result = new ArrayList<>();
        UserPreference preference = userService.getPreference(SyncariContext.getUser().getId());
        List<WidgetCreator> list = preference.getDashboard() == null ? factory.defaultWidgets()
                : factory.getWidgetCreatorsFor(preference.getDashboard().getWidgetIds());
        var org = SyncariContext.getOrganziation();
        var instance = SyncariContext.getInstance();
        var user = SyncariContext.getUser();

        result = list.parallelStream().map(w -> SyncariContext.runWithSupplier(org, instance, user, () -> {
            log.info("Fetching {}", w.getApiName());
            return w.populate(new WidgetDTO(), preference);
        })).collect(Collectors.toList());

        log.info("Successfully fetched {} widgets", result.size());
        return result;
    }

    @Secured(ANALYTICS)
    @RequestMapping(method = RequestMethod.GET, value = "/widget/{widgetId}")
    public WidgetDTO getWidget(@PathVariable String widgetId) {
        log.info("Starting fetch widget with id {}", widgetId);
        WidgetCreator creator = factory.getWidgetCreator(widgetId);
        UserPreference preference = userService.getPreference(SyncariContext.getUser().getId());
        WidgetDTO widget = creator.populate(new WidgetDTO(), preference);
        log.info("Done fetching widget with id {}", widgetId);
        return widget;
    }

    @Secured(ANALYTICS)
    @RequestMapping(method = RequestMethod.GET, value = "/widget")
    public List<Object> getDashboardWidgets() {
        List<Object> result = new ArrayList<>();
        UserPreference preference = userService.getPreference(SyncariContext.getUser().getId());
        List<WidgetCreator> widgetCreators = factory.getAllWidgetCreator();
        widgetCreators.forEach(w -> {
            result.add(w.populateMetadata(new WidgetDTO(), preference));
        });
        return result;
    }
    
}
