package com.syncari.core.model;

import javax.validation.constraints.NotNull;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Accessors(chain=true)
public class UnresolvedReference extends UUIDAuditModel {
    @NotNull(message = "Syncari entity def id is required")
    public String syncariEntityDefId;
    @NotNull(message = "Syncari record id is required")
    public String syncariRecordId;
    @NotNull(message = "Syncari attribute def api name is required")
    public String syncariAttributeName;
    @NotNull(message = "Connector id is required")
    public String connectorId;
    @NotNull(message = "External reference entity def id is required")
    public String externalRefEntityName;
    @NotNull(message = "External reference record id is required")
    public String externalRefRecordId;
    public String resolvedSyncariValue;
    public String referredSyncariEntity;

    private static final int MAX_RETRIES = 4;

    @Field
    public Integer retries = 0;
    @Field
    public Boolean unresolvable = false;

    public UnresolvedReference() {
    }

    public UnresolvedReference(String syncariEntityDefId, String syncariRecordId, String syncariAttributeName,
                               String connectorId, String externalRefEntityName, String externalRefRecordId, String referredSyncariEntity) {
        super();
        this.syncariEntityDefId = syncariEntityDefId;
        this.syncariRecordId = syncariRecordId;
        this.syncariAttributeName = syncariAttributeName;
        this.connectorId = connectorId;
        this.externalRefEntityName = externalRefEntityName;
        this.externalRefRecordId = externalRefRecordId;
        this.referredSyncariEntity = referredSyncariEntity;
    }

    public UnresolvedReference(String connectorId, String externalRefEntityName, String externalRefRecordId) {
        super();
        this.connectorId = connectorId;
        this.externalRefEntityName = externalRefEntityName;
        this.externalRefRecordId = externalRefRecordId;
    }

    public void incrementRetries() {
        if(++retries == MAX_RETRIES) {
            unresolvable = true;
        }
    }
}
