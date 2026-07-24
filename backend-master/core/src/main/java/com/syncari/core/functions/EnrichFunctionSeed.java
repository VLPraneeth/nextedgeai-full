package com.syncari.core.functions;

import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.PicklistType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.Parameter;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;

import java.util.List;
import java.util.Map;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

public class EnrichFunctionSeed {

    public static FunctionDefinition getEnrich() {
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
                .setName("enrich")
                .setDisplayName(i18n("enrichment_function"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("enrichment_function_help"))
                .setHelpPath("functions.")
                .setIconPath(format(FunctionsSeed.iconPath, "enrich"))
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

}
