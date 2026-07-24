package com.syncari.core.model.insights;

import com.syncari.core.model.misc.Position;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class PieVizConfig extends VizConfig {
    Position legendPosition = Position.BOTTOM;
    String colorTheme;
    QueryField category;
    QueryField subCategory;
    QueryField value;
    PieVizMinimumValue minimumValue;
    boolean legendVisible;
    boolean labelVisible;

    public static final int DEFAULT_LIMIT = 5000;

    @Override
    public int getLimit(){
        return limit > 0 ? limit : DEFAULT_LIMIT;
    }

    @Override
    public VizConfig makeCopy(){
        PieVizConfig func = new PieVizConfig();
        func.setName(name).setDatasetId(datasetId);

        if (null != this.getColumns()) {
            if (CollectionUtils.isNotEmpty(this.getColumns())){
                List<QueryField> cols = new ArrayList<>();
                this.getColumns().forEach(col -> {
                    cols.add(col.makeCopy());
                });
                func.setColumns(cols);
            }
        }

        if (null != category) {
            ((PieVizConfig)func).setCategory(category.makeCopy());
        }
        if (null != subCategory) {
            ((PieVizConfig)func).setSubCategory(subCategory.makeCopy());
        }
        if (null != value) {
            ((PieVizConfig)func).setValue(value.makeCopy());
        }
        func.setMinimumValue(this.getMinimumValue());
        func.setLegendVisible(this.isLegendVisible());
        func.setLabelVisible(this.isLabelVisible());

        // To do delete following when moving to new model.
        func.setLimit(this.getLimit());
        func.setJoins(this.getJoins());
        func.setSortList(this.getSortList());
        func.setPipelineDependencies(this.getPipelineDependencies());
        func.setGroupingColumns(this.getGroupingColumns());
        func.setFromWithAlias(this.fromWithAlias);
        func.setDateFilter(this.getDateFilter());
        func.setChildVizConfig(this.getChildVizConfig());
        func.setPredicate(this.getPredicate());
        func.setColorTheme(this.getColorTheme());
        func.setLegendPosition(this.getLegendPosition());

        return func;
    }

}
