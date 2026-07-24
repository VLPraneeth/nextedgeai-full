package com.syncari.core.repositories.syncari;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.syncari.core.model.UserInvitation;
import com.syncari.core.repositories.SyncariRepo;

@Repository
public interface UserInvitationRepo extends SyncariRepo<UserInvitation> {
	Optional<UserInvitation> findByUserId(String userId);
	
	Optional<UserInvitation> findByInvitationId(String invitationId);

}
