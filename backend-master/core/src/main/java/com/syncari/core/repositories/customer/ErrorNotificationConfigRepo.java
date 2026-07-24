package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;

import com.syncari.core.model.ErrorNotificationConfig;
import com.syncari.core.model.ErrorNotificationFrequency;
import com.syncari.core.model.misc.ErrorNotificationConfigStatus;
import com.syncari.core.repositories.SyncariRepo;

public interface ErrorNotificationConfigRepo extends SyncariRepo<ErrorNotificationConfig> {
	public Optional<ErrorNotificationConfig> findByName(String name);
	public List<ErrorNotificationConfig> findByStatus(ErrorNotificationConfigStatus status);
	public List<ErrorNotificationConfig> findByStatusAndCadence(ErrorNotificationConfigStatus status, ErrorNotificationFrequency cadence);
	public List<ErrorNotificationConfig> findByStatusAndCadenceIn(ErrorNotificationConfigStatus status, List<ErrorNotificationFrequency> cadences);
}
