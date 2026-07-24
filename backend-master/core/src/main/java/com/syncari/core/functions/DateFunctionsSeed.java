package com.syncari.core.functions;

import com.syncari.core.datatype.*;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.Parameter;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import com.syncari.utils.KeyValue;

import java.util.List;
import java.util.Map;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

public class DateFunctionsSeed {
    private static final Parameter dateTimeParam = new Parameter("datetime", DatatypeFactory.getDatatype("datetime"),
            false);

    public static FunctionDefinition dayOfWeek() {
        return new FunctionDefinition().setName(FunctionConstants.DAY_OF_WEEK).setDisplayName(i18n("dayofweek_func_title")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("dayofweek_func")).setIconPath(format(FunctionsSeed.iconPath, "day-of-week"))
                .setHelpPath("functions." + FunctionConstants.DAY_OF_WEEK)
                .setEngineType(EngineType.FUNCTION).setOutputType(new IntegerType()).setType(Type.STANDARD)
                .setPositionalParams(List.of(dateTimeParam));
    }

    public static FunctionDefinition dayOfMonth() {
        return new FunctionDefinition().setName(FunctionConstants.DAY_OF_MONTH).setDisplayName(i18n("dayofmonth_func_title")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("dayofmonth_func")).setIconPath(format(FunctionsSeed.iconPath, "day-of-month"))
                .setHelpPath("functions." + FunctionConstants.DAY_OF_MONTH)
                .setEngineType(EngineType.FUNCTION).setOutputType(new IntegerType()).setType(Type.STANDARD)
                .setPositionalParams(List.of(dateTimeParam));
    }

    public static FunctionDefinition dayOfYear() {
        return new FunctionDefinition().setName(FunctionConstants.DAY_OF_YEAR).setDisplayName(i18n("dayofyear_func_title")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("dayofyear_func")).setIconPath(format(FunctionsSeed.iconPath, "day-of-year"))
                .setHelpPath("functions." + FunctionConstants.DAY_OF_YEAR)
                .setEngineType(EngineType.FUNCTION).setOutputType(new IntegerType()).setType(Type.STANDARD)
                .setPositionalParams(List.of(dateTimeParam));
    }

    public static FunctionDefinition isAfterNow() {
        return new FunctionDefinition().setName(FunctionConstants.IS_AFTER_NOW).setDisplayName(i18n("is_after_now_func_title")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("isAfterNow_func")).setIconPath(format(FunctionsSeed.iconPath, "is-after-now"))
                .setHelpPath("functions." + FunctionConstants.IS_AFTER_NOW)
                .setEngineType(EngineType.FUNCTION).setOutputType(ObjectType.VALUE).setType(Type.STANDARD)
                .setPositionalParams(List.of(dateTimeParam));
    }

    public static FunctionDefinition isBeforeNow() {
        return new FunctionDefinition().setName(FunctionConstants.IS_BEFORE_NOW).setDisplayName(i18n("is_before_now_func_title"))
                .setScope(Scope.ATTRIBUTE).setHelpSummary(i18n("isBeforeNow_func"))
                .setHelpPath("functions." + FunctionConstants.IS_BEFORE_NOW)
                .setIconPath(format(FunctionsSeed.iconPath, "is-before-now")).setEngineType(EngineType.FUNCTION)
                .setOutputType(new BooleanType()).setType(Type.STANDARD).setPositionalParams(List.of(dateTimeParam));
    }

    public static FunctionDefinition dateFormat() {
        return new FunctionDefinition().setName(FunctionConstants.DATE_FORMAT).setDisplayName(i18n("date_format_title")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("dateFormat_func")).setIconPath(format(FunctionsSeed.iconPath, "date-format"))
                .setHelpPath("functions." + FunctionConstants.DATE_FORMAT)
                .setEngineType(EngineType.FUNCTION).setOutputType(new StringType()).setType(Type.STANDARD)
                .setPositionalParams(List.of(dateTimeParam))
                .setConfiguration(List.of(new FunctionConfiguration().setName("pattern").setLabel("Format Pattern").setHelpSummary(i18n("dateFormat_pattern_func")).setDatatype(new StringType()).setAdditionalProperties(Map.of("hideTokenPicker", true))));
    }
    
    public static FunctionDefinition now() {
        return new FunctionDefinition()
                .setName(FunctionConstants.NOW)
                .setDisplayName(i18n("now"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("now_func"))
                .setHelpPath("functions." + FunctionConstants.NOW)
                .setIconPath(format(FunctionsSeed.iconPath, "now"))
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new DatetimeType())
                .setType(Type.STANDARD)
                .setPositionalParams(List.of(dateTimeParam))
                .setConfiguration(List.of());
    }

