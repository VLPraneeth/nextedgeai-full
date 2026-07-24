package com.syncari.karibu.rest.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.ToString;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ToString(callSuper=true)
public class EntityDataResponse extends BaseKaribuResponse {

    private String id;
    private Map<String, Object> values = new HashMap<>();
    private Map<String, Object> idMapping = new LinkedHashMap<>();
    private String dataFitnessIndex;
    private boolean isSyncariDeleted;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String createdBy;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Date createdAt;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String updatedBy;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Date updatedAt;

    @Override
    public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
        return null;
    }

}
