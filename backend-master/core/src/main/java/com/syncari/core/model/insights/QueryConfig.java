package com.syncari.core.model.insights;

import com.syncari.core.model.insights.dataset.DatasetFrom;
import com.syncari.core.model.insights.dataset.Join;
import com.syncari.core.model.insights.dataset.Sort;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class QueryConfig {

    private List<QueryField> columns;
    private int limit;
    private List<AggregateConfig> groupingColumns = new ArrayList<>();
    private boolean isGroup;
    private List<Sort> sortList = new ArrayList<>();
    private List<DatasetFrom> fromDatasets;
    private Map<String, Object> predicate;
    private List<Join> joins;
    private QueryConfig childQueryConfig;
    private Long offset;

}
