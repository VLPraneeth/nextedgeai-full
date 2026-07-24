package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.insights.InsightsDashboard;
import com.syncari.core.service.InsightsDashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SYN_9750_SetSeededFlagForInsightsDashboard {

    @ChangeSet(order = "001", id = "setSeededFlagForInsightsDashboard", author = "abhinav", runAlways = true)
    public void setSeededFlagForInsightsDashboard(MongoTemplate mongoTemplate) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));

        List<String> seededDashboards = List.of("marketing", "sales", "executive", "success");
        InsightsDashboardService dashboardService = MigrationContext.getDashboardService();

        List<InsightsDashboard> toUpdate = new ArrayList<>();
        dashboardService.getAllDashboards().forEach(d -> {
            if(seededDashboards.contains(d.getName())){
                if(d.isApproved()){
                    log.info("Setting seeded=true for dashboard {} with id {}", d.getDisplayName(), d.getId());
                    d.setSeeded(true);
                    toUpdate.add(d);
                } else if(d.isDraft()){
                    log.info("Discarding draft for dashboard {} with id {} as its seeded", d.getDisplayName(), d.getId());
                    dashboardService.discardDraftDashboard(d);
                }
            }
        });
        if(!dryRun) {
            dashboardService.saveAll(toUpdate);
        }

    }
}
