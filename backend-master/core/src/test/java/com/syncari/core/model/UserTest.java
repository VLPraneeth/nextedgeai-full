package com.syncari.core.model;

import com.syncari.core.exceptions.SyncariValidationException;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.*;

public class UserTest {

    @Test
    public void validatePassword(){
        User user = new User().setEmail("syncaroo@syncari.com").setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));

        // password is validated for regular user
        try{
            user.validatePassword("testpwd");
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Password must be 8 or more characters in length.", e.getMessage());
        }

        // system user is ignored from password validation
        user.setSystemUser(true);
        user.validatePassword("testpwd");

        // api user is ignored from password validation
        user.setSystemUser(false).setApiUser(true);
        user.validatePassword("testpwd");

        // default syncari_admin is ignored from password validation
        user.setSystemUser(false).setApiUser(false).setEmail("admin@syncari.com");
        user.validatePassword("testpwd");
    }

    @Test
    public void needsPasswordReset(){
        User user = new User().setEmail("syncaroo@syncari.com").setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));

        user.setLastPasswordResetTimestamp(Instant.EPOCH);
        assertTrue(user.hasPasswordExpired());

        // system user is ignored from password validation
        user.setSystemUser(true);
        assertFalse(user.hasPasswordExpired());

        // api user is ignored from password validation
        user.setSystemUser(false).setApiUser(true);
        assertFalse(user.hasPasswordExpired());

        user.setSystemUser(false).setApiUser(false);
        // boundary condition
        // 90 days - 1 second
        user.setLastPasswordResetTimestamp(Instant.now().minus(Duration.ofDays(90)).minus(Duration.ofSeconds(1)));
        assertTrue(user.hasPasswordExpired());

        user.setSystemUser(false).setApiUser(false);
        // 90 days + 1 second
        user.setLastPasswordResetTimestamp(Instant.now().minus(Duration.ofDays(90)).plus(Duration.ofSeconds(1)));
        assertFalse(user.hasPasswordExpired());

        // lastPasswordResetTimestamp is not set - password has not expired
        user.setLastPasswordResetTimestamp(null);
        assertFalse(user.hasPasswordExpired());

        // default syncari_admin is ignored from password validation
        User admin = new User().setEmail("admin@syncari.com").setPassword(System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME"));
        admin.setLastPasswordResetTimestamp(Instant.EPOCH);
        assertFalse(admin.hasPasswordExpired());
    }
}
