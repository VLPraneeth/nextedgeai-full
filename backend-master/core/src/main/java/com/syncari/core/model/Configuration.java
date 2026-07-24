package com.syncari.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Configuration extends UUIDAuditModel {
	private long userInvitationExpiryInMillis;
	private long syncWindowSizeInMillis;

	public Configuration() {
	}

}
