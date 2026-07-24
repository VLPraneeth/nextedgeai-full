package com.syncari.connector.service;

import com.google.common.cache.LoadingCache;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.SaveResult;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.SoapFaultException;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MockSalesforceServiceTest extends SalesforceService {

    @Mock
    private ConnectorInfo connectorInfo;

    @Mock
    private LoadingCache<ConnectorInfo, PartnerConnection> mockCache;

    @Mock
    private PartnerConnection partnerConnection;

    @Mock
    private Transformer mockTransformer;

    @Mock
    private SObject sObject;

    @Mock
    private SaveResult saveResult;

    @Mock
    private SyncResponse syncResponse;

    @Before
    public void setUp() throws Exception {
        when(connectorInfo.getId()).thenReturn("CONNECTOR ID");
        cache = mockCache;
        when(mockCache.get(any())).thenReturn(partnerConnection);
        transformer = mockTransformer;
    }

    @Test
    public void shouldRemoveDuplicatedCreatedAt() throws ConnectionException {
        EntityData account1 = new EntityData(Constants.ACCOUNT)
                .setConnectorId(connectorInfo.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                        "DateField", "2023-11-20T00:00:00.000Z"
                )));
        EntityData account2 = new EntityData(Constants.ACCOUNT)
                .setConnectorId(connectorInfo.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                        "DateField", "12988-11-20T00:00:00.000Z"
                )));
        EntityData account3 = new EntityData(Constants.ACCOUNT)
                .setConnectorId(connectorInfo.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                        "DateField", "2023-11-20T00:00:00.000Z"
                )));
        SyncRequest request = new SyncRequest()
                .setConnector(connectorInfo)
                .setEntitySchema(new EntitySchema().setApiName(Constants.ACCOUNT))
                .setData(Map.of(connectorInfo.getId(), List.of(account1, account2, account3)));

        SaveResult[] results = {saveResult};

        when(partnerConnection.create(any()))
                .thenThrow(new SoapFaultException(QName.valueOf("CONNECTION_ERROR"), "12988-11-20T00:00:00.000Z' is not a valid value for the type xsd:date"))
                .thenReturn(results)
                .thenThrow(new SoapFaultException(QName.valueOf("CONNECTION_ERROR"), "12988-11-20T00:00:00.000Z' is not a valid value for the type xsd:date"));

        SObject[] sObjects = {sObject, sObject, sObject};
        when(transformer.toSObjects(any(), any())).thenReturn(sObjects);
        when(transformer.toSyncResponse(any(), anyList(), any())).thenReturn(syncResponse);

        try {
            post(request, Operation.create);
            Assert.fail("Shouldn't get here");
        } catch (Exception e) {
            Assert.assertEquals("12988-11-20T00:00:00.000Z' is not a valid value for the type xsd:date", e.getMessage());
        }

        verify(partnerConnection, times(3)).create(any());
    }
}