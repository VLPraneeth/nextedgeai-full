package com.syncari.core.model;

import com.syncari.core.model.misc.Taggable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@AllArgsConstructor
@EqualsAndHashCode
public class Tag extends UUIDAuditModel {
	String name;
	Object value;
	Taggable taggable;
	String taggedId;
}
