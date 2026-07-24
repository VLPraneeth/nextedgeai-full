package com.syncari.api.rest.controllers.data.insights;

import java.util.*;
import com.syncari.core.model.pagination.PageCursor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DashboardDatacardReadDataDTO {
    private PageCursor pageCursor;
    private Long previousTotalCount;
    private Map<String, VariableValueDTO> configuration;
}
