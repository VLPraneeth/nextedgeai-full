package com.syncari.core.model.misc;

import lombok.Data;
import lombok.experimental.Accessors;
import java.util.*;

@Data
@Accessors(chain = true)
public class DFIEvaluationResult {
    private boolean shouldProceed;              // true = allow save, false = reject
    private boolean allRulesPassed;             // true if all rules passed
    private List<FailedRule> failedRules;       // ALL rules that failed (REPORT, REJECT, etc.)
    private List<String> rejectionReasons;      // ALL reasons why record was rejected (if multiple)

    public DFIEvaluationResult() {
        this.shouldProceed = true;
        this.allRulesPassed = true;
        this.failedRules = new ArrayList<>();
        this.rejectionReasons = new ArrayList<>();
    }


    @Data
    @Accessors(chain = true)
    public static class FailedRule {
        private String ruleName;         // Name of failed rule
        private String ruleId;           // ID of failed rule
        private String scope;            // "record" or "attribute" (DFIConstants.RECORD_SCOPE/ATTRIBUTE_SCOPE)
        private String policy;           // "REPORT", "REJECT", "MODIFY" (future)
        private String fieldName;        // Field API name (null for record-level rules)
        private String fieldDisplayName; // Field display name for user-friendly messages
        private boolean isRequired;      // Whether field is required (nillable=false)
        private boolean causedRejection; // true if this rule caused record rejection
        private String failureReason;    // Why this specific rule failed/what action was taken
    }


    public void addRejectionReason(String reason) {
        this.rejectionReasons.add(reason);
        this.shouldProceed = false; // Any rejection reason means don't proceed
    }


    public String getCombinedRejectionReason() {
        if(rejectionReasons.isEmpty()) {
            return null;
        }
        if(rejectionReasons.size() == 1) {
            return rejectionReasons.get(0);
        }
        return rejectionReasons.size() + " DFI rules caused rejection: " + String.join("; ", rejectionReasons);
    }
}
