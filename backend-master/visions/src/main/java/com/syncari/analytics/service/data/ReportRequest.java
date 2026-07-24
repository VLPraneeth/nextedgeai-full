package com.syncari.analytics.service.data;

import java.time.Instant;

import com.syncari.core.model.misc.PageRequest;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReportRequest {
	PageRequest page;
	Instant startDate;
	Instant endDate;
	String endSystem;
}
