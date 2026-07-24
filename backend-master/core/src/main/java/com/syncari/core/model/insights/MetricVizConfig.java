package com.syncari.core.model.insights;

import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Data
public class MetricVizConfig extends VizConfig{

    public static final int LIMIT = 1;
    List<VizRange> ranges;

    @Override
    public int getLimit(){
        return LIMIT;
    }

    @Override
    public String toString(){
        return super.toString();
    }

    public VizConfig makeCopy(){
        MetricVizConfig func = new MetricVizConfig();
        func.setName(name).setDatasetId(datasetId);
        if (CollectionUtils.isNotEmpty(this.getColumns())){
            List<QueryField> cols = new ArrayList<>();
            this.getColumns().forEach(col -> {
                cols.add(col.makeCopy());
            });
            func.setColumns(cols);
        }
        if (null != dateFilter){
            func.setDateFilter(dateFilter.makeCopy());
        }

        func.setRanges(this.getRanges());

        // To do delete following when moving to new model.
        func.setJoins(this.getJoins());
        func.setSortList(sortList);
        func.setPipelineDependencies(this.getPipelineDependencies());
        func.setGroupingColumns(this.getGroupingColumns());
        func.setFromWithAlias(fromWithAlias);

        func.setChildVizConfig(this.getChildVizConfig());
        func.setPredicate(this.getPredicate());

        return func;
    }
}
