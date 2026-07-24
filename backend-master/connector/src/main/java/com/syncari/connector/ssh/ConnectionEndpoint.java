package com.syncari.connector.ssh;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ConnectionEndpoint {
    private final String host;
    private final int port;
    private final boolean tunneled;

    // Convenience methods for building connection strings
    public String toHostPort() {
        return host + ":" + port;
    }


    @Override
    public String toString() {
        return String.format("ConnectionEndpoint{host='%s', port=%d, tunneled=%s}", host, port, tunneled);
    }
}