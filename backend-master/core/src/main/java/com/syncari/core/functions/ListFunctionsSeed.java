package com.syncari.core.functions;

import com.syncari.core.datatype.*;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.Parameter;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import org.bson.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

public class ListFunctionsSeed {
    private static final Parameter objectParam = new Parameter("object", DatatypeFactory.getDatatype("object"),
            false);

    public static FunctionDefinition join() {
        return new FunctionDefinition().setName(FunctionConstants.JOIN).setDisplayName(i18n("join_func_title")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("join_func")).setIconPath(format(FunctionsSeed.iconPath, "join"))
                .setHelpPath("functions." + FunctionConstants.JOIN)
                .setEngineType(EngineType.FUNCTION).setOutputType(StringType.VALUE).setType(Type.STANDARD)
                .setPositionalParams(List.of(objectParam))
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setConfiguration(
                        List.of(
                                new FunctionConfiguration()
                                        .setName("delimiter")
                                        .setLabel(i18n("join_func_delimiter_title"))
                                        .setHelpSummary(i18n("join_func_delimiter_help_summary"))
                                        .setDatatype(new StringType())
                                        .setDefaultValue("")
                                        .setRequired(false)
                                        .setAdditionalProperties(Map.of("hideTokenPicker", true))
                        )
                );
    }

