package com.syncari.core.model;

import javax.validation.constraints.NotNull;

import com.syncari.core.model.misc.QuotaType;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Quota extends UUIDAuditModel {
	@NotNull(message = "Quota type is required")
	private QuotaType type;
	@NotNull(message = "Quota value is required")
	private String value;
	private String connectorId;
	
	public Quota() {}

}
