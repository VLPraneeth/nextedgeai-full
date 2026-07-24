package com.syncari.core.model.misc.sharable;

import com.syncari.core.model.InputPort;
import com.syncari.core.model.OutputPort;
import lombok.Data;

@Data
public class SharableEdge {
    String id;
    private OutputPort output;
    private String sourceStageId;
    private String graphId;
    private String destinationStageId;
    private InputPort input;
}
