package com.syncari.core.model;

import com.syncari.core.pipeline.expression.Expression;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Transient;

import java.util.Map;

@Data
@Accessors(chain = true)
public class FieldMergePolicy {
    @Transient
    private Expression expresson;
    private WinnerOverridePolicy overridePolicy = WinnerOverridePolicy.WHEN_BLANK;
    // send this along for setting the field level merge policy in transaction log
    private Map<String, Object> expressionMap;
}
