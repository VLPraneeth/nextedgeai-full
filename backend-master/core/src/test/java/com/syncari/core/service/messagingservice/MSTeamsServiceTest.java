package com.syncari.core.service.messagingservice;

import com.syncari.connector.Constants;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataTransformer;
import com.syncari.core.model.Connector;
import com.syncari.core.service.ConnectorService;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.Assert.fail;

@Ignore
public class MSTeamsServiceTest extends AbstractSyncariTest {
    @Autowired
    ConnectorService connectorService;

    @Autowired
    MSTeamsService service;

    @Autowired
    protected DataTransformer dataTransformer;

    private static final String MSTEAMS_CLIENT_ID = "284cf45f-f2f2-44a0-b2eb-8445273d9b09";

    private static final String MSTEAMS_CLIENT_SECRET = "test_value_32";

    private static final String MSTEAMS_TENANT_ID = "c811f892-f2ba-48f8-a22b-6a4d7e50d775";

    private static final String TEAMS_ID = "ad3dae5d-28ae-4a32-af22-bfd9ef8ad574";

    private String connectorId;

    @Before
    public void setup() {
        Connector connector = new Connector("msteams", connectorService.describe(Constants.MS_TEAMS).getId(),
                SlackService.END_POINT);
        connector.setAuthConfig(new AuthConfig(MSTEAMS_CLIENT_ID, MSTEAMS_CLIENT_SECRET));
        connector.getAuthConfig().setRefreshToken(System.getenv().getOrDefault("TEST_REFRESH_TOKEN", "REPLACE_ME"));
        connector.setMetaConfig(Map.of("tenantId", MSTEAMS_TENANT_ID));
        service.refreshToken(dataTransformer.toConnectorInfo(connector));
        connectorId = connectorService.save(connector).getId();
    }

    @Ignore
    @Test
    public void createChannelTest() {
        try {
            service.createChannel(TEAMS_ID, "Test Channel 23433wwe", connectorId);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void sendMessageTest() {
        try {
            service.sendMessage(TEAMS_ID, "19:9fd32cac93fe44b3bf23ce982458d9c6@thread.tacv2", "Hello World", connectorId);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

}
