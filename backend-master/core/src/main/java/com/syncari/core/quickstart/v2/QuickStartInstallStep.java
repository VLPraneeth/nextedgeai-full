package com.syncari.core.quickstart.v2;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.ReferenceDataMeta;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
public class QuickStartInstallStep {

    String quickStartInstallId;
    Step stepName;
    List<UnresolvedItem> values;

    public enum Step{
        OVERVIEW,
        MERGE_SETTINGS,
        CONNECTOR_CREATE,
        CONNECTOR_SELECT,
        ENTITIES,
        SERVICE_CREATE,
        SERVICE_SELECT,
        REF_DATA_CREATE,
        REF_DATA_SELECT,
        REVIEW,
        CONFIRM
    }

    public static QuickStartInstallStep getConnectorStep(List<QSDependency> connectorDependencies, boolean hasMultiple){
        List<UnresolvedItem> unresolvedConnectors = connectorDependencies.stream().map(c ->
            new UnresolvedItem().setDependency(c)
        ).collect(Collectors.toList());
        return new QuickStartInstallStep()
                .setStepName(!hasMultiple ? Step.CONNECTOR_CREATE : Step.CONNECTOR_SELECT)
                .setValues(unresolvedConnectors);
    }

    public static QuickStartInstallStep getEntitiesStep(List<QSDependency> entityDependencies, QSDependency parentDependency, List<QSDependency> dependencies){
        List<UnresolvedItem> unresolvedEntities = entityDependencies.stream().map(e -> {
            List<UnresolvedItem> unresolvedAttributes = ((EntityDefinition)e.getSourceValue()).getAttributes().stream().map(a -> {
                var attributeDependency = dependencies.stream().filter(dependency -> {
                    return dependency.getType() == QSDependency.Type.Attribute && ((AttributeDefinition)dependency.getSourceValue()).getId().equalsIgnoreCase(a.getId());
                }).findFirst();
                if (attributeDependency.isPresent()) {
                    return new UnresolvedItem().setDependency(attributeDependency.get());
                }
                return null;
            }).filter(d -> d != null).collect(Collectors.toList());
            return new UnresolvedItem()
                    .setParent(parentDependency)
                    .setDependency(e).setChildren(unresolvedAttributes);
        }).collect(Collectors.toList());
        return new QuickStartInstallStep()
                .setStepName(Step.ENTITIES)
                .setValues(unresolvedEntities);
    }

    public static QuickStartInstallStep getRefDataStep(List<QSDependency> refDataList, boolean hasMultiple){
        List<UnresolvedItem> unresolvedRefData = refDataList.stream().map(c ->
            new UnresolvedItem().setDependency(c)
        ).collect(Collectors.toList());
        return new QuickStartInstallStep()
                .setStepName(!hasMultiple ? Step.REF_DATA_CREATE : Step.REF_DATA_SELECT)
                .setValues(unresolvedRefData);
    }

    public static QuickStartInstallStep getServiceCredStep(List<QSDependency> serviceCredList, boolean hasMultiple){
        List<UnresolvedItem> unresolvedRefData = serviceCredList.stream().map(c ->
            new UnresolvedItem().setDependency(c)
        ).collect(Collectors.toList());
        return new QuickStartInstallStep()
                .setStepName(!hasMultiple ? Step.SERVICE_CREATE : Step.SERVICE_SELECT)
                .setValues(unresolvedRefData);
    }

    public static QuickStartInstallStep getMergeSettingsStep(PipelineQSConfig config){
        return new QuickStartInstallStep()
                .setStepName(Step.MERGE_SETTINGS);
    }
}
