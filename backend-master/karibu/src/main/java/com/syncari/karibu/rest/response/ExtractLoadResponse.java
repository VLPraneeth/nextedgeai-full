package com.syncari.karibu.rest.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtractLoadResponse {
    private String status;
    private String message;
    private String pipelineId;
    private String pipelineName;
    private String sourceEntityId;
    private String sourceEntityName;
    private String syncariEntityId;
    private String syncariEntityName;
    private String destinationEntityId;
    private String destinationEntityName;
    private Integer fieldsMapped;
    private Integer fieldsCreated;
    private List<MappedField> mappedFields;
    private boolean published;
    private boolean resyncStarted;
    private String resyncId;
    private boolean autoSchemaSyncEnabled;
}
