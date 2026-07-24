package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.Fragment;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Layout;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.SharedItem;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.Tag;
import com.syncari.core.model.User;
import com.syncari.core.model.UserPreference;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.misc.Sharable;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.model.misc.fragment.FragmentEdge;
import com.syncari.core.model.misc.fragment.FragmentGraph;
import com.syncari.core.model.misc.fragment.FragmentNode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.repositories.customer.EdgeRepo;
import com.syncari.core.repositories.customer.FragmentRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.repositories.customer.TagRepo;
import com.syncari.core.repositories.syncari.SharedItemRepo;

public class FragmentServiceTest extends AbstractSyncariTest {

    @Autowired
    FunctionService functionService;

    @Autowired
    FragmentService fragmentService;

    @Autowired
    MappingNodeRepo mappingNodeRepo;

    @Autowired
    EdgeRepo edgeRepo;

    @Autowired
    FragmentRepo fragmentRepo;

    @Autowired
    TagRepo tagRepo;

    @Autowired
    TagService tagService;

    @Autowired
    ProvisioningService provisioningService;

    @Autowired
    SharedItemRepo sharedItemRepo;

    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;

    @Autowired
    UserService userService;

    @Override
    public void tearDown() {
        resetRepos(edgeRepo, mappingNodeRepo, fragmentRepo, tagRepo);
        super.tearDown();
    }

    @Test
    public void testCreateEntityFragment(){
        Fragment fragment = createEntityFragment();
        Fragment retrieved = fragmentService.getFragment(Scope.ENTITY, fragment.getId());
        assertEquals(fragment.getId(), retrieved.getId());
        assertEquals(Scope.ENTITY, retrieved.getScope());
        assertEquals("Entity Fragment", retrieved.getName());
        assertFalse(retrieved.isShared());
        assertEquals(3, retrieved.getFragmentGraph().getNodes().size());
        assertEquals(2, retrieved.getFragmentGraph().getEdges().size());
        assertEquals(5, retrieved.getFragmentGraph().getLayouts().size());

        Set<String> tagNames = tagService.getTagNames(Taggable.fragment, retrieved.getId());
        assertEquals(2, tagNames.size());
        assertTrue(tagNames.contains("entityTag1"));
        assertTrue(tagNames.contains("entityTag2"));
    }

    @Test
    public void testCreateAttributeFragment(){
        Fragment fragment = createAttributeFragment();
        Fragment retrieved = fragmentService.getFragment(Scope.ATTRIBUTE, fragment.getId());
        assertEquals(fragment.getId(), retrieved.getId());
        assertEquals(Scope.ATTRIBUTE, retrieved.getScope());
        assertEquals("Attribute Fragment", retrieved.getName());
        assertFalse(retrieved.isShared());
        assertEquals(3, retrieved.getFragmentGraph().getNodes().size());
        assertEquals(2, retrieved.getFragmentGraph().getEdges().size());
        assertEquals(5, retrieved.getFragmentGraph().getLayouts().size());

        Set<String> tagNames = tagService.getTagNames(Taggable.fragment, retrieved.getId());
        assertEquals(2, tagNames.size());
        assertTrue(tagNames.contains("attributeTag1"));
        assertTrue(tagNames.contains("attributeTag2"));
    }

    @Test
    public void createDuplicateNameEntityFragment(){
        Fragment fragment1 = createEntityFragment();
        try{
            createEntityFragment();
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Fragment with name Entity Fragment already exists", e.getMessage());
        }
        assertEquals(1, fragmentService.listEntityFragments().size());
    }

    @Test
    public void createDuplicateNameAttributeFragment(){
        Fragment fragment1 = createAttributeFragment();
        try{
            createAttributeFragment();
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Fragment with name Attribute Fragment already exists", e.getMessage());
        }

        assertEquals(1, fragmentService.listFieldFragments().size());
    }

