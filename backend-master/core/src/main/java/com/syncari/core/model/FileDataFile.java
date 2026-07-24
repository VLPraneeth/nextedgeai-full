package com.syncari.core.model;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.data.annotation.Transient;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.Wither;

@Data
@Accessors(chain = true)
@Wither
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDataFile extends UUIDAuditModel {
	private String folderId;
	@NotNull(message = "Dataset name is required")
	private String name;
	private String idColumn;
	@Transient
	private List<String> tags;
	@Transient
	private String warnings;
	private String filePath;
	private Long rowsCount;
	private boolean withTrim = true;
}
