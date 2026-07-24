package com.syncari.core.model;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Deprecated
@Data
@AllArgsConstructor
public class Inbox extends UUIDAuditModel {
	private String userId;
	private List<Notification> notifications = new ArrayList<>();

	public Inbox(String userId) {
		this.userId = userId;
	}

	public Inbox() {}
}
