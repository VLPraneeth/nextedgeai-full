package com.syncari.core.enrich.salesintel;

import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

@Accessors
@AllArgsConstructor
public class SalesIntelPersonRequest {

    private String email;
    private String lastName;
    private String firstName;
    private String phoneNumber;

}
