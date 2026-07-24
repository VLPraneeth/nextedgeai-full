package com.syncari.api.rest.controllers.data;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SetPasswordRequest {
	private String password;

	public SetPasswordRequest() {
	}
}
