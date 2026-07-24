package com.syncari.connector.data;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ConvertData {
	private String leadId;
	private String accountId;
	private String contactId;
	private String ownerId;
	private String opportunityId;
	private String convertedStatus;

}
