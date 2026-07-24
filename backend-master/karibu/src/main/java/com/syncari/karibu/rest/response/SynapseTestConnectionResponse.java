package com.syncari.karibu.rest.response;

import com.google.api.client.util.ArrayMap;
import com.syncari.connector.data.TestConnectionResponse;
import lombok.Data;
import lombok.ToString;
import net.snowflake.client.jdbc.internal.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
@ToString(callSuper=true)
public class SynapseTestConnectionResponse {

    private String message;
    private String code;
    private List<String> errors = new ArrayList<>();
    private Map<String, Object> authConfig;

    public <k extends KaribuResponse, h extends TestConnectionResponse> Object populate(h object) {

        TestConnectionResponse synapseTestConnectionDTO = (TestConnectionResponse) object;

        SynapseTestConnectionResponse response = new SynapseTestConnectionResponse();

        response.setMessage(synapseTestConnectionDTO.getMessage());
        response.setCode(synapseTestConnectionDTO.getCode());
        response.setErrors(synapseTestConnectionDTO.getErrors());
        response.setAuthConfig(getAuthConfig(synapseTestConnectionDTO));

        return response;

    }


    private Map<String, Object> getAuthConfig(TestConnectionResponse testConnection){
        ObjectMapper oMapper = new ObjectMapper();
        Map<String, Object> authConfig = new ArrayMap<>() {};

        if (null != testConnection.getAuthConfig()) {
            authConfig = oMapper.convertValue(testConnection.getAuthConfig(), Map.class);
            authConfig.values().removeIf(Objects::isNull);
            authConfig.put("authType", testConnection.getMetaConfig().get("authType"));
        }

        return authConfig;
    }

}
