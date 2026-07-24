package com.syncari.core.functions;

import com.syncari.core.datatype.*;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.Parameter;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;

import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

public class MathFunctionsSeed {

    public static FunctionDefinition getRandom() {
        return getMathFunction("random", List.of(), "random_func_title", "random_func");
    }

    public static FunctionDefinition getRandomOnEntity() {
        String name = "randomOnEntity";
        return new FunctionDefinition()
                .setName(name)
                .setHelpPath("function." + name)
                .setDisplayName(i18n("random_func_title"))
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("random_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "random"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of())
                .setOutputType(new DoubleType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter(
                                "first", DatatypeFactory.getDatatype("object"), false)
                        )
                );
    }

    public static FunctionDefinition getComputeRatio() {
        return getMathFunction("computeRatio", List.of(
                new FunctionConfiguration().setDatatype(DoubleType.VALUE).setName("numerator").setLabel("compute_ratio_numerator_label").setHelpSummary("compute_ratio_numerator_help").setHelpText("compute_ratio_numerator_help"),
                new FunctionConfiguration().setDatatype(DoubleType.VALUE).setName("denominator").setLabel("compute_ratio_denominator_label").setHelpSummary("compute_ratio_denominator_help").setHelpText("compute_ratio_denominator_help"),
                new FunctionConfiguration().setDatatype(BooleanType.VALUE).setName("asPercentage").setLabel("compute_ratio_percentage_label").setHelpSummary("compute_ratio_percentage_help").setHelpText("compute_ratio_percentage_help").setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration().setDatatype(IntegerType.VALUE).setName("roundTo").setLabel("compute_ratio_round_label").setHelpSummary("compute_ratio_round_help").setHelpText("compute_ratio_round_help")
                ),
                "compute_ratio_title", "compute_ratio_help","compute-ratio");
    }


    public static FunctionDefinition getAbs() {
        return getMathFunction("abs", List.of(), "abs_func_title", "abs_func","absolute");
    }

    public static FunctionDefinition getFloor() {
        return getMathFunction("floor", List.of(), "floor_func_title", "floor_func");
    }

