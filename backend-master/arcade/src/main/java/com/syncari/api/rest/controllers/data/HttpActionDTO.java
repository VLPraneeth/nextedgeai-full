package com.syncari.api.rest.controllers.data;

import com.syncari.core.actions.http.AuthenticationInfo;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class HttpActionDTO extends CustomActionDTO {
    private String endpoint;
    private String method;
    private String body;
    private String credentialId;
    private String metadataId;
    private Map<String, String> headers;
    private List<KeyValue> variables;
    private int batchSize;
    private boolean isBatch;
}
