package com.syncari.core.utils;

import com.syncari.core.exceptions.SyncariValidationException;
import lombok.SneakyThrows;
import org.passay.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class PasswordConstraintValidator {

    public final static List<Rule> rulesList = Arrays.asList(

            // length between 8 and 32 characters
            new LengthRule(8, 32),

    // at least one upper-case character
                new CharacterRule(EnglishCharacterData.UpperCase, 1),

    // at least one lower-case character
                new CharacterRule(EnglishCharacterData.LowerCase, 1),

    // at least one digit character
                new CharacterRule(EnglishCharacterData.Digit, 1),

    // at least one symbol (special character)
                new CharacterRule(EnglishCharacterData.Special, 1),

    // no whitespace
                new WhitespaceRule(),

    // rejects passwords that contain a sequence of >= 4 characters alphabetical  (e.g. abcd)
                new IllegalSequenceRule(EnglishSequenceData.Alphabetical, 4, false),
    // rejects passwords that contain a sequence of >= 4 characters numerical   (e.g. 1234)
                new IllegalSequenceRule(EnglishSequenceData.Numerical, 4, false),
    // rejects passwords that contain a us keyboard sequence of >= 4 characters   (e.g. qwer, asdf, zxcv etc)
                new IllegalSequenceRule(EnglishSequenceData.USQwerty, 4, false)
        );

    @SneakyThrows
    public static void validatePassword(String password) {

        //customizing validation messages
        Properties props = new Properties();
        InputStream inputStream = PasswordConstraintValidator.class.getClassLoader().getResourceAsStream("password.properties");
        props.load(inputStream);
        MessageResolver resolver = new PropertiesMessageResolver(props);

        PasswordValidator validator = new PasswordValidator(resolver, rulesList);

        RuleResult result = validator.validate(new PasswordData(password));

        if (!result.isValid()) {
            List<String> messages = validator.getMessages(result);
            throw new SyncariValidationException(messages.get(0));
        }
    }

}
