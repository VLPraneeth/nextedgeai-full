package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
public class ComplexQField extends QueryField{

    @Override
    public String getName(){
        return queryFunction.getName();
    }

    @Override
    public String getAlias(){
        return queryFunction.getAlias();
    }

    @Override
    public AggFunctions getFunction(){
        return queryFunction.getQueryFunction();
    }

    @Override
    public String getEscapedName(String escapeChar, Map<String, String> entityIdAliasMap){
        return queryFunction.getEscapedName(escapeChar, entityIdAliasMap);
    }

    @Override
    public String toString(){
        return super.toString();
    }

    public QueryField makeCopy(){
        return new ComplexQField().setColor(color).setDisplayFormat(displayFormat).setDescription(description).setQueryFunction(queryFunction.makeCopy());
    }
}