    @Test
    public void updateDuplicateNameEntityFragment(){
        Fragment fragment1 = createEntityFragment();
        Fragment fragment2 = fragmentRepo.save(new Fragment().setName("New Entity Graph").setOwnerUserId("user123")
                .setScope(Scope.ENTITY).setFragmentGraph(createEntityFragmentGraph()));
        try{
            fragment2.setName(fragment1.getName());
            fragmentService.updateFragment(fragment2);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Fragment with name Entity Fragment already exists", e.getMessage());
        }

        fragment2.setName("New Entity Graph Updated");
        fragmentService.updateFragment(fragment2);
        assertTrue(fragmentRepo.findByName(fragment2.getName()).isPresent());

    }

    @Test
    public void updateDuplicateNameAttributeFragment(){
        Fragment fragment1 = createAttributeFragment();
        Fragment fragment2 = fragmentRepo.save(new Fragment().setName("New Attribute Graph").setOwnerUserId("user123")
                .setScope(Scope.ATTRIBUTE).setFragmentGraph(createAttributeFragmentGraph()));
        try{
            fragment2.setName(fragment1.getName());
            fragmentService.updateFragment(fragment2);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Fragment with name Attribute Fragment already exists", e.getMessage());
        }

        fragment2.setName("New Attribute Graph Updated");
        fragmentService.updateFragment(fragment2);
        assertTrue(fragmentRepo.findByName(fragment2.getName()).isPresent());
    }

    @Test
    public void listEntityFragment(){
        assertTrue(fragmentService.listEntityFragments().isEmpty());
        Fragment created = createEntityFragment();
        List<Fragment> retrieved = fragmentService.listEntityFragments();

        assertEquals(1, retrieved.size());
        assertEquals(created.getId(), retrieved.get(0).getId());
        assertEquals(Scope.ENTITY, retrieved.get(0).getScope());
        assertEquals("Entity Fragment", retrieved.get(0).getName());
        assertFalse(retrieved.get(0).isShared());
    }

    @Test
    public void listAttributeFragment(){
        assertTrue(fragmentService.listFieldFragments().isEmpty());
        Fragment created = createAttributeFragment();
        List<Fragment> retrieved = fragmentService.listFieldFragments();

        assertEquals(1, retrieved.size());
        assertEquals(created.getId(), retrieved.get(0).getId());
        assertEquals(Scope.ATTRIBUTE, retrieved.get(0).getScope());
        assertEquals("Attribute Fragment", retrieved.get(0).getName());
        assertFalse(retrieved.get(0).isShared());
    }

    @Test
    public void getFragment(){
        Fragment entityFragment = createEntityFragment();
        Fragment retrieved = fragmentService.getFragment(Scope.ENTITY, entityFragment.getId());
        assertEquals(entityFragment.getId(), retrieved.getId());
        assertEquals(Scope.ENTITY, retrieved.getScope());

        Fragment attribFragment = createAttributeFragment();
        retrieved = fragmentService.getFragment(Scope.ATTRIBUTE, attribFragment.getId());
        assertEquals(attribFragment.getId(), retrieved.getId());
        assertEquals(Scope.ATTRIBUTE, retrieved.getScope());

        // getFragment with entity fragment's id and attribute scope
        try {
            fragmentService.getFragment(Scope.ATTRIBUTE, entityFragment.getId());
            fail();
        } catch (RuntimeException e){
            assertEquals(String.format("Attribute Fragment with id %s doesn't exist", entityFragment.getId()), e.getMessage());
        }
    }

