package com.syncari.core.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
@Accessors(chain = true)
public class UnresolvedRecord extends UUIDAuditModel {

    public enum UnResolvedRecordStatus {
        UNRESOLVED, PERMANENTLY_UNRESOLVED;
    }

    public static final long MAX_UNRESOLVED_WARN_TIME = Duration.ofMinutes(60 * 3).toMillis();
    public static final long MAX_UNRESOLVED_ERROR_TIME = Duration.ofMinutes(60 * 48).toMillis();
    private String syncariId;
    private String syncariEntityDefinitionId;

    private String connectorId;
    private String externalEntityDefinitionId;
    private UnResolvedRecordStatus status= UnResolvedRecordStatus.UNRESOLVED;
    private Set<String> unresolvedFieldIds = new HashSet<>();


    public boolean exceedsWarningThreshold() {
        return Instant.now().toEpochMilli() - this.getCreatedAt().toInstant().toEpochMilli() > MAX_UNRESOLVED_WARN_TIME;
    }
    public long elapsedTimeInMillis() {
        return Instant.now().toEpochMilli() - this.getCreatedAt().toInstant().toEpochMilli();
    }

    public boolean exceedsErrorThreshold() {
        return Instant.now().toEpochMilli() - this.getCreatedAt().toInstant().toEpochMilli() > MAX_UNRESOLVED_ERROR_TIME;
    }

    public UnresolvedRecord addUnresolvedField(String fieldId) {
        unresolvedFieldIds.add(fieldId);
        return this;
    }

    public boolean hasUnresolvedFields() {
        return !unresolvedFieldIds.isEmpty();
    }

}
