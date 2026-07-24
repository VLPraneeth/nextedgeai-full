package com.syncari.core.utils;

import com.syncari.core.exceptions.SyncariValidationException;
import org.junit.Assert;
import org.junit.Test;

public class PasswordConstraintValidatorTest {

    @Test
    public void validatePassword(){
        assertPasswordValidation(() -> PasswordConstraintValidator.validatePassword("syncari"),
                "Password must be 8 or more characters in length.");
        assertPasswordValidation(() -> PasswordConstraintValidator.validatePassword("syncari_syncari_syncari_syncari_syncari"),
                "Password must be no more than 32 characters in length.");
        assertPasswordValidation(() -> PasswordConstraintValidator.validatePassword("syncarirocks"),
                "Password must contain 1 or more uppercase characters.");
        assertPasswordValidation(() -> PasswordConstraintValidator.validatePassword("SYNCARIROCKS"),
                "Password must contain 1 or more lowercase characters.");
        assertPasswordValidation(() -> PasswordConstraintValidator.validatePassword("SyncariRocks"),
                "Password must contain 1 or more digit characters.");
        assertPasswordValidation(() -> PasswordConstraintValidator.validatePassword("SyncariRocks12"),
                "Password must contain 1 or more special characters.");
        assertPasswordValidation(() -> PasswordConstraintValidator.validatePassword("SyncariRocks1234!"),
                "Password contains the illegal numerical sequence '1234'.");
        assertPasswordValidation(() -> PasswordConstraintValidator.validatePassword("Syncari12_abcd"),
                "Password contains the illegal alphabetical sequence 'abcd'.");
        assertPasswordValidation(() -> PasswordConstraintValidator.validatePassword("Syncari12_qwert"),
                "Password contains the illegal QWERTY sequence 'qwert'.");
        assertPasswordValidation(() -> PasswordConstraintValidator.validatePassword("Syncari Rocks 12!"),
                "Password contains a whitespace character.");

        // valid password
        PasswordConstraintValidator.validatePassword("SyncariRocks12!");
    }

    private void assertPasswordValidation(Runnable validate, String msg){
        try{
            validate.run();
        } catch (SyncariValidationException e){
            Assert.assertEquals(msg, e.getMessage());
        }
    }
}
