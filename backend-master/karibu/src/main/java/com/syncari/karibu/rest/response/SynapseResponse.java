package com.syncari.karibu.rest.response;

import com.google.api.client.util.ArrayMap;
import com.syncari.core.model.Connector;
import com.syncari.core.model.UUIDAuditModel;
import lombok.Data;
import lombok.ToString;
import net.snowflake.client.jdbc.internal.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@Data
@ToString(callSuper=true)
public class SynapseResponse extends BaseKaribuResponse {

    private String synapseTypeId;
    private String status;
    private String refreshSchemaStatus;
    private Map<String, Object> configuration;

    @Override
    public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
        Connector synapseDTO = (Connector) object;
        SynapseResponse response = new SynapseResponse();

        response.setId(synapseDTO.getId());
        response.setName(synapseDTO.getName());
        response.setSynapseTypeId(synapseDTO.getMetadataId());
        response.setStatus(synapseDTO.getStatus().toString());
        //response.setConfiguration(getAuthConfig(synapseDTO));
        response.setCreatedBy(synapseDTO.getCreatedBy());
        response.setCreatedAt(synapseDTO.getCreatedAt());
        response.setUpdatedBy(synapseDTO.getUpdatedBy());
        response.setUpdatedAt(synapseDTO.getUpdatedAt());

        return response;

    }

    private Map<String, Object> getAuthConfig(Connector connector){
        ObjectMapper oMapper = new ObjectMapper();
        Map<String, Object> authConfig = new ArrayMap<>() {};

        if (null != connector.getAuthConfig()) {
            authConfig = oMapper.convertValue(connector.getAuthConfig(), Map.class);
            if (authConfig.get("password") != null)
                authConfig.put("password", getBase64Encode(authConfig.get("password").toString()));
            if (authConfig.get("clientSecret") != null)
                authConfig.put("clientSecret", getBase64Encode(authConfig.get("clientSecret").toString()));
            authConfig.values().removeIf(Objects::isNull);
            authConfig.put("authType", connector.getMetaConfig().get("authType"));
        }

        return authConfig;
    }

    private String getBase64Encode(String toEncode) {
        return Base64.getEncoder().encodeToString(toEncode.getBytes());
    }
}
