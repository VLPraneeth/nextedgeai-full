package com.syncari.core.service;

import com.syncari.core.SyncariContext;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.ComponentDependency;
import com.syncari.core.model.InsightsUserPreference;
import com.syncari.core.model.Tag;
import com.syncari.core.model.User;
import com.syncari.core.model.insights.DashboardVariableMapping;
import com.syncari.core.model.insights.DataCardSetting;
import com.syncari.core.model.insights.Datacard;
import com.syncari.core.model.insights.InsightsDashboard;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.repositories.DraftableRepo;
import com.syncari.core.repositories.customer.InsightsDashboardRepo;
import com.syncari.core.repositories.customer.InsightsUserPreferenceRepo;
import com.syncari.utils.I18n;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component
public class InsightsDashboardService extends DraftService<InsightsDashboard> {

    @Autowired
    InsightsDashboardRepo dashboardRepo;

    @Autowired
    TagService tagService;

    @Autowired
    UserService userService;

    @Autowired
    InsightsUserPreferenceRepo insightsUserPreferenceRepo;

    @Autowired
    ComponentDependencyService dependencyService;

    @Autowired
    InsightsSharingService insightsSharingService;

    @Override
    protected DraftableRepo<InsightsDashboard> getDraftableRepo() {
        return dashboardRepo;
    }

    @Override
    protected void processArchived(InsightsDashboard archived) {
        archived.setName(format("%s_%s_%s", archived.getName(), archived.getId(), DELETED));
    }

    public InsightsDashboard getDashboard(String dashboardId){
        return findDashboard(dashboardId).orElseThrow(() -> new NotFoundException(InsightsDashboard.class, "id", dashboardId));
    }

    public Optional<InsightsDashboard> getDashboardById(String dashboardId){
        return findDashboard(dashboardId);
    }

    public Optional<InsightsDashboard> findDashboard(String dashboardId){
        return dashboardRepo.findById(dashboardId);
    }

    public List<InsightsDashboard> getAllDashboards(){
        return dashboardRepo.findAllDashboards();
    }

    public InsightsDashboard createDashboardDraft(InsightsDashboard dashboard){
        validateCondition(StringUtils.isEmpty(dashboard.getDisplayName()), I18n.i18n("dashboard_empty_displayname"));
        validateCondition(!dashboard.isDraft(), i18n("dashboard_non_draft_upsert"), dashboard.getDisplayName());
        validateCondition(!StringUtils.isEmpty(dashboard.getId()), i18n("dashboard_with_id_already_exists"), dashboard.getId());

        // create apiName using displayName
        String apiName = StringUtils.isEmpty(dashboard.getName())
                ? TextUtil.createApiName(dashboard.getDisplayName())
                : dashboard.getName();

        Set<String> existingDashboardNames = getAllDashboards().stream().map(d -> d.getName()).collect(Collectors.toSet());
        int i = 1;
        String possibleApiName = apiName;
        while(existingDashboardNames.contains(possibleApiName)) {
            possibleApiName = apiName + "_" + i++;
        }
        apiName = possibleApiName;

        dashboard.setName(apiName);
        var saved = dashboardRepo.save(dashboard);
        // save tags
        var tagMap = saved.getTags().stream().collect(Collectors.toMap(t -> t.getName(), t -> t.getValue()));
        List<Tag> tags = tagService.assign(tagMap, Taggable.dashboard, saved.getId());
        saved.setTags(tags);

        // update component dependencies
        updateDashboardDependencies(saved);
        return dashboard;
    }

    /**
     * Approve the draft version of insights dashboard
     * @param draft
     */
    public InsightsDashboard approveDraftDashboard(InsightsDashboard draft){
        validateCondition(!draft.isDraft(), i18n("dashboard_approve_failed_no_draft"), draft.getDisplayName());
        // add more validations if any
        log.info("Approving dashboard draft {}", draft.getDisplayName());
        var approved = approveDraft(draft);

        // associate draft tags with approved dashboard if its different
        if(!approved.getId().equals(draft.getId())){
            List<Tag> draftTags = tagService.findTagsFor(Taggable.dashboard, draft.getId());
            tagService.updateTagsFor(approved.getId(), Taggable.dashboard, draftTags);
            approved.setTags(draftTags);

            // remove tags of draft entity and its attributes
            tagService.removeTagsFor(Taggable.entity, draft.getId());
        }

        // re-update the published dashboard dependencies
        updateDashboardDependencies(approved);

        // delete draft dependencies if it had parent dashboard
        if(draft.getParentId() != null) {
            dependencyService.deleteDependenciesBy(draft.getId(), ComponentType.dashboard);
            dependencyService.deleteDependenciesOn(draft.getId(), ComponentType.dashboard);
        }

        return approved;
    }

    /**
     * Discard the draft version of insights dashboard
     * @param draft
     */
    public void discardDraftDashboard(InsightsDashboard draft){
        validateCondition(!draft.isDraft(), i18n("dashboard_discard_failed_no_draft"), draft.getDisplayName());
        // add more validations if any
        log.info("Discarding dashboard draft {}", draft.getDisplayName());
        deleteDashboard(draft);
    }