    @Test
    public void updateEntityFragment(){
        Fragment created = createEntityFragment();
        Fragment retrieved = fragmentService.getFragment(Scope.ENTITY, created.getId());
        retrieved.setTags(tagService.findTagsFor(Taggable.fragment, retrieved.getId()));
        assertEquals(created.getId(), retrieved.getId());
        assertEquals("Entity Fragment", retrieved.getName());

        Set<String> tagNames = tagService.getTagNames(Taggable.fragment, retrieved.getId());
        assertEquals(2, tagNames.size());
        assertTrue(tagNames.contains("entityTag1"));
        assertTrue(tagNames.contains("entityTag2"));

        retrieved.setName("Entity Fragment Update");
        retrieved.getTags().removeIf( t-> t.getName().equals("entityTag1"));
        retrieved.getTags().add(new Tag("entityTag3", true, Taggable.fragment, null));

        Fragment updated = fragmentService.updateFragment(retrieved);
        assertEquals("Entity Fragment Update", updated.getName());
        assertEquals(retrieved.getId(), updated.getId());

        retrieved = fragmentService.getFragment(Scope.ENTITY, updated.getId());
        assertEquals("Entity Fragment Update", retrieved.getName());

        Set<String> updatedTagNames = tagService.getTagNames(Taggable.fragment, retrieved.getId());
        assertEquals(2, updatedTagNames.size());
        assertFalse(updatedTagNames.contains("entityTag1"));
        assertTrue(updatedTagNames.contains("entityTag2"));
        assertTrue(updatedTagNames.contains("entityTag3"));
    }

    @Test
    public void updateAttributeFragment(){
        Fragment created = createAttributeFragment();
        Fragment retrieved = fragmentService.getFragment(Scope.ATTRIBUTE, created.getId());
        retrieved.setTags(tagService.findTagsFor(Taggable.fragment, retrieved.getId()));
        assertEquals(created.getId(), retrieved.getId());
        assertEquals("Attribute Fragment", retrieved.getName());

        Set<String> tagNames = tagService.getTagNames(Taggable.fragment, retrieved.getId());
        assertEquals(2, tagNames.size());
        assertTrue(tagNames.contains("attributeTag1"));
        assertTrue(tagNames.contains("attributeTag2"));

        retrieved.setName("Attribute Fragment Update");
        retrieved.getTags().removeIf( t-> t.getName().equals("attributeTag1"));
        retrieved.getTags().add(new Tag("attributeTag3", true, Taggable.fragment, null));

        Fragment updated = fragmentService.updateFragment(retrieved);
        assertEquals("Attribute Fragment Update", updated.getName());
        assertEquals(retrieved.getId(), updated.getId());

        retrieved = fragmentService.getFragment(Scope.ATTRIBUTE, updated.getId());
        assertEquals("Attribute Fragment Update", retrieved.getName());

        Set<String> updatedTagNames = tagService.getTagNames(Taggable.fragment, retrieved.getId());
        assertEquals(2, updatedTagNames.size());
        assertFalse(updatedTagNames.contains("attributeTag1"));
        assertTrue(updatedTagNames.contains("attributeTag2"));
        assertTrue(updatedTagNames.contains("attributeTag3"));
    }

    @Test
    public void updateSharedFragment(){

        Fragment fragment = createEntityFragment();
        fragment.setShared(true);
        fragment.setSharedItemId("shared123");
        fragment = fragmentRepo.save(fragment);
        try{
            fragmentService.updateFragment(fragment);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Cannot update fragment shared by others", e.getMessage());
        }
    }

    @Test
    public void deleteEntityFragment(){
        Fragment created = createEntityFragment();
        List<Tag> tags = tagService.findTagsFor(Taggable.fragment, created.getId());
        assertFalse(tags.isEmpty());
        fragmentService.deleteFragment(Scope.ENTITY, created.getId());

        List<Fragment> entityFragments = fragmentService.listEntityFragments();
        assertTrue(entityFragments.isEmpty());
        tags = tagService.findTagsFor(Taggable.fragment, created.getId());
        assertTrue(tags.isEmpty());
    }

    @Test
    public void deleteAttributeFragment(){
        Fragment created = createAttributeFragment();
        List<Tag> tags = tagService.findTagsFor(Taggable.fragment, created.getId());
        assertFalse(tags.isEmpty());
        fragmentService.deleteFragment(Scope.ATTRIBUTE, created.getId());

        List<Fragment> attributeFragments = fragmentService.listEntityFragments();
        assertTrue(attributeFragments.isEmpty());
        tags = tagService.findTagsFor(Taggable.fragment, created.getId());
        assertTrue(tags.isEmpty());
    }

