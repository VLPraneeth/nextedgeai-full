package com.syncari.core.functions;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.service.def.FileService;
import com.syncari.core.DataTransformer;
import com.syncari.core.datatype.*;
import com.syncari.core.model.ActionResult;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.jtwig.TokenEnvironment;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.SchemaHelper;
import lombok.SneakyThrows;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.bson.types.ObjectId;
import org.jtwig.environment.DefaultEnvironmentConfiguration;
import org.jtwig.environment.EnvironmentFactory;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;


public class ExportSyncariRecordsActionTest {
    ConnectorService connectorService;

    DataServiceFactory dataServiceFactory;
    TokenHelper tokenHelper;
    EntityRepo entityRepo;
    DataTransformer transformer;
    TokenEnvironment environment = new TokenEnvironment(new EnvironmentFactory().create(new DefaultEnvironmentConfiguration()), Map.of());

    @Before
    public void before() {
        connectorService = mock(ConnectorService.class);
        dataServiceFactory = mock(DataServiceFactory.class);
        entityRepo = mock(EntityRepo.class);
        transformer = mock(DataTransformer.class);
        tokenHelper = new TokenHelper(environment);
    }

    @SneakyThrows
    protected void setField(Object target, String fieldName, Object value) {
        Field declaredField = null;
        Class clz = target.getClass();
        while (clz != null && declaredField == null && clz != Object.class) {
            try {
                declaredField = clz.getDeclaredField(fieldName);

            } catch (Exception e) {
            }
            clz = clz.getSuperclass();
        }
        declaredField.setAccessible(true);
        declaredField.set(target, value);
    }

