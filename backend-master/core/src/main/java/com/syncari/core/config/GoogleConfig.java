package com.syncari.core.config;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.storage.v1.BigQueryWriteClient;
import com.google.cloud.bigquery.storage.v1.BigQueryWriteSettings;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.syncari.core.event.store.BigQueryEventStore;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.service.secrets.SecretManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@Component
@Slf4j
public class GoogleConfig {
    @Autowired
    AppConfig appConfig;
    @Autowired
    SecretManager secretManager;

    @Value("${nextedge.aws.mode:false}")
    boolean awsMode;

    @Value("${nextedge.gcs.endpoint:http://gcs-emulator:4443}")
    String gcsEndpoint;

    @Value("${nextedge.bigquery.endpoint:http://bigquery-emulator:9050}")
    String bigQueryEndpoint;

    @Value("${nextedge.bigquery.grpc.endpoint:bigquery-emulator:9060}")
    String bigQueryGrpcEndpoint;

    @Bean("bigQuery")
    public BigQuery bigQueryService() throws Exception {
        if (awsMode) {
            log.info("Using the private NextEdge BigQuery compatibility endpoint at {}", bigQueryEndpoint);
            return BigQueryOptions.newBuilder()
                    .setProjectId(appConfig.getGcpProjectId())
                    .setHost(bigQueryEndpoint)
                    .setCredentials(emulatorCredentials())
                    .build()
                    .getService();
        }
        Collection<String> scopes = List.of("https://www.googleapis.com/auth/bigquery",
                "https://www.googleapis.com/auth/bigquery.insertdata");
        Credentials credentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(appConfig.gcpCredentialsKey)))
                .createScoped(scopes);
        return BigQueryOptions.newBuilder()
                .setProjectId(appConfig.getGcpProjectId())
                .setCredentials(credentials)
                .build()
                .getService();
    }

    @Bean("storageService")
    public Storage storageService() throws Exception {
		if (awsMode) {
			Storage storage = StorageOptions.newBuilder()
					.setProjectId(appConfig.getGcpProjectId())
					.setHost(gcsEndpoint)
					.setCredentials(emulatorCredentials())
					.build()
					.getService();
			ensureBucket(storage, appConfig.getGcsBucketName());
			ensureBucket(storage, appConfig.getGcsCfBucketName());
			log.info("Using the private NextEdge object-storage compatibility endpoint at {}", gcsEndpoint);
			return storage;
		}
        Credentials credentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(appConfig.getGcpCredentialsKey())));

        return StorageOptions.newBuilder()
                .setProjectId(appConfig.getGcpProjectId())
                .setCredentials(credentials)
                .build()
                .getService();
    }

    @Bean
    public BigQueryWriteClient bigQueryWriteClient(@Qualifier("bigQueryWriteSettings") BigQueryWriteSettings bigQueryWriteSettings) {
        try {
            return BigQueryWriteClient.create(bigQueryWriteSettings);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create BigQueryWriteClient", e);
        }
    }

    @Bean("bigQueryWriteSettings")
    public BigQueryWriteSettings bigQueryWriteSettings() throws IOException {
        if (awsMode) {
            ManagedChannel channel = ManagedChannelBuilder.forTarget(bigQueryGrpcEndpoint)
                    .usePlaintext()
                    .build();
            return BigQueryWriteSettings.newBuilder()
                    .setCredentialsProvider(NoCredentialsProvider.create())
                    .setTransportChannelProvider(FixedTransportChannelProvider.create(
                            GrpcTransportChannel.create(channel)))
                    .build();
        }
        Collection<String> scopes = List.of(
                "https://www.googleapis.com/auth/bigquery",
                "https://www.googleapis.com/auth/bigquery.insertdata"
        );
        try (InputStream credentialsStream = new ByteArrayInputStream(Base64.getDecoder().decode(appConfig.gcpCredentialsKey))) {
            Credentials credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(scopes);
            CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(credentials);
            BigQueryWriteSettings settings = BigQueryWriteSettings.newBuilder().setCredentialsProvider(credentialsProvider).build();
            log.info("BigQueryWriteSettings created with CredentialsProvider: {}", settings.getCredentialsProvider() != null);
            return settings;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create BigQueryWriteSettings from credentials", e);
        }
    }

    @Bean("gcsCfFileManager")
    public GCSFileManager gcsCfFileManager(@Qualifier("storageService") Storage storage) throws Exception {
        if (awsMode) {
            return new GCSFileManager(storage, appConfig);
        }
        Credentials credentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(appConfig.getCfDeployerCredentialsKey())));

        Storage s = StorageOptions.newBuilder()
                .setProjectId(appConfig.getGcpProjectId())
                .setCredentials(credentials)
                .build()
                .getService();
        return new GCSFileManager(s, appConfig);
    }

    @Bean("gcsImportedFilesFileManager")
    public GCSFileManager gcsImportedFilesFileManager(@Qualifier("storageService") Storage storage) throws Exception {
        if (awsMode) {
            return new GCSFileManager(storage, appConfig);
        }
        String importedFilesCredentials = "local".equalsIgnoreCase(appConfig.getEnvName())
                ? appConfig.getGcpCredentialsKey()
                : secretManager.getSecret("imported_files_gcp_credentials_encoded_key");
        Credentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(
                Base64.getDecoder().decode(importedFilesCredentials)));

        Storage s = StorageOptions.newBuilder()
                .setProjectId(appConfig.getGcpProjectId())
                .setCredentials(credentials)
                .build()
                .getService();
        return new GCSFileManager(s, appConfig);
    }

    @Bean
    @DependsOn({"bigQuery"})
    public EventStore eventStore() {
        return new BigQueryEventStore();
    }

    private void ensureBucket(Storage storage, String bucketName) {
        if (bucketName != null && !bucketName.isBlank() && storage.get(bucketName) == null) {
            storage.create(BucketInfo.of(bucketName));
        }
    }

    private Credentials emulatorCredentials() {
        return GoogleCredentials.create(new AccessToken("nextedge-local-emulator", new Date(Long.MAX_VALUE)));
    }
}
