package com.syncari.api.rest.controllers.data.insights;


import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class GroupByDTO {
    DatasetFieldDTO datasetField;
    // this could be a variable (in format {{variablename}}) or dategroupbyoption
    String dateGroupByOption;
}
