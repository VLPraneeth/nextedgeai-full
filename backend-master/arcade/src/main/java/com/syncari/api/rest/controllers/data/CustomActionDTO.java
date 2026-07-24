package com.syncari.api.rest.controllers.data;

import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class CustomActionDTO implements Serializable {

    private String id;
    private String apiName;
    private String displayName;
    private String description;
    private String basicHelpText;
    private String helpLink;
    private List<String> tags = List.of();
    private String iconPath;
    private String scope;
    private String status;
    private boolean shareWithOrg;
    private boolean shareGlobally;
}
