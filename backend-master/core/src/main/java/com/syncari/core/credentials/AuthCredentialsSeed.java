package com.syncari.core.credentials;

import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.AuthType;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.PasswordType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.FunctionDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthCredentialsSeed {

    private static Map<String, AuthMetadata> credentialMap = new HashMap<>();

    static {
        credentialMap.put(Constants.GENERIC_NONE, none());
        credentialMap.put(Constants.GENERIC_API_KEY, apiKey());
        credentialMap.put(Constants.GENERIC_SIMPLE_OAUTH, oAuth());
        credentialMap.put(Constants.GENERIC_BEARER_TOKEN, bearerToken());
    }

    public static AuthMetadata apiKey() {
        var fields = List.of(new AuthField().setName("token").setDataType(PasswordType.NAME).setLabel("API Key"));
        return new AuthMetadata(AuthType.ApiKey, fields, "API Key", "");
    }

    public static AuthMetadata oAuth() {
        var fields = List.of(
                new AuthField().setName("clientId").setDataType(PasswordType.NAME).setLabel("Client ID"),
                new AuthField().setName("clientSecret").setDataType(PasswordType.NAME).setLabel("Client Secret"),
                new AuthField().setName("endpoint").setDataType(StringType.NAME).setLabel("Endpoint")
                );
        return new AuthMetadata(AuthType.SimpleOAuth, fields, "OAuth", "");
    }

    public static AuthMetadata bearerToken() {
        var fields = List.of(
                new AuthField().setName("accessToken").setDataType(PasswordType.NAME).setLabel("Bearer Token")
        );
        return new AuthMetadata(AuthType.ApiSecretKey, fields, "Bearer Token", "");
    }

    public static AuthMetadata getAuthMetadata(String name) {
        return credentialMap.get(name);
    }
    
    public static AuthMetadata none() {
      return new AuthMetadata(AuthType.None, List.of(), "None", "");
  }
}
