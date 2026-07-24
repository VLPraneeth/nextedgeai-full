package com.syncari.core.model.misc;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiConfig {
	long dailyQuota;

	public ApiConfig() {
	}

	@Override
	public ApiConfig clone() {
		return new ApiConfig(dailyQuota);
	}
}
