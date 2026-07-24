package com.syncari.core.pipeline;

import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class NodeInfoContext {

    MappingNode currentNode;
    MappingGraph pipeline;
}
