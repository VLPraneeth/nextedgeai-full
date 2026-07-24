package com.syncari.karibu.rest.util;

import com.syncari.core.model.User;
import com.syncari.core.service.UserService;
import com.syncari.karibu.rest.request.UserReqest;
import com.syncari.karibu.rest.response.UserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserUtils {

    @Autowired
    UserService userService;

    public User convertUserCreateRequest(UserReqest userReqest) {
        User user = new User();
        user.setEmail(userReqest.getEmail());
        user.setFirstName(userReqest.getFirstName());
        user.setLastName(userReqest.getLastName());
        user.setApiUser(userReqest.isApiUser());
        user.setAdmin(false);
        user.setSuperAdmin(false);
        user.setPassword(User.generatePassword());
        return user;
    }

    public UserResponse convertToUserResponseWithRoles(User user){
        UserResponse response = new UserResponse();

        response.setId(user.getId());
        response.setCreatedBy(user.getCreatedBy());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedBy(user.getUpdatedBy());
        response.setUpdatedAt(user.getUpdatedAt());

        response.setEmail(user.getEmail());
        response.setStatus(user.getStatus());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setApiUser(user.isApiUser());
        response.setClientId(user.getClientId());
        response.setClientSecret(user.getClientSecret());
        response.setUserRoles(userService.getUserRoles(user.getId()));

        return response;
    }
}
