package com.syncari.core.model.insights.provider.ts;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class TSColumn {

    private String name;
    private String type;
    private boolean canImport=true;
    private boolean selected=true;
    private boolean isLinkedActive=true;
    private boolean isImported=false;
    private String tableName;
    private String schemaName;
    private String dbName;

    @JsonProperty("isLinkedActive")
    public boolean isLinkedActive() {
        return isLinkedActive;
    }

    public void setLinkedActive(boolean linkedActive) {
        isLinkedActive = linkedActive;
    }

    @JsonProperty("isImported")
    public boolean isImported() {
        return isImported;
    }

    public void setImported(boolean imported) {
        isImported = imported;
    }


}
