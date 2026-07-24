package com.syncari.core.notification;

import java.util.List;

import com.syncari.core.model.NotificationChannel;

public class NotificationChannelSeed {
	public static List<NotificationChannel> allChannels() {
		return List.of(email());
	}
	
	private static NotificationChannel email() {
		return NotificationChannel.builder().type("email").label("Email").configurationType("input").build();
	}
}
