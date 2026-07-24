package com.syncari.core.quickstart.v2.dependency;

import com.syncari.core.model.AdvancedDedupeConfig;
import com.syncari.core.model.Connector;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.DatAuthorityStrategy;
import com.syncari.core.model.DataAuthority;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FieldMergePolicy;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.misc.sharable.SharableCoreEntityNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CoreEntityNodeDependencyGenerator implements DependencyService {

    @Autowired
    ConnectorService connectorService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    DefaultPredicateDependencyGenerator defaultPredicateDependencyGenerator;

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        SharableCoreEntityNodeConfig nodeConfig = node.getTypedConfiguration();

        // Dependency list
        // 1: Referenced core Entity
        // 2: Connector from Data Authority
        // 3: Dedupe config
        QSDependency entityDep = DependencyUtil.getEntityDependency(nodeConfig.getEntityDefinition());
        qsConfig.addDependency(entityDep);

        // Also add required fields of the entity as dependencies too
        EntityDefinition syncariEntity = schemaService.getEntity(nodeConfig.getEntityDefinition().getId());
        syncariEntity.getIdField().ifPresent(idField -> qsConfig.addDependency(DependencyUtil.getAttributeDependency(idField)));
        syncariEntity.getWatermarkField().ifPresent(wmField -> qsConfig.addDependency(DependencyUtil.getAttributeDependency(wmField)));
        // Adding syncari entity attributes
        syncariEntity.getAttributes().forEach(a -> {
            if (!a.isSystem() && !a.isSyncariDefined() && !a.isIdField() && !a.isWatermarkField()){
                qsConfig.addDependency(DependencyUtil.getAttributeDependency(a));
            }
        });

        // add dependencies from data authority
        DataAuthority dataAuthority = nodeConfig.getDataAuthority();
        if(DatAuthorityStrategy.SELECTED_CONNECTOR.equals(dataAuthority.getDatAuthorityStrategy())){
            String connectorId = dataAuthority.getDataAuthorityConfiguration().get("connectorId").toString();
            Connector conn = context.getConnector(connectorId).orElseThrow();
            qsConfig.addDependency(DependencyUtil.getConnectorDependency(conn));
        }

        // add dependencies from dedupe merge config
        AdvancedDedupeConfig dedupeConfig = nodeConfig.getAdvancedDedupeConfig();
        if(dedupeConfig != null) {
        	// 1. dependencies from skipWhen
            Expression skipWhenCriteria = dedupeConfig.skipWhenCriteria();
            if(skipWhenCriteria != null) {
            	ExpressionDependencyVisitor visitor = new ExpressionDependencyVisitor(defaultPredicateDependencyGenerator, context);
            	skipWhenCriteria.accept(visitor);
            }
            
         // 2. dependencies from dupesCriteria
            List<Expression> dupesCriteria = dedupeConfig.findDupesCriteria();
            ExpressionDependencyVisitor visitor = new ExpressionDependencyVisitor(defaultPredicateDependencyGenerator, context);
            dupesCriteria.forEach(e -> {
                e.accept(visitor);
            });

            // 3. dependencies from selectWinner
            List<Expression> winnerSelectionPredicates = dedupeConfig.getWinnerSelectionPredicates();
            winnerSelectionPredicates.forEach(e -> {
                e.accept(visitor);
            });

            // 4. dependencies from fieldMergePolicies
            List<FieldMergePolicy> fieldMergePolicies = dedupeConfig.getFieldMergePolicies();
            fieldMergePolicies.forEach(mergePolicy -> {
                mergePolicy.getExpresson().accept(visitor);
            });
        }
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        SharableNode node = context.getCurrentNode();
        SharableCoreEntityNodeConfig nodeConfig = node.getTypedConfiguration();
        var srcEntityRef = nodeConfig.getEntityDefinition();
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        EntityDefinition destEntityRef = (EntityDefinition) qsConfig.getResolvedValueByType(srcEntityRef.getId(), QSDependency.Type.Entity);

        DataAuthority dataAuthority = nodeConfig.getDataAuthority();
        if(DatAuthorityStrategy.SELECTED_CONNECTOR.equals(dataAuthority.getDatAuthorityStrategy())){
            String srcConnectorId = dataAuthority.getDataAuthorityConfiguration().get("connectorId").toString();
            Connector destConnRef = (Connector) qsConfig.getResolvedValueByType(srcConnectorId, QSDependency.Type.Connector);
            if(destConnRef != null) {
                dataAuthority = DataAuthority.selectedConnector(destConnRef.getId());
            }
        }

        // resolve dedupe merge config
        AdvancedDedupeConfig dedupeConfig = nodeConfig.getAdvancedDedupeConfig();
        if(dedupeConfig != null) {

            // 1. resolved dependencies from dupesCriteria
            dedupeConfig.setFindDupes(getResolvedFindDupes(dedupeConfig, context));

            // 2. resolved dependencies from selectWinner
            dedupeConfig.setSelectWinner(getResolvedSelectWinner(dedupeConfig, context));

            // 3. resolved dependencies from fieldMergePolicies
            dedupeConfig.setFieldMergePolicies(getResolvedFieldMergePolicies(dedupeConfig, context));
            
            //4. resolved dependencies from skipWhenCriteria
            dedupeConfig.setSkipWhen(getResolvedSkipWhen(dedupeConfig, context));
        }

        CoreEntityNodeConfig coreNodeConfig = new CoreEntityNodeConfig()
                .setEntityDefinition(destEntityRef)
                .setAdvancedDedupeConfig(dedupeConfig)
                .setDataAuthority(dataAuthority);

        return new MappingNode()
                .setConfiguration(coreNodeConfig)
                .setApiName(destEntityRef.getApiName())
                .setName(destEntityRef.getDisplayName())
                .setScope(Scope.ENTITY);

    }
    
    private Map<String, Object> getResolvedSkipWhen(AdvancedDedupeConfig dedupeConfig, QuickStartContext context){
        ExpressionDependencyResolver resolver = new ExpressionDependencyResolver(context);
        Map<String, Object> skipWhenConfigMap = new HashMap<>(
				dedupeConfig.getSkipWhen() != null ? dedupeConfig.getSkipWhen() : Map.of());
        var resolvedPredicate = resolver.fromMap(skipWhenConfigMap);
        return resolvedPredicate;
    }

    private Map<String, Object> getResolvedFindDupes(AdvancedDedupeConfig dedupeConfig, QuickStartContext context){
        ExpressionDependencyResolver resolver = new ExpressionDependencyResolver(context);
        Map<String, Object> findDupesConfigMap = new HashMap<>(dedupeConfig.getFindDupes());
        List<Map<String, Object>> compositeValues = (List<Map<String, Object>>) findDupesConfigMap.getOrDefault("compositeValues", List.of());
        List<Map<String, Object>> updatedCompositeValues = new ArrayList<>();
        compositeValues.forEach(p -> {
            Map<String, Object> findDupePredicate = new HashMap<>((Map<String, Object>) p.getOrDefault("findDupesPredicate", Map.of()));
            Map<String, Object> predicate = (Map<String, Object>) findDupePredicate.getOrDefault("value", Map.of());
            var resolvedPredicate = resolver.fromMap(predicate);
            findDupePredicate.put("value", resolvedPredicate);
            p.put("findDupesPredicate", findDupePredicate);
            updatedCompositeValues.add(p);
        });
        findDupesConfigMap.put("compositeValues", updatedCompositeValues);
        return findDupesConfigMap;
    }

    private Map<String, Object> getResolvedSelectWinner(AdvancedDedupeConfig dedupeConfig, QuickStartContext context){
        ExpressionDependencyResolver resolver = new ExpressionDependencyResolver(context);
        Map<String, Object> selectWinnerConfigMap = new HashMap<>(dedupeConfig.getSelectWinner());
        List<Map<String, Object>> compositeValues = (List<Map<String, Object>>) selectWinnerConfigMap.getOrDefault("compositeValues", List.of());
        List<Map<String, Object>> updatedCompositeValues = new ArrayList<>();
        compositeValues.forEach(p -> {
            Map<String, Object> findDupePredicate = new HashMap<>((Map<String, Object>) p.getOrDefault("winnerSelectionPredicate", Map.of()));
            Map<String, Object> predicate = (Map<String, Object>) findDupePredicate.getOrDefault("value", Map.of());
            var resolvedPredicate = resolver.fromMap(predicate);
            findDupePredicate.put("value", resolvedPredicate);
            p.put("winnerSelectionPredicate", findDupePredicate);
            updatedCompositeValues.add(p);
        });
        selectWinnerConfigMap.put("compositeValues", updatedCompositeValues);
        return selectWinnerConfigMap;
    }

    private Map<String, Object> getResolvedFieldMergePolicies(AdvancedDedupeConfig dedupeConfig, QuickStartContext context){
        ExpressionDependencyResolver resolver = new ExpressionDependencyResolver(context);
        Map<String, Object> selectWinnerConfigMap = new HashMap<>((Map<String, Object>)dedupeConfig.getConfigMap().get("fieldMergePolicies"));
        List<Map<String, Object>> compositeValues = (List<Map<String, Object>>) selectWinnerConfigMap.getOrDefault("compositeValues", List.of());
        List<Map<String, Object>> updatedCompositeValues = new ArrayList<>();
        compositeValues.forEach(p -> {
            Map<String, Object> findDupePredicate = new HashMap<>((Map<String, Object>) p.getOrDefault("fieldMergePredicate", Map.of()));
            Map<String, Object> predicate = (Map<String, Object>) findDupePredicate.getOrDefault("value", Map.of());
            var resolvedPredicate = resolver.fromMap(predicate);
            findDupePredicate.put("value", resolvedPredicate);
            p.put("fieldMergePredicate", findDupePredicate);
            updatedCompositeValues.add(p);
        });
        selectWinnerConfigMap.put("compositeValues", updatedCompositeValues);
        return selectWinnerConfigMap;
    }

}
