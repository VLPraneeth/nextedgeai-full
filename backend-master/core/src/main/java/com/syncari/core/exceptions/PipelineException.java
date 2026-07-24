package com.syncari.core.exceptions;

import com.syncari.core.model.util.Scope;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PipelineException extends RuntimeException {
    private String nodeId;
    private String graphId;
    private Scope scope;
    private String externalEntityDefinitionId;

    public PipelineException(Throwable cause) {
        super(cause);
    }
}
