package com.syncari.core.model.misc;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReferenceDataSource {
	ReferenceDataSourceType type;
	String location;
	private String accessKey;
	private String secretKey;

	public ReferenceDataSource() {
	}

	public ReferenceDataSource(ReferenceDataSourceType type, String location) {
		this.type = type;
		this.location = location;
	}

}
