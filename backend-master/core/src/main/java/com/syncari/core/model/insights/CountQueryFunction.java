package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class CountQueryFunction extends UnaryQueryFunction {

    AggFunctions function = AggFunctions.COUNT;

    private QueryFunction innerQueryFunction;

    @Override
    public AggFunctions getQueryFunction() {
        return function;
    }

    @Override
    public String buildExpression(String escapeChar, Map<String, String> entityIdAliasMap) {
        if ((null != innerQueryFunction) && (innerQueryFunction.validate())){
            return null == this.getAlias() ? function.name() + "(" + innerQueryFunction.buildExpression(escapeChar,entityIdAliasMap) + ")" :
                    function.name() + "(" + innerQueryFunction.buildExpression(escapeChar,entityIdAliasMap) + ")" + " " + "\"" +  this.getAlias() + "\"";
        }else{
            return null == this.getAlias() ? function.name() + "(" + this.getEscapedName(escapeChar,entityIdAliasMap) + ")" :
                    this.getEscapedName(escapeChar,entityIdAliasMap).equals("\"*\"") ? function.name() + "(*)"  + " " + "\"" +  this.getAlias() + "\"" :
                    function.name() + "(" + this.getEscapedName(escapeChar,entityIdAliasMap) + ")" + " " + "\"" +  this.getAlias() + "\"";
        }
    }
    @Override
    public String toString(){
        return super.toString();
    }

    @Override
    public String getDataType(){
        return "integer";
    }

    public QueryFunction makeCopy(){
        QueryFunction func = new CountQueryFunction().setAlias(alias).setDataType(dataType);
        if (CollectionUtils.isNotEmpty(this.getColumns())){
            List<QField> cols = new ArrayList<>();
            this.getColumns().forEach(col -> {
                cols.add(col.makeCopy());
            });
            func.setColumns(cols);
        }
        if (null != innerQueryFunction){
            ((CountQueryFunction)func).setInnerQueryFunction(innerQueryFunction.makeCopy());
        }
        return func;
    }
}
