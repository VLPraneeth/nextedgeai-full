package com.syncari.api.rest.controllers.data;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class FileDataFileMeta {
	private String id;
	private String folderId;
	private String name;
	private String idColumn;
	private List<String> tags;
	private String filePath;
	private Date uploadedAt;
	private String uploadedBy;
	private String fileType;
	private Long rowsCount;
	private String message;
}
