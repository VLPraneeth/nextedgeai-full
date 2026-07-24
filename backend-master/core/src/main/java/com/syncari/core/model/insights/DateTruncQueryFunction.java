package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
public class DateTruncQueryFunction extends UnaryQueryFunction{

    AggFunctions function = AggFunctions.DATE_TRUNC;

    private String truncatedField = "month";

    @Override
    public String getName() {
        List<QField> notLiteralColumns = columns.stream().filter(c -> !c.isLiteral()).collect(Collectors.toList());
        return notLiteralColumns.stream().findFirst().get().getType() != QField.Type.DATASET ? notLiteralColumns.stream().findFirst().get().getName().toLowerCase() : notLiteralColumns.stream().findFirst().get().getName();

    }

    @Override
    public AggFunctions getQueryFunction() {
        return function;
    }

    @Override
    public boolean validate() {
        return ((super.validate()) && (null != truncatedField));
    }

    @Override
    protected String getEscapedName(String escapeChar, Map<String, String> entityIdAliasMap){
        String entityDatasetId = columns.stream().filter(c -> !c.isLiteral()).findFirst().get().getDatasetId();;
        String alias = null;
        if (StringUtils.isNotEmpty(entityDatasetId)){
            alias = entityIdAliasMap.get(entityDatasetId);
        }
        return (null != alias) ? ("\"" + alias + "\"." + escapeChar + getName().toLowerCase() + escapeChar) : (escapeChar + getName() + escapeChar);
    }

    @Override
    public String buildExpression(String escapeChar, Map<String, String> entityIdAliasMap) {
        return StringUtils.isNotEmpty(this.getAlias()) ?  function.name() + "(\'"+ this.getTruncatedField() + "\'," + this.getEscapedName(escapeChar, entityIdAliasMap) + ")" + " " + "\"" +  this.getAlias() + "\"" :
                function.name() + "(\'"+ this.getTruncatedField() + "\'," + this.getEscapedName(escapeChar, entityIdAliasMap) + ")";
    }

    public QueryFunction makeCopy(){
        QueryFunction func = new DateTruncQueryFunction().setAlias(alias).setDataType(dataType);
        if (CollectionUtils.isNotEmpty(this.getColumns())){
            List<QField> cols = new ArrayList<>();
            this.getColumns().forEach(col -> {
                cols.add(col.makeCopy());
            });
            func.setColumns(cols);
        }
        return func;
    }
}
