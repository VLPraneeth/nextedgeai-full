package com.syncari.core.model.insights;

import com.syncari.core.model.misc.Position;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class FunnelVizConfig extends VizConfig {
    Position legendPosition = Position.BOTTOM;
    String colorTheme;
    QueryField measure;
    QueryField dataField;
    String sortBy;
    boolean ascending;
    List<String> stages;
    Position labelPosition = Position.LEFT;
    boolean legendVisible;
    boolean labelVisible;
    String displayAdditional;

    public static final int DEFAULT_LIMIT = 100;

    @Override
    public int getLimit(){
        return limit > 0 ? limit : DEFAULT_LIMIT;
    }

    @Override
    public VizConfig makeCopy(){
        FunnelVizConfig func = new FunnelVizConfig();
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

        if (null != measure) {
            func.setMeasure(measure.makeCopy());
        }
        if (null != dataField) {
            func.setDataField(dataField.makeCopy());
        }
        func.setSortBy(this.getSortBy());
        func.setAscending(this.isAscending());
        func.setStages(this.getStages());
        func.setLabelPosition(this.getLabelPosition());
        func.setLegendVisible(this.isLegendVisible());
        func.setLabelVisible(this.isLabelVisible());
        func.setDisplayAdditional(this.getDisplayAdditional());

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
