package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
@Data
@Accessors(chain = true)
public class DatasourceDTO {
    List<DatasetFieldDTO> dataSourceFields = new ArrayList<>();
    private String dataSourceAlias;
}
