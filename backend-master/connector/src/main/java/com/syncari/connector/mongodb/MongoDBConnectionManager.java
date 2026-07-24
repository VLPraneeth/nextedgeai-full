package com.syncari.connector.mongodb;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.syncari.connector.ConnectorInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MongoDBConnectionManager {

    private static final int DEFAULT_PORT = 27017;
    private static final String DEFAULT_AUTH_DATABASE = "admin";
    private static final int CONNECTION_TIMEOUT_MS = 300000; // 5 minutes
    private static final int SOCKET_TIMEOUT_MS = 300000; // 5 minutes

    private final LoadingCache<String, MongoClient> clientCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .removalListener(notification -> {
                if (notification.getValue() != null) {
                    MongoClient client = (MongoClient) notification.getValue();
                    try {
                        client.close();
                        log.info("MongoDB client closed successfully for key: {}", notification.getKey());
                    } catch (Exception e) {
                        log.warn("Error closing MongoDB client: {}", e.getMessage());
                    }
                }
            })
            .build(new CacheLoader<String, MongoClient>() {
                @Override
                public MongoClient load(String cacheKey) {
                    // This will never be called directly - we use get() with connector info
                    throw new UnsupportedOperationException("Use getClient(ConnectorInfo) instead");
                }
            });

    /**
     * Get or create a MongoClient for the given connector configuration
     */
    public MongoClient getClient(ConnectorInfo connector) {
        String cacheKey = buildCacheKey(connector);
        try {
            return clientCache.get(cacheKey, () -> createMongoClient(connector));
        } catch (Exception e) {
            log.error("Failed to get MongoDB client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create MongoDB connection", e);
        }
    }

    /**
     * Close and invalidate the client for a connector
     */
    public void closeClient(ConnectorInfo connector) {
        String cacheKey = buildCacheKey(connector);
        clientCache.invalidate(cacheKey);
    }

    /**
     * Build cache key from connector configuration
     */
    private String buildCacheKey(ConnectorInfo connector) {
        return connector.getId() + "_" + connector.getInstanceId();
    }

    /**
     * Create a new MongoClient based on connector configuration
     */
    private MongoClient createMongoClient(ConnectorInfo connector) {
        log.info("Creating new MongoDB client for connector: {}", connector.getId());

        String connectionString = getMetaValue(connector, "connectionString");

        if (StringUtils.isNotBlank(connectionString)) {
            // Use connection string override
            return createClientFromConnectionString(connectionString, connector);
        } else {
            // Build from components
            return createClientFromComponents(connector);
        }
    }

    /**
     * Create MongoClient from connection string
     */
    private MongoClient createClientFromConnectionString(String connectionString, ConnectorInfo connector) {
        try {
            String fullConnectionString;

            // Check if connection string already has protocol
            if (connectionString.startsWith("mongodb://") || connectionString.startsWith("mongodb+srv://")) {
                // Use as-is (full override)
                fullConnectionString = connectionString;
            } else {
                // Prepend protocol and credentials
                String username = connector.getAuthConfig().getUserName();
                String password = connector.getAuthConfig().getPassword();

                StringBuilder builder = new StringBuilder("mongodb://");

                if (StringUtils.isNotBlank(username)) {
                    builder.append(username);
                    if (StringUtils.isNotBlank(password)) {
                        builder.append(":").append(password);
                    }
                    builder.append("@");
                }

                builder.append(connectionString);
                fullConnectionString = builder.toString();
            }

            ConnectionString connString = new ConnectionString(fullConnectionString);
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connString)
                    .applyToSocketSettings(builder ->
                            builder.connectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                                    .readTimeout(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .build();

            return MongoClients.create(settings);
        } catch (Exception e) {
            log.error("Failed to create MongoDB client from connection string: {}", e.getMessage());
            throw new RuntimeException("Invalid MongoDB connection string: " + e.getMessage(), e);
        }
    }

    /**
     * Create MongoClient from individual components
     */
    private MongoClient createClientFromComponents(ConnectorInfo connector) {
        String host = getMetaValue(connector, "host");
        String portStr = getMetaValue(connector, "port");
        String database = getMetaValue(connector, "database");
        String authDatabase = getMetaValue(connector, "authDatabase");
        String username = connector.getAuthConfig().getUserName();
        String password = connector.getAuthConfig().getPassword();
        boolean useSsl = Boolean.parseBoolean(getMetaValue(connector, "useSsl", "true"));
        boolean validateCertificates = Boolean.parseBoolean(getMetaValue(connector, "sslValidateCertificates", "false"));

        // Validate required fields
        if (StringUtils.isBlank(host)) {
            throw new RuntimeException("MongoDB host is required");
        }
        if (StringUtils.isBlank(database)) {
            throw new RuntimeException("MongoDB database name is required");
        }

        int port = StringUtils.isNotBlank(portStr) ? Integer.parseInt(portStr) : DEFAULT_PORT;
        String authDb = StringUtils.isNotBlank(authDatabase) ? authDatabase : DEFAULT_AUTH_DATABASE;

        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                .applyToClusterSettings(builder ->
                        builder.hosts(Collections.singletonList(new ServerAddress(host, port))))
                .applyToSocketSettings(builder ->
                        builder.connectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                                .readTimeout(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS));

        // Add credentials if provided
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            MongoCredential credential = MongoCredential.createScramSha256Credential(
                    username,
                    authDb,
                    password.toCharArray()
            );
            settingsBuilder.credential(credential);
        }

        // Configure SSL if enabled
        if (useSsl) {
            try {
                if (validateCertificates) {
                    // Use default SSL context with certificate validation
                    settingsBuilder.applyToSslSettings(builder -> builder.enabled(true));
                } else {
                    // Use trust-all SSL context (no certificate validation)
                    SSLContext sslContext = createTrustAllSSLContext();
                    settingsBuilder.applyToSslSettings(builder ->
                            builder.enabled(true)
                                    .context(sslContext));
                }
            } catch (Exception e) {
                log.warn("Failed to configure SSL, proceeding without SSL: {}", e.getMessage());
            }
        }

        MongoClientSettings settings = settingsBuilder.build();
        return MongoClients.create(settings);
    }

    /**
     * Create SSL context that trusts all certificates (disables certificate validation)
     * WARNING: This should only be used in development/testing environments or when
     * 'sslValidateCertificates' is explicitly set to false by the user.
     * For production use, enable certificate validation via the sslValidateCertificates config option.
     */
    private SSLContext createTrustAllSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext;
    }

    /**
     * Get database name from connector configuration
     */
    public String getDatabaseName(ConnectorInfo connector) {
        String connectionString = getMetaValue(connector, "connectionString");

        if (StringUtils.isNotBlank(connectionString)) {
            // Extract database from connection string
            try {
                ConnectionString connString = new ConnectionString(connectionString);
                String database = connString.getDatabase();
                if (StringUtils.isNotBlank(database)) {
                    return database;
                }
            } catch (Exception e) {
                log.warn("Failed to extract database from connection string: {}", e.getMessage());
            }
        }

        // Get from metaConfig
        String database = getMetaValue(connector, "database");
        if (StringUtils.isBlank(database)) {
            throw new RuntimeException("MongoDB database name is required");
        }
        return database;
    }

    /**
     * Get value from metaConfig
     */
    private String getMetaValue(ConnectorInfo connector, String key) {
        return getMetaValue(connector, key, null);
    }

    /**
     * Get value from metaConfig with default
     */
    private String getMetaValue(ConnectorInfo connector, String key, String defaultValue) {
        Object value = connector.getMetaConfig().get(key);
        if (value != null) {
            return value.toString();
        }
        return defaultValue;
    }
}
