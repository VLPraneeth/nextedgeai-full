package com.syncari.core.model;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
@ToString
public class ExternalDeleteInfo {
    List<ExternalId> disconnectedSources = new ArrayList<>();
    boolean isSyncariDeleted;
    ExternalId deletedId;

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
