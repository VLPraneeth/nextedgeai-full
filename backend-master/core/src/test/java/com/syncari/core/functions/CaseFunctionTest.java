package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.service.FunctionService;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CaseFunctionTest extends AbstractSyncariTest {

    @Autowired
    CaseFunction function;

    @Autowired
    FunctionService functionService;

    @Test
    public void validateDuplicateCaseLabel() {
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        var sinkField1 = SchemaHelper.createAttribute("sinkfield1", StringType.VALUE, srcEntity.getId());
        var sinkField2 = SchemaHelper.createAttribute("sinkfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(sinkField1);
        srcEntity.addField(sinkField2);

        var predicates = List.of(Map.of(
                        "left", Map.of("datatype", "string", "type", "variable", "value", coreField1.getId()),
                        "operator", "empty",
                        "right", Map.of("type", "literal", "value", "")
                )
        );

        List<Map<String, Object>> cases = new ArrayList<>();
        cases.add(Map.of("caseName", "case1", "value", "val1", "datatype", "text", "predicate", Map.of("predicates", predicates, "operator", "AND")));
        cases.add(Map.of("caseName", "case1", "value", "val2", "datatype", "text", "predicate", Map.of("predicates", predicates, "operator", "AND")));
        Map<String, Object> defaultCase = Map.of("value", "defVal", "datatype", "text");
        Map<String, Object> caseData = Map.of(CaseFunction.DEFAULT_CASE_KEY, defaultCase,
                "cases", cases);

        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("case", "case", Map.of("case", caseData))
                .function("caseBranch", "caseBranch", Map.of("value", "case1"))
                .dest(sinkField1)
                .connect("srcfield1", "case")
                .connect("case", "caseBranch")
                .connect("caseBranch", "sinkfield1").getGraph();
        MappingNode caseNode = field1Graph.findNodeByName("case").get();

        ValidationContext context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(caseNode);

        try {
            function.validate(context);
            fail();
        } catch (Exception e) {
            assertEquals("Case name case1 is used more than once in node "+caseNode.getName()+" of graph "+field1Graph.getName()+". Case names must be unique", e.getMessage());
        }

    }

    @Test
    public void validateUsingDefaultForCustomCase() {
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        var sinkField1 = SchemaHelper.createAttribute("sinkfield1", StringType.VALUE, srcEntity.getId());
        var sinkField2 = SchemaHelper.createAttribute("sinkfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(sinkField1);
        srcEntity.addField(sinkField2);

        Map<String, Object> predicate = Map.of("predicates", List.of(Map.of("operator", "empty", "right", Map.of("type", "literal", "value", ""),
                "left", Map.of("value", coreField1.getId(), "label", coreField1.getApiName(), "type", "variable", "datatype", "string", "picklistGroup", "coreAccount"))));
        var cases = List.of(Map.of("caseName", CaseFunction.DEFAULT_CASE_NAME, "value", "val1", "datatype", "text", "predicate", predicate));
        Map<String, Object> defaultCase = Map.of("value", "defVal", "datatype", "text");
        Map<String, Object> caseData = Map.of(CaseFunction.DEFAULT_CASE_KEY, defaultCase,
                "cases", cases);

        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("case", "case", Map.of("case", caseData))
                .function("caseBranch", "caseBranch", Map.of("value", "case1"))
                .dest(sinkField1)
                .connect("srcfield1", "case")
                .connect("case", "caseBranch")
                .connect("caseBranch", "sinkfield1").getGraph();
        MappingNode caseNode = field1Graph.findNodeByName("case").get();

        ValidationContext context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(caseNode);

        try {
            function.validate(context);
            fail();
        } catch (Exception e) {
            assertEquals("Cannot use default as a case name for  node "+caseNode.getName()+" of graph "+field1Graph.getName(), e.getMessage());
        }
    }

    @Test
    public void validateDuplicatePathForSameCaseLabel() {
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        var sinkField1 = SchemaHelper.createAttribute("sinkfield1", StringType.VALUE, srcEntity.getId());
        var sinkField2 = SchemaHelper.createAttribute("sinkfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(sinkField1);
        srcEntity.addField(sinkField2);

        Map<String, Object> predicate = Map.of("operator", "AND", "predicates", List.of(Map.of("operator", "empty", "right", Map.of("type", "literal", "value", ""),
                "left", Map.of("value", coreField1.getId(), "label", coreField1.getApiName(), "type", "variable", "datatype", "string", "picklistGroup", "coreAccount"))));
        var cases = List.of(Map.of("caseName", "case1", "value", "val1", "datatype", "text", "predicate", predicate));
        Map<String, Object> defaultCase = Map.of("value", "defVal", "datatype", "text");
        Map<String, Object> caseData = Map.of(CaseFunction.DEFAULT_CASE_KEY, defaultCase,
                "cases", cases);

        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("case", "case", Map.of("case", caseData))
                .function("caseBranch", "caseBranch", Map.of("value", "case1"))
                .function("caseBranch", "caseBranch1", Map.of("value", CaseFunction.ANY_CASE_NAME))
                .dest(sinkField1)
                .connect("srcfield1", "case")
                .connect("case", "caseBranch")
                .connect("case", "caseBranch1")
                .connect("caseBranch1", "sinkfield1")
                .connect("caseBranch", "sinkfield1").getGraph();
        MappingNode caseNode = field1Graph.findNodeByName("case").get();

        ValidationContext context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(caseNode);
        function.validate(context);

        // has duplicate paths
        field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("case", "case", Map.of("case", caseData))
                .function("caseBranch", "caseBranch", Map.of("value", "case1"))
                .function("caseBranch", "caseBranch1", Map.of("value", "case1"))
                .dest(sinkField1)
                .connect("srcfield1", "case")
                .connect("case", "caseBranch")
                .connect("case", "caseBranch1")
                .connect("caseBranch1", "sinkfield1")
                .connect("caseBranch", "sinkfield1").getGraph();
        caseNode = field1Graph.findNodeByName("case").get();

        context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(caseNode);

        try {
            function.validate(context);
            fail();
        } catch (Exception e) {
            assertEquals("Case Label case1 has more than one path from Case Function Node case", e.getMessage());
        }
    }

    @Test
    public void validateConfig(){
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        var sinkField1 = SchemaHelper.createAttribute("sinkfield1", StringType.VALUE, srcEntity.getId());
        var sinkField2 = SchemaHelper.createAttribute("sinkfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(sinkField1);
        srcEntity.addField(sinkField2);

        Map<String, Object> predicate = Map.of("operator", "AND","predicates", List.of(Map.of("operator", "empty", "right", Map.of("type", "literal", "value", ""),
                "left", Map.of("value", coreField1.getId(), "label", coreField1.getApiName(), "type", "variable", "datatype", "string", "picklistGroup", "coreAccount"))));
        var cases = List.of(Map.of("caseName", "case1", "value", "val1", "datatype", "text", "predicate", predicate));
        Map<String, Object> defaultCase;
        Map<String, Object> caseData;

        //invalid : case without default and custom case, like a switch node without any configuration
        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("case", "case", Map.of())
                .function("caseBranch", "caseBranch", Map.of("value", "case1"))
                .function("caseBranch", "caseBranch1", Map.of("value", CaseFunction.ANY_CASE_NAME))
                .dest(sinkField1)
                .connect("srcfield1", "case")
                .connect("case", "caseBranch")
                .connect("case", "caseBranch1")
                .connect("caseBranch1", "sinkfield1")
                .connect("caseBranch", "sinkfield1").getGraph();
        MappingNode caseNode = field1Graph.findNodeByName("case").get();

        ValidationContext context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(caseNode);

        try {
            function.validate(context);
            fail();
        } catch (Exception e) {
            assertEquals("Invalid Case configuration. Missing mandatory details in node case of graph corefield1", e.getMessage());
        }


        //valid case : without any custom case, default case exists
        defaultCase = Map.of("value", "defVal", "datatype", "text");
        caseData = Map.of(CaseFunction.DEFAULT_CASE_KEY, defaultCase);

        field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("case", "case", Map.of("case", caseData))
                .function("caseBranch", "caseBranch", Map.of("value", "case1"))
                .function("caseBranch", "caseBranch1", Map.of("value", CaseFunction.ANY_CASE_NAME))
                .dest(sinkField1)
                .connect("srcfield1", "case")
                .connect("case", "caseBranch")
                .connect("case", "caseBranch1")
                .connect("caseBranch1", "sinkfield1")
                .connect("caseBranch", "sinkfield1").getGraph();
        caseNode = field1Graph.findNodeByName("case").get();

        context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(caseNode);
        function.validate(context);

        //invalid : no datatype chosen for default case
        defaultCase = Map.of("value", "defVal");
        caseData = Map.of(CaseFunction.DEFAULT_CASE_KEY, defaultCase);

        field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("case", "case", Map.of("case", caseData))
                .function("caseBranch", "caseBranch", Map.of("value", "case1"))
                .function("caseBranch", "caseBranch1", Map.of("value", CaseFunction.ANY_CASE_NAME))
                .dest(sinkField1)
                .connect("srcfield1", "case")
                .connect("case", "caseBranch")
                .connect("case", "caseBranch1")
                .connect("caseBranch1", "sinkfield1")
                .connect("caseBranch", "sinkfield1").getGraph();
        caseNode = field1Graph.findNodeByName("case").get();

        context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(caseNode);

        try {
            function.validate(context);
            fail();
        } catch (Exception e) {
            assertEquals("Data Type is mandatory for Switch cases. Missing datatype in default case of node case of graph corefield1", e.getMessage());
        }

        //invalid : no datatype chosen for custom case
        defaultCase = Map.of("value", "defVal", "datatype", "text");
        cases = List.of(
                Map.of("caseName", "case1", "value", "val1", "datatype", "text", "predicate", predicate),
                Map.of("caseName", "case2", "value", "val1", "predicate", predicate)
                );
        caseData = Map.of(CaseFunction.DEFAULT_CASE_KEY, defaultCase, "cases", cases);

        field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("case", "case", Map.of("case", caseData))
                .function("caseBranch", "caseBranch", Map.of("value", "case1"))
                .function("caseBranch", "caseBranch1", Map.of("value", CaseFunction.ANY_CASE_NAME))
                .dest(sinkField1)
                .connect("srcfield1", "case")
                .connect("case", "caseBranch")
                .connect("case", "caseBranch1")
                .connect("caseBranch1", "sinkfield1")
                .connect("caseBranch", "sinkfield1").getGraph();
        caseNode = field1Graph.findNodeByName("case").get();

        context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(caseNode);

        try {
            function.validate(context);
            fail();
        } catch (Exception e) {
            assertEquals("Data Type is mandatory for Switch cases. Missing datatype in node case of graph corefield1", e.getMessage());
        }

        //invalid: custom case with no case name
        defaultCase = Map.of("value", "defVal", "datatype", "text");
        cases = List.of(
                Map.of("caseName", "case1", "value", "val1", "datatype", "text", "predicate", predicate),
                Map.of("value", "val1", "datatype", "text","predicate", predicate)
        );
        caseData = Map.of(CaseFunction.DEFAULT_CASE_KEY, defaultCase, "cases", cases);

        field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("case", "case", Map.of("case", caseData))
                .function("caseBranch", "caseBranch", Map.of("value", "case1"))
                .function("caseBranch", "caseBranch1", Map.of("value", CaseFunction.ANY_CASE_NAME))
                .dest(sinkField1)
                .connect("srcfield1", "case")
                .connect("case", "caseBranch")
                .connect("case", "caseBranch1")
                .connect("caseBranch1", "sinkfield1")
                .connect("caseBranch", "sinkfield1").getGraph();
        caseNode = field1Graph.findNodeByName("case").get();

        context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(caseNode);

        try {
            function.validate(context);
            fail();
        } catch (Exception e) {
            assertEquals("Case Name is mandatory for Switch cases. Missing case name in node case of graph corefield1", e.getMessage());
        }
    }

}
