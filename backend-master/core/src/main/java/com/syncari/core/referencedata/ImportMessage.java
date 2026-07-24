package com.syncari.core.referencedata;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImportMessage {
	String syncariId;
	String refMetaId;
	
	public ImportMessage() {}
}
