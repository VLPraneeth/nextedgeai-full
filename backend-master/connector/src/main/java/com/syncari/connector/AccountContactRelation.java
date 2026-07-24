package com.syncari.connector;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AccountContactRelation {
    String id;
    String accountId;
    String contactId;
    Object endDate;
    Object startDate;
    Object roles;
    boolean active;
    boolean direct;
}