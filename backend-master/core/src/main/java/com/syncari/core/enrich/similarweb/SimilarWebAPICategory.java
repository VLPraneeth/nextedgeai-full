package com.syncari.core.enrich.similarweb;

import java.util.Arrays;
import java.util.List;

import static com.syncari.core.enrich.similarweb.SimilarWebAPIName.*;

public enum SimilarWebAPICategory{
    TOTAL_TRAFFIC("total-traffic-and-engagement",VISIT,PAGES_PER_VISIT,AVG_VISIT_DURATION,BOUNCE_RATE,DESKTOP_VISITS,MOBILE_VISITS),
    DESKTOP_TRAFFIC("traffic-and-engagement",VISIT,PAGES_PER_VISIT,AVG_VISIT_DURATION,
            BOUNCE_RATE, DESKTOP_UNIQUE_VISITORS),
    MOBILE_WEB_TRAFFIC("mobile-web",VISIT,PAGES_PER_VISIT,AVG_VISIT_DURATION,
            BOUNCE_RATE, DESKTOP_UNIQUE_VISITORS),
    GLOBAL_RANK("global-rank", SimilarWebAPIName.GLOBAL_RANK),
    COUNTRY_RANK("country-rank", SimilarWebAPIName.COUNTRY_RANK),
    LEAD_ENRICHEMNT("lead-enrichment", SITE_TYPE, EMPLOYEE_RANGE, ESTIMATED_REVENUE, COMPANY_HQ, WEBSITE_CATEGORY),
    TECHNOGRAPHICS("technographics", SimilarWebAPIName.TECHNOGRAPHICS),;

    private String apiCategory;
    private List<SimilarWebAPIName> availableMetrics;

    SimilarWebAPICategory(String apiCategory,SimilarWebAPIName... availableMetrics) {
        this.apiCategory = apiCategory;
        this.availableMetrics = availableMetrics==null? List.of(): Arrays.asList(availableMetrics);
    }

    public String getApiCategory() {
        return apiCategory;
    }

    public List<SimilarWebAPIName> getAvailableMetrics() {
        return availableMetrics;
    }
}
