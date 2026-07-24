package com.syncari.core.quickstart.v2;

import com.syncari.utils.KeyValue;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class QSInstallSeed {

    public static KeyValue toQSInstallConfigDTO(QSInstallWizardConfig qsInstallWizardConfig) {
        return KeyValue.of(
                "id", qsInstallWizardConfig.getId(),
                "displayName", qsInstallWizardConfig.getDisplayName(),
                "requiredSynapses", qsInstallWizardConfig.getRequiredSynapses(),
                "config", KeyValue.of(
                        "configuration", qsInstallWizardConfig.getConfiguration(),
                        "renderer", KeyValue.of(
                                "renderType", "fullContentPanel",
                                "steps", qsInstallWizardConfig.getSteps(),
                                "title", qsInstallWizardConfig.getTitle()
                        )
                )
        );
    }

    public static KeyValue getQuickStartTitle(String title) {
        return KeyValue.of(
                "renderType", "displayText",
                "id", "quickStartTitle",
                "name", "quickStartTitle",
                "defaultValue", "dummy",
                "textProps", KeyValue.of(
                        "children", title,
                        "weight", "semibold",
                        "color", "gray-900",
                        "size", "lg"
                )
        );
    }

    public static KeyValue getMergeSettings() {
        return KeyValue.of(
                "renderType", "mergeOptions",
                "id", "selectMergeOptions",
                "name", "selectMergeOptions"
                /*"defaultValue", "dummy",
                "textProps", KeyValue.of(
                        "children", title,
                        "weight", "semibold",
                        "color", "gray-900",
                        "size", "lg"
                )*/
        );
    }

    public static KeyValue getMergeOptionsTitle() {
        return KeyValue.of(
                "renderType", "displayText",
                "id", "mergeOptionsTitle",
                "name", "mergeOptionsTitle",
                "defaultValue", "dummy",
                "textProps", KeyValue.of(
                        "children", "Merge with existing pipelines",
                        "weight", "semibold",
                        "color", "gray-900",
                        "size", "lg"
                )
        );
    }

    public static KeyValue getMergeOptionsDescription() {
        return KeyValue.of(
                "renderType", "displayText",
                "id", "mergeOptionsDescription",
                "name", "mergeOptionsDescription",
                "defaultValue", "dummy",
                "textProps", KeyValue.of(
                        "children", "Select how you would like to merge this Quick Start with existing pipelines. Quick Start nodes can replace or add to existing paths on the source and destinations sides of pipelines.",
                        "weight", "regular",
                        "color", "gray-850",
                        "size", "md")
                );
    }
    public static KeyValue getPublishedByText(String userName, String orgName) {
        String publishedByText = String.format("Published by %s %s",
                StringUtils.isBlank(userName) ? "Unknown User" : userName,
                StringUtils.isBlank(orgName) ? "" : "(" + orgName + ")"
        );
        return KeyValue.of(
                "renderType", "displayText",
                "id", "publishedByText",
                "name", "publishedByText",
                "textProps", KeyValue.of(
                        "children", publishedByText,
                        "as", "span",
                        "size", "md",
                        "color", "gray-750",
                        "beDangerous", true
                )
        );
    }


    public static KeyValue getRequiredSynapses(List<String> requiredSynapses) {
        return KeyValue.of(
                "renderType", "displayText",
                "id", "requiredSynapsesText",
                "name", "requiredSynapsesText",
                "textProps", KeyValue.of(
                        "children", String.format("Requires %s", String.join(", ", requiredSynapses)),
                        "as", "span",
                        "size", "md",
                        "color", "gray-750",
                        "beDangerous", true
                )
        );
    }

    public static KeyValue getOverviewDescription(String description) {
        return KeyValue.of(
                "renderType", "displayText",
                "id", "overviewDescription",
                "name", "overviewDescription",
                "textProps", KeyValue.of(
                        "children", description,
                        "as", "p",
                        "size", "md",
                        "color", "black",
                        "beDangerous", true
                )
        );
    }

    public static KeyValue getResolveSynapse(String synapseName, String synapseType, String connectorMetadataName) {
        var id = String.format("resolveSynapses%s", synapseName);
        return KeyValue.of(
                "renderType", "quickStartInstallErrorResolution",
                "id", id,
                "name", id,
                "resolutionData", KeyValue.of(
                        "type", "create_synapse",
                        "synapseName", synapseName,
                        "synapseType", synapseType,
                        "connectorMetadataName", connectorMetadataName
                )
        );
    }

    public static KeyValue getSynapseResolved(String resolvedId, String dependencyName, String dependencyType) {
        return KeyValue.of(
                "renderType", "quickStartInstallErrorResolution",
                "id", resolvedId,
                "name", resolvedId,
                "resolutionData", KeyValue.of(
                        "type", "issue_resolved",
                        "successTitle", String.format("%s %s successfully verified", dependencyName, dependencyType),
                        "successMessage", String.format("We successfully verified %s %s, and you can now continue to the next step.", dependencyName, dependencyType)
                )
        );
    }


    public static KeyValue getResolveMatchType(List<KeyValue> matches, String type) {
        var id = String.format("match%s", type);
        return KeyValue.of(
                "renderType", "quickStartInstallErrorResolution",
                "id", id,
                "name", id ,
                "resolutionData", KeyValue.of(
                        "title", String.format("Select %s", type),
                        "description", String.format("Select which %s will be used during this process below.", type),
                        "type", "select_matches",
                        "matches", matches
                )
        );
    }

    public static KeyValue getErrorResolutionForUnresolvedEntity(List<KeyValue> matches, String type) {
        var id = String.format("match%s", type);
        return KeyValue.of(
                "renderType", "quickStartInstallErrorResolution",
                "id", id,
                "name", id ,
                "resolutionData", KeyValue.of(
                        "title", String.format("Select %s", type),
                        "description", String.format("Select which %s and its attributes will be used during this process below.", type),
                        "type", "create_entities",
                        "matches", matches
                )
        );
    }

    public static KeyValue getResolveMatchSynapseEntityAndFields(String synapseName, String name, List<KeyValue> items, KeyValue defaultValue) {
        var synapseEntity = new KeyValue(
                "renderType", "schemaMatcher",
                "id", name,
                "name", name,
                "synapseName", synapseName,
                "items", items
        );
        if (defaultValue != null) {
            synapseEntity.put("defaultValue", defaultValue);
        }
        return synapseEntity;
    }

    public static KeyValue getResolveServiceCredential(String serviceProvider, String name) {
        return KeyValue.of(
                "renderType", "quickStartInstallErrorResolution",
                "id", name,
                "name", name,
                "resolutionData", KeyValue.of(
                        "type", "service_credentials",
                        "serviceProvider", StringUtils.capitalize(serviceProvider)
                )
        );
    }

    public static KeyValue getResolveReferenceData(String title, List<String> columnNames) {
        return KeyValue.of(
                "renderType", "quickStartInstallErrorResolution",
                "id", "resolveReferenceData",
                "name", "resolveReferenceData",
                "resolutionData", KeyValue.of(
                        "type", "reference_data",
                        "datasetTitle", title,
                        "columns", columnNames
                )
        );
    }

    public static KeyValue getQuickStartReviewInfo() {
        return KeyValue.of(
                "datatype", "infoBox",
                "id", "quickStartReview",
                "name", "quickStartReview",
                "message", "Review the changes this Quick Start will make",
                "description", "Carefully review all of the changes that will be made to your instance below before running the Quick Start.",
                "showIcon", false
        );
    }


    public static KeyValue getQuickStartReviewDraftWarning() {
        return KeyValue.of(
                "datatype", "infoBox",
                "id", "quickStartReviewDraftWarning",
                "name", "quickStartReviewDraftWarning",
                "message", "Existing pipeline drafts will be discarded",
                "description", "Any existing pipeline drafts will be replaced by the newly created pipelines. Published pipelines will not be affected.",
                "type", "warning",
                "showIcon", false
        );
    }

    public static KeyValue getInstallReviewItems(List<KeyValue> reviewItems) {
        return KeyValue.of(
                "renderType", "quickStartInstallReview",
                "id", "quickStartInstallReview",
                "name", "quickStartInstallReview",
                "message", "Review the changes this Quick Start will make",
                "reviewItems", reviewItems
        );
    }

    public static KeyValue getConfirmation(String postInstallMessage) {
        return KeyValue.of(
                "renderType", "quickStartPostInstallation",
                "id", "confirmQuickStart",
                "postInstallMessage", postInstallMessage,
                "name", "confirmQuickStart"
        );
    }

    public static KeyValue getOverviewStep() {
        return KeyValue.of(
                "fields", List.of(
                        "quickStartTitle",
                        "publishedByText",
                        "requiredSynapsesText",
                        "overviewDescription"
                ),
                "layout", KeyValue.of(
                        "type", "stack",
                        "className", "synri-skull-stack-container-md"
                ),
                "stepName", "Overview"
        );
    }

    public static KeyValue getMergeSettingsStep() {
        return KeyValue.of(
                "fields", List.of(
                        "mergeOptionsTitle",
                        "mergeOptionsDescription",
                        "selectMergeOptions"
                ),
                "layout", KeyValue.of(
                        "type", "stack",
                        "className", "synri-skull-stack-container-md"
                ),
                "stepName", "Merge Settings"
        );
    }

    public static KeyValue getResolveSynapseStep(List<String> fields, String synapseName) {
        return KeyValue.of(
                "dynamicSteps", true,
                "fields", fields,
                "layout", KeyValue.of(
                        "type", "stack",
                        "className", "synri-skull-stack-container-md"),
                "stepName", String.format("Resolve %s synapse", synapseName)
        );
    }

    public static KeyValue getMatchSynapsesStep(String type) {
        return KeyValue.of(
                "fields", List.of(String.format("match%s", type)),
                "dynamicSteps", true,
                "layout", KeyValue.of(
                        "type", "stack",
                        "className", "synri-skull-stack-container-md"
                ),
                "stepName", String.format("Match %s", type)
        );
    }

    public static KeyValue getMatchSynapseEntityAndFieldStep(String synapseName, String fieldName) {
        return KeyValue.of(
                "fields", List.of(fieldName),
                "dynamicSteps", true,
                "layout", KeyValue.of(
                        "type", "stack",
                        "className", "synri-skull-stack-container-md"
                ),
                "stepName", String.format("Match %s entity", synapseName)
        );
    }

    public static KeyValue getResolveServiceCredentialsStep(List<String> fields, String name) {
        return KeyValue.of(
                "dynamicSteps", true,
                "fields", fields,
                "layout", KeyValue.of(
                        "type", "stack",
                        "className", "synri-skull-stack-container-md"
                ),
                "stepName", String.format("Resolve %s service credentials", name)
        );
    }

    public static KeyValue getResolveReferenceDataStep(List<String> fields, String name) {
        return KeyValue.of(
                "dynamicSteps", true,
                "fields", fields,
                "layout", KeyValue.of(
                        "type", "stack",
                        "className", "synri-skull-stack-container-md"
                ),
                "stepName", String.format("Resolve %s reference data", name)
        );
    }

    public static KeyValue getReviewStep() {
        return KeyValue.of(
                "dynamicSteps", true,
                "applyStep", true,
                "fields", List.of("quickStartReview", "quickStartReviewDraftWarning", "quickStartInstallReview"),
                "layout", KeyValue.of(
                        "type", "stack",
                        "className", "synri-skull-stack-container-md"
                ),
                "stepName", "Review",
                "next", KeyValue.of(
                        "buttonText", "Confirm and run"
                )
        );
    }

    public static KeyValue getConfirmStep() {
        return KeyValue.of(
                "closeStep", true,
                "fields", List.of("confirmQuickStart"),
                "layout", KeyValue.of(
                        "type", "stack",
                        "className", "synri-skull-stack-container-md"
                ),
                "stepName", "Confirm"
        );
    }

    public static KeyValue getSynapseUsedReviewStep(List<String> synapseDisplayNames) {
        if (synapseDisplayNames != null) {
            return KeyValue.of(
                    "label", "Synapses used",
                    "count", synapseDisplayNames.size(),
                    "renderInfo", KeyValue.of(
                            "type", "stringList",
                            "data", synapseDisplayNames
                    )
            );
        }
        return null;
    }

    public static KeyValue getPipelinesReplacedReviewStep(List<KeyValue> data) {
        return KeyValue.of(
                "label", "Pipelines will be replaced",
                "count", 2,
                "renderInfo", KeyValue.of(
                        "type", "replacedByPipelines",
                        "data", data != null ? data : List.of(
                                KeyValue.of(
                                        "id", "123",
                                        "apiName", "account",
                                        "displayName", "Account",
                                        "replacementFields", List.of(
                                                KeyValue.of(
                                                        "field", KeyValue.of(
                                                                "id", "60e49df87c987b42ccd83c1c",
                                                                "apiName", "ParentId",
                                                                "displayName", "Parent Account ID",
                                                                "dataType", "reference"
                                                        ),
                                                        "replacementField", KeyValue.of(
                                                                "id", "60e49df87c987b42ccd83c1c",
                                                                "apiName", "ParentId",
                                                                "displayName", "Parent Account ID",
                                                                "dataType", "reference"
                                                        )
                                                ),
                                                KeyValue.of(
                                                        "field", KeyValue.of(
                                                                "id", "60e49df87c987b42ccd83c29",
                                                                "apiName", "PhotoUrl",
                                                                "displayName", "Photo URL",
                                                                "dataType", "url"
                                                        ),
                                                        "replacementField", KeyValue.of(
                                                                "id", "60e49df87c987b42ccd83c29",
                                                                "apiName", "PhotoUrl",
                                                                "displayName", "Photo URL",
                                                                "dataType", "url"
                                                        )
                                                ),
                                                KeyValue.of(
                                                        "field", KeyValue.of(
                                                                "id", "60e49df87c987b42ccd83c3b",
                                                                "apiName", "Score",
                                                                "displayName", "Score",
                                                                "dataType", "double"
                                                        ),
                                                        "replacementField", KeyValue.of(
                                                                "id", "60e49df87c987b42ccd83c3b",
                                                                "apiName", "Score",
                                                                "displayName", "Score",
                                                                "dataType", "double"
                                                        )
                                                ),
                                                KeyValue.of(
                                                        "field", KeyValue.of(
                                                                "id", "60e49df87c987b42ccd83c23",
                                                                "apiName", "ShippingCity",
                                                                "displayName", "Shipping City",
                                                                "dataType", "string"
                                                        ),
                                                        "replacementField", KeyValue.of(
                                                                "id", "60e49df87c987b42ccd83c23",
                                                                "apiName", "ShippingCity",
                                                                "displayName", "Shipping City",
                                                                "dataType", "string"
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    public static KeyValue getFieldPipelinesCreatedReviewStep() {
        return KeyValue.of(
                "label", "Pipelines created",
                "count", 4,
                "renderInfo", KeyValue.of(
                        "type", "fieldPipelines",
                        "data", List.of(
                                KeyValue.of(
                                        "id", "60e49df87c987b42ccd83c17",
                                        "apiName", "AboutUs",
                                        "displayName", "About Us",
                                        "dataType", "string"
                                ),
                                KeyValue.of(
                                        "id", "60e49df87c987b42ccd83c2c",
                                        "apiName", "Description",
                                        "displayName", "Account Description",
                                        "dataType", "textarea"

                                ),
                                KeyValue.of(
                                        "id", "60e49df87c987b42ccd83c16",
                                        "apiName", "Id",
                                        "displayName", "Account ID",
                                        "dataType", "id"

                                ),
                                KeyValue.of(
                                        "id", "60e49df87c987b42ccd83c1a",
                                        "apiName", "Name",
                                        "displayName", "Account Name",
                                        "dataType", "string"
                                )

                        )
                )
        );
    }

    public static KeyValue getEntityPipelinesCreatedReviewStep(List<KeyValue> pipelines) {
        // Start with all entity pipelines
        int pipelinesCount = pipelines.size();

        for(KeyValue pipeline: pipelines){
            var fields = (List) pipeline.getOrDefault("fields", List.of());
            // Add the field pipelines to the count
            pipelinesCount += fields.size();
        }

        return KeyValue.of(
                "label", "Pipelines created",
                "count", pipelinesCount,
                "renderInfo", KeyValue.of(
                        "type", "entityPipelines",
                        "data", pipelines
                )
        );
    }

    public static KeyValue getReferenceDataSetsReviewStep(List<String> refData) {
        return KeyValue.of(
                "label", "Reference datasets will be used",
                "count", refData.size(),
                "renderInfo", KeyValue.of(
                        "type", "stringList",
                        "data", refData
                )
        );
    }

    public static KeyValue getServiceCredentialsReviewStep(List<String> serviceCredentials) {
        return KeyValue.of(
                "label", "Service provider will be used",
                "count", 1,
                "renderInfo", KeyValue.of(
                        "type", "stringList",
                        "data", serviceCredentials
                )
        );
    }
}