    public static FunctionDefinition getFloorOnEntity() {
        String name = "floorOnEntity";
        return new FunctionDefinition()
                .setName(name)
                .setHelpPath("function." + name)
                .setDisplayName(i18n("floor_func_title"))
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("floor_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "floor"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of(new FunctionConfiguration()
                        .setName("value")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("value_label")
                        .setHelpSummary("value_help")
                        .setHelpText("value_help")
                        .setDefaultValue("")))
                .setOutputType(new DoubleType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter(
                                "first", DatatypeFactory.getDatatype("object"), false)
                        )
                );
    }

    public static FunctionDefinition getCeil() {
        return getMathFunction("ceil", List.of(), "ceil_func_title", "ceil_func","ceiling");
    }

    public static FunctionDefinition getCeilOnEntity() {
        String name = "ceilOnEntity";
        return new FunctionDefinition()
                .setName(name)
                .setHelpPath("function." + name)
                .setDisplayName(i18n("ceil_func_title"))
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("ceil_func_help"))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, "ceiling"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(List.of(new FunctionConfiguration()
                        .setName("value")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("value_label")
                        .setHelpSummary("value_help")
                        .setHelpText("value_help")
                        .setDefaultValue("")))
                .setOutputType(new DoubleType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter(
                                "first", DatatypeFactory.getDatatype("object"), false)
                        )
                );
    }

    public static FunctionDefinition getMin() {
        return getMathFunction("min", List.of(), "min_func_title", "min_func");
    }

    public static FunctionDefinition getMax() {
        return getMathFunction("max", List.of(), "max_func_title", "max_func");
    }

    public static FunctionDefinition getRound() {
        final String helpKey = "round_func";
        Set<RoundingMode> excludedModes = Set.of(RoundingMode.UNNECESSARY, RoundingMode.FLOOR, RoundingMode.CEILING);
        final List<Map<String, String>> roundingModeConfig = Arrays.stream(RoundingMode.values())
                .filter(r -> !excludedModes.contains(r))
                .map(r -> Map.of("value", r.name(), "label", i18n(r.name())))
                .collect(Collectors.toList());
        return getMathFunction("round", List.of(
                new FunctionConfiguration()
                        .setName("value")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("value_label")
                        .setHelpSummary("value_help")
                        .setHelpText("value_help")
                        .setDefaultValue(""),
                new FunctionConfiguration()
                        .setName("decimalPoints")
                        .setDatatype(IntegerType.VALUE)
                        .setRequired(true)
                        .setLabel("decimal_points_label")
                        .setHelpSummary("decimal_points_help")
                        .setHelpText("decimal_points_help")
                        .setDefaultValue(0),
                new FunctionConfiguration()
                        .setLabel(i18n("rounding_mode_label")).setHelpSummary(i18n("rounding_mode_help"))
                        .setName("roundingMode").setDatatype(PicklistType.VALUE)
                        .setDefaultValue(RoundingMode.HALF_UP.name())
                        .setAdditionalProperties(Map.of("values", roundingModeConfig))
        ), "round_func_title", helpKey);
    }

    public static FunctionDefinition getMultiply() {
        return getMathFunction("multiply", List.of(new FunctionConfiguration()
                .setLabel(i18n("multiply_factor_label")).setHelpSummary(i18n("multiply_factor_help"))
                .setName("multiplyBy").setDatatype( DoubleType.VALUE)), "multiply_func_title", "multiply_func");
    }

    public static FunctionDefinition getMultiplyOnEntity() {
        return getMathFunction("multiplyOnEntity", List.of(
                new FunctionConfiguration()
                        .setName("value")
                        .setDatatype(StringType.VALUE)
                        .setRequired(true)
                        .setLabel("value_label")
                        .setHelpSummary("value_help")
                        .setHelpText("value_help")
                        .setDefaultValue(""),
                new FunctionConfiguration()
                        .setLabel(i18n("multiply_factor_label")).setHelpSummary(i18n("multiply_factor_help"))
                        .setName("multiplyBy").setDatatype( DoubleType.VALUE)
                ), "multiply_func_title", "multiply_func", "multiply")
                .setScope(Scope.ENTITY);
    }

    public static FunctionDefinition getIncrement() {
        return getMathFunction("increment", List.of(new FunctionConfiguration().setLabel(i18n("increment_amount_to_add_label"))
                .setHelpSummary(i18n("increment_amount_to_add_help")).setName("amountToAdd")
                .setDatatype( DoubleType.VALUE)), "increment_func_title", "increment_func");
    }
    
    public static FunctionDefinition getIncrementOnEntity() {
      return getMathFunction("incrementOnEntity", List.of(
              new FunctionConfiguration()
                      .setName("value")
                      .setDatatype(StringType.VALUE)
                      .setRequired(true)
                      .setLabel("value_label")
                      .setHelpSummary("value_help")
                      .setHelpText("value_help")
                      .setDefaultValue(""),
                      new FunctionConfiguration().setLabel(i18n("increment_amount_to_add_label"))
                      .setHelpSummary(i18n("increment_amount_to_add_help")).setName("amountToAdd")
                      .setDatatype( DoubleType.VALUE)), "increment_func_title", "increment_func", "increment")
              .setScope(Scope.ENTITY);
  }

    public static FunctionDefinition getDecrement() {
        return getMathFunction("decrement", List.of(new FunctionConfiguration()
                .setLabel(i18n("decrement_amount_to_subtract_label")).setHelpSummary(i18n("decrement_amount_to_subtract_help"))
                .setName("amountToSubtract").setDatatype( DoubleType.VALUE)), "decrement_func_title", "decrement_func");
    }

    public static FunctionDefinition getMathFunction(String name, List<FunctionConfiguration> configuration, String titleKey, String helpKey) {
        return getMathFunction(name, configuration, titleKey, helpKey,name);
    }
    public static FunctionDefinition getMathFunction(String name, List<FunctionConfiguration> configuration, String titleKey, String helpKey,String iconName) {
        return new FunctionDefinition()
                .setName(name)
                .setHelpPath("function." + name)
                .setDisplayName(i18n(titleKey))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n(helpKey))
                .setHelpPath("functions." + name)
                .setIconPath(format(FunctionsSeed.iconPath, iconName))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setOutputType(new DoubleType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter(
                                "first", DatatypeFactory.getDatatype("object"), false)
                        )
                );
    }
}
