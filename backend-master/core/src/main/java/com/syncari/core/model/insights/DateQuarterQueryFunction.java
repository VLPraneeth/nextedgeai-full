package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
public class DateQuarterQueryFunction extends ConcatQueryFunction{

    @Override
    public AggFunctions getQFunction(){
        return AggFunctions.QUARTER;
    }

    private List<QField> fieldsToConcat;

    @Override
    public String getEscapedName(String escapeChar, Map<String, String> entityIdAliasMap) {
        List<String> quarterAttribs = new ArrayList<>();
        // Need to add field to be used for each function type
        if (CollectionUtils.isNotEmpty(fieldsToConcat)){
            fieldsToConcat.stream().filter(f -> f.isFunction()).forEach(fun -> fun.getQueryFunction().setColumns(columns));
            quarterAttribs.addAll(fieldsToConcat.stream()
                    .map(c -> {
                        String nameTouse = c.getType() != QField.Type.DATASET ? c.getName().toLowerCase() : c.getName();
                        String alias = (MapUtils.isNotEmpty(entityIdAliasMap) && (null != c.getDatasetId())) ? entityIdAliasMap.get(c.getDatasetId()): null;
                        return (null != alias) ? (c.isLiteral() ? "\'"+ c.getName()+"\'" : (c.isFunction()? c.getQueryFunction().buildExpression(escapeChar,entityIdAliasMap) :
                                isConcatField ? "\"" + alias + "\"." + escapeChar + nameTouse + escapeChar : "")) :
                                (c.isLiteral() ? "\'"+c.getName()+"\'" : c.isFunction()? c.getQueryFunction().buildExpression(escapeChar,entityIdAliasMap) :
                                        isConcatField ? escapeChar + c.getName() + escapeChar: "");
                    }).collect(Collectors.toList()));
        }
        return StringUtils.join(quarterAttribs, ",");
    }

    @Override
    public boolean validate() {
        return CollectionUtils.isNotEmpty(fieldsToConcat) && fieldsToConcat.size()>1;
    }
}
