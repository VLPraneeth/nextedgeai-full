package com.syncari.core.model.insights;

import com.syncari.core.model.insights.dataset.Join;
import com.syncari.core.model.insights.dataset.Sort;
import com.syncari.core.model.insights.dataset.Variable;
import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;
import com.syncari.core.model.misc.Position;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class VizConfig {
    public static final int DEFAULT_LIMIT = 100;
    String name;
    String vizLabel;
    boolean vizLabelVisible;
    Position vizLabelPosition;
    // TODO: change this to simple field with apiName and displayname. Query function is not needed
    List<QueryField> columns;
    public int limit; // default limit is 2k if not set explicitly
    String datasetId;
    Map<String, Variable> variablesMap = new HashMap<>();
    List<KeyValue> categoryValues = new ArrayList<>();
    // TODO: to do remove attributes which are not needed after deploying new model
    DateFilter dateFilter;
    List<Sort> sortList = new ArrayList<>();
    Map<String, String> fromWithAlias;
    private Map<String, Object> predicate;
    private VizConfig childVizConfig;
    private List<Join> joins;
    private List<AggregateConfig> groupingColumns = new ArrayList<>();
    // We need to add offset for different visualization, which is missing.

    private List<PipelineDependency> pipelineDependencies = new ArrayList<>();

    public boolean hasDateFilterSupport(){
        return dateFilter != null;
    }

    public int getLimit(){
        return limit > 0 ? limit : DEFAULT_LIMIT;
    }

    @Override
    public String toString(){
        String withoutColumns = " datasetId : " + datasetId + " name : " + name;
        String withConfig =  (CollectionUtils.isNotEmpty(columns)) ? withoutColumns + " columns : " + columns : withoutColumns + " categoryValues : " + categoryValues;
        return hasDateFilterSupport() ? withConfig + dateFilter : withConfig;
    }

    public VizConfig makeCopy(){
        VizConfig func = new VizConfig().setName(name).setDatasetId(datasetId)
            .setVizLabel(vizLabel)
            .setVizLabelVisible(vizLabelVisible)
            .setVizLabelPosition(vizLabelPosition);
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
        func.setJoins(joins);
        func.setSortList(sortList);
        func.setPipelineDependencies(pipelineDependencies);
        func.setGroupingColumns(groupingColumns);
        func.setFromWithAlias(fromWithAlias);

        func.setChildVizConfig(childVizConfig);
        func.setPredicate(predicate);
        func.setVariablesMap(variablesMap);
        if (CollectionUtils.isNotEmpty(categoryValues)){
            func.setCategoryValues(categoryValues);
        }

        return func;
    }
}
