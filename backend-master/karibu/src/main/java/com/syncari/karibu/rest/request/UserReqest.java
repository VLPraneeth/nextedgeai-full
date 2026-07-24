package com.syncari.karibu.rest.request;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
@AllArgsConstructor
public class UserReqest {
    @NotEmpty(message = "Field email is empty. Please verify this request parameter")
    private String email;
    @NotEmpty(message = "Field firstName is empty. Please verify this request parameter")
    private String firstName;
    @NotEmpty(message = "Field lastName is empty. Please verify this request parameter")
    private String lastName;
    boolean isApiUser;
    @NotEmpty(message = "Field userRoles is empty. Please verify this request parameter")
    private Map<String, Set<String>> userRoles = new HashMap<String, Set<String>>();
}
