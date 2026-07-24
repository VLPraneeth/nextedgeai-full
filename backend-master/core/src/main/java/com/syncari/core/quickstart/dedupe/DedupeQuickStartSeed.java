package com.syncari.core.quickstart.dedupe;

import com.syncari.connector.Constants;
import com.syncari.core.model.RecordLevelWinnerSelection;
import com.syncari.core.model.WinnerOverridePolicy;
import com.syncari.core.model.WinnerValueSelectionPolicy;
import com.syncari.utils.KeyValue;

import java.util.Map;
import java.util.List;

public class DedupeQuickStartSeed {

    public KeyValue getSupportedEntities() {
        return KeyValue.of(
                Constants.SALESFORCE, List.of(Constants.ACCOUNT, Constants.LEAD, Constants.CONTACT),
                Constants.HUBSPOT, List.of("company", "contact"),
                Constants.NETSUITE, List.of("customer", Constants.CONTACT.toLowerCase()),
                Constants.MARKETO, List.of("lead")
        );
    }

    public KeyValue getDefaultAttributes() {
        return KeyValue.of(
                Constants.SALESFORCE, KeyValue.of(
                        Constants.ACCOUNT, List.of("Name", "Website"),
                        Constants.LEAD, List.of("Email"),
                        Constants.CONTACT, List.of("Email")),
                Constants.HUBSPOT, KeyValue.of(
                        "company", List.of("name", "website"),
                        "contact", List.of("Email")),
                Constants.NETSUITE, KeyValue.of(
                        "customer", List.of("companyName", "url"),
                        Constants.CONTACT.toLowerCase(), List.of("email")),
                Constants.MARKETO, KeyValue.of(
                        "lead", List.of("Email"))
        );
    }

    public List<KeyValue> getConfiguration() {
        return List.of(
                getPageOneInfo(),
                getSynapseId(),
                getSynapseEntityId(),
                getPreventDestinationSync(),
                getFindDuplicates(),
                getWinnerSelection(),
                getMergePolicy(),
                getOverridePolicy(),
                getConfigureSettingsPreview(),
                getPreviewDescription(),
                getConfirmation()
        );
    }

    public KeyValue getRenderer() {
        return new KeyValue("renderType", "quickStartWizard")
                .set("title", "Deduplicate data")
                .set("steps", getSteps());
    }

    public KeyValue getPageOneInfo() {
        return new KeyValue("id", "dedupMergeDescription")
                .set("name", "dedupMergeDescription")
                .set("datatype", "infoBox")
                .set("message", "Deduplicate your data")
                .set("description", "This Quick Start looks for duplicates in your data and merges them, using industry best practices and user-defined criteria for winner selection.")
                .set("showIcon", true);
    }

    public KeyValue getSynapseId() {
        return new KeyValue("id", "synapseId")
                .set("name", "synapseId")
                .set("label", "Synapse")
                .set("datatype", "picklist")
                .set("helpSummary", "Select a synapse to perform dedupe and merge.")
                .set("values", List.of()
        );
    }

    public KeyValue getSynapseEntityId() {
        return new KeyValue("id", "synapseEntityId")
                .set("name", "synapseEntityId")
                .set("label", "Entity")
                .set("datatype", "picklist")
                .set("helpSummary", "Select a synapse entity to perform dedupe and merge.")
                .set("dependsOn", new KeyValue("dependantField", "synapseId")
                    .set("dependantType", "DedupeQuickStartEntity")
                )
                .set("values", List.of()
        );
    }

    public KeyValue getPreventDestinationSync() {
        return new KeyValue("id", "preventDestinationSync")
                .set("name", "preventDestinationSync")
                .set("label", "Prevent destination sync")
                .set("datatype", "checkbox")
                .set("helpSummary", "Do not sync back the merged record to the synapse.")
                .set("defaultChecked", true)
                .set("values", List.of());
    }

    public KeyValue getFindDuplicates() {
        return new KeyValue("id", "findDuplicates")
                .set("name", "findDuplicates")
                .set("label", "Find duplicates")
                .set("datatype", "multiselectfield")
                .set("helpSummary", "Select synapse field. Find duplicates by matching incoming record against existing records. The conditions are matched in the order they are defined and will stop when one or more matches are found for a condition. Define the strictest match as the first condition and progressively relax matching conditions.")
                .set("values", List.of()
        );
    }

    public KeyValue getWinnerSelection() {
        return new KeyValue("id", "winnerSelection")
                .set("name", "winnerSelection")
                .set("label", "Select winner")
                .set("datatype", "picklist")
                .set("defaultValue", RecordLevelWinnerSelection.MOST_RECENTLY_UPDATED.name().toLowerCase())
                .set("helpSummary", "Select one record as the winner, by either selecting a field and a criteria or by selecting 'Record' option and a criteria. The conditions are matched in the order they are defined, until a winner is found. If no winner is found, the incoming record is considered the winner.")
                .set("values", List.of(
                        Map.of(
                                "label", "Earliest Created Record",
                                "value", RecordLevelWinnerSelection.OLDEST_CREATED.name().toLowerCase()
                        ),
                        Map.of(
                                "label", "Earliest Updated Record",
                                "value", RecordLevelWinnerSelection.OLDEST_UPDATED.name().toLowerCase()
                        ),
                        Map.of(
                                "label", "Most Complete record",
                                "value", RecordLevelWinnerSelection.MOST_COMPLETE.name().toLowerCase()
                        ),
                        Map.of(
                                "label", "Most Recently Created Record",
                                "value", RecordLevelWinnerSelection.MOST_RECENTLY_CREATED.name().toLowerCase()
                        ),
                        Map.of(
                                "label", "Most Recently Updated Record",
                                "value", RecordLevelWinnerSelection.MOST_RECENTLY_UPDATED.name().toLowerCase()
                        )
                )
        );
    }

