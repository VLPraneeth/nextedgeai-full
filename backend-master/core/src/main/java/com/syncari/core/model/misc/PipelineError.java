package com.syncari.core.model.misc;

import com.syncari.core.model.ErrorCategory;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Data
@Accessors(chain = true)
public class PipelineError {
	Instant startTime;
	Instant endTime;
	Status status;
	String details;
	String message;
	String nodeId;
	String graphId;
	Scope scope;
	boolean errorEmailSent;
	boolean isInternal;
	ErrorCategory category;
	int count;
	boolean pausedByError;
	private static final int MAX_RETRY_DURATION = 3 * 60 * 60 * 1000; // 3 hours
	private final static String dateFormat = "yyyy-MM-dd HH:mm:ss";

	public boolean continueToRetry() {
		return (Instant.now().toEpochMilli() - startTime.toEpochMilli()) <  MAX_RETRY_DURATION && status == Status.ACTIVE;
	}

	public boolean isActive() {
		return status == Status.ACTIVE;
	}

	public boolean resolvedWithinThreshold(Instant stopped) {
		if(startTime == null || stopped == null) return false;
		return (stopped.toEpochMilli() - startTime.toEpochMilli()) <  MAX_RETRY_DURATION && status == Status.ACTIVE;
	}

	public void increment() {
		count++;
	}

	public boolean isFirstError() {
		// send email for 5th retry
		return count == 5;
	}

	@Override
	public String toString() {
		return "PipelineError [start=" + nullOr(startTime) + ", end=" + nullOr(endTime) +
				", count=" + count + ", status=" + nullOr(status) + ", details=" + nullOr(details) +
				", message=" + nullOr(message) +"]";
	}

	private String nullOr(String value) {
		return value == null ? "" : value;
	}

	private String nullOr(Instant value) {
		return value == null ? "" : format(value.toEpochMilli());
	}

	private String nullOr(Status value) {
		return value == null ? "" : value.name();
	}

	private String format(long dateInEpocMilli) {
		DateTimeFormatter df = DateTimeFormatter.ofPattern(dateFormat).withZone(ZoneOffset.UTC);
		return df.format(Instant.ofEpochMilli(dateInEpocMilli));
	}
}
