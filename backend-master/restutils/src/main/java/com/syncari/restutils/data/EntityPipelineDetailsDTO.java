package com.syncari.restutils.data;

import java.time.Instant;
import java.util.List;

import com.syncari.core.model.PipelineSettings;
import com.syncari.core.model.misc.EntityPipelineDetails;
import com.syncari.core.model.misc.EntityPipelineDetailsStatus;
import com.syncari.core.model.misc.EntityPipelineDetailsUser;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class EntityPipelineDetailsDTO {
	private String syncariEntityId;
	private List<EntityPipelineDetailsStatus> sources;
	private List<EntityPipelineDetailsStatus> sinks;
	private Long fieldsMapped;
	private Boolean mergeConfig;
	private Instant lastModifiedOn;
	private EntityPipelineDetailsUser lastModifiedBy;
	private Instant lastPublishedOn;
	private Long numberOfVersions;
	private ResyncDetailDTO resyncDetail;
	private PipelineSettings settings;
	
	public EntityPipelineDetailsDTO(EntityPipelineDetails details, ResyncDetailDTO resyncDetail) {
		this.syncariEntityId = details.getSyncariEntityId();
		this.sources = details.getSources();
		this.sinks = details.getSinks();
		this.fieldsMapped = details.getFieldsMapped();
		this.mergeConfig = details.getMergeConfig();
		this.lastModifiedOn = details.getLastModifiedOn();
		this.lastModifiedBy = details.getLastModifiedBy();
		this.lastPublishedOn = details.getLastPublishedOn();
		this.numberOfVersions = details.getNumberOfVersions();
		this.resyncDetail = resyncDetail;
		this.settings = details.getSettings();
	}
}
