package com.syncari.core.insights;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.expression.VizConfigExpression;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.insights.QField;
import com.syncari.core.model.insights.dataset.VariableValue;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.utils.I18n;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.function.Function;

import static com.syncari.utils.I18n.i18n;

@Slf4j
public class InsightsQueryPredicateParser extends PredicateParser {
    protected Map<String, Function<Map<String, Object>, Expression>> insightsProcessors = new HashMap<>();

    private Map<String, Pair<String, List<AttributeDefinition>>> entityAndItsAttributeMap;
    private Map<String, Datatype> aliasDataTypeMap = new HashMap<>();


    public void setEntityAndItsAttributeMap(Map<String, Pair<String, List<AttributeDefinition>>> entityAndItsAttributeMap) {
        this.entityAndItsAttributeMap = entityAndItsAttributeMap;
    }

    public Map<String, Datatype> getAliasDataTypeMap() {
        return aliasDataTypeMap;
    }

    public InsightsQueryPredicateParser(){
        super();
        insightsProcessors.put("vizconfig",this::vizconfig);
    }
    public Expression fromMap(Map<String, Object> expressionMap) {
        if(expressionMap == null) {
            throw new SyncariValidationException(I18n.i18n("invalid_expression_insights"));
        }
        if(expressionMap.isEmpty()){
            return null;
        }
        String operator = expressionMap.getOrDefault("operator",
                expressionMap.getOrDefault("type", "invalid")).toString().toLowerCase();
        List<Map<String, Object>> predicates = list("predicates", expressionMap);
        if(logicalOpratorProcessors.containsKey(operator)){
            return logicalOpratorProcessors.get(operator).apply(predicates);
        }else if (operatorProcessors.containsKey(operator)){
            return operatorProcessors.getOrDefault(operator,this::invalid).apply(expressionMap);
        }else{
            return insightsProcessors.getOrDefault(operator,this::invalid).apply(expressionMap);
        }
    }

    protected Expression vizconfig(Map<String, Object> expressionMap) {
        return new VizConfigExpression(expressionMap.get("value"), true);
    }

    @Override
    protected Expression literal(Map<String, Object> expressionMap) {
        String dataType = (String)expressionMap.get("dataType");
        Object valueinMap = expressionMap.get("value");
        /*if (isValueVariable(valueinMap)){
            Object strripedVal = getStrippedVariable(valueinMap);
            if (strripedVal instanceof List){
                List<Object> valueinMapList = new ArrayList<>();
                ((List)strripedVal).forEach(sV -> {
                    VariableValue variableValue = varValues.get(sV);
                    valueinMapList.add(variableValue.getDefaultValue());
                });
                valueinMap = valueinMapList;
            }else{
                VariableValue variableValue = varValues.get(strripedVal);
                if (null != variableValue) {
                    if (!variableValue.getDatatype().equalsIgnoreCase("relativetime")){
                        // then put the right value for query
                        valueinMap = variableValue.getDefaultValue();
                        dataType = variableValue.getDatatype();
                    }else{
                        log.error("Variable value is relative time but it should not come here, this variable should be resolved before");
                    }
                }
            }

        }*/
        if (null != dataType){
            return castToDataType(valueinMap, dataType);
        }else{
            return Expression.lit(valueinMap);

        }
    }

    private Expression castToDataType(Object value, String dataType){
        if (dataType.equalsIgnoreCase("boolean")){
            return Expression.lit(Boolean.valueOf(value.toString()));
        }else if (dataType.equalsIgnoreCase("integer")){
            return Expression.lit(Integer.valueOf(value.toString()));
        }else if (dataType.equalsIgnoreCase("long")){
            return Expression.lit(Long.valueOf(value.toString()));
        }
        return Expression.lit(value);
    }

    protected Expression variable(Map<String, Object> expressionMap) {
        QField.Type typeOfDataset = (null != expressionMap.get("datasetType")) ? QField.Type.valueOf((String)expressionMap.get("datasetType")) : null;
        Datatype dataType = (null != expressionMap.get("dataType")) ? DatatypeFactory.getDatatype((String)expressionMap.get("dataType")) : StringType.VALUE;
        String varName;
        if ((null != typeOfDataset) && ((typeOfDataset.equals(QField.Type.DATASET) || typeOfDataset.equals(QField.Type.ENTITY) ))){
             varName = getApiNameofAttribute(expressionMap);
        }else{
            if ((null != expressionMap.get("value")) && (!ObjectId.isValid(expressionMap.get("value").toString()))){
                varName = expressionMap.get("value").toString();
            }else{
                varName = getApiNameofAttribute(expressionMap);
            }
        }
        aliasDataTypeMap.put(varName, dataType);
        return Expression.var(varName, dataType);

    }

    private String getApiNameofAttribute(Map<String, Object> expressionMap){
        QField.Type typeOfDataset = QField.Type.valueOf((String)expressionMap.get("datasetType"));
        if (typeOfDataset.equals(QField.Type.ENTITY)){
            if (MapUtils.isNotEmpty(entityAndItsAttributeMap)){
                String datasetId = ((String)expressionMap.get("datasetId"));
                Pair<String, List<AttributeDefinition>> attributeDefinitionsPairWithDatastore = entityAndItsAttributeMap.get(datasetId);
                String fieldApiName = expressionMap.get("apiName").toString();
                String datastoreFieldName = (null != attributeDefinitionsPairWithDatastore) ? attributeDefinitionsPairWithDatastore.y.stream().filter(def -> def.getApiName().equals(fieldApiName)).findFirst().get().getDataStoreName() : fieldApiName;
                String entityAliasName = (null != attributeDefinitionsPairWithDatastore) ? attributeDefinitionsPairWithDatastore.x : ((String)expressionMap.get("datasetApiName"));
                return "\"" + entityAliasName + "\".\"" + datastoreFieldName.toString().toLowerCase() + "\"";

            }else{
                return getFieldNameFromApiName(expressionMap, false);
            }

        }else if (typeOfDataset.equals(QField.Type.DATASET)){
            return getFieldNameFromApiName(expressionMap, true);
        }
        return expressionMap.get("value").toString();
    }

    private String getFieldNameFromApiName(Map<String, Object> expressionMap, boolean isTypeDataset){
        String entityAliasName = ((String)expressionMap.get("datasetApiName"));
        if (StringUtils.isNotEmpty(entityAliasName)){
            return isTypeDataset? "\""+entityAliasName + "\".\"" + expressionMap.get("apiName").toString()+ "\"" : "\""+entityAliasName + "\".\"" + expressionMap.get("apiName").toString().toLowerCase() + "\"";
        }else{
            return isTypeDataset ? "\""+ expressionMap.get("apiName").toString() +"\"": "\""+ expressionMap.get("apiName").toString().toLowerCase() +"\"";
        }
    }
    @Override
    protected Expression invalid(Map<String, Object> expressionMap){
        String operator = expressionMap.getOrDefault("operator","").toString().toLowerCase();
        throw new SyncariValidationException(String.format(i18n("invalid_operator"), operator));
    }

    protected Expression gte(Map<String, Object> expressionMap) {
        return Expression.gte(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }


    protected Expression gt(Map<String, Object> expressionMap) {
        return Expression.gt(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression ne(Map<String, Object> expressionMap) {
        return Expression.ne(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression eq(Map<String, Object> expressionMap) {
        return Expression.eq(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression lte(Map<String, Object> expressionMap) {
        return Expression.lte(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }

    protected Expression lt(Map<String, Object> expressionMap) {
        return Expression.lt(fromMap(nested("left", expressionMap)), fromMap(nested("right", expressionMap)));
    }
}
