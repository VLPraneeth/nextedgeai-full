package com.syncari.core.service;

import com.syncari.connector.data.AuthField;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConnectorServiceOptionalPasswordTest {

    @InjectMocks
    private ConnectorService connectorService;
    @Mock
    private ConnectorMetadataService connectorMetaService;
    @Mock
    private DataServiceFactory factory;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private SynapseInfoService synapseService;

    @Test
    public void blankOptionalPasswordMetadataIsNotEncrypted() {
        Connector connector = connectorWithPasswordMetadata("");

        Connector encrypted = connectorService.encrypt(connector);

        assertEquals("", encrypted.getMetaConfig().get("sshPassword"));
        verify(encryptionService, never()).encrypt(anyString());
    }

    @Test
    public void populatedPasswordMetadataIsEncrypted() {
        Connector connector = connectorWithPasswordMetadata("secret");
        when(encryptionService.encrypt("secret")).thenReturn("encrypted");

        Connector encrypted = connectorService.encrypt(connector);

        assertEquals("encrypted", encrypted.getMetaConfig().get("sshPassword"));
        verify(encryptionService).encrypt("secret");
    }

    private Connector connectorWithPasswordMetadata(String password) {
        ConnectorMetadata metadata = new ConnectorMetadata("metadata-id");
        when(connectorMetaService.findById("metadata-id")).thenReturn(Optional.of(metadata));
        when(factory.isSynapseService(metadata)).thenReturn(true);
        when(factory.getSynapseService(metadata)).thenReturn(synapseService);
        when(synapseService.getConfigureFields()).thenReturn(List.of(
                new AuthField().setName("sshPassword").setDataType("password")
        ));

        Connector connector = new Connector("mysql-demo", "metadata-id", null);
        connector.setMetaConfig(new HashMap<>());
        connector.getMetaConfig().put("sshPassword", password);
        return connector;
    }
}
