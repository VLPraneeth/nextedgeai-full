package com.syncari.api.rest.controllers.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataFixReadQueryRequest {

    @NotNull(message = "Query text is required")
    private String queryText;

    private String targetDatabase;
}
