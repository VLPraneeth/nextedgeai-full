package com.syncari.viper.streams.stages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.core.datatype.IntegerType;
import com.syncari.core.service.*;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.AbstractSyncariTest;
import com.syncari.connector.EntityData;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.utils.Pair;


public class FieldPipelineTextFunctionTest extends AbstractSyncariTest {
    @Autowired
    FunctionService functionService;
    @Mock
    SchemaService schemaService;
    @Mock
    EntityRepo entityRepo;
    @Mock
    ConnectorService connectorService;
    @Autowired
    ExecuteFieldPipeline executeFieldPipeline;
    @Autowired
    SyncDetailMetricService syncDetailMetricService;
    @Autowired
    FeatureService featureService;
    @Autowired
    PipelineUtil pipelineUtil;

    @Autowired
    NotificationService notificationService;


    FieldPipelineTestHelper helper;


    @Before
    public void init() {
        doNothing().when(eventService).log(any());
        executeFieldPipeline = new ExecuteFieldPipeline(connectorService,entityRepo,executeFieldPipeline.graphService,executeFieldPipeline.evaluator
                ,schemaService,executeFieldPipeline.attributeProxyRepo,executeFieldPipeline.eventStore,
                executeFieldPipeline.recordMergeService,executeFieldPipeline.idMappingRepo,
                executeFieldPipeline.unresolvedReferenceRepo,executeFieldPipeline.datastoreService,executeFieldPipeline.repoService,executeFieldPipeline.requeueService,executeFieldPipeline.transactionLogService,syncDetailMetricService,featureService, pipelineUtil,notificationService);
        helper = new FieldPipelineTestHelper(functionService, schemaService,entityRepo, connectorService,executeFieldPipeline);
    }


    @Test
    public void trimText() {
        String coreField = "name";
        String sourceField = "Name";
        String testText = "    test    ";
        String functionName = "trim";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, testText);

        // test trim (....test....)
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("test", change.getChanges().getValue(coreField));

