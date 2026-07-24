package com.syncari.connector.data;

import com.syncari.utils.KeyValue;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuthMetadata {
	private AuthType authType;
	List<AuthField> fields = new ArrayList<>();
	String label;
	String helpSummary;
	String brandImagePath;
	KeyValue options;

	public AuthMetadata(AuthType authType, List<AuthField> fields, String label, String helpSummary) {
		this.authType = authType;
		this.fields = fields;
		this.label = label;
		this.helpSummary = helpSummary;
	}

	public AuthMetadata addField(AuthField field) {
		fields.add(field);
		return this;

	}

	public AuthMetadata() {
	}
}
