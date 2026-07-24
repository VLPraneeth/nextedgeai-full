package com.syncari.core.dfiv2;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Data
public class DFIResponse {

    private String entityId;
    private String entityName;
    private String evaluatedAt;
    private Map<String, Result> results;

    @Data
    public static class Result {
        private String categoryId;
        private String categoryName;
        private String ruleName;
        private List<Identifier> passed;
        private List<Identifier> failed;
    }

    @Data
    public static class Identifier {
        private String syncariRecordId;
        private String syncariAttributeId;
    }
}
