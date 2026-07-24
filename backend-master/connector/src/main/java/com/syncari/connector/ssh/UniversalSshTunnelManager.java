package com.syncari.connector.ssh;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UniversalSshTunnelManager {
    private final LoadingCache<String, SshTunnel> tunnelCache;

    public UniversalSshTunnelManager() {
        this.tunnelCache = CacheBuilder.newBuilder()
                .maximumSize(200)                        // Max tunnels, LRU eviction
                .recordStats()                           // Enable metrics for monitoring
                .removalListener((RemovalListener<String, SshTunnel>) notification -> {
                    SshTunnel tunnel = notification.getValue();
                    if (tunnel != null) {
                        log.info("SSH tunnel removed from cache: localPort={}, cause={}",
                                tunnel.getLocalPort(), notification.getCause());
                        try {
                            tunnel.close();
                        } catch (Exception e) {
                            log.warn("Error closing tunnel during cache removal: {}", e.getMessage());
                        }
                    }
                })
                .build(new CacheLoader<String, SshTunnel>() {
                    @Override
                    public SshTunnel load(String key) {
                        throw new UnsupportedOperationException("Tunnels must be explicitly created via getOrCreateTunnel()");
                    }
                });
    }

    public <T extends TunnelableConnection> T createConnection(
            TunnelConfig config,
            ConnectionFactory<T> factory) {

        if (config.isSshEnabled()) {
            SshTunnel tunnel = getOrCreateTunnel(config);
            return factory.create(config.withLocalhost(tunnel.getLocalPort()));
        }
        return factory.create(config);
    }

    public SshTunnel getOrCreateTunnel(TunnelConfig config) {
        String cacheKey = buildCacheKey(config);

        try {
            // Check if cached tunnel exists and is healthy
            SshTunnel existingTunnel = tunnelCache.getIfPresent(cacheKey);
            if (existingTunnel != null) {
                if (existingTunnel.isHealthy()) {
                    log.debug("Reusing healthy cached SSH tunnel: key={}, localPort={}",
                            cacheKey, existingTunnel.getLocalPort());
                    return existingTunnel;
                } else {
                    log.warn("Cached SSH tunnel is unhealthy, invalidating: key={}", cacheKey);
                    tunnelCache.invalidate(cacheKey);
                }
            }

            // Create new tunnel if none exists or cached one was unhealthy
            return tunnelCache.get(cacheKey, () -> {
                log.debug("Creating new SSH tunnel for: {}", cacheKey);
                return createTunnelWithRetry(config);
            });
        } catch (Exception e) {
            log.error("Failed to get or create SSH tunnel for: {}", cacheKey, e);
            throw new RuntimeException("SSH tunnel creation failed", e);
        }
    }

    private SshTunnel createTunnelWithRetry(TunnelConfig config) {
        int maxRetries = 3;
        long baseDelayMs = 1000; // 1 second

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return new SshTunnel(config);
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.error("Failed to create SSH tunnel after {} attempts", maxRetries, e);
                    throw new RuntimeException("SSH tunnel creation failed after " + maxRetries + " attempts", e);
                }

                long delayMs = baseDelayMs * (1L << (attempt - 1)); // Exponential backoff
                log.warn("SSH tunnel creation attempt {}/{} failed, retrying in {}ms: {}",
                        attempt, maxRetries, delayMs, e.getMessage());

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during SSH tunnel creation retry", ie);
                }
            }
        }

        throw new IllegalStateException("Should not reach here"); // Unreachable
    }

    private String buildCacheKey(TunnelConfig config) {
        return config.buildStableKey();
    }

    public void closeTunnel(String cacheKey) {
        tunnelCache.invalidate(cacheKey);
    }

    public void closeAllTunnels() {
        tunnelCache.invalidateAll();
    }

    // Monitoring methods
    public long getTunnelCount() {
        return tunnelCache.size();
    }

    public com.google.common.cache.CacheStats getCacheStats() {
        return tunnelCache.stats();
    }

    public double getCacheHitRate() {
        return tunnelCache.stats().hitRate();
    }

    public long getCacheMissCount() {
        return tunnelCache.stats().missCount();
    }

    public double getAverageLoadPenalty() {
        return tunnelCache.stats().averageLoadPenalty();
    }

}

// Functional interfaces for connection creation
@FunctionalInterface
interface ConnectionFactory<T> {
    T create(TunnelConfig config);
}

// Marker interface for connections that support SSH tunneling
interface TunnelableConnection {
    // Marker interface for connections that support SSH tunneling
    // Implemented by: HikariDataSource, RestTemplate, KafkaProducer, etc.
}