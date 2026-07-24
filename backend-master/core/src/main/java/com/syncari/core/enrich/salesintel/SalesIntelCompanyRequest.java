package com.syncari.core.enrich.salesintel;

import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

@Accessors
@AllArgsConstructor
public class SalesIntelCompanyRequest {

    private String company_domain;
    private String company_industries;
    private String company_location_states;
    private String company_location_zipcodes;
    private Integer company_max_revenue;
    private Integer company_max_size;
    private Integer company_min_revenue;
    private Integer company_min_size;
    private String company_name;
    private Integer page;
    private Integer pageSize;
    private Boolean humanVerified;
}
