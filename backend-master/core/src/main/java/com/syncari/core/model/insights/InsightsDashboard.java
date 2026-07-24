package com.syncari.core.model.insights;

import com.syncari.core.model.Tag;
import com.syncari.core.model.misc.DraftableModel;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Transient;
import com.syncari.core.model.insights.DashboardVariableMapping;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class InsightsDashboard extends DraftableModel<InsightsDashboard> {

    String name;
    String displayName;
    String description;
    @Deprecated(forRemoval = true)
    List<String> dataCardIds = new ArrayList<>();
    List<DataCardSetting> dataCardSettings = new ArrayList<>();
    boolean seeded;
    List<DashboardVariableMapping> dashboardVariableMappings;

    @Transient
    List<Tag> tags = new ArrayList<>();

    @Override
    public InsightsDashboard makeCopy() {
        return new InsightsDashboard().setName(name).setDisplayName(displayName).setDescription(description)
                .setDataCardSettings(dataCardSettings).setDataCardIds(dataCardIds)
                .setDashboardVariableMappings(dashboardVariableMappings);
    }

    @Override
    public void copyValuesFrom(InsightsDashboard model) {
        setName(model.getName()).setDisplayName(model.getDisplayName()).setDescription(model.getDescription())
                .setDataCardSettings(model.getDataCardSettings()).setDataCardIds(model.getDataCardIds())
                .setDashboardVariableMappings(model.getDashboardVariableMappings());
    }
}
