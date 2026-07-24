package com.syncari.restutils.validations;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.syncari.utils.I18n.i18n;

@Component
public class OrganisationValidations {

    @Autowired
    UserService userService;

    public void validateNewUser(String userEmail, Boolean isAPIUser, Map<String, Set<String>> userRoles) {
        if (isAPIUser && userService.getUserByEmail(userEmail).isPresent())
            throw new SyncariValidationException(String.format(i18n("already_existing_user"), userEmail));
        validateUserRoles(userRoles);
    }

    public void validateUserRoles(Map<String, Set<String>> userRoles) {
        userRoles.forEach((k, v) -> {
            if(v.size() < 1) {
                throw new RuntimeException(String.format(i18n("missing_user_roles"), k));
            }
        });

    }
}
