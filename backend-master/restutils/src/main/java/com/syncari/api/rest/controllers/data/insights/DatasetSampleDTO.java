package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.model.insights.dataset.DatasetPageInfo;
import com.syncari.core.model.pagination.PageInfo;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class DatasetSampleDTO implements Serializable {

    private List<DatasetSampleColumnsDTO> columns;
    private List<List<DatasetSampleDataDTO>> data;
    private DatasetPageInfo pageInfo;
}
