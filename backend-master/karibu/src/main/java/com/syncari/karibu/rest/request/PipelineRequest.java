package com.syncari.karibu.rest.request;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class PipelineRequest {
    private String id;
    private String entityId;
    private String fieldId;
    private String parentId;
    private String name;
    private String scope;
    private List<Map<String, Object>> nodes;
    private List<Map<String, Object>> edges;

    public PipelineRequest() {
    }
}