    @Test
    public void deleteSharedFragment(){
        User user = SyncariContext.getUser();
        Instance shareInstance = provisioningService.provisionInstance(SyncariContext.getOrganziation(),
                "sharedInstanceFragDel", "sharedInstanceFragDel", InstanceType.trial, "default", user);
        try{
            Fragment fragment = createEntityFragment();
            fragmentService.share(fragment.getId(), List.of(shareInstance.getSyncariId()));
            Optional<SharedItem> sharedItem = sharedItemRepo.findSharedItemBySourceIdAndItemType(fragment.getId(), Sharable.FRAGMENT);
            assertTrue(sharedItem.isPresent());
            assertEquals(1, sharedItem.get().getSharingInstances().size());
            assertTrue(sharedItem.get().getSharingInstances().containsKey(shareInstance.getSyncariId()));

            SyncariContext.runWithContext(SyncariContext.getOrganziation(), shareInstance, user, () -> {
                var fragments = fragmentService.listEntityFragments();
                assertEquals(1, fragments.size());
                var f = fragments.get(0);
                assertTrue(f.isShared());
                assertEquals(sharedItem.get().getId(), f.getSharedItemId());
                assertEquals(f.getId(), sharedItem.get().getSharingInstances().get(shareInstance.getSyncariId()));
            });

            // deleting source fragment while still shared throws an error
            try {
                fragmentService.deleteFragment(fragment.getScope(), fragment.getId());
            } catch (RuntimeException e){
                assertEquals("Remove sharing to delete fragment", e.getMessage());
            }

            // delete from sharedInstance - success
            SyncariContext.runWithContext(SyncariContext.getOrganziation(), shareInstance, user, () -> {
                var fragments = fragmentService.listEntityFragments();
                assertEquals(1, fragments.size());
                var f = fragments.get(0);
                assertTrue(f.isShared());
                fragmentService.deleteFragment(f.getScope(), f.getId());

                fragments = fragmentService.listEntityFragments();
                assertTrue(fragments.isEmpty());
            });

            Optional<SharedItem> sharedItemAfterDeletingFromSharedInstance = sharedItemRepo.findSharedItemBySourceIdAndItemType(fragment.getId(), Sharable.FRAGMENT);
            assertTrue(sharedItemAfterDeletingFromSharedInstance.isPresent());
            assertTrue(sharedItemAfterDeletingFromSharedInstance.get().getSharingInstances().isEmpty());

            // delete the source fragment - success
            fragmentService.deleteFragment(fragment.getScope(), fragment.getId());
            assertTrue(fragmentService.listEntityFragments().isEmpty());
            Optional<SharedItem> sharedItemAfterDeletingSourceFragment = sharedItemRepo.findSharedItemBySourceIdAndItemType(fragment.getId(), Sharable.FRAGMENT);
            assertFalse(sharedItemAfterDeletingSourceFragment.isPresent());

        } finally {
            provisioningService.deprovisionInstance(shareInstance.getSyncariId(), false);
        }
    }

    @Test
    public void shareSharedFragment(){
        Fragment fragment = createEntityFragment();
        fragment.setShared(true);
        fragmentRepo.save(fragment);
        try {
            fragmentService.share(fragment.getId(), List.of("ABC123"));
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Shared Fragments by other users can't be shared again", e.getMessage());
        }
    }

