package com.syncari.core.quickstart.unify;

import com.syncari.core.quickstart.QuickStartConstants;
import com.syncari.utils.KeyValue;

import java.util.List;
import java.util.Map;

import static com.syncari.utils.I18n.i18n;

public class UnifyQuickStartSeed {

    public static KeyValue getRenderer() {
        return new KeyValue("renderType", "quickStartWizard")
                .set("title", i18n(QuickStartConstants.UNIFY))
                .set("steps", getSteps());
    }

    public static List<Object> getSteps() {
        return List.of(
                getSelectEntityStep(),
                getSynapseUnificationSettingStep(),
                getPreviewStep(),
                getConfirmationStep()
        );
    }

    public static Map<String, Object> getSelectEntityStep() {
        return Map.of(
                "stepNumber", 0,
                "stepName", "Select Entity",
                "fields", List.of("unifyDescription", "syncariEntity")
        );
    }

    public static Map<String, Object> getSynapseUnificationSettingStep() {
        return Map.of(
                "stepNumber", 1,
                "stepName", "Review and configure",
                "fields", List.of("synapseUnificationSetting")
        );
    }

    public static KeyValue getPreviewStep() {
        return new KeyValue()
                .set("stepNumber", 2)
                .set("stepName", "Preview")
                .set("dynamicSteps", true)
                .set("applyStep", true)
                .set("preview", true)
                .set("next", Map.of(
                        "buttonText", "Start unification"
                ))
                .set("fields", List.of(
                        "previewDescription",
                        "syncariEntity",
                        "synapseUnificationSetting"
                ));
    }

    public static Map<String, Object> getConfirmationStep() {
        return Map.of(
                "stepNumber", 3,
                "stepName", "Confirmation",
                "closeStep", "true",
                "fields", List.of(
                        "quickStartConfirmation"
                )
        );
    }

    public static List<KeyValue> getConfiguration() {
        return List.of(
                getUnifyDescription(),
                getSyncariEntitySelection(),
                getSynapseUnificationSetting(),
                getPreviewDescription(),
                getConfirmationPage()
        );
    }

    public static KeyValue getUnifyDescription() {
        return new KeyValue("id", "unifyDescription")
                .set("name", "unifyDescription")
                .set("datatype", "infoBox")
                .set("message", "Unify your data")
                .set("description", "This Quick Start allows you to unify data from multiple external systems, allowing you to gain deeper, more holistic insights at the record level.")
                .set("showIcon", true);
    }

    public static KeyValue getSyncariEntitySelection() {
        return new KeyValue("id", "syncariEntity")
                .set("name", "syncariEntity")
                .set("implicit", "syncariEntity")
                .set("label", "Syncari entity")
                .set("datatype", "picklist")
                .set("helpSummary", "Select the Syncari entity to unify.")
                .set("values", List.of());
    }

    public static KeyValue getSynapseUnificationSetting() {
        Map<String, Object> dependsOn = new KeyValue("dependantType", "quickstarts")
                .set("dependantFields", List.of("syncariEntity"))
                .set("metadata", Map.of(
                        "componentName", "unify_quick_start",
                        "configName", "synapseUnificationSetting",
                        "configType", "table"
                ));
        List<KeyValue> columnDefs = List.of(
                new KeyValue("headerName", "").set("field", "unify").set("cellRenderer", "checkbox")
                    .set("rowSelectionField", true).set("width", 0),
                new KeyValue("headerName", "Synapse").set("field", "synapse").set("cellRenderer", "textLabel").set("width", 100),
                new KeyValue("headerName", "Entity").set("field", "entity").set("cellRenderer", "textLabel").set("width", 100),
                new KeyValue("headerName", "Unification Fields").set("field", "unificationField").set("cellRenderer", "multiSelectField").set("width", 300).set("flex", 1)
        );

        return new KeyValue("id", "synapseUnificationSetting")
                .set("name", "synapseUnificationSetting")
                .set("label", "Select synapses for unification")
                .set("datatype", "table")
                .set("helpSummary", "Select and configure synapses for unification.")
                .set("values", List.of())
                .set("dependsOn", dependsOn)
                .set("columnDefs", columnDefs);
    }

    public static KeyValue getPreviewDescription() {
        return new KeyValue("id", "previewDescription")
                .set("name", "previewDescription")
                .set("datatype", "infoBox")
                .set("type", "info")
                .set("description", "Verify your unification settings and proceed when ready.")
                .set("showIcon", true);
    }

    public static KeyValue getConfirmationPage() {
        return new KeyValue("id", "quickStartConfirmation")
                .set("name", "quickStartConfirmation")
                .set("datatype", "confirmationInfoBox")
                .set("type", "info")
                .set("message", "We'll notify you when complete")
                .set("description", "We'll send you a notification when the data unification is finished. You can safely close this quick start panel.");
    }
}
