package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.lambda.Agg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@Accessors(chain = true)
public class ConcatQueryFunction extends NaryQueryFunction {

    private AggFunctions function = getQFunction();
    String Function_Name = "CONCAT";

    boolean isConcatField = true;

    public AggFunctions getQFunction(){
        return AggFunctions.CONCAT;
    }

    @Override
    public AggFunctions getQueryFunction() {
        return function;
    }

    @Override
    public String getEscapedName(String escapeChar, Map<String, String> entityIdAliasMap) {
        List<String> escapedColumnsNames = columns.stream()
                .map(c -> {
                    String nameTouse = c.getType() != QField.Type.DATASET ? c.getName().toLowerCase() : c.getName();
                    String alias = (MapUtils.isNotEmpty(entityIdAliasMap) && (null != c.getDatasetId())) ? entityIdAliasMap.get(c.getDatasetId()): null;
                    return (null != alias) ? (c.isLiteral() ? "\'"+ c.getName()+"\'" : "\"" + alias + "\"." + escapeChar + nameTouse + escapeChar) :
                            (c.isLiteral() ? "\'"+c.getName()+"\'" : escapeChar + c.getName() + escapeChar);
                })
                .collect(Collectors.toList());
        List<String> filterNotEmpty = escapedColumnsNames.stream().filter(c -> StringUtils.isNotEmpty(c)).collect(Collectors.toList());
        return StringUtils.join(filterNotEmpty, ",");
    }

    @Override
    public String buildExpression(String escapeChar, Map<String, String> entityIdAliasMap) {
        return Function_Name+ "(" + this.getEscapedName(escapeChar, entityIdAliasMap) + ")" + " " + "\"" +  this.getAlias() + "\"";
    }

    public QueryFunction makeCopy(){
        QueryFunction func = new ConcatQueryFunction().setAlias(alias).setDataType(dataType);
        if (CollectionUtils.isNotEmpty(this.getColumns())){
            List<QField> cols = new ArrayList<>();
            this.getColumns().forEach(col -> {
                cols.add(col.makeCopy());
            });
            func.setColumns(cols);
        }

        if (CollectionUtils.isNotEmpty(this.getInnerQueryFields())){
            List<QueryField> cols = new ArrayList<>();
            this.getInnerQueryFields().forEach(col -> {
                cols.add(col.makeCopy());
            });
            ((ConcatQueryFunction)func).setInnerQueryFields(cols);
        }
        return func;
    }


}
