package com.syncari.core.insights.query;

import com.syncari.connector.ConnectorInfo;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.model.insights.QueryConfig;
import com.syncari.core.model.insights.dataset.VariableValue;

import java.util.Map;
import java.util.Optional;

public interface InsightsQueryBuilder {

    String buildQuery(QueryConfig config, ConnectorInfo connectorInfo, Optional<String> datasetId,
                      Map<String, VariableValue> variableValuesMapTobeused,Map<String, Datatype> variableLeftTypes);

    boolean validateQuery(String query);
}
