package com.syncari.karibu.rest.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.ToString;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@ToString(callSuper=true)
public class QuickStartResponse extends BaseKaribuResponse {

    private String id;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String type;
    private String displayName;
    private String description;
    private String postInstallationInstruction;
    private String status;
    private String iconPath;
    private String publishToQuickStartLibrary;
    private boolean shareWithOrg;
    private List<String> tags;
    private List<String> requiredSynapses;
    private List<String> shareWithInstances;
    private List<Map<String, Object>> pipelines;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String createdBy;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Date createdAt;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String updatedBy;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Date updatedAt;


    @Override
    public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {return null;}

}
