package com.syncari.api.rest.controllers.data;

import com.syncari.core.model.misc.DataFixQueryType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataFixQueryRequest {

    @NotNull(message = "Query text is required")
    private String queryText;

    @NotNull(message = "Query type is required")
    private DataFixQueryType queryType;

    private String targetCollection; // Optional - will be auto-extracted from query if not provided

    private String targetDatabase;

    @NotNull(message = "Justification is required")
    private String justification;

    @NotNull(message = "Approver is required")
    private String approverId;
}
