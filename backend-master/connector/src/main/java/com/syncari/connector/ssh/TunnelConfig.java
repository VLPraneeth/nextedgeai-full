package com.syncari.connector.ssh;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TunnelConfig {
    private boolean sshEnabled;
    private String sshHost;
    private int sshPort = 22;
    private String sshUsername;
    private String sshPassword;        // Optional: for password auth
    private String sshPrivateKey;      // Optional: for key auth
    private String sshPassphrase;      // Optional: for encrypted keys

    // Target service details
    private String targetHost;
    private int targetPort;

    // Constructor for target service details
    public TunnelConfig(String targetHost, int targetPort) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    // Helper method to create a copy with localhost configuration
    public TunnelConfig withLocalhost(int localPort) {
        TunnelConfig copy = new TunnelConfig();
        copy.sshEnabled = this.sshEnabled;
        copy.sshHost = this.sshHost;
        copy.sshPort = this.sshPort;
        copy.sshUsername = this.sshUsername;
        copy.sshPassword = this.sshPassword;
        copy.sshPrivateKey = this.sshPrivateKey;
        copy.sshPassphrase = this.sshPassphrase;
        copy.targetHost = "localhost";
        copy.targetPort = localPort;
        return copy;
    }

    public String buildStableKey() {
        return String.format("%s:%d:%s:%s",
                targetHost, targetPort, sshHost, sshUsername);
    }
}