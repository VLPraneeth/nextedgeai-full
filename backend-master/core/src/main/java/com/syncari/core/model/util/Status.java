package com.syncari.core.model.util;

public enum Status {
	NEW,
	ACTIVE,
	INACTIVE, //To be Deprecated.
	DELETED,
	DELETING, // To be removed. Now hard deleting needs to be used
	HARD_DELETING,
	PENDING,
	PROCESSING,
	COMPLETED,
	ERROR,
	CANCELLED
}
