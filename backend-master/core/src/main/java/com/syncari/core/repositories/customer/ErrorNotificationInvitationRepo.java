package com.syncari.core.repositories.customer;

import java.util.Optional;

import com.syncari.core.model.ErrorNotificationInvitation;
import com.syncari.core.repositories.SyncariRepo;

public interface ErrorNotificationInvitationRepo extends SyncariRepo<ErrorNotificationInvitation> {
	Optional<ErrorNotificationInvitation> findByInvitationId(String invitationId);
	void deleteByConfigIdAndEmail(String configId, String email);
	Optional<ErrorNotificationInvitation> findByConfigIdAndEmail(String configId, String email);
}
