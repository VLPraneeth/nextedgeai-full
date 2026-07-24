package com.syncari.core.model.misc.test;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@Accessors(chain = true)
public class TestConfig {

    List<SimulationNodeInput> inputs = new ArrayList<>();
    List<SimulationNodeInput> expectedOutputs = new ArrayList<>();

    public Optional<SimulationNodeInput> findTestInputsForNode(String nodeId){
        return inputs.stream().filter(ip -> ip.getNodeId().equals(nodeId)).findFirst();
    }

    public Optional<SimulationNodeInput> findTestExpectedOutputsForNode(String nodeId){
        return expectedOutputs.stream().filter(ip -> ip.getNodeId().equals(nodeId)).findFirst();
    }
}
