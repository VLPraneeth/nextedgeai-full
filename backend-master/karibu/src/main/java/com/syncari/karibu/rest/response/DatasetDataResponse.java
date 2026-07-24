package com.syncari.karibu.rest.response;

import com.syncari.api.rest.controllers.data.insights.DatasetSampleColumnsDTO;
import com.syncari.api.rest.controllers.data.insights.DatasetSampleDTO;
import com.syncari.api.rest.controllers.data.insights.DatasetSampleDataDTO;
import com.syncari.core.model.UUIDAuditModel;
import com.syncari.core.model.insights.dataset.DatasetPageInfo;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString(callSuper=true)
public class DatasetDataResponse extends BaseKaribuResponse {

    private List<DatasetSampleColumnsDTO> columns;
    private List<List<DatasetSampleDataDTO>> data;
    private DatasetPageInfo pageInfo;

    @Override
    public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
        return null;
    }
}
