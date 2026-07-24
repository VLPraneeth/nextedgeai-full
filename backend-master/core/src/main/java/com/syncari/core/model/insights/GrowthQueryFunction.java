package com.syncari.core.model.insights;

import com.syncari.core.exceptions.SyncariValidationException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class GrowthQueryFunction extends DecimalNaryQueryFunction {

    public GrowthQueryFunction() {
        super(AggFunctions.GROWTH);
    }

    @Override
    protected String toExpression(List<String> params) {
        if (params.size() != 2) {
            throw new SyncariValidationException("Growth function requires exactly two numeric fields, but % (%) were given", params.size(), params);
        }
        return String.format("ROUND(CAST(((%s - %s)/NULLIF(%s,0))*100 as numeric), 2)", params.get(0), params.get(1), params.get(1));
    }

}
