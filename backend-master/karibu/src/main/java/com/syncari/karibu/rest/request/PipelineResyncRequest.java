package com.syncari.karibu.rest.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PipelineResyncRequest {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> entityIds;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String fromDate;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String toDate;
}
