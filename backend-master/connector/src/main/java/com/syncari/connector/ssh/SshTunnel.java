package com.syncari.connector.ssh;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Properties;

@Slf4j
public class SshTunnel implements AutoCloseable {
    private final String connectorKey;
    private final Session session;
    private final int localPort;
    private volatile boolean isConnected;

    public SshTunnel(TunnelConfig config) {
        this.connectorKey = config.buildStableKey();

        log.debug("Creating SSH tunnel for key: {}", connectorKey);

        Session tempSession = null;
        int tempPort = 0;

        try {

            // Create JSch session
            JSch jsch = new JSch();
            tempSession = jsch.getSession(config.getSshUsername(), config.getSshHost(), config.getSshPort());

            // Configure SSH session properties
            Properties sessionConfig = new Properties();
            sessionConfig.put("StrictHostKeyChecking", "no");
            sessionConfig.put("UserKnownHostsFile", "/dev/null");
            sessionConfig.put("PreferredAuthentications", "publickey,password");
            sessionConfig.put("ConnectTimeout", "10000");
            sessionConfig.put("ServerAliveInterval", "30000");
            sessionConfig.put("ServerAliveCountMax", "3");
            tempSession.setConfig(sessionConfig);

            // Set up authentication
            setupAuthentication(jsch, tempSession, config);

            // Connect to SSH server
            tempSession.connect();
            log.debug("SSH session connected to {}@{}:{}", config.getSshUsername(), config.getSshHost(), config.getSshPort());

            // Set up port forwarding with automatic port assignment
            tempPort = tempSession.setPortForwardingL(0, config.getTargetHost(), config.getTargetPort());
            log.debug("Port forwarding established: localhost:{} -> {}:{}", tempPort, config.getTargetHost(), config.getTargetPort());

            // Assign to final fields
            this.session = tempSession;
            this.localPort = tempPort;
            this.isConnected = true;

            log.info("SSH tunnel created: localhost:{} -> {}:{}", localPort, config.getTargetHost(), config.getTargetPort());

        } catch (Exception e) {
            log.error("Failed to create JSch SSH tunnel: {}", e.getMessage(), e);

            // Clean up on failure
            if (tempSession != null && tempSession.isConnected()) {
                try {
                    tempSession.disconnect();
                } catch (Exception closeEx) {
                    log.warn("Error disconnecting SSH session during cleanup: {}", closeEx.getMessage());
                }
            }

            throw new RuntimeException("Failed to establish JSch SSH tunnel", e);
        }
    }


    private void setupAuthentication(JSch jsch, Session session, TunnelConfig config) throws JSchException {
        if (StringUtils.isNotBlank(config.getSshPrivateKey())) {
            log.debug("Using private key authentication");
            setupPrivateKeyAuth(jsch, config);
        } else if (StringUtils.isNotBlank(config.getSshPassword())) {
            log.debug("Using password authentication");
            session.setPassword(config.getSshPassword());
        } else {
            throw new IllegalArgumentException("Either SSH password or private key must be provided");
        }
    }

    private void setupPrivateKeyAuth(JSch jsch, TunnelConfig config) throws JSchException {
        try {
            String privateKey = config.getSshPrivateKey();
            String passphrase = config.getSshPassphrase();

            // Add private key to JSch
            if (StringUtils.isNotBlank(passphrase)) {
                jsch.addIdentity("ssh-key", privateKey.getBytes(), null, passphrase.getBytes());
            } else {
                jsch.addIdentity("ssh-key", privateKey.getBytes(), null, null);
            }

            log.debug("Private key added to JSch identity");
        } catch (Exception e) {
            log.error("Failed to setup private key authentication: {}", e.getMessage());
            throw new JSchException("Failed to add SSH private key: " + e.getMessage());
        }
    }



    public boolean isHealthy() {
        if (session == null || !session.isConnected() || !isConnected) {
            return false;
        }

        // Similar to SftpClient#getClient - actually test the connection
        // by opening a channel to verify it's not just "connected" but actually working
        Channel testChannel = null;
        try {
            testChannel = session.openChannel("session");
            testChannel.connect(3000); // 3 second timeout - tolerant of network delays
            return true;
        } catch (Exception e) {
            log.debug("SSH tunnel health check failed: {}", e.getMessage());
            return false;
        } finally {
            if (testChannel != null && testChannel.isConnected()) {
                try {
                    testChannel.disconnect();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    public int getLocalPort() {
        return localPort;
    }

    public String getConnectorKey() {
        return connectorKey;
    }


    @Override
    public void close() {
        try {
            isConnected = false;

            if (session != null) {
                log.debug("Closing SSH tunnel session for connection: {}", connectorKey);

                // ALWAYS attempt port forwarding cleanup (defensive approach)
                try {
                    session.delPortForwardingL(localPort);
                    log.debug("Port forwarding removed for port {}", localPort);
                } catch (Exception e) {
                    log.warn("Error removing port forwarding for port {}: {}", localPort, e.getMessage());
                    // Continue with session cleanup even if port forwarding cleanup fails
                }

                // Disconnect session if still connected
                if (session.isConnected()) {
                    session.disconnect();
                    log.debug("SSH session disconnected");
                }
            }

            log.debug("SSH tunnel cleanup completed for: {}", connectorKey);

        } catch (Exception e) {
            log.warn("Error closing SSH tunnel: {}", e.getMessage());
        }
    }
}