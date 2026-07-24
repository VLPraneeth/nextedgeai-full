package com.syncari.core.quickstart.v2;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.ExternalIdType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.Pair;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Transient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Accessors(chain = true)
@Data
public class PipelineQSConfig extends QSConfig {

    private boolean fieldsOnly;

    private List<Pipeline> pipelines = new ArrayList<>();
    private List<QSDependency> dependencies = new ArrayList<>();
    private List<Pair<SharableNode, MappingNode>> postProcessDependencies = new ArrayList<>();

    @Transient
    private Map<Pair<String, QSDependency.Type>, QSDependency> dependencyMap = new HashMap<>();

    public boolean isResolved(){
        return dependencies.stream().allMatch(d -> d.isResolved());
    }

    public Map<Pair<String, QSDependency.Type>, QSDependency> getDependencyMap(){
        if(dependencyMap.isEmpty()) {
            dependencyMap = dependencies.stream().collect(Collectors.toMap(d -> d.getKey(), d -> d));
        }
        return dependencyMap;
    }

    public void addDependency(QSDependency dependency){
        if(!dependencyMap.containsKey(dependency.getKey())){
            dependencies.add(dependency);
            dependencyMap.put(dependency.getKey(), dependency);
        }
    }

    public Object getResolvedValueByType(String id, QSDependency.Type type){
        // token fields can have static texts as well. resolve tokens in the template
        if(QSDependency.Type.Token.equals(type)){
            return resolveTokenString(id);
        }
        if(getDependencyMap().containsKey(Pair.of(id, type))){
            QSDependency dependency = getDependencyMap().get(Pair.of(id, type));
            if(dependency.isResolved()) {
                return dependency.getDestinationValue();
            } else {
                // use the source value if dependency is not resolved
                log.warn("Dependency for id {} and type {} is not resolved. Using source value", id, type.name());
                return dependency.getSourceValue();
            }
        }
        log.error("No dependency found for id {} and type {}", id, type.name());
        return null;
    }

    private String resolveTokenString(String srcToken) {
        List<String> extractedTokens = TokenHelper.extractTokensFromTemplate(srcToken);
        String resolvedToken = srcToken;
        for(String tok: extractedTokens){
            QSDependency dependency = getDependencyMap().get(Pair.of(tok, QSDependency.Type.Token));
            if(dependency != null && dependency.isResolved()) {
                resolvedToken = StringUtils.replace(resolvedToken, tok, dependency.getDestinationValue().toString());
            }
        }
        return resolvedToken;
    }

    public List<QSDependency> findUnresolvedConnectorDependency(){
        return findConnectorDependency(true);
    }

    // Find connector dependencies that needs user resolution
    public List<QSDependency> findNonSystemResolvedConnectorDependency() {
        return dependencies.stream()
                .filter(d -> QSDependency.Type.Connector.equals(d.getType()))
                .filter(d -> !d.isSystemResolved())
                .collect(Collectors.toList());
    }

    public List<QSDependency> findConnectorDependency(boolean unresolvedOnly){
        return dependencies.stream()
                .filter(d -> QSDependency.Type.Connector.equals(d.getType()))
                .filter(d -> !unresolvedOnly || !d.isResolved())
                .collect(Collectors.toList());
    }

    public List<QSDependency> findUnresolvedEntityOfConnector(String connectorId){
        return dependencies.stream()
                .filter(d -> QSDependency.Type.Entity.equals(d.getType()))
                .filter(d -> ((EntityDefinition) d.getSourceValue()).getConnectorId().equals(connectorId))
                .filter(d -> !d.isResolved())
                .collect(Collectors.toList());
    }
    public List<QSDependency> findNonSystemResolvedEntityOfConnector(String connectorId){
        return dependencies.stream()
                .filter(d -> QSDependency.Type.Entity.equals(d.getType()))
                .filter(d -> ((EntityDefinition) d.getSourceValue()).getConnectorId().equals(connectorId))
                .filter(d -> !d.isSystemResolved())
                .collect(Collectors.toList());
    }

