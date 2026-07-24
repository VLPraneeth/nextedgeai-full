package com.syncari.core.dfi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static com.syncari.utils.I18n.i18n;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.ConditionAssignment;
import com.syncari.core.model.DfiRuleAssignment;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.RuleAssignment;
import com.syncari.core.model.RuleDefinition.Impact;
import com.syncari.core.model.RuleDefinition.RuleType;
import com.syncari.core.repositories.customer.DfiRuleAssignmentRepo;
import com.syncari.core.service.DfiRuleAssignmentService;
import com.syncari.core.service.SchemaService;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DfiRuleAssignmentServiceTest extends AbstractSyncariTest {
    @Autowired
    DfiRuleAssignmentService service;
    @Autowired
    SchemaService schemaService;

    @Autowired
    DfiRuleAssignmentRepo dfiRuleAssignmentRepo;

    EntityDefinition userEntity;

    @Override
    public void setUp() {
        super.setUp();
        if (userEntity == null) {
            userEntity = schemaService.getSyncariEntityByName("user").get();
        }
    }

    @Override
    public void tearDown() {
        dfiRuleAssignmentRepo.deleteByEntityId(userEntity.getId());
        super.tearDown();
    }


    private DfiRuleAssignment getAccountEntityDraft() {
        return new DfiRuleAssignment().setEntityId(userEntity.getId()).setEntityApiName("user");
    }

    @Test
    public void basic() {
        Optional<DfiRuleAssignment> draft = service.findDraft(userEntity.getId());
        assertFalse(draft.isPresent());
        Optional<DfiRuleAssignment> published = service.findPublished(userEntity.getId());
        assertFalse(published.isPresent());
    }

    @Test
    public void findOrCreateDraft() {
        DfiRuleAssignment draft = service.findOrCreateDraft(userEntity.getId());
        service.saveDraft(draft);
        DfiRuleAssignment saved = service.findOrCreateDraft(userEntity.getId());
        assertNotNull(saved);
        service.saveDraft(saved);
        Optional<DfiRuleAssignment> oSaved = service.findDraft(userEntity.getId());
        assertTrue(oSaved.isPresent());
        assertEquals(1, dfiRuleAssignmentRepo.findByEntityId(userEntity.getId()).size());
    }

    @Test
    public void saveDraft() {
        DfiRuleAssignment draft = service.findOrCreateDraft(userEntity.getId());
        service.saveDraft(draft);
        Optional<DfiRuleAssignment> saved = service.findDraft(userEntity.getId());
        assertTrue(saved.isPresent());
        service.saveDraft(saved.get());
        saved = service.findDraft(userEntity.getId());
        assertTrue(saved.isPresent());
        assertEquals(1, dfiRuleAssignmentRepo.findByEntityId(userEntity.getId()).size());

        // Draft by same entity keeps overwriting the latest draft.
        draft = service.findOrCreateDraft(userEntity.getId());
        service.saveDraft(draft);
        assertEquals(1, dfiRuleAssignmentRepo.findByEntityId(userEntity.getId()).size());
    }

    @Test
    public void validate() {
        DfiRuleAssignment draft = service.findOrCreateDraft(userEntity.getId());

        try {
            draft.setRules(Set.of(getUserFirstNameRuleAssignment(userEntity).setName("")));
            service.isValid(draft);
        } catch (Exception e) {
            assertEquals(i18n("name_is_required"), e.getMessage());
        }

        try {
            draft.setRules(Set.of(getUserFirstNameRuleAssignment(userEntity).setConditions(Set.of())));
            service.isValid(draft);
        } catch (Exception e) {
            assertEquals(i18n("one_condition_required"), e.getMessage());
        }

        try {
            ConditionAssignment condition = new ConditionAssignment().setName(RuleConstants.WITHIN_NUMERIC_RANGE).setConditionMatches(true)
                .setImpact(Impact.HIGH).setType(RuleType.INT_RANGE).setConditionValues(List.of("ABC", "XYZ"));
            draft.setRules(Set.of(getUserFirstNameRuleAssignment2(userEntity).setConditions(Set.of(condition))));
            service.isValid(draft);
        } catch (Exception e) {
            assertEquals(String.format(i18n("invalid_long_condition"), RuleConstants.WITHIN_NUMERIC_RANGE, List.of("ABC", "XYZ")), e.getMessage());
        }

        try {
            ConditionAssignment condition = new ConditionAssignment().setName(RuleConstants.WITHIN_NUMERIC_RANGE).setConditionMatches(true)
                .setImpact(Impact.HIGH).setType(RuleType.INT_RANGE).setConditionValues(List.of("10", "1"));
            draft.setRules(Set.of(getUserFirstNameRuleAssignment2(userEntity).setConditions(Set.of(condition))));
            service.isValid(draft);
        } catch (Exception e) {
            assertEquals(String.format(i18n("invalid_long_condition"), RuleConstants.WITHIN_NUMERIC_RANGE, List.of("10", "1")), e.getMessage());
        }

        // Positive case, is valid.
        ConditionAssignment conditionV = new ConditionAssignment().setName(RuleConstants.WITHIN_NUMERIC_RANGE).setConditionMatches(true)
            .setImpact(Impact.HIGH).setType(RuleType.INT_RANGE).setConditionValues(List.of("1", "10"));
        draft.setRules(Set.of(getUserFirstNameRuleAssignment2(userEntity).setConditions(Set.of(conditionV))));
        service.isValid(draft);

        ConditionAssignment conditionV2 = new ConditionAssignment().setName(RuleConstants.WITHIN_NUMERIC_RANGE).setConditionMatches(true)
            .setImpact(Impact.HIGH).setType(RuleType.INT_RANGE).setConditionValues(List.of("1.234", "10.999"));
        draft.setRules(Set.of(getUserFirstNameRuleAssignment2(userEntity).setConditions(Set.of(conditionV2))));
        service.isValid(draft);

        try {
            ConditionAssignment condition = new ConditionAssignment().setName(RuleConstants.WITHIN_DATE_RANGE).setConditionMatches(true)
                .setImpact(Impact.HIGH).setType(RuleType.DATE_RANGE).setConditionValues(List.of("ABC", "XYZ"));
            draft.setRules(Set.of(getUserFirstNameRuleAssignment2(userEntity).setConditions(Set.of(condition))));
            service.isValid(draft);
        } catch (Exception e) {
            assertEquals(String.format(i18n("invalid_date_condition"), RuleConstants.WITHIN_DATE_RANGE, List.of("ABC", "XYZ")), e.getMessage());
        }
    }

    @Test
    public void publish() {
        DfiRuleAssignment draft = service.findOrCreateDraft(userEntity.getId());
        service.saveDraft(draft);
        Optional<DfiRuleAssignment> saved = service.findDraft(userEntity.getId());
        assertTrue(saved.isPresent());
        service.publish(saved.get());
        saved = service.findDraft(userEntity.getId());
        assertTrue(saved.isPresent());
        saved = service.findPublished(userEntity.getId());
        assertTrue(saved.isPresent());
        assertEquals(2, dfiRuleAssignmentRepo.findByEntityId(userEntity.getId()).size());

        // publish by same entity keeps overwriting the published version.
        DfiRuleAssignment drf = getAccountEntityDraft();
        service.publish(drf);
        assertEquals(3, dfiRuleAssignmentRepo.findByEntityId(userEntity.getId()).size());

        // Recreate a new draft and publish.
        draft = service.findOrCreateDraft(userEntity.getId());
        draft = service.saveDraft(draft);
        assertEquals(3, dfiRuleAssignmentRepo.findByEntityId(userEntity.getId()).size());
        // A new publish will overwrite the existing published one.
        service.publish(draft);
        assertEquals(4, dfiRuleAssignmentRepo.findByEntityId(userEntity.getId()).size());
        // Find exactly one published, one draft and two archived (two drafts were approved and archived).
        List<DfiRuleAssignment> dfiRuleAssignments = dfiRuleAssignmentRepo.findByEntityId(userEntity.getId());
        Optional<DfiRuleAssignment> pub = dfiRuleAssignments.stream().filter(x -> x.isApproved()).findFirst();
        assertTrue(pub.isPresent());
        List<DfiRuleAssignment> archived = dfiRuleAssignments.stream().filter(x -> x.isArchived()).collect(Collectors.toList());
        assertEquals(2, archived.size());
        Optional<DfiRuleAssignment> drft = dfiRuleAssignments.stream().filter(x -> x.isDraft()).findFirst();
        assertTrue(drft.isPresent());

        // Reset
        dfiRuleAssignmentRepo.deleteByEntityId(userEntity.getId());
        assertEquals(0, dfiRuleAssignmentRepo.findByEntityId(userEntity.getId()).size());
    }

    @Test
    public void deleteDraft() {
        DfiRuleAssignment draft = service.findOrCreateDraft(userEntity.getId());
        draft = service.saveDraft(draft);
        service.publish(draft);
        assertEquals(2, dfiRuleAssignmentRepo.findByEntityId(userEntity.getId()).size());
        service.deleteDraft(draft);
        // A new draft is created from published upon delete/discard of a draft.
        assertEquals(2, dfiRuleAssignmentRepo.findByEntityId(userEntity.getId()).size());
    }

    @Test
    public void basicTest() {
        DfiRuleAssignment draft = service.findOrCreateDraft(userEntity.getId());
        draft.setRules(Set.of(getUserFirstNameRuleAssignment(userEntity), getUserFirstNameRuleAssignment2(userEntity)));
        draft = service.saveDraft(draft);
        service.publish(draft);
        List<DfiRuleAssignment> dfiRuleAssignments = dfiRuleAssignmentRepo.findByEntityId(userEntity.getId());
        Optional<DfiRuleAssignment> pub = dfiRuleAssignments.stream().filter(x -> x.isApproved()).findFirst();
        assertTrue(pub.isPresent());
        Optional<DfiRuleAssignment> drft = dfiRuleAssignments.stream().filter(x -> x.isDraft()).findFirst();
        assertTrue(drft.isPresent());
        assertEquals(2, pub.get().getRules().size());
        assertEquals(2, drft.get().getRules().size());
        // TODO: More deep level assertions, like conditions etc.
        //assertEquals()
        Map<String, List<RuleAssignment>> rulesByField = service.getRulesForEntityByField(userEntity.getApiName());
        // one field (same field), two rules are expected.
        assertEquals(1, rulesByField.keySet().size());
        assertEquals(2, rulesByField.get("FirstName").size());
    }

    public static RuleAssignment getWithinNumericRangeRule(EntityDefinition userEntity) {
        AttributeDefinition firstName = userEntity.getFieldByName("FirstName");
        ConditionAssignment condition = new ConditionAssignment().setName(RuleConstants.IS_NOT_EMPTY).setConditionMatches(true)
            .setImpact(Impact.HIGH).setType(RuleType.BOOLEAN);
        RuleAssignment rule = new RuleAssignment().setName("getWithinNumericRangeRule").setEntityApiName(userEntity.getApiName())
            .setSelectedFields(Set.of(firstName.getId()))
            .setConditions(Set.of(condition));
        return rule;
    }

    public static RuleAssignment getUserFirstNameRuleAssignment(EntityDefinition userEntity) {
        AttributeDefinition firstName = userEntity.getFieldByName("FirstName");
        ConditionAssignment condition = new ConditionAssignment().setName(RuleConstants.IS_NOT_EMPTY).setConditionMatches(true)
            .setImpact(Impact.HIGH).setType(RuleType.BOOLEAN);
        RuleAssignment rule = new RuleAssignment().setName("getUserFirstNameRuleAssignment").setEntityApiName(userEntity.getApiName())
            .setSelectedFields(Set.of(firstName.getId()))
            .setConditions(Set.of(condition));
        return rule;
    }

    public static RuleAssignment getUserFirstNameRuleAssignment2(EntityDefinition userEntity) {
        AttributeDefinition firstName = userEntity.getFieldByName("FirstName");
        ConditionAssignment condition = new ConditionAssignment().setName(RuleConstants.WITHIN_LENGTH_RANGE).setConditionMatches(true)
            .setImpact(Impact.HIGH).setType(RuleType.BOOLEAN).setConditionValues(List.of("5", "10"));
        RuleAssignment rule = new RuleAssignment().setName("getUserFirstNameRuleAssignment2").setEntityApiName(userEntity.getApiName())
            .setSelectedFields(Set.of(firstName.getId()))
            .setConditions(Set.of(condition));
        return rule;
    }

    public static RuleAssignment getUserLastNameRuleAssignment(EntityDefinition userEntity) {
        AttributeDefinition firstName = userEntity.getFieldByName("LastName");
        ConditionAssignment condition = new ConditionAssignment().setName(RuleConstants.IS_NOT_EMPTY).setConditionMatches(true)
            .setImpact(Impact.HIGH).setType(RuleType.BOOLEAN);
        RuleAssignment rule = new RuleAssignment().setName("getUserLastNameRuleAssignment").setEntityApiName(userEntity.getApiName())
            .setSelectedFields(Set.of(firstName.getId()))
            .setConditions(Set.of(condition));
        return rule;
    }
}
