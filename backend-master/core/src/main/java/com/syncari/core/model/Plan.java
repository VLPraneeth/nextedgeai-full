package com.syncari.core.model;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class Plan extends UUIDAuditModel {
	@NotNull(message = "Plan name is required")
	private String name;
	private List<String> featureIds = new ArrayList<>();
	private List<Quota> quota = new ArrayList<>();

	public Plan() {
	}

	public Plan(String name) {
		this.name = name;
	}
}
