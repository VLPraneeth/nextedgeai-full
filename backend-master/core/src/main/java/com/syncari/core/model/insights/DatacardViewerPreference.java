package com.syncari.core.model.insights;

import com.syncari.core.model.insights.dataset.Variable;
import lombok.Data;

import java.util.List;

@Data
public class DatacardViewerPreference {

    String dashboardId;
    String datacardId;
    List<Variable> datacardVariables;
}
