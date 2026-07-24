package com.syncari.core.actions;

import com.syncari.connector.Constants;
import com.syncari.core.datatype.*;
import com.syncari.core.functions.CreateFileAction;
import com.syncari.core.functions.ExportSyncariRecordsAction;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.Parameter;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import com.syncari.utils.KeyValue;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

public class ActionsSeed {

    private static Map<String, ActionDefinition> actionDefMap = new HashMap<>();
    public static final String iconPath = "/assets/icons/actions/%s.svg";

    static {
        addToMap(actionDefMap, addToMarketoProgramSeed());
        addToMap(actionDefMap, addToMarketoListSeed());
        addToMap(actionDefMap, removeFromMarketoListSeed());
        addToMap(actionDefMap, addToSalesforceCampaignSeed());
        addToMap(actionDefMap, convertSalesforceLeadSeed());
        addToMap(actionDefMap, createSalesforceTaskSeed());
        addToMap(actionDefMap, createZendeskSeed());
        addToMap(actionDefMap, sendEmailSeed());
        addToMap(actionDefMap, sendSlackMessageSeed());
        addToMap(actionDefMap, requeueRecord());
        addToMap(actionDefMap, addToHubspotListSeed());
        addToMap(actionDefMap, createExternalRecordSeed());
        addToMap(actionDefMap, updateExternalRecordSeed());
        addToMap(actionDefMap, deleteExternalRecordSeed());
        addToMap(actionDefMap, createSalesforceFileSeed());
        addToMap(actionDefMap, deleteSyncariRecords());
        addToMap(actionDefMap, updateSyncariRecords());
        addToMap(actionDefMap, insertSyncariRecord());
        addToMap(actionDefMap, exportSyncariRecords());
        addToMap(actionDefMap, sendMSTeamsMessageSeed());
        addToMap(actionDefMap, createMSTeamsChannelSeed());
        addToMap(actionDefMap, respondToWebhookSeed());
        addToMap(actionDefMap, createFile());
    }

    private static void addToMap( Map<String, ActionDefinition> actionMap, ActionDefinition actionDefinition) {
        actionDefMap.put(actionDefinition.getName(), actionDefinition);
    }

    public static ActionDefinition get(String name) {
        return actionDefMap.get(name);
    }

    public static int count() {
        return actionDefMap.size();
    }
    public static ActionDefinition populateAction(ActionDefinition def){
        ActionDefinition fromSeed = ActionsSeed.get(def.getName());
        if(fromSeed != null) {
            def.setName(fromSeed.getName())
                    .setDisplayName(fromSeed.getDisplayName())
                    .setScope(fromSeed.getScope())
                    .setHelpSummary(fromSeed.getHelpSummary()).setHelpPath(fromSeed.getHelpPath())
                    .setIconPath(fromSeed.getIconPath())
                    .setEngineType(fromSeed.getEngineType())
                    .setType(fromSeed.getType())
                    .setPositionalParams(fromSeed.getPositionalParams())
                    .setHidden(fromSeed.isHidden())
                    .setConfiguration(fromSeed.getConfiguration())
                    .setDynamicConfig(fromSeed.isDynamicConfig());
        }
        return def;
    }

