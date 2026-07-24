package com.syncari.api.rest.controllers.data;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TestPipelineDTOTest {

    @Test
    public void recordIdsFlattened(){
        TestPipelineDTO testPipelineDTO = new TestPipelineDTO();
        testPipelineDTO.setRecordIds(Map.of("c1", List.of("1,2,3")));
        assertEquals(Map.of("c1",List.of("1","2","3")),testPipelineDTO.getRecordIds());
        testPipelineDTO.setRecordIds(Map.of("c1", List.of("1","2")));
        assertEquals(Map.of("c1",List.of("1","2")),testPipelineDTO.getRecordIds());
    }

}