package com.syncari.core.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;


@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorNotificationInvitation extends UUIDAuditModel {
	// 30 days
	private static long INVITATION_EXPIRY = 30L * 24 * 60 * 60 * 1000;
	
	private String invitationId;
	private String email;
	private String configId;
	
	public boolean hasExpired() {
	    return Instant.now().toEpochMilli() > this.getCreatedAt().getTime() + INVITATION_EXPIRY;
	}
}

