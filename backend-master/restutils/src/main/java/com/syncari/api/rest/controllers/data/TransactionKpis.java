package com.syncari.api.rest.controllers.data;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TransactionKpis {
	private long transactions;
	private String mostActiveEntity;
	private String mostActiveSynapse;
	private long newRecords;
	private long updateRecords;
}
