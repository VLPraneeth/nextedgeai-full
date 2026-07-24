package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.ANALYTICS;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.syncari.api.rest.dashboard.widget.WidgetCreatorFactory;
import com.syncari.core.model.Dashboard;
import com.syncari.core.model.misc.Widget;
import com.syncari.core.service.DashboardService;
import com.syncari.core.service.UserService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v2/dashboard")
public class NewDashboardController {
    @Autowired
    UserService userService;
    @Autowired
    WidgetCreatorFactory factory;
    @Autowired
    DashboardService dashboardService;

    @Secured(ANALYTICS)
    @RequestMapping(method = RequestMethod.GET, value = "/{dashboardName}/widget/{widgetName}")
    public Widget getWidget(@PathVariable String dashboardName, @PathVariable String widgetName) {
        log.info("Starting fetch widget with name {}", widgetName);
        return dashboardService.getWidget(dashboardName, widgetName);
    }

    @Secured(ANALYTICS)
    @RequestMapping(method = RequestMethod.GET, value = "/{dashboardName}")
    public Dashboard getDashboardByName(@PathVariable String dashboardName) {
        log.info("Starting dashboard fetch");
        return dashboardService.getDashBoard(dashboardName);
    }
    
    @Secured(ANALYTICS)
    @RequestMapping(method = RequestMethod.GET)
    public List<Dashboard> getDashboards(@RequestParam(required = false) String category) {
        List<Dashboard> dashBoards = dashboardService.getDashBoards(category);
        dashBoards.stream().forEach(d -> d.setWidgets(List.of()));
        return dashBoards;
    }
}
