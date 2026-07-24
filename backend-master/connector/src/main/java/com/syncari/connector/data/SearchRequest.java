package com.syncari.connector.data;

import java.util.ArrayList;
import java.util.List;

import com.syncari.connector.ConnectorInfo;

import com.syncari.utils.Storage;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SearchRequest {
	String query;
	private List<Object> params = new ArrayList<Object>();
	private ConnectorInfo connector;
	private Storage storage;
}
