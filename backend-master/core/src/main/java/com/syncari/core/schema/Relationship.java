package com.syncari.core.schema;

import com.syncari.core.model.util.Status;
import com.syncari.core.model.util.SyncDirection;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Relationship {
	String id;
	String sourceEntityId;
	String targetEntityId;
	SyncDirection direction;
	
	public Relationship() {}
}
