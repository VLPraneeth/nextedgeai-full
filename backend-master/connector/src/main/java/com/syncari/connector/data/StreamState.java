package com.syncari.connector.data;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@ToString
@Accessors(chain = true)
public class StreamState {
	private long lastModified;
	private String previousCursor;
	private boolean offsetOverflow;
	//Can add more states later
}

