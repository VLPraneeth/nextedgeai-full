package com.syncari.karibu.rest.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.ToString;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@ToString(callSuper=true)
public class FieldUpdateResponse extends BaseKaribuResponse {

    private List<String> successfulUpdatedFieldIds;
    private List<Map<String, String>> failedUpdatedFieldIds;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String id;
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

    public FieldUpdateResponse populateFieldUpdateResponse (List<String> successfulUpdatedFieldIds, List<Map<String, String>> failedUpdatedFieldIds) {
        FieldUpdateResponse response = new FieldUpdateResponse();

        response.setSuccessfulUpdatedFieldIds(successfulUpdatedFieldIds);
        response.setFailedUpdatedFieldIds(failedUpdatedFieldIds);

        return response;
    }

}
