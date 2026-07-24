
package com.syncari.api.rest.controllers.data.quickstart.v2;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class QSPipelineConfigDTO implements Serializable {
    private boolean fieldsOnly;
    private List<QSEntityPipelineDTO> entities;
}
