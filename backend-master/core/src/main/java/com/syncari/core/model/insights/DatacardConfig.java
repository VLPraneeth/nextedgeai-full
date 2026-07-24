package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DatacardConfig {
    DateRange dateRange;
}
