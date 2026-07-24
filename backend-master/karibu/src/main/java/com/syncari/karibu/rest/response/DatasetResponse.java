package com.syncari.karibu.rest.response;

import com.syncari.api.rest.controllers.data.insights.DatasetConfigDTO;
import com.syncari.api.rest.controllers.data.insights.VariableDTO;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Data
@ToString(callSuper=true)
@Accessors(chain = true)
public class DatasetResponse extends BaseKaribuResponse{

    String displayName;
    String description;
    boolean hidden;
    boolean seeded;
    DraftStatus draftStatus;
    Set<String> tags = new LinkedHashSet<>();
    DatasetConfigDTO datasetConfig;
    Map<String, VariableDTO> variablesMap;

    @Override
    public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
        return null;
    }
}
