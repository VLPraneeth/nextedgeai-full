package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
@Slf4j
public class NoQueryFunction extends QueryFunction {

    AggFunctions function = AggFunctions.NONE;
    QueryFunction innerQueryFunction;

    @Override
    public AggFunctions getQueryFunction() {
        return function;
    }

    @Override
    public String getName() {
        return columns.stream().findFirst().get().getType() != QField.Type.DATASET ? columns.stream().findFirst().get().getName().toLowerCase() : columns.stream().findFirst().get().getName();
    }

    @Override
    public String getDataType(){
        return columns.stream().findFirst().map(c -> c.getDataType()).orElse("string");
    }

    @Override
    public String getAlias() {
        if (StringUtils.isNotEmpty(alias)){
            return alias;
        }else{
            return this.getName();
        }
    }

    @Override
    protected String getEscapedName(String escapeChar, Map<String, String> entityIdAliasMap){
        String entityDatasetId = columns.stream().findFirst().get().getDatasetId();
        String datasourceAlias = columns.stream().findFirst().get().getDatasourceAlias();
        String entityAlias = StringUtils.isNotEmpty(datasourceAlias)? datasourceAlias :
                entityIdAliasMap.getOrDefault(entityDatasetId,null);
        return (null != entityAlias) ? ("\"" + entityAlias + "\"." + escapeChar + getName() + escapeChar) : (escapeChar + getName() + escapeChar);
    }

    @Override
    public boolean validate() {
        return CollectionUtils.isNotEmpty(this.getColumns()) && (this.getColumns().size() == 1);
    }

    @Override
    public String buildExpression(String escapeChar, Map<String, String> entityIdAliasMap){
        if ((null != innerQueryFunction) && (innerQueryFunction.validate())){
            return StringUtils.isBlank(this.getAlias()) ? innerQueryFunction.buildExpression(escapeChar,entityIdAliasMap) : innerQueryFunction.buildExpression(escapeChar,  entityIdAliasMap) + " " + "\"" + this.getAlias() + "\"";
        }else{
            return StringUtils.isBlank(this.getAlias()) ? this.getEscapedName(escapeChar,entityIdAliasMap) : this.getEscapedName(escapeChar,  entityIdAliasMap) + " " + "\"" + this.getAlias() + "\"";
            //return this.getName();
        }
    }

    public QueryFunction makeCopy(){
        QueryFunction func = new NoQueryFunction().setAlias(alias).setDataType(dataType);
        if (CollectionUtils.isNotEmpty(this.getColumns())){
            List<QField> cols = new ArrayList<>();
            this.getColumns().forEach(col -> {
                cols.add(col.makeCopy());
            });
            func.setColumns(cols);
        }
        if (null != innerQueryFunction){
            ((NoQueryFunction)func).setInnerQueryFunction(innerQueryFunction.makeCopy());
        }
        return func;
    }
}