        // leading spaces
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, " test"));
        assertTrue(change.getChanges().has(coreField));
        assertEquals("test", change.getChanges().getValue(coreField));

        // trailing spaces
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, "test "));
        assertTrue(change.getChanges().has(coreField));
        assertEquals("test", change.getChanges().getValue(coreField));

        // sentence spaces
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, "this is a test sentence"));
        assertTrue(change.getChanges().has(coreField));
        assertEquals("this is a test sentence", change.getChanges().getValue(coreField));

        // null
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, null));
        assertFalse(change.getChanges().has(coreField));
        assertNull(change.getChanges().getValue(coreField));
    }

    @Test
    public void camelCase() {
        String coreField = "name";
        String sourceField = "Name";
        String testText = "this is a camel";
        String functionName = "camelCase";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, testText);

        // test trim (....test....)
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("This Is A Camel", change.getChanges().getValue(coreField));

        // no spaces...
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, "thisisatest"));
        assertTrue(change.getChanges().has(coreField));
        assertEquals("Thisisatest", change.getChanges().getValue(coreField));

        // all upper...
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, "THIS IS A TEST"));
        assertTrue(change.getChanges().has(coreField));
        assertEquals("This Is A Test", change.getChanges().getValue(coreField));

        // null...
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, null));
        assertFalse(change.getChanges().has(coreField));
        assertNull(change.getChanges().getValue(coreField));
    }

    @Test
    public void capitalize() {
        String coreField = "name";
        String sourceField = "Name";
        String testText = "this is capitalized.";
        String functionName = "capitalize";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, testText);

        // test trim (....test....)
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("This is capitalized.", change.getChanges().getValue(coreField));

        // no spaces...
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, "thisiscapitalized"));
        assertTrue(change.getChanges().has(coreField));
        assertEquals("Thisiscapitalized", change.getChanges().getValue(coreField));

        // all upper...
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, "THIS IS CAPITALIZED"));
        assertTrue(change.getChanges().has(coreField));
        assertEquals("THIS IS CAPITALIZED", change.getChanges().getValue(coreField));

        // null...
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, null));
        assertFalse(change.getChanges().has(coreField));
        assertNull(change.getChanges().getValue(coreField));
    }

    @Test
    public void urlEncode() {
        String coreField = "name";
        String sourceField = "Name";
        String testText = "</this is='' a=& test=\"\">";
        String functionName = "urlEncode";

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, testText);

        // large string test
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("%3C%2Fthis+is%3D%27%27+a%3D%26+test%3D%22%22%3E", change.getChanges().getValue(coreField));

        // single character
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, "+"));
        assertTrue(change.getChanges().has(coreField));
        assertEquals("%2B", change.getChanges().getValue(coreField));

        // trailing spaces
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, "this is a test"));
        assertTrue(change.getChanges().has(coreField));
        assertEquals("this+is+a+test", change.getChanges().getValue(coreField));

        // null
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, null));
        assertFalse(change.getChanges().has(coreField));
        assertNull(change.getChanges().getValue(coreField));
    }

    @Test
    public void setValue() {
        String coreField = "name";
        String sourceField = "Name";
        String testText = "X1234";
        String functionName = "setValue";

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, testText);

        // set a value
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertEquals("X1234", change.getChanges().getValue(coreField));

        // set a blank value
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, ""));
        assertEquals("", change.getChanges().getValue(coreField));

        // null
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, null));
        assertNull(change.getChanges().getValue(coreField));
    }

    @Test
    public void encryptDecrypt() {
        String coreField1 = "name";
        String sourceField1 = "Name";
        String key = "superSecret";

        List<Pair> coreFields = new ArrayList<>();
        coreFields.add(Pair.of(coreField1, new StringType()));

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of(sourceField1, new StringType()));

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, coreFields);
        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, "this is unreadable");

        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);

        Map<String, Object> config = new HashMap<>();
        config.put("key", key);

        // set a value
        Change change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "encrypt", config, entityData);
        String encryptedText = change.getChanges().getValueAsString(coreField1);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals(69, encryptedText.length());

        // push the encrypted text back into the entity data
        entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, encryptedText);

        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "decrypt", config, entityData);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals("this is unreadable", change.getChanges().getValueAsString(coreField1));

        // push the encrypted text back into the entity data
        entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, null);

        // check null
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "decrypt", config, entityData);
        assertNull(change.getChanges().getValueAsString(coreField1));

        // check null
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "encrypt", config, entityData);
        assertNull(change.getChanges().getValueAsString(coreField1));

        // check to see if we're creating cryptographically secure data (Salt should change EVERY encryption!!)
        // push the encrypted text back into the entity data
        entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, "abcdefg abcdefg abcdefg 123 123 123");

        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "encrypt", config, entityData);
        assertNotNull(change.getChanges().getValueAsString(coreField1));
        String round1 = change.getChanges().getValueAsString(coreField1);

        // encrypt again
        // push the encrypted text back into the entity data
        entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, "abcdefg abcdefg abcdefg 123 123 123");

        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "encrypt", config, entityData);
        String round2 = change.getChanges().getValueAsString(coreField1);

        assertNotEquals(round1, round2);
        assertTrue(round1.length() > 0);
        assertTrue(round2.length() > 0);
    }

    @Test
    public void reverseText() {
        String coreField = "name";
        String sourceField = "Name";
        String testText = "abcdefg1234567";
        String functionName = "reverseString";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, testText);

        // reverse a string
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("7654321gfedcba", change.getChanges().getValue(coreField));

        // reverse a string
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, " a b c d e f g h i ");
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(" i h g f e d c b a ", change.getChanges().getValue(coreField));

        // null
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, null));
        assertFalse(change.getChanges().has(coreField));
        assertNull(change.getChanges().getValue(coreField));
    }

    @Test
    public void replace() {
        String coreField1 = "name";
        String sourceField1 = "Name";

        String functionName = "replace";

        List<Pair> coreFields = new ArrayList<>();
        coreFields.add(Pair.of(coreField1, new StringType()));

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of(sourceField1, new StringType()));

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, coreFields);
        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, "Are you hungry?");

        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);

        Map<String, Object> config = new HashMap<>();
        config.put("searchExpression", "u");
        config.put("replaceWith", "X");

        // set a value
        Change change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, functionName, config, entityData);
        String replacedText = change.getChanges().getValueAsString(coreField1);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals(replacedText, "Are yoX hXngry?");

        config = new HashMap<>();
        config.put("searchExpression", "A");
        config.put("replaceWith", "p");

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField1, "AAA");
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, functionName, config, entityData);
        replacedText = change.getChanges().getValueAsString(coreField1);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals(replacedText, "ppp");

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField1, null);
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, functionName, config, entityData);
        replacedText = change.getChanges().getValueAsString(coreField1);
        assertFalse(change.getChanges().has(coreField1));
        assertNull(replacedText);
    }

    @Test
    public void extractText() {
        String coreField1 = "name";
        String sourceField1 = "Name";

        String functionName = "extractText";

        List<Pair> coreFields = new ArrayList<>();
        coreFields.add(Pair.of(coreField1, new StringType()));

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of(sourceField1, new StringType()));

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, coreFields);
        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString());

        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);

        Map<String, Object> config = new HashMap<>();
        config.put("searchExpression", "(SYN-\\d+)");
        config.put("input", "SYN-12345 jira ticket");

        Change change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, functionName, config, entityData);
        String replacedText = change.getChanges().getValueAsString(coreField1);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals(replacedText, "SYN-12345");
    }

    @Test
    public void encodeDecode() {
        String coreField1 = "name";
        String sourceField1 = "Name";

        List<Pair> coreFields = new ArrayList<>();
        coreFields.add(Pair.of(coreField1, new StringType()));

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of(sourceField1, new StringType()));

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, coreFields);
        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, "this is not encoded");

        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);

        // set a value
        Change change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "encode", Map.of(), entityData);
        String encodedText = change.getChanges().getValueAsString(coreField1);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals(28, encodedText.length());
        assertEquals("dGhpcyBpcyBub3QgZW5jb2RlZA==", encodedText);

        // push the encrypted text back into the entity data
        entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, encodedText);

        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "decode", Map.of(), entityData);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals("this is not encoded", change.getChanges().getValueAsString(coreField1));

        // push the encrypted text back into the entity data
        entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, null);

        // check null
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "decode", Map.of(), entityData);
        assertNull(change.getChanges().getValueAsString(coreField1));

        // check null
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "encode", Map.of(), entityData);
        assertNull(change.getChanges().getValueAsString(coreField1));
    }

    @Test
    public void stripTags() {
        String coreField1 = "name";
        String sourceField1 = "Name";

        String functionName = "striptags";

        List<Pair> coreFields = new ArrayList<>();
        coreFields.add(Pair.of(coreField1, new StringType()));

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of(sourceField1, new StringType()));

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, "<p>this</p><body>that</body><script>WOW</script>test<br>test<br>");

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, coreFields);
        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);

        Map<String, Object> config = new HashMap<>();
        // no config
        Change change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, functionName, config, entityData);
        String tags = change.getChanges().getValueAsString(coreField1);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals("thisthatWOWtesttest", tags);

        config.put("allowedTags", "<br>");
        // set a value
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, functionName, config, entityData);
        tags = change.getChanges().getValueAsString(coreField1);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals("thisthatWOWtest<br>test<br>", tags);

        // set a value
        entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, "xxx><xxx");
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, functionName, config, entityData);
        tags = change.getChanges().getValueAsString(coreField1);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals("xxx><xxx", tags);

        // set a value
        entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, null);
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, functionName, config, entityData);
        tags = change.getChanges().getValueAsString(coreField1);
        assertFalse(change.getChanges().has(coreField1));
        assertNull(tags);
    }

    @Test
    public void subString() {
        String coreField1 = "name";
        String sourceField1 = "Name";

        String functionName = "substring";

        List<Pair> coreFields = new ArrayList<>();
        coreFields.add(Pair.of(coreField1, new StringType()));

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of(sourceField1, new StringType()));

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, "01234567890ABCDEFGHIJK");

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, coreFields);
        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);

        Map<String, Object> config = new HashMap<>();
        config.put("startIndex", 5);
        config.put("endIndex", 5);

        // set a value
        Change change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, functionName, config, entityData);
        String substr = change.getChanges().getValueAsString(coreField1);
        assertFalse(change.getChanges().has(coreField1));
        assertEquals(null, substr);

        config = new HashMap<>();
        config.put("startIndex", 5);
        config.put("endIndex", 10);

        // set a value
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, functionName, config, entityData);
        substr = change.getChanges().getValueAsString(coreField1);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals("56789", substr);

        // set a value
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField1,  null);
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, functionName, config, entityData);
        substr = change.getChanges().getValueAsString(coreField1);
        assertFalse(change.getChanges().has(coreField1));
        assertNull(substr);
    }

    @Test
    public void split() {
        String coreField1 = "name";
        String sourceField1 = "Name";

        List<Pair> coreFields = new ArrayList<>();
        coreFields.add(Pair.of(coreField1, new StringType()));

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of(sourceField1, new StringType()));

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, "0|1|2|3|4|5|6|7|8|9|10");

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, coreFields);
        coreEntityDef.getFieldByName("name").setMultiValueField(true);
        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);

        Map<String, Object> config = new HashMap<>();
        config.put("delimiter", "\\|");

        Change change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "split", config, entityData);
        Object result = change.getChanges().getValue(coreField1);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals(List.of("0","1","2","3","4","5","6","7","8","9","10"), result);

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField1, "1,2,3");
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "split", Map.of(), entityData);
        result = change.getChanges().getValue(coreField1);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals(List.of("1","2","3"), result);

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField1, null);
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "split", config, entityData);
        result = change.getChanges().getValue(coreField1);
        assertTrue(change.getChanges().has(coreField1));

        // TODO: Shouldn't this be null?
        assertEquals(List.of(), result);
    }


    @Test
    public void removeNonPrintable() {
        String coreField = "name";
        String sourceField = "Name";
        String testText = "A\nB\tC";
        String functionName = "removeNonPrintable";

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, testText);

        // large string test
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("ABC", change.getChanges().getValue(coreField));

        // null
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, null));
        assertFalse(change.getChanges().has(coreField));
        assertNull(change.getChanges().getValue(coreField));
    }

    @Test
    public void lower() {
        String coreField = "name";
        String sourceField = "Name";
        String testText = "THIS is ALL mostly LOWERCASE.";
        String functionName = "lower";

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, testText);

        // large string test
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("this is all mostly lowercase.", change.getChanges().getValue(coreField));

        // single character
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, "1234567890aAa"));
        assertTrue(change.getChanges().has(coreField));
        assertEquals("1234567890aaa", change.getChanges().getValue(coreField));

        // trailing spaces
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, "   AAA   "));
        assertTrue(change.getChanges().has(coreField));
        assertEquals("   aaa   ", change.getChanges().getValue(coreField));

        // null
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, null));
        assertFalse(change.getChanges().has(coreField));
        assertNull(change.getChanges().getValue(coreField));
    }

    @Test
    public void extractDomain() {
        String coreField = "name";
        String sourceField = "Name";
        String testText = "mike@syncari.com";
        String functionName = "extractDomain";

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, testText);

        // large string test
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("syncari.com", change.getChanges().getValue(coreField));

        // single character
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, "roger@rogers.com"));
        assertTrue(change.getChanges().has(coreField));
        assertEquals("rogers.com", change.getChanges().getValue(coreField));

        // trailing spaces
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, "Email me at mike@syncari.com"));
        assertTrue(change.getChanges().has(coreField));
        assertEquals("syncari.com", change.getChanges().getValue(coreField));

        // null
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, functionName, entityData.addValue(sourceField, null));
        assertFalse(change.getChanges().has(coreField));
        assertNull(change.getChanges().getValue(coreField));
    }

    @Test
    public void phoneNumberFormat() {
        String coreField1 = "name";
        String sourceField1 = "Name";

        List<Pair> coreFields = new ArrayList<>();
        coreFields.add(Pair.of(coreField1, new StringType()));

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of(sourceField1, new StringType()));

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, coreFields);
        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, "4166785736");

        Map<String, Object> config = new HashMap<>();
        config.put("format", "E164");
        config.put("countryCodeField", "US");

        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);

        // set a value
        Change change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "formatPhone", config, entityData);
        String formattedText = change.getChanges().getValueAsString(coreField1);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals("+14166785736", formattedText);

        // set a value
        entityData = new EntityData("account").setSyncariEntityId(
                ObjectId.get().toHexString()).addValue(sourceField1, "234-8023247048");
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "formatPhone", config, entityData);
        formattedText = change.getChanges().getValueAsString(coreField1);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals("234-8023247048", formattedText);

        entityData = new EntityData("account").setSyncariEntityId(
                ObjectId.get().toHexString()).addValue(sourceField1, "(212) 944-6000 press 0");
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "formatPhone", config, entityData);
        formattedText = change.getChanges().getValueAsString(coreField1);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals("(212) 944-6000 press 0", formattedText);

        entityData = new EntityData("account").setSyncariEntityId(
                ObjectId.get().toHexString()).addValue(sourceField1, null);
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, "formatPhone", config, entityData);
        formattedText = change.getChanges().getValueAsString(coreField1);
        assertFalse(change.getChanges().has(coreField1));
        assertNull(formattedText);
    }

    @Test
    public void mask() {
        String coreField = "name";
        String sourceField = "Name";

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, "test");

        // large string test
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, "mask", entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("****", change.getChanges().getValue(coreField));

        // null
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, "mask", entityData.addValue(sourceField, null));
        assertFalse(change.getChanges().has(coreField));
        assertNull(change.getChanges().getValue(coreField));
    }
    
    @Test
    public void uuid() {
        String coreField = "name";
        String sourceField = "Name";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, "test");
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, "uuid", entityData);
        assertTrue(change.getChanges().has(coreField));
        assertNotNull(change.getChanges().getValue(coreField));
    }
    
    @Test
    public void rtrim() {
        String coreField = "name";
        String sourceField = "Name";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, "test    ");
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, "rtrim", entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("test", change.getChanges().getValue(coreField));

        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, "   test    ");
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, "rtrim", entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("   test", change.getChanges().getValue(coreField));
    }
    
    @Test
    public void ltrim() {
        String coreField = "name";
        String sourceField = "Name";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, "    test");
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, "ltrim", entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("test", change.getChanges().getValue(coreField));
        
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, "   test    ");
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, "ltrim", entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("test    ", change.getChanges().getValue(coreField));
    }
    
    @Test
    public void rpad() {
        String coreField = "name";
        String sourceField = "Name";
        Map<String, Object> config = new HashMap<>();
        config.put("pad", "-");
        config.put("size", 7);
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, "test");
        Change change = helper.executeFunction(coreEntityDef, null, sourceField, coreField, "rpad", config, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("test---", change.getChanges().getValue(coreField));
        
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, "test ");
        change = helper.executeFunction(coreEntityDef, null, sourceField, coreField, "rpad", config, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("test --", change.getChanges().getValue(coreField));
    }
    
    @Test
    public void lpad() {
        String coreField = "name";
        String sourceField = "Name";
        Map<String, Object> config = new HashMap<>();
        config.put("pad", "-");
        config.put("size", 7);
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, "test");
        Change change = helper.executeFunction(coreEntityDef, null, sourceField, coreField, "lpad", config, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("---test", change.getChanges().getValue(coreField));
        
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, " test");
        change = helper.executeFunction(coreEntityDef, null, sourceField, coreField, "lpad", config, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("-- test", change.getChanges().getValue(coreField));
    }
    
    @Test
    public void concatenate() {
        String coreField = "name";
        String first = "first";
        String last = "last";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new StringType())));
        EntityDefinition srcEntityDef = helper.getEntityDef("accountsrc", null, List.of(Pair.of(first, new StringType()), Pair.of(last, new StringType())));
        Map<String, Object> config = new HashMap<>();
        config.put("separator", "-");
        config.put("values", List.of(srcEntityDef.getAttributes().get(0).getId(), srcEntityDef.getAttributes().get(1).getId()));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(first, "test").addValue(last, "val");
        Change change = helper.executeFunction(coreEntityDef, srcEntityDef, first, coreField, "concatenate", config, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("test-val", change.getChanges().getValue(coreField));
        
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(first, "test").addValue(last, null);
        change = helper.executeFunction(coreEntityDef, srcEntityDef, first, coreField, "concatenate", config, entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals("test", change.getChanges().getValue(coreField));
    }
    
    @Test
    public void length() {
        String coreField = "name";
        String sourceField = "Name";
        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, List.of(Pair.of(coreField, new IntegerType()), Pair.of(sourceField, new StringType())));
        EntityData entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, "test");
        Change change = helper.executeFunction(coreEntityDef, sourceField, coreField, "length", entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(4l, change.getChanges().getValue(coreField));
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField, null);
        change = helper.executeFunction(coreEntityDef, sourceField, coreField, "length", entityData);
        assertTrue(change.getChanges().has(coreField));
        assertEquals(0l, change.getChanges().getValue(coreField));
    }

    @Test
    public void indexOf() {
        String coreField1 = "name";
        String sourceField1 = "Name";

        String functionName = "indexOf";

        List<Pair> coreFields = new ArrayList<>();
        coreFields.add(Pair.of(coreField1, new IntegerType()));

        List<Pair> sourceFields = new ArrayList<>();
        sourceFields.add(Pair.of(sourceField1, new StringType()));

        EntityData entityData = new EntityData("account")
                .setSyncariEntityId(ObjectId.get().toHexString())
                .addValue(sourceField1, "01234567890ABCDEFGHIJK");

        EntityDefinition coreEntityDef = helper.getEntityDef("account", null, coreFields);
        EntityDefinition sourceEntityDef = helper.getEntityDef("account", null, sourceFields);

        Map<String, Object> config = new HashMap<>();
        config.put("searchString", "12345");

        // set a value
        Change change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, functionName, config, entityData);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals(change.getChanges().getValue(coreField1), 1l);

        config = new HashMap<>();
        config.put("searchString", "ABC");

        // set a value
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, functionName, config, entityData);
        assertTrue(change.getChanges().has(coreField1));
        assertEquals(change.getChanges().getValue(coreField1), 11l);

        // set a value
        entityData = new EntityData("account").setSyncariEntityId(ObjectId.get().toHexString()).addValue(sourceField1,  null);
        change = helper.executeFunction(
                coreEntityDef, sourceEntityDef, sourceField1, coreField1, functionName, config, entityData);
        assertEquals(change.getChanges().getValue(coreField1), -1l);
    }
}
