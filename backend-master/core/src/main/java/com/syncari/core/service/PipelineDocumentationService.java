package com.syncari.core.service;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.*;
import com.syncari.core.model.llm.LLMContext;
import com.syncari.core.model.llm.LLMResponse;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.service.llm.LLMService;
import com.syncari.utils.Pair;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

import static com.syncari.core.model.util.MappingNodeType.*;

@Component
public class PipelineDocumentationService {
    @Autowired
    MappingGraphService mappingGraphService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    LLMService llmService;

    private static final String SYSTEM_PROMPT = "You are a document writer who analyzes a hub and spoke data pipeline and writes an overview, " +
            "a pipeline step summary, business value in markdown format. The syncari entity in the middle provides a 360 view of data.The input will have an entity pipeline " +
            "and a set of field pipelines, separated by dashed lines. Keep business value to two sentences." +
            "Emit only markdown code.Do not add the markdown format specifier.Specify the entities involved in the overview and only add a general summary" +
            " of field pipelines.For every field pipeline, emit only one bullet point.";
    private static final String USER_PROMPT =
            "{{entityPipeline}}" +
                    "----------------------\n" +
                    "{%for fp in fieldPipelines%}" +
                    "{{fp}}\n" +
                    "-----------------------" +
                    "{%endfor%}";

    public Documentation generateDocumentation(String syncariEntityId, DraftStatus status) {
        PipelineSummary summary = generatePipelineSummary(syncariEntityId, status);
        final LLMContext llmContext = new LLMContext()
                .add("entityPipeline", summary.epDot)
                .add("fieldPipelines", summary.fpDots);
        final LLMResponse pipelineDocumentation = llmService.generate(SYSTEM_PROMPT, USER_PROMPT, llmContext);
        return new Documentation().setContent(pipelineDocumentation.getResponse());
    }

    private PipelineSummary generatePipelineSummary(String syncariEntityId, DraftStatus status) {
        Map<String, EntityDefinition> entityDefinitionMap = new HashMap<>();
        Map<String, Connector> connectorMap = new HashMap<>();

        Function<MappingNode, String> defaultVisitor = node -> String.format("%s (%s)", node.getName(), node.getApiName());
        Map<MappingNodeType, Function<MappingNode, String>> nodeVisitors = Map.of(
                ATTRIBUTE_SOURCE, node -> getAttrSourceString(entityDefinitionMap, connectorMap, node),
                ATTRIBUTE_SINK, node -> getAttrDestString(entityDefinitionMap, connectorMap, node),
                ENTITY_SOURCE, node -> getEntitySourceString(entityDefinitionMap, connectorMap, node),
                ENTITY_SINK, node -> getEntityDestString(entityDefinitionMap, connectorMap, node),
                ACTION, node -> String.format("%s (Action)", node.getName()),
                FUNCTION, node -> getFunctionString(node),
                CORE_ENTITY, node -> getCoreEntityString(entityDefinitionMap, node),
                CORE_ATTRIBUTE, node -> getCoreAttrString(entityDefinitionMap, node)
        );
        PipelineSummary summary = new PipelineSummary();
        final Optional<Pair<MappingGraph, List<MappingGraph>>> pipeline = fetchFullGraph(syncariEntityId, status);
        pipeline.ifPresent(p -> {
            summary.setEpDot(generateDot(defaultVisitor, nodeVisitors, p.getX()));
            p.getY().forEach(m -> summary.addFPDot(generateDot(defaultVisitor, nodeVisitors, m)));
        });
        return summary;
    }

    private String getCoreAttrString(Map<String, EntityDefinition> entityDefinitionMap, MappingNode node) {
        CoreAttributeNodeConfig c = node.getTypedConfiguration();
        final AttributeDefinition attributeDefinition = c.getAttributeDefinition();
        final EntityDefinition entityDef = findEntityDefinition(attributeDefinition.getEntityId(), entityDefinitionMap);
        return String.format("syncari.%s.%s", entityDef.getDisplayName(), attributeDefinition.getDisplayName());
    }

    private String getCoreEntityString(Map<String, EntityDefinition> entityDefinitionMap, MappingNode node) {
        CoreEntityNodeConfig c = node.getTypedConfiguration();
        final EntityDefinition entityDef = findEntityDefinition(c.getEntityDefinition().getId(), entityDefinitionMap);
        return String.format("syncari.%s", entityDef.getDisplayName());
    }

    private static String getFunctionString(MappingNode node) {
        final boolean isFilter = "filter".equals(node.getApiName()) || "filterOnField".equals(node.getApiName());
        if (isFilter) {
            return String.format("%s (Decision)", node.getName());
        } else {
            return String.format("%s (%s)", node.getName(), node.getApiName());
        }
    }

