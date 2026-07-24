package com.syncari.core.model.insights.dataset;

import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Data
public class DatasetFilter extends PredicateParser{

    // override expression functions for nested queries, otherwise predicate parser should work out of the box
    protected Map<String, Function<List<Map<String, Object>>, Expression>> datasetOperationalOperator = new HashMap<>();
    public DatasetFilter(String prefix){
        super(prefix);
    }
}