    @Test
    public void executeBaiscExport() throws IOException {
        final ExportSyncariRecordsAction exportSyncariRecordsAction = new ExportSyncariRecordsAction();

        setField(exportSyncariRecordsAction, "connectorService", connectorService);
        setField(exportSyncariRecordsAction, "tokenHelper", tokenHelper);
        setField(exportSyncariRecordsAction, "dataServiceFactory", dataServiceFactory);
        setField(exportSyncariRecordsAction, "entityRepo", entityRepo);
        setField(exportSyncariRecordsAction, "dataTransformer", transformer);

        final GenericActionConfig actionConfig = new GenericActionConfig();

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("exportTest")
                .id()
                .string("field1")
                .datetime("updated_at")
                .bool("is_new_record")
                .dbl("amount")
                .integer("quantity")
                .string("extra")
                .date("dob")
                .getEntityDefinition();
        StringBuilder records = new StringBuilder();
        FileService mockFileService = new FileService() {
            @Override
            public void writeFile(ConnectorInfo connector, InputStream inputStream, String fileName, String baseFolder) {
                try {
                    records.append(new String(inputStream.readAllBytes()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        final Connector sftp = SchemaHelper.createConnector("SFTP", ObjectId.get().toHexString(), ObjectId.get().toHexString());
        when(connectorService.find(sftp.getId())).thenReturn(Optional.of(sftp));
        when(dataServiceFactory.getFileService(eq(sftp.getMetadata()))).thenReturn(mockFileService);
        when(entityRepo.hasCaseInsensitiveIndexOnField(any(), any())).thenReturn(false);
        when(transformer.toConnectorInfo(sftp)).thenReturn(new ConnectorInfo());
        final Page<EntityData> firstPage = new Page<>();
        final Calendar instance = Calendar.getInstance();
        instance.set(2020, 04, 03);
        final Date date = instance.getTime();
        firstPage.setRecords(List.of(
                new EntityData().setId("r1").addValue("field1", "r1f1").addValue("updated_at", ZonedDateTime.now()).addValue("dob", date).addValue("is_new_record", true).addValue("amount", 24.5d).addValue("quantity", 12).addValue("extra", "r1extra"),
                new EntityData().setId("r2").addValue("field1", "r2f1").addValue("updated_at", ZonedDateTime.now()).addValue("dob", date).addValue("is_new_record", false).addValue("amount", 12.2d).addValue("quantity", 6).addValue("extra", "r2extra\r\nwithnewline")
        ));
        final Page<Object> secondPage = new Page<>(null, List.of());
        when(entityRepo.search(eq(entityDefinition), any(Optional.class), any(PageCursor.class))).thenReturn(firstPage, secondPage);
        actionConfig.setConfigMap(Map.of(
                ExportSyncariRecordsAction.SYNCARI_ENTITY_DEF_ID, entityDefinition.getId(),
                ExportSyncariRecordsAction.FILE_NAME, "test.csv",
                ExportSyncariRecordsAction.FOLDER, "/home/sftpusers/example",
                ExportSyncariRecordsAction.MAX_RECORDS, "",
                ExportSyncariRecordsAction.EXPORT_FIELDS, List.of(),
                ExportSyncariRecordsAction.STORAGE_SYNAPSE_ID, sftp.getId(),
                ExportSyncariRecordsAction.DATE_FORMAT, "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                ExportSyncariRecordsAction.PREDICATE, Map.of()
        ));

        final GraphContext context = new GraphContext();
        context.cache(entityDefinition.getId(), entityDefinition);
        final ActionResult exportResult = exportSyncariRecordsAction.execute(actionConfig, context);
        assertTrue(exportResult.isStatus());
        System.out.println(records);
        List<CSVRecord> rows = new ArrayList<>();
        CSVFormat.DEFAULT.parse(new StringReader(records.toString())).forEach(r -> rows.add(r));
        assertEquals(3, rows.size());
        assertEquals(8, rows.get(0).size());
        assertEquals(Map.of("totalExportCount", 2), exportResult.getResult());

    }

    @Test
    public void executeUseDisplayNameInExport() throws IOException {
        final ExportSyncariRecordsAction exportSyncariRecordsAction = new ExportSyncariRecordsAction();

        setField(exportSyncariRecordsAction, "connectorService", connectorService);
        setField(exportSyncariRecordsAction, "tokenHelper", tokenHelper);
        setField(exportSyncariRecordsAction, "dataServiceFactory", dataServiceFactory);
        setField(exportSyncariRecordsAction, "entityRepo", entityRepo);
        setField(exportSyncariRecordsAction, "dataTransformer", transformer);

        final GenericActionConfig actionConfig = new GenericActionConfig();

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("exportTest")
                .id()
                .field("Field 1", "field1", StringType.VALUE)
                .field("Updated At", "updated_at", DatetimeType.VALUE)
                .field("Is New Record", "is_new_record", BooleanType.VALUE)
                .field("Amount", "amount", DoubleType.VALUE)
                .field("Qty", "quantity", IntegerType.VALUE)
                .string("extra")
                .getEntityDefinition();
        List<String> expectedHeader = List.of("id", "Field 1", "Updated At", "Is New Record", "Amount", "Qty", "extra");
        StringBuilder records = new StringBuilder();
        final StringBuilder actualFileName = new StringBuilder();
        FileService mockFileService = new FileService() {
            @Override
            public void writeFile(ConnectorInfo connector, InputStream inputStream, String fileName, String baseFolder) {
                try {
                    actualFileName.append(fileName);
                    records.append(new String(inputStream.readAllBytes()));
                } catch (Exception e) {
                    //
                }
            }
        };
        final Connector sftp = SchemaHelper.createConnector("SFTP", ObjectId.get().toHexString(), ObjectId.get().toHexString());
        when(connectorService.find(sftp.getId())).thenReturn(Optional.of(sftp));
        when(dataServiceFactory.getFileService(eq(sftp.getMetadata()))).thenReturn(mockFileService);
        when(entityRepo.hasCaseInsensitiveIndexOnField(any(), any())).thenReturn(false);
        when(transformer.toConnectorInfo(sftp)).thenReturn(new ConnectorInfo());
        final Page<EntityData> firstPage = new Page<>();
        firstPage.setRecords(List.of(
                new EntityData().setId("r1").addValue("field1", "r1f1").addValue("updated_at", ZonedDateTime.now()).addValue("is_new_record", true).addValue("amount", 24.5d).addValue("quantity", 12).addValue("extra", "r1extra"),
                new EntityData().setId("r2").addValue("field1", "r2f1").addValue("updated_at", ZonedDateTime.now()).addValue("is_new_record", false).addValue("amount", 12.2d).addValue("quantity", 6).addValue("extra", "r2extra")
        ));
        final Page<Object> secondPage = new Page<>(null, List.of());
        when(entityRepo.search(eq(entityDefinition), any(Optional.class), any(PageCursor.class))).thenReturn(firstPage, secondPage);
        actionConfig.setConfigMap(Map.of(
                ExportSyncariRecordsAction.SYNCARI_ENTITY_DEF_ID, entityDefinition.getId(),
                ExportSyncariRecordsAction.FILE_NAME, "{{someKey}}test.csv",
                ExportSyncariRecordsAction.FOLDER, "/home/sftpusers/example",
                ExportSyncariRecordsAction.MAX_RECORDS, "",
                ExportSyncariRecordsAction.USE_DISPLAY_NAME, "true",
                ExportSyncariRecordsAction.EXPORT_FIELDS, List.of(),
                ExportSyncariRecordsAction.STORAGE_SYNAPSE_ID, sftp.getId(),
                ExportSyncariRecordsAction.PREDICATE, Map.of()
        ));

        final GraphContext context = new GraphContext();
        context.set("someKey", "fileNamePrefix");
        context.cache(entityDefinition.getId(), entityDefinition);
        final ActionResult exportResult = exportSyncariRecordsAction.execute(actionConfig, context);
        assertTrue(exportResult.isStatus());
        List<CSVRecord> rows = new ArrayList<>();
        CSVFormat.DEFAULT.parse(new StringReader(records.toString())).forEach(r -> rows.add(r));
        assertEquals(3, rows.size());
        List<String> headers = new ArrayList<>();
        rows.get(0).iterator().forEachRemaining(r -> headers.add(r));
        assertEquals(expectedHeader, headers);
        assertEquals(actualFileName.toString(), "fileNamePrefixtest.csv");
        assertEquals(Map.of("totalExportCount", 2), exportResult.getResult());

    }
    
    @Test
    public void executeDateDateTimeExport() throws IOException {
        final ExportSyncariRecordsAction exportSyncariRecordsAction = new ExportSyncariRecordsAction();

        setField(exportSyncariRecordsAction, "connectorService", connectorService);
        setField(exportSyncariRecordsAction, "tokenHelper", tokenHelper);
        setField(exportSyncariRecordsAction, "dataServiceFactory", dataServiceFactory);
        setField(exportSyncariRecordsAction, "entityRepo", entityRepo);
        setField(exportSyncariRecordsAction, "dataTransformer", transformer);

        final GenericActionConfig actionConfig = new GenericActionConfig();

        final EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("exportTest")
                .id()
                .string("field1")
                .datetime("updated_at")
                .bool("is_new_record")
                .dbl("amount")
                .integer("quantity")
                .string("extra")
                .date("dob")
                .getEntityDefinition();
        StringBuilder records = new StringBuilder();
        FileService mockFileService = new FileService() {
            @Override
            public void writeFile(ConnectorInfo connector, InputStream inputStream, String fileName, String baseFolder) {
                try {
                    records.append(new String(inputStream.readAllBytes()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        final Connector sftp = SchemaHelper.createConnector("SFTP", ObjectId.get().toHexString(), ObjectId.get().toHexString());
        when(connectorService.find(sftp.getId())).thenReturn(Optional.of(sftp));
        when(dataServiceFactory.getFileService(eq(sftp.getMetadata()))).thenReturn(mockFileService);
        when(entityRepo.hasCaseInsensitiveIndexOnField(any(), any())).thenReturn(false);
        when(transformer.toConnectorInfo(sftp)).thenReturn(new ConnectorInfo());
        final Page<EntityData> firstPage = new Page<>();
        final Calendar instance = Calendar.getInstance();
        instance.set(2020, 04, 03);
        final Date date = instance.getTime();
        firstPage.setRecords(List.of(
                new EntityData().setId("r1").addValue("field1", "r1f1").addValue("updated_at", ZonedDateTime.now()).addValue("dob", date).addValue("is_new_record", true).addValue("amount", 24.5d).addValue("quantity", 12).addValue("extra", "r1extra"),
                new EntityData().setId("r2").addValue("field1", "r2f1").addValue("updated_at", ZonedDateTime.now()).addValue("dob", date).addValue("is_new_record", false).addValue("amount", 12.2d).addValue("quantity", 6).addValue("extra", "r2extra\r\nwithnewline")
        ));
        final Page<Object> secondPage = new Page<>(null, List.of());
        when(entityRepo.search(eq(entityDefinition), any(Optional.class), any(PageCursor.class))).thenReturn(firstPage, secondPage);
        actionConfig.setConfigMap(Map.of(
                ExportSyncariRecordsAction.SYNCARI_ENTITY_DEF_ID, entityDefinition.getId(),
                ExportSyncariRecordsAction.FILE_NAME, "test.csv",
                ExportSyncariRecordsAction.FOLDER, "/home/sftpusers/example",
                ExportSyncariRecordsAction.MAX_RECORDS, "",
                ExportSyncariRecordsAction.EXPORT_FIELDS, List.of(),
                ExportSyncariRecordsAction.STORAGE_SYNAPSE_ID, sftp.getId(),
                ExportSyncariRecordsAction.DATE_FORMAT, "",
                ExportSyncariRecordsAction.DATETIME_FORMAT,"",
                ExportSyncariRecordsAction.PREDICATE, Map.of()
        ));

        final GraphContext context = new GraphContext();
        context.cache(entityDefinition.getId(), entityDefinition);
        final ActionResult exportResult = exportSyncariRecordsAction.execute(actionConfig, context);
        assertTrue(exportResult.isStatus());
        System.out.println(records);
        List<CSVRecord> rows = new ArrayList<>();
        CSVFormat.DEFAULT.parse(new StringReader(records.toString())).forEach(r -> rows.add(r));
        assertEquals(3, rows.size());
        assertEquals(8, rows.get(0).size());
        System.out.println(rows.get(1).get(2));
        assertTrue(!rows.get(1).get(2).isBlank());
        System.out.println(rows.get(1).get(7));
        assertTrue(!rows.get(1).get(7).isBlank());
        assertEquals(Map.of("totalExportCount", 2), exportResult.getResult());

    }
}