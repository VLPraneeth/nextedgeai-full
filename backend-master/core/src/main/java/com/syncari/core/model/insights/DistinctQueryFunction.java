package com.syncari.core.model.insights;

import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class DistinctQueryFunction extends  UnaryQueryFunction{

    AggFunctions function = AggFunctions.DISTINCT;

    @Override
    public AggFunctions getQueryFunction() {
        return function;
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public String buildExpression(String escapeChar, Map<String, String> entityIdAliasMap) {
        return (StringUtils.isEmpty(this.getAlias())) ? function.name() + " " + this.getEscapedName(escapeChar,entityIdAliasMap) :
                function.name() + " " + this.getEscapedName(escapeChar,entityIdAliasMap) + " " + "\"" + this.getAlias() + "\"";
    }

    public QueryFunction makeCopy(){
        QueryFunction func = new DistinctQueryFunction().setAlias(alias).setDataType(dataType);
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

