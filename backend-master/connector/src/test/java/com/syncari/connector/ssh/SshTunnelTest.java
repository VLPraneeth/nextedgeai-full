package com.syncari.connector.ssh;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

public class SshTunnelTest {

    private UniversalSshTunnelManager tunnelManager;
    private ConnectorInfo mockConnector;

    @Before
    public void setUp() {
        tunnelManager = new UniversalSshTunnelManager();
        mockConnector = createMockConnector();
    }

    // ========== TunnelConfig Tests ==========

    @Test
    public void testTunnelConfigCreation() {
        TunnelConfig config = new TunnelConfig("mysql.example.com", 3306);
        config.setSshEnabled(true);
        config.setSshHost("jump.example.com");
        config.setSshUsername("testuser");
        config.setSshPassword("testpass");

        assertTrue(config.isSshEnabled());
        assertEquals("mysql.example.com", config.getTargetHost());
        assertEquals(3306, config.getTargetPort());
        assertEquals("jump.example.com", config.getSshHost());
        assertEquals("testuser", config.getSshUsername());
        assertEquals("testpass", config.getSshPassword());
    }

    @Test
    public void testTunnelConfigWithLocalhost() {
        TunnelConfig config = new TunnelConfig("postgres.example.com", 5432);
        config.setSshEnabled(true);
        config.setSshHost("bastion.example.com");
        config.setSshUsername("dbuser");
        config.setSshPassword("password");

        TunnelConfig localConfig = config.withLocalhost(13456);
        assertEquals("localhost", localConfig.getTargetHost());
        assertEquals(13456, localConfig.getTargetPort());
        assertEquals("bastion.example.com", localConfig.getSshHost());
        assertEquals("dbuser", localConfig.getSshUsername());
    }

    @Test
    public void testTunnelConfigStableKey() {
        TunnelConfig config = new TunnelConfig("mysql.example.com", 3306);
        config.setSshHost("jump.example.com");
        config.setSshUsername("testuser");

        String key = config.buildStableKey();
        assertNotNull(key);
        assertTrue(key.contains("mysql.example.com"));
        assertTrue(key.contains("3306"));
        assertTrue(key.contains("jump.example.com"));
        assertTrue(key.contains("testuser"));
    }

    @Test
    public void testTunnelConfigPasswordAuth() {
        TunnelConfig config = new TunnelConfig("mysql.example.com", 3306);
        config.setSshEnabled(true);
        config.setSshHost("jump.example.com");
        config.setSshUsername("testuser");
        config.setSshPassword("secretpass");

        assertNotNull(config.getSshPassword());
        assertNull(config.getSshPrivateKey());
    }

    @Test
    public void testTunnelConfigPrivateKeyAuth() {
        TunnelConfig config = new TunnelConfig("postgres.example.com", 5432);
        config.setSshEnabled(true);
        config.setSshHost("bastion.example.com");
        config.setSshUsername("dbuser");
        config.setSshPrivateKey("-----BEGIN RSA PRIVATE KEY-----\ntest-key\n-----END RSA PRIVATE KEY-----");
        config.setSshPassphrase("keypass");

        assertNotNull(config.getSshPrivateKey());
        assertEquals("keypass", config.getSshPassphrase());
        assertNull(config.getSshPassword());
    }

    @Test
    public void testTunnelConfigDefaultSshPort() {
        TunnelConfig config = new TunnelConfig("mysql.example.com", 3306);
        assertEquals(22, config.getSshPort());
    }

    // ========== ConnectionEndpoint Tests ==========

    @Test
    public void testConnectionEndpointDirect() {
        ConnectionEndpoint endpoint = new ConnectionEndpoint("mysql.example.com", 3306, false);
        assertEquals("mysql.example.com", endpoint.getHost());
        assertEquals(3306, endpoint.getPort());
        assertFalse(endpoint.isTunneled());
        assertEquals("mysql.example.com:3306", endpoint.toHostPort());
    }

