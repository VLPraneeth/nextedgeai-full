package com.syncari.connector.data;

import java.util.ArrayList;
import java.util.List;

import com.syncari.connector.ConnectorInfo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ConvertRequest {
	private ConnectorInfo connector;
	private boolean doNotCreateOpportunity;
	private List<ConvertData> data = new ArrayList<>();

	public ConvertRequest Builder(ConnectorInfo connector, EntitySchema entitySchema) {
		this.connector = connector;
		return this;
	}

}
