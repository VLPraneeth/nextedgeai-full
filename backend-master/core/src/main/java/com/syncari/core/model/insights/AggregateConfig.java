package com.syncari.core.model.insights;

import com.syncari.utils.I18n;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Objects;

import static com.syncari.core.utils.ValidationUtils.validateCondition;

@Data
@Accessors(chain = true)
public class AggregateConfig {
    private QField aggregateField;
    private QueryFunction queryFunction;

    public void validate(){
        validateCondition(aggregateField == null, I18n.i18n("error_dataset_aggregate"));
    }
}
