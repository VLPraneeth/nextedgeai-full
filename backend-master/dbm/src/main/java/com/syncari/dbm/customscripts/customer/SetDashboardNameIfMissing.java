package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.service.InsightsDashboardService;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.parboiled.common.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.stream.Collectors;

@Slf4j
public class SetDashboardNameIfMissing {

    @ChangeSet(order = "001", id = "setDashboardNameIfMissing", author = "abhinav")
    public void setDashboardNameIfMissing(MongoTemplate mongoTemplate) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));

        InsightsDashboardService dashboardService = MigrationContext.getDashboardService();

        var dashboards = dashboardService.getAllDashboards().stream().map(d -> {
            if(StringUtils.isEmpty(d.getName())){
                log.info("Updating name for dashboard {} with id {}", d.getDisplayName(), d.getId());
                d.setName(TextUtil.createApiName(d.getDisplayName()));
            }
            return d;
        }).collect(Collectors.toList());
        if(!dryRun) {
            dashboardService.saveAll(dashboards);
        }

    }
}
