package com.syncari.connector.custom;

import com.syncari.connector.config.AuthConfig;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@AllArgsConstructor
@Accessors(chain = true)
@ToString
public class CustomAuthConfig extends AuthConfig{
    private CustomAuthType auth_type;

    public CustomAuthConfig() {}

    public CustomAuthConfig(AuthConfig config) {
        super(config);
    }
}
