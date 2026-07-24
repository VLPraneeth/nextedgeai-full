package com.syncari.core.model.misc;

import java.util.Date;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
public abstract class Audit extends Model {
	String createdBy;
	String updatedBy;
	Date createdAt;
	Date updatedAt;
	public Audit(){

	}
}
