package com.syncari.viper.streams.stages;

import com.syncari.connector.EntityData;
import com.syncari.core.model.*;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

public class RecordAuthorityTest {

    @Test
    public void latestRecordWins(){
        AttributeValue value1 = new AttributeValue("value1", new AttributeDefinition().setEntityId("e1"), "con1", new MappingNode());
        AttributeValue value2 = new AttributeValue("value2", new AttributeDefinition().setEntityId("e2"), "con2", new MappingNode());
        StagedBatchRecord record1 = new StagedBatchRecord().setEntityData(new EntityData().setLastModified(Instant.now().minusSeconds(1).toEpochMilli()).setConnectorId("con1").setId("r1")).setExternalEntityDefinitionId("e1").setExternalRecordId("r1");
        StagedBatchRecord record2 = new StagedBatchRecord().setEntityData(new EntityData().setLastModified(Instant.now().minusSeconds(10).toEpochMilli()).setConnectorId("con2").setId("r2")).setExternalEntityDefinitionId("e2").setExternalRecordId("r2");
        LatestRecordAuthority latestRecordAuthority = new LatestRecordAuthority(List.of(record1,record2), List.of(value1,value2), new CoreAttributeNodeConfig());
        Optional<AttributeValue> authority = latestRecordAuthority.authority();
        assertEquals(value1.value,authority.get().value);
        record2.getEntityData().setLastModified(Instant.now().toEpochMilli());
        authority = latestRecordAuthority.authority();
        assertEquals(value2.value,authority.get().value);
    }


    @Test
    public void selectedSynapseWins(){
        AttributeValue value1 = new AttributeValue("value1", new AttributeDefinition().setEntityId("e1"), "con1", new MappingNode());
        AttributeValue value2 = new AttributeValue("value2", new AttributeDefinition().setEntityId("e2"), "con2", new MappingNode());
        StagedBatchRecord record1 = new StagedBatchRecord().setEntityData(new EntityData().setLastModified(Instant.now().toEpochMilli()).setConnectorId("con1").setId("r1")).setExternalEntityDefinitionId("e1").setExternalRecordId("r1");
        StagedBatchRecord record2 = new StagedBatchRecord().setEntityData(new EntityData().setLastModified(Instant.now().minusSeconds(10).toEpochMilli()).setConnectorId("con2").setId("r2")).setExternalEntityDefinitionId("e2").setExternalRecordId("r2");
        CoreAttributeNodeConfig coreConfig = new CoreAttributeNodeConfig();
        coreConfig.setDataAuthority(DataAuthority.selectedConnector("con2"));
        SelectedConnectorAuthority selectedConnectorAuthority = new SelectedConnectorAuthority(List.of(record1,record2), List.of(value1,value2), coreConfig);
        Optional<AttributeValue> authority = selectedConnectorAuthority.authority();
        assertEquals(value2,authority.get());
        coreConfig.setDataAuthority(DataAuthority.selectedConnector("con1"));
        authority = selectedConnectorAuthority.authority();
        assertEquals(value1,authority.get());
    }

    @Test
    public void selectedSynapseDefaultsToLatest(){
        AttributeValue value1 = new AttributeValue("value1", new AttributeDefinition().setEntityId("e1"), "con1", new MappingNode());
        AttributeValue value2 = new AttributeValue("value2", new AttributeDefinition().setEntityId("e2"), "con2", new MappingNode());
        StagedBatchRecord record1 = new StagedBatchRecord().setEntityData(new EntityData().setLastModified(Instant.now().minusSeconds(10).toEpochMilli()).setConnectorId("con1").setId("r1")).setExternalEntityDefinitionId("e1").setExternalRecordId("r1");
        StagedBatchRecord record2 = new StagedBatchRecord().setEntityData(new EntityData().setLastModified(Instant.now().minusSeconds(20).toEpochMilli()).setConnectorId("con2").setId("r2")).setExternalEntityDefinitionId("e2").setExternalRecordId("r2");
        CoreAttributeNodeConfig coreConfig = new CoreAttributeNodeConfig();
        coreConfig.setDataAuthority(DataAuthority.selectedConnector("con3"));
        SelectedConnectorAuthority selectedConnectorAuthority = new SelectedConnectorAuthority(List.of(record1,record2), List.of(value1,value2), coreConfig);
        Optional<AttributeValue> authority = selectedConnectorAuthority.authority();
        assertEquals(value1.value,authority.get().value);

        record2.getEntityData().setLastModified(Instant.now().toEpochMilli());
        authority = selectedConnectorAuthority.authority();
        assertEquals(value2.value,authority.get().value);

    }
}