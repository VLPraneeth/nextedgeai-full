package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
public abstract class QueryField {
    QueryFunction queryFunction;
    String description;
    String color;
    String displayFormat;

    public abstract String getName();
    public abstract String getAlias();
    public abstract AggFunctions getFunction();
    public abstract String getEscapedName(String escapeChar, Map<String, String> entityIdAliasMap);

    @Override
    public String toString(){
        return "description : " + description + " color : " + color + " displayFormat : " + displayFormat + "queryFunction : " + queryFunction;
    }

   public abstract QueryField makeCopy();

}
