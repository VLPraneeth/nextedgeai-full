package com.syncari.api.rest.controllers.data.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ErrorCatalogDTO {

	private String id;
	private String category;
	private String title;
	private String helpText;

	public ErrorCatalogDTO() {
	}


}
