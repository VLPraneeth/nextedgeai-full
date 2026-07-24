package com.syncari.core.quickstart.v2;

import com.syncari.core.model.Connector;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.service.ConnectorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for QuickStartV2Service
 * <p>
 * SYN-20634: Quick Start install failing when synapse exists
 * Tests for connector resolution steps generation.
 */
@RunWith(MockitoJUnitRunner.class)
public class QuickStartV2ServiceTest {

    @Mock
    private ConnectorService connectorService;

    @InjectMocks
    private QuickStartV2Service quickStartV2Service;

    private static final String WEBHOOK_METADATA_ID = "webhook-receiver-metadata-id";

    @Before
    public void setUp() {
        // Setup is handled by MockitoJUnitRunner
    }

    /**
     * Test that when NO connector with matching metadataId exists,
     * it SHOULD be added to missingConnectors (CONNECTOR_CREATE step).
     */
    @Test
    public void testGetConnectorResolutionSteps_NoMatchingConnector_ShouldBeMissing() {
        // Arrange: No connectors in destination that match
        when(connectorService.getAllActive()).thenReturn(Collections.emptyList());

        // Create source connector with a metadataId that doesn't exist in dest
        String nonExistentMetadataId = "non-existent-metadata-id";
        Connector sourceConnector = createConnector("source-id-2", "Some Synapse", nonExistentMetadataId);

        QSDependency connectorDependency = new QSDependency()
                .setId(sourceConnector.getId())
                .setType(QSDependency.Type.Connector)
                .setSourceValue(sourceConnector)
                .setDestinationValue(null)
                .setSystemResolved(false);

        PipelineQSConfig qsConfig = new PipelineQSConfig();
        qsConfig.addDependency(connectorDependency);

        // Act
        List<QuickStartInstallStep> steps = quickStartV2Service.getConnectorResolutionSteps(qsConfig);

        // Assert: Since no matching connector exists, it should be treated as missing
        boolean hasConnectorCreateStep = steps.stream()
                .anyMatch(step -> step.getStepName() == QuickStartInstallStep.Step.CONNECTOR_CREATE);

        assertTrue(
                "When no matching connector exists, it should generate a CONNECTOR_CREATE step",
                hasConnectorCreateStep
        );
    }

    /**
     * Test that when multiple connectors with matching metadataId exist,
     * a CONNECTOR_SELECT step should be generated (user must choose).
     */
    @Test
    public void testGetConnectorResolutionSteps_MultipleMatchingConnectors_ShouldSelect() {
        // Arrange: Set up destination instance with TWO matching connectors
        String metadataId = "duplicate-metadata-id";
        Connector destConnector1 = createConnector("dest-id-1", "Webhook 1", metadataId);
        Connector destConnector2 = createConnector("dest-id-2", "Webhook 2", metadataId);
        when(connectorService.getAllActive()).thenReturn(Arrays.asList(destConnector1, destConnector2));

        // Source connector
        Connector sourceConnector = createConnector("source-id-3", "Source Webhook", metadataId);

        QSDependency connectorDependency = new QSDependency()
                .setId(sourceConnector.getId())
                .setType(QSDependency.Type.Connector)
                .setSourceValue(sourceConnector)
                .setDestinationValue(null)
                .setSystemResolved(false);

        PipelineQSConfig qsConfig = new PipelineQSConfig();
        qsConfig.addDependency(connectorDependency);

        // Act
        List<QuickStartInstallStep> steps = quickStartV2Service.getConnectorResolutionSteps(qsConfig);

        // Assert: Multiple matches should generate CONNECTOR_SELECT step
        boolean hasConnectorSelectStep = steps.stream()
                .anyMatch(step -> step.getStepName() == QuickStartInstallStep.Step.CONNECTOR_SELECT);

        assertTrue(
                "When multiple matching connectors exist, it should generate a CONNECTOR_SELECT step",
                hasConnectorSelectStep
        );
    }

    /**
     * Test that already resolved dependencies (systemResolved=true) are not added to missing or select lists.
     */
    @Test
    public void testGetConnectorResolutionSteps_AlreadyResolved_NoSteps() {
        // Arrange
        Connector destConnector = createConnector("dest-id-1", "Resolved Connector", WEBHOOK_METADATA_ID);
        when(connectorService.getAllActive()).thenReturn(Collections.singletonList(destConnector));

        Connector sourceConnector = createConnector("source-id-4", "Source Connector", WEBHOOK_METADATA_ID);

        // Already resolved dependency (simulating what resolveSynapseDependencies does)
        QSDependency connectorDependency = new QSDependency()
                .setId(sourceConnector.getId())
                .setType(QSDependency.Type.Connector)
                .setSourceValue(sourceConnector)
                .setDestinationValue(destConnector)  // Already resolved
                .setSystemResolved(true);            // Marked as system resolved

        PipelineQSConfig qsConfig = new PipelineQSConfig();
        qsConfig.addDependency(connectorDependency);

        // Act
        List<QuickStartInstallStep> steps = quickStartV2Service.getConnectorResolutionSteps(qsConfig);

        // Assert: Already resolved should not generate any connector steps
        boolean hasConnectorStep = steps.stream()
                .anyMatch(step -> step.getStepName() == QuickStartInstallStep.Step.CONNECTOR_CREATE
                        || step.getStepName() == QuickStartInstallStep.Step.CONNECTOR_SELECT);

        assertFalse(
                "Already resolved connectors should not generate any connector resolution steps",
                hasConnectorStep
        );
    }

    /**
     * SYN-20634: Test that connectors with same display name but different metadataId
     * are NOT matched (this is correct behavior - matching is by metadataId).
     */
    @Test
    public void testGetConnectorResolutionSteps_SameNameDifferentMetadataId_ShouldBeMissing() {
        // Arrange: Destination has a connector with same NAME but different metadataId
        String destMetadataId = "salesforce-metadata-id";
        String srcMetadataId = "webhook-receiver-metadata-id";

        Connector destConnector = createConnector("dest-id-1", "Welcome Home Influencer", destMetadataId);
        when(connectorService.getAllActive()).thenReturn(Collections.singletonList(destConnector));

        // Source connector has same name but different metadataId
        Connector sourceConnector = createConnector("source-id-1", "Welcome Home Influencer", srcMetadataId);

        QSDependency connectorDependency = new QSDependency()
                .setId(sourceConnector.getId())
                .setType(QSDependency.Type.Connector)
                .setSourceValue(sourceConnector)
                .setDestinationValue(null)
                .setSystemResolved(false);

        PipelineQSConfig qsConfig = new PipelineQSConfig();
        qsConfig.addDependency(connectorDependency);

        // Act
        List<QuickStartInstallStep> steps = quickStartV2Service.getConnectorResolutionSteps(qsConfig);

        // Assert: Different metadataId means no match - should generate CONNECTOR_CREATE step
        boolean hasConnectorCreateStep = steps.stream()
                .anyMatch(step -> step.getStepName() == QuickStartInstallStep.Step.CONNECTOR_CREATE);

        assertTrue(
                "Connectors with same display name but different metadataId should NOT match. " +
                        "This tests the scenario from SYN-20634 where the error message was confusing.",
                hasConnectorCreateStep
        );
    }

    private Connector createConnector(String id, String name, String metadataId) {
        Connector connector = new Connector();
        connector.setId(id);
        connector.setName(name);
        connector.setMetadataId(metadataId);
        connector.setStatus(ConnectorStatus.ACTIVE);
        return connector;
    }
}
