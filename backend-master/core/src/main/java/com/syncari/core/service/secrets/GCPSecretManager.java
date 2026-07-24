package com.syncari.core.service.secrets;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.syncari.core.config.AppConfig;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Base64;

@Component
public class GCPSecretManager implements SecretManager {
    @Autowired
    AppConfig appConfig;

    @SneakyThrows
    public String getSecret(String secretName) {
        final GoogleCredentials googleCredentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(appConfig.getGcpCredentialsKey())));
        final String projectName = appConfig.getGcpProjectId();
        final FixedCredentialsProvider fixedCredentialsProvider = FixedCredentialsProvider.create(googleCredentials);
        final SecretManagerServiceSettings settings = SecretManagerServiceSettings.newBuilder()
                .setCredentialsProvider(fixedCredentialsProvider).build();
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create(settings)) {
            final AccessSecretVersionResponse latest = client.accessSecretVersion(SecretVersionName.of(projectName, secretName, "latest"));
            return latest.getPayload().getData().toStringUtf8();
        }
    }
}
