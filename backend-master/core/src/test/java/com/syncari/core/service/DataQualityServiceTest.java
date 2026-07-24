package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.Features;
import com.syncari.core.dfiv2.DFIConstants;
import com.syncari.core.model.DataQualityRule;
import com.syncari.core.model.Feature;
import com.syncari.core.model.misc.FeatureStatus;
import com.syncari.core.repositories.customer.DataQualityRuleRepo;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@Slf4j
public class DataQualityServiceTest extends AbstractSyncariTest {

    @Autowired
    DataQualityService dataQualityService;

    @MockBean
    DataQualityRuleRepo dataQualityRuleRepo;

    @MockBean
    FeatureService featureService;

    @Override
    public void setUp() {
        super.setUp();
        Feature dfiFeature = new Feature();
        dfiFeature.setStatus(FeatureStatus.active);
        when(featureService.isEnabled(Features.DfiV2Provisioning)).thenReturn(true);
        when(featureService.getOrCreateFeatureByName(Features.DfiV2Provisioning)).thenReturn(dfiFeature);
    }

    @Test
    public void testGetRecordRules() {
        String graphId = ObjectId.get().toHexString();
        String entityId = ObjectId.get().toHexString();

        DataQualityRule validRecordRule = createRule("Valid Record Rule", DFIConstants.SCOPE_TYPE_SYSTEM,
                Arrays.asList(DFIConstants.RECORD_SCOPE), false, graphId, entityId);
        DataQualityRule attributeRule = createRule("Attribute Rule", DFIConstants.ATTRIBUTE_SCOPE,
                Arrays.asList("attr1"), false, graphId, entityId);
        DataQualityRule allFieldsRule = createRule("All Fields Rule", DFIConstants.SCOPE_TYPE_SYSTEM,
                Arrays.asList(DFIConstants.ALL_FIELDS_VALUE), false, graphId, entityId);
        DataQualityRule deletedRecordRule = createRule("Deleted Record Rule", DFIConstants.SCOPE_TYPE_SYSTEM,
                Arrays.asList(DFIConstants.RECORD_SCOPE), true, graphId, entityId);
        DataQualityRule nullScopeRule = createRule("Null Scope Rule", DFIConstants.SCOPE_TYPE_SYSTEM,
                null, false, graphId, entityId);

        List<DataQualityRule> allRules = Arrays.asList(validRecordRule, attributeRule, allFieldsRule,
                deletedRecordRule, nullScopeRule);

        List<DataQualityRule> result = dataQualityService.getRecordRules(allRules);

        assertEquals(1, result.size());
        assertEquals(validRecordRule.getId(), result.get(0).getId());
        assertEquals(DFIConstants.SCOPE_TYPE_SYSTEM, result.get(0).getScopeType());
        assertTrue(result.get(0).getScope().contains(DFIConstants.RECORD_SCOPE));
        assertFalse(result.get(0).getIsDeleted());

        List<DataQualityRule> emptyResult = dataQualityService.getRecordRules(Collections.emptyList());
        assertNotNull(emptyResult);
        assertTrue(emptyResult.isEmpty());
    }

    @Test
    public void testGetRulesByAttribute() {
        String graphId = ObjectId.get().toHexString();
        String entityId = ObjectId.get().toHexString();
        String attrId1 = ObjectId.get().toHexString();
        String attrId2 = ObjectId.get().toHexString();
        String attrId3 = ObjectId.get().toHexString();

        DataQualityRule allFieldsRule = createRule("All Fields Rule", DFIConstants.SCOPE_TYPE_SYSTEM,
                Arrays.asList(DFIConstants.ALL_FIELDS_VALUE), false, graphId, entityId);
        DataQualityRule attr1Rule = createRule("Attr1 Rule", DFIConstants.ATTRIBUTE_SCOPE,
                Arrays.asList(attrId1), false, graphId, entityId);
        DataQualityRule multiAttrRule = createRule("Multi Attr Rule", DFIConstants.ATTRIBUTE_SCOPE,
                Arrays.asList(attrId1, attrId2), false, graphId, entityId);
        DataQualityRule attr3Rule = createRule("Attr3 Rule", DFIConstants.ATTRIBUTE_SCOPE,
                Arrays.asList(attrId3), false, graphId, entityId);
        DataQualityRule recordRule = createRule("Record Rule", DFIConstants.SCOPE_TYPE_SYSTEM,
                Arrays.asList(DFIConstants.RECORD_SCOPE), false, graphId, entityId);
        DataQualityRule deletedAttrRule = createRule("Deleted Attr Rule", DFIConstants.ATTRIBUTE_SCOPE,
                Arrays.asList(attrId1), true, graphId, entityId);
        DataQualityRule nullScopeRule = createRule("Null Scope Rule", DFIConstants.ATTRIBUTE_SCOPE,
                null, false, graphId, entityId);

        List<DataQualityRule> allRules = Arrays.asList(allFieldsRule, attr1Rule, multiAttrRule,
                attr3Rule, recordRule, deletedAttrRule, nullScopeRule);

        List<DataQualityRule> result1 = dataQualityService.getRulesByAttribute(attrId1, allRules);
        assertEquals(3, result1.size());
        assertTrue(result1.stream().anyMatch(r -> r.getName().equals("All Fields Rule")));
        assertTrue(result1.stream().anyMatch(r -> r.getName().equals("Attr1 Rule")));
        assertTrue(result1.stream().anyMatch(r -> r.getName().equals("Multi Attr Rule")));

        List<DataQualityRule> result2 = dataQualityService.getRulesByAttribute(attrId2, allRules);
        assertEquals(2, result2.size());
        assertTrue(result2.stream().anyMatch(r -> r.getName().equals("All Fields Rule")));
        assertTrue(result2.stream().anyMatch(r -> r.getName().equals("Multi Attr Rule")));

        List<DataQualityRule> result3 = dataQualityService.getRulesByAttribute(attrId3, allRules);
        assertEquals(2, result3.size());
        assertTrue(result3.stream().anyMatch(r -> r.getName().equals("All Fields Rule")));
        assertTrue(result3.stream().anyMatch(r -> r.getName().equals("Attr3 Rule")));

        List<DataQualityRule> resultUnknown = dataQualityService.getRulesByAttribute("unknownAttr", allRules);
        assertEquals(1, resultUnknown.size());
        assertEquals("All Fields Rule", resultUnknown.get(0).getName());

        List<DataQualityRule> emptyResult = dataQualityService.getRulesByAttribute(attrId1, Collections.emptyList());
        assertNotNull(emptyResult);
        assertTrue(emptyResult.isEmpty());
    }

    private DataQualityRule createRule(String name, String scopeType, List<String> scope,
                                       boolean isDeleted, String graphId, String entityId) {
        DataQualityRule rule = new DataQualityRule();
        rule.setId(ObjectId.get().toHexString());
        rule.setName(name);
        rule.setScopeType(scopeType);
        rule.setScope(scope);
        rule.setIsDeleted(isDeleted);
        rule.setMappingGraphId(graphId);
        rule.setEntityId(entityId);
        rule.setCategory("cat1");
        rule.setPolicy("policy1");
        rule.setRuleConfig(new HashMap<>());
        return rule;
    }
}
