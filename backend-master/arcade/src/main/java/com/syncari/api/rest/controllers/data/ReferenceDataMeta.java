package com.syncari.api.rest.controllers.data;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.syncari.core.model.misc.DataImportStatus;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReferenceDataMeta {
	private String id;
	private String name;
	private String type;
	private DataImportStatus status;
	private String importDetails;
	private String lastImported;
	private String location;
	private String accessKey;
	private String secretKey;
	private MultipartFile csvFile;
	private String totalRecords;
	private List<Dependency> usedInPipelines = new ArrayList<Dependency>();
	private boolean isStandard = false;

	public ReferenceDataMeta() {
	}
}
