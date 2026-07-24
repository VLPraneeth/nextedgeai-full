package com.syncari.core.model;

import com.syncari.core.model.misc.ExternalValue;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
@ToString
public class ExternalRecordInfo {
    ExternalId id;
    Map<String, ExternalValue> values;

    @Data
    @Accessors(chain = true)
    public static class ExternalId {
        private String connectorName;
        private String connectorId;
        private String id;
        private String apiName;
        private String displayName;

        private String entityId;
    }
}
