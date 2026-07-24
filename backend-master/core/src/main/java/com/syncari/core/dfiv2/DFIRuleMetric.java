package com.syncari.core.dfiv2;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors( chain = true)
public class DFIRuleMetric {
    String ruleId;
    int successCount;
    int failedCount;
}