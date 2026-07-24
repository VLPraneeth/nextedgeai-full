package com.syncari.core.dashboard;


import com.syncari.core.model.Dashboard;
import org.apache.commons.lang3.StringUtils;

public class DashboardSeed {
    public static final String DQS = "dqs";
    public static final String DQS_OVERVIEW = "dqsOverview";

    public static Dashboard populate(Dashboard d){
        Dashboard fromSeed = DashboardSeed.get(d.getName());
        assert d.getName() != null;
        if(fromSeed != null) {
            d.setName(fromSeed.getName()).setTitle(fromSeed.getTitle()).setEntityApiName(fromSeed.getEntityApiName())
                .setEntityId(fromSeed.getEntityId())
                .setWidgets(fromSeed.getWidgets());
        }
        return d;
    }

    public static Dashboard get(String name) {
        if (name.equalsIgnoreCase("default")) {
            return getDefault();
        } else if (name.equalsIgnoreCase(DQS_OVERVIEW)) {
            return getDqs();
        }
        return getEntityDqs(name);
    }

    public static Dashboard getDefault() {
        return new Dashboard()
                .setName("default")
                .setTitle("Default");
    }
    
    public static Dashboard getDqs() {
        return new Dashboard()
                .setName(DQS_OVERVIEW)
                .setTitle("DQS Overview")
                .setCategory(DQS)
                .setEntityId(DashboardSeed.DQS_OVERVIEW)
                .setEntityApiName(DashboardSeed.DQS_OVERVIEW)
                .addWidget(WidgetSeed.get("overallFitness"))
                .addWidget(WidgetSeed.get("dataFitnessOvertime"))
                .addWidget(WidgetSeed.get("improvementOpportunities"))
                .addWidget(WidgetSeed.get("entityDataQualityBreakdown"));
    }

    public static Dashboard getEntityDqs(String name) {
        String entityApiName = name;
        if (StringUtils.isNotBlank(name) && name.contains("_" + DQS_OVERVIEW)) {
            entityApiName = name.substring(0, name.indexOf("_" + DQS_OVERVIEW));
        }
        return new Dashboard()
            .setName(entityApiName + "_" + DQS_OVERVIEW)
            .setTitle(StringUtils.capitalize(entityApiName))
            .setCategory(DQS)
            .setEntityApiName(entityApiName)
            .addWidget(WidgetSeed.get("overallFitness"))
            .addWidget(WidgetSeed.get("dataFitnessOvertime"))
            .addWidget(WidgetSeed.get(WidgetSeed.FIELD_SCORE));
    }
}