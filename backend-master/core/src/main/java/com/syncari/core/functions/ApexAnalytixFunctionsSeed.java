package com.syncari.core.functions;

import com.syncari.connector.Constants;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.PicklistType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.enrich.apexanalytix.ApexAnalytixSeed;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.Parameter;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import com.syncari.utils.KeyValue;

import java.util.*;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

public class ApexAnalytixFunctionsSeed {

    public static final String APEX_ANALYTIX_COMPANY_ENRICH = "apexAnalytixCompanyEnrich";

    public static FunctionDefinition getApexAnalytixCompanyEnrich() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("serviceId")
                        .setDatatype(new PicklistType()).setLabel(i18n("apex_credentials"))
                        .setHelpSummary(i18n("apex_connector_help"))
                        .setHelpText(i18n("apex_connector_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("type","Service", "serviceType", "ENRICH","service","Apexanalytix")),
                new FunctionConfiguration()
                        .setName("companyName")
                        .setDatatype(new StringType()).setLabel(i18n("aa_company_name_label"))
                        .setHelpSummary(i18n("aa_company_name_help"))
                        .setHelpText(i18n("aa_company_name_help"))
                        .setDefaultValue("")
                        .setRequired(true)
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("country")
                        .setDatatype(new StringType()).setLabel(i18n("aa_country_label"))
                        .setHelpSummary(i18n("aa_country_help"))
                        .setHelpText(i18n("aa_country_help"))
                        .setDefaultValue("")
                        .setRequired(true)
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("lookUpKey")
                        .setDatatype(new PicklistType()).setLabel("Enrichment Field")
                        .setDefaultValue("")
                        .setRequired(true)
                        .setAdditionalProperties(Map.of("values", getFields("company")))
                 );
        return new FunctionDefinition()
                .setName(APEX_ANALYTIX_COMPANY_ENRICH)
                .setDisplayName(i18n("aa_account_enrichment"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("aa_account_enrichment_help"))
                .setHelpPath("functions." + APEX_ANALYTIX_COMPANY_ENRICH)
                .setIconPath(format(FunctionsSeed.iconPath, Constants.APEX_ANALYTIX))
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
        ApexAnalytixSeed.getEntity(name).getAttributes().stream().forEach(a ->
                values.add(new KeyValue("value", a.getApiName()).set("label", a.getDisplayName())));
        return values;
    }

}
