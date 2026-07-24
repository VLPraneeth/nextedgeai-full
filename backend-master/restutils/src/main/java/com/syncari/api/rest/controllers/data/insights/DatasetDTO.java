package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.insights.dataset.Dataset;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Data
@Accessors(chain = true)
public class DatasetDTO {

    String id;
    String name;
    String displayName;
    String description;
    boolean hidden;
    boolean seeded;
    DraftStatus draftStatus;
    Set<String> tags = new LinkedHashSet<>();
    DatasetConfigDTO datasetConfig;
    Map<String, VariableDTO> variablesMap;
    String createdBy;
    String updatedBy;
    Dataset.DatasetType datasetType;
    ZonedDateTime createdAt;
    ZonedDateTime updatedAt;
    String sql;

    public boolean isSQLMode(){
        return ((null != datasetConfig) && (datasetConfig.getConfigMode().equals(DatasetConfigDTO.ConfigMode.SQL)));
    }
}
