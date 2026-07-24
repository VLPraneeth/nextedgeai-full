package com.syncari.api.rest.controllers.data;

import java.util.List;

import com.syncari.core.model.ErrorCatalog;
import com.syncari.core.model.NotificationChannel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorCatalogMetaData {
	private List<ErrorCatalog> notificationItems;
	private List<NotificationChannel> channels;
	private List<ErrorFrequencyMetaData> frequencies;
}
