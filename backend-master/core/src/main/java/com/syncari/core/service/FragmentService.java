package com.syncari.core.service;

import com.syncari.core.SyncariContext;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.model.NodeConfiguration;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.Fragment;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.SharedItem;
import com.syncari.core.model.Tag;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.fragment.FragmentNode;
import com.syncari.core.model.UserPreference;
import com.syncari.core.model.misc.fragment.FragmentSharePreference;
import com.syncari.core.model.misc.Sharable;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import com.syncari.core.repositories.customer.FragmentRepo;
import com.syncari.core.repositories.syncari.SharedItemRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class FragmentService implements SharingService {

    @Autowired
    FragmentRepo fragmentRepo;

    @Autowired
    TagService tagService;

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    SharedItemRepo sharedItemRepo;

    @Autowired
    FunctionService functionService;

    @Autowired
    ActionService actionDefinitionRepo;

    @Autowired
    UserService userService;


    public List<Fragment> listEntityFragments(){
        return fragmentRepo.findAllByScope(Scope.ENTITY);
    }

    public List<Fragment> listFieldFragments(){
        return fragmentRepo.findAllByScope(Scope.ATTRIBUTE);
    }

    public Fragment getFragment(Scope scope, String fragmentId){
        Fragment fragment = getFragment(fragmentId);
        if(!scope.equals(fragment.getScope())){
            throw new RuntimeException(String.format(i18n("fragment_not_exist"),
                    StringUtils.capitalize(scope.name().toLowerCase()), fragmentId));
        }
        return fragment;
    }

    private Fragment getFragment(String fragmentId){
        Fragment fragment = fragmentRepo.findById(fragmentId)
                .orElseThrow(() -> new NotFoundException(Fragment.class, "Id", fragmentId));
        fragment.setTags(tagService.findTagsFor(Taggable.fragment, fragmentId));
        return fragment;
    }

    public Fragment createFragment(Fragment fragment){
        fragment.validate();
        Optional<Fragment> dupNameFragment = fragmentRepo.findByName(fragment.getName());
        validateCondition(dupNameFragment.isPresent(), i18n("fragment_duplicate_name_error", fragment.getName()));
        Fragment saved = fragmentRepo.save(fragment);
        log.info("New {} fragment is created with id {}", fragment.getScope().name().toLowerCase(), saved.getId());
        Map<String, Object> tagMap = fragment.getTags().stream().collect(Collectors.toMap(t -> t.getName(), t -> t.getValue()));
        List<Tag> tags = tagService.assign(tagMap, Taggable.fragment, saved.getId());
        fragment.setTags(tags);

        return fragment;
    }

    public Fragment updateFragment(Fragment fragment){
        fragment.validate();
        validateCondition(fragment.isShared(), i18n("shared_fragment_update_error", fragment.getName()));
        Optional<Fragment> dupNameFragment = fragmentRepo.findByName(fragment.getName());
        validateCondition(dupNameFragment.isPresent() && !dupNameFragment.get().getId().equals(fragment.getId()),
                i18n("fragment_duplicate_name_error", fragment.getName()));
        Fragment existing = getFragment(fragment.getScope(), fragment.getId());
        Fragment saved = fragmentRepo.save(fragment);
        log.info("Updated {} fragment with id {}", fragment.getScope().name().toLowerCase(), saved.getId());
        tagService.updateTagsFor(existing.getId(), Taggable.fragment, fragment.getTags());
        return saved;
    }

    public void deleteFragment(Scope scope, String fragmentId){
        Fragment existing = getFragment(scope, fragmentId);
        // remove the sharing info from sharedItem record
        if(existing.isShared()){
            deleteSharedFragment(existing);
        } else {
            deletedSourceFragment(existing);
        }
    }

    private void deletedSourceFragment(Fragment fragment){
        // Not allow source fragment deletion if its shared with other instances
        Optional<SharedItem> sharedItem = sharedItemRepo.findSharedItemBySourceIdAndItemType(fragment.getId(), Sharable.FRAGMENT);
        sharedItem.ifPresent(item -> {
            if(!item.getSharingInstances().isEmpty()) {
                throw new RuntimeException(i18n("fragment_shared_delete_error"));
            }else{
                // since the fragment is being deleted remove the corresponding sharedItem record
                sharedItemRepo.delete(item);
                log.info("SharedItem with id {} for fragment {} is deleted", item.getId(), fragment.getId());
            }
        });

        fragmentRepo.delete(fragment);
        log.info("{} fragment with id {} is deleted", fragment.getScope().name().toLowerCase(), fragment.getId());

        tagService.removeTagsFor(Taggable.fragment, fragment.getId());

    }

    private void deleteSharedFragment(Fragment fragment){
        // if shared fragment is deleted, remove the entry from sharedItem
        Optional<SharedItem> sharedItem = sharedItemRepo.findById(fragment.getSharedItemId());
        sharedItem.ifPresent(item -> {
            item.getSharingInstances().remove(SyncariContext.getSyncariId());
            sharedItemRepo.save(item);
            log.info("Sharing info of shared fragment {} from instance {} is removed from sharedItem {}",
                    fragment.getId(), SyncariContext.getSyncariId(), item.getId());
        });

        fragmentRepo.delete(fragment);
        log.info("Shared {} fragment with id {} is deleted", fragment.getScope().name().toLowerCase(), fragment.getId());

        tagService.removeTagsFor(Taggable.fragment, fragment.getId());
    }

    public Set<String> getSharingInstances(String fragmentId){
        Optional<SharedItem> sharedFragment = sharedItemRepo.findSharedItemBySourceIdAndItemType(fragmentId, Sharable.FRAGMENT);

        if(sharedFragment.isPresent()) {
            return sharedFragment.get().getSharingInstances().keySet();
        } else{
            return Collections.emptySet();
        }
    }

    @Override
    public void share(String sourceId, List<String> instances) {
        Fragment source = getFragment(sourceId);

        validateCondition(source.isShared(), i18n("shared_fragment_sharing_error"));

        // check already shared instances and then share with newly added ones
        Optional<SharedItem> existingSharedFragment = sharedItemRepo.findSharedItemBySourceIdAndItemType(sourceId, Sharable.FRAGMENT);
        SharedItem sharedFragment = existingSharedFragment.isPresent() ? existingSharedFragment.get()
                : sharedItemRepo.save(new SharedItem().setItemType(Sharable.FRAGMENT).setSourceInstance(SyncariContext.getSyncariId())
                .setSourceId(sourceId).setOwnerUserId(SyncariContext.getUser().getId()).setSharingInstances(new HashMap<>()));

        Set<String> userInstances = SyncariContext.getUser().getAvailableInstances();
        // share with newly added instances
        List<String> newSharedInstances = instances.stream().filter(i -> !sharedFragment.getSharingInstances().containsKey(i))
                .collect(Collectors.toList());
        newSharedInstances.forEach(instance -> {
            validateCondition(!userInstances.contains(instance), i18n("fragment_sharing_instance_permission_error", instance));
            log.info("Sharing Fragment Id {} with instance {}", source.getId(), instance);
            shareWithInstance(sharedFragment, instance, source);
        });

        // unshare from instances - remove it from sharedInstance and also the corresponding record from sharedItem
        Set<String> sharingInstances = sharedFragment.getSharingInstances().keySet();
        List<String> unsharedInstances = sharingInstances.stream().filter(i -> !instances.contains(i))
                .collect(Collectors.toList());
        unsharedInstances.forEach(instance -> {
            validateCondition(!userInstances.contains(instance), i18n("fragment_sharing_instance_permission_error", instance));
            log.info("Unsharing Fragment Id {} from instance {}", source.getId(), instance);
            unshareFromInstance(sharedFragment, instance, source);
        });
        sharedItemRepo.save(sharedFragment);
    }

    private void shareWithInstance(SharedItem sharedItem, String syncariId, Fragment source){
        Organization org = subscriptionService.getOrgBySyncariId(syncariId);
        Instance instance = org.getInstance(syncariId)
                .orElseThrow(() -> new NotFoundException(Instance.class, "syncariId", syncariId));
        User owner = SyncariContext.getUser();
        SyncariContext.runWithContext(org, instance, owner, () -> {
            Fragment shared = source.makeCopy();
            updateNodeConfig(shared);
            shared.setShared(true);
            shared.setSharedItemId(sharedItem.getId());
            shared = createFragment(shared);

            // create a corresponding record in sharedItem
            sharedItem.getSharingInstances().put(syncariId, shared.getId());
        });
    }

    private void unshareFromInstance(SharedItem sharedItem, String syncariId, Fragment source){
        Organization org = subscriptionService.getOrgBySyncariId(syncariId);
        Instance instance = org.getInstance(syncariId)
                .orElseThrow(() -> new NotFoundException(Instance.class, "syncariId", syncariId));
        User owner = SyncariContext.getUser();
        String sharedFragmentId = sharedItem.getSharingInstances().get(syncariId);
        SyncariContext.runWithContext(org, instance, owner, () -> {
            log.info("Deleting Fragment with id {} from instance {}", sharedFragmentId, syncariId);
            deleteFragment(source.getScope(), sharedFragmentId);

            // remove corresponding record from sharedItem
            sharedItem.getSharingInstances().remove(syncariId);
        });

    }

    private void updateNodeConfig(Fragment shared) {
        shared.getFragmentGraph().getNodes().forEach(node -> {
            node.setConfiguration(getNodeConfig(node));
        });
    }

    private NodeConfiguration getNodeConfig(FragmentNode node){
        switch (node.getType()) {
            case FUNCTION:
                SimpleFunctionNodeConfig existingFunctionConfig = node.getTypedConfiguration();
                var configMap = new HashMap<String, Object>();
                configMap.putAll(existingFunctionConfig.getConfigMap());

                SimpleFunctionNodeConfig functionConfig = new SimpleFunctionNodeConfig();
                FunctionDefinition functionDefinition = functionService.findByNameAndScope(node.getApiName(), node.getScope())
                        .orElseThrow(() -> new NotFoundException(FunctionDefinition.class, "name_and_scope", node.getApiName()+"_"+node.getScope().name()));
                FunctionCall functionCall = new FunctionCall().setFunctionDefinition(functionDefinition);
                configMap.put("configId", functionDefinition.getId());
                configMap.put("definition", functionDefinition.getId());
                functionCall.setConfig(configMap);

                functionConfig.setFunctionCall(functionCall);
                return functionConfig;

            case ACTION:
                GenericActionConfig existingActionConfig = node.getTypedConfiguration();
                ActionDefinition actionDefinition = actionDefinitionRepo.findByName(node.getApiName())
                        .orElseThrow(() -> new NotFoundException(format(i18n("action_not_found"), node.getApiName(), SyncariContext.getInstance().getDisplayName())));
                var actionConfigMap = new HashMap<String, Object>();
                actionConfigMap.putAll(existingActionConfig.getConfigMap());

                GenericActionConfig actionConfig = new GenericActionConfig();
                actionConfig.setName(actionDefinition.getName());
                actionConfigMap.put("configId", actionDefinition.getId());
                actionConfigMap.put("definition", actionDefinition.getId());
                actionConfig.setConfigMap(actionConfigMap);
                return actionConfig;

            default:
                return node.getConfiguration();
        }
    }

    @Override
    public void unshare(String sourceId, List<String> instances) {
        Fragment source = getFragment(sourceId);
        Set<String> userInstances = SyncariContext.getUser().getAvailableInstances();
        Optional<SharedItem> existingSharedFragment = sharedItemRepo.findSharedItemBySourceIdAndItemType(sourceId, Sharable.FRAGMENT);
        validateCondition(existingSharedFragment.isEmpty(), i18n("fragment_not_shared_error"));
        var sharedFragment = existingSharedFragment.get();
        instances.forEach(instance -> {
            validateCondition(!userInstances.contains(instance), i18n("fragment_sharing_instance_permission_error", instance));
            validateCondition(!sharedFragment.getSharingInstances().containsKey(instance),
                    i18n("fragment_not_shared_for_unsharing_error", instance));
            log.info("Unsharing Fragment Id {} from instance {}", source.getId(), instance);
            unshareFromInstance(sharedFragment, instance, source);
        });
        sharedItemRepo.save(sharedFragment);
    }

    private Fragment shareValidation(Scope scope, String fragmentId) {
        Fragment fragment = getFragment(scope, fragmentId);
        if (!fragment.isShared()) {
            throw new RuntimeException(String.format(i18n("non_shared_fragment"),
                    StringUtils.capitalize(scope.name().toLowerCase()), fragmentId));
        }
        return fragment;
    }

    public Fragment hideFragment(Scope scope, String fragmentId) {
        shareValidation(scope, fragmentId);
        UserPreference preference = userService.getPreference(SyncariContext.getUser().getId());
        FragmentSharePreference fragmentPref = preference.getFragmentShare();
        if (fragmentPref == null) {
            fragmentPref = new FragmentSharePreference();
            fragmentPref.getHidden().add(fragmentId);
        } else {
            if (!fragmentPref.getHidden().contains(fragmentId)) {
                fragmentPref.getHidden().add(fragmentId);
            } else {
                // Just log the error if its already hidden
                log.warn(String.format("Fragment %s is already hidden", fragmentId));
            }
        }
        userService.updateFragmentSharePreference(SyncariContext.getUser().getId(), fragmentPref);
        return getFragment(fragmentId);
    }

    public Fragment showFragment(Scope scope, String fragmentId) {
        shareValidation(scope, fragmentId);
        UserPreference preference = userService.getPreference(SyncariContext.getUser().getId());
        FragmentSharePreference fragmentPref = preference.getFragmentShare();
        if(fragmentPref == null || !fragmentPref.getHidden().contains(fragmentId)){
            log.warn(String.format("Fragment %s is not hidden", fragmentId));
        } else{
            fragmentPref.getHidden().remove(fragmentId);
        }
        userService.updateFragmentSharePreference(SyncariContext.getUser().getId(), fragmentPref);
        return getFragment(fragmentId);
    }}
