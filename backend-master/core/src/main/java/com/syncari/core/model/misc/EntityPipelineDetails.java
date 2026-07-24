package com.syncari.core.model.misc;

import java.time.Instant;
import java.util.List;

import com.syncari.core.model.PipelineSettings;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class EntityPipelineDetails {
	private String syncariEntityId;
	private List<EntityPipelineDetailsStatus> sources;
	private List<EntityPipelineDetailsStatus> sinks;
	private Long fieldsMapped;
	private Boolean mergeConfig;
	private Instant lastModifiedOn;
	private EntityPipelineDetailsUser lastModifiedBy;
	private Instant lastPublishedOn;
	private Long numberOfVersions;
	private PipelineSettings settings;
}
