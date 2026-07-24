package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.actions.CustomActionDefinition;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.Assert.*;

public class ActionServiceTest extends AbstractSyncariTest {

    @Autowired
    ActionService actionService;

    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @Test
    public void getActions(){
        List<ActionDefinition> actions = actionService.getAllActions(); //load all standard actions created by syncari
        assertEquals(19, actions.size());
        actions.forEach(a -> {
            assertEquals(Type.STANDARD, a.getType());
        });

    }

    @Test
    public void getEntityActions(){
        List<ActionDefinition> actions = actionService.getEntityActions();
        assertEquals(19, actions.size());
        // assert that list contain wait action
        assertTrue(actions.stream().anyMatch(a -> ActionConstants.REQUEUE_RECORD.equals(a.getName())));
    }

    @Test
    public void getAttributeActions(){
        List<ActionDefinition> actions = actionService.getAttributeActions();
        assertEquals(18, actions.size());
        // assert that list doesnot contain wait action
        assertFalse(actions.stream().anyMatch(a -> ActionConstants.REQUEUE_RECORD.equals(a.getName())));
    }

    @Test
    public void getActionsWithCustomAction(){
        // create a custom action
        actionService.metadataCache.invalidateAll();
        ActionDefinition customAction = new ActionDefinition();
        customAction.setName("customAction").setDisplayName("Custom Action");
        customAction.setType(Type.CUSTOM);
        customAction.setScope(Scope.ENTITY);
        customAction.setDraftStatus(DraftStatus.APPROVED);
        customAction = actionDefinitionRepo.save(customAction);

        List<ActionDefinition> entityActions = actionService.getEntityActions();
        assertEquals(20, entityActions.size());
        // assert that list contain wait action and custom action
        assertTrue(entityActions.stream().anyMatch(a -> ActionConstants.REQUEUE_RECORD.equals(a.getName())));
        assertTrue(entityActions.stream().anyMatch(a -> "customAction".equals(a.getName())));

        List<ActionDefinition> attrActions = actionService.getAttributeActions();
        assertEquals(19, attrActions.size());
        // assert that list doesnot contain wait action and contain custom action
        assertFalse(attrActions.stream().anyMatch(a -> ActionConstants.REQUEUE_RECORD.equals(a.getName())));
        assertTrue(attrActions.stream().anyMatch(a -> "customAction".equals(a.getName())));

        List<ActionDefinition> allActions = actionService.getAllActions();
        assertEquals(20, allActions.size());

        actionDefinitionRepo.delete(customAction);
        actionService.metadataCache.invalidateAll();
    }

    @Test
    public void getActionsWithCustomActionAndGlobalShared(){
        // create a custom  action with global shared id
        actionService.metadataCache.invalidateAll();
        CustomActionDefinition customAction = new CustomActionDefinition();
        customAction.setName("customAction").setDisplayName("Custom Action");
        customAction.setType(Type.CUSTOM);
        customAction.setScope(Scope.ENTITY);
        customAction.setDraftStatus(DraftStatus.NEW);
        customAction.setGlobalSharedItemId("dummy");
        customAction = actionDefinitionRepo.save(customAction);

        List<ActionDefinition> entityActions = actionService.getEditableActions();
        assertEquals(0, entityActions.size());
        customAction.setGlobalSharedItemId(null);
        customAction = actionDefinitionRepo.save(customAction);
        entityActions = actionService.getEditableActions();
        assertEquals(1, entityActions.size());


        actionDefinitionRepo.delete(customAction);
        actionService.metadataCache.invalidateAll();
    }
}
