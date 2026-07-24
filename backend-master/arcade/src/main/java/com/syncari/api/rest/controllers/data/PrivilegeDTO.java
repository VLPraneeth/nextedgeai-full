package com.syncari.api.rest.controllers.data;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PrivilegeDTO {

    String resourceId;
    String privilegeId;
    String displayName;
}
