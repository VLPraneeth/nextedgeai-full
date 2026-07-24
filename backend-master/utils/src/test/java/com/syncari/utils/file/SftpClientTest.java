package com.syncari.utils.file;

import com.jcraft.jsch.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)

public class SftpClientTest {

    private static final String path = "/home/sftpuser/syncari";
    private static final String host = "34.170.229.163:22";
    private static final String userName = "sftpuser";
    private static final String password = System.getenv().getOrDefault("TEST_PASSWORD", "REPLACE_ME");

    JSch jSch = mock(JSch.class);

    public class TestSftpClient extends SftpClient {
        public TestSftpClient(String baseFolderPath, String userName, String password, String host) {
            this(baseFolderPath, userName, password, host, null, null);
        }

        public TestSftpClient(String baseFolderPath, String userName, String password, String host, ChannelSftp channel, Session session) {
            super(baseFolderPath, userName, password, host);
            this.client = channel;
            this.session = session;
        }

        protected JSch getSecureChannel() {
            return jSch;
        }
    }

    @Test
    public void testGetClientThrowsJSchExceptionAfterTimeout() throws JSchException {
        ChannelSftp mockChannel = mock(ChannelSftp.class);
        Session mockSession = mock(Session.class);
        TestSftpClient client = new TestSftpClient(path, userName, password, host);
        when(jSch.getSession(eq(userName), anyString(), anyInt())).thenReturn(mockSession);
        doThrow(new JSchException("Session.connect: Mock"))
                .doNothing()
                .when(mockSession).connect();
        lenient().when(mockSession.openChannel(anyString())).thenReturn(mockChannel);
        lenient().doNothing().when(mockChannel).connect();

        ChannelSftp channel = client.getClient();
        verify(mockSession, times(2)).connect();
        assertNotNull(channel);

    }

    @Test
    public void testBrokenPipe() throws Exception {
        ChannelSftp mockChannel = mock(ChannelSftp.class);
        ChannelSftp mockChannel2 = mock(ChannelSftp.class);
        Session mockSession = mock(Session.class);
        when(jSch.getSession(eq(userName), anyString(), anyInt())).thenReturn(mockSession);
        doThrow(new SftpException(1, "Broken pipe")).when(mockChannel).stat(".");
        when(mockChannel.isConnected()).thenReturn(true);
        when(mockSession.isConnected()).thenReturn(true);
        lenient().doNothing().when(mockSession).setConfig("StrictHostKeyChecking", "no");
        lenient().doNothing().when(mockSession).setServerAliveInterval(5000);
        lenient().doNothing().when(mockSession).setServerAliveCountMax(12);
        lenient().doNothing().when(mockSession).setTimeout(60000);
        lenient().doNothing().when(mockSession).connect();
        lenient().when(mockSession.openChannel(anyString())).thenReturn(mockChannel2);
        lenient().doNothing().when(mockChannel2).connect();
        TestSftpClient client = new TestSftpClient(path, userName, password, host, mockChannel, mockSession);
        ChannelSftp c = client.getClient();
        assertSame(c, mockChannel2);
        verify(mockChannel, times(1)).stat(".");
        verify(mockSession, times(1)).connect();
        assertNotNull(c);

    }

    @Test
    public void testGetClientThrowsJSchExceptionAfterTimeoutDoNotRetryOnDifferentException() throws JSchException {
        TestSftpClient client = new TestSftpClient(path, userName, password, host);
        Session mockSession = mock(Session.class);
        ChannelSftp mockChannel = mock(ChannelSftp.class);
        when(jSch.getSession(eq(userName), anyString(), anyInt())).thenReturn(mockSession);
        doThrow(new JSchException("Another exception"))
                .doNothing()
                .when(mockSession).connect();
        lenient().when(mockSession.openChannel(anyString())).thenReturn(mockChannel);
        lenient().doNothing().when(mockChannel).connect();

        try {
            client.getClient();
            fail("Should not get here");
        } catch (Exception e) {
            verify(mockSession, times(1)).connect();
        }
    }
}