    public List<QSDependency> findAllEntityOfConnectorDependencies(String connectorId){
        return dependencies.stream()
                .filter(d -> QSDependency.Type.Entity.equals(d.getType()))
                .filter(d -> ((EntityDefinition) d.getSourceValue()).getConnectorId().equals(connectorId))
                .collect(Collectors.toList());
    }

    public List<QSDependency> findUnresolvedAttributeDependencyOfEntity(String entityId){
        return dependencies.stream()
                .filter(d -> QSDependency.Type.Attribute.equals(d.getType()))
                .filter(d -> ((AttributeDefinition) d.getSourceValue()).getEntityId().equals(entityId))
                .filter(d -> !d.isResolved())
                .collect(Collectors.toList());
    }


    public List<QSDependency> findNonSystemResolvedAttributeDependencyOfEntity(String entityId){
        return dependencies.stream()
                .filter(d -> QSDependency.Type.Attribute.equals(d.getType()))
                .filter(d -> ((AttributeDefinition) d.getSourceValue()).getEntityId().equals(entityId))
                .filter(d -> !d.isSystemResolved())
                .collect(Collectors.toList());
    }

    public List<QSDependency> findAllAttributeDependenciesOfEntity(String entityId){
        return dependencies.stream()
                .filter(d -> QSDependency.Type.Attribute.equals(d.getType()))
                .filter(d -> ((AttributeDefinition) d.getSourceValue()).getEntityId().equals(entityId))
                .filter(d -> !(((AttributeDefinition) d.getSourceValue()).getDataType().getName().equals(ExternalIdType.NAME)))
                .collect(Collectors.toList());
    }
    
    public List<QSDependency> findAllExternalIdDependencies(){
      return dependencies.stream()
              .filter(d -> QSDependency.Type.Attribute.equals(d.getType()))
              .filter(d -> (((AttributeDefinition) d.getSourceValue()).getDataType().getName().equals(ExternalIdType.NAME)))
              .collect(Collectors.toList());
  }

    // find service credential dependencies that needs user resolution
    public List<QSDependency> findNonSystemResolvedServiceDependency(){
        return dependencies.stream()
                .filter(d -> QSDependency.Type.Service.equals(d.getType()))
                .filter(d -> !d.isSystemResolved())
                .collect(Collectors.toList());
    }

    public List<QSDependency> findUnresolvedServiceDependency(){
        return findServiceDependency(true);
    }

    public List<QSDependency> findServiceDependency(boolean unresolvedOnly){
        return dependencies.stream()
                .filter(d -> QSDependency.Type.Service.equals(d.getType()))
                .filter(d -> !unresolvedOnly || !d.isResolved())
                .collect(Collectors.toList());
    }

    // Find reference data dependency that needs user resolution
    public List<QSDependency> findNonSystemResolvedRefDataDependency(){
        return dependencies.stream()
                .filter(d -> QSDependency.Type.Reference.equals(d.getType()))
                .filter(d -> !d.isSystemResolved())
                .collect(Collectors.toList());
    }

    public List<QSDependency> findUnresolvedRefDataDependency(){
        return findRefDataDependency(true);
    }

    public List<QSDependency> findRefDataDependency(boolean unresolvedOnly){
        return dependencies.stream()
                .filter(d -> QSDependency.Type.Reference.equals(d.getType()))
                .filter(d -> !unresolvedOnly || !d.isResolved())
                .collect(Collectors.toList());
    }

    public List<QSDependency> findUnresolvedDependencyByType(QSDependency.Type type){
        return dependencies.stream()
                .filter(d -> type.equals(d.getType()))
                .filter(d -> !d.isResolved())
                .collect(Collectors.toList());
    }

    public Optional<QSDependency> findDependencyByIdAndType(String id, QSDependency.Type type){
        return Optional.ofNullable(getDependencyMap().get(Pair.of(id, type)));
    }

    public List<QSDependency> getAllResolvedValueByType(QSDependency.Type type){
        return dependencies.stream()
                .filter(d -> type.equals(d.getType()))
                .filter(d -> d.isResolved())
                .collect(Collectors.toList());
    }
}