    @Test
    public void shareAndUnshareWithInstanceWithoutUserAccess(){
        Fragment fragment = createEntityFragment();
        // validate sharing - user do not have access to ABC123 instance and sharing should fail
        try {
            fragmentService.share(fragment.getId(), List.of("ABC123"));
            fail();
        } catch (SyncariValidationException e){
            assertEquals("You do not have permission to share/unshare fragment with instance ABC123", e.getMessage());
        }

        // validate unsharing - fragment is shared with instance ABC123 (mocked) but user doesn't have permission to instance and unsharing should fail
        SharedItem sharedItem = sharedItemRepo.findSharedItemBySourceIdAndItemType(fragment.getId(), Sharable.FRAGMENT).get();
        sharedItem.setSharingInstances(Map.of("ABC123", "fragment123"));
        sharedItemRepo.save(sharedItem);

        try {
            fragmentService.share(fragment.getId(), List.of());
            fail();
        } catch (SyncariValidationException e){
            assertEquals("You do not have permission to share/unshare fragment with instance ABC123", e.getMessage());
        }
    }

    @Test
    public void shareAndUnshareFragment(){
        User user = SyncariContext.getUser();
        assertEquals(1, SyncariContext.getOrganziation().getInstances().size());
        Instance shareInstance = provisioningService.provisionInstance(SyncariContext.getOrganziation(),
                "shareInstance", "shareInstance", InstanceType.trial, "default", user);
        try {
            Fragment fragment = createEntityFragment();

            fragmentService.share(fragment.getId(), List.of(shareInstance.getSyncariId()));
            Optional<SharedItem> sharedItem = sharedItemRepo.findSharedItemBySourceIdAndItemType(fragment.getId(), Sharable.FRAGMENT);
            assertTrue(sharedItem.isPresent());
            assertEquals(1, sharedItem.get().getSharingInstances().size());
            assertTrue(sharedItem.get().getSharingInstances().containsKey(shareInstance.getSyncariId()));

            SyncariContext.runWithContext(SyncariContext.getOrganziation(), shareInstance, user, () -> {
                var fragments = fragmentService.listEntityFragments();
                assertEquals(1, fragments.size());
                var f = fragments.get(0);
                assertTrue(f.isShared());
                assertEquals(sharedItem.get().getId(), f.getSharedItemId());
                assertEquals(3, f.getFragmentGraph().getNodes().size());
                assertEquals(2, f.getFragmentGraph().getEdges().size());
                assertEquals(5, f.getFragmentGraph().getLayouts().size());

                f.getFragmentGraph().getNodes().forEach(node -> {
                    var configMap = node.getConfiguration().getConfigMap();
                    if (node.getType().equals(MappingNodeType.FUNCTION)) {
                        // get functionNode and check if the function definitions are resolved correctly
                        var funcDefinition = functionService.findByNameAndScope(node.getApiName(), node.getScope()).get();
                        assertEquals(funcDefinition.getId(), configMap.get("configId"));
                        assertEquals(funcDefinition.getId(), configMap.get("definition"));
                    } else if (MappingNodeType.CORE_ENTITY.equals(node.getType())) {
                        // entityDefinitions are not carried forward to shared instances
                        assertNull(configMap.get("entityDefinition"));
                    } else if (MappingNodeType.CORE_ATTRIBUTE.equals(node.getType())) {
                        // attributeDefinitions are not carried forward to shared instances
                        assertNull(configMap.get("attributeDefinition"));
                    } else if (MappingNodeType.ACTION.equals(node.getType())) {
                        // actionName will remain same in shared instances
                        var actionDef = actionDefinitionRepo.findByName(node.getApiName()).get();
                        assertEquals(actionDef.getName(), configMap.get("name"));
                    } else {
                        fail(String.format("Nodes of Type %s are not supported by Fragments", node.getType().name()));
                    }

                });
                assertEquals(f.getId(), sharedItem.get().getSharingInstances().get(shareInstance.getSyncariId()));
            });

            // share again with already shared instance - no change
            fragmentService.share(fragment.getId(), List.of(shareInstance.getSyncariId()));
            var resharedItem = sharedItemRepo.findSharedItemBySourceIdAndItemType(fragment.getId(), Sharable.FRAGMENT);
            assertTrue(resharedItem.isPresent());
            assertEquals(1, resharedItem.get().getSharingInstances().size());
            assertTrue(resharedItem.get().getSharingInstances().containsKey(shareInstance.getSyncariId()));

            SyncariContext.runWithContext(SyncariContext.getOrganziation(), shareInstance, user, () -> {
                var fragments = fragmentService.listEntityFragments();
                assertEquals(1, fragments.size());
                var f = fragments.get(0);
                assertTrue(f.isShared());
                assertEquals(resharedItem.get().getId(), f.getSharedItemId());
                assertEquals(f.getId(), resharedItem.get().getSharingInstances().get(shareInstance.getSyncariId()));
            });

            // remove sharing
            fragmentService.share(fragment.getId(), List.of());
            Optional<SharedItem> itemAfterUnsharing = sharedItemRepo.findSharedItemBySourceIdAndItemType(fragment.getId(), Sharable.FRAGMENT);
            assertTrue(itemAfterUnsharing.isPresent());
            assertTrue(itemAfterUnsharing.get().getSharingInstances().isEmpty());

            // shared fragment will be deleted from shared instance
            SyncariContext.runWithContext(SyncariContext.getOrganziation(), shareInstance, user, () -> {
                var fragments = fragmentService.listEntityFragments();
                assertTrue(fragments.isEmpty());
            });
        } finally {
            provisioningService.deprovisionInstance(shareInstance.getSyncariId(), false);
        }
        assertEquals(1, SyncariContext.getOrganziation().getInstances().size());
    }