    public static FunctionDefinition split() {
        return new FunctionDefinition().setName(FunctionConstants.SPLIT).setDisplayName(i18n("split_func_title")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("split_func")).setIconPath(format(FunctionsSeed.iconPath, "split"))
                .setHelpPath("functions." + FunctionConstants.SPLIT)
                .setEngineType(EngineType.FUNCTION).setOutputType(new ListType()).setType(Type.BUILT_IN)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("string"),
                        false)))
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setConfiguration(
                        List.of(
                                new FunctionConfiguration()
                                        .setName("delimiter")
                                        .setLabel(i18n("split_func_delimiter_title"))
                                        .setHelpSummary(i18n("split_func_delimiter_help_summary"))
                                        .setDatatype(new StringType())
                                        .setDefaultValue("")
                                        .setRequired(true)
                                        .setAdditionalProperties(Map.of("hideTokenPicker", true))
                        )
                );
    }

    public static FunctionDefinition reverseList() {
        return new FunctionDefinition().setName(FunctionConstants.REVERSE_LIST).setDisplayName(i18n("reverseList_func_title")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("reverseList_func_help")).setIconPath(format(FunctionsSeed.iconPath, "reverse-list"))
                .setHelpPath("functions." + FunctionConstants.REVERSE_LIST)
                .setEngineType(EngineType.FUNCTION).setOutputType(new ListType()).setType(Type.BUILT_IN)
                .setPositionalParams(List.of(new Parameter("values", DatatypeFactory.getDatatype("list"), false) ))
                .setAdditionalInputTypes(List.of(ListType.VALUE));
    }

    public static FunctionDefinition findInList() {
        List<FunctionConfiguration> configuration = List.of(
                new FunctionConfiguration()
                        .setName("input")
                        .setDatatype(new StringType()).setLabel(i18n("find_in_list_input_label"))
                        .setHelpSummary(i18n("find_in_list_input_help"))
                        .setHelpText(i18n("find_in_list_input_help"))
                        .setDefaultValue("{{previous}}")
                        .setAdditionalProperties(Map.of()),

                new FunctionConfiguration().setName("predicate").setDatatype(new PredicateType())
                        .setLabel(i18n("findInList_predicate_label"))
                        .setHelpSummary(i18n("findInList_predicate_help"))
                        .setHelpText(i18n("findInList_predicate_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("fieldSet", "conditionFields", "operatorType", "FindInListOperator")),
                new FunctionConfiguration().setName("field").setDatatype(new PicklistType()).setLabel("Field")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet", "conditionFields", "values", getValues())),
                new FunctionConfiguration().setName("operator").setDatatype(new PicklistType()).setLabel("Operator")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields", "operatorType","FindInListOperator", "dependsOn", Map.of("dependantType", "FindInListOperator", "dependantField", "configuration.field"))),
                new FunctionConfiguration().setName("value").setDatatype(new StringType()).setLabel("Value")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields", "type", "literal"))
        );
        return new FunctionDefinition().setName(FunctionConstants.FIND_IN_LIST).setDisplayName(i18n("findInList_func_title")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("findInList_func_help")).setIconPath(format(FunctionsSeed.iconPath, "find-in-list"))
                .setHelpPath("functions." + FunctionConstants.FIND_IN_LIST)
                .setEngineType(EngineType.FUNCTION).setOutputType(new ListType()).setType(Type.BUILT_IN)
                .setPositionalParams(List.of(new Parameter("values", DatatypeFactory.getDatatype("list"), false) ))
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setConfiguration(configuration);
    }

    private static List<Document> getValues() {
        Map<String, String> fields = new HashMap<>();
        fields.put("syncari_findInList_ValueInList", "Value In List");
        fields.put("syncari_findInList_Position", "Position");
        return fields.entrySet().stream().map(e -> new Document("value", e.getKey()).append("type","variable").append("label",e.getValue())).collect(Collectors.toList());
    }

    public static FunctionDefinition getListItem() {
        List<FunctionConfiguration> configuration = List.of(
                new FunctionConfiguration().setName("position").setDatatype(new IntegerType())
                        .setLabel(i18n("position_label"))
                        .setHelpSummary(i18n("position_help"))
                        .setHelpText(i18n("position_help"))
                        .setDefaultValue(0).setRequired(true)
        );
        return new FunctionDefinition().setName(FunctionConstants.GET_LIST_ITEM).setDisplayName(i18n("getListItem_func_title")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("getListItem_func_help")).setIconPath(format(FunctionsSeed.iconPath, "get-list-item"))
                .setHelpPath("functions." + FunctionConstants.GET_LIST_ITEM)
                .setEngineType(EngineType.FUNCTION).setOutputType(new ObjectType()).setType(Type.BUILT_IN)
                .setPositionalParams(List.of(new Parameter("values", DatatypeFactory.getDatatype("list"), false) ))
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setConfiguration(configuration);
    }

    public static FunctionDefinition sort() {
        return new FunctionDefinition().setName(FunctionConstants.SORT).setDisplayName(i18n("sort_func_title")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("sort_func_help")).setIconPath(format(FunctionsSeed.iconPath, "sort"))
                .setHelpPath("functions." + FunctionConstants.SORT)
                .setEngineType(EngineType.FUNCTION).setOutputType(new ListType()).setType(Type.BUILT_IN)
                .setPositionalParams(List.of(new Parameter("values", DatatypeFactory.getDatatype("list"), false) ))
                .setAdditionalInputTypes(List.of(ListType.VALUE));
    }

    public static FunctionDefinition addToList() {

        List<FunctionConfiguration> configuration = List.of(
                new FunctionConfiguration().setName(ListMutateFunctions.DATA_TYPE).setDatatype(new PicklistType()).setLabel("addToList_dataType_func_label")
                .setDefaultValue("").setRequired(true).setAdditionalProperties(Map.of("values", FunctionsSeed.getSupportedDataTypes()))
                .setHelpSummary("addToList_dataType_func_help"),
                new FunctionConfiguration().setName(ListMutateFunctions.LIST_INDEX).setDatatype(new IntegerType()).setLabel("addToList_index_func_label")
                        .setAdditionalProperties(Map.of())
                        .setHelpSummary("addToList_index_func_help"),
                new FunctionConfiguration().setName(ListMutateFunctions.VALUE).setDatatype(new StringType()).setLabel("addToList_value_func_label")
                        .setDefaultValue("").setRequired(true).setAdditionalProperties(Map.of())
                        .setHelpSummary("addToList_value_func_help")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "datatype", "dependantField", "configuration.dataType"))),
                new FunctionConfiguration().setName(ListMutateFunctions.INPUT_LIST).setDatatype(new StringType()).setLabel("addToList_input_func_label")
                        .setDefaultValue("").setRequired(false).setAdditionalProperties(Map.of())
                        .setHelpSummary("addToList_input_func_help")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "datatype", "dependantField", "configuration.dataType")))
        );

        return new FunctionDefinition().setName(FunctionConstants.ADD_TO_LIST).setDisplayName("addToList_func_label").setScope(Scope.ATTRIBUTE)
                .setHelpSummary("addToList_func_help").setIconPath(format(FunctionsSeed.iconPath, "add-to-list"))
                .setHelpPath("functions." + FunctionConstants.ADD_TO_LIST)
                .setEngineType(EngineType.FUNCTION).setOutputType(new ListType()).setType(Type.BUILT_IN)
                .setPositionalParams(List.of(new Parameter("values", DatatypeFactory.getDatatype("list"), false)))
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setConfiguration(configuration);
    }

    public static FunctionDefinition removeFromList() {

        List<FunctionConfiguration> configuration = List.of(
                new FunctionConfiguration().setName(ListMutateFunctions.DATA_TYPE).setDatatype(new PicklistType()).setLabel("removeFromList_dataType_func_label")
                        .setDefaultValue("").setRequired(true).setAdditionalProperties(Map.of("values", FunctionsSeed.getSupportedDataTypes()))
                        .setHelpSummary("removeFromList_dataType_func_help"),
                new FunctionConfiguration().setName(ListMutateFunctions.LIST_INDEX).setDatatype(new IntegerType()).setLabel("removeFromList_index_func_label")
                        .setAdditionalProperties(Map.of())
                        .setHelpSummary("removeFromList_index_func_help"),
                new FunctionConfiguration().setName(ListMutateFunctions.VALUE).setDatatype(new StringType()).setLabel("removeFromList_value_func_label")
                        .setDefaultValue("").setAdditionalProperties(Map.of())
                        .setHelpSummary("removeFromList_value_func_help")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "datatype", "dependantField", "configuration.dataType"))),
                new FunctionConfiguration().setName(ListMutateFunctions.INPUT_LIST).setDatatype(new StringType()).setLabel("removeFromList_input_func_label")
                        .setDefaultValue("").setAdditionalProperties(Map.of())
                        .setHelpSummary("removeFromList_input_func_help")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "datatype", "dependantField", "configuration.dataType")))
        );

        return new FunctionDefinition().setName(FunctionConstants.REMOVE_FROM_LIST).setDisplayName(i18n("removeFromList_func_label")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("removeFromList_func_help")).setIconPath(format(FunctionsSeed.iconPath, "remove-from-list"))
                .setHelpPath("functions." + FunctionConstants.REMOVE_FROM_LIST)
                .setEngineType(EngineType.FUNCTION).setOutputType(new ListType()).setType(Type.BUILT_IN)
                .setPositionalParams(List.of(new Parameter("values", DatatypeFactory.getDatatype("list"), false)))
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setConfiguration(configuration);
    }

    public static FunctionDefinition removeDuplicates() {

        List<FunctionConfiguration> configuration = List.of(
                new FunctionConfiguration().setName(ListMutateFunctions.INPUT_LIST).setDatatype(new StringType()).setLabel("removeDuplicates_input_func_label")
                        .setDefaultValue("").setRequired(false).setAdditionalProperties(Map.of())
                        .setHelpSummary("removeDuplicate_input_func_help")
        );

        return new FunctionDefinition().setName(FunctionConstants.REMOVE_DUPLICATES).setDisplayName("removeDuplicates_func_label").setScope(Scope.ATTRIBUTE)
                .setHelpSummary("removeDuplicates_func_help").setIconPath(format(FunctionsSeed.iconPath, "remove-duplicates"))
                .setHelpPath("functions." + FunctionConstants.REMOVE_DUPLICATES)
                .setEngineType(EngineType.FUNCTION).setOutputType(new ListType()).setType(Type.BUILT_IN)
                .setPositionalParams(List.of(new Parameter("values", DatatypeFactory.getDatatype("list"), false)))
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setConfiguration(configuration);
    }
}
