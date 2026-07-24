package com.syncari.core.model;

import javax.validation.constraints.NotNull;

import org.springframework.data.mongodb.core.mapping.Document;

import com.syncari.core.model.misc.EventCategory;

import lombok.Data;

@Data
@Document
public class EventType extends UUIDAuditModel {
	@NotNull(message = "Event type name is required")
	String name;
	String label;
	EventCategory category;
	
	public EventType() {}
	
	public EventType(String name, String label) {
		this.name = name;
		this.label = label;
	}
}
