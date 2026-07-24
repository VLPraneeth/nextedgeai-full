package com.syncari.core.model;

import java.util.Date;

import javax.validation.constraints.NotNull;

import org.springframework.data.mongodb.core.mapping.Document;

import com.syncari.core.model.misc.Model;

import lombok.Data;

@Data
@Document
public class AuditLog extends Model {
	@NotNull(message = "who is required")
	private String who;
	@NotNull(message = "what is required")
	private String what;
	@NotNull(message = "where is required")
	private String where;
	@NotNull(message = "when is required")
	private Date when;
	private String server;
	private String status;

	public AuditLog(String who, String what, String where, Date when, String server, String status) {
		this.who = who;
		this.what = what;
		this.where = where;
		this.when = when;
		this.server = server;
		this.status = status;
	}

	public AuditLog() {
	}

}
