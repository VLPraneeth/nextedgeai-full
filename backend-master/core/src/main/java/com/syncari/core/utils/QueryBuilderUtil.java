package com.syncari.core.utils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class QueryBuilderUtil {

    public static String getExpressionForCurrentAnnotation(String currentAnnotation){
        switch (currentAnnotation.toUpperCase()){
            case "TODAY": return "DATE_TRUNC('day',CURRENT_DATE)";
            case "THIS MONTH": return "DATE_TRUNC('month',CURRENT_DATE)";
            case "THIS YEAR": return "DATE_TRUNC('year',CURRENT_DATE)";
            case "THIS WEEK": return "DATE_TRUNC('week',CURRENT_DATE)";
            case "THIS QUARTER": return "DATE_TRUNC('quarter',CURRENT_DATE)";
        }
        return null;
    }

    public static String getDatePartField(String currentAnnotation){
        switch (currentAnnotation.toUpperCase()){
            case "TODAY": return "day";
            case "THIS MONTH": return "month";
            case "THIS YEAR": return "year";
            case "THIS WEEK": return "week";
            case "THIS QUARTER": return "quarter";
        }
        return null;
    }

    public static boolean isValueVariable(Object val){
        if ((val instanceof String) && StringUtils.isNotEmpty((String)val) && ((String)val).startsWith("{{") && ((String)val).endsWith("}}")){
            return true;
        }else if ((val instanceof List) && CollectionUtils.isNotEmpty((List)val) && ((List)val).get(0).toString().startsWith("{{") && ((List)val).get(0).toString().endsWith("}}")){
            return true;
        }
        return false;
    }



    public static Object getStrippedVariable(Object variableValue){
        if (variableValue instanceof List){
            List<String> result = new ArrayList<>();
            for (Object val : (List)variableValue){
                String strippedVal =  StringUtils.stripStart(((String)val), "{{");
                String strippedEnd = StringUtils.stripEnd(strippedVal, "}}");
                result.add(strippedEnd);
            }
            return result;
        }
        String strippedVal =  StringUtils.stripStart(((String)variableValue), "{{");
        String strippedEnd = StringUtils.stripEnd(strippedVal, "}}");
        return strippedEnd;
    }

    public static String getValue(Object value){
        String result;
        if (value instanceof List){
            List<String> listOfVals = new ArrayList<>();
            ((List)value).forEach(val -> {
                listOfVals.add(val.toString());
            });
            result = StringUtils.join(listOfVals, ",");
        }else{
            result = value.toString();
        }
        return result;
    }
}
