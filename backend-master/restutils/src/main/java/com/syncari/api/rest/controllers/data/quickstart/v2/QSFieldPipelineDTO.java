package com.syncari.api.rest.controllers.data.quickstart.v2;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class QSFieldPipelineDTO implements Serializable {
    private String id;
    private String displayName;
    private String apiName;
    private String datatype;
}