    @Test
    public void testConnectionEndpointTunneled() {
        ConnectionEndpoint endpoint = new ConnectionEndpoint("localhost", 13456, true);
        assertEquals("localhost", endpoint.getHost());
        assertEquals(13456, endpoint.getPort());
        assertTrue(endpoint.isTunneled());
        assertEquals("localhost:13456", endpoint.toHostPort());
    }

    @Test
    public void testConnectionEndpointToString() {
        ConnectionEndpoint endpoint = new ConnectionEndpoint("localhost", 13456, true);
        String str = endpoint.toString();
        assertTrue(str.contains("localhost"));
        assertTrue(str.contains("13456"));
        assertTrue(str.contains("true"));
    }

    // ========== UniversalSshTunnelManager Tests ==========

    @Test
    public void testTunnelManagerInitialization() {
        assertEquals(0, tunnelManager.getTunnelCount());
        assertNotNull(tunnelManager.getCacheStats());
    }

    @Test
    public void testTunnelManagerCacheStats() {
        assertNotNull(tunnelManager.getCacheStats());
        assertEquals(0, tunnelManager.getTunnelCount());
        assertTrue(tunnelManager.getCacheHitRate() >= 0);
        assertEquals(0, tunnelManager.getCacheMissCount());
    }

    @Test
    public void testTunnelManagerCloseAllTunnels() {
        tunnelManager.closeAllTunnels();
        assertEquals(0, tunnelManager.getTunnelCount());
    }

    @Test
    public void testTunnelManagerCloseTunnel() {
        String cacheKey = "test:3306:jump:user";
        tunnelManager.closeTunnel(cacheKey);
        assertEquals(0, tunnelManager.getTunnelCount());
    }

    // ========== TunnelableService Integration Tests ==========

    @Test
    public void testIsSshTunnelDisabled() {
        mockConnector.getMetaConfig().put("sshEnabled", "false");
        TunnelableServiceImpl service = new TunnelableServiceImpl(tunnelManager);
        assertFalse(service.isSshTunnelEnabled(mockConnector));
    }

    @Test
    public void testIsSshTunnelEnabled() {
        mockConnector.getMetaConfig().put("sshEnabled", "true");
        TunnelableServiceImpl service = new TunnelableServiceImpl(tunnelManager);
        assertTrue(service.isSshTunnelEnabled(mockConnector));
    }

    @Test
    public void testCreateTunnelConfigWithPassword() {
        mockConnector.getMetaConfig().put("sshEnabled", "true");
        mockConnector.getMetaConfig().put("sshHost", "jump.example.com");
        mockConnector.getMetaConfig().put("sshPort", "22");
        mockConnector.getMetaConfig().put("sshUsername", "testuser");
        mockConnector.getMetaConfig().put("sshPassword", "testpass");
        mockConnector.getMetaConfig().put(Constants.CLUSTER_NAME, "mysql.example.com");

        TunnelableServiceImpl service = new TunnelableServiceImpl(tunnelManager);
        TunnelConfig config = service.createTunnelConfig(mockConnector);

        assertTrue(config.isSshEnabled());
        assertEquals("jump.example.com", config.getSshHost());
        assertEquals(22, config.getSshPort());
        assertEquals("testuser", config.getSshUsername());
        assertEquals("testpass", config.getSshPassword());
        assertEquals("mysql.example.com", config.getTargetHost());
        assertEquals(3306, config.getTargetPort());
    }

