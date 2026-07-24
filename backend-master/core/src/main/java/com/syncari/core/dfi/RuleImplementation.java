package com.syncari.core.dfi;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.syncari.connector.EntityData;
import com.syncari.core.functions.MiscFunctions;
import com.syncari.core.functions.TextFunctions;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.ParameterValue;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.utils.DateUtil;
import com.syncari.utils.TextUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class RuleImplementation {
    @Autowired
    MiscFunctions miscFunctions;
    @Autowired
    DateUtil dateUtil;

    @Autowired
    TextFunctions textFunctions;

    private static final RuleResult MATCH = new RuleResult(RuleResultType.match);
    private static final RuleResult FAIL = new RuleResult(RuleResultType.fail);
    private static final RuleResult NA = new RuleResult(RuleResultType.na);
    private final Map<String, Function<Object, RuleResult>> rules = new HashMap<>();
    private final Map<String, Function2<Object, Object, RuleResult>> multiValueRules = new HashMap<>();

    public RuleImplementation() {
        rules.put(RuleConstants.IS_CAMEL_CASED, (Object value) -> isCamelCase(value));
        rules.put(RuleConstants.IS_NOT_EMPTY, (Object value) -> isNotEmpty(value));
        rules.put(RuleConstants.IS_NOT_STALE, (Object value) -> isNotStale(value));
        rules.put(RuleConstants.IS_VALID_EMAIL, (Object value) -> isValidEmail(value));
        rules.put(RuleConstants.IS_PHONE_FORMATTED, (Object value) -> isPhoneFormatted(value));
        rules.put(RuleConstants.IS_NUMBER, (Object value) -> isNumber(value));
        multiValueRules.put(RuleConstants.MATCHES_REGEX, (Object value, Object conditionValue) -> matchesRegex(value, conditionValue));
        multiValueRules.put(RuleConstants.WITHIN_NUMERIC_RANGE, (Object value, Object conditionValue) -> withinNumericRange(value, conditionValue));
        multiValueRules.put(RuleConstants.WITHIN_LENGTH_RANGE, (Object value, Object conditionValue) -> withinLengthRange(value, conditionValue));
        multiValueRules.put(RuleConstants.WITHIN_DATE_RANGE, (Object value, Object conditionValue) -> withinDateRange(value, conditionValue));
    }
    
    public RuleResult execute(String ruleName, Object param) {
        return rules.get(ruleName).apply(param);
    }

    public RuleResult execute(String ruleName, Object param, Object jsonValue) {
        if (jsonValue == null || StringUtils.isEmpty(jsonValue.toString())
            || (jsonValue instanceof List && ((List) jsonValue).size() == 0)) {
            return execute(ruleName, param);
        }
        return multiValueRules.get(ruleName).apply(param, jsonValue);
    }

    private RuleResult isCamelCase(Object value) {
        if (value == null)
            return NA;
        String camelCased = textFunctions.camelCase(List.of(value), null, null);
        if (!camelCased.equals(value)) {
            return FAIL;
        }
        return MATCH;
    }

    private RuleResult isNotEmpty(Object value) {
        if (value == null || "".equalsIgnoreCase(value.toString()))
            return FAIL;
        return MATCH;
    }

    private RuleResult isNotStale(Object value) {
        if (value == null || !(value instanceof EntityData))
            return NA;
        EntityData converted = (EntityData) value;
        if (converted.getSyncariTimestamp() > Instant.now().minus(30, ChronoUnit.DAYS).getEpochSecond())
            return FAIL;
        return MATCH;
    }

    private RuleResult isValidEmail(Object value) {
        if (value == null)
            return NA;
        Pattern r = Pattern.compile(TextUtil.VALID_EMAIL_REGEX);
        Matcher m = r.matcher(value.toString());
        if (m.find())
            return MATCH;
        else
            return FAIL;
    }
    
    private RuleResult isPhoneFormatted(Object value) {
        if (value == null)
            return NA;
        
        Map<String, Object> config = new HashMap<>();
        config.put("format", PhoneNumberFormat.E164.name());
        FunctionCall call = new FunctionCall().setConfig(config ).setParams(List.of(ParameterValue.string("param", "input")));
        GraphContext context = new GraphContext();
        String formatted = miscFunctions.formatPhone(value.toString(), call, context);
        if (!MiscFunctions.isValidNumber(formatted, PhoneNumberUtil.getInstance())) {
            config.put("format", PhoneNumberFormat.INTERNATIONAL.name());
            formatted = miscFunctions.formatPhone(value.toString(), call, context);
            if (!MiscFunctions.isValidNumber(formatted, PhoneNumberUtil.getInstance())) {
                config.put("format", PhoneNumberFormat.NATIONAL.name());
                formatted = miscFunctions.formatPhone(value.toString(), call, context);
                if (!MiscFunctions.isValidNumber(formatted, PhoneNumberUtil.getInstance())) {
                    // finally try with a leading + (its ok if there was already one and if it was valid should have passed above),
                    // and also try US as default region.
                    if (!MiscFunctions.isValidNumber("+" + formatted, PhoneNumberUtil.getInstance()) &&
                        !MiscFunctions.isValidNumber(formatted, PhoneNumberUtil.getInstance(), "US")) {
                        return FAIL;
                    }
                }
            }
        }
        return MATCH;
    }

    private RuleResult matchesRegex(Object value, Object regexValue) {
        if (value == null || "".equalsIgnoreCase(value.toString()))
            return FAIL;
        Pattern pattern = Pattern.compile(((List) regexValue).get(0).toString(), Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(value.toString());
        if (m.find())
            return MATCH;
        else
            return FAIL;
    }

    private RuleResult withinLengthRange(Object value, Object range) {
        if (value == null || "".equalsIgnoreCase(value.toString()))
            return FAIL;
        List<Integer> lengthRange = ((List<String>) range).stream().map(NumberUtils::toInt).collect(Collectors.toList());
        if (isNumber(lengthRange.get(0)) == FAIL || isNumber(lengthRange.get(1)) == FAIL)
            return FAIL;
        if (value.toString().length() < lengthRange.get(0) || value.toString().length() > lengthRange.get(1))
            return FAIL;
        return MATCH;
    }

    private RuleResult isNumber(Object value) {
        if (value == null || !(value instanceof Number))
            return FAIL;
        return MATCH;
    }

    private RuleResult withinNumericRange(Object value, Object range) {
        if (isNumber(value) == FAIL)
            return FAIL;
        List<Double> doubleRange = ((List<String>) range).stream().map(NumberUtils::toDouble).collect(Collectors.toList());
        Number numericValue =  (Number) value;
        if ((numericValue.doubleValue() < doubleRange.get(0) || (numericValue.doubleValue() > doubleRange.get(1))))
            return FAIL;
        return MATCH;
    }

    private RuleResult withinDateRange(Object value, Object range) {
        if (value == null || "".equalsIgnoreCase(value.toString()))
            return FAIL;
        List<String> dateRange = (List<String>) range;
        long millisValue = dateUtil.toEpochMilli(value.toString());
        if (millisValue < dateUtil.toEpochMilli(dateRange.get(0)) || millisValue > dateUtil.toEpochMilli(dateRange.get(1)))
            return FAIL;
        return MATCH;
    }

    @FunctionalInterface
    interface Function2<One, Two, Three> {
        public Three apply(One one, Two two);
    }
}
