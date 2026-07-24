package com.syncari.core.insights;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.expression.VizConfigExpression;
import com.syncari.core.model.insights.dataset.VariableValue;
import com.syncari.core.pipeline.SimpleExpressionVisitor;
import com.syncari.core.pipeline.expression.*;
import com.syncari.core.utils.QueryBuilderUtil;
import com.syncari.utils.I18n;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.connection.DataType;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class QueryBuilderVisitor extends SimpleExpressionVisitor {
    protected Stack<String> rendered = new Stack<>();
    protected String generated = null;

    private Map<String, Datatype> variableDataTypeMap;

    private Map<String, VariableValue> variableValueMap;
    private Map<String, Datatype> variableLeftDataTypeMap;

    public void setVariableLeftDataTypeMap(Map<String, Datatype> variableLeftDataTypeMap) {
        this.variableLeftDataTypeMap = variableLeftDataTypeMap;
    }

    public void setVariableDataTypeMap(Map<String, Datatype> variableDataTypeMap) {
        this.variableDataTypeMap = variableDataTypeMap;
    }

    public void setVariableValueMap(Map<String, VariableValue> variableValueMap) {
        this.variableValueMap = variableValueMap;
    }

    @Override
    public void visit(Equal exp) {
        var right = rendered.pop();
        var left = rendered.pop();
        var mayBeNewRight = convertRightRelativeTime(right);
        var mayBeNewLeft = convertLeftRelativeTime(right, left);
        if (StringUtils.isNotEmpty(mayBeNewRight) && StringUtils.isNotEmpty(mayBeNewLeft)){
            rendered.push(String.format(" (%s = %s) ",mayBeNewLeft,mayBeNewRight));
        }else if ((null == mayBeNewRight) && StringUtils.isNotEmpty(mayBeNewLeft)){
            Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
            rendered.push(String.format(" (%s = %s) ",mayBeNewLeft,converted));
        }else{
            if (QueryBuilderUtil.isValueVariable(StringUtils.strip(right, "\'"))){
                rendered.push(String.format(" (%s = %s) ",left,right));
            }else{
                Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
                rendered.push(String.format(" (%s = %s) ",left,converted));
            }

        }
        if (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left)) && QueryBuilderUtil.isValueVariable(StringUtils.strip(right, "\'"))){
            variableLeftDataTypeMap.put(QueryBuilderUtil.getStrippedVariable(StringUtils.strip(right, "\'")).toString(),variableDataTypeMap.get(left));
        }
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        var right = rendered.pop();
        var left = rendered.pop();
        var mayBeNewRight = convertRightRelativeTime(right);
        var mayBeNewLeft = convertLeftRelativeTime(right, left);
        if (StringUtils.isNotEmpty(mayBeNewRight) && StringUtils.isNotEmpty(mayBeNewLeft)){
            rendered.push(String.format(" (%s > %s) ",mayBeNewLeft,mayBeNewRight));
        }else if ((null == mayBeNewRight) && StringUtils.isNotEmpty(mayBeNewLeft)){
            Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
            rendered.push(String.format(" (%s > %s) ",mayBeNewLeft,converted));
        }else{
            if (QueryBuilderUtil.isValueVariable(StringUtils.strip(right, "\'"))){
                rendered.push(String.format(" (%s > %s) ",left,right));
            }else{
                Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
                rendered.push(String.format(" (%s > %s) ",left,converted));
            }

        }
        if (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left)) && QueryBuilderUtil.isValueVariable(StringUtils.strip(right, "\'"))){
            variableLeftDataTypeMap.put(QueryBuilderUtil.getStrippedVariable(StringUtils.strip(right, "\'")).toString(),variableDataTypeMap.get(left));
        }
    }

    @Override
    public void visit(LessThan lessThan) {
        var right = rendered.pop();
        var left = rendered.pop();
        var mayBeNewRight = convertRightRelativeTime(right);
        var mayBeNewLeft = convertLeftRelativeTime(right, left);
        if (StringUtils.isNotEmpty(mayBeNewRight) && StringUtils.isNotEmpty(mayBeNewLeft)){
            rendered.push(String.format(" (%s < %s) ",mayBeNewLeft,mayBeNewRight));
        }else if ((null == mayBeNewRight) && StringUtils.isNotEmpty(mayBeNewLeft)){
            Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
            rendered.push(String.format(" (%s < %s) ",mayBeNewLeft,converted));
        }else{
            if (QueryBuilderUtil.isValueVariable(StringUtils.strip(right, "\'"))){
                rendered.push(String.format(" (%s < %s) ",left,right));
            }else{
                Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
                rendered.push(String.format(" (%s < %s) ",left,converted));
            }
        }
        if (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left)) && QueryBuilderUtil.isValueVariable(StringUtils.strip(right, "\'"))){
            variableLeftDataTypeMap.put(QueryBuilderUtil.getStrippedVariable(StringUtils.strip(right, "\'")).toString(),variableDataTypeMap.get(left));
        }
    }

    @Override
    public void visit(GreaterThanEqual greaterThanEqual) {
        var right = rendered.pop();
        var left = rendered.pop();
        var mayBeNewRight = convertRightRelativeTime(right);
        var mayBeNewLeft = convertLeftRelativeTime(right, left);
        if (StringUtils.isNotEmpty(mayBeNewRight) && StringUtils.isNotEmpty(mayBeNewLeft)){
            rendered.push(String.format(" (%s >= %s) ",mayBeNewLeft,mayBeNewRight));
        }else if ((null == mayBeNewRight) && StringUtils.isNotEmpty(mayBeNewLeft)){
            Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
            rendered.push(String.format(" (%s >= %s) ",mayBeNewLeft,converted));
        }else{
            if (QueryBuilderUtil.isValueVariable(StringUtils.strip(right, "\'"))){
                rendered.push(String.format(" (%s >= %s) ",left,right));
            }else{
                Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
                rendered.push(String.format(" (%s >= %s) ",left,converted));
            }

        }
        if (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left)) && QueryBuilderUtil.isValueVariable(StringUtils.strip(right, "\'"))){
            variableLeftDataTypeMap.put(QueryBuilderUtil.getStrippedVariable(StringUtils.strip(right, "\'")).toString(),variableDataTypeMap.get(left));
        }
    }

    @Override
    public void visit(LessThanEqual lessThanEqual) {
        var right = rendered.pop();
        var left = rendered.pop();
        var mayBeNewRight = convertRightRelativeTime(right);
        var mayBeNewLeft = convertLeftRelativeTime(right, left);
        if (StringUtils.isNotEmpty(mayBeNewRight) && StringUtils.isNotEmpty(mayBeNewLeft)){
            rendered.push(String.format(" (%s <= %s) ",mayBeNewLeft,mayBeNewRight));
        }else if ((null == mayBeNewRight) && StringUtils.isNotEmpty(mayBeNewLeft)){
            Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
            rendered.push(String.format(" (%s <= %s) ",mayBeNewLeft,converted));
        }else{
            if (QueryBuilderUtil.isValueVariable(StringUtils.strip(right, "\'"))){
                rendered.push(String.format(" (%s <= %s) ",left,right));
            }else{
                Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
                rendered.push(String.format(" (%s <= %s) ",left,converted));
            }
        }
        if (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left)) && QueryBuilderUtil.isValueVariable(StringUtils.strip(right, "\'"))){
            variableLeftDataTypeMap.put(QueryBuilderUtil.getStrippedVariable(StringUtils.strip(right, "\'")).toString(),variableDataTypeMap.get(left));
        }
    }

    public void visit(And exp) {
        var right = rendered.pop();
        var left = rendered.pop();
        Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
        rendered.push(String.format(" (%s and %s) ",left,converted));
    }

    public void visit(Or exp) {
        var right = rendered.pop();
        var left = rendered.pop();
        Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
        rendered.push(String.format(" (%s or %s) ",left,converted));
    }

    @Override
    public void visit(NotIn expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
        rendered.push(String.format(" %s not in %s", left,converted));
    }

    @Override
    public void visit(NotEmpty expression) {
        var left = rendered.pop();
        rendered.push(String.format(" %s is not null", left));
    }

    @Override
    public void visit(Empty expression) {
        var left = rendered.pop();
        rendered.push(String.format(" %s is null", left));
    }

    @Override
    public void visit(Contains expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format(" LOWER(%s) LIKE LOWER(\'%%%s%%\')", left, StringUtils.strip(right, "\'")));
    }


    @Override
    public void visit(In expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
        rendered.push(String.format(" %s in %s", left,converted));
    }

    @Override
    public void visit(NotEqual expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        var mayBeNewRight = convertRightRelativeTime(right);
        var mayBeNewLeft = convertLeftRelativeTime(right, left);
        if (StringUtils.isNotEmpty(mayBeNewRight) && StringUtils.isNotEmpty(mayBeNewLeft)){
            rendered.push(String.format(" (%s != %s) ",mayBeNewLeft,mayBeNewRight));

        }else if ((null == mayBeNewRight) && StringUtils.isNotEmpty(mayBeNewLeft)){
            Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
            rendered.push(String.format(" (%s != %s) ",mayBeNewLeft,converted));
        }else{
            if (QueryBuilderUtil.isValueVariable(StringUtils.strip(right, "\'"))){
                rendered.push(String.format(" (%s != %s) ",left,right));
            }else{
                Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
                rendered.push(String.format(" %s != %s", left,converted));
            }
        }
        if (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left)) && QueryBuilderUtil.isValueVariable(StringUtils.strip(right, "\'"))){
            variableLeftDataTypeMap.put(QueryBuilderUtil.getStrippedVariable(StringUtils.strip(right, "\'")).toString(),variableDataTypeMap.get(left));
        }
    }

    // Add equal ignore case
    @Override
    public void visit(EqualIgnoreCase exp) {
        var right = rendered.pop();
        var left = rendered.pop();
        Object converted = (MapUtils.isNotEmpty(variableDataTypeMap) && (null != variableDataTypeMap.get(left))) ? right.startsWith("\'") ? "\'" + convert(left, right)  + "\'" : convert(left,right) : right;
        rendered.push(String.format(" (%s ILIKE %s) ",left,converted));
    }

    @Override
    public void visit(BetweenExpression exp) {
        var upper = rendered.pop();
        var lower = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format(" (%s BETWEEN %s AND %s) ",left,lower, upper));
    }

    @Override
    public void visit(NotBetweenExpression exp) {
        var upper = rendered.pop();
        var lower = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format(" (%s NOT BETWEEN %s AND %s) ",left,lower, upper));
    }

    //Starts with operator
    @Override
    public void visit(StartsWith expression) {
        var right = rendered.pop();
        var left = rendered.pop();
        rendered.push(String.format(" %s ILIKE %s%%\'", left,StringUtils.removeEnd(right, "\'")));
    }

    public String getGeneratedBody() {
        if (generated == null) generated = rendered.pop();
        return generated;
    }

    public void visit(LiteralExpression exp) {
        if (exp.isRendered()) {
            rendered.push(exp.getValue() == null? null: getStringValue(exp.getValue()));
        } else {
            if(exp.getValue()!=null && List.class.isAssignableFrom(exp.getValue().getClass())){
                List<Object> valList = new ArrayList<>();
                List.class.cast(exp.getValue()).forEach(val -> {
                    if (List.class.isAssignableFrom(val.getClass())){
                        List.class.cast(val).forEach(v -> {
                            valList.add(v);
                        });
                    }else{
                        valList.add(val);
                    }
                });
                rendered.push(valList.stream().map(v->getStringValue(v)).collect(Collectors.joining(",","(",")")).toString());
            }else if (exp.getValue()!=null && Boolean.class.isAssignableFrom(exp.getValue().getClass())){
                rendered.push(Boolean.class.cast(exp.getValue()).toString());
            }else{
                rendered.push(getStringValue(exp.getValue().toString()));
            }
        }
    }
    protected String getStringValue(Object value) {
        if (isDatePartOrCurrentDate(value)){
            return value.toString();
        }
        if ((!((String) value).startsWith("\'"))  && (!((String) value).endsWith("\'"))){
            return ("\'" + value + "\'");
        }else{
            return value.toString();
        }
    }

    private boolean isDatePartOrCurrentDate(Object value){
        return ((String) value).equalsIgnoreCase("CURRENT_DATE") || ((String) value).contains("DATE_PART") || ((String) value).contains("DATE_TRUNC");
    }

    public void visit(VariableExpression exp) {
        if (exp.isRendered()) {
            rendered.push(String.format("\"%s\")",exp.getVariableName()));
        } else {
            rendered.push(exp.getVariableName());
        }
    }

    @Override
    public void visit(VizConfigExpression exp) {
        if (exp.isRendered()) {
            rendered.push(exp.getValue() == null? null: getVizConfigQuery(exp.getValue()));
        } else {
            rendered.push(getVizConfigQuery(exp.getValue().toString()));
        }
    }

    protected String getVizConfigQuery(Object value) {
        if(String.class.isAssignableFrom(value.getClass())) {
            return ("(" + value + ")");
        }else{
            return ("\'" + value.toString() + "\'");
        }
    }

    private Object convert(String left, String right){
        Object converted = getDataTypeSpecificConversion(variableDataTypeMap.get(left).convert(StringUtils.strip(right, "\'")));
        if (StringUtils.isNotEmpty(right) && (null == converted)){
            return StringUtils.strip(right, "\'");
        }
        return converted;
    }

    Object getDataTypeSpecificConversion(Object converted) {
        if(converted == null) return converted;
        if(converted instanceof ZonedDateTime) {
            return Date.from(((ZonedDateTime)converted).toInstant());
        }
        return converted;
    }

    private String convertRightRelativeTime(String value){
        if (DatetimeType.isCurrentAnnotation(StringUtils.strip(value, "\'"))){
            return QueryBuilderUtil.getExpressionForCurrentAnnotation(StringUtils.strip(value, "\'"));
        }
        return null;
    }

    private String convertLeftRelativeTime(String right, String left){
        if (isVariableDatetime(StringUtils.strip(right, "\'"))){
            VariableValue variableValue = variableValueMap.get(QueryBuilderUtil.getStrippedVariable(StringUtils.strip(right, "\'")));
            if ((null != variableValue) && (DatetimeType.isCurrentAnnotation(StringUtils.strip(variableValue.getDefaultValue().toString(), "\'")))){
                String datepartField =  QueryBuilderUtil.getDatePartField(StringUtils.strip(variableValue.getDefaultValue().toString(), "\'"));
                return "DATE_TRUNC(\'"+ datepartField + "\'," + left + ")";
            }
        }
        if (DatetimeType.isCurrentAnnotation(StringUtils.strip(right, "\'"))){
            String datepartField =  QueryBuilderUtil.getDatePartField(StringUtils.strip(right, "\'"));
            return "DATE_TRUNC(\'"+ datepartField + "\'," + left + ")";
        }
        return null;
    }

    private boolean isVariableDatetime(Object val){
        if (QueryBuilderUtil.isValueVariable(val)){
            Object value = QueryBuilderUtil.getStrippedVariable(val);
            String dataType = variableValueMap.get(value).getDatatype();
            return ((StringUtils.isNotEmpty(dataType)) && (dataType.equalsIgnoreCase("datetime")));
        }
        return false;
    }

}
