package com.syncari.core.model;

import com.syncari.core.pipeline.expression.Equal;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.LiteralExpression;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.pipeline.expression.dedupe.FirstMatchingValueExpression;
import com.syncari.core.pipeline.expression.dedupe.LeastFrequentValueExpression;
import com.syncari.core.pipeline.expression.dedupe.OldestUpdatedValueExpression;
import com.syncari.utils.KeyValue;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class AdvancedDedupeConfigTest {
    @Test
    public void translateFromLegacyMergePolicy(){
        AdvancedDedupeConfig advancedDedupeConfig = new AdvancedDedupeConfig().setFieldLevelOverrides(
                KeyValue.of("name","fieldLevelOverrides","compositeValues", List.of(
                        KeyValue.of("field",KeyValue.of("name","field","value","5f5580ac61b01fe6a9fbac05"),
                                "fieldMergePolicy",KeyValue.of("name","fieldMergePolicy","value","LEAST_FREQUENT"),
                                "fieldOverridePolicy",KeyValue.of("name","fieldOverridePolicy","value","ALWAYS")
                        ),
                        KeyValue.of("field",KeyValue.of("name","field","value","5f5580ac61b01fe6a9fbac06"),
                                "fieldMergePolicy",KeyValue.of("name","fieldMergePolicy","value","EARLIEST_WITH_VALUE"),
                                "fieldOverridePolicy",KeyValue.of("name","fieldOverridePolicy","value","WHEN_BLANK")
                        )

                ))
        );
        List<FieldMergePolicy> fieldMergePolicies = advancedDedupeConfig.getFieldMergePolicies();
        assertEquals(2,fieldMergePolicies.size());
        assertEquals(WinnerOverridePolicy.ALWAYS,fieldMergePolicies.get(0).getOverridePolicy());
        assertEquals("least_frequent", ((LeastFrequentValueExpression)fieldMergePolicies.get(0).getExpresson()).getName());
        assertEquals(WinnerOverridePolicy.WHEN_BLANK,fieldMergePolicies.get(1).getOverridePolicy());
        assertEquals( FieldLevelWinnerSelection.OLDEST_UPDATED_WITH_VALUE.name().toLowerCase(), ((OldestUpdatedValueExpression)fieldMergePolicies.get(1).getExpresson()).getName());
    }

    @Test
    public void translateFromNewMergePolicy(){
        AdvancedDedupeConfig advancedDedupeConfig = new AdvancedDedupeConfig().setFieldMergePolicies(
                KeyValue.of("name","fieldMergePolicies","compositeValues", List.of(
                        KeyValue.of("fieldMergePredicate",KeyValue.of("name","fieldMergePredicate","value",KeyValue.of("predicates",
                                List.of(

                                        KeyValue.of(
                                            "left",KeyValue.of("datatype","picklist","picklistGroup","Fields","label","Credit Line :Account","type","variable","value","5f5580ac61b01fe6a9fbac02"),
                                            "operator","eq",
                                            "right",KeyValue.of("value","300","type","literal"),
                                            "name","fieldMergePredicate"
                                    )
                                ),"operator","AND"
                                    )),
                                    "fieldOverridePolicy",KeyValue.of("name","fieldOverridePolicy","value","WHEN_BLANK")
                            ),
                        KeyValue.of("fieldMergePredicate",KeyValue.of("name","fieldMergePredicate","value",KeyValue.of("predicates",
                                List.of(

                                        KeyValue.of(
                                                "left",KeyValue.of("datatype","picklist","picklistGroup","Fields","label","Debit Line :Account","type","variable","value","5f5580ac61b01fe6a9fbac04"),
                                                "operator","firstMatchingValue",
                                                "right",KeyValue.of("value",List.of("1","2","3"),"type","literal"),
                                                "name","fieldMergePredicate"
                                        )
                                ),"operator","AND"
                                )), "fieldOverridePolicy",KeyValue.of("name","fieldOverridePolicy","value","ALWAYS")
                        )
                ))
        );
        List<FieldMergePolicy> fieldMergePolicies = advancedDedupeConfig.getFieldMergePolicies();
        assertEquals(2,fieldMergePolicies.size());
        assertEquals(WinnerOverridePolicy.WHEN_BLANK,fieldMergePolicies.get(0).getOverridePolicy());
        assertEquals("eq", ((Equal)fieldMergePolicies.get(0).getExpresson()).getName());
        assertEquals("field_5f5580ac61b01fe6a9fbac02", ((VariableExpression)((Equal)fieldMergePolicies.get(0).getExpresson()).getLeft()).getVariableName());
        assertEquals("300", ((LiteralExpression)((Equal)fieldMergePolicies.get(0).getExpresson()).getRight()).getValue());

        assertEquals(WinnerOverridePolicy.ALWAYS,fieldMergePolicies.get(1).getOverridePolicy());
        assertEquals("firstMatchingValue", ((FirstMatchingValueExpression)fieldMergePolicies.get(1).getExpresson()).getName());
        assertEquals("field_5f5580ac61b01fe6a9fbac04", ((VariableExpression)((FirstMatchingValueExpression)fieldMergePolicies.get(1).getExpresson()).getLeft()).getVariableName());
        assertEquals(List.of("1","2","3"), ((LiteralExpression)((FirstMatchingValueExpression)fieldMergePolicies.get(1).getExpresson()).getRight()).getValue());

    }

    @Test
    public void emptyFindDupeExpressionsHandled(){
        AdvancedDedupeConfig advancedDedupeConfig = new AdvancedDedupeConfig().setFindDupes(
                KeyValue.of("name","findDupes","compositeValues", List.of(
                        KeyValue.of("repeatId","1"),KeyValue.of("repeatId","2"))));
        List<Expression> dupesCriteria = advancedDedupeConfig.findDupesCriteria();
        assertEquals(0,dupesCriteria.size());
    }
}