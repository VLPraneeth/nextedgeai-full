package com.syncari.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InstanceConfiguration extends UUIDAuditModel {

    public static final String DEBUG_MODE = "debugMode";
    public static final String DEBUG_MODE_EXPIRY_SECS = "debugModeExpirySecs";
    String key;
    Object value;

    public InstanceConfiguration() {
    }

    public <T> T cast(){
        return (T) value;
    }

}
