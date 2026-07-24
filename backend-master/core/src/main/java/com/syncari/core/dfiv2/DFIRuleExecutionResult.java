package com.syncari.core.dfiv2;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DFIRuleExecutionResult {
    String syncariRecordId;
    String syncariAttributeId;
    String categoryId;
    String categoryName;
    String ruleId;
    String ruleName;
    Boolean result;
}