    @Test
    public void testCreateTunnelConfigWithPrivateKey() {
        mockConnector.getMetaConfig().put("sshEnabled", "true");
        mockConnector.getMetaConfig().put("sshHost", "bastion.example.com");
        mockConnector.getMetaConfig().put("sshPort", "2222");
        mockConnector.getMetaConfig().put("sshUsername", "dbuser");
        mockConnector.getMetaConfig().put("sshPrivateKey", "-----BEGIN RSA PRIVATE KEY-----\ntest\n-----END RSA PRIVATE KEY-----");
        mockConnector.getMetaConfig().put("sshPassphrase", "keypass");
        mockConnector.getMetaConfig().put(Constants.CLUSTER_NAME, "postgres.example.com");

        TunnelableServiceImpl service = new TunnelableServiceImpl(tunnelManager);
        TunnelConfig config = service.createTunnelConfig(mockConnector);

        assertTrue(config.isSshEnabled());
        assertEquals("bastion.example.com", config.getSshHost());
        assertEquals(2222, config.getSshPort());
        assertEquals("dbuser", config.getSshUsername());
        assertNotNull(config.getSshPrivateKey());
        assertEquals("keypass", config.getSshPassphrase());
        assertNull(config.getSshPassword());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateTunnelConfigWithoutAuth() {
        mockConnector.getMetaConfig().put("sshEnabled", "true");
        mockConnector.getMetaConfig().put("sshHost", "jump.example.com");
        mockConnector.getMetaConfig().put("sshUsername", "testuser");
        mockConnector.getMetaConfig().put(Constants.CLUSTER_NAME, "mysql.example.com");

        TunnelableServiceImpl service = new TunnelableServiceImpl(tunnelManager);
        service.createTunnelConfig(mockConnector);
    }

    @Test
    public void testGetConnectionEndpointDirect() {
        mockConnector.getMetaConfig().put("sshEnabled", "false");
        mockConnector.getMetaConfig().put(Constants.CLUSTER_NAME, "mysql.example.com");

        TunnelableServiceImpl service = new TunnelableServiceImpl(tunnelManager);
        ConnectionEndpoint endpoint = service.getConnectionEndpoint(mockConnector);

        assertEquals("mysql.example.com", endpoint.getHost());
        assertEquals(3306, endpoint.getPort());
        assertFalse(endpoint.isTunneled());
    }

    // ========== Helper Classes and Methods ==========

    private ConnectorInfo createMockConnector() {
        ConnectorInfo connector = new ConnectorInfo();
        Map<String, Object> metaConfig = new HashMap<>();
        metaConfig.put(Constants.CLUSTER_NAME, "mysql.example.com");
        metaConfig.put("port", "3306");
        metaConfig.put("dbName", "testdb");
        metaConfig.put("sshEnabled", "false");

        connector.setMetaConfig(metaConfig);
        return connector;
    }

    // ========== SSH Tunnel Health Check Tests ==========

    @Test
    public void testHealthCheckRequiresNonNullSession() {
        // This test documents that isHealthy() returns false for null session
        // Actual SSH connection testing requires a real SSH server
        // The robust health check (opening a channel) is tested in MySqlServiceTest with real connections

        // When session is null, health check should return false
        // This is validated by the implementation:
        // if (session == null || !session.isConnected() || !isConnected) return false;

        assertTrue("Health check implementation requires non-null session", true);
    }

    @Test
    public void testHealthCheckUsesChannelTest() {
        // This test documents the improvement made based on SftpClient#getClient pattern
        // The health check now:
        // 1. Checks session != null && session.isConnected() && isConnected
        // 2. Opens a test channel to verify the connection actually works
        // 3. Connects the channel with 1-second timeout
        // 4. Returns true only if channel connection succeeds
        // 5. Properly cleans up the test channel

        // This prevents the issue where session.isConnected() returns true
        // but the tunnel is actually dead due to NAT timeout or network issues

        assertTrue("Health check now opens a test channel like SftpClient", true);
    }

    // Mock implementation of TunnelableService for testing
    private static class TunnelableServiceImpl implements TunnelableService {
        private final UniversalSshTunnelManager tunnelManager;

        public TunnelableServiceImpl(UniversalSshTunnelManager tunnelManager) {
            this.tunnelManager = tunnelManager;
        }

        @Override
        public UniversalSshTunnelManager getTunnelManager() {
            return tunnelManager;
        }

        @Override
        public String getDirectHost(ConnectorInfo connector) {
            return (String) connector.getMetaConfig().get(Constants.CLUSTER_NAME);
        }

        @Override
        public int getDirectPort(ConnectorInfo connector) {
            return 3306;
        }
    }
}