    @Test
    public void showAndHideEntityFragment(){
        Fragment entityFragment = createEntityFragment();
        User user = SyncariContext.getUser();
        try {
            fragmentService.showFragment(Scope.ENTITY, entityFragment.getId());
        } catch (RuntimeException e){
            assertEquals("This action is not allowed for a non-shared fragment.", e.getMessage());
        }

        try {
            fragmentService.hideFragment(Scope.ENTITY, entityFragment.getId());
        } catch (RuntimeException e){
            assertEquals("This action is not allowed for a non-shared fragment.", e.getMessage());
        }
        entityFragment = fragmentRepo.save(entityFragment.setShared(true));
        UserPreference pref = userService.getPreference(user.getId());
        assertNull(pref.getFragmentShare());

        // showFragment for non hidden fragment will no change anything
        fragmentService.showFragment(Scope.ENTITY, entityFragment.getId());
        pref = userService.getPreference(user.getId());
        assertNull(pref.getFragmentShare());

        // Hide fragment will make an entry in UserPreference
        fragmentService.hideFragment(Scope.ENTITY, entityFragment.getId());
        pref = userService.getPreference(user.getId());
        assertNotNull(pref.getFragmentShare());
        assertFalse(pref.getFragmentShare().getHidden().isEmpty());
        assertEquals(1, pref.getFragmentShare().getHidden().size());
        assertTrue(pref.getFragmentShare().getHidden().contains(entityFragment.getId()));

        // hideFragment for already hidden fragment will be no-op
        fragmentService.hideFragment(Scope.ENTITY, entityFragment.getId());
        pref = userService.getPreference(user.getId());
        assertNotNull(pref.getFragmentShare());
        assertFalse(pref.getFragmentShare().getHidden().isEmpty());
        assertEquals(1, pref.getFragmentShare().getHidden().size());
        assertTrue(pref.getFragmentShare().getHidden().contains(entityFragment.getId()));

        // showFragment will remove hidden entry from user preferences
        fragmentService.showFragment(Scope.ENTITY, entityFragment.getId());
        pref = userService.getPreference(user.getId());
        assertNotNull(pref.getFragmentShare());
        assertTrue(pref.getFragmentShare().getHidden().isEmpty());
        assertFalse(pref.getFragmentShare().getHidden().contains(entityFragment.getId()));
    }

