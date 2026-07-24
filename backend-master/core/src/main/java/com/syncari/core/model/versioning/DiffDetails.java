package com.syncari.core.model.versioning;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@Accessors(chain = true)
public class DiffDetails {
	private String id;
	private String label;
	private boolean renderHtml;
	private String previousValue;
	private String value;
}
