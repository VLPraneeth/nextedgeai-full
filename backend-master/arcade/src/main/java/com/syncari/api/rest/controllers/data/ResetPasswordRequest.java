package com.syncari.api.rest.controllers.data;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResetPasswordRequest {
	private String currentPwd;
	private String newPwd;

	public ResetPasswordRequest() {
	}
}
