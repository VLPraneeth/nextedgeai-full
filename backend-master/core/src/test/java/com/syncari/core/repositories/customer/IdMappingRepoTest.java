package com.syncari.core.repositories.customer;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.IdMapping;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class IdMappingRepoTest extends AbstractSyncariTest {

    @Autowired
    private IdMappingRepo idMappingRepo;

    @Test
    public void duplicateInsertsFail(){
        idMappingRepo.deleteAll();
        IdMapping idMapping = new IdMapping();
        idMapping.setMappings(List.of(
                IdMapping.mapping("demo1","id1","account")
        ));
        idMapping.setSyncariId(UUID.randomUUID().toString());
        idMapping.setEntityName("Lead");

        final IdMapping saveed = idMappingRepo.save(idMapping);
        assertEquals(1, idMappingRepo.count());
        IdMapping newMap = idMappingRepo.findById(saveed.getId()).get();
        newMap.addMapping(
                "demo1","id1","account"
        );

        idMappingRepo.save(newMap );
        IdMapping dup = new IdMapping();
        dup.setMappings(List.of(
                IdMapping.mapping("demo1","id1","account")
        ));
        dup.setSyncariId(UUID.randomUUID().toString());
        dup.setEntityName("Lead");
        try {
            idMappingRepo.save(dup);
            fail();
        }catch (DuplicateKeyException e){
        }

    }

    @Test
    public void queryBySystemNameAndId() {
        idMappingRepo.deleteAll();
        IdMapping idMapping = new IdMapping();
        idMapping.setMappings(List.of(
                IdMapping.mapping("demo1","id1","account"),
                IdMapping.mapping("demo2","id2","account"))
        );
        idMapping.setEntityName("Account");
        idMapping.setSyncariId(UUID.randomUUID().toString());
        IdMapping leadMapping = new IdMapping();
        leadMapping.setEntityName("Lead");
        leadMapping.setMappings(List.of(
                IdMapping.mapping("demo1","id3","lead"),
                IdMapping.mapping("demo2","id4","lead"),
                IdMapping.mapping("demo2","id5","lead"))
        );
        leadMapping.setSyncariId(UUID.randomUUID().toString());
        IdMapping saved = idMappingRepo.save(idMapping);
        IdMapping savedLead = idMappingRepo.save(leadMapping);
        Optional<IdMapping> retrieved = idMappingRepo.findByExternalId("Account","demo1", "account","id1");
        assertNotNull(retrieved.get());
        assertEquals(saved, retrieved.get());
        Optional<IdMapping> retrieved2 = idMappingRepo.findByExternalId("Account","demo2", "account","id2");
        assertNotNull(retrieved2.get());
        assertEquals(saved, retrieved2.get());
        Optional<IdMapping> retrievedEmpty = idMappingRepo.findByExternalId("Account","demo2","account", "id3");
        assertTrue(retrievedEmpty.isEmpty());
        Optional<IdMapping> retrievedLeadMapping = idMappingRepo.findByExternalId("Lead","demo2", "lead","id4");
        assertEquals(savedLead, retrievedLeadMapping.get());


        List<IdMapping> retrieveEmptyLeadMappings = idMappingRepo.findByExternalIds("Lead","demo2","lead", List.of("id3"));
        assertTrue(retrieveEmptyLeadMappings .isEmpty());
        List<IdMapping> retrievedLeadMappings = idMappingRepo.findByExternalIds("Lead","demo1","lead", List.of("id3"));
        assertEquals(savedLead, retrievedLeadMappings.get(0));


        List<IdMapping> allrDemo2etrievedLeadMappings = idMappingRepo.findByExternalIds("Lead","demo2", "lead",List.of("id4","id5"));
        assertEquals(savedLead, allrDemo2etrievedLeadMappings.get(0));

    }

    @Test
    public void upsertUpdatesExistingMappings(){
        IdMapping idMapping = new IdMapping()
                .setSyncariId("SyncariActId1")
                .setEntityName("Account")
                .setMappings(List.of(
                IdMapping.mapping("demo1","id1","account")
        ));
        assertEquals(0,idMappingRepo.findAll().size());
        idMappingRepo.upsert(List.of(idMapping));
        assertEquals(1,idMappingRepo.findAll().size());
        IdMapping retrieved = idMappingRepo.findAll().get(0);
        assertEquals(idMapping.getSyncariId(),retrieved.getSyncariId());
        assertEquals(idMapping.getMappings(),retrieved.getMappings());
        assertNotNull(retrieved.getUpdatedAt());

        IdMapping withNewSynapse = new IdMapping()
                .setSyncariId("SyncariActId1")
                .setEntityName("Account")
                .setMappings(List.of(
                        IdMapping.mapping("demo2","id2","account_synapse2")
                ));
        idMappingRepo.upsert(List.of(withNewSynapse));
        assertEquals(1,idMappingRepo.findAll().size());

        retrieved = idMappingRepo.findAll().get(0);
        assertEquals(idMapping.getSyncariId(),retrieved.getSyncariId());
        assertEquals(idMapping.getEntityName(),retrieved.getEntityName());
        assertEquals(2,retrieved.getMappings().size());

        IdMapping withNewEntityDef = new IdMapping()
                .setSyncariId("SyncariActId1")
                .setEntityName("Account")
                .setMappings(List.of(
                        IdMapping.mapping("demo2","id3","account_synapse3")
                ));
        idMappingRepo.upsert(List.of(withNewEntityDef));
        assertEquals(1,idMappingRepo.findAll().size());

        retrieved = idMappingRepo.findAll().get(0);
        assertEquals(idMapping.getEntityName(),retrieved.getEntityName());
        assertEquals(idMapping.getSyncariId(),retrieved.getSyncariId());
        assertEquals(3,retrieved.getMappings().size());

    }

    @Test
    public void upsertWithDisconnectedFlagSettings(){
        IdMapping idMapping = new IdMapping()
                .setSyncariId("SyncariActId1")
                .setEntityName("Account")
                .setMappings(List.of(
                        IdMapping.mapping("demo1","id1","account"),
                        IdMapping.mapping("demo2","id2","account_synapse2"),
                        IdMapping.mapping("demo2","id3","account_synapse3")
                ));
        assertEquals(0,idMappingRepo.findAll().size());
        idMappingRepo.upsert(List.of(idMapping));
        assertEquals(1,idMappingRepo.findAll().size());
        IdMapping disconnectOne = new IdMapping()
                .setSyncariId("SyncariActId1")
                .setEntityName("Account")
                .setMappings(List.of(
                        IdMapping.mapping("demo2","id3","account_synapse3").setDisconnected(true)
                ));
        idMappingRepo.upsert(List.of(disconnectOne));
        //repeated upsert of disconnected records shouldn't cause dupes
        idMappingRepo.upsert(List.of(disconnectOne));
        assertEquals(1,idMappingRepo.findAll().size());

        IdMapping retrieved = idMappingRepo.findAll().get(0);
        assertEquals(idMapping.getEntityName(),retrieved.getEntityName());
        assertEquals(idMapping.getSyncariId(),retrieved.getSyncariId());
        assertEquals(3,retrieved.getMappings().size());
        final Optional<IdMapping.Mapping> disconnectedMapping = retrieved.findDisconnected("demo2","account_synapse3","id3");
        assertTrue(disconnectedMapping.get().isDisconnected());
        //reconnect the disconnected one. Make sure no new id mappings are added
        IdMapping reconnectOne = new IdMapping()
                .setSyncariId("SyncariActId1")
                .setEntityName("Account")
                .setMappings(List.of(
                        IdMapping.mapping("demo2","id3","account_synapse3")
                ));
        idMappingRepo.upsert(List.of(reconnectOne));

        assertEquals(1,idMappingRepo.findAll().size());

        retrieved = idMappingRepo.findAll().get(0);
        assertEquals(idMapping.getEntityName(),retrieved.getEntityName());
        assertEquals(idMapping.getSyncariId(),retrieved.getSyncariId());
        assertEquals(3,retrieved.getMappings().size());
        final Optional<IdMapping.Mapping> reconnected = retrieved.findMapping("demo2","account_synapse3","id3");
        assertTrue(reconnected.get().isConnected());


    }

    @Test
    public void upsertWithRepeatedSyncariIds(){
        IdMapping idMapping1 = new IdMapping()
                .setSyncariId("SyncariActId1")
                .setEntityName("Account")
                .addMapping("demo1","id1","account");
        IdMapping idMapping2 = new IdMapping()
                .setSyncariId("SyncariActId1")
                .setEntityName("Account")
                .addMapping("demo2","id2","account2");
        assertEquals(0,idMappingRepo.findAll().size());
        idMappingRepo.upsert(List.of(idMapping1,idMapping2));
        final List<IdMapping> all = idMappingRepo.findAll();
        assertEquals(1, all.size());

        IdMapping retrieved = all.get(0);
        assertEquals(idMapping1.getSyncariId(), retrieved.getSyncariId());
        assertEquals(idMapping1.getEntityName(), retrieved.getEntityName());
        assertEquals(2, retrieved.getConnectedMappings().size());
        assertTrue(retrieved.findMapping("demo1","account","id1").isPresent());
        assertTrue(retrieved.findMapping("demo2","account2","id2").isPresent());
    }

    @Test
    public void addMappingTests(){
        IdMapping idMapping = new IdMapping()
                .setSyncariId("SyncariActId1")
                .setEntityName("Account")
                .setMappings(new ArrayList<>(List.of(
                        IdMapping.mapping("demo1","id1","account"),
                        IdMapping.mapping("demo2","id2","account_synapse2"),
                        IdMapping.mapping("demo2","id3","account_synapse3")
                )));
        assertEquals(0,idMappingRepo.findAll().size());
        idMappingRepo.upsert(List.of(idMapping));
        assertEquals(1,idMappingRepo.findAll().size());

        idMapping.addMapping(IdMapping.mapping("demo2","id3","account_synapse3").setDisconnected(true));
        idMappingRepo.upsert(List.of(idMapping));
        assertEquals(1,idMappingRepo.findAll().size());

        IdMapping retrieved = idMappingRepo.findAll().get(0);
        assertEquals(idMapping.getEntityName(),retrieved.getEntityName());
        assertEquals(idMapping.getSyncariId(),retrieved.getSyncariId());
        assertEquals(3,retrieved.getMappings().size());
        final Optional<IdMapping.Mapping> disconnectedMapping = retrieved.findDisconnected("demo2","account_synapse3","id3");
        assertTrue(disconnectedMapping.get().isDisconnected());



        //reconnect the disconnected one. Make sure no new id mappings are added
        idMapping.addMapping(IdMapping.mapping("demo2","id3","account_synapse3"));
        idMappingRepo.upsert(List.of(idMapping));

        assertEquals(1,idMappingRepo.findAll().size());

        retrieved = idMappingRepo.findAll().get(0);
        assertEquals(idMapping.getEntityName(),retrieved.getEntityName());
        assertEquals(idMapping.getSyncariId(),retrieved.getSyncariId());
        assertEquals(3,retrieved.getMappings().size());
        final Optional<IdMapping.Mapping> reconnected = retrieved.findMapping("demo2","account_synapse3","id3");
        assertTrue(reconnected.get().isConnected());

        //what happens when we add another mapping from same engtitydef?
        idMapping.addMapping(IdMapping.mapping("demo2","id4","account_synapse3"));
        idMappingRepo.upsert(List.of(idMapping));
        assertEquals(1,idMappingRepo.findAll().size());
        retrieved = idMappingRepo.findAll().get(0);
        assertEquals(idMapping.getEntityName(),retrieved.getEntityName());
        assertEquals(idMapping.getSyncariId(),retrieved.getSyncariId());
        assertEquals(4,retrieved.getMappings().size());
        final Optional<IdMapping.Mapping> theFourth = retrieved.findMapping("demo2","account_synapse3","id4");
        assertTrue(theFourth.get().isConnected());
        assertEquals(2, retrieved.getMappings("demo2", "account_synapse3").size());
    }

    @Test
    public void upsertMultipleTimesIsNoOp(){
        List<IdMapping.Mapping> mappings = new ArrayList<>();
        mappings.add(IdMapping.mapping("demo1","id1","account"));
        IdMapping idMapping = new IdMapping()
                .setSyncariId("SyncariActId1")
                .setEntityName("Account")
                .setMappings(mappings);
        assertEquals(0,idMappingRepo.findAll().size());
        idMappingRepo.upsert(List.of(idMapping));
        assertEquals(1,idMappingRepo.findAll().size());
        IdMapping retrieved = idMappingRepo.findAll().get(0);
        assertEquals(idMapping.getSyncariId(),retrieved.getSyncariId());
        assertEquals(idMapping.getMappings(),retrieved.getMappings());
        assertNotNull(retrieved.getUpdatedAt());

        IdMapping upsertSame = new IdMapping()
                .setSyncariId("SyncariActId1")
                .setEntityName("Account")
                .setMappings(List.of(
                        IdMapping.mapping("demo1","id1","account")
                ));
        idMappingRepo.upsert(List.of(upsertSame));
        assertEquals(1,idMappingRepo.findAll().size());

        retrieved = idMappingRepo.findAll().get(0);
        assertEquals(idMapping.getSyncariId(),retrieved.getSyncariId());
        assertEquals(idMapping.getEntityName(),retrieved.getEntityName());
        assertEquals(1,retrieved.getMappings().size());



    }

    @Test
    public void queryBySyncariIdAndSystem() {
        IdMapping idMapping = new IdMapping();
        idMapping.setSyncariId("SyncariActId1");
        idMapping.setMappings(List.of(
                IdMapping.mapping("demo1","id1","account"),
                IdMapping.mapping("demo2","id2","account"))
        );
        idMapping.setEntityName("Account");
        IdMapping leadMapping = new IdMapping();
        leadMapping.setEntityName("Lead");
        leadMapping.setSyncariId("syncarileadid1");
        leadMapping.setMappings(List.of(
                IdMapping.mapping("demo1","id3","lead"),
                IdMapping.mapping("demo2","id4","lead"),
                IdMapping.mapping("demo2","id5","lead"))
        );
        IdMapping saved = idMappingRepo.save(idMapping);
        IdMapping savedLead = idMappingRepo.save(leadMapping);
        Optional<IdMapping> retrieved = idMappingRepo.findExistingMapping("Account","SyncariActId1", "demo1","account");
        assertNotNull(retrieved.get());
        assertEquals(saved, retrieved.get());

    }

    @Test
    public void multipleRecordsFromSameSystem(){
        IdMapping idMapping = new IdMapping();
        idMapping.setSyncariId("SyncariActId1");
        idMapping.setMappings(List.of(
                IdMapping.mapping("demo1", "id1", "account"),
                IdMapping.mapping("demo1", "id2", "account"))
        );
        idMapping.setEntityName("Account");
        IdMapping saved = idMappingRepo.save(idMapping);
        Optional<IdMapping> retrieved1 = idMappingRepo.findByExternalId("Account", "demo1", "account", "id1");
        assertNotNull(retrieved1.get());
        assertEquals(saved, retrieved1.get());
        Optional<IdMapping> retrieved2 = idMappingRepo.findByExternalId("Account", "demo1", "account", "id2");
        assertNotNull(retrieved2.get());
        assertEquals(saved, retrieved2.get());
    }
    @Test
    public void findByExternalIds() {
        IdMapping idMapping = new IdMapping();
        idMapping.setSyncariId("SyncariActId1");
        idMapping.setMappings(List.of(
                IdMapping.mapping("demo1","id1","account"),
                IdMapping.mapping("demo2","id2","account"))
        );
        idMapping.setEntityName("Account");

        IdMapping idMapping1 = new IdMapping();
        idMapping1.setSyncariId("SyncariActId2");
        idMapping1.setMappings(List.of(
                IdMapping.mapping("demo1","id3","account"),
                IdMapping.mapping("demo2","id4","account"))
        );
        idMapping1.setEntityName("Account");

        idMappingRepo.saveAll(List.of(idMapping,idMapping1));
        List<IdMapping> retrieved = idMappingRepo.findByExternalIds("Account","demo2", "account", List.of("id2","id4"));
        assertEquals(2,retrieved.size());
        assertEquals(Set.of(idMapping.getSyncariId(),idMapping1.getSyncariId()),retrieved.stream().map(r->r.getSyncariId()).collect(Collectors.toSet()));

    }

    @Test
    public void findOrphans(){
        Instant startingPoint = Instant.now();
        IdMapping idMapping = new IdMapping()
                .setSyncariId("SyncariActId1")
                .setEntityName("Account")
                .setMappings(List.of(
                        IdMapping.mapping("demo1","id1","account"),
                        IdMapping.mapping("demo2","id2","account_synapse2"),
                        IdMapping.mapping("demo2","id3","account_synapse3")
                ));
        assertEquals(0,idMappingRepo.findAll().size());
        idMapping=idMappingRepo.save(idMapping);
        assertEquals(1,idMappingRepo.findAll().size());
        //no orphans
        assertEquals(0,idMappingRepo.findOrphans("Account", Instant.EPOCH).size());
        //disconnect all
        idMapping.getMappings().forEach(m->m.setDisconnected(true));
        idMappingRepo.save(idMapping);
        List<IdMapping> orphans = idMappingRepo.findOrphans("Account", Instant.EPOCH);
        assertEquals(1, orphans.size());
        assertEquals(idMapping.getSyncariId(),orphans.get(0).getSyncariId());
        assertEquals(idMapping.getId(),orphans.get(0).getId());

        //honors timestamps
        orphans = idMappingRepo.findOrphans("Account", Instant.now());
        assertEquals(0, orphans.size());
        orphans = idMappingRepo.findOrphans("Account",startingPoint );
        assertEquals(1, orphans.size());

    }
}
