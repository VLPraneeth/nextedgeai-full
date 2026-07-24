package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class TableVizConfig extends VizConfig{

    public static final int DEFAULT_LIMIT = 25;

    @Override
    public int getLimit(){
        return limit > 0 ? limit : DEFAULT_LIMIT;
    }

    @Override
    public String toString(){
        return super.toString();
    }

    public VizConfig makeCopy(){
        TableVizConfig func = new TableVizConfig();
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
        // To do delete following when moving to new model.
        func.setLimit(this.getLimit());
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
