package com.syncari.core.enrich.similarweb;

public enum SimilarWebAPIName{
    VISIT("visits", "visits", "visits"),
    PAGES_PER_VISIT("pages-per-visit","pages_per_visit", "pages-per-visit"),
    AVG_VISIT_DURATION("average-visit-duration", "average_visit_duration", "average-visit-duration"),
    BOUNCE_RATE("bounce-rate", "bounce_rate", "bounce-rate"),
    GLOBAL_RANK("global-rank", "global_rank", "global-rank"),
    COUNTRY_RANK("country-rank", "country_rank", "country-rank"),
    DESKTOP_UNIQUE_VISITORS("desktop_unique_visitors", "unique_visitors", "desktop_unique_visitors"),
    MOBILE_WEB_UNIQUE_VISITORS("mobileweb_unique_visitors", "unique_visitors", "mobileweb_unique_visitors"),
    DESKTOP_VISITS("visits-split", "desktop_visit_share", "desktop_visits_split"),
    MOBILE_VISITS("visits-split", "mobile_web_visit_share", "mobile_visits_split"),
    SITE_TYPE("all", "site_type", "site_type"),
    EMPLOYEE_RANGE("all", "employee_range", "employee_range"),
    ESTIMATED_REVENUE("all", "estimated_revenue_in_usd", "estimated_revenue"),
    COMPANY_HQ("all", "headquarters", "company_hq"),
    WEBSITE_CATEGORY("all", "website_category", "website_category"),
    TECHNOGRAPHICS("all", "technologies", "technographics");

    private String apiName;
    private String metricName;
    private String displayNameKey;

    SimilarWebAPIName(String apiName, String metricName, String displayNameKey) {
        this.apiName = apiName;
        this.metricName = metricName;
        this.displayNameKey = displayNameKey;
    }

    public String getApiName() {
        return apiName;
    }

    public String getMetricName() {
        return metricName;
    }

    public String getDisplayNameKey() {
        return displayNameKey;
    }
}
