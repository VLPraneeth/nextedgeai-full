package com.syncari.api.rest.controllers.data.test;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class PipelineTestData {

    private List<PipelineTestNodeData> input = new ArrayList<>();
    private List<PipelineTestNodeData> expectedResult = new ArrayList<>();
    private List<PipelineTestNodeData> actualResult = new ArrayList<>();
}
