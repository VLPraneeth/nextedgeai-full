package com.syncari.core.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserInvitation extends UUIDAuditModel {
	
    private String userId;
	private String invitationId;
	// 30 days
    private static long INVITATION_EXPIRY = 30L * 24 * 60 * 60 * 1000;
	
	public UserInvitation() {}
	
	public boolean hasExpired() {
	    return Instant.now().toEpochMilli() > this.getCreatedAt().getTime() + INVITATION_EXPIRY;
	}
    
}
