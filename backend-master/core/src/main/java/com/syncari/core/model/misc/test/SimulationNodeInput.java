package com.syncari.core.model.misc.test;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
public class SimulationNodeInput {

    String nodeId;
    String nodeName;
    Map<String, Object> fieldValues;

}
