package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.model.pagination.PageInfo;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class InsightsShareDetailsResponse {

    private List<InsightsShareDetailsDTO> shareDetailsRecords;
    private PageInfo pageInfo;
}
