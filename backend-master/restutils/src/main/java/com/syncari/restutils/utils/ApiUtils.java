package com.syncari.restutils.utils;

import com.syncari.core.model.Connector;
import com.syncari.core.service.ConnectorService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class ApiUtils {

    public String encodeCursor(String cursor) {
        return Base64.getEncoder().encodeToString(cursor.getBytes());
    }

    public String decodeCursor(String cursor) {
        byte[] cursorDecoded = Base64.getDecoder().decode(cursor);
        return new String(cursorDecoded);
    }

    public Instant getDateTimeInstant(String inputDate) {
        try {
            OffsetDateTime odt = OffsetDateTime.parse(inputDate);
            OffsetDateTime odtUtc = odt.withOffsetSameInstant(ZoneOffset.UTC);
            return Instant.parse(odtUtc.toString());
        } catch (DateTimeParseException e1) {
            try {
                return Instant.parse(inputDate);
            } catch (DateTimeParseException e2) {
                throw e2;
            }
        }
    }

    public Connector maskSensitiveData(Connector connector) {
        if (connector.getAuthConfig() != null) {
            if (StringUtils.isNotBlank(connector.getAuthConfig().getPassword())) {
                connector.getAuthConfig()
                        .setPassword("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getClientSecret())) {
                connector.getAuthConfig()
                        .setClientSecret("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getClientId())) {
                connector.getAuthConfig()
                        .setClientId("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getConsumerKey())) {
                connector.getAuthConfig()
                        .setConsumerKey("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getConsumerSecret())) {
                connector.getAuthConfig()
                        .setConsumerSecret("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getTokenId())) {
                connector.getAuthConfig()
                        .setTokenId("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getTokenSecret())) {
                connector.getAuthConfig()
                        .setTokenSecret("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getToken())) {
                connector.getAuthConfig().setToken("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getAccessToken())) {
                connector.getAuthConfig()
                        .setAccessToken("*****");
            }
            if (StringUtils.isNotBlank(connector.getAuthConfig().getRefreshToken())) {
                connector.getAuthConfig()
                        .setRefreshToken("*****");
            }
            if(connector.getAuthConfig().getAdditionalHeaders()!=null) {
                Map<String, String> decryptedAdditionalConfig = new HashMap<>();
                connector.getAuthConfig().getAdditionalHeaders().forEach((key, value) -> decryptedAdditionalConfig.put(key, "*****"));
                connector.getAuthConfig().setAdditionalHeaders(decryptedAdditionalConfig);
            }
        }
        connector.getMetaConfig().remove(ConnectorService.GCP_CRED_KEY);
        return connector;
    }

}
