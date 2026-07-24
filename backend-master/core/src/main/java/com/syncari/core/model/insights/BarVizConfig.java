package com.syncari.core.model.insights;

import com.syncari.core.model.misc.Position;
import com.syncari.core.model.pagination.Sort;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class BarVizConfig extends VizConfig {
    QueryField xAxis;
    List<QueryField> yAxis = new ArrayList<>();
    List<QueryField> series = new ArrayList<>();
    StackingType stacking = StackingType.none;
    Position legendPosition = Position.BOTTOM;
    String colorTheme;

    public static final int DEFAULT_LIMIT = 100;

    @Override
    public int getLimit(){
        return limit > 0 ? limit : DEFAULT_LIMIT;
    }

    @Override
    public VizConfig makeCopy(){
        BarVizConfig func = new BarVizConfig();
        func.setName(name).setDatasetId(datasetId);
        if (CollectionUtils.isNotEmpty(this.getColumns())){
            List<QueryField> cols = new ArrayList<>();
            this.getColumns().forEach(col -> {
                cols.add(col.makeCopy());
            });
            func.setColumns(cols);
        }
        if (CollectionUtils.isNotEmpty(this.getYAxis())){
            List<QueryField> cols = new ArrayList<>();
            this.getYAxis().forEach(col -> {
                cols.add(col.makeCopy());
            });
            ((BarVizConfig)func).setYAxis(cols);
        }
        if (CollectionUtils.isNotEmpty(this.getSeries())){
            List<QueryField> cols = new ArrayList<>();
            this.getSeries().forEach(col -> {
                cols.add(col.makeCopy());
            });
            ((BarVizConfig)func).setSeries(cols);
        }
        if (null != xAxis){
            ((BarVizConfig)func).setXAxis(xAxis.makeCopy());
        }
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
