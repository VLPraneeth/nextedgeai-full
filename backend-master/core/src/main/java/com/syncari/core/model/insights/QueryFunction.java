package com.syncari.core.model.insights;

import com.syncari.core.model.insights.dataset.VariableValue;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
public abstract class QueryFunction {

    List<QField> columns;
    String alias;
    String dataType = "string";
    public abstract AggFunctions getQueryFunction();

    public abstract String getName();

    public abstract String getAlias();

    public abstract  boolean validate();

    protected String getEscapedName(String escapeChar, Map<String, String> entityIdAliasMap){
        String entityDatasetId = columns.stream().findFirst().get().getDatasetId();
        String datasourceAlias = columns.stream().findFirst().get().getDatasourceAlias();
        String alias = StringUtils.isNotEmpty(datasourceAlias)? datasourceAlias : entityIdAliasMap.getOrDefault(entityDatasetId,null);
        return (null != alias) ? ("\"" + alias + "\"." + escapeChar + getName() + escapeChar) : (escapeChar + getName() + escapeChar);
    }

    public abstract  String buildExpression(String escapeChar, Map<String, String> entityIdAliasMap);

    public String buildAlias(List<String> entityId,String displayName, Map<String, String> entityIdAliasMap){
        String entityIdAliasNameJoined = entityId.stream().map(e -> entityIdAliasMap.get(e)).collect(Collectors.joining(":"));
        return entityIdAliasNameJoined + ":" + displayName;
    }

    @Override
    public String toString(){
        return "columns : " + columns.toString() + " alias : " + alias + " dataType : "+ dataType;
    }

    public abstract QueryFunction makeCopy();
}
