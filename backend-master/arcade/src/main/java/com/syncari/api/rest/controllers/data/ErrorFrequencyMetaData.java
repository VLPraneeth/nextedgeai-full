package com.syncari.api.rest.controllers.data;

import lombok.Builder;
import lombok.Data;

@Deprecated
@Data
@Builder
public class ErrorFrequencyMetaData {
	private String frequency;
	private String label;
}
