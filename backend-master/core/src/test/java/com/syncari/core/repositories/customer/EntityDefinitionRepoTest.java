package com.syncari.core.repositories.customer;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.util.Status;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class EntityDefinitionRepoTest extends AbstractSyncariTest {
    @Autowired
    EntityDefinitionRepo entityProxyRepo;
    @Test
    public void findEntities(){
        EntityDefinition draft = new EntityDefinition("custom1", "Account").setConnectorId("c1");
        draft.setDraftStatus(DraftStatus.NEW);
        draft.setStatus(Status.ACTIVE);
        draft = entityProxyRepo.save(draft);
        EntityDefinition approved = new EntityDefinition("custom1", "Account").setConnectorId("c1");
        approved.setDraftStatus(DraftStatus.APPROVED);
        approved.setStatus(Status.ACTIVE);
        approved = entityProxyRepo.save(approved);
        assertEquals(2, entityProxyRepo.findEntities("c1","CuStom1").size());
        
        EntityDefinition approvedInactive = new EntityDefinition("custom2", "Account").setConnectorId("c1");
        approved.setDraftStatus(DraftStatus.APPROVED);
        approved.setStatus(Status.INACTIVE);
        approvedInactive = entityProxyRepo.save(approvedInactive);
        var activeEntities = entityProxyRepo.findActiveEntitiesByConnectorIds(Set.of("c1"));
        assertEquals(0, activeEntities.stream().filter(e -> "custom2".equals(e.getApiName())).count());
        
        entityProxyRepo.deleteAll(List.of(draft,approved, approvedInactive));
    }

    @Test(expected = DuplicateKeyException.class)
    public void insertDuplicate() {
        EntityDefinition draft = null;
        EntityDefinition dupDraft = null;

        try {
            draft = new EntityDefinition("caseSensitiveEntity", "Case Sensitive Entity").setConnectorId("c1");
            draft.setDraftStatus(DraftStatus.NEW);
            draft.setStatus(Status.ACTIVE);
            draft = entityProxyRepo.save(draft);

            dupDraft = new EntityDefinition("CAseSEnsitiveEntiTY", "Case SENSITIVE Entity").setConnectorId("c1");
            dupDraft.setDraftStatus(DraftStatus.NEW);
            dupDraft.setStatus(Status.ACTIVE);
            entityProxyRepo.save(dupDraft);
        } finally {
            if (draft != null) {
                entityProxyRepo.delete(draft);
            }
        }

    }
}