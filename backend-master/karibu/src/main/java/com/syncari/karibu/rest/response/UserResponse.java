package com.syncari.karibu.rest.response;

import com.syncari.core.model.UUIDAuditModel;
import com.syncari.core.model.User;
import com.syncari.core.model.util.Status;
import lombok.Data;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
@ToString(callSuper=true)
public class UserResponse  extends BaseKaribuResponse {

    private String email;
    private Status status;
    private String firstName;
    private String lastName;
    private boolean isApiUser;
    private String clientId;
    private String clientSecret;
    private Map<String, Set<String>> userRoles = new HashMap<String, Set<String>>();

    @Override
    public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
        User user = (User) object;
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

        return response;
    }
}
