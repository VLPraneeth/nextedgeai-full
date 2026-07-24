package com.syncari.connector.syncari;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.AutoConfigureDataMongo;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@AutoConfigureDataMongo
@EnableAutoConfiguration(exclude = MongoDataAutoConfiguration.class)
@ComponentScan(basePackages = "com.syncari")
@TestPropertySource("classpath:test_application.properties")
public class SyncariServiceTest {

    @Autowired
    SyncariService syncariService;

    @Test
    public void getByIdTest() {
        SyncRequest syncRequest = new SyncRequest();
        EntitySchema entitySchema = new EntitySchema("timeTicker");
        syncRequest.setEntitySchema(entitySchema);
        syncRequest.setData(Map.of("id", List.of(new EntityData("timeTicker").setId("1688053359758"))));
        syncRequest.setConnector(new ConnectorInfo().setId("id"));
        List<EntityData> entityData = syncariService.getByIds(syncRequest);
        assertFalse(entityData.isEmpty());
        assertTrue(entityData.get(0).getValueAsString("year").equalsIgnoreCase("2023"));
    }

}