    public static FunctionDefinition nowOnEntity() {
        return new FunctionDefinition()
                .setName(FunctionConstants.NOW_ON_ENTITY)
                .setDisplayName(i18n("now"))
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("now_func"))
                .setHelpPath("functions." + FunctionConstants.NOW)
                .setIconPath(format(FunctionsSeed.iconPath, "now"))
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new DatetimeType())
                .setType(Type.STANDARD)
                .setPositionalParams(List.of(dateTimeParam))
                .setConfiguration(List.of(new FunctionConfiguration()
                        .setName("returnValue")
                        .setLabel("Return Value As")
                        .setDatatype(new PicklistType())
                        .setDefaultValue("DATETIME")
                        .setAdditionalProperties(dateTimeTypes())
                        .setRequired(true)));
    }
    
    public static FunctionDefinition minus() {
        return new FunctionDefinition()
                .setName(FunctionConstants.DATE_MINUS)
                .setDisplayName(i18n("minus_func_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("minus_func"))
                .setHelpPath("functions." + FunctionConstants.DATE_MINUS)
                .setIconPath(format(FunctionsSeed.iconPath, "minus-date"))
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new DatetimeType())
                .setType(Type.STANDARD)
                .setPositionalParams(List.of(dateTimeParam))
                .setConfiguration(
                        List.of(
                                new FunctionConfiguration()
                                        .setName("delta")
                                        .setLabel("Amount to subtract")
                                        .setDatatype(new IntegerType())
                                        .setDefaultValue(1)
                                        .setAdditionalProperties(Map.of("hideTokenPicker", true))
                                        .setRequired(true),
                                new FunctionConfiguration()
                                        .setName("unit")
                                        .setLabel("Unit")
                                        .setDatatype(new PicklistType())
                                        .setAdditionalProperties(
                                                chronoUnits()
                                        )
                                        .setRequired(true)
                        )
                );
    }
    
    public static FunctionDefinition plus() {
        return new FunctionDefinition().setName(FunctionConstants.DATE_PLUS)
                .setDisplayName(i18n("plus_func_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("plus_func"))
                .setHelpPath("functions." + FunctionConstants.DATE_PLUS)
                .setIconPath(format(FunctionsSeed.iconPath, "plus"))
                .setEngineType(EngineType.FUNCTION)
                .setOutputType(new DatetimeType())
                .setType(Type.STANDARD)
                .setPositionalParams(List.of(dateTimeParam))
                .setConfiguration(
                        List.of(
                                new FunctionConfiguration()
                                        .setName("delta")
                                        .setLabel("Amount to add")
                                        .setDatatype(new IntegerType())
                                        .setDefaultValue(1)
                                        .setAdditionalProperties(Map.of("hideTokenPicker", true))
                                        .setRequired(true),
                                new FunctionConfiguration()
                                        .setName("unit")
                                        .setLabel("Unit")
                                        .setDatatype(new PicklistType())
                                        .setAdditionalProperties(
                                                chronoUnits()
                                        )
                                        .setRequired(true)
                        )
                );
    }

    public static FunctionDefinition parse() {
        return new FunctionDefinition().setName(FunctionConstants.DATE_PARSE).setDisplayName(i18n("parse_func_title")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary("parse_func_help").setIconPath(format(FunctionsSeed.iconPath, "parse"))
                .setHelpPath("functions." + FunctionConstants.DATE_PARSE)
                .setEngineType(EngineType.FUNCTION).setOutputType(new DateType()).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("dateString", DatatypeFactory.getDatatype("string"),
                        false)))
                .setConfiguration(
                        List.of(
                                new FunctionConfiguration()
                                        .setName("format")
                                        .setLabel("Date Format")
                                        .setHelpSummary("parse_func_date_format")
                                        .setDatatype(new CombolistType())
                                        .setRequired(true)
                                        .setAdditionalProperties(
                                                Map.of("values", dateTimeFormats())
                                        )
                        )
                );
    }

    public static FunctionDefinition dateDiff() {

        return new FunctionDefinition().setName(FunctionConstants.DATE_DIFF).setDisplayName(i18n("date_diff_func_title")).setScope(Scope.ATTRIBUTE)
                .setHelpSummary("date_diff_func_help").setIconPath(format(FunctionsSeed.iconPath, "parse"))
                .setHelpPath("functions." + FunctionConstants.DATE_DIFF)
                .setEngineType(EngineType.FUNCTION).setOutputType(new IntegerType()).setType(Type.STANDARD)
                .setPositionalParams(List.of(dateTimeParam))
                .setConfiguration(
                        List.of(
                                new FunctionConfiguration()
                                        .setName("unit")
                                        .setLabel("Date Difference Unit")
                                        .setHelpSummary("parse_func_date_format")
                                        .setDatatype(new PicklistType())
                                        .setRequired(true)
                                        .setAdditionalProperties(
                                                dateDiffUnits()
                                        ),
                                new FunctionConfiguration()
                                        .setName("fromDate")
                                        .setLabel("From Date")
                                        .setHelpSummary("parse_func_date_format")
                                        .setDatatype(new StringType())
                                        .setDefaultValue("")
                                        .setRequired(true),
                                new FunctionConfiguration().setName("toDate").setDatatype(new StringType()).setLabel("To Date")
                                        .setDefaultValue("")
                                        .setRequired(true).setAdditionalProperties(Map.of())
                                        .setHelpSummary("addToList_value_func_help")

                        )
                );
    }

    public static FunctionDefinition dateDiffOnEntity() {
        return new FunctionDefinition().setName(FunctionConstants.DATE_DIFF_ENTITY).setDisplayName(i18n("date_diff_func_title")).setScope(Scope.ENTITY)
                .setHelpSummary("date_diff_func_help").setIconPath(format(FunctionsSeed.iconPath, "parse"))
                .setHelpPath("functions." + FunctionConstants.DATE_DIFF_ENTITY)
                .setEngineType(EngineType.FUNCTION).setOutputType(new IntegerType()).setType(Type.STANDARD)
                .setPositionalParams(List.of(dateTimeParam))
                .setConfiguration(
                        List.of(
                                new FunctionConfiguration()
                                        .setName("unit")
                                        .setLabel("Date Difference Unit")
                                        .setHelpSummary("parse_func_date_format")
                                        .setDatatype(new PicklistType())
                                        .setRequired(true)
                                        .setAdditionalProperties(
                                                dateDiffUnits()
                                        ),
                                new FunctionConfiguration()
                                        .setName("fromDate")
                                        .setLabel("From Date")
                                        .setHelpSummary("parse_func_date_format")
                                        .setDatatype(new StringType())
                                        .setRequired(true)
                                        .setDefaultValue(""),
                                new FunctionConfiguration().setName("toDate").setDatatype(new StringType()).setLabel("To Date")
                                        .setDefaultValue("").setRequired(true).setAdditionalProperties(Map.of())
                                        .setHelpSummary("addToList_value_func_help")

                        )
                );
    }

    public static Map<String, Object> chronoUnits() {
        return Map.of("values",
                List.of(
                        Map.of("value", "YEARS","label", "Years"),
                        Map.of("value", "MONTHS","label", "Months"),
                        Map.of("value", "DAYS","label", "Days"),
                        Map.of("value", "HOURS","label", "Hours"),
                        Map.of("value", "MINUTES","label", "Minutes"),
                        Map.of("value", "SECONDS","label", "Seconds")
                )
        );
    }
    public static Map<String, Object> dateTimeTypes() {
        return Map.of("values",
                List.of(
                        Map.of("value", DateFunctions.DATETIME,"label", "Date Time"),
                        Map.of("value", DateFunctions.DATE,"label", "Date"),
                        Map.of("value", DateFunctions.SECONDS_OPTION,"label", "Seconds (since epoch)"),
                        Map.of("value", DateFunctions.MILLIS_OPTION,"label", "Milliseconds (since epoch)")
                )
        );
    }

    public static Map<String, Object> dateDiffUnits() {
        return Map.of("values",
                List.of(
                        Map.of("value", "YEARS","label", "Years"),
                        Map.of("value", "MONTHS","label", "Months"),
                        Map.of("value", "DAYS","label", "Days"),
                        Map.of("value", "WEEKS","label", "Weeks"),
                        Map.of("value", "HOURS","label", "Hours"),
                        Map.of("value", "MINUTES","label", "Minutes"),
                        Map.of("value", "SECONDS","label", "Seconds"),
                        Map.of("value", "MILLISECONDS","label", "milliseconds")
                )
        );
    }

    public static List<Map<String, String>> dateTimeFormats() {
        return List.of(
                Map.of("value", "Epoch Timestamp in Seconds","label", "Epoch Timestamp in Seconds"),
                Map.of("value", "Epoch Timestamp in Milliseconds","label", "Epoch Timestamp in Milliseconds")
        );
    }

}
