package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;

@Data
public class SortDTO {
    DatasetFieldDTO field;
    boolean ascending;
}
