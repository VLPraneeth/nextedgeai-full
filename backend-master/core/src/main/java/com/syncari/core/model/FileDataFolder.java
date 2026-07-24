package com.syncari.core.model;

import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.Wither;

@Data
@AllArgsConstructor
@Accessors(chain = true)
@Wither
public class FileDataFolder extends UUIDAuditModel {
	@NotNull(message = "Dataset name is required")
	private String name;
	private String folderName;
	private String description;
}