    public KeyValue getMergePolicy() {
        return new KeyValue("id", "mergePolicy")
                .set("name", "mergePolicy")
                .set("label", "Merge policy")
                .set("datatype", "picklist")
                .set("defaultValue", WinnerValueSelectionPolicy.LATEST_WITH_VALUE.name())
                .set("helpSummary", "Once a winner is selected, use this policy to describe how to use values in losing records.")
                .set("values", List.of(
                        Map.of(
                                "label", "Most Frequent Value",
                                "value", WinnerValueSelectionPolicy.MOST_FREQUENT.name()
                        ),
                        Map.of(
                                "label", "Least Frequent Value",
                                "value", WinnerValueSelectionPolicy.LEAST_FREQUENT.name()
                        ),
                        Map.of(
                                "label", "Highest Value",
                                "value", WinnerValueSelectionPolicy.MIN.name()
                        ),
                        Map.of(
                                "label", "Lowest Value",
                                "value", WinnerValueSelectionPolicy.MAX.name()
                        ),
                        Map.of(
                                "label", "Latest With a Value",
                                "value", WinnerValueSelectionPolicy.LATEST_WITH_VALUE.name()
                        ),
                        Map.of(
                                "label", "Earliest With a Value",
                                "value", WinnerValueSelectionPolicy.EARLIEST_WITH_VALUE.name()
                        )
                )
        );
    }

    public KeyValue getOverridePolicy() {
        return new KeyValue("id", "overridePolicy")
                .set("name", "overridePolicy")
                .set("label", "Override policy")
                .set("datatype", "picklist")
                .set("defaultValue", WinnerOverridePolicy.WHEN_BLANK.name())
                .set("helpSummary", "Define when the above merge policy should be applied.")
                .set("values", List.of(
                        Map.of(
                                "label", "Override when winner is blank",
                                "value", WinnerOverridePolicy.WHEN_BLANK.name()
                        ),
                        Map.of(
                                "label", "Never override winner",
                                "value", WinnerOverridePolicy.NEVER.name()
                        ),
                        Map.of(
                                "label", "Always override winner",
                                "value", WinnerOverridePolicy.ALWAYS.name()
                        )
                )
        );
    }

    public KeyValue getConfirmation() {
        return new KeyValue("id", "quickStartConfirmation")
                .set("name", "quickStartConfirmation")
                .set("datatype", "confirmationInfoBox")
                .set("message", "We'll notify you when complete")
                .set("description", "We'll send you a notification when the data unification is finished. You can safely close this quick start panel.");
    }

    public static KeyValue getPreviewDescription() {
        return new KeyValue("id", "previewDescription")
                .set("name", "previewDescription")
                .set("datatype", "infoBox")
                .set("type", "info")
                .set("description", "Verify your settings and proceed when ready.")
                .set("showIcon", true);
    }

    public static KeyValue getConfigureSettingsPreview() {
        return new KeyValue("id", "configureSettingsPreview")
                .set("name", "configureSettingsPreview")
                .set("datatype", "infoBox")
                .set("type", "info")
                .set("description", "Select the fields Syncari will use for deduplication, as well as the criteria for choosing winning values and merging duplicates.")
                .set("showIcon", true);
    }


    public List<Object> getSteps() {
        return List.of(
                getSelectEntityStep(),
                getConfigureStep(),
                getPreviewStep(),
                getConfirmationStep()
        );
    }

    public Map<String, Object> getSelectEntityStep() {
        return Map.of(
                "stepName", "Select entity",
                "fields", List.of(
                        "dedupMergeDescription",
                        "synapseId",
                        "synapseEntityId",
                        "preventDestinationSync"
                )
        );
    }

    public Map<String, Object> getConfigureStep() {
        return Map.of(
                "stepName", "Configure settings",
                "dynamicSteps", true,
                "fields", List.of(
                        "configureSettingsPreview",
                        "findDuplicates",
                        "winnerSelection",
                        "mergePolicy",
                        "overridePolicy"
                )
        );
    }

    public KeyValue getPreviewStep() {
        var step = new KeyValue();
        step.put("stepName", "Review settings");
        step.put("applyStep", true);
        step.put("preview", true);
        step.put("applyStep", true);
        step.put("preview", true);
        step.put("next", Map.of(
                "buttonText", "Start deduplicate and merge"
        ));
        step.put("fields", List.of(
                "previewDescription",
                "synapseId",
                "synapseEntityId",
                "findDuplicates",
                "winnerSelection",
                "mergePolicy",
                "overridePolicy"
        ));
        return step;
    }

    public Map<String, Object> getConfirmationStep() {
        return Map.of(
                "stepName", "Confirmation",
                "closeStep", "true",
                "fields", List.of(
                        "quickStartConfirmation"
                )
        );
    }
}
