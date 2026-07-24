package com.syncari.core.model;

import javax.validation.constraints.NotNull;

import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Document;

import com.syncari.core.model.misc.FeatureStage;
import com.syncari.core.model.misc.FeatureStatus;

import lombok.Data;

@Data
@Document
@Accessors(chain = true)
public class Feature extends UUIDAuditModel {
	@NotNull(message = "Feature name is required")
	public String name;
	public FeatureStage stage;
	public FeatureStatus status;
	public String params;
	public boolean hidden;

	public Feature(String name) {
		this.name = name;
		this.status = FeatureStatus.inactive;
		this.stage = FeatureStage.internal;
	}

	public Feature(String name, FeatureStage stage, FeatureStatus status) {
		this.name = name;
		this.status = status;
		this.stage = stage;
	}

	public Feature() {
	}
	
	public boolean isActive() {
		return status == FeatureStatus.active;
	}

}
