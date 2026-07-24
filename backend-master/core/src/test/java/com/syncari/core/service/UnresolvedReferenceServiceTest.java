package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.TestConfig;
import com.syncari.core.model.UnresolvedReference;
import com.syncari.core.repositories.customer.UnresolvedReferenceRepo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class UnresolvedReferenceServiceTest extends AbstractSyncariTest {
    @Autowired
    UnresolvedReferenceService unresolvedReferenceService;
    @Autowired
    UnresolvedReferenceRepo unresolvedReferenceRepo;

    private List<UnresolvedReference> createUnresolvedReferences(int num, String externalEntityDefName, String connectorId) {
        List<UnresolvedReference> references = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            references.add(new UnresolvedReference().setExternalRefRecordId("externalId" + i)
                    .setExternalRefEntityName(externalEntityDefName)
                    .setSyncariAttributeName("someAttribute")
                    .setSyncariRecordId("syncariRecord"+i)
                    .setConnectorId(connectorId)
                    .setSyncariEntityDefId("syncariEntityDefId")
            );
        }
        return references;
    }


    @Test
    public void pagination() {
        List<UnresolvedReference> unresolvedReferences = createUnresolvedReferences(10, "externalEntity", "testConnector");
        unresolvedReferenceRepo.saveAll(unresolvedReferences);
        String nextId = "";
        List<UnresolvedReference> page1 = unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "testConnector", "externalEntity", 3);
        assertEquals(3, page1.size());

        nextId = page1.get(page1.size() - 1).getId();
        List<UnresolvedReference> page2 = unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "testConnector", "externalEntity", 3);
        assertEquals(3, page2.size());

        nextId = page2.get(page2.size() - 1).getId();
        List<UnresolvedReference> page3 = unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "testConnector", "externalEntity", 3);
        assertEquals(3, page3.size());

        nextId = page3.get(page3.size() - 1).getId();
        List<UnresolvedReference> page4 = unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "testConnector", "externalEntity", 3);
        assertEquals(1, page4.size());
        
        nextId = page4.get(page4.size() - 1).getId();
        List<UnresolvedReference> page5 = unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "testConnector", "externalEntity", 3);
        assertTrue(page5.isEmpty());
    }

    @Test
    public void returnsOnlyUnResolvedRefs() {
        List<UnresolvedReference> unresolvedReferences = createUnresolvedReferences(4, "externalEntity", "testConnector");
        unresolvedReferences.get(0).setResolvedSyncariValue("this_is_resolved!");
        unresolvedReferenceRepo.saveAll(unresolvedReferences);
        String nextId = "";
        List<UnresolvedReference> page1 = unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "testConnector", "externalEntity", 10);
        assertEquals(3, page1.size());

        nextId = page1.get(page1.size() - 1).getId();
        List<UnresolvedReference> page2 = unresolvedReferenceService.getUnresolvedReferencesFor(nextId, "testConnector", "externalEntity", 10);
        assertTrue(page2.isEmpty());

    }
}
