package com.syncari.core.model.versioning;

import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@Accessors(chain = true)
public class Diff {
	private DiffType op;
	private String nodeType;
	private String itemName;
	private String displayName;
	private List<DiffDetails> values;
}
