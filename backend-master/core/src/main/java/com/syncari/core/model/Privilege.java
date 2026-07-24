package com.syncari.core.model;

import javax.validation.constraints.NotNull;

import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document
public class Privilege extends UUIDAuditModel {
	@NotNull(message = "Privilege name is required")
	private String name;

	public Privilege(String name) {
		this.name = name;
	}

	public Privilege() {
	}

}
