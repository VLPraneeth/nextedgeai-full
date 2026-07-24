package com.syncari.core.model.misc;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@AllArgsConstructor
public class EntitySyncStatusSummary {

	List<EntitySyncStatus> sources = new ArrayList<>();
	List<EntitySyncStatus> sinks = new ArrayList<>();

	public EntitySyncStatusSummary(){}
}
