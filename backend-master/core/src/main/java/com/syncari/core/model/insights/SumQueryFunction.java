package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class SumQueryFunction extends UnaryQueryFunction {

    AggFunctions function = AggFunctions.SUM;

    @Override
    public AggFunctions getQueryFunction() {
        return function;
    }

    @Override
    public String buildExpression(String escapeChar, Map<String, String> entityIdAliasMap) {
        return null == this.getAlias() ? function.name() + "(" + this.getEscapedName(escapeChar,entityIdAliasMap) + ")" :
                function.name() + "(" + this.getEscapedName(escapeChar,entityIdAliasMap) + ")" + " " + "\"" +  this.getAlias() + "\"";
    }

    @Override
    public String getDataType(){
        return "double";
    }

    public QueryFunction makeCopy(){
        QueryFunction func = new SumQueryFunction().setAlias(alias);
        func.setDataType(dataType);
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
