package com.syncari.core.functions;

import com.syncari.core.datatype.*;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.Parameter;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import com.syncari.utils.KeyValue;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

public class FunctionsSeed {
    public static Map<String, FunctionDefinition> attribDefMap = new HashMap<>();
    public static Map<String, FunctionDefinition> entityDefMap = new HashMap<>();
    public static final String iconPath = "/assets/icons/functions/%s.svg";

    static {
        attribDefMap.put(FunctionConstants.CONCATENATE, TextFunctionsSeed.getConcat());
        attribDefMap.put(FunctionConstants.LPAD, TextFunctionsSeed.getLPad());
        attribDefMap.put(FunctionConstants.RPAD, TextFunctionsSeed.getRPad());
        attribDefMap.put(FunctionConstants.INDEXOF, TextFunctionsSeed.getIndexOf());
        attribDefMap.put(FunctionConstants.DAY_OF_WEEK, DateFunctionsSeed.dayOfWeek());
        attribDefMap.put(FunctionConstants.DAY_OF_MONTH, DateFunctionsSeed.dayOfMonth());
        attribDefMap.put(FunctionConstants.DAY_OF_YEAR, DateFunctionsSeed.dayOfYear());
        attribDefMap.put(FunctionConstants.IS_AFTER_NOW, DateFunctionsSeed.isAfterNow());
        attribDefMap.put(FunctionConstants.IS_BEFORE_NOW, DateFunctionsSeed.isBeforeNow());
        attribDefMap.put(FunctionConstants.DATE_FORMAT, DateFunctionsSeed.dateFormat());
        attribDefMap.put(FunctionConstants.NOW, DateFunctionsSeed.now());
        attribDefMap.put(FunctionConstants.DATE_MINUS, DateFunctionsSeed.minus());
        attribDefMap.put(FunctionConstants.DATE_PLUS, DateFunctionsSeed.plus());
        attribDefMap.put(FunctionConstants.DATE_PARSE, DateFunctionsSeed.parse());
        attribDefMap.put(FunctionConstants.LOOKUP_REF_DATA, lookupReferenceDataSeed());
        attribDefMap.put(FunctionConstants.ENRICH_PERSON, enrichPersonSeed());
        attribDefMap.put(FunctionConstants.ENRICH_COMPANY, enrichCompanySeed());
        attribDefMap.put(FunctionConstants.SET_VALUE, setValueSeed());
        attribDefMap.put(FunctionConstants.DATE_DIFF, DateFunctionsSeed.dateDiff());
        attribDefMap.put(FunctionConstants.EXTRACT_TEXT, TextFunctionsSeed.extractText());
        attribDefMap.put(FunctionConstants.MD5_TEXT, TextFunctionsSeed.getMD5Hash());
        attribDefMap.put(FunctionConstants.PARSE_JSON_TO_ARRAY, JsonFunctionsSeed.parseJsonToArray(Scope.ATTRIBUTE));
        attribDefMap.put(FunctionConstants.PARSE_JSON_TO_OBJECT, JsonFunctionsSeed.parseJsonToObject(Scope.ATTRIBUTE));
        addToMap(attribDefMap, MathFunctionsSeed.getAbs());
        addToMap(attribDefMap, MathFunctionsSeed.getRandom());
        addToMap(attribDefMap, MathFunctionsSeed.getMax());
        addToMap(attribDefMap, MathFunctionsSeed.getMin());
        addToMap(attribDefMap, MathFunctionsSeed.getFloor());
        addToMap(attribDefMap, MathFunctionsSeed.getCeil());
        addToMap(attribDefMap, MathFunctionsSeed.getIncrement());
        addToMap(attribDefMap, MathFunctionsSeed.getDecrement());
        addToMap(attribDefMap, MathFunctionsSeed.getRound());
        addToMap(attribDefMap, MathFunctionsSeed.getMultiply());
        addToMap(attribDefMap, MathFunctionsSeed.getComputeRatio());
        addToMap(attribDefMap, TextFunctionsSeed.extractDomainOnField());
        addToMap(attribDefMap, TextFunctionsSeed.getCamelCase());
        addToMap(attribDefMap, TextFunctionsSeed.getCapitalize());
        addToMap(attribDefMap, TextFunctionsSeed.getLength());
        addToMap(attribDefMap, TextFunctionsSeed.getLower());
        addToMap(attribDefMap, TextFunctionsSeed.getUpper());
        addToMap(attribDefMap, TextFunctionsSeed.getLTrim());
        addToMap(attribDefMap, TextFunctionsSeed.getRTrim());
        addToMap(attribDefMap, TextFunctionsSeed.getMask());
        addToMap(attribDefMap, TextFunctionsSeed.getNumberFormat());
        addToMap(attribDefMap, TextFunctionsSeed.getPhoneNumberFormat());
        addToMap(attribDefMap, TextFunctionsSeed.getRemoveNonPrintable());
        addToMap(attribDefMap, TextFunctionsSeed.getStripTags());
        addToMap(attribDefMap, TextFunctionsSeed.getSubstring());
        addToMap(attribDefMap, TextFunctionsSeed.getTrim());
        addToMap(attribDefMap, TextFunctionsSeed.getReverseString());

        addToMap(attribDefMap, SimilarWebFunctionsSeed.getTrafficMetrics());
        addToMap(attribDefMap, SalesIntelFunctionsSeed.getSalesIntelPersonMetric());
        addToMap(attribDefMap, SalesIntelFunctionsSeed.getSalesIntelCompanyMetric());
        addToMap(attribDefMap, ApexAnalytixFunctionsSeed.getApexAnalytixCompanyEnrich());
        addToMap(attribDefMap, AidentifiedFunctionsSeed.getAidentifiedCompanyEnrich());
        addToMap(attribDefMap, lookupSyncariRecordOnFieldSeed());
        addToMap(attribDefMap, updateSyncariRecordsSeedOnField());
        addToMap(attribDefMap, lookupExternalRecordSeed(Scope.ATTRIBUTE));
        addToMap(attribDefMap, filterOnAttributeSeed());
        addToMap(attribDefMap, isFalseAttributeSeed());
        addToMap(attribDefMap, isTrueAttributeSeed());
        addToMap(attribDefMap, loopSeed(Scope.ATTRIBUTE));
        addToMap(attribDefMap, afterSeed(Scope.ATTRIBUTE));
        addToMap(attribDefMap, forEachSeed(Scope.ATTRIBUTE));
        addToMap(attribDefMap, endLoopSeed(Scope.ATTRIBUTE));
        addToMap(attribDefMap, predicateNodeAttributeSeed());
        addToMap(attribDefMap, ListFunctionsSeed.join());
        addToMap(attribDefMap, ListFunctionsSeed.reverseList());
        addToMap(attribDefMap, ListFunctionsSeed.sort());
        addToMap(attribDefMap, ListFunctionsSeed.split());
        addToMap(attribDefMap, ListFunctionsSeed.addToList());
        addToMap(attribDefMap, ListFunctionsSeed.removeFromList());
        addToMap(attribDefMap, ListFunctionsSeed.removeDuplicates());
        addToMap(attribDefMap, ListFunctionsSeed.findInList());
        addToMap(attribDefMap, ListFunctionsSeed.getListItem());

        addToMap(attribDefMap, setFieldsSeed());
        addToMap(attribDefMap, findValueSeed());
        addToMap(attribDefMap, TextFunctionsSeed.replaceFunction());
        addToMap(attribDefMap, insertSyncariRecordSeedOnField());
        addToMap(attribDefMap, AggregateFunctionsSeed.sumRecordOnFieldSeed());
        addToMap(attribDefMap, AggregateFunctionsSeed.countRecordsOnFieldSeed());
        addToMap(attribDefMap, AggregateFunctionsSeed.avgRecordOnFieldSeed());
        addToMap(attribDefMap, AggregateFunctionsSeed.stdDevRecordOnFieldSeed());
        addToMap(attribDefMap, decodeSeed());
        addToMap(attribDefMap, encodeSeed());
        addToMap(attribDefMap, decryptSeed());
        addToMap(attribDefMap, encryptSeed());
        addToMap(attribDefMap, uuidSeed());
        addToMap(attribDefMap, urlEncodeSeed());
        addToMap(attribDefMap, isEmptySeed());
        addToMap(attribDefMap, lastSeed());
        addToMap(attribDefMap, lookupDatasetRecordsSeed("lookupDatasetOnField", Scope.ATTRIBUTE));
        addToMap(attribDefMap, caseSeed(Scope.ATTRIBUTE));
        addToMap(attribDefMap, caseBranchSeed(Scope.ATTRIBUTE));
        addToMap(attribDefMap, autoIncrement(Scope.ATTRIBUTE, FunctionConstants.AUTO_INCREMENT));
        addToMap(attribDefMap, TextFunctionsSeed.charAt());
        addToMap(attribDefMap, JsonFunctionsSeed.convertToJSONStringOnField());
        addToMap(attribDefMap, TextFunctionsSeed.getJWTToken());

        addToMap(entityDefMap, DateFunctionsSeed.nowOnEntity());
        addToMap(entityDefMap, lookupReferenceDataOnEntitySeed());
        addToMap(entityDefMap, TextFunctionsSeed.getLengthOnEntity());
        addToMap(entityDefMap, TextFunctionsSeed.getSubstringOnEntity());
        addToMap(entityDefMap, TextFunctionsSeed.splitOnEntity());
        addToMap(entityDefMap, TextFunctionsSeed.lowerOnEntity());
        addToMap(entityDefMap, TextFunctionsSeed.upperOnEntity());
        addToMap(entityDefMap, TextFunctionsSeed.charAtOnEntity());
        addToMap(entityDefMap, TextFunctionsSeed.getJWTTokenOnEntity());
        addToMap(entityDefMap, MathFunctionsSeed.getFloorOnEntity());
        addToMap(entityDefMap, MathFunctionsSeed.getCeilOnEntity());
        addToMap(entityDefMap, MathFunctionsSeed.getRandomOnEntity());
        addToMap(entityDefMap, MathFunctionsSeed.getMultiplyOnEntity());
        addToMap(entityDefMap, MathFunctionsSeed.getIncrementOnEntity());
        addToMap(entityDefMap, attachRecordSeed());
        addToMap(entityDefMap, advancedAttachRecordSeed());
        addToMap(entityDefMap, setValueOnEntitySeed());
        addToMap(entityDefMap, TextFunctionsSeed.extractDomainOnEntity());
        addToMap(entityDefMap, lookupSyncariRecordSeed());
        addToMap(entityDefMap, updateSyncariRecordsSeed());
        addToMap(entityDefMap, filterOnEntitySeed());
        addToMap(entityDefMap, caseSeed(Scope.ENTITY));
        addToMap(entityDefMap, caseBranchSeed(Scope.ENTITY));
        addToMap(entityDefMap, isFalseEntitySeed());
        addToMap(entityDefMap, isTrueEntitySeed());
        addToMap(entityDefMap, predicateNodeEntitySeed());
        addToMap(entityDefMap, firstOnEntitySeed());
        addToMap(entityDefMap, TextFunctionsSeed.replaceOnEntityFunction());
        addToMap(entityDefMap, insertSyncariRecordSeed());
        addToMap(entityDefMap, AggregateFunctionsSeed.sumRecordSeed());
        addToMap(entityDefMap, AggregateFunctionsSeed.countRecordsSeed());
        addToMap(entityDefMap, AggregateFunctionsSeed.avgRecordSeed());
        addToMap(entityDefMap, AggregateFunctionsSeed.stdDevRecordSeed());
        addToMap(entityDefMap, DateFunctionsSeed.dateDiffOnEntity());
        addToMap(entityDefMap, TextFunctionsSeed.getMD5HashOnEntity());
        addToMap(entityDefMap, JsonFunctionsSeed.parseJsonToArrayOnEntity());
        addToMap(entityDefMap, JsonFunctionsSeed.parseJsonToObjectOnEntity());
        addToMap(entityDefMap, JsonFunctionsSeed.convertToJSONStringOnEntity());
        addToMap(entityDefMap, lookupExternalRecordSeed(Scope.ENTITY));
        addToMap(entityDefMap, loopSeed(Scope.ENTITY));
        addToMap(entityDefMap, afterSeed(Scope.ENTITY));
        addToMap(entityDefMap, forEachSeed(Scope.ENTITY));
        addToMap(entityDefMap, endLoopSeed(Scope.ENTITY));
        addToMap(entityDefMap, autoIncrementEntity(FunctionConstants.AUTO_INCREMENT_ON_ENTITY));
        addToMap(entityDefMap, lookupDatasetRecordsSeed("lookupDataset", Scope.ENTITY));
        addToMap(entityDefMap, TextFunctionsSeed.getPhoneNumberFormatOnEntity());
    }

