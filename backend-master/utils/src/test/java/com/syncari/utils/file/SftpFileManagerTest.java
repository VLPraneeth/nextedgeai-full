package com.syncari.utils.file;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Vector;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class SftpFileManagerTest {

    private SftpClient sftpClient;
    private ChannelSftp channelSftp;

    @Before
    public void setup(){
        sftpClient = mock(SftpClient.class);
        channelSftp = mock(ChannelSftp.class);
    }

    @Test
    public void testLastModifiedValue(){
        SftpFileManager sftpFileManager = new SftpFileManager(sftpClient);
        when(sftpClient.getClient()).thenReturn(channelSftp);
        ChannelSftp.LsEntry entryMock = mock(ChannelSftp.LsEntry.class);
        SftpATTRS attrsMock = mock(SftpATTRS.class);
        Vector vecMock = new Vector();
        try{
            when(channelSftp.ls(any())).thenReturn(vecMock);
            vecMock.add(entryMock);
        } catch (SftpException e) {
            throw new RuntimeException(e);
        }
        when(entryMock.getAttrs()).thenReturn(attrsMock);
        when(attrsMock.getMTime()).thenReturn(1630000000);
        long lastModfied = sftpFileManager.lastModified("uri");
        assertEquals(String.valueOf(lastModfied).length(), String.valueOf(Instant.now().toEpochMilli()).length());
    }

}
