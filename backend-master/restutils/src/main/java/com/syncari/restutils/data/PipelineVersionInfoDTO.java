package com.syncari.restutils.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
@SuperBuilder(toBuilder = true)
public class PipelineVersionInfoDTO {
	private String id;
    private String targetId;
    private String pipelineType;
    private String displayName;
    private String apiName;
    @JsonInclude(Include.NON_NULL)
    private String changeType;
}
