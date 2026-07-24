package com.syncari.core.model.misc;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class ScoreContributingFactor {
    String category;
    String label;
    String description;
    String ruleId;
    String fieldName;
    String entityId;
    int averageScore;
    Map<String, Object> filterCondition = new HashMap<String, Object>();
}
