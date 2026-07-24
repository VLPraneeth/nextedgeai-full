package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.model.pagination.PageCursor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DatasetReadDataDTO {

    private DatasetDTO dataset;
    private PageCursor pageCursor;
    private Long previousTotalCount;
}
