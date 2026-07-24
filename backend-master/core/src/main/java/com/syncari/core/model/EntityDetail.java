package com.syncari.core.model;

import java.util.List;

import com.syncari.connector.EntityData;
import com.syncari.core.model.misc.Watermark;

import lombok.Data;

@Data
public class EntityDetail {
	private Watermark watermark;
	private List<EntityData> data;

	public EntityDetail( Watermark watermark, List<EntityData> data) {
		this.watermark = watermark;
		this.data = data;
	}
}
