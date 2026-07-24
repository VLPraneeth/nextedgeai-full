package com.syncari.connector.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@AllArgsConstructor
public class ProxyConfig {
	private String host;
	private int port;
}
