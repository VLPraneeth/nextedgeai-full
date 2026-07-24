package com.syncari.connector.data;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ConvertResult {
    String leadId;
    String contactId;
    String accountId;
    String opptyId;
    boolean success;
    String error;
}
