package com.syncari.core.model.insights.dataset;

import com.syncari.core.model.insights.QField;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Sort {
    QField columnName;
    boolean ascending;

}
