package com.syncari.core.model;

import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;

import com.syncari.core.model.misc.Audit;

import lombok.Data;

@Data
@SuperBuilder(toBuilder = true)
public abstract class UUIDAuditModel extends Audit {
	@Id
	String id;
	public UUIDAuditModel(){
	}
}
