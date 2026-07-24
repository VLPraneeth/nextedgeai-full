package com.syncari.core.quickstart.v2;

import com.syncari.utils.KeyValue;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.List;

@Data
@Accessors(chain = true)
public class QSAuthoringSeed {

    public final static String ICON_PATH_URL = "/arcade/api/v1/quickstart/icon/%s/NEW";

    private final static String DEFAULT_ICON_PATH_URL = "/assets/icons/logos/quick-start-default.png";

    public enum PulishOption {
        publish,
        dontPublish
    }

    public static QSAuthoringConfig getConfig() {
        var qsConfig = new QSAuthoringConfig()
                .setDescription("Quick start wizard")
                .setDisplayName("Quick start wizard")
                .setHelpLink("https://syncari.helpdocs.io/quickstart")
                .setHelpSummary("Quick start help")
                .setName("quick_start")
                .setConfiguration(getConfiguration())
                .setRenderer(getRenderer());
        return qsConfig;
    }

    private static KeyValue getRenderer() {
        var steps = List.of(
            KeyValue.of(
                    "stepName", "Basic Settings",
                    "fields", List.of(
                            "quickStartBasicSettingsDescription",
                            "displayName",
                            "description",
                            "tags",
                            "postInstallationInstruction",
                            "iconPath"),
                            "layout", KeyValue.of(
                                    "type", "stack",
                                    "className", "synri-skull-stack-container-md"
                            )
            ),
            KeyValue.of(
                    "stepName", "Pipeline Settings",
                    "dynamicSteps", true,
                    "fields", List.of("pipelinePickerSelectionWarning", "pipelines"),
                            "layout", KeyValue.of(
                            "type", "stack",
                            "className", "synri-skull-stack-container-lg")
            ),
            KeyValue.of(
                    "stepName", "Review",
                    "dynamicSteps", true,
                    "fields", List.of(
                                "reviewSettingsDescription",
                                "reviewSelections"
                    ),
                    "layout", KeyValue.of(
                            "type", "stack",
                            "className", "synri-skull-stack-container-lg")
            ),
            KeyValue.of(
                    "stepName", "Publish",
                    "dynamicSteps", true,
                    "applyStep", true,
                    "fields", List.of(
                            "publishSettingsDescription",
                            "shareWithInstances"
                    ),
                    "layout", KeyValue.of(
                            "type", "stack",
                            "className", "synri-skull-stack-container-md"
                    )
            ),
            KeyValue.of(
                    "stepName", "Confirmation",
                    "fields", List.of("quickStartConfirmation"),
                    "closeStep", true,
                    "layout", KeyValue.of(
                            "type", "stack",
                            "className", "synri-skull-stack-container-full-center"
                    )
            )
        );
        return KeyValue.of(
                "renderType", "fullContentPanel",
                "title", "New Quickstart",
                "steps", steps
        );
    }

    private static List<KeyValue> getConfiguration() {
        return List.of(
                KeyValue.of(
                        "id", "quickStartBasicSettingsDescription",
                        "datatype", "infoBox",
                        "message", "Set up Quick Start display settings",
                        "description", "Enter the information that will show when users run your Quick Start, including the display name and descriptive text.",
                        "datatype", "infoBox",
                        "name", "quickStartBasicSettingsDescription",
                        "showIcon", false
                ),
                KeyValue.of(
                        "id", "displayName",
                        "datatype", "string",
                        "helpSummary", "Name of your quick start",
                        "name", "displayName",
                        "label", "Display Name"
                ),
                KeyValue.of(
                        "datatype", "richtext",
                        "helpSummary", "Description of your Quick Start.",
                        "id", "description",
                        "label", "Description",
                        "name", "description",
                        "placeholder", ""
                ),
                KeyValue.of(
                        "datatype", "tag",
                        "helpSummary", "Tags are used to improve search results.",
                        "id", "tags",
                        "label", "Tags",
                        "name", "tags"
                ),
                KeyValue.of(
                        "datatype", "richtext",
                        "helpSummary", "Message displayed at the end of the installation",
                        "id", "postInstallationInstruction",
                        "label", "Post Installation Message",
                        "placeholder", "",
                        "name", "postInstallationInstruction"
                ),
                KeyValue.of(
                        "datatype", "image",
                        "helpSummary", "Icon to represent this quick start",
                        "id", "iconPath",
                        "name", "iconPath",
                        "defaultValue", DEFAULT_ICON_PATH_URL,
                        "label", "Custom icon"
                ),
                KeyValue.of(
                        "datatype", "infoBox",
                        "message", "Review and select all required Entity and Field Pipelines",
                        "description", "If the Quick Start contains dependent Entity or Field Pipelines, check that the relevant Field and Entities are selected.",
                        "id", "pipelinePickerSelectionWarning",
                        "name", "pipelinePickerSelectionWarning",
                        "type", "warning",
                        "showIcon", false
                ),
                KeyValue.of(
                        "renderType", "pipelinePicker",
                        "id", "pipelines",
                        "name", "pipelines"
                ),
                KeyValue.of(
                        "datatype", "infoBox",
                        "message", "Review basic and pipeline Quick Start settings",
                        "description", "Confirm your setttings and pipeline selections are correct before publishing your Quick Start.",
                        "id", "reviewSettingsDescription",
                        "name", "reviewSettingsDescription",
                        "showIcon", false
                ),
                KeyValue.of(
                        "datatype", "infoBox",
                        "message", "Configure your Share settings",
                        "description", "Select which of your Syncari instances will see this Quick Start.",
                        "id", "publishSettingsDescription",
                        "name", "publishSettingsDescription",
                        "showIcon", false
                ),
                getSelectionsReview(KeyValue.of()),
                getPublishOptions("dontPublish"),
                getShareWithOrgSelection(false),
                getShareInstances(List.of(), List.of()),
                KeyValue.of(
                        "datatype", "confirmationInfoBox",
                        "description", "You can safely close this panel.",
                        "id", "quickStartConfirmation",
                        "message", "Your Quick Start has been saved",
                        "name", "quickStartConfirmation"
                )
        );
    }

