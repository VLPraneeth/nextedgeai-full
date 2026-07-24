package com.syncari.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorCatalog extends UUIDAuditModel {

	private ErrorCategory category;
	private String title;
	private String helpText;
	private ErrorPriority priority;
	private boolean active;

	public ErrorCatalog() {
	}


}
