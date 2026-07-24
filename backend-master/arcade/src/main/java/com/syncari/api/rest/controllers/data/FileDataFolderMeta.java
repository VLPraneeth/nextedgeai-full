package com.syncari.api.rest.controllers.data;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class FileDataFolderMeta {
	private String id;
	private String name;
	private String description;
	private List<FileDataFileMeta> files;
}
