package com.syncari.core.functions;

import com.syncari.core.DataTransformer;
import com.syncari.core.enrich.similarweb.MetricRange;
import com.syncari.core.enrich.similarweb.SimilarWebAPICategory;
import com.syncari.core.enrich.similarweb.SimilarWebAPIName;
import com.syncari.core.enrich.similarweb.SimilarWebService;
import com.syncari.core.enrich.similarweb.VisitMetric;
import com.syncari.core.model.Connector;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class SimilarWebFunctions {
    @Autowired
    SimilarWebService similarWebService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    DataTransformer transformer;
    @Autowired
    TokenHelper tokenHelper;

    @Function
    public Object similarWebTrafficData(Object domain, FunctionCall functionCall, GraphContext context) {
        if (domain == null) {
            return null;
        }
        String lastMonth = ZonedDateTime.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String countryCode = getStringValue(context, functionCall, "countryCode", "world");
        String date = getStringValue(context, functionCall, "date", lastMonth);
        String similarWebConnectorId = getStringValue(context, functionCall, "similarWebConnectorId", "");
        String apiCategory = getStringValue(context, functionCall, "apiCategory", SimilarWebAPICategory.TOTAL_TRAFFIC.name());
        String apiName = getStringValue(context, functionCall, "apiName", SimilarWebAPIName.VISIT.name());
        Optional<Connector> connector = context.cache(similarWebConnectorId,()-> connectorService.find(similarWebConnectorId));
        var output = connector.map(c -> {
            SimilarWebAPICategory category = SimilarWebAPICategory.valueOf(apiCategory);
            MetricRange metricRange = similarWebService.getMetricRange(transformer.toConnectorInfo(c), domain.toString(), category);
            var validDate = metricRange == null ? date : getValidDate(date, metricRange.getStartDate(), metricRange.getEndDate());
            switch (category){
                case TOTAL_TRAFFIC:
                case DESKTOP_TRAFFIC:
                case MOBILE_WEB_TRAFFIC:
                case GLOBAL_RANK:
                case COUNTRY_RANK:
                    List<VisitMetric> visits = similarWebService.trafficMetrics(transformer.toConnectorInfo(c),
                            domain.toString(), validDate, validDate, countryCode, SimilarWebAPICategory.valueOf(apiCategory),
                            SimilarWebAPIName.valueOf(apiName));
                    return visits.isEmpty() ? 0d : visits.get(0).getMetric();

                case LEAD_ENRICHEMNT:
                    return similarWebService.leadEnrichment(transformer.toConnectorInfo(c),
                            domain.toString(), validDate, validDate, countryCode, SimilarWebAPICategory.valueOf(apiCategory),
                            SimilarWebAPIName.valueOf(apiName));

                case TECHNOGRAPHICS:
                    return similarWebService.retrieveTechnographics(transformer.toConnectorInfo(c),
                            domain.toString(), SimilarWebAPICategory.valueOf(apiCategory),
                            SimilarWebAPIName.valueOf(apiName));

                default:
                    log.error("Unknown SimilarWebAPICategoty: {}", category.name());
                    return null;

            }
        });

        return output.orElse(null);
    }

    protected String getStringValue(GraphContext context, FunctionCall functionCall, String configKey, String defaultValue) {
        String value = functionCall.getConfig().getOrDefault(configKey, "").toString();
        String resolved = tokenHelper.resolveTokens(context, value);
        return StringUtils.isBlank(value) ? defaultValue : resolved;
    }

    private String getValidDate(String inputDateStr, String startDateStr, String endDateStr){
        if(StringUtils.isBlank(startDateStr) || StringUtils.isBlank(endDateStr)){
            return inputDateStr;
        }

        Date inputDate = DateUtil.parse(inputDateStr, "yyyy-MM");
        Date startDate = DateUtil.parse(startDateStr, "yyyy-MM");
        Date endDate = DateUtil.parse(endDateStr, "yyyy-MM");

        if(inputDate.compareTo(startDate) >= 0 && inputDate.compareTo(endDate) <= 0){
            return inputDateStr;
        }
        return endDateStr;
    }

}
