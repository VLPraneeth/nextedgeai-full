package com.syncari.karibu.rest.response;

import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString(callSuper=true)
public class ReferenceDataResponse extends BaseKaribuResponse {
	private String id;
	private String name;
	private String status;
	private String importDetails;
	private String lastImported;
	private Long totalRecords;
	private List<String> usedInFieldPipelines;
	private List<String> headerColumns;


	@Override
	public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
		ReferenceDataResponse response = new ReferenceDataResponse();
		return response;
	}
}
