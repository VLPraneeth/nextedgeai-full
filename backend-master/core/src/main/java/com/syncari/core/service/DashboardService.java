package com.syncari.core.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.syncari.core.dashboard.DashboardSeed;
import com.syncari.core.dashboard.WidgetCreator;
import com.syncari.core.dashboard.WidgetFactory;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Dashboard;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.Widget;
import com.syncari.core.repositories.customer.DashboardRepo;
import com.syncari.utils.I18n;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DashboardService {
    @Autowired
    DashboardRepo dashboardRepo;
    @Autowired
    EntityRepoService repoService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    WidgetFactory widgetFactory;

    public static final Set<String> systemDashboards = Set.of("dqsOverview", "default");

    public Dashboard getDashBoard(String name) {
        if(StringUtils.isBlank(name)) {
            throw new SyncariValidationException(I18n.i18n("dash_name_required"));
        }
        Dashboard d = new Dashboard().setCategory(DashboardSeed.DQS).setName(name);
        return populateEntity(DashboardSeed.populate(d));
    }

    public List<Dashboard> getDashBoards(String category) {
        List<EntityDefinition> syncariEntities = schemaService.getAllPublishedEntities(connectorService.getSyncariConnector().getId());
        Set<Dashboard> dashboards = new TreeSet<>();
        // TODO: Implement this the right way. We should support multiple category. 
        // TODO: Should we seed the dashboard entry in the dashboardRepo and use it ?
        if (StringUtils.isBlank(category) || !category.equalsIgnoreCase(DashboardSeed.DQS)) {
            return List.of();
        }
        dashboards.add(new Dashboard().setCategory(DashboardSeed.DQS).setName(DashboardSeed.DQS_OVERVIEW).setTitle("DQS Overview")
            .setEntityId(DashboardSeed.DQS_OVERVIEW).setEntityApiName(DashboardSeed.DQS_OVERVIEW));
        syncariEntities.forEach(x -> dashboards.add(
                DashboardSeed.populate(new Dashboard().setCategory(DashboardSeed.DQS)
                    .setName(x.getApiName() + "_" + DashboardSeed.DQS_OVERVIEW)).setEntityId(x.getId()).setTitle(x.getDisplayName())
            ));
        return dashboards.stream().map(f -> f).collect(Collectors.toList());
    }

    public Widget getWidget(String dashboardName, String widgetName) {
        Dashboard dashBoard = getDashBoard(dashboardName);
        populate(dashBoard, widgetName);
        return dashBoard.getWidget(widgetName).get();
    }

    private void populate(Dashboard dashBoard, String widgetName) {
        WidgetCreator widgetCreator = widgetFactory.getWidgetCreator(widgetName);
        widgetCreator.populateData(dashBoard);
    }

    private Dashboard populateEntity(Dashboard dashboard) {
        // TODO: in near future, we would persist this info when creating the dashboard to avoid this lookup.
        // This is a temporary fix for UI to identify the entity.
        if (StringUtils.isEmpty(dashboard.getEntityApiName()) || systemDashboards.contains(dashboard.getName())) {
            return dashboard;
        }
        Optional<EntityDefinition> entity = schemaService.getSyncariEntityByName(dashboard.getEntityApiName());
        if (!entity.isPresent()) {
            throw new SyncariValidationException(String.format(I18n.i18n("dash_not_found"), dashboard.getEntityApiName()));
        }
        return dashboard.setEntityId(entity.get().getId()).setEntityApiName(entity.get().getApiName()).setTitle(entity.get().getDisplayName());
    }
}