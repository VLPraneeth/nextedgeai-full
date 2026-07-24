package com.syncari.core.schema;

import lombok.Data;

@Data
public class EntityLocation {
	int x;
	int y;
	
	public EntityLocation(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public EntityLocation() {}
}
