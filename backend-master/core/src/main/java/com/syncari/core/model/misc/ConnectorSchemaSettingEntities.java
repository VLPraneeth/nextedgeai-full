package com.syncari.core.model.misc;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class ConnectorSchemaSettingEntities {
    @NotNull(message = "External Source Entity is required")
	private String fromEntityId;

	@NotNull(message = "Syncari Entity is required")
	private String syncariEntityId;

	private List<String> toEntityIds = new ArrayList<>();

}
