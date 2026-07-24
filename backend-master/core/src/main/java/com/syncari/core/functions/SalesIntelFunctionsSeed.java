package com.syncari.core.functions;

import com.syncari.core.datatype.*;
import com.syncari.core.enrich.similarweb.SimilarWebAPICategory;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.Parameter;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import com.syncari.utils.KeyValue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

public class SalesIntelFunctionsSeed {

    public static final String SALESINTEL_PERSON_ENRICH = "salesIntelPersonEnrich";
    public static final String SALESINTEL_COMPANY_ENRICH = "salesIntelCompanyEnrich";

    public static FunctionDefinition getSalesIntelPersonMetric() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("serviceId")
                        .setDatatype(new PicklistType()).setLabel(i18n("si_credentials"))
                        .setHelpSummary(i18n("si_connector_help"))
                        .setHelpText(i18n("si_connector_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("type","Service", "serviceType", "ENRICH","service","Salesintel")),
                new FunctionConfiguration()
                        .setName("entityDefinition")
                        .setDatatype(new PicklistType()).setLabel("Source Entity")
                        .setHelpSummary(i18n("si_source_entity_help"))
                        .setHelpText(i18n("si_source_entity_help"))
                        .setDefaultValue("")
                        .setRequired(true)
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("email")
                        .setDatatype(new PicklistType()).setLabel(i18n("si_email_label"))
                        .setHelpSummary(i18n("si_email_help"))
                        .setHelpText(i18n("si_email_help"))
                        .setDefaultValue("")
                        .setRequired(true)
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("phoneNumber")
                        .setDatatype(new PicklistType()).setLabel(i18n("si_phoneNumber_label"))
                        .setHelpSummary(i18n("si_phoneNumber_help"))
                        .setHelpText(i18n("si_phoneNumber_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("firstName")
                        .setDatatype(new PicklistType()).setLabel(i18n("si_first_name_label"))
                        .setHelpSummary(i18n("si_first_name_help"))
                        .setHelpText(i18n("si_first_name_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("lastName")
                        .setDatatype(new PicklistType()).setLabel(i18n("si_last_name_label"))
                        .setHelpSummary(i18n("si_last_name_help"))
                        .setHelpText(i18n("si_last_name_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("lookUpKey")
                        .setDatatype(new PicklistType()).setLabel("Enrichment Field")
                        .setDefaultValue("")
                        .setRequired(true)
                        .setAdditionalProperties(Map.of())
                 );
        return new FunctionDefinition()
                .setName(SALESINTEL_PERSON_ENRICH)
                .setDisplayName(i18n("si_person_enrichment"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("si_person_enrichment_help"))
                .setHelpPath("functions." + SALESINTEL_PERSON_ENRICH)
                .setIconPath(format(FunctionsSeed.iconPath, "salesintel"))
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

    public static FunctionDefinition getSalesIntelCompanyMetric() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("serviceId")
                        .setDatatype(new PicklistType()).setLabel(i18n("si_credentials"))
                        .setHelpSummary(i18n("si_connector_help"))
                        .setHelpText(i18n("si_connector_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("type","Service", "serviceType", "ENRICH","service","Salesintel")),
                new FunctionConfiguration()
                        .setName("entityDefinition")
                        .setDatatype(new PicklistType()).setLabel("Source Entity")
                        .setHelpSummary(i18n("si_source_entity_help"))
                        .setHelpText(i18n("si_source_entity_help"))
                        .setDefaultValue("")
                        .setRequired(true)
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("companyName")
                        .setDatatype(new PicklistType()).setLabel(i18n("si_company_name_label"))
                        .setHelpSummary(i18n("si_company_name_help"))
                        .setHelpText(i18n("si_company_name_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("companyDomain")
                        .setDatatype(new PicklistType()).setLabel(i18n("si_company_domain_label"))
                        .setHelpSummary(i18n("si_company_domain_help"))
                        .setRequired(true)
                        .setHelpText(i18n("si_company_domain_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("companyIndustries")
                        .setDatatype(new PicklistType()).setLabel(i18n("si_company_industries_label"))
                        .setHelpSummary(i18n("si_company_industries_help"))
                        .setHelpText(i18n("si_company_industries_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("companyLocationStates")
                        .setDatatype(new PicklistType()).setLabel(i18n("si_company_location_states_label"))
                        .setHelpSummary(i18n("si_company_location_states_help"))
                        .setHelpText(i18n("si_company_location_states_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("lookUpKey")
                        .setDatatype(new PicklistType()).setLabel("Enrichment Field")
                        .setHelpSummary(i18n("si_enrichField_help"))
                        .setHelpText(i18n("si_enrichField_help"))
                        .setDefaultValue("")
                        .setRequired(true).
                        setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("verified")
                        .setDatatype(new BooleanType()).setLabel(i18n("si_verified_label"))
                        .setHelpSummary(i18n("si_verified_help"))
                        .setHelpText(i18n("si_verified_help"))
                        .setDefaultValue("")

        );
        return new FunctionDefinition()
                .setName(SALESINTEL_COMPANY_ENRICH)
                .setDisplayName(i18n("si_company_enrichment"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("si_company_enrichment_help"))
                .setHelpPath("functions." + SALESINTEL_COMPANY_ENRICH)
                .setIconPath(format(FunctionsSeed.iconPath, "salesintel"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setDynamicConfig(true)
                .setPositionalParams(
                        List.of(new Parameter(
                                "companyName", DatatypeFactory.getDatatype("string"), false)
                        )
                );
    }

}
