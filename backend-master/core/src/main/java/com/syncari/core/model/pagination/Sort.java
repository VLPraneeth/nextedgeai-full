package com.syncari.core.model.pagination;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Sort {
    String columnName;
    boolean ascending;
}
