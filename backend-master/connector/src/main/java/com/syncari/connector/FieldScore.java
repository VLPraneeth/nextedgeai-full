package com.syncari.connector;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldScore implements Serializable {
    int fieldScore;
    // Key : Rule api name, Value : Score
    Map<String, Integer> byRuleScores = new HashMap<String, Integer>();
    
    public FieldScore addByRule(String key, Integer score) {
        score = score == null ? 0 : score;
        byRuleScores.put(key, score);
        return this;
    }
    
    public int compute() {
        // field score = sum of all rule score / number of rules
        int totalFieldScore = byRuleScores.entrySet().stream().map(entry -> entry.getValue()).reduce(0, Integer::sum);
        int totalFieldCount = byRuleScores.size();
        if (totalFieldScore != 0 && totalFieldCount != 0) {
            fieldScore = Math.abs(totalFieldScore / totalFieldCount);
        }
        return fieldScore;
    }
}
