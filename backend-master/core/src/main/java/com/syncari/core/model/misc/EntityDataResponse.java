package com.syncari.core.model.misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.syncari.connector.EntityData;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class EntityDataResponse {
    EntityData record;
    ValidationErrors errors = new ValidationErrors();

    @JsonIgnore
    public boolean isSuccess() {
        return errors.isEmpty();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        String code;
        String message;
    }

    @Data
    @Accessors(chain = true)
    public static class ValidationErrors {
        Map<String, List<ErrorDetail>> fields = new HashMap<>();
        List<ErrorDetail> record = new ArrayList<>();

        @JsonIgnore
        public boolean isEmpty() {
            return fields.isEmpty() && record.isEmpty();
        }

        public void addFieldError(String fieldName, String code, String message) {
            fields.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(new ErrorDetail(code, message));
        }

        public void addRecordError(String code, String message) {
            record.add(new ErrorDetail(code, message));
        }
    }
}