    public static KeyValue getShareInstances(List<KeyValue> instances, List<String> sharedToInstances) {
        return KeyValue.of(
                "renderType", "instancePicker",
                "id", "shareWithInstances",
                "label", "Share with instances",
                "name", "shareWithInstances",
                "defaultValue", sharedToInstances,
                "values", instances
        );
    }

    public static KeyValue getPublishOptions(String defaultValue) {
        return KeyValue.of(
                "datatype", "picklist",
                "helpSummary", "Publish to Quick Start Library (Admin only)",
                "id", "publishToQuickStartLibrary",
                "label", "Publish to Quick Start Library (Admin only)",
                "name", "publishToQuickStartLibrary",
                "defaultValue", defaultValue,
                "values", List.of(
                        KeyValue.of(
                                "label", "Yes, publish to Library",
                                "value", "publish"
                        ),
                        KeyValue.of(
                                "label", "No, do not publish to the library",
                                "value", "dontPublish"
                        )
                )
        );
    }

    public static KeyValue getShareWithOrgSelection(boolean defaultValue) {
        return KeyValue.of(
                "datatype", "boolean",
                "helpSummary", "Share within current Organization",
                "id", "shareWithOrg",
                "label", "Share with Organization",
                "name", "shareWithOrg",
                "defaultValue", defaultValue
        );
    }

    public static KeyValue getPublish(KeyValue inputs) {
        return KeyValue.of(
                "datatype", "picklist",
                "helpSummary", "Publish to Quick Start Library (Admin only)",
                "id", "publishToQuickStartLibrary",
                "label", "Publish to Quick Start Library (Admin only)",
                "name", "publishToQuickStartLibrary",
                "value", "publish",
                "values", List.of(
                        KeyValue.of(
                                "label", "Yes, publish to Library",
                                "value", "publish"
                        ),
                        KeyValue.of(
                                "label", "No, do not publish to the library",
                                "value", "dontPublish"
                        )
                )
        );
    }

    public static KeyValue getSelectionsReview(KeyValue inputs) {
        var pipelinesPreview = (LinkedHashMap)inputs.get("pipelines");
        if (pipelinesPreview == null) {
            pipelinesPreview = new LinkedHashMap();
        }
        pipelinesPreview.put("id", "pipelinesPreview");
        pipelinesPreview.put("name", "pipelinesPreview");
        pipelinesPreview.put("renderType", "pipelinePickerPreview");

        return KeyValue.of(
                "renderType", "skullColumns",
                "id", "reviewSelections",
                "name", "reviewSelections",
                "columns", List.of(KeyValue.of(
                        "items", List.of(
                                KeyValue.of(
                                        "buttonText", "Edit",
                                        "id", "settingsHeader",
                                        "name", "settingsHeader",
                                        "renderType", "jumpToStepLabel",
                                        "stepNumber", 0,
                                        "text", "Basic settings"
                                ),
                                KeyValue.of(
                                        "datatype", "string",
                                        "displayMode", "readonly",
                                        "id", "displayNamePreview",
                                        "label", "Display name",
                                        "name", "displayNamePreview",
                                        "tooltip", "Name visible to installer",
                                        "value", inputs.get("displayName") == null ? "" : inputs.get("displayName")
                                ),
                                KeyValue.of(
                                        "datatype", "richtext",
                                        "displayMode", "readonly",
                                        "id", "descriptionPreview",
                                        "label", "Description",
                                        "name", "descriptionPreview",
                                        "tooltip", "Description visible to installer",
                                        "value", inputs.get("description") == null ? "No description provided" : inputs.get("description")
                                ),
                                KeyValue.of(
                                        "datatype", "tag",
                                        "displayMode", "readonly",
                                        "id", "tagPreview",
                                        "label", "Tags",
                                        "name", "tagPreview",
                                        "tooltip", "Tags are used to improve search results",
                                        "value", inputs.get("tags") == null ? List.of() : inputs.get("tags")
                                ),
                                KeyValue.of(
                                        "datatype", "richtext",
                                        "displayMode", "readonly",
                                        "id", "postInstallMessagePreview",
                                        "label", "Post-installation message",
                                        "name", "postInstallMessagePreview",
                                        "tooltip", "Message displayed after Quick Start has been installed",
                                        "value", inputs.get("postInstallationInstruction") == null ? "No post-installation message provided" : inputs.get("postInstallationInstruction")
                                )
                        ),
                        "span", 14
                ),KeyValue.of(
                        "items", List.of(
                                KeyValue.of(
                                        "buttonText", "Edit",
                                        "id", "pipelineSettingsHeader",
                                        "name", "pipelineSettingsHeader",
                                        "renderType", "jumpToStepLabel",
                                        "stepNumber", 1,
                                        "text", "Pipeline settings"
                                ),
                                pipelinesPreview
                        ),
                        "span", 10
                ))
        );
    }
}