    public void deleteDashboard(InsightsDashboard dashboard) {
        validateCondition(null==dashboard, i18n("dashboard_missing_failed_delete"));
        validateCondition(dashboard.isSeeded(), i18n("dashboard_delete_seeded_error"));
        log.info("Deleting dashboard  {}", dashboard.getDisplayName());
        delete(dashboard);
        // delete all tags associated with the draft
        tagService.removeTagsFor(Taggable.dashboard, dashboard.getId());

        // delete all dependencies of this dataset
        log.info("Deleting all dependencies of dashboard {}", dashboard.getDisplayName());
        dependencyService.deleteDependenciesBy(dashboard.getId(), ComponentType.dashboard);
        // delete all dependencis dataset depends on
        dependencyService.deleteDependenciesOn(dashboard.getId(), ComponentType.dashboard);
        // delete shared item records
        insightsSharingService.deleteSharedItemsByDashboardId(dashboard.getId());
    }

    @Override
    public InsightsDashboard createDraftFor(InsightsDashboard model) {
        validateCondition(model.isSeeded(), i18n("dashboard_create_draft_seeded"));
        validateCondition(!model.isApproved(), i18n("dashboard_create_draft_missing_published"));
        validateCondition(hasDraft(model), i18n("dashboard_draft_exists"), model.getDisplayName());
        // TODO: copy datacard layouts as well
        InsightsDashboard draft = super.createDraftFor(model);
        // associate/clone draft tags with approved dashboard if ids are different
        List<Tag> tags = tagService.cloneTags(model.getId(), draft.getId(), Taggable.dashboard);
        draft.setTags(tags);

        // add component dependencies
        updateDashboardDependencies(draft);
        return draft;
    }

    /**
     * update dashboard
     * @param dashboardId String
     * @param incoming InsightsDashboard
     */
    public InsightsDashboard updateDashboard(String dashboardId, InsightsDashboard incoming){
        InsightsDashboard existing = getDashboard(dashboardId);
        validateCondition(!existing.isDraft(), i18n("dashboard_update_failed_non_draft"), existing.getDisplayName());
        // add more validations if any
        log.info("Updating dashboard draft {}", existing.getDisplayName());
        existing.setDisplayName(incoming.getDisplayName());
        existing.setDescription(incoming.getDescription());
        existing.setDataCardIds(incoming.getDataCardIds());
        existing.setDataCardSettings(incoming.getDataCardSettings());

        // Remove user dashboard datacard preferences
        removeUserDashboardDatacard(dashboardId, incoming.getDataCardSettings());

        InsightsDashboard updated = dashboardRepo.save(existing);
        // save the newly added tags and delete the removed tags
        List<Tag> incomingTags = incoming.getTags();
        tagService.updateTagsFor(existing.getId(), Taggable.dashboard, incomingTags);

        // update component dependencies
        updateDashboardDependencies(updated);

        return updated;
    }

     public List<DashboardVariableMapping> getDashboardVariableMappings(String dashboardId) {
        InsightsDashboard dashboard = getDashboard(dashboardId);
        List<DashboardVariableMapping> mappings = dashboard.getDashboardVariableMappings();
        return null != mappings ? mappings : List.of();
    }

    public void setDashboardVariableMappings(String dashboardId, List<DashboardVariableMapping> dashboardVariableMapping) {
        InsightsDashboard dashboard = getDashboard(dashboardId);
        dashboard.setDashboardVariableMappings(dashboardVariableMapping);
        dashboardRepo.save(dashboard);
    }

    public void updateDashboardDependencies(InsightsDashboard dashboard) {
        List<ComponentDependency> dependencies = new ArrayList<>();
        dashboard.getDataCardSettings().forEach(dcSetting -> {
            var dep = new ComponentDependency(dashboard.getId(), ComponentType.dashboard, dcSetting.getDatacardId(), ComponentType.datacard);
            dependencies.add(dep);
        });
        dependencyService.updateDependenciesFor(dashboard.getId(), ComponentType.dashboard, dependencies);
    }

    public InsightsDashboard save(InsightsDashboard dashboard){
        return dashboardRepo.save(dashboard);
    }

    public List<InsightsDashboard> saveAll(List<InsightsDashboard> dashboards){
        return dashboardRepo.saveAll(dashboards);
    }

    public List<InsightsDashboard> getAllDashboardsContainingDatacard(String datacardId){
        return dashboardRepo.findAllDashboardByDataCardIn(datacardId);
    }

    private void removeUserDashboardDatacard(String dashboardId, List<DataCardSetting> incomingDatacards) {
        var userId = SyncariContext.getUser().getId();
        User user = userService.findUserById(userId)
                .orElseThrow(() -> new NotFoundException(User.class, "userId", userId));
        validateCondition(!user.isActive(), "User is not Active");
        var insightsUserPref =  insightsUserPreferenceRepo.findByUserId(userId)
                .orElse(new InsightsUserPreference().setUserId(userId));
        var datacardViewerPref = insightsUserPref.getDatacardViewerPreferences();
        if (null != datacardViewerPref) {
            var dashboardDatacards = datacardViewerPref.stream().filter(pref -> {
                if (pref.getDashboardId().equalsIgnoreCase(dashboardId)) {
                    // check if it still exists
                    return incomingDatacards.stream().anyMatch(card -> card.getDatacardId().equalsIgnoreCase(pref.getDatacardId()));
                }
                return true;
            }).collect(Collectors.toList());
            insightsUserPref.setDatacardViewerPreferences(dashboardDatacards);
            insightsUserPreferenceRepo.save(insightsUserPref);
        }
    }
}
