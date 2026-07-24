package com.syncari.connector;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntityScore implements Serializable {
    private int recordScore;
    // Key : Field api name, Value : FieldScore
    private Map<String, FieldScore> fieldScores = new HashMap<String, FieldScore>();

    public EntityScore addFieldScore(String key, FieldScore value) {
        fieldScores.put(key, value);
        return this;
    }

    public void compute() {
        // record score = sum of all field score / number of fields
        int totalEntityScore = fieldScores.entrySet().stream().map(entry -> entry.getValue().getFieldScore()).reduce(0,
                Integer::sum);
        int totalFieldCount = fieldScores.size();
        if (totalEntityScore != 0 && totalFieldCount != 0) {
            recordScore = Math.abs(totalEntityScore / totalFieldCount);
        }
    }
}