    private String getEntityDestString(Map<String, EntityDefinition> entityDefinitionMap, Map<String, Connector> connectorMap, MappingNode node) {
        EntitySinkNodeConfig c = node.getTypedConfiguration();
        final EntityDefinition entityDef = findEntityDefinition(c.getEntityDefinition().getId(), entityDefinitionMap);
        final Connector connector = findConnector(entityDef.getConnectorId(), connectorMap);
        return String.format("%s.%s", connector.getName(), entityDef.getDisplayName());
    }

    private String getEntitySourceString(Map<String, EntityDefinition> entityDefinitionMap, Map<String, Connector> connectorMap, MappingNode node) {
        EntitySourceNodeConfig c = node.getTypedConfiguration();
        final EntityDefinition entityDef = findEntityDefinition(c.getEntityDefinition().getId(), entityDefinitionMap);
        final Connector connector = findConnector(entityDef.getConnectorId(), connectorMap);
        return String.format("%s.%s", connector.getName(), entityDef.getDisplayName());
    }

    private String getAttrDestString(Map<String, EntityDefinition> entityDefinitionMap, Map<String, Connector> connectorMap, MappingNode node) {
        AttributeSinkNodeConfig c = node.getTypedConfiguration();
        final AttributeDefinition attributeDefinition = c.getAttributeDefinition();
        final EntityDefinition entityDef = findEntityDefinition(attributeDefinition.getEntityId(), entityDefinitionMap);
        final Connector connector = findConnector(entityDef.getConnectorId(), connectorMap);
        return String.format("%s.%s.%s", connector.getName(), entityDef.getDisplayName(), attributeDefinition.getDisplayName());
    }

    private String getAttrSourceString(Map<String, EntityDefinition> entityDefinitionMap, Map<String, Connector> connectorMap, MappingNode node) {
        AttributeSourceNodeConfig c = node.getTypedConfiguration();
        final AttributeDefinition attributeDefinition = c.getAttributeDefinition();
        final EntityDefinition entityDef = findEntityDefinition(attributeDefinition.getEntityId(), entityDefinitionMap);
        final Connector connector = findConnector(entityDef.getConnectorId(), connectorMap);
        return String.format("%s.%s.%s", connector.getName(), entityDef.getDisplayName(), attributeDefinition.getDisplayName());
    }

    private Optional<Pair<MappingGraph, List<MappingGraph>>> fetchFullGraph(String syncariEntityId, DraftStatus status) {
        switch (status) {
            case NEW:
                final Optional<MappingGraph> draftEP = mappingGraphService.retrieveDraftEntityGraph(syncariEntityId);
                List<MappingGraph> draftFPs = draftEP.map(p ->
                        mappingGraphService.retrieveDraftAttributeGraphs(p.getId())
                ).orElse(List.of());
                return draftEP.map(e -> Pair.of(e, draftFPs));
            case APPROVED:
                final Optional<MappingGraph> approvedEP = mappingGraphService.retrieveApprovedEntityGraph(syncariEntityId);
                List<MappingGraph> approvedFPs = approvedEP.map(p ->
                        mappingGraphService.retrieveApprovedAttributeGraphs(p.getId())
                ).orElse(List.of());
                return approvedEP.map(e -> Pair.of(e, approvedFPs));
            default:
                return Optional.empty();
        }
    }

    private String generateDot(Function<MappingNode, String> defaultVisitor, Map<MappingNodeType, Function<MappingNode, String>> nodeVisitors, MappingGraph p) {
        StringBuilder dot = new StringBuilder();
        p.getEdges().forEach(e -> {
            final String leftSide = nodeVisitors.getOrDefault(e.getSourceStage().getType(), defaultVisitor).apply(e.getSourceStage());
            final String rightSide = nodeVisitors.getOrDefault(e.getDestinationStage().getType(), defaultVisitor).apply(e.getDestinationStage());
            dot.append(leftSide + " -> " + rightSide);
            dot.append(",");
        });
        return dot.toString();
    }

    private Connector findConnector(String id, Map<String, Connector> cache) {
        if (!cache.containsKey(id)) {
            connectorService.find(id).ifPresent(c -> {
                cache.put(id, c);
            });
        }
        return cache.get(id);
    }

    private EntityDefinition findEntityDefinition(String id, Map<String, EntityDefinition> cache) {
        if (!cache.containsKey(id)) {
            schemaService.findEntity(id).ifPresent(e -> {
                cache.put(id, e);
            });
        }
        return cache.get(id);

    }
}

@Data
@Accessors(chain = true)
class PipelineSummary {
    String epDot;
    List<String> fpDots = new ArrayList<>();
    String mergeDetails;

    public PipelineSummary addFPDot(String fpDot) {
        fpDots.add(fpDot);
        return this;
    }
}