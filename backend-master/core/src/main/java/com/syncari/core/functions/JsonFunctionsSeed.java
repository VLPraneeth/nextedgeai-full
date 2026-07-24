package com.syncari.core.functions;

import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.ListType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.datatype.TextareaType;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.Parameter;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;

import java.util.List;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

public class JsonFunctionsSeed {

    public static FunctionDefinition parseJsonToArray(Scope scope) {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("input")
                        .setDatatype(TextareaType.VALUE)
                        .setRequired(true)
                        .setLabel("input_json_label")
                        .setHelpSummary("input_json_help")
                        .setHelpText("input_json_help")
                        .setDefaultValue("")
        );
        return new FunctionDefinition().setName(FunctionConstants.PARSE_JSON_TO_ARRAY).setDisplayName(i18n("parse_json_to_array")).setScope(scope)
                .setHelpSummary(i18n("parse_json_to_array_help"))
                .setHelpPath("functions." + FunctionConstants.PARSE_JSON_TO_ARRAY)
                .setIconPath(format(FunctionsSeed.iconPath, "parse-json-to-array"))
                .setEngineType(EngineType.FUNCTION).setOutputType(StringType.VALUE).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("string"),
                        false)))
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setConfiguration(configuration);
    }

    public static FunctionDefinition parseJsonToObject(Scope scope) {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("input")
                        .setDatatype(TextareaType.VALUE)
                        .setRequired(true)
                        .setLabel("input_json_label")
                        .setHelpSummary("input_json_help")
                        .setHelpText("input_json_help")
                        .setDefaultValue("")
        );
        return new FunctionDefinition().setName(FunctionConstants.PARSE_JSON_TO_OBJECT).setDisplayName(i18n("parse_json_to_object")).setScope(scope)
                .setHelpSummary(i18n("parse_json_to_object_help"))
                .setHelpPath("functions." + FunctionConstants.PARSE_JSON_TO_OBJECT)
                .setIconPath(format(FunctionsSeed.iconPath, "parse-json-to-object"))
                .setEngineType(EngineType.FUNCTION).setOutputType(new ListType()).setType(Type.BUILT_IN)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("string"),
                        false)))
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setConfiguration(configuration);
    }

    public static FunctionDefinition parseJsonToArrayOnEntity() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("input")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("input_json_label")
                        .setHelpSummary("input_json_help")
                        .setHelpText("input_json_help")
                        .setDefaultValue("")
        );
        return new FunctionDefinition().setName(FunctionConstants.PARSE_JSON_TO_ARRAY_ON_ENTITY).setDisplayName(i18n("parse_json_to_array")).setScope(Scope.ENTITY)
                .setHelpSummary(i18n("parse_json_to_array_help"))
                .setHelpPath("functions." + FunctionConstants.PARSE_JSON_TO_ARRAY)
                .setIconPath(format(FunctionsSeed.iconPath, "parse-json-to-array"))
                .setEngineType(EngineType.FUNCTION).setOutputType(StringType.VALUE).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("string"),
                        false)))
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setConfiguration(configuration);
    }

    public static FunctionDefinition parseJsonToObjectOnEntity() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("input")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("input_json_label")
                        .setHelpSummary("input_json_help")
                        .setHelpText("input_json_help")
                        .setDefaultValue("")
        );
        return new FunctionDefinition().setName(FunctionConstants.PARSE_JSON_TO_OBJECT_ON_ENTITY).setDisplayName(i18n("parse_json_to_object")).setScope(Scope.ENTITY)
                .setHelpSummary(i18n("parse_json_to_object_help"))
                .setHelpPath("functions." + FunctionConstants.PARSE_JSON_TO_OBJECT)
                .setIconPath(format(FunctionsSeed.iconPath, "parse-json-to-object"))
                .setEngineType(EngineType.FUNCTION).setOutputType(new ListType()).setType(Type.BUILT_IN)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("string"),
                        false)))
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setConfiguration(configuration);
    }

    public static FunctionDefinition convertToJSONStringOnEntity() {
        return convertToJSONString(FunctionConstants.CONVERT_TO_JSON_STRING_ON_ENTITY, Scope.ENTITY);
    }

    public static FunctionDefinition convertToJSONStringOnField() {
        return convertToJSONString(FunctionConstants.CONVERT_TO_JSON_STRING_ON_FIELD, Scope.ATTRIBUTE);
    }

    public static FunctionDefinition convertToJSONString(String functionName, Scope scope) {
        List<FunctionConfiguration> configuration = List.of(
                new FunctionConfiguration()
                        .setName("input")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("convert_to_json_string_input_label")
                        .setHelpSummary("convert_to_json_string_input_help")
                        .setHelpText("convert_to_json_string_input_help")
                        .setDefaultValue("")
        );
        return new FunctionDefinition().setName(functionName)
                .setDisplayName("convert_to_json_string_label")
                .setScope(scope)
                .setHelpSummary("convert_to_json_string_help")
                .setHelpPath("functions.convertToJSONString")
                .setIconPath(format(FunctionsSeed.iconPath, "convert-to-json-string"))
                .setEngineType(EngineType.FUNCTION).setOutputType(new ListType()).setType(Type.BUILT_IN)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("string"),
                        false)))
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setConfiguration(configuration);
    }


}
