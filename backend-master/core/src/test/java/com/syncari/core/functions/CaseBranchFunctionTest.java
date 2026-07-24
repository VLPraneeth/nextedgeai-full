package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.service.FunctionService;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CaseBranchFunctionTest extends AbstractSyncariTest {

    @Autowired
    CaseBranchFunction function;

    @Autowired
    FunctionService functionService;

    @Test
    public void validateCaseFunctionNodeIsConnected(){
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

        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("lower", "Lower")
                .function("caseBranch", "caseBranch")
                .dest(sinkField1)
                .connect("srcfield1", "Lower")
                .connect("Lower", "caseBranch")
                .connect("caseBranch", "corefield1")
                .connect("corefield1", "sinkfield1").getGraph();

        MappingNode caseBranchNode = field1Graph.findNodeByName("caseBranch").get();
        ValidationContext context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(caseBranchNode);

        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Case Node is not connected for case branch node caseBranch for graph corefield1", e.getMessage());
        }

    }

    @Test
    public void validateInvalidCaseValue(){
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
        var cases = List.of(Map.of("caseName", "case1", "value", "val1", "datatype", "text", "predicate", predicate));
        Map<String, Object> defaultCase = Map.of("value", "defVal", "datatype", "text");
        Map<String, Object> caseData = Map.of(CaseFunction.DEFAULT_CASE_KEY, defaultCase,
                "cases", cases);

        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("case", "case", Map.of("case", caseData))
                .function("caseBranch", "caseBranch", Map.of("value", "x3"))
                .dest(sinkField1)
                .connect("srcfield1", "case")
                .connect("case", "caseBranch")
                .connect("caseBranch", "sinkfield1").getGraph();
        MappingNode caseBranchNode = field1Graph.findNodeByName("caseBranch").get();


        ValidationContext context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(caseBranchNode);

        try{
            function.validate(context);
            fail();
        } catch (Exception e){
            assertEquals("Case Value x3 is not a valid value for node caseBranch of graph corefield1", e.getMessage());
        }
    }


    @Test
    public void validateDuplicateAndDefaultCaseValue(){
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
        var cases = List.of(Map.of("caseName", "case1", "value", "val1", "datatype", "text", "predicate", predicate));
        Map<String, Object> defaultCase = Map.of("value", "defVal", "datatype", "text");
        Map<String, Object> caseData = Map.of(CaseFunction.DEFAULT_CASE_KEY, defaultCase,
                "cases", cases);

        //valid custom case label
        MappingGraph field1Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("case", "case", Map.of("case", caseData))
                .function("caseBranch", "caseBranch", Map.of("value", "case1"))
                .dest(sinkField1)
                .connect("srcfield1", "case")
                .connect("case", "caseBranch")
                .connect("caseBranch", "sinkfield1").getGraph();
        MappingNode caseBranchNode = field1Graph.findNodeByName("caseBranch").get();

        ValidationContext context = new ValidationContext().setGraph(field1Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(caseBranchNode);
        function.validate(context);

        //valid Default case label
        MappingGraph field2Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("case", "case", Map.of("case", caseData))
                .function("caseBranch", "caseBranch", Map.of("value", CaseFunction.DEFAULT_CASE_NAME))
                .dest(sinkField1)
                .connect("srcfield1", "case")
                .connect("case", "caseBranch")
                .connect("caseBranch", "sinkfield1").getGraph();
        caseBranchNode = field2Graph.findNodeByName("caseBranch").get();

        context = new ValidationContext().setGraph(field2Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(caseBranchNode);
        function.validate(context);

        //valid Any case label
        MappingGraph field3Graph = newGraph(coreEntity.getFieldByName("corefield1"), functionService)
                .src(srcField1)
                .function("case", "case", Map.of("case", caseData))
                .function("caseBranch", "caseBranch", Map.of("value", CaseFunction.ANY_CASE_NAME))
                .dest(sinkField1)
                .connect("srcfield1", "case")
                .connect("case", "caseBranch")
                .connect("caseBranch", "sinkfield1").getGraph();
        caseBranchNode = field3Graph.findNodeByName("caseBranch").get();

        context = new ValidationContext().setGraph(field3Graph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(caseBranchNode);
        function.validate(context);

    }
}
