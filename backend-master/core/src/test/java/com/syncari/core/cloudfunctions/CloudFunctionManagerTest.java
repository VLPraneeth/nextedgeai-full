package com.syncari.core.cloudfunctions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.file.GCSFileManager;

import com.syncari.core.service.ConnectorMetadataService;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Ignore
public class CloudFunctionManagerTest extends AbstractSyncariTest {

    private static final String BASE_RESOURCE_PATH = "src/test/resources/connectormetadata/";

    private InputStream synapseFileStream;
    private InputStream requirementsFileStream;

    @Autowired
    CloudFunctionManager manager;

    @Autowired
    GCSFileManager gcsFileManager;

    @Autowired
    AppConfig appConfig;

    @Before
    public void setup() {
        try {
            synapseFileStream = new FileInputStream(BASE_RESOURCE_PATH + "main.py");
            requirementsFileStream = new FileInputStream(BASE_RESOURCE_PATH + "requirements.txt");
        } catch (FileNotFoundException e) {
            fail("File read exception");
        }
    }

    @Test
    public void cudCloudFunctionsTest() {
        String custSynapseIdentifier = ("custom_" + SyncariContext.getSyncariId() + "_" + "sample").toLowerCase();
        try {
            manager.create(custSynapseIdentifier, synapseFileStream, requirementsFileStream, CloudFunctionManager.DEFAULT_REGION, ConnectorMetadataService.getDraftFileName(custSynapseIdentifier));
            verifyFunctionStatus(custSynapseIdentifier, SyncariCloudFunctionStatus.CODE.ACTIVE);
            Thread.sleep(5000);
            manager.update(custSynapseIdentifier, synapseFileStream, requirementsFileStream, CloudFunctionManager.DEFAULT_REGION, ConnectorMetadataService.getDraftFileName(custSynapseIdentifier));
            verifyFunctionStatus(custSynapseIdentifier, SyncariCloudFunctionStatus.CODE.ACTIVE);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        } finally {
            manager.delete(custSynapseIdentifier, CloudFunctionManager.DEFAULT_REGION, ConnectorMetadataService.getDraftFileName(custSynapseIdentifier));
        }
    }

    private void verifyFunctionStatus(String custSynapseIdentifier, SyncariCloudFunctionStatus.CODE expectedStatus) {
        int retries = 10;
        SyncariCloudFunctionStatus status = null;
        while( (status==null || status.getCode() != expectedStatus) && retries > 0) {
            try {
                Thread.sleep(10000);
                status = manager.getStatus(custSynapseIdentifier, CloudFunctionManager.DEFAULT_REGION);
            } catch (Exception e) {
                // Do nothing
            }
            retries--;
        }
        assertEquals(expectedStatus, status.getCode());
    }

}