    @Test
    public void showAndHideFragment_ScopeMismatch(){
        Fragment entityFragment = createEntityFragment();
        try{
            fragmentService.hideFragment(Scope.ATTRIBUTE, entityFragment.getId());
        } catch (RuntimeException e){
            assertEquals(String.format("Attribute Fragment with id %s doesn't exist", entityFragment.getId()), e.getMessage());
        }

        try{
            fragmentService.showFragment(Scope.ATTRIBUTE, entityFragment.getId());
        } catch (RuntimeException e){
            assertEquals(String.format("Attribute Fragment with id %s doesn't exist", entityFragment.getId()), e.getMessage());
        }

        Fragment attributeFragment = createAttributeFragment();
        try{
            fragmentService.hideFragment(Scope.ENTITY, attributeFragment.getId());
        } catch (RuntimeException e){
            assertEquals(String.format("Entity Fragment with id %s doesn't exist", attributeFragment.getId()), e.getMessage());
        }

        try{
            fragmentService.showFragment(Scope.ENTITY, attributeFragment.getId());
        } catch (RuntimeException e){
            assertEquals(String.format("Entity Fragment with id %s doesn't exist", attributeFragment.getId()), e.getMessage());
        }
    }


    private Fragment createEntityFragment(){
        Fragment fragment = new Fragment();
        fragment.setName("Entity Fragment");
        fragment.setScope(Scope.ENTITY);
        fragment.setDescription("Fragment Description");
        fragment.setOwnerUserId(SyncariContext.getUser().getId());
        fragment.setShared(false);
        fragment.setFragmentGraph(createEntityFragmentGraph());
        fragment.setTags(List.of(new Tag("entityTag1", true, Taggable.fragment, null),
                new Tag("entityTag2", true, Taggable.fragment, null)));
        return fragmentService.createFragment(fragment);
    }

    private Fragment createAttributeFragment(){
        Fragment fragment = new Fragment();
        fragment.setName("Attribute Fragment");
        fragment.setScope(Scope.ATTRIBUTE);
        fragment.setDescription("Fragment Description");
        fragment.setOwnerUserId(SyncariContext.getUser().getId());
        fragment.setShared(false);
        fragment.setFragmentGraph(createAttributeFragmentGraph());
        fragment.setTags(List.of(new Tag("attributeTag1", true, Taggable.fragment, null),
                new Tag("attributeTag2", true, Taggable.fragment, null)));
        return fragmentService.createFragment(fragment);
    }

    private FragmentGraph createEntityFragmentGraph(){

        FragmentGraph graph = new FragmentGraph();

        FunctionDefinition mask = functionService.findByNameAndScope("isTrue", Scope.ENTITY).get();
        FunctionCall call = mask.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        FragmentNode node1 = new FragmentNode();
        node1.setName("funcNode1").setApiName("isTrue").setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node1.setTemplateId("node1").setId("node1");
        graph.getNodes().add(node1);

        var coreEntityConfig = new CoreEntityNodeConfig().setEntityDefinition(new EntityDefinition("entity1", "entity1"));
        FragmentNode coreNode = new FragmentNode();
        coreNode.setName("coreNode").setApiName("entity1").setScope(Scope.ENTITY)
                .setConfiguration(coreEntityConfig).setMappingGraphId("graph123");
        coreNode.setTemplateId("coreNode").setId("coreNode");
        graph.getNodes().add(coreNode);

        FunctionDefinition setValue = functionService.findByNameAndScope("setValueOnEntity", Scope.ENTITY).get();
        FunctionCall call2 = setValue.withParams().setConfig(Map.of("newValue", "NewValue", "attributeDefinitionId", "attributeId"));
        var setValueConfig = new SimpleFunctionNodeConfig().setFunctionCall(call2);
        var node2 = new FragmentNode();
        node2.setName("funcNode2").setApiName("setValueOnEntity").setScope(Scope.ENTITY)
                .setConfiguration(setValueConfig).setMappingGraphId("graph123");
        node2.setTemplateId("node2").setId("node2");
        graph.getNodes().add(node2);

        var fragmentEdge1 = new FragmentEdge().setTemplateId("edge1").setDestinationStage(coreNode)
                .setSourceStage(node1).setOutput(OutputPort.any()).setInput(InputPort.any());
        var fragmentEdge2 = new FragmentEdge().setTemplateId("edge2").setDestinationStage(node2)
                .setSourceStage(coreNode).setOutput(OutputPort.any()).setInput(InputPort.any());
        graph.getEdges().add(fragmentEdge1);
        graph.getEdges().add(fragmentEdge2);

        graph.getLayouts().add(Layout.node(node1.getTemplateId(), "100", "100"));
        graph.getLayouts().add(Layout.node(coreNode.getTemplateId(), "200", "100"));
        graph.getLayouts().add(Layout.node(node2.getTemplateId(), "300", "100"));
        graph.getLayouts().add(Layout.edge(fragmentEdge1.getTemplateId(), "3", "0"));
        graph.getLayouts().add(Layout.edge(fragmentEdge2.getTemplateId(), "3", "0"));

        return graph;
    }

