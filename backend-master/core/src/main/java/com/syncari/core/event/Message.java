package com.syncari.core.event;

import com.syncari.core.model.Event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class Message {
	String syncariId;
	Event event;
	
	public Message() {}
}
