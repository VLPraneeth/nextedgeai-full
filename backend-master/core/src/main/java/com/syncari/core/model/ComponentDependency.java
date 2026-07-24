package com.syncari.core.model;

import com.syncari.core.model.misc.ComponentType;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ComponentDependency extends UUIDAuditModel {
	private String fromId;
	private ComponentType fromComponent;
	private String toId;
	private ComponentType toComponent;
	
	public ComponentDependency() {}

	public ComponentDependency clone(){
		return new ComponentDependency(this.fromId, this.fromComponent, this.toId, this.toComponent);
	}

}
