package com.syncari.api.rest.controllers.data.quickstart.v2;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class QSEntityPipelineDTO implements Serializable {
    private String id;
    private String displayName;
    private String apiName;
    private List<QSFieldPipelineDTO> fields;
}