    private static FunctionDefinition firstOnEntitySeed() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("newValue").setDatatype(new StringType()).setLabel("Value")
                        .setDefaultValue("").setRequired(true).setAdditionalProperties(Map.of())
                        .setHelpSummary("firstOnEntity_function_newValue_help")
        );

        return new FunctionDefinition()
                .setName("firstOnEntity").setDisplayName("firstOnEntity_function_label").setScope(Scope.ENTITY)
                .setHelpSummary("firstOnEntity_function_help").setIconPath(format(FunctionsSeed.iconPath, "first"))
                .setHelpPath("functions.firstOnEntity")
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(ObjectType.VALUE).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("entity", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition firstOnAttributeSeed() {

        return new FunctionDefinition()
                .setName("first").setDisplayName("first_function_label").setScope(Scope.ATTRIBUTE)
                .setHelpSummary("first_function_help").setIconPath(format(FunctionsSeed.iconPath, "first"))
                .setHelpPath("functions.first")
                .setEngineType(EngineType.FUNCTION).setConfiguration(List.of())
                .setOutputType(ObjectType.VALUE).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("values", DatatypeFactory.getDatatype("list"), false)));
    }

    private static void addToMap( Map<String, FunctionDefinition> functionMap ,FunctionDefinition functionDefinition) {
        functionMap.put(functionDefinition.getName(), functionDefinition);
    }

    public static FunctionDefinition populateFunction(FunctionDefinition f){
        FunctionDefinition fromSeed = FunctionsSeed.get(f.getName(), f.getScope());

        if(fromSeed != null) {
            f.setName(fromSeed.getName()).setDisplayName(fromSeed.getDisplayName()).setScope(fromSeed.getScope())
                    .setHelpSummary(fromSeed.getHelpSummary()).setIconPath(fromSeed.getIconPath())
                    .setHelpPath(fromSeed.getHelpPath())
                    .setEngineType(fromSeed.getEngineType())
                    .setOutputType(fromSeed.getOutputType()).setType(fromSeed.getType())
                    .setPositionalParams(fromSeed.getPositionalParams())
                    .setAdditionalInputTypes(fromSeed.getAdditionalInputTypes())
                    .setHidden(fromSeed.isHidden())
                    .setDynamicConfig(fromSeed.isDynamicConfig())
                    .setConfiguration(fromSeed.getConfiguration());
        }
        return f;
    }

    public static FunctionDefinition get(String name, Scope scope) {
        if(Scope.ENTITY.equals(scope)){
            return entityDefMap.get(name);
        }
        return attribDefMap.get(name);
    }

    private static FunctionDefinition setValueOnEntitySeed(){
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("attributeDefinitionId").setDatatype(new PicklistType()).setLabel("Field Name")
                        .setDefaultValue("").setAdditionalProperties(Map.of()).setRequired(false),
                new FunctionConfiguration().setName("dataType").setDatatype(new PicklistType()).setLabel("Data Type")
                        .setHelpSummary("setValue_datatype_func")
                        .setDefaultValue("").setAdditionalProperties(Map.of("values", getSupportedDataTypes())).setRequired(false),
                new FunctionConfiguration().setName("setValueField").setDatatype(new CompositeType()).setLabel("Select field")
                        .setReadOnly(false).setRequired(false).setHelpSummary(i18n("set_value_on_entity_field_help"))
                        .setAdditionalProperties(Map.of("renderType","setValueField")),
                new FunctionConfiguration().setName("newValue").setDatatype(new TextareaType()).setLabel("New Value")
                        .setDefaultValue("").setRequired(false).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("useEmpty").setDatatype(new BooleanType())
                        .setLabel("setValueOnEntity_use_empty_label")
                        .setHelpSummary("setValueOnEntity_use_empty_help")
                        .setDefaultValue(false)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true))
        );

        return new FunctionDefinition()
                .setName(FunctionConstants.SET_VALUE_ON_ENTITY).setDisplayName("Set Value").setScope(Scope.ENTITY)
                .setHelpSummary(i18n("setValueOnEntity_func_help")).setIconPath(format(FunctionsSeed.iconPath, "default"))
                .setHelpPath("functions.setValueOnEntity")
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new StringType()).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setPositionalParams(List.of(new Parameter("entity", DatatypeFactory.getDatatype("object"), false)))
                .setAdditionalInputTypes(List.of(ListType.VALUE));
    }

    private static FunctionDefinition setValueSeed(){
        // TODO: remove static population of Data Type and make it metadata driven in UI
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("dataType").setDatatype(new PicklistType()).setLabel("Data Type")
                        .setHelpSummary("setValue_datatype_func")
                        .setDefaultValue("").setAdditionalProperties(Map.of("values", getSupportedDataTypes())).setRequired(false),
                new FunctionConfiguration().setName("setValueField").setDatatype(new CompositeType()).setLabel("Set field")
                        .setReadOnly(false).setRequired(false).setHelpSummary(i18n("set_value_field_help"))
                        .setAdditionalProperties(Map.of("renderType","setValueField1")),
                new FunctionConfiguration().setName("newValue").setDatatype(new TextareaType()).setLabel("New Value")
                        .setDefaultValue("").setRequired(false)
                        .setHelpSummary("setValue_newvalue_func")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "datatype", "dependantField", "configuration.dataType"))),
                new FunctionConfiguration().setName("useEmpty").setDatatype(new BooleanType())
                        .setLabel("setValue_use_empty_label")
                        .setHelpSummary("setValue_use_empty_help")
                        .setDefaultValue(false)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true))
        );

        return new FunctionDefinition()
                .setName(FunctionConstants.SET_VALUE).setDisplayName("Set Value").setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("setValue_func_help")).setIconPath(format(FunctionsSeed.iconPath, "default"))
                .setHelpPath("functions." + FunctionConstants.SET_VALUE)
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new StringType()).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setPositionalParams(List.of(new Parameter("first", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition findValueSeed(){
        // TODO: remove static population of Data Type and make it metadata driven in UI
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("fieldName").setDatatype(new StringType()).setLabel("Field Name")
                        .setHelpSummary("findValue_field_name_help")
                        .setAdditionalProperties(Map.of())
        );

        return new FunctionDefinition()
                .setName(FunctionConstants.FIND_VALUE).setDisplayName("Find Value").setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("findValue_func_help")).setIconPath(format(FunctionsSeed.iconPath, "default"))
                .setHelpPath("functions." + FunctionConstants.FIND_VALUE)
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(ObjectType.VALUE).setType(Type.STANDARD)
                //only available on child type fields
                .setDynamicConfig(true)
                .setPositionalParams(List.of(new Parameter("first", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition setFieldsSeed(){

        List<FunctionConfiguration> setFieldsConfig = List.of(
                new FunctionConfiguration().setName("setField").setDatatype(new PicklistType())
                        .setLabel("Field Name")
                        .setHelpSummary("setFields_field_name_help")
                        .setAdditionalProperties(Map.of(
                                "fieldSet","setFields",
                                "mapping", new KeyValue("graphKey","configuration.setField").set("configKey","value"),
                                "id","setField",
                                "name","setField"

                        )),
                new FunctionConfiguration().setName("fieldValue").setDatatype(new ObjectType())
                        .setLabel("Value")
                        .setHelpSummary("setFields_field_value_help")
                        .setAdditionalProperties(Map.of(
                                "fieldSet","setFields",
                                "mapping", new KeyValue("graphKey","configuration.fieldValue").set("configKey","value"),
                                "id","fieldValue",
                                "name","fieldValue",
                                "renderType","tokens"
                        ))

        );
        List<FunctionConfiguration> setFieldsConfigurations = List.of(
                new FunctionConfiguration().setName("rejectEmpty").setDatatype(new BooleanType())
                        .setLabel("update_records_reject_empty_label")
                        .setHelpSummary("update_records_reject_empty_help")
                        .setHelpText("update_records_reject_empty_help").setDefaultValue(true)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration().setName("setFields").setDatatype(new CompositeType())
                        .setLabel(i18n("setFields_fields_label"))
                        .setHelpSummary(i18n("setFields_fields_help"))
                        .setHelpText(i18n("setFields_fields_help"))
                        .setDefaultValue("").setRequired(false)
                        .setAdditionalProperties(Map.of("layout","row","graphKey","configuration.setFields","configKey","value","repeatable",true))
                        .setConfiguration(setFieldsConfig)

        );

        return new FunctionDefinition()
                .setName(FunctionConstants.SET_FIELD_VALUES).setDisplayName("Set Field Values").setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("setFields_func_help")).setIconPath(format(FunctionsSeed.iconPath, "default"))
                .setHelpPath("functions." + FunctionConstants.SET_FIELD_VALUES)
                .setEngineType(EngineType.FUNCTION).setConfiguration(setFieldsConfigurations)
                .setOutputType(ObjectType.VALUE).setType(Type.STANDARD)
                //only available on child type fields
                .setAvailableForDataTypes(Set.of(ChildType.VALUE))
                .setDynamicConfig(true)
                .setPositionalParams(List.of(new Parameter("first", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition lookupReferenceDataSeed(){
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("datasetId").setDatatype(new ReferenceType()).setLabel("Dataset")
                        .setHelpSummary(i18n("lookupRefData_dataset_func_help"))
                        .setDefaultValue("").setAdditionalProperties(Map.of("source","ReferenceData","hideTokenPicker", true)).setRequired(true),
                new FunctionConfiguration().setName("lookUpKey").setDatatype(new PicklistType()).setLabel("Lookup Key")
                        .setHelpSummary(i18n("lookupRefData_lookupkey_func_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "ReferenceData", "dependantField", "configuration.datasetId"))),
                new FunctionConfiguration().setName("destinationFieldName").setDatatype(new PicklistType()).setLabel("Destination Field")
                        .setHelpSummary(i18n("lookupRefData_destination_func_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "ReferenceData", "dependantField", "configuration.datasetId"))),
                new FunctionConfiguration().setName("defaultValue").setDatatype(new StringType()).setLabel("Default Value")
                        .setHelpSummary(i18n("lookupRefData_defaultValue_func_help"))
                        .setDefaultValue("").setAdditionalProperties(Map.of()).setRequired(false),
                new FunctionConfiguration().setName("ignoreCase").setDatatype(new BooleanType()).setLabel("Ignore Case")
                        .setDefaultValue("").setAdditionalProperties(Map.of("hideTokenPicker", true)).setRequired(false),
                new FunctionConfiguration().setName("operator").setDatatype(new PicklistType()).setLabel("Operator")
                        .setDefaultValue("exactMatch").setAdditionalProperties(Map.of("values", getRefLookupOperator())).setRequired(false)

        );

        return new FunctionDefinition()
                .setName(FunctionConstants.LOOKUP_REF_DATA).setDisplayName("Lookup Reference Data").setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("lookupRefData_func_help")).setIconPath(format(FunctionsSeed.iconPath, "lookup-reference-data"))
                .setHelpPath("functions." + FunctionConstants.LOOKUP_REF_DATA)
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new StringType()).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("lookUpValue", DatatypeFactory.getDatatype("string"), false)));
    }

    private static FunctionDefinition lookupReferenceDataOnEntitySeed(){
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("lookUpValue").setDatatype(new StringType()).setLabel("Lookup Value")
                        .setHelpSummary(i18n("lookupRefData_lookUpValue_func_help"))
                        .setAdditionalProperties(Map.of()).setRequired(true),
                new FunctionConfiguration().setName("datasetId").setDatatype(new ReferenceType()).setLabel("Dataset")
                        .setHelpSummary(i18n("lookupRefData_dataset_func_help"))
                        .setDefaultValue("").setAdditionalProperties(Map.of("source","ReferenceData","hideTokenPicker", true)).setRequired(true),
                new FunctionConfiguration().setName("lookUpKey").setDatatype(new PicklistType()).setLabel("Lookup Key")
                        .setHelpSummary(i18n("lookupRefData_lookupkey_func_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "ReferenceData", "dependantField", "configuration.datasetId"))),
                new FunctionConfiguration().setName("destinationFieldName").setDatatype(new PicklistType()).setLabel("Destination Field")
                        .setHelpSummary(i18n("lookupRefData_destination_func_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "ReferenceData", "dependantField", "configuration.datasetId"))),
                new FunctionConfiguration().setName("defaultValue").setDatatype(new StringType()).setLabel("Default Value")
                        .setHelpSummary(i18n("lookupRefData_defaultValue_func_help"))
                        .setDefaultValue("").setAdditionalProperties(Map.of()).setRequired(false),
                new FunctionConfiguration().setName("ignoreCase").setDatatype(new BooleanType()).setLabel("Ignore Case")
                        .setDefaultValue("").setAdditionalProperties(Map.of("hideTokenPicker", true)).setRequired(false),
                new FunctionConfiguration().setName("operator").setDatatype(new PicklistType()).setLabel("Operator")
                        .setDefaultValue("exactMatch").setAdditionalProperties(Map.of("values", getRefLookupOperator())).setRequired(false)

        );

        return new FunctionDefinition()
                .setName(FunctionConstants.LOOKUP_REF_DATA_ON_ENTITY).setDisplayName("Lookup Reference Data").setScope(Scope.ENTITY)
                .setHelpSummary(i18n("lookupRefData_func_help")).setIconPath(format(FunctionsSeed.iconPath, "lookup-reference-data"))
                .setHelpPath("functions." + FunctionConstants.LOOKUP_REF_DATA)
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new StringType()).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("lookUpValue", DatatypeFactory.getDatatype("object"), false)));
    }

    private static List<KeyValue> getRefLookupOperator() {
        return List.of(
                new KeyValue("label",i18n("lookupRef_exactMatch")).set("value",LookupReferenceDataFunction.EXACTMATCH),
                new KeyValue("label",i18n("lookupRef_Contains")).set("value",LookupReferenceDataFunction.CONTAINS),
                new KeyValue("label",i18n("lookupRef_In")).set("value", LookupReferenceDataFunction.IN)
        );
    }

    private static FunctionDefinition attachRecordSeed(){
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("externalEntityDefId").setDatatype(new PicklistType()).setLabel("Link Record of Type")
                        .setDefaultValue("").setAdditionalProperties(Map.of("type","SyncariEntity")).setRequired(true),
                new FunctionConfiguration().setName("syncariEntityDefId").setDatatype(new PicklistType()).setLabel("Syncari Entity")
                        .setDefaultValue("").setAdditionalProperties(Map.of("type","SyncariEntity")).setRequired(true),
                new FunctionConfiguration().setName("searchFieldId").setDatatype(new PicklistType()).setLabel("By Matching On")
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"))),
                new FunctionConfiguration().setName("inputFieldId").setDatatype(new PicklistType()).setLabel("With Input Field")
                        .setDefaultValue("").setAdditionalProperties(Map.of()).setRequired(true)
        );

        return new FunctionDefinition()
                .setName(FunctionConstants.ATTACH_RECORD).setDisplayName("[X] Attach Record").setScope(Scope.ENTITY)
                .setHelpSummary(i18n("attachRecord_func_help_deprecated")).setIconPath(format(FunctionsSeed.iconPath, "attach-record"))
                .setHelpPath("")
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setHidden(true)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition advancedAttachRecordSeed(){
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("attachPredicate").setDatatype(new PredicateType())
                        .setLabel(i18n("lookup_attach_record"))
                        .setHelpSummary(i18n("lookup_attach_record_help"))
                        .setHelpText(i18n("lookup_attach_record_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields")),
                new FunctionConfiguration().setName("field").setDatatype(new PicklistType()).setLabel("Field")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields")),
                new FunctionConfiguration().setName("operator").setDatatype(new PicklistType()).setLabel("Operator")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields","dependsOn", Map.of("dependantType", "Operator", "dependantField", "configuration.field"))),
                new FunctionConfiguration().setName("value").setDatatype(new StringType()).setLabel("Value")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields"))
        );

        return new FunctionDefinition()
                .setName(FunctionConstants.ADVANCED_ATTACH_RECORD).setDisplayName("Attach Record").setScope(Scope.ENTITY)
                .setHelpSummary(i18n("attachRecord_func_help")).setIconPath(format(FunctionsSeed.iconPath, "attach-record"))
                .setHelpPath("functions.advancedAttachRecord")
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .addContextVariable("allPreviousLookupRecords", ObjectType.VALUE, true)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition lookupSyncariRecordSeed(){
        List<FunctionConfiguration> sortConfigurations = List.of(
                new FunctionConfiguration().setName("sortField").setDatatype(new PicklistType())
                        .setLabel("Sort Field")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"),
                                "fieldSet","sort",
                                "mapping", new KeyValue("graphKey","configuration.sortField").set("configKey","value"),
                                "id","sortField",
                                "name","sortField"

                        )),
                new FunctionConfiguration().setName("sortDirection").setDatatype(new PicklistType())
                        .setLabel("Sort Direction")
                        .setAdditionalProperties(Map.of("values", List.of(new KeyValue("label","Ascending").set("value","asc"),new KeyValue("label","Descending").set("value","desc")),
                                "fieldSet","sort",
                                "mapping", new KeyValue("graphKey","configuration.sortDirection").set("configKey","value"),
                                "id","sortDirection",
                                "name","sortDirection"
                        ))

        );

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("syncariEntityDefId").setDatatype(new PicklistType())
                        .setLabel(i18n("lookup_record_entity_label"))
                        .setHelpSummary(i18n("lookup_record_entity_help"))
                        .setHelpText(i18n("lookup_record_entity_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("type","SyncariEntity")),

                new FunctionConfiguration().setName("predicate").setDatatype(new PredicateType())
                        .setLabel(i18n("lookup_record_predicate_label"))
                        .setHelpSummary(i18n("lookup_record_predicate_help"))
                        .setHelpText(i18n("lookup_record_predicate_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of( "fieldSet", "conditionFields")),
                new FunctionConfiguration().setName("dontMatchBlank").setDatatype(new BooleanType())
                        .setLabel("lookup_record_use_empty_label")
                        .setHelpSummary("lookup_record_use_empty_help")
                        .setDefaultValue(false)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration().setName("field").setDatatype(new PicklistType()).setLabel("Field")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"), "fieldSet","conditionFields")),
                new FunctionConfiguration().setName("operator").setDatatype(new PicklistType()).setLabel("Operator")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields","dependsOn", Map.of("dependantType", "Operator", "dependantField", "configuration.field"))),
                new FunctionConfiguration().setName("value").setDatatype(new StringType()).setLabel("Value")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields")),
                new FunctionConfiguration().setName("sortFields").setDatatype(new CompositeType())
                        .setLabel(i18n("lookup_record_sort_field_label"))
                        .setHelpSummary(i18n("lookup_record_sort_field_help"))
                        .setHelpText(i18n("lookup_record_sort_field_help"))
                        .setDefaultValue(List.of()).setRequired(false)
                        .setAdditionalProperties(Map.of("layout","row","graphKey","configuration.sortKeys","configKey","value","repeatable",true))
                        .setConfiguration(sortConfigurations),
                new FunctionConfiguration().setName("count").setDatatype(new BooleanType()).setLabel("lookup_record_count_label").setHelpSummary("lookup_record_count_help").setHelpText("lookup_record_count_help")
                        .setDefaultValue(false).setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration().setName("findAll").setDatatype(new BooleanType()).setLabel("lookup_record_findAll_label")
                        .setHelpText("lookup_record_findAll_help").setHelpSummary("lookup_record_findAll_help")
                        .setDefaultValue(false)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true))
        );
        return new FunctionDefinition()
                .setName(FunctionConstants.LOOKUP_SYNCARI_RECORD_ON_ENTITY).setDisplayName(i18n("lookupRecord_func_label")).setScope(Scope.ENTITY)
                .setHelpSummary(i18n("lookupRecord_func_help")).setIconPath(format(FunctionsSeed.iconPath, "look-up-syncari-record"))
                .setHelpPath("functions.advancedLookUpSyncariRecord")
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition lookupDatasetRecordsSeed(String functionName, Scope scope) {
        List<FunctionConfiguration> configuration = List.of(
                new FunctionConfiguration().setName("datasetId").setDatatype(new PicklistType())
                        .setLabel("lookup_dataset_dataset_id_label")
                        .setHelpSummary("lookup_dataset_dataset_id_help")
                        .setHelpText("lookup_dataset_dataset_id_help_text")
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("hasAdditionalConfig", true, "additionalConfigParams",Map.of("configLoaderType", "dataset"))),
                new FunctionConfiguration().setName("limit").setDatatype(new IntegerType()).setLabel("lookup_dataset_dataset_limit_label").setHelpSummary("lookup_dataset_dataset_limit_help").setHelpText("lookup_dataset_dataset_limit_help")
                        .setDefaultValue(1000)

        );
        return new FunctionDefinition()
                .setName(functionName).setDisplayName("lookup_dataset_display_name").setScope(scope)
                .setHelpSummary("lookup_dataset_help").setIconPath(format(FunctionsSeed.iconPath, "lookup-dataset-record"))
                .setHelpPath("functions.lookupDataset")
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition lookupSyncariRecordOnFieldSeed() {
        List<FunctionConfiguration> sortConfigurations = List.of(
                new FunctionConfiguration().setName("sortField").setDatatype(new PicklistType())
                        .setLabel("Sort Field")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"),
                                "fieldSet", "sort",
                                "mapping", new KeyValue("graphKey", "configuration.sortField").set("configKey", "value"),
                                "id", "sortField",
                                "name", "sortField"

                        )),
                new FunctionConfiguration().setName("sortDirection").setDatatype(new PicklistType())
                        .setLabel("Sort Direction")
                        .setAdditionalProperties(Map.of("values", List.of(new KeyValue("label","Ascending").set("value","asc"),new KeyValue("label","Descending").set("value","desc")),
                                "fieldSet","sort",
                                "mapping", new KeyValue("graphKey","configuration.sortDirection").set("configKey","value"),
                                "id","sortDirection",
                                "name","sortDirection"
                        ))

        );

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("syncariEntityDefId").setDatatype(new PicklistType())
                        .setLabel(i18n("lookup_record_entity_label"))
                        .setHelpSummary(i18n("lookup_record_entity_help"))
                        .setHelpText(i18n("lookup_record_entity_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("type","SyncariEntity")),

                new FunctionConfiguration().setName("predicate").setDatatype(new PredicateType())
                        .setLabel(i18n("lookup_record_predicate_label"))
                        .setHelpSummary(i18n("lookup_record_predicate_help"))
                        .setHelpText(i18n("lookup_record_predicate_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of( "fieldSet", "conditionFields")),
                new FunctionConfiguration().setName("dontMatchBlank").setDatatype(new BooleanType())
                        .setLabel("lookup_record_use_empty_label")
                        .setHelpSummary("lookup_record_use_empty_help")
                        .setDefaultValue(false)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration().setName("field").setDatatype(new PicklistType()).setLabel("Field")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"), "fieldSet","conditionFields")),
                new FunctionConfiguration().setName("operator").setDatatype(new PicklistType()).setLabel("Operator")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields","dependsOn", Map.of("dependantType", "Operator", "dependantField", "configuration.field"))),
                new FunctionConfiguration().setName("value").setDatatype(new StringType()).setLabel("Value")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields")),
                new FunctionConfiguration().setName("sortFields").setDatatype(new CompositeType())
                        .setLabel(i18n("lookup_record_sort_field_label"))
                        .setHelpSummary(i18n("lookup_record_sort_field_help"))
                        .setHelpText(i18n("lookup_record_sort_field_help"))
                        .setDefaultValue(List.of()).setRequired(false)
                        .setAdditionalProperties(Map.of("layout","row","graphKey","configuration.sortKeys","configKey","value","repeatable",true))
                        .setConfiguration(sortConfigurations),
                new FunctionConfiguration().setName("count").setDatatype(new BooleanType()).setLabel("lookup_record_count_label")
                        .setHelpText("lookup_record_count_help").setHelpSummary("lookup_record_count_help")
                        .setDefaultValue(false)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration().setName("findAll").setDatatype(new BooleanType()).setLabel("lookup_record_findAll_label")
                        .setHelpText("lookup_record_findAll_help").setHelpSummary("lookup_record_findAll_help")
                        .setDefaultValue(false)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true))
        );
        return new FunctionDefinition()
                .setName(FunctionConstants.LOOKUP_SYNCARI_RECORD_ON_FIELD).setDisplayName(i18n("lookupRecord_func_label")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("lookupRecord_func_help")).setIconPath(format(FunctionsSeed.iconPath, "look-up-syncari-record"))
                .setHelpPath("functions." + FunctionConstants.LOOKUP_SYNCARI_RECORD_ON_FIELD)
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }
    private static FunctionDefinition lookupExternalRecordSeed(Scope scope){
    	List<FunctionConfiguration> configuration =  List.of(
    			new FunctionConfiguration().setName("synapseId").setDatatype(new PicklistType())
    			.setLabel(i18n("lookup_external_record_synapse_label"))
    			.setHelpSummary(i18n("lookup_external_record_synapse_help"))
    			.setHelpText(i18n("lookup_external_record_synapse_help"))
    			.setDefaultValue("").setRequired(true)
    			.setAdditionalProperties(Map.of("type","SearchSynapse")),
    			
    			new FunctionConfiguration().setName("query").setDatatype(new StringType())
    			.setLabel(i18n("lookup_external_record_condition_label"))
    			.setHelpSummary(i18n("lookup_external_record_condition_help"))
    			.setHelpText(i18n("lookup_external_record_condition_help"))
    			.setDefaultValue("").setRequired(true)
    			.setAdditionalProperties(Map.of("hideTokenPicker", true)),
    			
    			new FunctionConfiguration().setName("positionalParams").setDatatype(new StringType())
    			.setLabel(i18n("lookup_external_record_positionalParams_label"))
    			.setHelpSummary(i18n("lookup_external_record_positionalParams_help"))
    			.setHelpText(i18n("lookup_external_record_positionalParams_help"))
    			.setDefaultValue("")
    			.setAdditionalProperties(Map.of())
    			);
    	return new FunctionDefinition()
    			.setName(FunctionConstants.LOOKUP_EXTERNAL_RECORD).setDisplayName(i18n("lookupExternalRecord_func_label")).setScope(scope)
    			.setHelpSummary(i18n("lookupExternalRecord_func_help")).setIconPath(format(FunctionsSeed.iconPath, "look-up-external-record"))
    			.setHelpPath("functions." + FunctionConstants.LOOKUP_EXTERNAL_RECORD)
    			.setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
    			.setOutputType(new ObjectType()).setType(Type.STANDARD)
    			.setDynamicConfig(true)
    			.setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }
    private static FunctionDefinition enrichPersonSeed(){
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("serviceId").setDatatype(new PicklistType())
                        .setHelpSummary(i18n("enrichPerson_source_func_help"))
                        .setLabel("Enrichment Source").setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("type","Service", "serviceType", "ENRICH")),
                new FunctionConfiguration().setName("enrichUsing").setDatatype(new PicklistType())
                        .setHelpSummary(i18n("enrichPerson_enrichField_func_help"))
                        .setLabel("Enrich Using").setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "Contact.EnrichUsing", "dependantField", "configuration.serviceId"))),
                new FunctionConfiguration().setName("entityDefinition").setDatatype(new PicklistType()).setLabel("Source Entity")
                        .setDefaultValue("").setRequired(true).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("emailField").setDatatype(new PicklistType()).setLabel("Input Field")
                        .setDefaultValue("").setRequired(true).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("additionalEnrichUsing").setDatatype(new PicklistType()).setLabel("Additional Enrichment Using")
                        .setDefaultValue("").setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("additionalEnrichField").setDatatype(new PicklistType()).setLabel("Additional Enrichment Field")
                        .setDefaultValue("").setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("lookUpKey").setDatatype(new PicklistType()).setLabel("Enrichment Field")
                        .setDefaultValue("").setRequired(true).setAdditionalProperties(Map.of())
        );

        return new FunctionDefinition()
                .setName(FunctionConstants.ENRICH_PERSON).setDisplayName("Enrich Person").setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("enrichPerson_func_help")).setIconPath(format(FunctionsSeed.iconPath, "clearbit-look-up-person"))
                .setHelpPath("functions." + FunctionConstants.ENRICH_PERSON)
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setDynamicConfig(true)
                .setOutputType(new StringType()).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("email", DatatypeFactory.getDatatype("string"), false)));
    }

    private static FunctionDefinition enrichCompanySeed(){
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("serviceId").setDatatype(new PicklistType())
                        .setLabel("Enrichment Source").setDefaultValue("").setRequired(true)
                        .setHelpSummary(i18n("enrichCompany_source_func_help"))
                        .setAdditionalProperties(Map.of("type","Service", "serviceType", "ENRICH")),
                new FunctionConfiguration().setName("enrichUsing").setDatatype(new PicklistType())
                        .setLabel("Enrich Using").setDefaultValue("").setRequired(true)
                        .setHelpSummary(i18n("enrichCompany_enrichField_func_help"))
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "Company.EnrichUsing", "dependantField", "configuration.serviceId"))),
                new FunctionConfiguration().setName("entityDefinition").setDatatype(new PicklistType()).setLabel("Source Entity")
                        .setDefaultValue("").setRequired(true).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("domainField").setDatatype(new PicklistType()).setLabel("Input Field")
                        .setDefaultValue("").setRequired(true).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("lookUpKey").setDatatype(new PicklistType()).setLabel("Enrichment Field")
                        .setDefaultValue("").setRequired(true).setAdditionalProperties(Map.of())
        );

        return new FunctionDefinition()
                .setName(FunctionConstants.ENRICH_COMPANY).setDisplayName("Enrich Company").setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("enrichCompany_func_help")).setIconPath(format(FunctionsSeed.iconPath, "clearbit-look-up-company"))
                .setHelpPath("functions." + FunctionConstants.ENRICH_COMPANY)
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setDynamicConfig(true)
                .setOutputType(new StringType()).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("email", DatatypeFactory.getDatatype("string"), false)));
    }

    private static FunctionDefinition updateSyncariRecordsSeed(){
        String name = "updateSyncariRecords";
        List<FunctionConfiguration> updateFieldConfigurations = List.of(
                new FunctionConfiguration().setName("updateField").setDatatype(new PicklistType())
                        .setLabel("Update Field")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"),
                                "fieldSet","updateFields",
                                "mapping", new KeyValue("graphKey","configuration.updateField").set("configKey","value"),
                                "id","updateField",
                                "name","updateField"

                        )),
                new FunctionConfiguration().setName("newValue").setDatatype(new ObjectType())
                        .setLabel("New Value")
                        .setAdditionalProperties(Map.of(
                                "fieldSet","updateFields",
                                "mapping", new KeyValue("graphKey","configuration.newValue").set("configKey","value"),
                                "id","newValue",
                                "name","newValue",
                                "renderType","tokens"
                        )),
                new FunctionConfiguration().setName("operation").setDatatype(new PicklistType())
                        .setLabel("Operation")
                        .setAdditionalProperties(Map.of(
                                "fieldSet","updateFields",
                                "mapping", new KeyValue("graphKey","configuration.operation").set("configKey","value"),
                                "id","operation",
                                "name","operation",
                                "dependsOn", Map.of("dependantType", "Operation", "dependantField", "configuration.updateField")
                        ))

        );

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("syncariEntityDefId").setDatatype(new PicklistType())
                        .setLabel(i18n("update_records_entity_label"))
                        .setHelpSummary(i18n("update_records_entity_help"))
                        .setHelpText(i18n("update_records_entity_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("type","SyncariEntity")),
                new FunctionConfiguration().setName("rejectEmpty").setDatatype(new BooleanType())
                        .setLabel(i18n("update_records_reject_empty_label"))
                        .setHelpSummary(i18n("update_records_reject_empty_help"))
                        .setHelpText(i18n("update_records_reject_empty_help")).setDefaultValue(true)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),                       

                new FunctionConfiguration().setName("predicate").setDatatype(new PredicateType())
                        .setLabel(i18n("update_records_predicate_label"))
                        .setHelpSummary(i18n("update_records_predicate_help"))
                        .setHelpText(i18n("update_records_predicate_help"))
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
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields")),
                new FunctionConfiguration().setName("updateFields").setDatatype(new CompositeType())
                        .setLabel(i18n("update_records_fields_label"))
                        .setHelpSummary(i18n("update_records_fields_help"))
                        .setHelpText(i18n("update_records_fields_help"))
                        .setDefaultValue("").setRequired(false)
                        .setAdditionalProperties(Map.of("layout","row","graphKey","configuration.updateFields","configKey","value","repeatable",true))
                        .setConfiguration(updateFieldConfigurations)
        );
        return new FunctionDefinition()
                .setName(name).setDisplayName(i18n("update_records_func_label")).setScope(Scope.ENTITY)
                .setHelpSummary(i18n("update_records_func_help")).setIconPath(format(FunctionsSeed.iconPath, "update-record"))
                .setHelpPath("functions.updateSyncariRecords")
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setHidden(true)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition updateSyncariRecordsSeedOnField(){
        String name = "updateSyncariRecordsOnField";
        List<FunctionConfiguration> updateFieldConfigurations = List.of(
                new FunctionConfiguration().setName("updateField").setDatatype(new PicklistType())
                        .setLabel("Update Field")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"),
                                "fieldSet","updateFields",
                                "mapping", new KeyValue("graphKey","configuration.updateField").set("configKey","value"),
                                "id","updateField",
                                "name","updateField"

                        )),
                new FunctionConfiguration().setName("newValue").setDatatype(new ObjectType())
                        .setLabel("New Value")
                        .setAdditionalProperties(Map.of(
                                "fieldSet","updateFields",
                                "mapping", new KeyValue("graphKey","configuration.newValue").set("configKey","value"),
                                "id","newValue",
                                "name","newValue",
                                "renderType","tokens"
                        )),
                new FunctionConfiguration().setName("operation").setDatatype(new PicklistType())
                        .setLabel("Operation")
                        .setAdditionalProperties(Map.of(
                                "fieldSet","updateFields",
                                "mapping", new KeyValue("graphKey","configuration.operation").set("configKey","value"),
                                "id","operation",
                                "name","operation",
                                "dependsOn", Map.of("dependantType", "Operation", "dependantField", "configuration.updateField")
                        ))


        );

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("syncariEntityDefId").setDatatype(new PicklistType())
                        .setLabel(i18n("update_records_entity_label"))
                        .setHelpSummary(i18n("update_records_entity_help"))
                        .setHelpText(i18n("update_records_entity_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("type","SyncariEntity")),
                new FunctionConfiguration().setName("rejectEmpty").setDatatype(new BooleanType())
                        .setLabel(i18n("update_records_reject_empty_label"))
                        .setHelpSummary(i18n("update_records_reject_empty_help"))
                        .setHelpText(i18n("update_records_reject_empty_help")).setDefaultValue(true)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),                       

                new FunctionConfiguration().setName("predicate").setDatatype(new PredicateType())
                        .setLabel(i18n("update_records_predicate_label"))
                        .setHelpSummary(i18n("update_records_predicate_help"))
                        .setHelpText(i18n("update_records_predicate_help"))
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
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields")),
                new FunctionConfiguration().setName("updateFields").setDatatype(new CompositeType())
                        .setLabel(i18n("update_records_fields_label"))
                        .setHelpSummary(i18n("update_records_fields_help"))
                        .setHelpText(i18n("update_records_fields_help"))
                        .setDefaultValue("").setRequired(false)
                        .setAdditionalProperties(Map.of("layout","row","graphKey","configuration.updateFields","configKey","value","repeatable",true))
                        .setConfiguration(updateFieldConfigurations)
        );
        return new FunctionDefinition()
                .setName(name).setDisplayName(i18n("update_records_func_label")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("update_records_func_help")).setIconPath(format(FunctionsSeed.iconPath, "update-record"))
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setHidden(true)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition insertSyncariRecordSeed(){
        String name = "insertRecord";
        List<FunctionConfiguration> updateFieldConfigurations = List.of(
                new FunctionConfiguration().setName("updateField").setDatatype(new PicklistType())
                        .setLabel("Field")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"),
                                "fieldSet","updateFields",
                                "mapping", new KeyValue("graphKey","configuration.updateField").set("configKey","value"),
                                "id","updateField",
                                "name","updateField"

                        )),
                new FunctionConfiguration().setName("newValue").setDatatype(new ObjectType())
                        .setLabel("New Value")
                        .setAdditionalProperties(Map.of(
                                "fieldSet","updateFields",
                                "mapping", new KeyValue("graphKey","configuration.newValue").set("configKey","value"),
                                "id","newValue",
                                "name","newValue",
                                "renderType","tokens"
                        ))


        );

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("syncariEntityDefId").setDatatype(new PicklistType())
                        .setLabel(i18n("insert_record_entity_label"))
                        .setHelpSummary(i18n("insert_record_entity_help"))
                        .setHelpText(i18n("insert_record_entity_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("type","SyncariEntity")),

                new FunctionConfiguration().setName("insertFields").setDatatype(new CompositeType())
                        .setLabel(i18n("insert_record_fields_label"))
                        .setHelpSummary(i18n("insert_record_fields_help"))
                        .setHelpText(i18n("insert_record_fields_help"))
                        .setDefaultValue("").setRequired(false)
                        .setAdditionalProperties(Map.of("layout","row","graphKey","configuration.insertFields","configKey","value","repeatable",true))
                        .setConfiguration(updateFieldConfigurations)
        );
        return new FunctionDefinition()
                .setName(name).setDisplayName(i18n("insert_record_func_label")).setScope(Scope.ENTITY)
                .setHelpSummary(i18n("insert_record_func_help")).setIconPath(format(FunctionsSeed.iconPath, "update-record"))
                .setHelpPath("functions." + name)
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setHidden(true)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition insertSyncariRecordSeedOnField(){
        String name = "insertRecordOnField";
        List<FunctionConfiguration> updateFieldConfigurations = List.of(
                new FunctionConfiguration().setName("updateField").setDatatype(new PicklistType())
                        .setLabel("Field")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"),
                                "fieldSet","updateFields",
                                "mapping", new KeyValue("graphKey","configuration.updateField").set("configKey","value"),
                                "id","updateField",
                                "name","updateField"

                        )),
                new FunctionConfiguration().setName("newValue").setDatatype(new ObjectType())
                        .setLabel("New Value")
                        .setAdditionalProperties(Map.of(
                                "fieldSet","updateFields",
                                "mapping", new KeyValue("graphKey","configuration.newValue").set("configKey","value"),
                                "id","newValue",
                                "name","newValue",
                                "renderType","tokens"
                        ))

        );

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("syncariEntityDefId").setDatatype(new PicklistType())
                        .setLabel(i18n("insert_record_entity_label"))
                        .setHelpSummary(i18n("insert_record_entity_help"))
                        .setHelpText(i18n("insert_record_entity_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("type","SyncariEntity")),
                new FunctionConfiguration().setName("insertFields").setDatatype(new CompositeType())
                        .setLabel(i18n("insert_record_fields_label"))
                        .setHelpSummary(i18n("insert_record_fields_help"))
                        .setHelpText(i18n("insert_record_fields_help"))
                        .setDefaultValue("").setRequired(false)
                        .setAdditionalProperties(Map.of("layout","row","graphKey","configuration.updateFields","configKey","value","repeatable",true))
                        .setConfiguration(updateFieldConfigurations)
        );
        return new FunctionDefinition()
                .setName(name).setDisplayName("insert_record_func_label").setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("insert_record_func_help")).setIconPath(format(FunctionsSeed.iconPath, "update-record"))
                .setHelpPath("functions." + name)
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setHidden(true)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition filterOnEntitySeed(){
        return filterSeed(Scope.ENTITY);
    }

    private static FunctionDefinition filterOnAttributeSeed(){
        return filterSeed(Scope.ATTRIBUTE);
    }

    private static FunctionDefinition filterSeed(Scope scope){
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("predicate").setDatatype(new PredicateType())
                        .setLabel(i18n("filter_predicate_label"))
                        .setHelpText(i18n("filter_predicate_help"))
                        .setHelpSummary(i18n("filter_predicate_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields")),
                new FunctionConfiguration().setName("field").setDatatype(new PicklistType()).setLabel("Field")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields","dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.attributeDefinition"), "allowUserToken", true)),
                new FunctionConfiguration().setName("operator").setDatatype(new PicklistType()).setLabel("Operator")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields","dependsOn", Map.of("dependantType", "Operator", "dependantField", "configuration.attributeDefinition"))),
                new FunctionConfiguration().setName("value").setDatatype(new StringType()).setLabel("Value")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields")),
                new FunctionConfiguration().setName("edgeOptions").setDatatype(new MapType()).setDefaultValue(true).setAdditionalProperties(
                        Map.of("options", Map.of("True", true, "False", false), "supported", true, "isDynamic", true)
                ),
                new FunctionConfiguration().setName("dontMatchBlank").setDatatype(new BooleanType())
                        .setLabel("filter_use_empty_label").setHelpSummary("filter_use_empty_help")
                        .setDefaultValue(false).setAdditionalProperties(Map.of("hideTokenPicker", true))
        );

        return new FunctionDefinition()
                .setName(FunctionConstants.FILTER).setDisplayName(i18n("filter_func_label")).setScope(scope)
                .setHelpSummary(i18n("filter_func_help")).setIconPath(format(FunctionsSeed.iconPath, "filter"))
                .setHelpPath("functions.filter")
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));

    }

    private static FunctionDefinition isFalseEntitySeed(){
        return isFalseSeed(Scope.ENTITY);
    }
    private static FunctionDefinition isFalseAttributeSeed(){
        return isFalseSeed(Scope.ATTRIBUTE);
    }
    private static FunctionDefinition isFalseSeed(Scope scope){
        return new FunctionDefinition()
                .setName(FunctionConstants.IS_FALSE).setDisplayName(i18n("is_false_func_label"))
                .setScope(scope)
                .setHelpSummary(i18n("is_false_func_help")).setIconPath(format(FunctionsSeed.iconPath, "is-false"))
                .setHelpPath("functions." + FunctionConstants.IS_FALSE)
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new ObjectType())
                .setType(Type.BUILT_IN)
                .setDynamicConfig(true)
                .setHidden(false)
                .setPositionalParams(List.of(new Parameter("filter", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition isTrueEntitySeed(){
        return isTrueSeed(Scope.ENTITY);
    }
    private static FunctionDefinition isTrueAttributeSeed(){
        return isTrueSeed(Scope.ATTRIBUTE);
    }
    private static FunctionDefinition isTrueSeed(Scope scope){
        return new FunctionDefinition()
                .setName(FunctionConstants.IS_TRUE).setDisplayName(i18n("is_true_func_label"))
                .setScope(scope)
                .setHelpSummary(i18n("is_true_func_help")).setIconPath(format(FunctionsSeed.iconPath, "is-true"))
                .setHelpPath("functions." + FunctionConstants.IS_TRUE)
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new ObjectType())
                .setType(Type.BUILT_IN)
                .setDynamicConfig(true)
                .setHidden(false)
                .setPositionalParams(List.of(new Parameter("filter", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition predicateNodeEntitySeed() {
        return predicateNodeSeed(Scope.ENTITY);
    }

    private static FunctionDefinition predicateNodeAttributeSeed() {
        return predicateNodeSeed(Scope.ATTRIBUTE);
    }

    private static FunctionDefinition predicateNodeSeed(Scope scope) {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("value")
                        .setDatatype(new BooleanType())
                        .setLabel("Value")
                        .setDefaultValue(true)
                        .setAdditionalProperties(Map.of()));

        return new FunctionDefinition()
                .setName(FunctionConstants.PREDICATE).setDisplayName(i18n("predicate_func_label"))
                .setScope(scope)
                .setHelpSummary(i18n("predicate_func_help")).setIconPath(format(FunctionsSeed.iconPath, "is-true"))
                .setHelpPath("functions." + FunctionConstants.PREDICATE)
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new ObjectType())
                .setType(Type.BUILT_IN)
                .setDynamicConfig(false)
                .setConfiguration(configuration)
                .setHidden(true)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition decodeSeed(){
        return new FunctionDefinition()
                .setName(FunctionConstants.DECODE).setDisplayName(i18n("decode_func_label"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("decode_func_help")).setIconPath(format(FunctionsSeed.iconPath, "decode"))
                .setHelpPath("functions." + FunctionConstants.DECODE)
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setPositionalParams(List.of(new Parameter("text", DatatypeFactory.getDatatype("string"), false)));
    }

    private static FunctionDefinition encodeSeed(){
        return new FunctionDefinition()
                .setName(FunctionConstants.ENCODE).setDisplayName(i18n("encode_func_label"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("encode_func_help")).setIconPath(format(FunctionsSeed.iconPath, "encode"))
                .setHelpPath("functions." + FunctionConstants.ENCODE)
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setPositionalParams(List.of(new Parameter("text", DatatypeFactory.getDatatype("string"), false)));
    }

    private static FunctionDefinition decryptSeed(){

        List<FunctionConfiguration> configurations = List.of(
                new FunctionConfiguration()
                        .setName("key")
                        .setDatatype(new PasswordType()).setLabel(i18n("decrypt_key_label"))
                        .setRequired(true)
                        .setHelpSummary(i18n("decrypt_key_func_help"))
                        .setHelpText(i18n("decrypt_key_func_help"))
                        .setAdditionalProperties(Map.of("hideTokenPicker", true))
        );

        return new FunctionDefinition()
                .setName(FunctionConstants.DECRYPT).setDisplayName(i18n("decrypt_func_label"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("decrypt_func_help")).setIconPath(format(FunctionsSeed.iconPath, "decrypt"))
                .setHelpPath("functions." + FunctionConstants.DECRYPT)
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setConfiguration(configurations)
                .setPositionalParams(List.of(new Parameter("text", DatatypeFactory.getDatatype("string"), false)));
    }

    private static FunctionDefinition encryptSeed(){

        List<FunctionConfiguration> configurations = List.of(
                new FunctionConfiguration()
                        .setName("key")
                        .setDatatype(new PasswordType()).setLabel(i18n("encrypt_key_label"))
                        .setRequired(true)
                        .setHelpSummary(i18n("encrypt_key_func"))
                        .setAdditionalProperties(Map.of("hideTokenPicker", true))
        );

        return new FunctionDefinition()
                .setName(FunctionConstants.ENCRYPT).setDisplayName(i18n("encrypt_func_label"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("encrypt_func_help")).setIconPath(format(FunctionsSeed.iconPath, "encrypt"))
                .setHelpPath("functions." + FunctionConstants.ENCRYPT)
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setConfiguration(configurations)
                .setPositionalParams(List.of(new Parameter("text", DatatypeFactory.getDatatype("string"), false)));
    }

    private static FunctionDefinition uuidSeed(){
        return new FunctionDefinition()
                .setName(FunctionConstants.UUID).setDisplayName(i18n("uuid_func_label"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("uuid_func_help")).setIconPath(format(FunctionsSeed.iconPath, "uuid"))
                .setHelpPath("functions." + FunctionConstants.UUID)
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setConfiguration(List.of())
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("string"), false)));
    }

    private static FunctionDefinition urlEncodeSeed(){
        return new FunctionDefinition()
                .setName(FunctionConstants.URL_ENCODE).setDisplayName(i18n("urlEncode_func_label"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("urlEncode_func_help")).setIconPath(format(FunctionsSeed.iconPath, "url-encode"))
                .setHelpPath("functions." + FunctionConstants.URL_ENCODE)
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setConfiguration(List.of())
                .setPositionalParams(List.of(new Parameter("url", DatatypeFactory.getDatatype("string"), false)));
    }

    private static FunctionDefinition isEmptySeed() {
        return new FunctionDefinition()
                .setName(FunctionConstants.IS_EMPTY).setDisplayName(i18n("empty_func_label"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("empty_func_help")).setIconPath(format(FunctionsSeed.iconPath, "empty"))
                .setHelpPath("functions." + FunctionConstants.IS_EMPTY)
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setConfiguration(List.of())
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition caseBranchSeed(Scope scope) {
        String displayName = "Case Branch";
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName(FunctionConstants.CASE_BRANCH).setDatatype(new PicklistType()).setLabel(displayName).
                        setAdditionalProperties(Map.of(
                                "mapping", new KeyValue("graphKey","configuration.value").set("configKey","value"), "values", List.of()))
        );

        return new FunctionDefinition()
                .setName(FunctionConstants.CASE_BRANCH).setDisplayName(displayName).setScope(scope)
                .setHelpSummary(i18n("case_branch_help")).setIconPath(format(FunctionsSeed.iconPath, "case-branch"))
                .setHelpPath("functions." + FunctionConstants.CASE)
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setPositionalParams(List.of(new Parameter("filter", DatatypeFactory.getDatatype("object"))))
                .setOutputType(new ObjectType()).setType(Type.BUILT_IN)
                .setDynamicConfig(true)
                .setHidden(true);
    }

    private static FunctionDefinition caseSeed(Scope scope) {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName(FunctionConstants.CASE).setDatatype(new CaseType()).setLabel(StringUtils.capitalize(FunctionConstants.CASE)),
                new FunctionConfiguration().setName("edgeOptions").setDatatype(new MapType()).setDefaultValue(true).setAdditionalProperties(
                        Map.of("options", Map.of(), "supported", true, "isDynamic", true, "dynamicConfig", true, "implicit", true, "edgeType", FunctionConstants.CASE))
        );

        return new FunctionDefinition()
                .setName(FunctionConstants.CASE).setDisplayName(CaseFunction.DISPLAY_NAME).setScope(scope)
                .setHelpSummary(i18n("case_func_help")).setIconPath(format(FunctionsSeed.iconPath, FunctionConstants.CASE))
                .setHelpPath("functions." + FunctionConstants.CASE)
                .setEngineType(EngineType.FUNCTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    /*
    		functions.insertOne(new Document("name", "last")
				.append("displayName", "Last")
				.append("helpSummary",
						"A function which takes a list of values and returns last value")
				.append("helpPath", "")
				.append("seeded", true)

				.append("iconPath", "/assets/icons/functions/last.svg")
				.append("scope", Scope.ATTRIBUTE.name())
				.append("engineType", EngineType.FUNCTION.name())
				.append("outputType", "object")
				.append("type", Type.BUILT_IN.name())
				.append("positionalParams", List.of(getParameterDoc("values", DatatypeFactory.getDatatype("list")))));
     */

    private static FunctionDefinition lastSeed() {
        return new FunctionDefinition()
                .setName(FunctionConstants.LAST).setDisplayName(i18n("last_func_label"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("last_func_help")).setIconPath(format(FunctionsSeed.iconPath, "last"))
                .setHelpPath("functions." + FunctionConstants.LAST)
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new ObjectType())
                .setType(Type.BUILT_IN)
                .setDynamicConfig(true)
                .setConfiguration(List.of())
                .setPositionalParams(List.of(new Parameter("values", DatatypeFactory.getDatatype("list"), false)));
    }

    private static FunctionDefinition loopSeed(Scope scope) {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("option")
                        .setDatatype(new PicklistType())
                        .setAdditionalProperties(Map.of("values",getLoopOptions()))
                        .setLabel(i18n("loop_option_title"))
                        .setHelpSummary(i18n("loop_option_help_summary"))
                        .setHelpText(i18n("loop_option_help_text"))
                        .setDefaultValue("index"),
                new FunctionConfiguration().setName("startIndex").setDatatype(new IntegerType())
                        .setLabel(i18n("loop_start_index_label"))
                        .setHelpText(i18n("loop_start_index_help"))
                        .setHelpSummary(i18n("loop_start_loop_summary"))
                        .setDefaultValue("").setRequired(false),
                new FunctionConfiguration().setName("endIndex").setDatatype(new IntegerType())
                        .setLabel(i18n("loop_end_index_label"))
                        .setHelpText(i18n("loop_end_index_help"))
                        .setHelpSummary(i18n("loop_end_index_summary"))
                        .setDefaultValue("").setRequired(false),
                new FunctionConfiguration().setName("variable").setDatatype(new StringType())
                        .setLabel(i18n("loop_variable_label"))
                        .setHelpText(i18n("loop_variable_help"))
                        .setHelpSummary(i18n("loop_variable_summary")),
                new FunctionConfiguration().setName("loopStart").setDatatype(new BooleanType()),
                new FunctionConfiguration().setName("predicate").setDatatype(new PredicateType())
                        .setLabel(i18n("filter_predicate_label"))
                        .setHelpText(i18n("filter_predicate_help"))
                        .setHelpSummary(i18n("filter_predicate_help"))
                        .setDefaultValue("").setRequired(false)
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields")),
                new FunctionConfiguration().setName("field").setDatatype(new PicklistType()).setLabel("Field")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields","dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.attributeDefinition"), "allowUserToken", true)),
                new FunctionConfiguration().setName("operator").setDatatype(new PicklistType()).setLabel("Operator")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields","dependsOn", Map.of("dependantType", "Operator", "dependantField", "configuration.attributeDefinition"))),
                new FunctionConfiguration().setName("value").setDatatype(new StringType()).setLabel("Value")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields")),
                new FunctionConfiguration().setName("maxLoop").setDatatype(new IntegerType())
                        .setLabel(i18n("max_loop_label"))
                        .setHelpText(i18n("max_loop_help"))
                        .setHelpSummary(i18n("max_loop_summary"))
                        .setDefaultValue("2000").setRequired(false)
                );
        ;

        return new FunctionDefinition()
                .setName(FunctionConstants.LOOP).setDisplayName(i18n("loop_func_label") != null ?  i18n("loop_func_label") : "Loop")
                .setScope(scope)
                .setHelpSummary(i18n("loop_func_help")).setIconPath(format(FunctionsSeed.iconPath, "loop"))
                .setHelpPath("functions." + FunctionConstants.LOOP)
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setConfiguration(configuration)
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static List<KeyValue> getLoopOptions() {
        return List.of(
                new KeyValue("label",i18n("loop_index")).set("value","index"),
                new KeyValue("label",i18n("loop_variable")).set("value","variable"),
                new KeyValue("label",i18n("loop_condition")).set("value","condition")
        );
    }


    private static FunctionDefinition forEachSeed(Scope scope) {
        return new FunctionDefinition()
                .setName(FunctionConstants.FOR_EACH).setDisplayName(i18n("foreach_func_label"))
                .setScope(scope)
                .setHelpSummary(i18n("foreach_func_help")).setIconPath(format(FunctionsSeed.iconPath, "for-each"))
                .setHelpPath("functions." + FunctionConstants.FOR_EACH)
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new ObjectType())
                .setType(Type.BUILT_IN)
                .setDynamicConfig(true)
                .setHidden(true)
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setPositionalParams(List.of(new Parameter("loop", DatatypeFactory.getDatatype("object"), false)));
    }
    private static FunctionDefinition afterSeed(Scope scope){
        return new FunctionDefinition()
                .setName(FunctionConstants.AFTER).setDisplayName(i18n("after_func_label") != null ? i18n("after_func_label") : "After")
                .setScope(scope)
                .setHelpSummary(i18n("is_false_func_help")).setIconPath(format(FunctionsSeed.iconPath, "after"))
                .setHelpPath("functions." + FunctionConstants.AFTER)
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new ObjectType())
                .setType(Type.BUILT_IN)
                .setDynamicConfig(true)
                .setHidden(true)
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setPositionalParams(List.of(new Parameter("loop", DatatypeFactory.getDatatype("object"), false)));
    }

    private static FunctionDefinition endLoopSeed(Scope scope){

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("loopEnd").setDatatype(new BooleanType()));

        return new FunctionDefinition()
                .setName(FunctionConstants.END_LOOP).setDisplayName(i18n("endloop_func_label"))
                .setScope(scope)
                .setHelpSummary(i18n("endloop_func_help")).setIconPath("")
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new ObjectType())
                .setType(Type.BUILT_IN)
                .setDynamicConfig(true)
                .setHidden(true)
                .setConfiguration(configuration)
                .setAdditionalInputTypes(List.of(ListType.VALUE))
                .setPositionalParams(List.of(new Parameter("loop", DatatypeFactory.getDatatype("object"), false)));

    }

    private static FunctionDefinition autoIncrement(Scope scope, String name){

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("sequenceName").setLabel("Sequence Name")
                        .setRequired(true)
                        .setHelpSummary("A unique name for the global sequence").setDatatype(new StringType()),
                new FunctionConfiguration().setName("startValue").setLabel("Start Value")
                        .setHelpSummary("A starting value for the sequence. It will be used to initialize the sequence. Default is 1")
                        .setRequired(false)
                        .setDatatype(new IntegerType())
                );

        return new FunctionDefinition()
                .setName(name).setDisplayName(i18n("autoIncrement_func_label"))
                .setScope(scope)
                .setHelpSummary(i18n("autoIncrement_func_help")).setIconPath(format(FunctionsSeed.iconPath, "auto-increment"))
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new IntegerType())
                .setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setConfiguration(configuration)
                .setPositionalParams(List.of(new Parameter("input", DatatypeFactory.getDatatype("string"), false)));

    }

    private static FunctionDefinition autoIncrementEntity(String name){

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("sequenceName").setLabel("Sequence Name")
                        .setRequired(true)
                        .setHelpSummary("A unique name for the global sequence").setDatatype(new StringType()),
                new FunctionConfiguration().setName("startValue").setLabel("Start Value")
                        .setHelpSummary("A starting value for the sequence. It will be used to initialize the sequence. Default is 1")
                        .setRequired(false)
                        .setDatatype(new IntegerType())
        );

        return new FunctionDefinition()
                .setName(name).setDisplayName(i18n("autoIncrement_func_label"))
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("autoIncrement_func_help")).setIconPath(format(FunctionsSeed.iconPath, "auto-increment"))
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new ObjectType())
                .setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setConfiguration(configuration)
                .setPositionalParams(List.of(new Parameter("input", DatatypeFactory.getDatatype("string"), false)));

    }

        public static List<Map<String, String>> getSupportedDataTypes() {
        return  new TreeMap<>(Map.of(
                "boolean","Boolean",
                "double","Double",
                "integer","Integer",
                "date","Date",
                "datetime","Date Time",
                "text","Text",
                "email","Email",
                "phone","Phone",
                "object","Object"
        )).entrySet().stream().map(e -> Map.of("value", e.getKey(), "label", e.getValue())).collect(Collectors.toList());
    }
}