    private FragmentGraph createAttributeFragmentGraph(){

        FragmentGraph graph = new FragmentGraph();

        FunctionDefinition mask = functionService.findByNameAndScope("isTrue", Scope.ATTRIBUTE).get();
        FunctionCall call = mask.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        FragmentNode node1 = new FragmentNode();
        node1.setName("funcNode1").setApiName("isTrue").setScope(Scope.ATTRIBUTE)
                .setConfiguration(funcConfig).setMappingGraphId("graph123");
        node1.setTemplateId("node1").setId("node1");
        graph.getNodes().add(node1);

        var coreEntityConfig = new CoreEntityNodeConfig().setEntityDefinition(new EntityDefinition("entity1", "entity1"));
        FragmentNode coreNode = new FragmentNode();
        coreNode.setName("coreNode").setApiName("entity1").setScope(Scope.ATTRIBUTE)
                .setConfiguration(coreEntityConfig).setMappingGraphId("graph123");
        coreNode.setTemplateId("coreNode").setId("coreNode");
        graph.getNodes().add(coreNode);

        FunctionDefinition setValue = functionService.findByNameAndScope("setValue", Scope.ATTRIBUTE).get();
        FunctionCall call2 = setValue.withParams().setConfig(Map.of("newValue", "NewValue", "dataType", "string"));
        var setValueConfig = new SimpleFunctionNodeConfig().setFunctionCall(call2);
        var node2 = new FragmentNode();
        node2.setName("funcNode2").setApiName("setValueOnEntity").setScope(Scope.ATTRIBUTE)
                .setConfiguration(setValueConfig).setMappingGraphId("graph123");
        node2.setTemplateId("node2").setId("node2");
        graph.getNodes().add(node2);

        var fragmentEdge1 = new FragmentEdge().setTemplateId("edge1").setDestinationStage(coreNode)
                .setSourceStage(node1).setOutput(OutputPort.any()).setInput(InputPort.any());
        var fragmentEdge2 = new FragmentEdge().setTemplateId("edge2").setDestinationStage(node2)
                .setSourceStage(coreNode).setOutput(OutputPort.any()).setInput(InputPort.any());
        graph.getEdges().add(fragmentEdge1);
        graph.getEdges().add(fragmentEdge2);

        graph.getLayouts().add(Layout.node(node1.getTemplateId(), "100", "100"));
        graph.getLayouts().add(Layout.node(coreNode.getTemplateId(), "200", "100"));
        graph.getLayouts().add(Layout.node(node2.getTemplateId(), "300", "100"));
        graph.getLayouts().add(Layout.edge(fragmentEdge1.getTemplateId(), "3", "0"));
        graph.getLayouts().add(Layout.edge(fragmentEdge2.getTemplateId(), "3", "0"));

        return graph;
    }
}
