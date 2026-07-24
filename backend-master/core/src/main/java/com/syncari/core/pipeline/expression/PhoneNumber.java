package com.syncari.core.pipeline.expression;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.syncari.core.pipeline.ExpressionVisitor;

import java.util.List;

public class PhoneNumber extends UnaryExpression {
    public static final String NAME = "phone";
    public static final List<String> SUPPORTED_COUNTRIES = List.of("US", "GB", "IN");


    public static boolean isValidPhoneNumber(String number) {
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        String normalizedNumber = number.replaceAll("[^0-9+]", "");

        for (String regionCode : SUPPORTED_COUNTRIES) {
            try {
                Phonenumber.PhoneNumber phoneNumber = phoneUtil.parse(normalizedNumber, regionCode);
                if (phoneUtil.isValidNumber(phoneNumber)) {
                    return true;
                }
            } catch (NumberParseException e) {
                return false;
            }
        }
        return false;
    }

    public PhoneNumber(Expression expression) {
        super(expression);
    }

    public String getName() {
        return NAME;
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        arg.accept(visitor);
        visitor.visit(this);
    }

}