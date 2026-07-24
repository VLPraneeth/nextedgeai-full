package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.model.insights.*;
import com.syncari.core.model.misc.Position;
import com.syncari.utils.KeyValue;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class VizConfigDTO {

    VizType vizType;
    StackingType stacking;
    String datasetId;
    String vizLabel;
    boolean vizLabelVisible;
    Position vizLabelPosition;

    Position legendPosition = Position.BOTTOM;
    String colorTheme;
    FieldProperty xAxis;
    @Deprecated(forRemoval = true)
    List<FieldProperty> yAxis;
    List<FieldProperty> series;
    List<FieldProperty> columns;
    @Deprecated(forRemoval = true)
    DateFilterDTO dateFilter;
    Map<String, VariableDTO> variablesMap = new HashMap<>();

    // Pie Viz config DTO
    FieldProperty value;
    FieldProperty category;
    FieldProperty subCategory;
    List<KeyValue> categoryValues;
    PieVizMinimumValue minimumValue;
    boolean legendVisible;
    boolean labelVisible;

    // Range attributes for viz type that need
    // range like gauge and metric
    List<VizRange> ranges;

    // Funnel config DTO
    FieldProperty measure;
    FieldProperty dataField;
    String sortBy;
    boolean ascending;
    List<String> stages;
    Position labelPosition = Position.LEFT;
    String displayAdditional;
}
