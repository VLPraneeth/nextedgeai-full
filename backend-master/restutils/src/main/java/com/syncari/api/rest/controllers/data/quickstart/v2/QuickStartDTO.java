package com.syncari.api.rest.controllers.data.quickstart.v2;

import com.syncari.core.model.Tag;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class QuickStartDTO implements Serializable {
    private String id;
    private String displayName;
    private String description;
    private List<String> tags;
    private String postInstallationInstruction;
    private QSPipelineConfigDTO pipelines;
    private String status;
    private String iconPath;
    private List<String> requiredSynapses;
    private List<String> shareWithInstances;
    private String publishToQuickStartLibrary;
    private boolean shareWithOrg;
    private String installStatus;
}
