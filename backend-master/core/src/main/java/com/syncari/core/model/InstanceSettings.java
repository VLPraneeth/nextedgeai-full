package com.syncari.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InstanceSettings extends UUIDAuditModel {

    String feature;
    String status;

    public InstanceSettings() {
    }

}
