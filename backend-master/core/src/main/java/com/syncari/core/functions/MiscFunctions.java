package com.syncari.core.functions;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.ValidationResult;
import com.google.i18n.phonenumbers.Phonenumber;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionResult;
import com.syncari.core.pipeline.FilterFailedResult;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.service.TagService;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MiscFunctions extends FunctionsBase {
    private static final String PLUS = "+";
    @Autowired
    TagService tagService;
    @Autowired
    TokenHelper tokenHelper;

//    @Function
//    public boolean hasTag(String targetId, String tagName, Taggable type) {
//        return tagService.hasTag(tagName, type, targetId);
//    }
    
    @Function
    public Object formatPhoneOnEntity(Object input, FunctionCall functionCall, GraphContext context) {
        if (input == null) return null;
        String value = tokenHelper.resolveTokens(context, getConfigOrDefault("value", functionCall, "", context));
        String phone = formatPhone(value, functionCall, context);
        context.put("previousValue", phone);
        context.put("Value From " + context.getCurrentNode().getName(), phone);

        return new FunctionResult(input, ObjectType.VALUE);
    }

    @Function
    public String formatPhone(String phoneNumber, FunctionCall functionCall, GraphContext context) {
        if (StringUtils.isBlank(phoneNumber)) {
            putInContext(context, false);
            return phoneNumber;
        }
        phoneNumber = StringUtils.trim(phoneNumber);
        String original = phoneNumber;
        String format = getConfig("format", functionCall, context);
        String countryCodeField = getConfig("countryCodeField", functionCall, context);
        if (countryCodeField == null) countryCodeField = "";
        String resolvedCountryCode = tokenHelper.resolveTokens(context, countryCodeField);
        String defaultCountryCode = getConfig("defaultCountryCode", functionCall, context);
        String computed = StringUtils.isBlank(resolvedCountryCode) ? defaultCountryCode : resolvedCountryCode;
        String lookup = StringUtils.EMPTY;
        if (!StringUtils.isBlank(computed)) {
            lookup = CountryCodeMap.lookup(computed);
        }
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        try {
            // Removing leading 0s
            phoneNumber = StringUtils.stripStart(phoneNumber, "0");
            // If the number starts with + but does not have a country code in it, use the incoming country code
            if(phoneNumber.startsWith(PLUS) && !StringUtils.isBlank(lookup)) {
                String stripped = StringUtils.stripStart(phoneNumber, "+");
                if(!stripped.startsWith(lookup)) {
                    phoneNumber = PLUS + lookup + stripped;
                }
            }
            // If that is invalid, append country code
            if(!isValidNumber(phoneNumber, phoneUtil) || !phoneNumber.startsWith(PLUS)) {
                if(!StringUtils.isBlank(lookup)) {
                    if(!phoneNumber.startsWith(PLUS)) {
                        if(phoneNumber.startsWith(lookup)) {
                            phoneNumber = PLUS + phoneNumber;
                        } else {
                            phoneNumber = PLUS + lookup + phoneNumber;
                        }
                    } else {
                        phoneNumber = phoneNumber.replace(PLUS, PLUS + lookup);
                    }
                }
            }
            // If that is invalid, the number is not valid anymore
            if(!isValidNumber(phoneNumber, phoneUtil)) {
                putInContext(context, false);
                return original;
            }
            PhoneNumber parsedStr = phoneUtil.parse(phoneNumber, null);
            boolean isFormatted = true;
            switch (PhoneNumberFormat.valueOf(format)) {
                case E164:
                    phoneNumber = phoneUtil.format(parsedStr, PhoneNumberFormat.E164);
                    break;
                case NATIONAL:
                    phoneNumber = phoneUtil.format(parsedStr, PhoneNumberFormat.NATIONAL);
                    break;
                case INTERNATIONAL:
                    phoneNumber = phoneUtil.format(parsedStr, PhoneNumberFormat.INTERNATIONAL);
                    break;

                default:
                    isFormatted = false;
                    phoneNumber = original;
            }
            putInContext(context, isFormatted);
            return phoneNumber;
        } catch (Exception e) {
            log.error("Error while formatting phone number {} to format {}, error {}", phoneNumber, format,
                    e.getMessage());
            putInContext(context, false);
            return original;
        }
    }

    private void putInContext(GraphContext context, boolean value) {
        if(context.getCurrentNode() != null) {
            context.put("Validation From " + context.getCurrentNode().getName(), value);
        }
    }
    @Function
    @AcceptsFilterValue
    public Object isTrue(Object input, FunctionCall functionCall, GraphContext context) {
        //Will just propagate the first parameter. Upstream filter sends either its input record,
        // or a FilterFailedResult
        return input;
    }

    @Function
    @AcceptsFilterValue
    public Object isFalse(Object input, FunctionCall functionCall, GraphContext context) {

        //If the filter evaluates to false, this branch in the pipeline should go forward.
        //Extract the original value from the filter results and pass along
        if (FilterFailedResult.isFailedFilter(input)) {
            FilterFailedResult failedResult = FilterFailedResult.normalizedFailedResult(input);
            //If the failure has an invalid result (typically when the sources are empty, or if the failure was propagated from 2 steps before)
            // return failure
            if(failedResult.hasInvalidResults()){
                return input;
            }
            return failedResult.getValue();
        } else {
            //The filter matched. So isFalse should stop the pipeline here
            return new FilterFailedResult(input);
        }
    }

    @Function
    @AcceptsFilterValue
    public Object predicate(Object input, FunctionCall functionCall, GraphContext context) {
        Boolean value = getConfig("value", functionCall, context);
        if(value) {
            return input;
        } else {
            if (FilterFailedResult.isFailedFilter(input)) {
                FilterFailedResult failedResult = FilterFailedResult.normalizedFailedResult(input);
                //If the failure has an invalid result (typically when the sources are empty, or if the failure was propagated from 2 steps before)
                // return failure
                if(failedResult.hasInvalidResults()){
                    return input;
                }
                return failedResult.getValue();
            } else {
                //The filter matched. So isFalse should stop the pipeline here
                return new FilterFailedResult(input);
            }
        }
    }

    public static boolean isValidNumber(String phoneNumber, PhoneNumberUtil phoneUtil) {
        return isValidNumber(phoneNumber, phoneUtil, "");
    }
    
    public static boolean isValidNumber(String phoneNumber, PhoneNumberUtil phoneUtil, String defaultRegion) {
        try {
            Phonenumber.PhoneNumber number = phoneUtil.parseAndKeepRawInput(phoneNumber, defaultRegion);
            ValidationResult possibleNumber = phoneUtil.isPossibleNumberWithReason(number);
            return possibleNumber == ValidationResult.IS_POSSIBLE && phoneUtil.isValidNumber(number);
        } catch (NumberParseException e) {
            return false;
        }
    }


}
