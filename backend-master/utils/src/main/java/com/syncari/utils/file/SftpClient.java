package com.syncari.utils.file;

import com.jcraft.jsch.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
public class SftpClient implements AutoCloseable{
	public static final int CONNECTION_TIMEOUT = 60000;
	public static final int SERVER_ALIVE_INTERVAL_MS = 5000;
	//sends at most 12 keepalive packets without receiving any response from the server at 5second intervals
	//matching CONNECTION_TIMEOUT
	public static final int SERVER_ALIVE_COUNT_MAX = 12;
	private String baseFolderPath;
	private String userName;
	private String password;
	private String privateKey;
	private String passphrase;
	private String host;
	private static final String COLON = ":";
	protected ChannelSftp client;
	protected Session session;

	public SftpClient(String baseFolderPath, String userName, String password, String host) {
		this.baseFolderPath = baseFolderPath;
		this.host = host;
		this.userName = userName;
		this.password = password;
	}

	public static SftpClient withPrivateKey(String baseFolderPath, String userName, String privateKey, String passphrase, String host) {
		final SftpClient sftpClient = new SftpClient(baseFolderPath, userName, null, host);
		sftpClient.privateKey = privateKey;
		sftpClient.passphrase = passphrase;
		return sftpClient;
	}

	public ChannelSftp getClient() {
		int maxRetries = 5; // Maximum number of retries
		int retryInterval = 30000; // Retry interval in milliseconds

		try {

			if (client != null && client.isConnected() && session != null && session.isConnected()) {
				try {
					client.stat(".");
					return client;
				} catch (SftpException e) {
					//connection broken. Reestablish below
				}
			}
			setupSession();
			int retries = 0;
			while (retries < maxRetries) {
				try {
					session.connect();
					break; // Connection successful, exit the retry loop
				} catch (JSchException e) {
					if (e.getMessage().contains("Session.connect")) {
						retries++;
						if (retries >= maxRetries) {
							log.error(ExceptionUtils.getStackTrace(e));
							throw new RuntimeException(e);
						} else {
							Thread.sleep(retryInterval);
						}
					} else {
						log.error(ExceptionUtils.getStackTrace(e));
						throw new RuntimeException(e);
					}
				}
			}
			Channel channel = session.openChannel("sftp");
			channel.connect();
			client = (ChannelSftp) channel;
			return client;
		} catch (Exception e) {
			log.error(ExceptionUtils.getStackTrace(e));
			throw new RuntimeException(e);
		}
	}

	@SneakyThrows
	private void setupSession() {
		JSch jsch = getSecureChannel();

		if (StringUtils.isNotBlank(privateKey)) {
			final String pathname = UUID.randomUUID().toString();
			if (StringUtils.isNotBlank(passphrase)) {
				jsch.addIdentity(pathname, privateKey.getBytes(StandardCharsets.UTF_8),
						null, passphrase.getBytes(StandardCharsets.UTF_8));
			} else {
				jsch.addIdentity(pathname, privateKey.getBytes(StandardCharsets.UTF_8),
						null, null);
			}
		}
		session = jsch.getSession(userName, extractServer(), extractPort());
		if (StringUtils.isBlank(privateKey)) {
			session.setPassword(password);
		}

		session.setConfig("StrictHostKeyChecking", "no");
		session.setServerAliveInterval(SERVER_ALIVE_INTERVAL_MS);
		session.setServerAliveCountMax(SERVER_ALIVE_COUNT_MAX);
		session.setTimeout(CONNECTION_TIMEOUT);
	}

	protected JSch getSecureChannel() {
		return new JSch();
	}

	public String getBaseFolder() {
		return baseFolderPath;
	}

	private String extractServer() {
		return host.split(":")[0];
	}
	private int extractPort() {
		//host.com:22
		String[] split = host.split(":");
		return split.length > 1 ?  Integer.parseInt(split[1]) :  22;
	}


	@Override
	public void close() {
		try {
			if(client != null && client.isConnected()){
				client.disconnect();
				client = null;
			}
			if(session != null && session.isConnected()){
				session.disconnect();
				session = null;
			}
		}catch (Exception e) {
			log.error("Error while closing sftp client", e);
		}
	}
}
