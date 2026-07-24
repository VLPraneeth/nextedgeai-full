package com.syncari.karibu.rest.response;

import java.util.Map;

import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.model.UUIDAuditModel;

import lombok.Data;

@Data
public class ReferenceDataItemResponse extends BaseKaribuResponse {
	private Map values;

	public ReferenceDataItemResponse() {
	}
	
	public ReferenceDataItemResponse(String id, Map values) {
        this.setId(id);
        this.setValues(values);
	}

	@Override
	public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
		ReferenceDataMeta ref = (ReferenceDataMeta) object;
        SynapseResponse response = new SynapseResponse();

        this.setId(ref.getId());
        this.setName(ref.getName());
        this.setCreatedBy(ref.getCreatedBy());
        this.setCreatedAt(ref.getCreatedAt());
        this.setUpdatedBy(ref.getUpdatedBy());
        this.setUpdatedAt(ref.getUpdatedAt());

        return response;
	}
}
