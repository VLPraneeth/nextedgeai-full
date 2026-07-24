package com.syncari.core.model;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.validation.constraints.NotNull;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.model.misc.DataImportStatus;
import com.syncari.core.model.misc.ReferenceData;
import com.syncari.core.model.misc.ReferenceDataSource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.Wither;

@Data
@AllArgsConstructor
@Accessors(chain = true)
@Wither
public class ReferenceDataMeta extends UUIDAuditModel implements Listable{
	@NotNull(message = "Dataset name is required")
	private String name;
	@NotNull(message = "Dataset source is required")
	private ReferenceDataSource source;
	@NotNull(message = "Dataset status is required")
	private DataImportStatus status;
	private String importDetails;
	private Map<String, Datatype> fields = new LinkedHashMap<>();
	private Long totalRecords;
	private String datasetCollectionName;
	private boolean isStandard = false;

	public ReferenceDataMeta() {}
	
	public ReferenceDataMeta(String name, ReferenceDataSource source) {
		this.name = name;
		this.source = source;
	}

	public String getFileName(){
		String[] splitLocation = source.getLocation().split("/");
		return splitLocation.length > 1 ? splitLocation[1] : splitLocation[0];
	}
}
