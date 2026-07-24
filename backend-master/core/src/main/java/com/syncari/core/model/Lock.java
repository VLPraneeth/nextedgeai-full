package com.syncari.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@Data
@RequiredArgsConstructor
public class Lock extends UUIDAuditModel{
    @NonNull  private String lockKey;
    @NonNull private String ownerId;
    private Map<String, Object> payload;
    private LockStatus status = LockStatus.UNLOCKED;
    public enum LockStatus {
        LOCKED, UNLOCKED
    }
    public Lock(){}
}