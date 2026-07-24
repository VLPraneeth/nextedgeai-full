package com.syncari.core.functions;

import com.syncari.core.datatype.*;
import com.syncari.core.enrich.similarweb.SimilarWebAPICategory;
import com.syncari.core.model.EngineType;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.Parameter;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

public class SimilarWebFunctionsSeed {

    public static final String TRAFFIC_DATA = "similarWebTrafficData";

    public static FunctionDefinition getTrafficMetrics() {
        List<FunctionConfiguration> configuration =  List.of(
                new FunctionConfiguration()
                        .setName("apiCategory")
                        .setDatatype(new PicklistType()).setLabel(i18n("sw_traffic_category_label"))
                        .setHelpSummary(i18n("sw_traffic_category_label_help"))
                        .setHelpText(i18n("sw_traffic_category_label_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("values",apiCategories())),
                new FunctionConfiguration()
                        .setName("apiName")
                        .setDatatype(new PicklistType()).setLabel(i18n("sw_traffic_metric_label"))
                        .setHelpSummary(i18n("sw_traffic_metric_label_help"))
                        .setHelpText(i18n("sw_traffic_metric_label_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("dependsOn",Map.of("dependantField","configuration.apiCategory","dependantType","SimilarWebMetrics"))),
                new FunctionConfiguration()
                        .setName("date")
                        .setDatatype(new StringType()).setLabel(i18n("sw_traffic_start_date_label"))
                        .setHelpSummary(i18n("sw_traffic_start_date_label_help"))
                        .setHelpText(i18n("sw_traffic_start_date_label_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of()),
                new FunctionConfiguration()
                        .setName("countryCode")
                        .setDatatype(new StringType()).setLabel(i18n("sw_traffic_country_code_label"))
                        .setHelpSummary(i18n("sw_traffic_country_code_label_help"))
                        .setHelpText(i18n("sw_traffic_country_code_label_help"))
                        .setDefaultValue(""),
                new FunctionConfiguration()
                        .setName("similarWebConnectorId")
                        .setDatatype(new PicklistType()).setLabel(i18n("sw_traffic_connector_label"))
                        .setHelpSummary(i18n("sw_traffic_connector_label_help"))
                        .setHelpText(i18n("sw_traffic_connector_label_help"))
                        .setDefaultValue("")
                        .setAdditionalProperties(Map.of("type","Service", "serviceType", "ENRICH","service","Similarweb"))

        );
        return new FunctionDefinition()
                .setName(TRAFFIC_DATA)
                .setDisplayName(i18n("sw_traffic_title"))
                .setScope(Scope.ATTRIBUTE)
                .setHelpSummary(i18n("sw_traffic_help"))
                .setHelpPath("functions." + TRAFFIC_DATA)
                .setIconPath(format(FunctionsSeed.iconPath, "similarweb"))
                .setEngineType(EngineType.FUNCTION)
                .setConfiguration(configuration)
                .setOutputType(new StringType())
                .setType(Type.STANDARD)
                .setPositionalParams(
                        List.of(new Parameter(
                                "domain", DatatypeFactory.getDatatype("string"), false)
                        )
                );
    }

    private static List<Map<String,String>> apiCategories() {
        return Arrays.asList(SimilarWebAPICategory.values()).stream().map(
                category -> Map.of("label",i18n(category.getApiCategory()),"value",category.name())
        ).collect(Collectors.toList());
    }
}
