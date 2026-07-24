package com.syncari.core.dfiv2;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class DFIResultManager {

    public static final String ENTITY_ID_KEY = "entityId";
    public static final String EVALUATED_AT_KEY = "evaluatedAt";
    public static final String CATEGORY_ID_KEY = "categoryId";
    public static final String CATEGORY_NAME_KEY = "categoryName";
    public static final String ENTITY_NAME_KEY = "entityName";
    public static final String RULE_NAME_KEY = "ruleName";

    public static final String PASSED_KEY = "passed";
    public static final String FAILED_KEY = "failed";
    public static final String SYNCARI_RECORD_ID_KEY = "syncariRecordId";
    public static final String SYNCARI_ATTRIBUTE_ID_KEY = "syncariAttributeId";
    public static final String RESULTS_KEY = "results";

    private static final int BATCH_SIZE = 1000;
    private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    String entityId;
    String entityName;
    String evaluatedAt;
    int count;
    private Map<String, List<DFIRuleExecutionResult>> groupedResults;

    public static String getCurrTimeStamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT).withZone(ZoneId.of("UTC"));
        return formatter.format(Instant.now());
    }

    public static boolean isValidTimestamp(String ts) {
        DateTimeFormatter timestampFormatter =
                DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT).withZone(ZoneId.of("UTC"));
        try {
            timestampFormatter.parse(ts, Instant::from);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public DFIResultManager(String entityId, String entityName){
        this.entityId = entityId;
        this.entityName = entityName;
        this.groupedResults = new HashMap<>();
        this.count = 0;
    }

    public int size() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public void addResult(DFIRuleExecutionResult result) {
        count += 1;
        groupedResults.computeIfAbsent(result.getRuleId(), k -> new ArrayList<>()).add(result);
    }

    public void addResults(List<DFIRuleExecutionResult> results) {
        if (results != null) {
            for (DFIRuleExecutionResult result : results) {
                addResult(result);
            }
        }
        this.evaluatedAt = getCurrTimeStamp();
    }

    public Iterable<Map<String, Object>> transformResultBatchesIterable() {
        return new ResultBatchIterable(BATCH_SIZE);
    }

    private class ResultBatchIterable implements Iterable<Map<String, Object>> {
        private final int batchSize;

        public ResultBatchIterable(int batchSize) {
            this.batchSize = batchSize;
        }

        @Override
        public Iterator<Map<String, Object>> iterator() {
            return new ResultRecordBatchIterator(batchSize);
        }
    }

    private class ResultRecordBatchIterator implements Iterator<Map<String, Object>> {
        private final int batchSize;
        private final Iterator<Map.Entry<String, List<DFIRuleExecutionResult>>> ruleIterator;
        private Map.Entry<String, List<DFIRuleExecutionResult>> currentRuleEntry = null;
        private int currentRuleIndex = 0;

        public ResultRecordBatchIterator(int batchSize) {
            this.batchSize = batchSize;
            this.ruleIterator = groupedResults.entrySet().iterator();
            if (ruleIterator.hasNext()) {
                currentRuleEntry = ruleIterator.next();
            }
        }

        @Override
        public boolean hasNext() {
            return currentRuleEntry != null || ruleIterator.hasNext();
        }

        @Override
        public Map<String, Object> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put(ENTITY_ID_KEY, entityId);
            payload.put(ENTITY_NAME_KEY, entityName);
            payload.put(EVALUATED_AT_KEY, evaluatedAt != null ? evaluatedAt : getCurrTimeStamp());
            Map<String, Object> batchResults = new HashMap<>();
            int currentBatchCount = 0;

            while (currentBatchCount < batchSize && currentRuleEntry != null) {
                String ruleId = currentRuleEntry.getKey();
                List<DFIRuleExecutionResult> ruleResults = currentRuleEntry.getValue();

                Map<String, Object> rulePayload = (Map<String, Object>) batchResults.computeIfAbsent(ruleId, k -> {
                    Map<String, Object> newPayload = new HashMap<>();
                    String categoryId = ruleResults.isEmpty() ? null : ruleResults.get(0).getCategoryId();
                    String ruleNameForRule = ruleResults.isEmpty() ? null : ruleResults.get(0).getRuleName();
                    String categoryNameForRule = ruleResults.isEmpty() ? null : ruleResults.get(0).getCategoryName();
                    newPayload.put(CATEGORY_ID_KEY, categoryId);
                    newPayload.put(CATEGORY_NAME_KEY, categoryNameForRule);
                    newPayload.put(RULE_NAME_KEY, ruleNameForRule);
                    newPayload.put(PASSED_KEY, new ArrayList<Map<String, String>>());
                    newPayload.put(FAILED_KEY, new ArrayList<Map<String, String>>());
                    return newPayload;
                });

                List<Map<String, String>> passedList = (List<Map<String, String>>) rulePayload.get(PASSED_KEY);
                List<Map<String, String>> failedList = (List<Map<String, String>>) rulePayload.get(FAILED_KEY);

                if (currentRuleIndex < ruleResults.size()) {
                    DFIRuleExecutionResult result = ruleResults.get(currentRuleIndex);
                    Map<String, String> recordInfo = new HashMap<>();
                    recordInfo.put(SYNCARI_RECORD_ID_KEY, result.getSyncariRecordId());
                    recordInfo.put(SYNCARI_ATTRIBUTE_ID_KEY, result.getSyncariAttributeId());
                    if (result.getResult()) {
                        passedList.add(recordInfo);
                    } else {
                        failedList.add(recordInfo);
                    }
                    currentBatchCount++;
                    currentRuleIndex++;

                    if (currentRuleIndex == ruleResults.size()) {
                        currentRuleIndex = 0;
                        if (ruleIterator.hasNext()) {
                            currentRuleEntry = ruleIterator.next();
                        } else {
                            currentRuleEntry = null;
                        }
                    }
                } else {
                    currentRuleIndex = 0;
                    if (ruleIterator.hasNext()) {
                        currentRuleEntry = ruleIterator.next();
                    } else {
                        currentRuleEntry = null;
                    }
                }

                if (currentRuleEntry != null && currentRuleIndex == currentRuleEntry.getValue().size()) {
                    currentRuleIndex = 0;
                    if (ruleIterator.hasNext()) {
                        currentRuleEntry = ruleIterator.next();
                    } else {
                        currentRuleEntry = null;
                    }
                }
            }
            payload.put(RESULTS_KEY, batchResults);
            return payload;
        }
    }

}

