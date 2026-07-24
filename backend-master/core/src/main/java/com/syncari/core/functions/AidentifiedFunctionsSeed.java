package com.syncari.core.functions;

import com.syncari.connector.Constants;
import com.syncari.core.datatype.*;
import com.syncari.core.enrich.aidentified.AidentifiedSeed;
import com.syncari.core.enrich.apexanalytix.ApexAnalytixSeed;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.Parameter;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import com.syncari.utils.KeyValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

public class AidentifiedFunctionsSeed {

    public static final String AIDENTIFIED_PEOPLE_ENRICH = "aidentifiedPeopleEnrich";

    public static FunctionDefinition getAidentifiedCompanyEnrich() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("serviceId")
                        .setDatatype(new PicklistType()).setLabel(i18n("aidentified_credentials"))
                        .setHelpSummary(i18n("aidentified_connector_help"))
                        .setHelpText(i18n("aidentified_connector_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("type","Service", "serviceType", "ENRICH","service","Aidentified")),
                new FunctionConfiguration()
                        .setName("fullName")
                        .setDatatype(new StringType()).setLabel(i18n("aid_full_name_label"))
                        .setHelpSummary(i18n("aid_full_name_help"))
                        .setHelpText(i18n("aid_full_name_help"))
                        .setDefaultValue("")
                        .setRequired(true)
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("firstName")
                        .setDatatype(new StringType()).setLabel(i18n("aid_first_name_label"))
                        .setHelpSummary(i18n("aid_first_name_help"))
                        .setHelpText(i18n("aid_first_name_help"))
                        .setDefaultValue("")
                        .setRequired(true)
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("lastName")
                        .setDatatype(new StringType()).setLabel(i18n("aid_last_name_label"))
                        .setHelpSummary(i18n("aid_last_name_help"))
                        .setHelpText(i18n("aid_last_name_help"))
                        .setDefaultValue("")
                        .setRequired(true)
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("record_id")
                        .setDatatype(new StringType()).setLabel(i18n("aid_record_id_label"))
                        .setHelpSummary(i18n("aid_record_id_help"))
                        .setHelpText(i18n("aid_record_id_help"))
                        .setDefaultValue("")
                        .setRequired(true)
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("emails")
                        .setDatatype(new EmailListType()).setMultiValuedVariable(true).setLabel(i18n("aid_emails_label"))
                        .setHelpSummary(i18n("aid_emails_help"))
                        .setHelpText(i18n("aid_emails_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("phones")
                        .setDatatype(new EmailListType()).setMultiValuedVariable(true).setLabel(i18n("aid_phones_label"))
                        .setHelpSummary(i18n("aid_phones_help"))
                        .setHelpText(i18n("aid_phones_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("address")
                        .setDatatype(new StringType()).setLabel(i18n("aid_address_label"))
                        .setHelpSummary(i18n("aid_address_help"))
                        .setHelpText(i18n("aid_address_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("address2")
                        .setDatatype(new StringType()).setLabel(i18n("aid_address2_label"))
                        .setHelpSummary(i18n("aid_address2_help"))
                        .setHelpText(i18n("aid_address2_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("city")
                        .setDatatype(new StringType()).setLabel(i18n("aid_city_label"))
                        .setHelpSummary(i18n("aid_city_help"))
                        .setHelpText(i18n("aid_city_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("state")
                        .setDatatype(new StringType()).setLabel(i18n("aid_state_label"))
                        .setHelpSummary(i18n("aid_state_help"))
                        .setHelpText(i18n("aid_state_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("postalcode")
                        .setDatatype(new StringType()).setLabel(i18n("aid_postalcode_label"))
                        .setHelpSummary(i18n("aid_postalcode_help"))
                        .setHelpText(i18n("aid_postalcode_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("lookUpKey")
                        .setDatatype(new PicklistType()).setLabel("Enrichment Field")
                        .setDefaultValue("")
                        .setRequired(true)
                        .setAdditionalProperties(Map.of("values", getFields("people")))
                 );
        return new FunctionDefinition()
                .setName(AIDENTIFIED_PEOPLE_ENRICH)
                .setDisplayName(i18n("aid_people_enrichment"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("aid_people_enrichment_help"))
                .setHelpPath("functions." + AIDENTIFIED_PEOPLE_ENRICH)
                .setIconPath(format(FunctionsSeed.iconPath, Constants.AIDENTIFIED))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setPositionalParams(
                        List.of(new Parameter(
                                "email", DatatypeFactory.getDatatype("string"), false)
                        )
                );
    }

    public static List getFields(String name) {
        List values = new ArrayList<>();
        AidentifiedSeed.getEntity(name).getAttributes().stream().forEach(a ->
                values.add(new KeyValue("value", a.getApiName()).set("label", a.getDisplayName())));
        return values;
    }

}
