package com.syncari.core.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class EnrichmentCache extends UUIDAuditModel {
    String serviceId;
    String entityName;
    String enrichKey;
    String enrichValue;

}