    private static ActionDefinition addToMarketoListSeed() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("synapseId").setDatatype(new PicklistType()).setLabel("Synapse")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("addToMarketoList_synapse_action_help"))
                        .setAdditionalProperties(Map.of("type", "Synapse", "synapseType", "marketo")),
                new FunctionConfiguration().setName("listId").setDatatype(new StringType()).setLabel("List Id")
                        .setDefaultValue("").setHelpSummary(i18n("addToMarketoList_list_action_help")).setRequired(true).setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration().setName("leadId").setDatatype(new StringType()).setLabel("Lead Id")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("addToHubspotList_type_action_help")).setAdditionalProperties(Map.of())
        );

        return new ActionDefinition()
                .setName(ActionConstants.ADD_TO_MARKETO_LIST).setDisplayName("Add To Marketo List").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("addToMarketoList_action_help")).setIconPath(format(ActionsSeed.iconPath, "add-to-list"))
                .setHelpPath("actions." + ActionConstants.ADD_TO_MARKETO_LIST).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition addToHubspotListSeed() {
    	List<FunctionConfiguration> configuration =  List.of(
    			new FunctionConfiguration().setName("synapseId").setDatatype(new PicklistType()).setLabel("Synapse")
    			.setDefaultValue("").setRequired(true).setHelpSummary(i18n("addToHubspotList_synapse_action_help"))
    			.setAdditionalProperties(Map.of("type", "Synapse", "synapseType", "hubspot")),
    			new FunctionConfiguration().setName("listId").setDatatype(new StringType()).setLabel("List Id")
    			.setDefaultValue("").setHelpSummary(i18n("addToHubspotList_list_action_help")).setRequired(true).setAdditionalProperties(Map.of("hideTokenPicker", true)),
    			new FunctionConfiguration().setName("valueType").setDatatype(new PicklistType()).setLabel("Type")
    			.setDefaultValue("").setRequired(true).setHelpSummary(i18n("addToHubspotList_type_action_help"))
    			.setAdditionalProperties(Map.of("values", List.of(new KeyValue("label","Contact Id").set("value","contactId"),new KeyValue("label","Email Address").set("value","email")))),
    			new FunctionConfiguration().setName("value").setDatatype(new StringType()).setLabel("Value")
    			.setDefaultValue("").setHelpSummary(i18n("addToHubspotList_value_action_help")).setRequired(true).setAdditionalProperties(Map.of())
    			);

    	return new ActionDefinition()
                       .setName(ActionConstants.ADD_TO_HUBSPOT_LIST).setDisplayName("Add To HubSpot List").setScope(Scope.ENTITY_AND_ATTRIBUTE)
    			.setHelpSummary(i18n("addToHubspotList_action_help")).setIconPath(format(ActionsSeed.iconPath, "add-to-list"))
    			.setHelpPath("actions." + ActionConstants.ADD_TO_HUBSPOT_LIST).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
    			.setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition createExternalRecordSeed() {
        List<FunctionConfiguration> updateFieldConfigurations = List.of(
                new FunctionConfiguration().setName("updateField").setDatatype(new PicklistType())
                        .setLabel("Field")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.entityId"),
                                "fieldSet","updateFields",
                                "mapping", new KeyValue("graphKey","configuration.updateField").set("configKey","value"),
                                "id","updateField",
                                "name","updateField"
                        )),
                new FunctionConfiguration().setName("newValue").setDatatype(new ObjectType())
                        .setLabel("New Value")
                        .setAdditionalProperties(Map.of(
                                "fieldSet","updateFields",
                                "mapping", new KeyValue("graphKey","configuration.newValue").set("configKey","value"),
                                "id","newValue",
                                "name","newValue",
                                "renderType","tokens"
                        ))
        );

    	List<FunctionConfiguration> configuration =  List.of(
    			new FunctionConfiguration().setName("synapseId").setDatatype(new PicklistType()).setLabel("Synapse")
    			.setDefaultValue("").setRequired(true).setHelpSummary(i18n("createExternalRecord_action_synapse_action_help"))
    			.setAdditionalProperties(Map.of("type", "Synapse")),
                new FunctionConfiguration().setName("entityId").setDatatype(new PicklistType()).setLabel("Entity")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("createExternalRecord_action_entity_action_help"))
                        .setAdditionalProperties(Map.of("type", "SynapseEntity", "dependsOn", Map.of("dependantType", "SynapseEntity", "dependantField", "configuration.synapseId"))),

                new FunctionConfiguration().setName("insertFields").setDatatype(new CompositeType())
                        .setLabel(i18n("createExternalRecord_action_fields_label"))
                        .setHelpSummary(i18n("createExternalRecord_action_fields_help"))
                        .setHelpText(i18n("createExternalRecord_action_fields_help"))
                        .setLlmHint("Sample structure : [{\"newValue\":{\"name\":\"newValue\",\"value\":\"test\"},\"updateField\":{\"name\":\"updateField\",\"value\":\"64b5948714070500010291b1\"}}" +
                                ". Where updateField value is the id of the field. This can be fetched using synapse entity field id")
                        .setDefaultValue("").setRequired(false)
                        .setAdditionalProperties(Map.of("layout", "row", "graphKey", "configuration.insertFields", "configKey", "value", "repeatable", true))
                        .setConfiguration(updateFieldConfigurations),
                new FunctionConfiguration().setName("batchMode").setDatatype(new BooleanType()).setLabel("Create Records in Batches")
                        .setDefaultValue(true).setRequired(false).setHelpSummary(i18n("createExternalRecord_action_batchMode_help"))
        );


        return new ActionDefinition()
                .setName(ActionConstants.CREATE_EXTERNAL_RECORD).setDisplayName("Create External Record").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("createExternalRecord_action_help")).setIconPath(format(ActionsSeed.iconPath, "convert-lead"))
                .setHelpPath("actions." + ActionConstants.CREATE_EXTERNAL_RECORD).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition updateExternalRecordSeed() {
        List<FunctionConfiguration> updateFieldConfigurations = List.of(
                new FunctionConfiguration().setName("updateField").setDatatype(new PicklistType())
                        .setLabel("Field")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.entityId"),
                                "fieldSet","updateFields",
                                "mapping", new KeyValue("graphKey","configuration.updateField").set("configKey","value"),
                                "id","updateField",
                                "name","updateField",
                                "allowUserToken", true
                        )),
                new FunctionConfiguration().setName("newValue").setDatatype(new ObjectType())
                        .setLabel("New Value")
                        .setAdditionalProperties(Map.of(
                                "fieldSet","updateFields",
                                "mapping", new KeyValue("graphKey","configuration.newValue").set("configKey","value"),
                                "id","newValue",
                                "name","newValue",
                                "renderType","tokens"
                        ))
        );

        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("synapseId").setDatatype(new PicklistType()).setLabel("Synapse")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("updateExternalRecord_action_synapse_help"))
                        .setAdditionalProperties(Map.of("type", "UpdateSynapse")),
                new FunctionConfiguration().setName("entityId").setDatatype(new PicklistType()).setLabel("Entity")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("updateExternalRecord_action_entity_help"))
                        .setAdditionalProperties(Map.of("type","SynapseEntity", "dependsOn", Map.of("dependantType", "SynapseEntity", "dependantField", "configuration.synapseId"))),
                new FunctionConfiguration().setName("recordId").setDatatype(new StringType()).setLabel("Record Id")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("updateExternalRecord_action_record_help")),

                new FunctionConfiguration().setName("updateFields").setDatatype(new CompositeType())
                        .setLabel(i18n("updateExternalRecord_action_fields_label"))
                        .setHelpSummary(i18n("updateExternalRecord_action_fields_help"))
                        .setHelpText(i18n("updateExternalRecord_action_fields_help"))
                        .setDefaultValue("").setRequired(false)
                        .setAdditionalProperties(Map.of("layout","row","graphKey","configuration.updateFields","configKey","value","repeatable",true))
                        .setConfiguration(updateFieldConfigurations));

        return new ActionDefinition()
                .setName(ActionConstants.UPDATE_EXTERNAL_RECORD).setDisplayName("Update External Record").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("updateExternalRecord_action_help")).setIconPath(format(ActionsSeed.iconPath, "update-external-record"))
                .setHelpPath("actions." + ActionConstants.UPDATE_EXTERNAL_RECORD).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition deleteExternalRecordSeed() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("synapseId").setDatatype(new PicklistType()).setLabel("Synapse")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("deleteExternalRecord_action_synapse_help"))
                        .setAdditionalProperties(Map.of("type", "DeleteSynapse")),
                new FunctionConfiguration().setName("entityId").setDatatype(new PicklistType()).setLabel("Entity")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("deleteExternalRecord_action_entity_help"))
                        .setAdditionalProperties(Map.of("type","SynapseEntity", "dependsOn", Map.of("dependantType", "SynapseEntity", "dependantField", "configuration.synapseId"))),
                new FunctionConfiguration().setName("recordId").setDatatype(new StringType()).setLabel("Record Id")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("deleteExternalRecord_action_record_help")));

        return new ActionDefinition()
                .setName(ActionConstants.DELETE_EXTERNAL_RECORD).setDisplayName("Delete External Record").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("deleteExternalRecord_action_help")).setIconPath(format(ActionsSeed.iconPath, "delete-external-record"))
                .setHelpPath("actions." + ActionConstants.DELETE_EXTERNAL_RECORD).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }
    private static ActionDefinition removeFromMarketoListSeed() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("synapseId").setDatatype(new PicklistType()).setLabel("Synapse")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("removeFromMarketoList_synapse_action_help"))
                        .setAdditionalProperties(Map.of("type", "Synapse", "synapseType", "marketo")),
                new FunctionConfiguration().setName("listId").setDatatype(new StringType()).setLabel("List Id")
                        .setDefaultValue("").setHelpSummary(i18n("removeFromMarketoList_list_action_help")).setRequired(true).setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration().setName("leadId").setDatatype(new StringType()).setLabel("Lead Id")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("removeFromMarketoList_lead_action_help")).setAdditionalProperties(Map.of())
        );

        return new ActionDefinition()
                .setName(ActionConstants.REMOVE_FROM_MARKETO_LIST).setDisplayName("Remove From Marketo List").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("removeFromMarketoList_action_help")).setIconPath(format(ActionsSeed.iconPath, "remove-from-list"))
                .setHelpPath("actions." + ActionConstants.REMOVE_FROM_MARKETO_LIST).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition addToMarketoProgramSeed() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("synapseId").setDatatype(new PicklistType()).setLabel("Synapse")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("addToMarketoProgram_synapse_action_help"))
                        .setAdditionalProperties(Map.of("type", "Synapse", "synapseType", "marketo")),
                new FunctionConfiguration().setName("programId").setDatatype(new StringType()).setLabel("Program Id")
                        .setDefaultValue("").setHelpSummary(i18n("addToMarketoProgram_program_action_help")).setRequired(true).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("programStatus").setDatatype(new StringType()).setLabel("Program Status")
                        .setDefaultValue("").setHelpSummary(i18n("addToMarketoProgram_program_status_action_help")).setRequired(false).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("leadId").setDatatype(new StringType()).setLabel("Lead Id")
                        .setDefaultValue("").setHelpSummary(i18n("addToMarketoProgram_lead_action_help")).setRequired(true).setAdditionalProperties(Map.of())
        );

        return new ActionDefinition()
                .setName(ActionConstants.ADD_TO_MARKETO_PROGRAM).setDisplayName("Add To Marketo Program").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("addToMarketoProgram_action_help")).setIconPath(format(ActionsSeed.iconPath, "add-to-program"))
                .setHelpPath("actions." + ActionConstants.ADD_TO_MARKETO_PROGRAM).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition addToSalesforceCampaignSeed() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("synapseId").setDatatype(new PicklistType()).setLabel("Synapse")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("addToSfdcCampaign_synapse_action_help"))
                        .setAdditionalProperties(Map.of("type", "Synapse", "synapseType", "salesforce")),
                new FunctionConfiguration().setName("entity").setDatatype(new PicklistType()).setLabel("Salesforce Entity")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("addToSfdcCampaign_entity_action_help"))
                        .setAdditionalProperties(Map.of("values",
                                List.of(
                                        Map.of("value", "Lead", "label", "Lead"),
                                        Map.of("value", "Contact", "label", "Contact")
                                ))),
                new FunctionConfiguration().setName("campaignId").setDatatype(new StringType()).setLabel("Campaign Id")
                        .setDefaultValue("").setHelpSummary(i18n("addToSfdcCampaign_campaign_action_help")).setRequired(true).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("leadId").setDatatype(new StringType()).setLabel("Lead/Contact Id")
                        .setDefaultValue("").setHelpSummary(i18n("addToSfdcCampaign_record_action_help")).setRequired(false).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("status").setDatatype(new StringType()).setLabel("Status")
                        .setDefaultValue("").setHelpSummary(i18n("addToSfdcCampaign_status_action_help")).setAdditionalProperties(Map.of())
        );

        return new ActionDefinition()
                .setName(ActionConstants.ADD_TO_SFDC_CAMPAIGN).setDisplayName("Add To Salesforce Campaign").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("addToSfdcCampaign_action_help")).setIconPath(format(ActionsSeed.iconPath, "add-to-sfdc-campaign"))
                .setHelpPath("actions." + ActionConstants.ADD_TO_SFDC_CAMPAIGN).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition convertSalesforceLeadSeed() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("synapseId").setDatatype(new PicklistType()).setLabel("Synapse")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("convertSalesforceLead_synapse_action_help"))
                        .setAdditionalProperties(Map.of("type", "Synapse", "synapseType", "salesforce")),
                new FunctionConfiguration().setName("leadId").setDatatype(new StringType()).setLabel("Lead Id")
                        .setDefaultValue("").setHelpSummary(i18n("convertSalesforceLead_lead_action_help")).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("ownerId").setDatatype(new StringType()).setLabel("Fallback Owner Id")
                        .setDefaultValue("").setHelpSummary(i18n("convertSalesforceLead_fallbackOwner_action_help")).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("contactId").setDatatype(new StringType()).setLabel("Contact Id")
                        .setDefaultValue("").setHelpSummary(i18n("convertSalesforceLead_contactId_action_help")).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("accountId").setDatatype(new StringType()).setLabel("Account Id")
                        .setDefaultValue("").setHelpSummary(i18n("convertSalesforceLead_accountId_action_help")).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("opportunityId").setDatatype(new StringType()).setLabel("Opportunity Id")
                        .setDefaultValue("").setHelpSummary(i18n("convertSalesforceLead_opportunityId_action_help")).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("skipOpportunity").setDatatype(new BooleanType()).setLabel("Do Not Create Opportunity")
                        .setDefaultValue(false).setHelpSummary(i18n("convertSalesforceLead_skipOpportunity_action_help"))
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration().setName("captureResults").setDatatype(new BooleanType()).setLabel("Capture conversion results")
                        .setDefaultValue(true).setHelpSummary(i18n("returnCreated_help"))
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration().setName("convertedStatus").setDatatype(new StringType()).setLabel("Converted Status")
                        .setDefaultValue("").setHelpSummary(i18n("convertSalesforceLead_convertedStatus_action_help")).setAdditionalProperties(Map.of())
        );

        return new ActionDefinition()
                .setName(ActionConstants.CONVERT_SALESFORCE_LEAD).setDisplayName("Convert Salesforce Lead").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("convertSalesforceLead_action_help")).setIconPath(format(ActionsSeed.iconPath, "convert-lead"))
                .setHelpPath("actions." + ActionConstants.CONVERT_SALESFORCE_LEAD).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition createSalesforceFileSeed() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("synapseId")
                        .setDatatype(new PicklistType()).setLabel("Synapse")
                        .setDefaultValue("")
                        .setRequired(true)
                        .setHelpSummary(i18n("createSalesforceFile_synapse_action_help"))
                        .setAdditionalProperties(Map.of("type", "Synapse", "synapseType", "salesforce")),
                new FunctionConfiguration()
                        .setName("fileLink")
                        .setDatatype(new StringType()).setLabel("Syncari File Link")
                        .setHelpSummary(i18n("createSalesforceFile_file_type_action_help"))
                        .setRequired(true),
                new FunctionConfiguration()
                        .setName("name")
                        .setDatatype(new StringType()).setLabel("File Name")
                        .setDefaultValue("")
                        .setHelpSummary(i18n("createSalesforceFile_file_name_action_help"))
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("recordId")
                        .setDatatype(new StringType()).setLabel("Record Id").setDefaultValue("")
                        .setHelpSummary(i18n("createSalesforceFile_record_id_action_help"))
        );
        return new ActionDefinition()
                .setName(ActionConstants.CREATE_SALESFORCE_FILE)
                .setDisplayName("Create Salesforce File").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("createSalesforceFile_action_help")).setIconPath(format(ActionsSeed.iconPath, "add-file"))
                .setHelpPath("actions." + ActionConstants.CREATE_SALESFORCE_FILE).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition createSalesforceTaskSeed() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("synapseId").setDatatype(new PicklistType()).setLabel("Synapse")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("createSalesforceTask_synapse_action_help"))
                        .setAdditionalProperties(Map.of("type", "Synapse", "synapseType", "salesforce")),
                new FunctionConfiguration().setName("OwnerId").setDatatype(new PicklistType()).setLabel("Assigned To")
                        .setDefaultValue("").setHelpSummary(i18n("createSalesforceTask_ownerId_action_help")).setRequired(true)
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "Entity.User", "dependantField", "configuration.synapseId"))),
                new FunctionConfiguration().setName("Status").setDatatype(new PicklistType()).setLabel("Status")
                        .setDefaultValue("").setHelpSummary(i18n("createSalesforceTask_status_action_help"))
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "SFDCTaskStatuses", "dependantField", "configuration.synapseId"))),
                new FunctionConfiguration().setName("Subject").setDatatype(new StringType()).setLabel("Subject")
                        .setDefaultValue("").setHelpSummary(i18n("createSalesforceTask_subject_action_help"))
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("ActivityDate").setDatatype(new DateType()).setLabel("Due Date")
                        .setDefaultValue("").setHelpSummary(i18n("createSalesforceTask_activityDate_action_help"))
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("Priority").setDatatype(new PicklistType()).setLabel("Priority")
                        .setDefaultValue("").setHelpSummary(i18n("createSalesforceTask_priority_action_help"))
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "SFDCTaskPriorities", "dependantField", "configuration.synapseId")))
        );
        return new ActionDefinition()
                .setName(ActionConstants.CREATE_SALESFORCE_TASK).setDisplayName("Create Salesforce Task").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("createSalesforceTask_action_help")).setIconPath(format(ActionsSeed.iconPath, "create-task"))
                .setHelpPath("actions." + ActionConstants.CREATE_SALESFORCE_TASK).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition createZendeskSeed() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("synapseId").setDatatype(new PicklistType()).setLabel("Synapse")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("createZendesk_synapse_action_help"))
                        .setAdditionalProperties(Map.of("type", "Synapse", "synapseType", "zendesk")),
                new FunctionConfiguration().setName("type").setDatatype(new PicklistType()).setLabel("Type")
                        .setDefaultValue("").setHelpSummary(i18n("createZendesk_type_action_help")).setRequired(true).
                        setAdditionalProperties(Map.of("values", zendeskTypeValues())),
                new FunctionConfiguration().setName("priority").setDatatype(new PicklistType()).setLabel("Priority")
                        .setDefaultValue("").setHelpSummary(i18n("createZendesk_priority_action_help")).setRequired(true)
                        .setAdditionalProperties(Map.of("values", zendeskPriorityValues()
)),
                new FunctionConfiguration().setName("subject").setDatatype(new StringType()).setLabel("Subject")
                        .setDefaultValue("").setHelpSummary(i18n("createZendesk_subject_action_help"))
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("zendeskDescription").setDatatype(new TextareaType()).setLabel("Description")
                        .setDefaultValue("").setHelpSummary(i18n("createZendesk_description_action_help"))
                        .setAdditionalProperties(Map.of())
        );

        return new ActionDefinition()
                .setName(ActionConstants.CREATE_ZENDESK_TICKET).setDisplayName("Create Zendesk Ticket").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("createZendeskTicket_action_help")).setIconPath(format(ActionsSeed.iconPath, "zendesk-ticket"))
                .setHelpPath("actions." + ActionConstants.CREATE_ZENDESK_TICKET).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }


    public static List<Map<String, String>> zendeskPriorityValues() {
        return List.of(
                Map.of("value", "low", "label", "Low"),
                Map.of("value", "normal", "label", "Normal"),
                Map.of("value", "high", "label", "High"),
                Map.of("value", "urgent", "label", "Urgent")
        );
    }

    public static List<Map<String, String>> zendeskTypeValues() {
        return List.of(
                Map.of("value", "question", "label", "Question"),
                Map.of("value", "incident", "label", "Incident"),
                Map.of("value", "problem", "label", "Problem"),
                Map.of("value", "task", "label", "Task")
        );
    }

    private static ActionDefinition sendEmailSeed() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("recipients").setDatatype(new EmailListType()).setLabel("Recipients")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("sendEmail_recepients_action_help"))
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("subject").setDatatype(new StringType()).setLabel("Subject")
                        .setDefaultValue("").setHelpSummary(i18n("sendEmail_subject_action_help")).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("body").setDatatype(new EmailBodyType()).setLabel("Email Body")
                        .setDefaultValue("").setHelpSummary(i18n("sendEmail_body_action_help")).setAdditionalProperties(Map.of("renderHtml", true))
        );

        return new ActionDefinition()
                .setName(ActionConstants.SEND_EMAIL).setDisplayName("Send Email").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("sendEmail_action_help")).setIconPath(format(ActionsSeed.iconPath, "send-email"))
                .setHelpPath("actions." + ActionConstants.SEND_EMAIL).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition sendSlackMessageSeed() {
        List<FunctionConfiguration> configuration =  List.of(

                new FunctionConfiguration().setName("serviceId").setDatatype(new PicklistType()).setLabel("Slack")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("sendSlack_synapse_action_help"))
                        .setAdditionalProperties(Map.of("type", "Synapse", "synapseType", "slack")),
                new FunctionConfiguration().setName("channel").setDatatype(new StringType()).setLabel("Channel or User")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("sendSlack_channel_action_help"))
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("message").setDatatype(new TextareaType()).setLabel("Message")
                        .setDefaultValue("").setHelpSummary(i18n("sendSlack_message_action_help")).setRequired(true).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("block").setDatatype(new TextareaType()).setLabel("Block")
                        .setDefaultValue("").setHelpSummary(i18n("sendSlack_block_action_help")).setRequired(false).setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("thread").setDatatype(new StringType()).setLabel("Thread")
                        .setDefaultValue("").setHelpSummary(i18n("sendSlack_thread_action_help")).setRequired(false).setAdditionalProperties(Map.of())

        );

        return new ActionDefinition()
                .setName(ActionConstants.SEND_SLACK_MESSAGE).setDisplayName("Send Slack Message").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("sendSlackMessage_action_help")).setIconPath(format(ActionsSeed.iconPath, "send-slack-message"))
                .setHelpPath("actions." + ActionConstants.SEND_SLACK_MESSAGE).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }


    private static ActionDefinition requeueRecord() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("retryTimeLimit").setDatatype(StringType.VALUE).setLabel("Retry Until")
                        .setDefaultValue("next 1 day").setRequired(true)
                        .setHelpText("requeueRecord_retryTimeLimit_help")
                        .setHelpSummary("requeueRecord_retryTimeLimit_help"),
                new FunctionConfiguration().setName("processExpiredRecord").setDatatype(BooleanType.VALUE).
                        setLabel("requeueRecord_processExpiredRecord_label")
                        .setDefaultValue(false).setRequired(false)
                        .setHelpText("requeueRecord_processExpiredRecord_help")
                        .setHelpSummary("requeueRecord_processExpiredRecord_help")

        );

        return new ActionDefinition()
                .setName(ActionConstants.REQUEUE_RECORD).setDisplayName("requeueRecord_action_label")
                .setScope(Scope.ENTITY)
                .setHelpSummary(i18n("requeueRecord_action_help"))
                .setIconPath(format(ActionsSeed.iconPath, "requeue-record"))
                .setHelpPath("actions.requeueRecord").setEngineType(EngineType.ACTION)
                .setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition deleteSyncariRecords() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("syncariEntityDefId").setDatatype(new PicklistType())
                        .setLabel(i18n("delete_records_entity_label"))
                        .setHelpSummary(i18n("delete_records_entity_help"))
                        .setHelpText(i18n("delete_records_entity_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("type","SyncariEntity")),
                new FunctionConfiguration().setName("predicate").setDatatype(new PredicateType())
                        .setLabel(i18n("delete_records_predicate_label"))
                        .setHelpSummary(i18n("delete_records_predicate_help"))
                        .setHelpText(i18n("delete_records_predicate_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of( "fieldSet", "conditionFields")),
                new FunctionConfiguration().setName("field").setDatatype(new PicklistType()).setLabel("Field")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"), "fieldSet","conditionFields")),
                new FunctionConfiguration().setName("operator").setDatatype(new PicklistType()).setLabel("Operator")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields","dependsOn", Map.of("dependantType", "Operator", "dependantField", "configuration.field"))),
                new FunctionConfiguration().setName("value").setDatatype(new StringType()).setLabel("Value")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet","conditionFields")),
                new FunctionConfiguration().setName("deleteInEnd").setDatatype(new BooleanType())
                        .setLabel(i18n("delete_in_end_system_label"))
                        .setHelpSummary(i18n("delete_in_end_system_help"))
                        .setHelpText(i18n("delete_in_end_system_help")).setDefaultValue(false)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true))
                );

        // TODO: Update help article
        return new ActionDefinition()
                .setName("deleteSyncariRecords").setDisplayName(i18n("delete_records_func_label")).setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("delete_records_func_help")).setIconPath(format(ActionsSeed.iconPath, "delete-syncari-record"))
                .setHelpPath("functions.updateSyncariRecords")
                .setEngineType(EngineType.ACTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(false)
                .setLlmHint("Use this action when a user tries to delete syncari record. Do not construct and execute raw sql")
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition updateSyncariRecords() {
        String name = ActionConstants.UPDATE_SYNCARI_RECORDS;
        List<FunctionConfiguration> updateFieldConfigurations = List.of(
                new FunctionConfiguration().setName("updateField").setDatatype(new PicklistType())
                        .setLabel("Update Field")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"),
                                "fieldSet", "updateFields",
                                "mapping", new KeyValue("graphKey", "configuration.updateField").set("configKey", "value"),
                                "id", "updateField",
                                "name", "updateField"

                        )),
                new FunctionConfiguration().setName("newValue").setDatatype(new ObjectType())
                        .setLabel("New Value")
                        .setAdditionalProperties(Map.of(
                                "fieldSet", "updateFields",
                                "mapping", new KeyValue("graphKey", "configuration.newValue").set("configKey", "value"),
                                "id", "newValue",
                                "name", "newValue",
                                "renderType", "tokens"
                        )),
                new FunctionConfiguration().setName("operation").setDatatype(new PicklistType())
                        .setLabel("Operation")
                        .setAdditionalProperties(Map.of(
                                "fieldSet", "updateFields",
                                "mapping", new KeyValue("graphKey", "configuration.operation").set("configKey", "value"),
                                "id", "operation",
                                "name", "operation",
                                "dependsOn", Map.of("dependantType", "Operation", "dependantField", "configuration.updateField")
                        ))

        );

        List<FunctionConfiguration> configuration = List.of(
                new FunctionConfiguration().setName("syncariEntityDefId").setDatatype(new PicklistType())
                        .setLabel(i18n("update_records_entity_label"))
                        .setHelpSummary(i18n("update_records_entity_help"))
                        .setHelpText(i18n("update_records_entity_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("type", "SyncariEntity")),
                new FunctionConfiguration().setName("rejectEmpty").setDatatype(new BooleanType())
                        .setLabel(i18n("update_records_reject_empty_label"))
                        .setHelpSummary(i18n("update_records_reject_empty_help"))
                        .setHelpText(i18n("update_records_reject_empty_help")).setDefaultValue(true)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),

                new FunctionConfiguration().setName("predicate").setDatatype(new PredicateType())
                        .setLabel(i18n("update_records_predicate_label"))
                        .setHelpSummary(i18n("update_records_predicate_help"))
                        .setHelpText(i18n("update_records_predicate_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("fieldSet", "conditionFields")),
                new FunctionConfiguration().setName("dontMatchBlank").setDatatype(new BooleanType())
                        .setLabel("update_records_use_empty_label")
                        .setHelpSummary("update_records_use_empty_help")
                        .setDefaultValue(false)
                        .setAdditionalProperties(Map.of("hideTokenPicker", true)),
                new FunctionConfiguration().setName("field").setDatatype(new PicklistType()).setLabel("Field")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"), "fieldSet", "conditionFields")),
                new FunctionConfiguration().setName("operator").setDatatype(new PicklistType()).setLabel("Operator")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet", "conditionFields", "dependsOn", Map.of("dependantType", "Operator", "dependantField", "configuration.field"))),
                new FunctionConfiguration().setName("value").setDatatype(new StringType()).setLabel("Value")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet", "conditionFields")),
                new FunctionConfiguration().setName("updateFields").setDatatype(new CompositeType())
                        .setLabel(i18n("update_records_fields_label"))
                        .setHelpSummary(i18n("update_records_fields_help"))
                        .setHelpText(i18n("update_records_fields_help"))
                        .setDefaultValue("").setRequired(false)
                        .setAdditionalProperties(Map.of("layout", "row", "graphKey", "configuration.updateFields", "configKey", "value", "repeatable", true))
                        .setConfiguration(updateFieldConfigurations)
        );
        return new ActionDefinition()
                .setName(name).setDisplayName(i18n("update_records_func_label")).setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("update_records_func_help")).setIconPath(format(ActionsSeed.iconPath, "update-record"))
                .setHelpPath("functions.updateSyncariRecords")
                .setEngineType(EngineType.ACTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(false)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }


    private static ActionDefinition insertSyncariRecord() {
        String name = ActionConstants.INSERT_SYNCARI_RECORD;
        List<FunctionConfiguration> updateFieldConfigurations = List.of(
                new FunctionConfiguration().setName("updateField").setDatatype(new PicklistType())
                        .setLabel("Field")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"),
                                "fieldSet", "updateFields",
                                "mapping", new KeyValue("graphKey", "configuration.updateField").set("configKey", "value"),
                                "id", "updateField",
                                "name", "updateField"

                        )),
                new FunctionConfiguration().setName("newValue").setDatatype(new ObjectType())
                        .setLabel("New Value")
                        .setAdditionalProperties(Map.of(
                                "fieldSet", "updateFields",
                                "mapping", new KeyValue("graphKey", "configuration.newValue").set("configKey", "value"),
                                "id", "newValue",
                                "name", "newValue",
                                "renderType", "tokens"
                        ))
        );

        List<FunctionConfiguration> configuration = List.of(
                new FunctionConfiguration().setName("syncariEntityDefId").setDatatype(new PicklistType())
                        .setLabel(i18n("insert_record_entity_label"))
                        .setHelpSummary(i18n("insert_record_entity_help"))
                        .setHelpText(i18n("insert_record_entity_help"))
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("type", "SyncariEntity")),

                new FunctionConfiguration().setName("insertFields").setDatatype(new CompositeType())
                        .setLabel(i18n("insert_record_fields_label"))
                        .setHelpSummary(i18n("insert_record_fields_help"))
                        .setHelpText(i18n("insert_record_fields_help"))
                        .setLlmHint("Sample structure : [{\"newValue\":{\"name\":\"newValue\",\"value\":\"test\"},\"updateField\":{\"name\":\"updateField\",\"value\":\"64b5948714070500010291b1\"}}" +
                                ". Where updateField value is the id of the field. This can be fetched using synapse entity field id")
                        .setDefaultValue("").setRequired(false)
                        .setAdditionalProperties(Map.of("layout", "row", "graphKey", "configuration.insertFields", "configKey", "value", "repeatable", true))
                        .setConfiguration(updateFieldConfigurations),
                new FunctionConfiguration().setName("batchMode").setDatatype(new BooleanType()).setLabel("Create Records in Batches")
                        .setDefaultValue(false).setRequired(false).setHelpSummary(i18n("createSyncariRecord_action_batchMode_help"))

        );
        return new ActionDefinition()
                .setName(name).setDisplayName(i18n("insert_record_func_label")).setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("insert_record_func_help")).setIconPath(format(ActionsSeed.iconPath, "update-record"))
                .setHelpPath("functions." + name)
                .setEngineType(EngineType.ACTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(false)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition exportSyncariRecords() {
        String name = ActionConstants.EXPORT_SYNCARI_RECORDS;

        List<FunctionConfiguration> configuration = List.of(
                new FunctionConfiguration().setName(ExportSyncariRecordsAction.SYNCARI_ENTITY_DEF_ID).setDatatype(new PicklistType())
                        .setLabel("export_records_syncari_entity_label")
                        .setHelpSummary("export_records_syncari_entity_help_summary")
                        .setHelpText("export_records_syncari_entity_help_text")
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("type", "SyncariEntity")),
                new FunctionConfiguration().setName(ExportSyncariRecordsAction.EXPORT_FIELDS).setDatatype(PicklistType.VALUE)
                        .setLabel("export_records_export_fields_label")
                        .setHelpSummary("export_records_export_fields_help_summary")
                        .setHelpText("export_records_export_fields_help_text")
                        .setMultiValuedVariable(true)
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId")
                        )),
                new FunctionConfiguration().setName(ExportSyncariRecordsAction.PREDICATE).setDatatype(new PredicateType())
                        .setLabel("export_records_predicate_label")
                        .setHelpSummary("export_records_predicate_help_summary")
                        .setHelpText("export_records_predicate_help_text")
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("fieldSet", "conditionFields")),
                new FunctionConfiguration().setName("field").setDatatype(new PicklistType()).setLabel("Field")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"), "fieldSet", "conditionFields")),
                new FunctionConfiguration().setName("operator").setDatatype(new PicklistType()).setLabel("Operator")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet", "conditionFields", "dependsOn", Map.of("dependantType", "Operator", "dependantField", "configuration.field"))),
                new FunctionConfiguration().setName("value").setDatatype(new StringType()).setLabel("Value")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet", "conditionFields")),
                new FunctionConfiguration().setName(ExportSyncariRecordsAction.MAX_RECORDS).setDatatype(IntegerType.VALUE)
                        .setLabel("export_records_max_records_label")
                        .setHelpSummary("export_records_max_records_help_summary")
                        .setHelpText("export_records_max_records_help_text")
                        .setDefaultValue("").setRequired(false),
                new FunctionConfiguration().setName(ExportSyncariRecordsAction.USE_DISPLAY_NAME).setDatatype(BooleanType.VALUE)
                        .setLabel("export_records_use_display_name_label")
                        .setHelpSummary("export_records_use_display_name_help_summary")
                        .setHelpText("export_records_use_display_name_help_text")
                        .setDefaultValue("").setRequired(false),
                new FunctionConfiguration().setName(ExportSyncariRecordsAction.DATETIME_FORMAT).setDatatype(StringType.VALUE)
                        .setLabel("export_records_datetime_format_label")
                        .setHelpSummary("export_records_datetime_format_help_summary")
                        .setHelpText("export_records_datetime_format_help_text")
                        .setDefaultValue("").setRequired(false),
                new FunctionConfiguration().setName(ExportSyncariRecordsAction.DATE_FORMAT).setDatatype(StringType.VALUE)
                        .setLabel("export_records_date_format_label")
                        .setHelpSummary("export_records_date_format_help_summary")
                        .setHelpText("export_records_date_format_help_text")
                        .setDefaultValue("").setRequired(false),
                new FunctionConfiguration().setName(ExportSyncariRecordsAction.STORAGE_SYNAPSE_ID).setDatatype(PicklistType.VALUE)
                        .setLabel("export_records_storage_synapse_label")
                        .setHelpSummary("export_records_storage_synapse_help_summary")
                        .setHelpText("export_records_storage_synapse_help_text")
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("type", "StorageSynapse")),
                new FunctionConfiguration().setName(ExportSyncariRecordsAction.FOLDER).setDatatype(StringType.VALUE)
                        .setLabel("export_records_folder_label")
                        .setHelpSummary("export_records_folder_help_summary")
                        .setHelpText("export_records_folder_help_text")
                        .setDefaultValue("").setRequired(true),
                new FunctionConfiguration().setName(ExportSyncariRecordsAction.FILE_NAME).setDatatype(StringType.VALUE)
                        .setLabel("export_records_file_name_label")
                        .setHelpSummary("export_records_file_name_help_summary")
                        .setHelpText("export_records_file_name_help_text")
                        .setDefaultValue("").setRequired(true)

        );
        return new ActionDefinition()
                .setName(name).setDisplayName("export_records_display_name").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary("export_records_help_summary").setIconPath(format(ActionsSeed.iconPath, "export-record"))
                .setHelpPath("actions.exportSyncariRecords")
                .setEngineType(EngineType.ACTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(false)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition createFile() {
        String name = ActionConstants.CREATE_FILE_ACTION;

        List<FunctionConfiguration> configuration = List.of(
                new FunctionConfiguration().setName(CreateFileAction.SYNCARI_ENTITY_DEF_ID).setDatatype(new PicklistType())
                        .setLabel("create_file_syncari_entity_label")
                        .setHelpSummary("create_file_syncari_entity_help_summary")
                        .setHelpText("create_file_syncari_entity_help_text")
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("type", "SyncariEntity")),
                new FunctionConfiguration().setName(CreateFileAction.PREDICATE).setDatatype(new PredicateType())
                        .setLabel("create_file_predicate_label")
                        .setHelpSummary("create_file_predicate_help_summary")
                        .setHelpText("create_file_predicate_help_text")
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("fieldSet", "conditionFields")),
                new FunctionConfiguration().setName("field").setDatatype(new PicklistType()).setLabel("Field")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("dependsOn", Map.of("dependantType", "AttributeList", "dependantField", "configuration.syncariEntityDefId"), "fieldSet", "conditionFields")),
                new FunctionConfiguration().setName("operator").setDatatype(new PicklistType()).setLabel("Operator")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet", "conditionFields", "dependsOn", Map.of("dependantType", "Operator", "dependantField", "configuration.field"))),
                new FunctionConfiguration().setName("value").setDatatype(new StringType()).setLabel("Value")
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("fieldSet", "conditionFields")),
                new FunctionConfiguration().setName(CreateFileAction.FILE_CONTENT)
                        .setDatatype(TextareaType.VALUE)
                        .setLabel("create_file_file_content_label")
                        .setHelpSummary("create_file_file_content_help_summary")
                        .setHelpText("create_file_file_content_help_text")
                        .setDefaultValue("").setRequired(false),
                new FunctionConfiguration().setName(ExportSyncariRecordsAction.STORAGE_SYNAPSE_ID)
                        .setDatatype(PicklistType.VALUE)
                        .setLabel("create_file_storage_synapse_label")
                        .setHelpSummary("create_file_storage_synapse_help_summary")
                        .setHelpText("create_file_storage_synapse_help_text")
                        .setDefaultValue("").setRequired(true)
                        .setAdditionalProperties(Map.of("type", "StorageSynapse")),
                new FunctionConfiguration().setName(ExportSyncariRecordsAction.FOLDER)
                        .setDatatype(StringType.VALUE)
                        .setLabel("create_file_folder_label")
                        .setHelpSummary("create_file_folder_help_summary")
                        .setHelpText("create_file_folder_help_text")
                        .setDefaultValue("").setRequired(true),
                new FunctionConfiguration().setName(ExportSyncariRecordsAction.FILE_NAME)
                        .setDatatype(StringType.VALUE)
                        .setLabel("create_file_file_name_label")
                        .setHelpSummary("create_file_file_name_help_summary")
                        .setHelpText("create_file_file_name_help_text")
                        .setDefaultValue("").setRequired(true)

        );
        return new ActionDefinition()
                .setName(name).setDisplayName("create_file_display_name")
                .setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary("create_file_help_summary").setIconPath(format(ActionsSeed.iconPath, "export-record"))
                .setHelpPath("actions.createFile")
                .setEngineType(EngineType.ACTION).setConfiguration(configuration)
                .setOutputType(new ObjectType()).setType(Type.STANDARD)
                .setDynamicConfig(false)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition sendMSTeamsMessageSeed() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("serviceId").setDatatype(new PicklistType()).setLabel("MS Teams")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("msTeams_synapse_action_help"))
                        .setAdditionalProperties(Map.of("type", "Synapse", "synapseType", Constants.MS_TEAMS)),
                new FunctionConfiguration().setName("teamId").setDatatype(new StringType()).setLabel("Team ID")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("msTeams_teamid_action_help"))
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("channel").setDatatype(new StringType()).setLabel("Channel ID")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("msTeams_channel_action_help"))
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("message").setDatatype(new TextareaType()).setLabel("Message")
                        .setDefaultValue("").setHelpSummary(i18n("sendMSTeams_message_action_help")).setRequired(true).setAdditionalProperties(Map.of())

        );

        return new ActionDefinition()
                .setName(ActionConstants.SEND_MSTEAMS_MESSAGE).setDisplayName("Send MS Teams Message").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("sendMSTeamsMessage_action_help")).setIconPath(format(ActionsSeed.iconPath, "microsoft-teams"))
                .setHelpPath("actions." + ActionConstants.SEND_MSTEAMS_MESSAGE).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition createMSTeamsChannelSeed() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("serviceId").setDatatype(new PicklistType()).setLabel("MS Teams")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("msTeams_synapse_action_help"))
                        .setAdditionalProperties(Map.of("type", "Synapse", "synapseType", Constants.MS_TEAMS)),
                new FunctionConfiguration().setName("teamId").setDatatype(new StringType()).setLabel("Team ID")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("msTeams_teamid_action_help"))
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration().setName("channel").setDatatype(new StringType()).setLabel("Channel Name")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("createMSTeamsChannel_channel_action_help"))
                        .setAdditionalProperties(Map.of())
        );

        return new ActionDefinition()
                .setName(ActionConstants.CREATE_MSTEAMS_CHANNEL).setDisplayName("Create MS Teams Channel").setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("createMSTeamsChannel_action_help")).setIconPath(format(ActionsSeed.iconPath, "microsoft-teams"))
                .setHelpPath("actions." + ActionConstants.CREATE_MSTEAMS_CHANNEL).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD)
                .setPositionalParams(List.of(new Parameter("value", DatatypeFactory.getDatatype("object"), false)));
    }

    private static ActionDefinition respondToWebhookSeed() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration().setName("statusCode").setDatatype(new PicklistType()).setLabel("Status Code")
                        .setAdditionalProperties(Map.of("values",whHttpcodes())).setDefaultValue(200).setRequired(true).setHelpSummary(i18n("respondToWebhook_status_help")),
                new FunctionConfiguration().setName("response").setDatatype(new TextareaType()).setLabel("Response")
                        .setDefaultValue("").setRequired(true).setHelpSummary(i18n("respondToWebhook_response_help")));

        return new ActionDefinition()
                .setName(ActionConstants.RESPOND_TO_WEBHOOK).setDisplayName(i18n("respondToWebhook_action_name")).setScope(Scope.ENTITY_AND_ATTRIBUTE)
                .setHelpSummary(i18n("respondToWebhook_action_help")).setIconPath(format(ActionsSeed.iconPath, "respond-to-webhook"))
                .setHelpPath("actions." + ActionConstants.RESPOND_TO_WEBHOOK).setEngineType(EngineType.ACTION).setConfiguration(configuration).setType(Type.STANDARD);
    }

    private static List<KeyValue> whHttpcodes() {
        return Arrays.stream(HttpStatus.values())
                .map(s -> new KeyValue()
                        .set("label", s.value() + " " + s.getReasonPhrase())
                        .set("value", s.value()))
                .collect(Collectors.toList());
    }

}
