package com.syncari.core.functions;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.syncari.core.datatype.*;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.Parameter;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import com.syncari.utils.KeyValue;
import org.bson.Document;

public class TextFunctionsSeed {
    
    public static FunctionDefinition getConcat() {
        String name = "concatenate";
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("separator")
                        .setDatatype(new StringType()).setLabel(i18n("delimiter_label"))
                        .setHelpSummary(i18n("choose_delimiter"))
                        .setHelpText(i18n("choose_delimiter"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration()
                        .setName("values")
                        .setDatatype(new PicklistType())
                        .setLabel(i18n("values"))
                        .setHelpSummary(i18n("concat_func_help"))
                        .setHelpText(i18n("concat_func_help"))
                        //.setDefaultValue("")
                        .setAdditionalProperties(Map.of())
        );
        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("concat_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("concat_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, name))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter(
                                "first", DatatypeFactory.getDatatype("string"), false)
                        )
                );
    }

    public static FunctionDefinition getLPad() {
        String name = "lpad";
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("pad")
                        .setDatatype(new StringType()).setLabel(i18n("pad_label"))
                        .setDefaultValue("")
                        .setHelpSummary(i18n("lpad_pad_func"))
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration()
                        .setName("size")
                        .setDatatype(new IntegerType()).setLabel(i18n("size_label"))
                        .setHelpSummary(i18n("lpad_size_func"))
                        .setDefaultValue(0)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true))                        
        );
        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("lpad_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("lpad_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "left-pad"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                		List.of(new Parameter("value", DatatypeFactory.getDatatype("string"), false))
                );
    }
    
    public static FunctionDefinition getRPad() {
    	String name = "rpad";
    	List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                .setName("pad")
                .setDatatype(new StringType()).setLabel(i18n("pad_label"))
                .setHelpSummary(i18n("rpad_pad_func"))
                .setDefaultValue("")
                .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration()
                .setName("size")
                .setDatatype(new IntegerType()).setLabel(i18n("size_label"))
                .setHelpSummary(i18n("rpad_pad_func"))
                .setDefaultValue(0)
                .setAdditionalProperties(Map.of("hideTokenPicker", true)) 
    			);
    	return new FunctionDefinition()
    			.setName(name)
    			.setDisplayName(i18n("rpad_title"))
    			.setScope(Scope.ATTRIBUTE)
    			.setHelpSummary(i18n("rpad_func_help"))
                .setHelpPath("functions." + name)
    			.setIconPath(format(FunctionsSeed.iconPath, "right-pad"))
    			.setEngineType(EngineType.FUNCTION)
    			.setConfiguration(configuration)
    			.setDynamicConfig(true)
    			.setOutputType(new StringType())
    			.setType(Type.STANDARD)
    			.setPositionalParams(
    					List.of(new Parameter("value", DatatypeFactory.getDatatype("string"), false))
    					);
    }

    public static FunctionDefinition getIndexOf() {
        String name = "indexOf";
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("searchString")
                        .setDatatype(new StringType())
                        .setLabel(i18n("search_string"))
                        .setHelpSummary(i18n("indexof_search_func"))
                        .setHelpText(i18n("indexof_search_func"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("hideTokenPicker", true))
        );
        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("indexof_func_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("indexof_func"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "index-of"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setOutputType(new IntegerType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter(
                                "first", DatatypeFactory.getDatatype("string"), false)
                        )
                );
    }

    private static List<KeyValue> getOptions() {
        return List.of(
                new KeyValue("label",i18n("extract_domain_fullDomain")).set("value","fullDomain"),
                new KeyValue("label",i18n("extract_domain_name")).set("value","name"),
                new KeyValue("label",i18n("extract_domain_tld")).set("value","tld")
                );
    }

    public static FunctionDefinition extractDomainOnEntity() {
        String name = "extractDomainOnEntity";
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("option")
                        .setDatatype(new PicklistType())
                        .setAdditionalProperties(Map.of("values",getOptions()))
                        .setLabel(i18n("extract_domain_option_title"))
                        .setHelpSummary(i18n("extract_domain_option_help"))
                        .setHelpText(i18n("extract_domain_option_help"))
                        .setDefaultValue(""),
                new FunctionConfiguration()
                        .setName("value")
                        .setDatatype(new StringType())
                        .setAdditionalProperties(Map.of())
                        .setLabel(i18n("extract_domain_value_title"))
                        .setHelpSummary(i18n("extract_domain_value_help"))
                        .setHelpText(i18n("extract_domain_value_help"))
                        .setDefaultValue("")
                        .setRequired(true)
        );
        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("extract_domain_title"))
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("extract_domain_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "extract-domain"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setHelpPath("functions.extractDomainOnEntity")
                .setPositionalParams(
                        List.of(new Parameter(
                                "input", ObjectType.VALUE, false)
                        )
                );
    }

    public static FunctionDefinition extractDomainOnField() {
        String name = "extractDomain";
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("option")
                        .setDatatype(new PicklistType())
                        .setAdditionalProperties(Map.of("values",getOptions()))
                        .setLabel(i18n("extract_domain_option_title"))
                        .setHelpSummary(i18n("extract_domain_option_help"))
                        .setHelpText(i18n("extract_domain_option_help"))
                        .setDefaultValue("")
        );
        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("extract_domain_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("extract_domain_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "extract-domain"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter(
                                "input", StringType.VALUE, false)
                        )
                );
    }

    public static FunctionDefinition replaceFunction() {
        String name = "replace";
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("searchExpression")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("replace_searchExpression_label")
                        .setHelpSummary("replace_searchExpression_help")
                        .setHelpText("replace_searchExpression_help")
                        .setDefaultValue(""),
                new FunctionConfiguration()
                        .setName("replaceWith")
                        .setDatatype(StringType.VALUE)
                        .setRequired(false)
                        .setLabel("replace_replaceWith_label")
                        .setHelpSummary("replace_replaceWith_help")
                        .setHelpText("replace_replaceWith_help")
                        .setDefaultValue(""),
                new FunctionConfiguration()
                        .setName("caseInsensitiveSearch")
                        .setDatatype(BooleanType.VALUE)
                        .setRequired(false)
                        .setLabel("replace_caseInsensitiveSearch_label")
                        .setHelpSummary("replace_caseInsensitiveSearch_help")
                        .setHelpText("replace_caseInsensitiveSearch_help")
                        .setDefaultValue(false)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true))


        );
        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("replace_label"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("replace_help"))
                .setHelpPath("functions.attribute" + name)
                .setIconPath(format(FunctionsSeed.iconPath, "replace"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter(
                                "input", StringType.VALUE, false)
                        )
                );
    }

    public static FunctionDefinition replaceOnEntityFunction() {
        String name = "replaceOnEntity";
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("value")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("replace_value_label")
                        .setHelpSummary("replace_value_help")
                        .setHelpText("replace_value_help")
                        .setDefaultValue(""),
                new FunctionConfiguration()
                        .setName("searchExpression")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("replace_searchExpression_label")
                        .setHelpSummary("replace_searchExpression_help")
                        .setHelpText("replace_searchExpression_help")
                        .setDefaultValue(""),
                new FunctionConfiguration()
                        .setName("replaceWith")
                        .setDatatype(StringType.VALUE)
                        .setRequired(false)
                        .setLabel("replace_replaceWith_label")
                        .setHelpSummary("replace_replaceWith_help")
                        .setHelpText("replace_replaceWith_help")
                        .setDefaultValue(""),
                new FunctionConfiguration()
                        .setName("caseInsensitiveSearch")
                        .setDatatype(BooleanType.VALUE)
                        .setRequired(false)
                        .setLabel("replace_caseInsensitiveSearch_label")
                        .setHelpSummary("replace_caseInsensitiveSearch_help")
                        .setHelpText("replace_caseInsensitiveSearch_help")
                        .setDefaultValue(false)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true))


        );
        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("replace_label"))
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("replace_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "replace"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setOutputType(ObjectType.VALUE)
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter(
                                "input", ObjectType.VALUE, false)
                        )
                )
                .setAdditionalInputTypes(List.of(ListType.VALUE));
    }

    public static FunctionDefinition getCamelCase() {
        String name = "camelCase";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("camelCase_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("camelCase_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "camel-case"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of())
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.BUILT_IN)
                .setPositionalParams(
                        List.of(new Parameter("text", DatatypeFactory.getDatatype("string"), false))
                );
    }

    public static FunctionDefinition getCapitalize() {
        String name = "capitalize";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("capitalize_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("capitalize_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "capitalize"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of())
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.BUILT_IN)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("string"), false))
                );
    }

    public static FunctionDefinition getLength() {
        String name = "length";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("length_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("length_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "length"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of())
                .setDynamicConfig(true)
                .setOutputType(new IntegerType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false))
                );
    }

    public static FunctionDefinition getLengthOnEntity() {
        String name = "lengthOnEntity";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("length_title"))
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("length_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "length"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of(new FunctionConfiguration()
                        .setName("value")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("value_label")
                        .setHelpSummary("value_help")
                        .setHelpText("value_help")
                        .setDefaultValue("")))
                .setDynamicConfig(true)
                .setOutputType(new IntegerType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false))
                );
    }

    public static FunctionDefinition getLower() {
        String name = "lower";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("lower_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("lower_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "lowercase"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of())
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("string"), false))
                );
    }

    public static FunctionDefinition getUpper() {
        String name = "upper";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("upper_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("upper_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "upper"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of())
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("string"), false))
                );
    }

    public static FunctionDefinition getLTrim() {
        String name = "ltrim";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("ltrim_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("ltrim_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "left-trim"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of())
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("string"), false))
                );
    }

    public static FunctionDefinition getRTrim() {
        String name = "rtrim";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("rtrim_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("rtrim_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "right-trim"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of())
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("string"), false))
                );
    }

    public static FunctionDefinition getTrim() {
        String name = "trim";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("trim_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("trim_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "trim"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of())
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("string"), false))
                );
    }

    public static FunctionDefinition getMask() {
        String name = "mask";

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("maskCharacter")
                        .setDatatype(new StringType())
                        .setLabel(i18n("mask_character_label"))
                        .setHelpSummary(i18n("mask_character_help"))
                        .setHelpText(i18n("mask_character_help"))
                        .setDefaultValue("*")
                        .setAdditionalProperties(Map.of("hideTokenPicker", true))
        );

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("mask_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("mask_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "mask"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("text", DatatypeFactory.getDatatype("string"), false))
                );
    }

    public static FunctionDefinition getNumberFormat() {
        String name = "numberFormat";

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("noofractionalDigits")
                        .setDatatype(new IntegerType())
                        .setLabel(i18n("fractional_digits_label"))
                        .setHelpSummary(i18n("fractional_digits_help"))
                        .setHelpText(i18n("fractional_digits_help"))
                        .setDefaultValue(2)
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("decimalSeparator")
                        .setDatatype(new StringType())
                        .setLabel(i18n("decimalSeparator_digits_label"))
                        .setHelpSummary(i18n("decimalSeparator_digits_help"))
                        .setHelpText(i18n("decimalSeparator_digits_help"))
                        .setDefaultValue(".")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("groupingSeparator")
                        .setDatatype(new StringType())
                        .setLabel(i18n("groupingSeparator_digits_label"))
                        .setHelpSummary(i18n("groupingSeparator_digits_help"))
                        .setHelpText(i18n("groupingSeparator_digits_help"))
                        .setDefaultValue(",")
                        .setAdditionalProperties(Map.of())
        );

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("number_format_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("number_format_func_help"))
                .setHelpPath("functions." + name)
                // different icon?
                .setIconPath(format(FunctionsSeed.iconPath, "length"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("number", DatatypeFactory.getDatatype("number"), false))
                );
    }
    
    public static FunctionDefinition getPhoneNumberFormatOnEntity() {
      String name = "formatPhoneOnEntity";

      List<FunctionConfiguration> configuration =  List.of(
              new FunctionConfiguration()
                      .setName("format")
                      .setDatatype(new PicklistType())
                      .setLabel(i18n("formatPhone_format_label"))
                      .setHelpSummary(i18n("formatPhone_format_func_help"))
                      .setHelpText(i18n("formatPhone_format_func_help"))
                      .setAdditionalProperties(Map.of("values", getFormats())),
              new FunctionConfiguration()
                      .setName("countryCodeField")
                      .setDatatype(new StringType())
                      .setLabel(i18n("formatPhone_countryCodeField_label"))
                      .setHelpSummary(i18n("formatPhone_countryCodeField_func_help"))
                      .setHelpText(i18n("formatPhone_countryCodeField_func_help"))
                      .setDefaultValue("")
                      .setAdditionalProperties(Map.of()),
              new FunctionConfiguration()
                      .setName("defaultCountryCode")
                      .setDatatype(new StringType())
                      .setLabel(i18n("formatPhone_defaultCountryCode_label"))
                      .setHelpSummary(i18n("formatPhone_defaultCountryCode_func_help"))
                      .setHelpText(i18n("formatPhone_defaultCountryCode_func_help"))
                      .setDefaultValue("")
                      .setRequired(true)
                      .setAdditionalProperties(Map.of()),
              new FunctionConfiguration()
                      .setName("value")
                      .setDatatype(new StringType())
                      .setLabel("Value")
                      .setDefaultValue("")
                      .setAdditionalProperties(Map.of())
                      .setLabel(i18n("formatPhoneOnEntity_value_title"))
                      .setHelpSummary(i18n("formatPhone_func_help"))
                      .setHelpText(i18n("formatPhone_func_help"))
                      .setDefaultValue("")
                      .setRequired(true)
      );

      return new FunctionDefinition()
              .setName(name)
              .setDisplayName(i18n("formatPhone_title"))
              .setScope(Scope.ENTITY)
              .setHelpSummary(i18n("formatPhone_func_help"))
              .setHelpPath("functions." + name)
              .setIconPath(format(FunctionsSeed.iconPath, "format-phone"))
              .setEngineType(EngineType.FUNCTION)
              .setConfiguration(configuration)
              .setDynamicConfig(true)
              .setOutputType(new ObjectType())
              .setType(Type.STANDARD)
              .setPositionalParams(
                  List.of(new Parameter(
                      "input", ObjectType.VALUE, false)
              )
              );
  }

    public static FunctionDefinition getPhoneNumberFormat() {
        String name = "formatPhone";

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("format")
                        .setDatatype(new PicklistType())
                        .setLabel(i18n("formatPhone_format_label"))
                        .setHelpSummary(i18n("formatPhone_format_func_help"))
                        .setHelpText(i18n("formatPhone_format_func_help"))
                        .setAdditionalProperties(Map.of("values", getFormats())),
                new FunctionConfiguration()
                        .setName("countryCodeField")
                        .setDatatype(new StringType())
                        .setLabel(i18n("formatPhone_countryCodeField_label"))
                        .setHelpSummary(i18n("formatPhone_countryCodeField_func_help"))
                        .setHelpText(i18n("formatPhone_countryCodeField_func_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("defaultCountryCode")
                        .setDatatype(new StringType())
                        .setLabel(i18n("formatPhone_defaultCountryCode_label"))
                        .setHelpSummary(i18n("formatPhone_defaultCountryCode_func_help"))
                        .setHelpText(i18n("formatPhone_defaultCountryCode_func_help"))
                        .setDefaultValue("")
                        .setRequired(true)
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("value")
                        .setDatatype(new StringType())
                        .setLabel("Value")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet", "conditionFields"))
        );

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("formatPhone_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("formatPhone_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "format-phone"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setDynamicConfig(true)
                .setOutputType(new ObjectType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false))
                );
    }

    private static List<Document> getFormats() {
        Map<String, String> fields = new HashMap<>();
        fields.put("E164", "E164");
        fields.put("INTERNATIONAL", "International");
        fields.put("NATIONAL", "National");
        return fields.entrySet().stream().map(e -> new Document("value", e.getKey()).append("label",e.getValue())).collect(Collectors.toList());
    }

    public static FunctionDefinition getRemoveNonPrintable() {
        String name = "removeNonPrintable";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("removeNonPrintable_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("removeNonPrintable_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "remove-non-printable"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of())
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("text", DatatypeFactory.getDatatype("string"), false))
                );
    }

    public static FunctionDefinition getReverseString() {
        String name = "reverseString";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("reverseString_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("reverseString_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "reverse-string"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of())
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("values", DatatypeFactory.getDatatype("string"), false))
                );
    }

    public static FunctionDefinition getStripTags() {
        String name = "striptags";

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("allowedTags")
                        .setDatatype(new StringType())
                        .setLabel(i18n("allowedTags_label"))
                        .setHelpSummary(i18n("allowedTags_help"))
                        .setHelpText(i18n("allowedTags_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of())
        );

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("striptags_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("striptags_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "strip-tags"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("text", DatatypeFactory.getDatatype("string"), false))
                );
    }

    public static FunctionDefinition getSubstring() {
        String name = "substring";

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("startIndex")
                        .setDatatype(new IntegerType())
                        .setLabel(i18n("startIndex_label"))
                        .setHelpSummary(i18n("startIndex_help"))
                        .setHelpText(i18n("startIndex_help"))
                        .setDefaultValue(0)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration()
                        .setName("endIndex")
                        .setDatatype(new IntegerType())
                        .setLabel(i18n("endIndex_label"))
                        .setHelpSummary(i18n("endIndex_help"))
                        .setHelpText(i18n("endIndex_help"))
                        .setDefaultValue(-1)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true))
        );

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("substring_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("substring_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "substring"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("text", DatatypeFactory.getDatatype("string"), false))
                );
    }

    public static FunctionDefinition splitOnEntity() {
        return new FunctionDefinition().setName(FunctionConstants.SPLIT_ON_ENTITY).setDisplayName(i18n("split_func_title")).setScope(Scope.ENTITY)
                .setHelpSummary(i18n("split_func_help")).setIconPath(format(FunctionsSeed.iconPath, "split"))
                .setHelpPath("functions." + FunctionConstants.SPLIT)
                .setEngineType(EngineType.FUNCTION).setOutputType(new ObjectType()).setType(Type.BUILT_IN)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"),
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
                                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                                new FunctionConfiguration()
                                        .setName("value")
                                        .setDatatype(StringType.VALUE)
                                        .setRequired(true)
                                        .setLabel("value_label")
                                        .setHelpSummary("value_help")
                                        .setHelpText("value_help")
                                        .setDefaultValue("")
                        )
                );
    }

    public static FunctionDefinition getJWTTokenOnEntity() {
        String name = "jwtTokenOnEntity";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("jwtToken_title"))
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("jwtToken_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "generate-jwt-token"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of(new FunctionConfiguration()
                                .setName("headers")
                                .setDatatype(StringType.VALUE)
                                .setRequired(false)
                                .setLabel(i18n("headers_label"))
                                .setHelpSummary(i18n("headers_help"))
                                .setHelpText(i18n("headers_help"))
                                .setDefaultValue(""),
                        new FunctionConfiguration()
                                .setName("claims")
                                .setDatatype(StringType.VALUE)
                                .setRequired(false)
                                .setLabel(i18n("claims_label"))
                                .setHelpSummary(i18n("claims_help"))
                                .setHelpText(i18n("claims_help"))
                                .setDefaultValue(""),
                        new FunctionConfiguration()
                                .setName("signingKey")
                                .setDatatype(StringType.VALUE)
                                .setRequired(true)
                                .setLabel(i18n("signingkey_label"))
                                .setHelpSummary(i18n("signingkey_help"))
                                .setHelpText(i18n("signingkey_help"))
                                .setDefaultValue("")))
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false))
                );
    }

    public static FunctionDefinition getJWTToken() {
        String name = "jwtToken";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("jwtToken_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("jwtToken_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "generate-jwt-token"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of(new FunctionConfiguration()
                                .setName("headers")
                                .setDatatype(StringType.VALUE)
                                .setRequired(false)
                                .setLabel(i18n("headers_label"))
                                .setHelpSummary(i18n("headers_help"))
                                .setHelpText(i18n("headers_help"))
                                .setDefaultValue(""),
                        new FunctionConfiguration()
                                .setName("claims")
                                .setDatatype(StringType.VALUE)
                                .setRequired(false)
                                .setLabel(i18n("claims_label"))
                                .setHelpSummary(i18n("claims_help"))
                                .setHelpText(i18n("claims_help"))
                                .setDefaultValue(""),
                        new FunctionConfiguration()
                                .setName("signingKey")
                                .setDatatype(StringType.VALUE)
                                .setRequired(true)
                                .setLabel(i18n("signingkey_label"))
                                .setHelpSummary(i18n("signingkey_help"))
                                .setHelpText(i18n("signingkey_help"))
                                .setDefaultValue("")))
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("string"), false))
                );
    }

    public static FunctionDefinition charAtOnEntity() {
        String name = "charAtOnEntity";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("charAt_title"))
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("charAt_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "char-at"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of(new FunctionConfiguration()
                        .setName("value")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel(i18n("value_label"))
                        .setHelpSummary(i18n("value_help"))
                        .setHelpText(i18n("value_help"))
                        .setDefaultValue(0),
                        new FunctionConfiguration()
                                .setName("index")
                                .setDatatype(IntegerType.VALUE)
                                .setRequired(true)
                                .setLabel(i18n("index_label"))
                                .setHelpSummary(i18n("index_help"))
                                .setHelpText(i18n("index_help"))
                                .setDefaultValue(0)))
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false))
                );
    }

    public static FunctionDefinition charAt() {
        String name = "charAt";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("charAt_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("charAt_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "char-at"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of(new FunctionConfiguration()
                        .setName("value")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel(i18n("value_label"))
                        .setHelpSummary(i18n("value_help"))
                        .setHelpText(i18n("value_help"))
                        .setDefaultValue(0),
                        new FunctionConfiguration()
                                .setName("index")
                                .setDatatype(IntegerType.VALUE)
                                .setRequired(true)
                                .setLabel(i18n("index_label"))
                                .setHelpSummary(i18n("index_help"))
                                .setHelpText(i18n("index_help"))
                                .setDefaultValue(0)))
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("string"), false))
                );
    }

    public static FunctionDefinition lowerOnEntity() {
        String name = "lowerOnEntity";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("lower_title"))
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("lower_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "lowercase"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of(new FunctionConfiguration()
                        .setName("value")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("value_label")
                        .setHelpSummary("value_help")
                        .setHelpText("value_help")
                        .setDefaultValue("")))
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false))
                );
    }

    public static FunctionDefinition upperOnEntity() {
        String name = "upperOnEntity";

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("upper_title"))
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("upper_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "uppercase"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of(new FunctionConfiguration()
                        .setName("value")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("value_label")
                        .setHelpSummary("value_help")
                        .setHelpText("value_help")
                        .setDefaultValue("")))
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false))
                ).setAdditionalInputTypes(List.of(ListType.VALUE));
    }

    public static FunctionDefinition getSubstringOnEntity() {
        String name = "substringOnEntity";

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("startIndex")
                        .setDatatype(new IntegerType())
                        .setLabel(i18n("startIndex_label"))
                        .setHelpSummary(i18n("startIndex_help"))
                        .setHelpText(i18n("startIndex_help"))
                        .setDefaultValue(0)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration()
                        .setName("endIndex")
                        .setDatatype(new IntegerType())
                        .setLabel(i18n("endIndex_label"))
                        .setHelpSummary(i18n("endIndex_help"))
                        .setHelpText(i18n("endIndex_help"))
                        .setDefaultValue(-1)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration()
                        .setName("value")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("value_label")
                        .setHelpSummary("value_help")
                        .setHelpText("value_help")
                        .setDefaultValue("")
        );

        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("substring_title"))
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("substring_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "substring"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setDynamicConfig(true)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter("text", DatatypeFactory.getDatatype("object"), false))
                ).setAdditionalInputTypes(List.of(ListType.VALUE));
    }

    public static FunctionDefinition extractText() {
        String name = FunctionConstants.EXTRACT_TEXT;

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("searchExpression")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("replace_searchExpression_label")
                        .setHelpSummary("replace_searchExpression_help")
                        .setHelpText("replace_searchExpression_help")
                        .setDefaultValue(""),
                new FunctionConfiguration()
                        .setName("input")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("extractText_input_label")
                        .setHelpSummary("extractText_input_help")
                        .setHelpText("extractText_input_help")
                        .setDefaultValue(""),
                new FunctionConfiguration()
                        .setName("caseInsensitiveSearch")
                        .setDatatype(BooleanType.VALUE)
                        .setRequired(false)
                        .setLabel("replace_caseInsensitiveSearch_label")
                        .setHelpSummary("replace_caseInsensitiveSearch_help")
                        .setHelpText("replace_caseInsensitiveSearch_help")
                        .setDefaultValue(false)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration()
                        .setName("findAllMatches")
                        .setDatatype(BooleanType.VALUE)
                        .setRequired(false)
                        .setLabel("extractText_findallmatches_label")
                        .setHelpSummary("extractText_findallmatches_help")
                        .setHelpText("extractText_findallmatches_help")
                        .setDefaultValue(false)
        );
        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("extractText_label"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("extractText_help"))
                .setHelpPath("functions.attribute" + name)
                .setIconPath(format(FunctionsSeed.iconPath, "extract-text"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter(
                                "input", StringType.VALUE, false)
                        )
                );
    }

    public static FunctionDefinition getMD5Hash() {
        String name = FunctionConstants.MD5_TEXT;

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("input")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("md5_input_label")
                        .setHelpSummary("md5_input_help")
                        .setHelpText("md5_input_help")
                        .setDefaultValue("")
        );
        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("md5_label"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("md5_help"))
                .setHelpPath("functions.attribute" + name)
                .setIconPath(format(FunctionsSeed.iconPath, "md5hash"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter(
                                "input", StringType.VALUE, false)
                        )
                );
    }

    public static FunctionDefinition getMD5HashOnEntity() {
        String name = FunctionConstants.MD5_TEXT_ENTITY;

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("input")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("md5_input_label")
                        .setHelpSummary("md5_input_help")
                        .setHelpText("md5_input_help")
                        .setDefaultValue("")
        );
        return new FunctionDefinition()
                .setName(name)
                .setDisplayName(i18n("md5_label"))
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("md5_help"))
                .setHelpPath("functions.attribute" + name)
                .setIconPath(format(FunctionsSeed.iconPath, "md5hash"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter(
                                "input", ObjectType.VALUE, false)
                        )
                );
    }
}
