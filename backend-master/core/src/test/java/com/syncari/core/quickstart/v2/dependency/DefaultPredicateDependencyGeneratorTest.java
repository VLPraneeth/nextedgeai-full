package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.datatype.StringType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.pipeline.expression.LiteralExpression;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DefaultPredicateDependencyGeneratorTest {

    @Test
    public void generateDependenciesForLiteralExpression_ValidAttributeId(){

        ConnectorService connectorService = mock(ConnectorService.class);
        SchemaService schemaService = mock(SchemaService.class);

        AttributeDefinition attrib1 = new AttributeDefinition().setApiName("attrib1").setDisplayName("Attrib1").setDataType(StringType.VALUE);
        String id = ObjectId.get().toHexString();
        attrib1.setId(id);
        LiteralExpression literal = new LiteralExpression(id, true);
        doReturn(Optional.of(attrib1)).when(schemaService).findAttribute(id);

        QuickStartContext context = new QuickStartContext(connectorService, schemaService);
        context.setQsConfig(new PipelineQSConfig());

        List<QSDependency> dependencyList = ((PipelineQSConfig)context.getQsConfig()).getDependencies();
        assertTrue(dependencyList.isEmpty());
        DefaultPredicateDependencyGenerator predicateDependencyGenerator = new DefaultPredicateDependencyGenerator(schemaService);
        predicateDependencyGenerator.generateDependency(literal, context);
        dependencyList = ((PipelineQSConfig)context.getQsConfig()).getDependencies();
        assertEquals(1, dependencyList.size());
        assertEquals(id, dependencyList.get(0).getId());

    }

    @Test
    public void generateDependenciesForLiteralExpression_InvalidAttributeId(){

        ConnectorService connectorService = mock(ConnectorService.class);
        SchemaService schemaService = mock(SchemaService.class);

        AttributeDefinition attrib1 = new AttributeDefinition().setApiName("attrib1").setDisplayName("Attrib1").setDataType(StringType.VALUE);
        String id = ObjectId.get().toHexString();
        attrib1.setId(id);
        String syncariId = ObjectId.get().toHexString();
        doReturn(Optional.of(attrib1)).when(schemaService).findAttribute(id);

        QuickStartContext context = new QuickStartContext(connectorService, schemaService);
        context.setQsConfig(new PipelineQSConfig());

        List<QSDependency> dependencyList = ((PipelineQSConfig)context.getQsConfig()).getDependencies();
        assertTrue(dependencyList.isEmpty());
        DefaultPredicateDependencyGenerator predicateDependencyGenerator = new DefaultPredicateDependencyGenerator(schemaService);
        LiteralExpression literal = new LiteralExpression(syncariId, true);
        predicateDependencyGenerator.generateDependency(literal, context);
        dependencyList = ((PipelineQSConfig)context.getQsConfig()).getDependencies();
        assertTrue(dependencyList.isEmpty());

    }

    @Test
    public void generateDependenciesForLiteralExpression_DynamicToken(){

        ConnectorService connectorService = mock(ConnectorService.class);
        SchemaService schemaService = mock(SchemaService.class);

        QuickStartContext context = new QuickStartContext(connectorService, schemaService);
        context.setQsConfig(new PipelineQSConfig());

        List<QSDependency> dependencyList = ((PipelineQSConfig)context.getQsConfig()).getDependencies();
        assertTrue(dependencyList.isEmpty());
        DefaultPredicateDependencyGenerator predicateDependencyGenerator = new DefaultPredicateDependencyGenerator(schemaService);
        LiteralExpression literal = new LiteralExpression("{{sfdc1.Lead.FirstName}}", true); // token reference to attribute
        predicateDependencyGenerator.generateDependency(literal, context);
        dependencyList = ((PipelineQSConfig)context.getQsConfig()).getDependencies();
        assertEquals(1, dependencyList.size());
        assertEquals("{{sfdc1.Lead.FirstName}}", dependencyList.get(0).getId());
    }

    @Test
    public void generateDependenciesForLiteralExpression_StaticToken(){

        ConnectorService connectorService = mock(ConnectorService.class);
        SchemaService schemaService = mock(SchemaService.class);

        QuickStartContext context = new QuickStartContext(connectorService, schemaService);
        context.setQsConfig(new PipelineQSConfig());

        List<QSDependency> dependencyList = ((PipelineQSConfig)context.getQsConfig()).getDependencies();
        assertTrue(dependencyList.isEmpty());
        DefaultPredicateDependencyGenerator predicateDependencyGenerator = new DefaultPredicateDependencyGenerator(schemaService);
        LiteralExpression literal = new LiteralExpression("{{previousValue}}", true); // token reference to attribute
        predicateDependencyGenerator.generateDependency(literal, context);
        dependencyList = ((PipelineQSConfig)context.getQsConfig()).getDependencies();
        assertTrue(dependencyList.isEmpty());

    }
}
