package com.syncari.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebugConfig {
    private boolean enabled;
    private int expirySeconds;
    private Instant updatedAt;
    private long remainingSeconds;
}
