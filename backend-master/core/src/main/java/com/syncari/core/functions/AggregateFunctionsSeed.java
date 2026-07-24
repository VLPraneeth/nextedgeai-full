package com.syncari.core.functions;

import com.syncari.core.datatype.*;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.Parameter;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import com.syncari.utils.KeyValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

public class AggregateFunctionsSeed {

    public static FunctionDefinition countRecordsSeed(){
        return createAggregateFunction("countRecords", "count", "count-record", Scope.ENTITY);
    }
    public static FunctionDefinition countRecordsOnFieldSeed(){
        return createAggregateFunction("countRecordsOnField", "count", "count-record", Scope.ATTRIBUTE);
    }
    public static FunctionDefinition sumRecordSeed(){
        return createAggregateFunctionWithField("sumRecords", "sum", "sum-record", Scope.ENTITY);
    }
    public static FunctionDefinition sumRecordOnFieldSeed(){
        return createAggregateFunctionWithField("sumRecordsOnField", "sum", "sum-record", Scope.ATTRIBUTE);
    }
    public static FunctionDefinition avgRecordSeed(){
        return createAggregateFunctionWithField("avgRecords", "avg", "avg-record", Scope.ENTITY);
    }
    public static FunctionDefinition avgRecordOnFieldSeed(){
        return createAggregateFunctionWithField("avgRecordsOnField", "avg", "avg-record", Scope.ATTRIBUTE);
    }
    public static FunctionDefinition stdDevRecordSeed(){
        return createAggregateFunctionWithField("stdDevRecords", "stdDev", "stddev-record", Scope.ENTITY);
    }
    public static FunctionDefinition stdDevRecordOnFieldSeed(){
        return createAggregateFunctionWithField("stdDevRecordsOnField", "stdDev", "stddev-record", Scope.ATTRIBUTE);
    }

    private static FunctionDefinition createAggregateFunctionWithField(String name, String documentationPrefix, String iconName, Scope scope) {
        FunctionDefinition aggregateFunction = createAggregateFunction(name, documentationPrefix, iconName, scope);
        List<FunctionConfiguration> configurationList = new ArrayList<>(aggregateFunction.getConfiguration());
        configurationList.add(aggregateFieldConfiguration(documentationPrefix));
        aggregateFunction.setConfiguration(configurationList);
        return aggregateFunction;
    }

    private static FunctionDefinition createAggregateFunction(String name, String documentationPrefix, String iconName, Scope scope) {
        List<FunctionConfiguration> configuration = aggregateFunctionConfiguration(documentationPrefix);
        return new FunctionDefinition()
                .setName(name).setDisplayName(documentationPrefix+"_record_func_label").setScope(scope)
                .setHelpSummary(documentationPrefix+"_record_func_help").setIconPath(format(FunctionsSeed.iconPath, iconName))
                .setHelpPath("functions." + documentationPrefix)
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static List<FunctionConfiguration> aggregateFunctionConfiguration(String documentationPrefix) {
        return List.of(
                    new FunctionConfiguration().setName("syncariEntityDefId").setDatatype(new PicklistType())
                            .setLabel(documentationPrefix+"_record_entity_label")
                            .setHelpSummary(documentationPrefix+"_record_entity_help")
                            .setHelpText(documentationPrefix+"_record_entity_help")
                            .setDefaultValue("").setRequired(true)
                            .setAdditionalProperties(Map.of("type","SyncariEntity")),

                    new FunctionConfiguration().setName("predicate").setDatatype(new PredicateType())
                            .setLabel(documentationPrefix+"_record_predicate_label")
                            .setHelpSummary(documentationPrefix+"_record_predicate_help")
                            .setHelpText(documentationPrefix+"_record_predicate_help")
                            .setDefaultValue("").setRequired(true)
                            .setAdditionalProperties(Map.of( "fieldSet", "conditionFields")),
                    new FunctionConfiguration().setName("field").setDatatype(new PicklistType()).setLabel("Field")
                            .setDefaultValue("")
                            .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"), "fieldSet","conditionFields")),
                    new FunctionConfiguration().setName("operator").setDatatype(new PicklistType()).setLabel("Operator")
                            .setDefaultValue("")
                            .setAdditionalProperties(Map.of("fieldSet","conditionFields","dependsOn", Map.of("dependantType", "Operator", "dependantField", "configuration.field"))),
                    new FunctionConfiguration().setName("value").setDatatype(new StringType()).setLabel("Value")
                            .setDefaultValue("")
                            .setAdditionalProperties(Map.of("fieldSet","conditionFields"))
            );
    }

    private static FunctionConfiguration aggregateFieldConfiguration(String documentationPrefix) {
        return new FunctionConfiguration().setName("fieldId").setDatatype(new PicklistType())
                .setLabel(documentationPrefix+"_record_field_label")
                .setHelpSummary(documentationPrefix+"_record_field_help")
                .setHelpText(documentationPrefix+"_record_field_help")
                .setRequired(true)
                .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"),
                        "mapping", List.of(new KeyValue("graphKey","configuration.fieldId").set("configKey","value")),
                        "id","fieldId",
                        "name","fieldId"
                ));
    }

}
