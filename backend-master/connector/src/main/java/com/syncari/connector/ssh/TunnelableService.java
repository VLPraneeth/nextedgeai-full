package com.syncari.connector.ssh;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.data.TestConnectionResponse;
import com.syncari.connector.data.AuthField;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface TunnelableService {

    UniversalSshTunnelManager getTunnelManager();

    /**
     * Generic method to get connection endpoint with SSH tunnel support
     * @param connector ConnectorInfo containing configuration
     * @return ConnectionEndpoint with host/port (either direct or tunneled)
     */
    default ConnectionEndpoint getConnectionEndpoint(ConnectorInfo connector) {
        if (isSshTunnelEnabled(connector)) {
            TunnelConfig tunnelConfig = createTunnelConfig(connector);
            SshTunnel tunnel = getTunnelManager().getOrCreateTunnel(tunnelConfig);
            return new ConnectionEndpoint("localhost", tunnel.getLocalPort(), true);
        }

        // Direct connection
        String host = getDirectHost(connector);
        int port = getDirectPort(connector);
        return new ConnectionEndpoint(host, port, false);
    }

    /**
     * Check if SSH tunnel is enabled for this connector
     */
    default boolean isSshTunnelEnabled(ConnectorInfo connector) {
        return Boolean.parseBoolean(
            connector.getMetaConfig().getOrDefault("sshEnabled", "false").toString());
    }

    /**
     * Create tunnel configuration from connector metadata - REUSABLE across all services
     */
    default TunnelConfig createTunnelConfig(ConnectorInfo connector) {
        Map<String, Object> metaConfig = connector.getMetaConfig();

        TunnelConfig config = new TunnelConfig();
        config.setSshEnabled(true);
        config.setSshHost((String) metaConfig.get("sshHost"));
        config.setSshPort(Integer.parseInt(metaConfig.getOrDefault("sshPort", "22").toString()));
        config.setSshUsername((String) metaConfig.get("sshUsername"));

        // Auto-detect authentication method (SAME LOGIC FOR ALL SERVICES)
        String privateKey = (String) metaConfig.get("sshPrivateKey");
        String password = (String) metaConfig.get("sshPassword");

        if (StringUtils.isNotBlank(privateKey)) {
            config.setSshPrivateKey(privateKey);
            config.setSshPassphrase((String) metaConfig.get("sshPassphrase"));
        } else if (StringUtils.isNotBlank(password)) {
            config.setSshPassword(password);
        } else {
            throw new IllegalArgumentException("Either SSH password or private key must be provided");
        }

        // Target service details - implemented by subclasses
        config.setTargetHost(getDirectHost(connector));
        config.setTargetPort(getDirectPort(connector));

        return config;
    }

    /**
     * Add standard SSH configuration fields - REUSABLE for all connectors
     */
    default List<AuthField> addSshConfigurationFields(List<AuthField> fields) {
        // SSH Tunnel Section
        fields.add(new AuthField()
            .setName("sshEnabled")
            .setDataType("checkbox")
            .setLabel("Enable SSH Tunnel")
            .setDescription("Connect through SSH jump server"));

        fields.add(new AuthField()
            .setName("sshHost")
            .setDataType("text")
            .setLabel("SSH Jump Server Host")
            .setRequired(false));

        fields.add(new AuthField()
            .setName("sshPort")
            .setDataType("number")
            .setLabel("SSH Port")
            .setDefaultValue("22"));

        fields.add(new AuthField()
            .setName("sshUsername")
            .setDataType("text")
            .setLabel("SSH Username")
            .setRequired(false));

        // SSH Authentication - Auto-detected
        fields.add(new AuthField()
            .setName("sshPassword")
            .setDataType("password")
            .setLabel("SSH Password")
            .setRequired(false)
            .setDescription("Use either SSH password OR private key authentication"));

        fields.add(new AuthField()
            .setName("sshPrivateKey")
            .setDataType("textarea")
            .setLabel("SSH Private Key")
            .setRequired(false)
            .setDescription("Paste your private key (RSA, ECDSA, or Ed25519). Alternative to password."));

        fields.add(new AuthField()
            .setName("sshPassphrase")
            .setDataType("password")
            .setLabel("Private Key Passphrase")
            .setRequired(false)
            .setDescription("Only needed if your private key is encrypted"));

        return fields;
    }



    // Abstract methods to be implemented by specific services
    String getDirectHost(ConnectorInfo connector);
    int getDirectPort(ConnectorInfo connector);
}