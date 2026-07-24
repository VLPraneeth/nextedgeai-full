package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.model.insights.dataset.DatasetPageInfo;
import com.syncari.utils.KeyValue;
import lombok.Data;

import java.util.List;

@Data
public class ChartVizData extends VizData {
    // Series is a discriminator if there are multiple charts of same type, for example multiple bars or multiple lines
    List<Series> series;
    List<KeyValue> rows;
    DatasetPageInfo pageInfo;
}
