package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DatasetConfigDTO {

    private List<ProjectionDTO> calculatedFields;
    private List<SelectedFieldDTO> selectedFields;
    private List<DatasetFromDTO> fromDataset;
    private Map<String, Object> filter;
    private List<JoinDTO> joins;
    private List<GroupByDTO> groupBy;
    private List<SortDTO> sort;
    private Integer limit;
    private boolean isGroup;
    private ConfigMode configMode = ConfigMode.BASIC;

    public enum ConfigMode{
        BASIC,
        SQL
    }
}
